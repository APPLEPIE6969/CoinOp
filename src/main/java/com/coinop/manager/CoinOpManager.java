package com.coinop.manager;

import com.coinop.api.CoinOpAPI;
import com.coinop.config.CoinOpConfig;
import com.coinop.config.PriceBounds;
import com.coinop.database.DatabaseManager;
import com.coinop.economy.EconomyManager;
import com.coinop.engine.MatchingEngine;
import com.coinop.engine.MatchingEngine.MatchResult;
import com.coinop.model.Order;
import com.coinop.model.OrderBook;
import com.coinop.model.Trade;
import com.coinop.sync.SyncManager;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Central manager - coordinates order matching, economy, sync, and limits
public class CoinOpManager implements CoinOpAPI {

    private final JavaPlugin plugin;
    private final MatchingEngine matchingEngine;
    private final EconomyManager economyManager;
    private final PriceBounds priceBounds;
    private final CoinOpConfig config;
    private SyncManager syncManager;
    private final DatabaseManager databaseManager;
    private final List<TradeListener> tradeListeners;

    // Daily trade limits: commodityId -> playerUuid -> volume
    private final Map<String, Map<UUID, Long>> dailyTradeVolume;
    private long lastDailyReset;

    public CoinOpManager(JavaPlugin plugin, EconomyManager economyManager, CoinOpConfig config) {
        this(plugin, economyManager, config, null);
    }

    public CoinOpManager(JavaPlugin plugin, EconomyManager economyManager, CoinOpConfig config,
            DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.config = config;
        this.databaseManager = databaseManager;
        this.priceBounds = config.getPriceBounds();
        this.matchingEngine = new MatchingEngine(economyManager, priceBounds, this::isValidCommodity);
        this.tradeListeners = new ArrayList<>();
        this.dailyTradeVolume = new ConcurrentHashMap<>();
        this.lastDailyReset = System.currentTimeMillis();

        // Register internal trade listener
        matchingEngine.addTradeListener(this::handleTrade);
    }

    public void setSyncManager(SyncManager syncManager) {
        this.syncManager = syncManager;
    }

    // Handle trades from matching engine
    private void handleTrade(Trade trade) {
        // Update daily volume for both parties
        updateDailyVolume(trade.getCommodityId(), trade.getBuyerUuid(), trade.getAmount());
        updateDailyVolume(trade.getCommodityId(), trade.getSellerUuid(), trade.getAmount());

        // Save to database
        if (databaseManager != null) {
            databaseManager.saveTrade(trade);
        }

        // Sync to other servers
        if (syncManager != null) {
            syncManager.broadcastTrade(trade);
        }

        // Notify listeners
        for (TradeListener listener : tradeListeners) {
            try {
                listener.onTrade(trade);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in trade listener: " + e.getMessage());
            }
        }
    }

    private void updateDailyVolume(String commodityId, UUID playerUuid, long amount) {
        dailyTradeVolume.computeIfAbsent(commodityId, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, amount, Long::sum);
    }

    // Check if player can trade more today
    private boolean canTradeToday(String commodityId, UUID playerUuid, long additionalAmount) {
        long limit = config.getDailyTradeLimit(commodityId);
        if (limit <= 0)
            return true;

        Map<UUID, Long> commodityVolumes = dailyTradeVolume.get(commodityId);
        if (commodityVolumes == null)
            return true;

        Long currentVolume = commodityVolumes.get(playerUuid);
        if (currentVolume == null)
            return true;

        return currentVolume + additionalAmount <= limit;
    }

    public void resetDailyVolumes() {
        dailyTradeVolume.clear();
        lastDailyReset = System.currentTimeMillis();
    }

    // === CoinOpAPI Implementation ===

    @Override
    public double getBestSellPrice(String commodityId) {
        OrderBook book = matchingEngine.getOrderBook(commodityId);
        double price = book.getBestSellPrice();
        return price == Double.MAX_VALUE ? -1 : price;
    }

    @Override
    public double getBestBuyPrice(String commodityId) {
        OrderBook book = matchingEngine.getOrderBook(commodityId);
        double price = book.getBestBuyPrice();
        return price == 0 ? -1 : price;
    }

    @Override
    public OrderBook getOrderBook(String commodityId) {
        return matchingEngine.getOrderBook(commodityId);
    }

    @Override
    public MatchResult placeBuyOrder(UUID playerUuid, String commodityId, long amount, double pricePerUnit) {
        // Check daily limit
        if (!canTradeToday(commodityId, playerUuid, amount)) {
            return MatchResult.rejected("Daily trade limit reached for " + commodityId);
        }

        // Check balance for worst case
        double maxCost = amount * pricePerUnit;
        if (!economyManager.hasBalance(playerUuid, maxCost)) {
            return MatchResult.rejected("Insufficient funds. Need " + economyManager.format(maxCost) +
                    ", have " + economyManager.format(economyManager.getBalance(playerUuid)));
        }

        Order order = new Order.Builder()
                .playerUuid(playerUuid)
                .commodityId(commodityId)
                .type(Order.OrderType.BUY)
                .pricePerUnit(pricePerUnit)
                .amount(amount)
                .build();

        MatchResult result = matchingEngine.processOrder(order);

        // Withdraw for filled portion
        if (!result.isRejected() && result.getFilledAmount() > 0) {
            double filledCost = result.getFilledAmount() * result.getAveragePrice();
            economyManager.withdraw(playerUuid, filledCost);
        }

        return result;
    }

    @Override
    public MatchResult placeSellOrder(UUID playerUuid, String commodityId, long amount, double pricePerUnit) {
        // Check daily limit
        if (!canTradeToday(commodityId, playerUuid, amount)) {
            return MatchResult.rejected("Daily trade limit reached for " + commodityId);
        }

        Order order = new Order.Builder()
                .playerUuid(playerUuid)
                .commodityId(commodityId)
                .type(Order.OrderType.SELL)
                .pricePerUnit(pricePerUnit)
                .amount(amount)
                .build();

        MatchResult result = matchingEngine.processOrder(order);

        // Deposit for filled portion
        if (!result.isRejected() && result.getFilledAmount() > 0) {
            double filledValue = result.getFilledAmount() * result.getAveragePrice();
            economyManager.deposit(playerUuid, filledValue);
        }

        return result;
    }

    @Override
    public MatchResult instantBuy(UUID playerUuid, String commodityId, long amount) {
        // Check daily limit
        if (!canTradeToday(commodityId, playerUuid, amount)) {
            return MatchResult.rejected("Daily trade limit reached for " + commodityId);
        }

        // Check if sell orders exist
        double bestSellPrice = getBestSellPrice(commodityId);
        if (bestSellPrice <= 0) {
            return MatchResult.rejected("No sell orders available");
        }

        // Check balance
        double maxCost = amount * bestSellPrice;
        if (!economyManager.hasBalance(playerUuid, maxCost)) {
            return MatchResult.rejected("Insufficient funds. Need " + economyManager.format(maxCost) +
                    ", have " + economyManager.format(economyManager.getBalance(playerUuid)));
        }

        MatchResult result = matchingEngine.instantBuy(playerUuid, commodityId, amount);

        if (!result.isRejected() && result.getFilledAmount() > 0) {
            double filledCost = result.getFilledAmount() * result.getAveragePrice();
            economyManager.withdraw(playerUuid, filledCost);
        }

        return result;
    }

    @Override
    public MatchResult instantSell(UUID playerUuid, String commodityId, long amount) {
        // Check daily limit
        if (!canTradeToday(commodityId, playerUuid, amount)) {
            return MatchResult.rejected("Daily trade limit reached for " + commodityId);
        }

        MatchResult result = matchingEngine.instantSell(playerUuid, commodityId, amount);

        if (!result.isRejected() && result.getFilledAmount() > 0) {
            double filledValue = result.getFilledAmount() * result.getAveragePrice();
            economyManager.deposit(playerUuid, filledValue);
        }

        return result;
    }

    @Override
    public boolean cancelOrder(String commodityId, long orderId) {
        return matchingEngine.cancelOrder(commodityId, orderId);
    }

    @Override
    public int cancelAllOrders(UUID playerUuid) {
        return matchingEngine.cancelAllOrders(playerUuid);
    }

    @Override
    public Map<String, List<Order>> getPlayerOrders(UUID playerUuid) {
        Map<String, List<Order>> result = new HashMap<>();

        for (Map.Entry<String, OrderBook> entry : matchingEngine.getAllOrderBooks().entrySet()) {
            Set<Order> orders = entry.getValue().getPlayerOrders(playerUuid);
            if (!orders.isEmpty()) {
                result.put(entry.getKey(), new ArrayList<>(orders));
            }
        }

        return result;
    }

    @Override
    public List<Trade> getRecentTrades(String commodityId, int limit) {
        return matchingEngine.getRecentTrades(commodityId, limit);
    }

    @Override
    public long get24hVolume(String commodityId) {
        OrderBook book = matchingEngine.getOrderBook(commodityId);
        return book.get24hVolume();
    }

    @Override
    public double get24hVWAP(String commodityId) {
        OrderBook book = matchingEngine.getOrderBook(commodityId);
        return book.get24hVWAP();
    }

    @Override
    public boolean isValidCommodity(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return isValidCommodity(item.getType().name());
    }

    @Override
    public boolean isValidCommodity(String commodityId) {
        if (commodityId == null || commodityId.isEmpty()) {
            return false;
        }

        try {
            Material.valueOf(commodityId);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return config.getAllowedCommodities().isEmpty()
                || config.getAllowedCommodities().contains(commodityId);
    }

    @Override
    public String getCommodityId(ItemStack item) {
        if (!isValidCommodity(item)) {
            return null;
        }
        return item.getType().name();
    }

    @Override
    public String getCommodityDisplayName(String commodityId) {
        try {
            Material material = Material.valueOf(commodityId);
            return formatMaterialName(material);
        } catch (IllegalArgumentException e) {
            return commodityId;
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    @Override
    public void registerTradeListener(TradeListener listener) {
        tradeListeners.add(listener);
    }

    @Override
    public void unregisterTradeListener(TradeListener listener) {
        tradeListeners.remove(listener);
    }

    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public CoinOpConfig getConfig() {
        return config;
    }

    public PriceBounds getPriceBounds() {
        return priceBounds;
    }

    // Load orders from database
    public void loadOrders(Map<String, List<Order>> orders) {
        for (Map.Entry<String, List<Order>> entry : orders.entrySet()) {
            String commodityId = entry.getKey();
            OrderBook book = matchingEngine.getOrderBook(commodityId);
            for (Order order : entry.getValue()) {
                book.addOrder(order);
            }
        }
    }

    // Save state and cleanup
    public void shutdown() {
        tradeListeners.clear();

        // Save active orders
        if (databaseManager != null) {
            for (Map.Entry<String, OrderBook> entry : matchingEngine.getAllOrderBooks().entrySet()) {
                OrderBook book = entry.getValue();
                Iterator<Order> bidsIter = book.getBidsIterator();
                while (bidsIter.hasNext()) {
                    databaseManager.saveOrder(bidsIter.next());
                }
                Iterator<Order> asksIter = book.getAsksIterator();
                while (asksIter.hasNext()) {
                    databaseManager.saveOrder(asksIter.next());
                }
            }
        }
    }
}
