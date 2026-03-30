package com.coinop.api;

import com.coinop.engine.MatchingEngine.MatchResult;
import com.coinop.model.Order;
import com.coinop.model.OrderBook;
import com.coinop.model.Trade;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Public API for third-party plugin integration
// Use: Bukkit.getServicesManager().getRegistration(CoinOpAPI.class).getProvider()
public interface CoinOpAPI {

    // Market data
    double getBestSellPrice(String commodityId);

    double getBestBuyPrice(String commodityId);

    OrderBook getOrderBook(String commodityId);

    // Order placement
    MatchResult placeBuyOrder(UUID playerUuid, String commodityId, long amount, double pricePerUnit);

    MatchResult placeSellOrder(UUID playerUuid, String commodityId, long amount, double pricePerUnit);

    // Instant trades at market price
    MatchResult instantBuy(UUID playerUuid, String commodityId, long amount);

    MatchResult instantSell(UUID playerUuid, String commodityId, long amount);

    // Order management
    boolean cancelOrder(String commodityId, long orderId);

    int cancelAllOrders(UUID playerUuid);

    Map<String, List<Order>> getPlayerOrders(UUID playerUuid);

    // Trade history
    List<Trade> getRecentTrades(String commodityId, int limit);

    long get24hVolume(String commodityId);

    double get24hVWAP(String commodityId);

    // Commodity utilities
    boolean isValidCommodity(ItemStack item);

    boolean isValidCommodity(String commodityId);

    String getCommodityId(ItemStack item);

    String getCommodityDisplayName(String commodityId);

    // Event listeners
    void registerTradeListener(TradeListener listener);

    void unregisterTradeListener(TradeListener listener);

    // Trade event callback
    interface TradeListener {
        void onTrade(Trade trade);
    }
}
