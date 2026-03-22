package com.coinop.model;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

// Order book for a single commodity - holds buy and sell orders
// Uses ConcurrentSkipListSet for thread-safe sorted access
public class OrderBook {

    private final String commodityId;

    // Buy orders: highest price first
    private final ConcurrentSkipListSet<Order> bids;

    // Sell orders: lowest price first
    private final ConcurrentSkipListSet<Order> asks;

    // Quick lookup by order ID
    private final Map<Long, Order> orderIndex;

    // Quick lookup by player
    private final Map<UUID, Set<Order>> playerOrderIndex;

    // Lock for operations touching both bids and asks
    private final ReentrantReadWriteLock lock;

    // 24h stats
    private volatile double lastTradePrice;
    private volatile long totalVolume24h;
    private volatile double totalPrice24h;
    private volatile long lastResetTime;

    public OrderBook(String commodityId) {
        this.commodityId = commodityId;
        this.bids = new ConcurrentSkipListSet<>();
        this.asks = new ConcurrentSkipListSet<>();
        this.orderIndex = new ConcurrentHashMap<>();
        this.playerOrderIndex = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastResetTime = System.currentTimeMillis();
    }

    public String getCommodityId() {
        return commodityId;
    }

    public boolean addOrder(Order order) {
        if (!order.getCommodityId().equals(commodityId)) {
            throw new IllegalArgumentException("Order commodity does not match order book commodity");
        }

        lock.writeLock().lock();
        try {
            ConcurrentSkipListSet<Order> book = order.getType() == Order.OrderType.BUY ? bids : asks;

            boolean added = book.add(order);
            if (added) {
                orderIndex.put(order.getOrderId(), order);
                playerOrderIndex.computeIfAbsent(order.getPlayerUuid(), k -> ConcurrentHashMap.newKeySet())
                        .add(order);
            }
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Order removeOrder(long orderId) {
        lock.writeLock().lock();
        try {
            Order order = orderIndex.remove(orderId);
            if (order != null) {
                ConcurrentSkipListSet<Order> book = order.getType() == Order.OrderType.BUY ? bids : asks;
                book.remove(order);

                Set<Order> playerOrders = playerOrderIndex.get(order.getPlayerUuid());
                if (playerOrders != null) {
                    playerOrders.remove(order);
                    if (playerOrders.isEmpty()) {
                        playerOrderIndex.remove(order.getPlayerUuid());
                    }
                }

                order.cancel();
            }
            return order;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Order getOrder(long orderId) {
        return orderIndex.get(orderId);
    }

    // Best buy price (highest)
    public double getBestBuyPrice() {
        lock.readLock().lock();
        try {
            Order bestBid = bids.isEmpty() ? null : bids.first();
            return bestBid != null ? bestBid.getPricePerUnit() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Best sell price (lowest)
    public double getBestSellPrice() {
        lock.readLock().lock();
        try {
            Order bestAsk = asks.isEmpty() ? null : asks.first();
            return bestAsk != null ? bestAsk.getPricePerUnit() : Double.MAX_VALUE;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Order getBestBuyOrder() {
        lock.readLock().lock();
        try {
            return bids.isEmpty() ? null : bids.first();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Order getBestSellOrder() {
        lock.readLock().lock();
        try {
            return asks.isEmpty() ? null : asks.first();
        } finally {
            lock.readLock().unlock();
        }
    }

    // Spread between best buy and sell
    public double getSpread() {
        double bestBuy = getBestBuyPrice();
        double bestSell = getBestSellPrice();

        if (bestBuy == 0 || bestSell == Double.MAX_VALUE) {
            return -1;
        }
        return bestSell - bestBuy;
    }

    // Mid-price for display
    public double getMidPrice() {
        double bestBuy = getBestBuyPrice();
        double bestSell = getBestSellPrice();

        if (bestBuy == 0 && bestSell == Double.MAX_VALUE) {
            return lastTradePrice;
        }
        if (bestBuy == 0)
            return bestSell;
        if (bestSell == Double.MAX_VALUE)
            return bestBuy;

        return (bestBuy + bestSell) / 2;
    }

    public Set<Order> getPlayerOrders(UUID playerUuid) {
        Set<Order> orders = playerOrderIndex.get(playerUuid);
        return orders != null ? Collections.unmodifiableSet(orders) : Collections.emptySet();
    }

    public long getBuyVolumeAtPrice(double price) {
        return bids.stream()
                .filter(o -> Math.abs(o.getPricePerUnit() - price) < 0.001)
                .mapToLong(Order::getRemainingAmount)
                .sum();
    }

    public long getSellVolumeAtPrice(double price) {
        return asks.stream()
                .filter(o -> Math.abs(o.getPricePerUnit() - price) < 0.001)
                .mapToLong(Order::getRemainingAmount)
                .sum();
    }

    public long getTotalBuyVolume() {
        return bids.stream().mapToLong(Order::getRemainingAmount).sum();
    }

    public long getTotalSellVolume() {
        return asks.stream().mapToLong(Order::getRemainingAmount).sum();
    }

    public int getBuyOrderCount() {
        return bids.size();
    }

    public int getSellOrderCount() {
        return asks.size();
    }

    // Get order book depth for display
    public Map<String, List<PriceLevel>> getDepth(int levels) {
        lock.readLock().lock();
        try {
            Map<String, List<PriceLevel>> depth = new HashMap<>();

            // Aggregate bids by price
            depth.put("bids", bids.stream()
                    .collect(Collectors.groupingBy(Order::getPricePerUnit, TreeMap::new,
                            Collectors.summingLong(Order::getRemainingAmount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.<Double, Long>comparingByKey().reversed())
                    .limit(levels)
                    .map(e -> new PriceLevel(e.getKey(), e.getValue()))
                    .collect(Collectors.toList()));

            // Aggregate asks by price
            depth.put("asks", asks.stream()
                    .collect(Collectors.groupingBy(Order::getPricePerUnit, TreeMap::new,
                            Collectors.summingLong(Order::getRemainingAmount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(levels)
                    .map(e -> new PriceLevel(e.getKey(), e.getValue()))
                    .collect(Collectors.toList()));

            return depth;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Record a trade for stats
    public void recordTrade(double price, long volume) {
        this.lastTradePrice = price;
        this.totalVolume24h += volume;
        this.totalPrice24h += price * volume;
    }

    public double getLastTradePrice() {
        return lastTradePrice;
    }

    // Volume weighted average price over 24h
    public double get24hVWAP() {
        if (totalVolume24h == 0)
            return 0;
        return totalPrice24h / totalVolume24h;
    }

    public long get24hVolume() {
        return totalVolume24h;
    }

    // Called by scheduled task
    public void reset24hStats() {
        this.totalVolume24h = 0;
        this.totalPrice24h = 0;
        this.lastResetTime = System.currentTimeMillis();
    }

    public Iterator<Order> getBidsIterator() {
        return bids.iterator();
    }

    public Iterator<Order> getAsksIterator() {
        return asks.iterator();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            bids.clear();
            asks.clear();
            orderIndex.clear();
            playerOrderIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Remove inactive orders, return count
    public int cleanInactiveOrders() {
        lock.writeLock().lock();
        try {
            int removed = 0;

            Iterator<Order> bidIterator = bids.iterator();
            while (bidIterator.hasNext()) {
                Order order = bidIterator.next();
                if (!order.isActive()) {
                    bidIterator.remove();
                    orderIndex.remove(order.getOrderId());
                    removed++;
                }
            }

            Iterator<Order> askIterator = asks.iterator();
            while (askIterator.hasNext()) {
                Order order = askIterator.next();
                if (!order.isActive()) {
                    askIterator.remove();
                    orderIndex.remove(order.getOrderId());
                    removed++;
                }
            }

            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Price level with aggregated volume
    public static class PriceLevel {
        private final double price;
        private final long volume;

        public PriceLevel(double price, long volume) {
            this.price = price;
            this.volume = volume;
        }

        public double getPrice() {
            return price;
        }

        public long getVolume() {
            return volume;
        }
    }
}
