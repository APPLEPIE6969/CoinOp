package com.coinop.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Main config - handles all plugin settings from config.yml
public class CoinOpConfig {

    private final JavaPlugin plugin;
    private final PriceBounds priceBounds;

    // Commodities
    private final Set<String> allowedCommodities;
    private final Map<String, String> commodityCategories;

    // Trade limits
    private final Map<String, Long> dailyTradeLimits;
    private long globalDailyTradeLimit;

    // Economy settings
    private double taxRate;
    private long minOrderAmount;
    private long maxOrderAmount;

    // Multi-server sync
    private boolean syncEnabled;
    private String redisHost;
    private int redisPort;
    private String redisPassword;

    // Database
    private String databaseType;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUser;
    private String databasePassword;

    // Lore display
    private boolean loreEnabled;
    private int loreUpdateInterval;

    // Anvil rework
    private boolean anvilReworkEnabled;
    private boolean enchantmentExtractionEnabled;

    // GUI settings
    private boolean guiEnabled;
    private boolean guiOpenOnCommand;
    private String guiTitle;
    private int guiMainMenuSize;
    private int guiItemsPerPage;
    private int guiUpdateInterval;
    private Map<String, String> guiSounds;
    private Map<String, String> guiCategoryIcons;
    private Map<String, List<String>> categoryItems;

    public CoinOpConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.priceBounds = new PriceBounds();
        this.allowedCommodities = ConcurrentHashMap.newKeySet();
        this.commodityCategories = new ConcurrentHashMap<>();
        this.dailyTradeLimits = new ConcurrentHashMap<>();

        // Defaults
        this.globalDailyTradeLimit = 0;
        this.taxRate = 0.0;
        this.minOrderAmount = 1;
        this.maxOrderAmount = Integer.MAX_VALUE;
        this.syncEnabled = false;
        this.loreEnabled = true;
        this.loreUpdateInterval = 20;
        this.anvilReworkEnabled = true;
        this.enchantmentExtractionEnabled = true;
        this.guiEnabled = true;
        this.guiOpenOnCommand = true;
        this.guiTitle = "&6CoinOp &8- &7Market";
        this.guiMainMenuSize = 54;
        this.guiItemsPerPage = 45;
        this.guiUpdateInterval = 20;
        this.guiSounds = new ConcurrentHashMap<>();
        this.guiCategoryIcons = new ConcurrentHashMap<>();
        this.categoryItems = new ConcurrentHashMap<>();
    }

    public void load() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // Economy
        this.taxRate = config.getDouble("economy.tax-rate", 0.0);
        this.minOrderAmount = config.getLong("orders.min-amount", 1);
        this.maxOrderAmount = config.getLong("orders.max-amount", Integer.MAX_VALUE);
        this.globalDailyTradeLimit = config.getLong("limits.global-daily-limit", 0);

        // Allowed commodities (empty = all items)
        List<String> commodities = config.getStringList("commodities.allowed");
        if (commodities != null && !commodities.isEmpty()) {
            allowedCommodities.clear();
            allowedCommodities.addAll(commodities);
        }

        // Categories
        if (config.contains("commodities.categories")) {
            for (String category : config.getConfigurationSection("commodities.categories").getKeys(false)) {
                List<String> items = config.getStringList("commodities.categories." + category);
                for (String item : items) {
                    commodityCategories.put(item, category);
                }
            }
        }

        // Daily limits per commodity
        if (config.contains("limits.daily-limits")) {
            for (String commodity : config.getConfigurationSection("limits.daily-limits").getKeys(false)) {
                long limit = config.getLong("limits.daily-limits." + commodity);
                dailyTradeLimits.put(commodity, limit);
            }
        }

        // Price bounds
        if (config.contains("price-bounds")) {
            Map<String, Object> boundsConfig = new HashMap<>();
            for (String key : config.getConfigurationSection("price-bounds").getKeys(false)) {
                boundsConfig.put(key, config.get("price-bounds." + key));
            }
            priceBounds.loadFromConfig(boundsConfig);
        }

        // Redis
        this.syncEnabled = config.getBoolean("sync.enabled", false);
        this.redisHost = config.getString("sync.redis.host", "localhost");
        this.redisPort = config.getInt("sync.redis.port", 6379);
        this.redisPassword = config.getString("sync.redis.password", "");

        // Database
        this.databaseType = config.getString("database.type", "sqlite");
        this.databaseHost = config.getString("database.host", "localhost");
        this.databasePort = config.getInt("database.port", 3306);
        this.databaseName = config.getString("database.name", "coinop");
        this.databaseUser = config.getString("database.user", "root");
        this.databasePassword = config.getString("database.password", "");

        // Lore
        this.loreEnabled = config.getBoolean("lore.enabled", true);
        this.loreUpdateInterval = config.getInt("lore.update-interval", 20);

        // Anvil
        this.anvilReworkEnabled = config.getBoolean("anvil-rework.enabled", true);
        this.enchantmentExtractionEnabled = config.getBoolean("anvil-rework.enchantment-extraction", true);

        // GUI
        this.guiEnabled = config.getBoolean("gui.enabled", true);
        this.guiOpenOnCommand = config.getBoolean("gui.open-on-command", true);
        this.guiTitle = config.getString("gui.title", "&6CoinOp &8- &7Market");
        this.guiMainMenuSize = config.getInt("gui.main-menu-size", 54);
        this.guiItemsPerPage = config.getInt("gui.items-per-page", 45);
        this.guiUpdateInterval = config.getInt("gui.update-interval", 20);

        // GUI sounds
        if (config.contains("gui.sounds")) {
            for (String key : config.getConfigurationSection("gui.sounds").getKeys(false)) {
                guiSounds.put(key, config.getString("gui.sounds." + key));
            }
        }

        // Category icons
        if (config.contains("gui.icons")) {
            for (String key : config.getConfigurationSection("gui.icons").getKeys(false)) {
                guiCategoryIcons.put(key, config.getString("gui.icons." + key));
            }
        }

        // Category items for browsing
        if (config.contains("commodities.categories")) {
            for (String category : config.getConfigurationSection("commodities.categories").getKeys(false)) {
                List<String> items = config.getStringList("commodities.categories." + category);
                categoryItems.put(category, items);
            }
        }
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();

        config.set("economy.tax-rate", taxRate);
        config.set("orders.min-amount", minOrderAmount);
        config.set("orders.max-amount", maxOrderAmount);
        config.set("limits.global-daily-limit", globalDailyTradeLimit);
        config.set("commodities.allowed", new ArrayList<>(allowedCommodities));

        for (Map.Entry<String, Long> entry : dailyTradeLimits.entrySet()) {
            config.set("limits.daily-limits." + entry.getKey(), entry.getValue());
        }

        config.set("price-bounds", priceBounds.exportToConfig());
        config.set("sync.enabled", syncEnabled);
        config.set("sync.redis.host", redisHost);
        config.set("sync.redis.port", redisPort);
        config.set("sync.redis.password", redisPassword);
        config.set("database.type", databaseType);
        config.set("database.host", databaseHost);
        config.set("database.port", databasePort);
        config.set("database.name", databaseName);
        config.set("database.user", databaseUser);
        config.set("database.password", databasePassword);
        config.set("lore.enabled", loreEnabled);
        config.set("lore.update-interval", loreUpdateInterval);
        config.set("anvil-rework.enabled", anvilReworkEnabled);
        config.set("anvil-rework.enchantment-extraction", enchantmentExtractionEnabled);

        plugin.saveConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    // Getters

    public PriceBounds getPriceBounds() {
        return priceBounds;
    }

    public Set<String> getAllowedCommodities() {
        return Collections.unmodifiableSet(allowedCommodities);
    }

    public String getCategory(String commodityId) {
        return commodityCategories.get(commodityId);
    }

    public Map<String, String> getCommodityCategories() {
        return Collections.unmodifiableMap(commodityCategories);
    }

    public long getDailyTradeLimit(String commodityId) {
        Long limit = dailyTradeLimits.get(commodityId);
        return limit != null ? limit : globalDailyTradeLimit;
    }

    public long getGlobalDailyTradeLimit() {
        return globalDailyTradeLimit;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public long getMinOrderAmount() {
        return minOrderAmount;
    }

    public long getMaxOrderAmount() {
        return maxOrderAmount;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public boolean isLoreEnabled() {
        return loreEnabled;
    }

    public int getLoreUpdateInterval() {
        return loreUpdateInterval;
    }

    public boolean isAnvilReworkEnabled() {
        return anvilReworkEnabled;
    }

    public boolean isEnchantmentExtractionEnabled() {
        return enchantmentExtractionEnabled;
    }

    // GUI getters

    public boolean isGuiEnabled() {
        return guiEnabled;
    }

    public boolean isGuiOpenOnCommand() {
        return guiOpenOnCommand;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiMainMenuSize() {
        return guiMainMenuSize;
    }

    public int getGuiItemsPerPage() {
        return guiItemsPerPage;
    }

    public int getGuiUpdateInterval() {
        return guiUpdateInterval;
    }

    public String getGuiSound(String soundType) {
        return guiSounds.get(soundType);
    }

    public String getGuiCategoryIcon(String category) {
        return guiCategoryIcons.get(category);
    }

    public List<String> getCommoditiesInCategory(String category) {
        return categoryItems.getOrDefault(category, Collections.emptyList());
    }

    public Map<String, String> getCategories() {
        return Collections.unmodifiableMap(guiCategoryIcons);
    }

    // Setters

    public void setTaxRate(double taxRate) {
        this.taxRate = Math.max(0, Math.min(1, taxRate));
    }

    public void setMinOrderAmount(long minOrderAmount) {
        this.minOrderAmount = Math.max(1, minOrderAmount);
    }

    public void setMaxOrderAmount(long maxOrderAmount) {
        this.maxOrderAmount = Math.max(minOrderAmount, maxOrderAmount);
    }

    public void setGlobalDailyTradeLimit(long limit) {
        this.globalDailyTradeLimit = Math.max(0, limit);
    }

    public void setDailyTradeLimit(String commodityId, long limit) {
        if (limit <= 0) {
            dailyTradeLimits.remove(commodityId);
        } else {
            dailyTradeLimits.put(commodityId, limit);
        }
    }

    public Map<String, Long> getAllDailyTradeLimits() {
        return Collections.unmodifiableMap(dailyTradeLimits);
    }

    public void addAllowedCommodity(String commodityId) {
        allowedCommodities.add(commodityId);
    }

    public void removeAllowedCommodity(String commodityId) {
        allowedCommodities.remove(commodityId);
    }

    public void setCategory(String commodityId, String category) {
        commodityCategories.put(commodityId, category);
    }

    public void setSyncEnabled(boolean enabled) {
        this.syncEnabled = enabled;
    }

    public void setLoreEnabled(boolean enabled) {
        this.loreEnabled = enabled;
    }

    public void setAnvilReworkEnabled(boolean enabled) {
        this.anvilReworkEnabled = enabled;
    }

    public void setEnchantmentExtractionEnabled(boolean enabled) {
        this.enchantmentExtractionEnabled = enabled;
    }
}
