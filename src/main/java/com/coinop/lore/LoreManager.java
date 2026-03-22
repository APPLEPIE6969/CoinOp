package com.coinop.lore;

import com.coinop.api.CoinOpAPI;
import com.coinop.config.CoinOpConfig;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Manages lore-based price discovery - shows market data in item tooltips
public class LoreManager implements Listener {

    private final JavaPlugin plugin;
    private final CoinOpAPI CoinOpAPI;
    private final CoinOpConfig config;
    private final Map<String, PriceData> priceDataCache;
    private volatile long lastCacheUpdate;
    private static final long CACHE_UPDATE_INTERVAL = 5000;

    // Price history for trend calculation
    private final Map<String, LinkedList<PricePoint>> priceHistory;
    private static final int MAX_HISTORY_POINTS = 288; // 24h at 5-min intervals

    public LoreManager(JavaPlugin plugin, CoinOpAPI CoinOpAPI, CoinOpConfig config) {
        this.plugin = plugin;
        this.CoinOpAPI = CoinOpAPI;
        this.config = config;
        this.priceDataCache = new ConcurrentHashMap<>();
        this.priceHistory = new ConcurrentHashMap<>();
        this.lastCacheUpdate = 0;
    }

    public void start() {
        if (!config.isLoreEnabled())
            return;

        // Periodic cache updates
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePriceCache();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, config.getLoreUpdateInterval());

        // History updates every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                recordPriceHistory();
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);

        plugin.getLogger().info("Lore-based price discovery enabled.");
    }

    private void updatePriceCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_UPDATE_INTERVAL)
            return;
        lastCacheUpdate = now;

        for (String commodityId : priceDataCache.keySet()) {
            updateCommodityPriceData(commodityId);
        }
    }

    private void updateCommodityPriceData(String commodityId) {
        double buyPrice = CoinOpAPI.getBestBuyPrice(commodityId);
        double sellPrice = CoinOpAPI.getBestSellPrice(commodityId);
        long volume24h = CoinOpAPI.get24hVolume(commodityId);
        double vwap = CoinOpAPI.get24hVWAP(commodityId);

        Trend trend = calculateTrend(commodityId);

        PriceData data = new PriceData(
                commodityId,
                buyPrice > 0 ? buyPrice : -1,
                sellPrice > 0 ? sellPrice : -1,
                volume24h,
                vwap,
                trend,
                System.currentTimeMillis());

        priceDataCache.put(commodityId, data);
    }

    private void recordPriceHistory() {
        for (String commodityId : priceDataCache.keySet()) {
            PriceData data = priceDataCache.get(commodityId);
            if (data != null && data.hasValidPrices()) {
                LinkedList<PricePoint> history = priceHistory.computeIfAbsent(
                        commodityId, k -> new LinkedList<>());

                history.addLast(new PricePoint(data.getMidPrice(), System.currentTimeMillis()));

                while (history.size() > MAX_HISTORY_POINTS) {
                    history.removeFirst();
                }
            }
        }
    }

    // Calculate trend by comparing recent vs older averages
    private Trend calculateTrend(String commodityId) {
        LinkedList<PricePoint> history = priceHistory.get(commodityId);
        if (history == null || history.size() < 2)
            return Trend.NEUTRAL;

        int size = history.size();
        double recentAvg = 0;
        double olderAvg = 0;

        int recentCount = Math.min(6, size / 2);
        int olderCount = Math.min(6, size / 2);

        for (int i = 0; i < recentCount; i++) {
            recentAvg += history.get(size - 1 - i).price;
        }
        recentAvg /= recentCount;

        for (int i = 0; i < olderCount; i++) {
            olderAvg += history.get(size - 1 - recentCount - i).price;
        }
        olderAvg /= olderCount;

        if (olderAvg == 0)
            return Trend.NEUTRAL;

        double change = (recentAvg - olderAvg) / olderAvg;

        if (change > 0.05)
            return Trend.STRONG_UP;
        if (change > 0.02)
            return Trend.UP;
        if (change < -0.05)
            return Trend.STRONG_DOWN;
        if (change < -0.02)
            return Trend.DOWN;
        return Trend.NEUTRAL;
    }

    public PriceData getPriceData(String commodityId) {
        PriceData data = priceDataCache.get(commodityId);
        if (data == null || data.isExpired()) {
            updateCommodityPriceData(commodityId);
            data = priceDataCache.get(commodityId);
        }
        return data;
    }

    // Apply CoinOp lore to an item
    public ItemStack applyCoinOpLore(ItemStack item) {
        if (!config.isLoreEnabled() || item == null || item.getType() == Material.AIR) {
            return item;
        }

        String commodityId = CoinOpAPI.getCommodityId(item);
        if (commodityId == null)
            return item;

        PriceData data = getPriceData(commodityId);
        if (data == null)
            return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        List<String> lore = new ArrayList<>();

        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + " CoinOp Market Data");
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Buy price
        if (data.hasBuyPrice()) {
            lore.add(ChatColor.GREEN + "Buy Price: " + ChatColor.WHITE +
                    String.format("%.2f", data.getBuyPrice()) + " each");
        } else {
            lore.add(ChatColor.GRAY + "Buy Price: " + ChatColor.DARK_GRAY + "No orders");
        }

        // Sell price
        if (data.hasSellPrice()) {
            lore.add(ChatColor.RED + "Sell Price: " + ChatColor.WHITE +
                    String.format("%.2f", data.getSellPrice()) + " each");
        } else {
            lore.add(ChatColor.GRAY + "Sell Price: " + ChatColor.DARK_GRAY + "No orders");
        }

        // Spread
        if (data.hasValidPrices()) {
            double spread = data.getSpread();
            double spreadPercent = data.getSpreadPercent();
            lore.add(ChatColor.YELLOW + "Spread: " + ChatColor.WHITE +
                    String.format("%.2f (%.1f%%)", spread, spreadPercent));
        }

        // 24h Volume
        lore.add(ChatColor.AQUA + "24h Volume: " + ChatColor.WHITE +
                formatNumber(data.getVolume24h()));

        // Trend
        String trendStr = formatTrend(data.getTrend());
        lore.add(ChatColor.LIGHT_PURPLE + "Trend: " + trendStr);

        // VWAP
        if (data.getVwap() > 0) {
            lore.add(ChatColor.BLUE + "24h Avg: " + ChatColor.WHITE +
                    String.format("%.2f", data.getVwap()));
        }

        // Instant values
        lore.add("");
        lore.add(ChatColor.GRAY + "Instant Sell (" + formatNumber(item.getAmount()) + "): " +
                ChatColor.GREEN
                + (data.hasSellPrice() ? String.format("%.2f", data.getSellPrice() * item.getAmount()) : "N/A"));
        lore.add(ChatColor.GRAY + "Instant Buy (" + formatNumber(item.getAmount()) + "): " +
                ChatColor.RED
                + (data.hasBuyPrice() ? String.format("%.2f", data.getBuyPrice() * item.getAmount()) : "N/A"));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private String formatTrend(Trend trend) {
        switch (trend) {
            case STRONG_UP:
                return ChatColor.DARK_GREEN + "▲▲ Strong Up";
            case UP:
                return ChatColor.GREEN + "▲ Up";
            case NEUTRAL:
                return ChatColor.YELLOW + "● Stable";
            case DOWN:
                return ChatColor.RED + "▼ Down";
            case STRONG_DOWN:
                return ChatColor.DARK_RED + "▼▼ Strong Down";
            default:
                return ChatColor.GRAY + "? Unknown";
        }
    }

    public void registerCommodity(String commodityId) {
        priceDataCache.computeIfAbsent(commodityId, id -> {
            updateCommodityPriceData(id);
            return priceDataCache.get(id);
        });
    }

    public void clearCache() {
        priceDataCache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.isLoreEnabled())
            return;
    }

    // Price data container
    public static class PriceData {
        private final String commodityId;
        private final double buyPrice;
        private final double sellPrice;
        private final long volume24h;
        private final double vwap;
        private final Trend trend;
        private final long timestamp;

        public PriceData(String commodityId, double buyPrice, double sellPrice,
                long volume24h, double vwap, Trend trend, long timestamp) {
            this.commodityId = commodityId;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.volume24h = volume24h;
            this.vwap = vwap;
            this.trend = trend;
            this.timestamp = timestamp;
        }

        public String getCommodityId() {
            return commodityId;
        }

        public double getBuyPrice() {
            return buyPrice;
        }

        public double getSellPrice() {
            return sellPrice;
        }

        public long getVolume24h() {
            return volume24h;
        }

        public double getVwap() {
            return vwap;
        }

        public Trend getTrend() {
            return trend;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean hasBuyPrice() {
            return buyPrice > 0;
        }

        public boolean hasSellPrice() {
            return sellPrice > 0;
        }

        public boolean hasValidPrices() {
            return hasBuyPrice() && hasSellPrice();
        }

        public double getMidPrice() {
            if (hasValidPrices())
                return (buyPrice + sellPrice) / 2;
            return hasBuyPrice() ? buyPrice : sellPrice;
        }

        public double getSpread() {
            if (hasValidPrices())
                return sellPrice - buyPrice;
            return 0;
        }

        public double getSpreadPercent() {
            double mid = getMidPrice();
            if (mid > 0 && hasValidPrices()) {
                return (getSpread() / mid) * 100;
            }
            return 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_UPDATE_INTERVAL * 2;
        }
    }

    // Price point for history
    public static class PricePoint {
        public final double price;
        public final long timestamp;

        public PricePoint(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }

    // Trend enum
    public enum Trend {
        STRONG_UP,
        UP,
        NEUTRAL,
        DOWN,
        STRONG_DOWN
    }
}
