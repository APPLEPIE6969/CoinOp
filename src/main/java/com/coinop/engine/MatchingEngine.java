package com.coinop.engine;

import com.coinop.model.Order;
import com.coinop.model.OrderBook;
import com.coinop.model.Trade;
import com.coinop.config.PriceBounds;
import com.coinop.economy.EconomyManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

// Order matching engine - continuous double auction with price-time priority
public class MatchingEngine {

    private final Map<String, OrderBook> orderBooks;
    private final EconomyManager economyManager;
    private final PriceBounds priceBounds;
    private final Predicate<String> commodityValidator;
    private final List<Trade> tradeHistory;
    private static final int MAX_TRADE_HISTORY = 10000;
    private final AtomicLong tradeIdGenerator;
    private final List<TradeListener> tradeListeners;

    public MatchingEngine(EconomyManager economyManager, PriceBounds priceBounds, Predicate<String> commodityValidator) {
        this.orderBooks = new ConcurrentHashMap<>();
        this.economyManager = economyManager;
        this.priceBounds = priceBounds;
        this.commodityValidator = commodityValidator;
        this.tradeHistory = Collections.synchronizedList(new ArrayList<>());
        this.tradeIdGenerator = new AtomicLong(System.currentTimeMillis());
        this.tradeListeners = new ArrayList<>();
    }

    public OrderBook getOrderBook(String commodityId) {
        OrderBook book = orderBooks.get(commodityId);
        if (book != null) {
            return book;
        }

        if (commodityValidator != null && !commodityValidator.test(commodityId)) {
            throw new IllegalArgumentException("Invalid commodity: " + commodityId);
        }

        return orderBooks.computeIfAbsent(commodityId, OrderBook::new);
    }

    // Main entry point - process an order through the matching engine
    public MatchResult processOrder(Order order) {
        // Validate price bounds
        if (!priceBounds.isWithinBounds(order.getCommodityId(), order.getPricePerUnit())) {
            return MatchResult.rejected("Price outside allowed bounds");
        }

        OrderBook book = getOrderBook(order.getCommodityId());

        // Synchronize on the order book to prevent race conditions
        synchronized (book) {
            return processOrderInternal(order, book);
        }
    }

    private MatchResult processOrderInternal(Order order, OrderBook book) {
        List<Trade> trades = new ArrayList<>();

        // Get matching orders from the opposite side
        List<Order> matchingOrders;
        if (order.getType() == Order.OrderType.BUY) {
            matchingOrders = new ArrayList<>();
            Iterator<Order> iter = book.getAsksIterator();
            while (iter.hasNext()) {
                matchingOrders.add(iter.next());
            }
        } else {
            matchingOrders = new ArrayList<>();
            Iterator<Order> iter = book.getBidsIterator();
            while (iter.hasNext()) {
                matchingOrders.add(iter.next());
            }
        }

        long remainingAmount = order.getAmount();
        double totalValue = 0;

        // Match against existing orders
        for (Order matchingOrder : matchingOrders) {
            if (remainingAmount <= 0)
                break;

            // Check if prices cross
            boolean pricesCross = order.getType() == Order.OrderType.BUY
                    ? order.getPricePerUnit() >= matchingOrder.getPricePerUnit()
                    : order.getPricePerUnit() <= matchingOrder.getPricePerUnit();

            if (!pricesCross)
                break;

            // Calculate match amount
            long matchAmount = Math.min(remainingAmount, matchingOrder.getRemainingAmount());
            double matchPrice = matchingOrder.getPricePerUnit(); // Use resting order's price

            // Create trade
            Trade trade = createTrade(order, matchingOrder, matchAmount, matchPrice);
            trades.add(trade);

            // Update amounts
            matchingOrder.fill(matchAmount);
            remainingAmount -= matchAmount;
            totalValue += matchAmount * matchPrice;

            // Pay the resting order's owner
            if (economyManager != null) {
                double restingValue = matchAmount * matchPrice;
                if (order.getType() == Order.OrderType.BUY) {
                    economyManager.deposit(matchingOrder.getPlayerUuid(), restingValue);
                }
            }

            // Remove fully filled orders
            if (matchingOrder.getRemainingAmount() == 0) {
                book.removeOrder(matchingOrder.getOrderId());
            }
        }

        // Place unfilled portion in order book
        if (remainingAmount > 0 && !order.isInstant()) {
            Order remainingOrder = new Order.Builder()
                    .playerUuid(order.getPlayerUuid())
                    .commodityId(order.getCommodityId())
                    .type(order.getType())
                    .pricePerUnit(order.getPricePerUnit())
                    .amount(remainingAmount)
                    .build();
            book.addOrder(remainingOrder);
        }

        // Record trades and notify listeners
        for (Trade trade : trades) {
            tradeHistory.add(trade);
            book.recordTrade(trade.getPrice(), trade.getAmount());
            notifyTradeListeners(trade);
        }

        // Trim history if too large
        while (tradeHistory.size() > MAX_TRADE_HISTORY) {
            tradeHistory.remove(0);
        }

        return new MatchResult(order, trades, remainingAmount, totalValue);
    }

    // Instant buy at best available price
    public MatchResult instantBuy(UUID playerUuid, String commodityId, long amount) {
        OrderBook book = getOrderBook(commodityId);
        double bestSellPrice = book.getBestSellPrice();

        if (bestSellPrice == Double.MAX_VALUE) {
            return MatchResult.rejected("No sell orders available");
        }

        Order order = new Order.Builder()
                .playerUuid(playerUuid)
                .commodityId(commodityId)
                .type(Order.OrderType.BUY)
                .pricePerUnit(bestSellPrice)
                .amount(amount)
                .instant(true)
                .build();

        return processOrder(order);
    }

    // Instant sell at best available price
    public MatchResult instantSell(UUID playerUuid, String commodityId, long amount) {
        OrderBook book = getOrderBook(commodityId);
        double bestBuyPrice = book.getBestBuyPrice();

        if (bestBuyPrice == 0) {
            return MatchResult.rejected("No buy orders available");
        }

        Order order = new Order.Builder()
                .playerUuid(playerUuid)
                .commodityId(commodityId)
                .type(Order.OrderType.SELL)
                .pricePerUnit(bestBuyPrice)
                .amount(amount)
                .instant(true)
                .build();

        return processOrder(order);
    }

    private Trade createTrade(Order incomingOrder, Order restingOrder, long amount, double price) {
        UUID buyerUuid = incomingOrder.getType() == Order.OrderType.BUY
                ? incomingOrder.getPlayerUuid()
                : restingOrder.getPlayerUuid();
        UUID sellerUuid = incomingOrder.getType() == Order.OrderType.SELL
                ? incomingOrder.getPlayerUuid()
                : restingOrder.getPlayerUuid();

        return new Trade(
                tradeIdGenerator.incrementAndGet(),
                incomingOrder.getCommodityId(),
                buyerUuid,
                sellerUuid,
                amount,
                price,
                System.currentTimeMillis());
    }

    public boolean cancelOrder(String commodityId, long orderId) {
        OrderBook book = orderBooks.get(commodityId);
        if (book == null)
            return false;

        synchronized (book) {
            Order order = book.removeOrder(orderId);
            return order != null;
        }
    }

    public int cancelAllOrders(UUID playerUuid) {
        int cancelled = 0;
        for (OrderBook book : orderBooks.values()) {
            Set<Order> playerOrders = new HashSet<>(book.getPlayerOrders(playerUuid));
            for (Order order : playerOrders) {
                if (book.removeOrder(order.getOrderId()) != null) {
                    cancelled++;
                }
            }
        }
        return cancelled;
    }

    public List<Trade> getRecentTrades(String commodityId, int limit) {
        List<Trade> recent = new ArrayList<>();
        synchronized (tradeHistory) {
            for (int i = tradeHistory.size() - 1; i >= 0 && recent.size() < limit; i--) {
                Trade trade = tradeHistory.get(i);
                if (trade.getCommodityId().equals(commodityId)) {
                    recent.add(trade);
                }
            }
        }
        return recent;
    }

    public Map<String, OrderBook> getAllOrderBooks() {
        return Collections.unmodifiableMap(orderBooks);
    }

    public void addTradeListener(TradeListener listener) {
        tradeListeners.add(listener);
    }

    public void removeTradeListener(TradeListener listener) {
        tradeListeners.remove(listener);
    }

    private void notifyTradeListeners(Trade trade) {
        for (TradeListener listener : tradeListeners) {
            try {
                listener.onTrade(trade);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public int cleanupInactiveOrders() {
        int total = 0;
        for (OrderBook book : orderBooks.values()) {
            total += book.cleanInactiveOrders();
        }
        return total;
    }

    public interface TradeListener {
        void onTrade(Trade trade);
    }

    // Result of order matching
    public static class MatchResult {
        private final Order originalOrder;
        private final List<Trade> trades;
        private final long unfilledAmount;
        private final double totalValue;
        private final boolean rejected;
        private final String rejectionReason;

        private MatchResult(Order originalOrder, List<Trade> trades, long unfilledAmount,
                double totalValue, boolean rejected, String rejectionReason) {
            this.originalOrder = originalOrder;
            this.trades = trades;
            this.unfilledAmount = unfilledAmount;
            this.totalValue = totalValue;
            this.rejected = rejected;
            this.rejectionReason = rejectionReason;
        }

        public MatchResult(Order order, List<Trade> trades, long unfilledAmount, double totalValue) {
            this(order, trades, unfilledAmount, totalValue, false, null);
        }

        public static MatchResult rejected(String reason) {
            return new MatchResult(null, Collections.emptyList(), 0, 0, true, reason);
        }

        public Order getOriginalOrder() {
            return originalOrder;
        }

        public List<Trade> getTrades() {
            return Collections.unmodifiableList(trades);
        }

        public long getUnfilledAmount() {
            return unfilledAmount;
        }

        public double getTotalValue() {
            return totalValue;
        }

        public boolean isRejected() {
            return rejected;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public boolean isFullyFilled() {
            return !rejected && unfilledAmount == 0;
        }

        public boolean isPartiallyFilled() {
            return !rejected && unfilledAmount > 0 && !trades.isEmpty();
        }

        public long getFilledAmount() {
            return originalOrder != null ? originalOrder.getAmount() - unfilledAmount : 0;
        }

        public double getAveragePrice() {
            long filled = getFilledAmount();
            return filled > 0 ? totalValue / filled : 0;
        }
    }
}
