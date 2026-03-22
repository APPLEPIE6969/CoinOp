package com.coinop.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

// Price bounds to prevent market manipulation
// Sets min/max prices per commodity or globally
public class PriceBounds {

    private static final double DEFAULT_MIN_PRICE = 0.01;
    private static final double DEFAULT_MAX_PRICE = 1_000_000_000.0; // 1 billion

    // commodityId -> [minPrice, maxPrice]
    private final Map<String, double[]> bounds;

    private double globalMinPrice;
    private double globalMaxPrice;
    private boolean enabled;

    public PriceBounds() {
        this.bounds = new ConcurrentHashMap<>();
        this.globalMinPrice = DEFAULT_MIN_PRICE;
        this.globalMaxPrice = DEFAULT_MAX_PRICE;
        this.enabled = true;
    }

    public boolean isWithinBounds(String commodityId, double price) {
        if (!enabled)
            return true;

        double[] commodityBounds = bounds.get(commodityId);
        double minPrice = commodityBounds != null ? commodityBounds[0] : globalMinPrice;
        double maxPrice = commodityBounds != null ? commodityBounds[1] : globalMaxPrice;

        return price >= minPrice && price <= maxPrice;
    }

    public double getMinPrice(String commodityId) {
        if (!enabled)
            return 0;

        double[] commodityBounds = bounds.get(commodityId);
        return commodityBounds != null ? commodityBounds[0] : globalMinPrice;
    }

    public double getMaxPrice(String commodityId) {
        if (!enabled)
            return Double.MAX_VALUE;

        double[] commodityBounds = bounds.get(commodityId);
        return commodityBounds != null ? commodityBounds[1] : globalMaxPrice;
    }

    public void setBounds(String commodityId, double minPrice, double maxPrice) {
        if (minPrice > maxPrice) {
            throw new IllegalArgumentException("Min price can't exceed max price");
        }
        bounds.put(commodityId, new double[] { minPrice, maxPrice });
    }

    public void removeBounds(String commodityId) {
        bounds.remove(commodityId);
    }

    public Map<String, double[]> getAllBounds() {
        return java.util.Collections.unmodifiableMap(bounds);
    }

    public void setGlobalMinPrice(double minPrice) {
        this.globalMinPrice = minPrice;
    }

    public void setGlobalMaxPrice(double maxPrice) {
        this.globalMaxPrice = maxPrice;
    }

    public double getGlobalMinPrice() {
        return globalMinPrice;
    }

    public double getGlobalMaxPrice() {
        return globalMaxPrice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @SuppressWarnings("unchecked")
    public void loadFromConfig(Map<String, Object> configMap) {
        if (configMap == null)
            return;

        // Global bounds
        if (configMap.containsKey("global-min")) {
            this.globalMinPrice = ((Number) configMap.get("global-min")).doubleValue();
        }
        if (configMap.containsKey("global-max")) {
            this.globalMaxPrice = ((Number) configMap.get("global-max")).doubleValue();
        }
        if (configMap.containsKey("enabled")) {
            this.enabled = (Boolean) configMap.get("enabled");
        }

        // Per-commodity bounds
        if (configMap.containsKey("commodities")) {
            Object commoditiesObj = configMap.get("commodities");

            // Bukkit MemorySection
            if (commoditiesObj instanceof MemorySection) {
                MemorySection commoditiesSection = (MemorySection) commoditiesObj;
                for (String commodityId : commoditiesSection.getKeys(false)) {
                    Object boundsObj = commoditiesSection.get(commodityId);
                    if (boundsObj instanceof MemorySection) {
                        MemorySection boundsSection = (MemorySection) boundsObj;
                        double min = boundsSection.contains("min")
                                ? boundsSection.getDouble("min")
                                : globalMinPrice;
                        double max = boundsSection.contains("max")
                                ? boundsSection.getDouble("max")
                                : globalMaxPrice;
                        setBounds(commodityId, min, max);
                    }
                }
            } else if (commoditiesObj instanceof Map) {
                // Regular Map
                Map<String, Object> commodities = (Map<String, Object>) commoditiesObj;
                for (Map.Entry<String, Object> entry : commodities.entrySet()) {
                    String commodityId = entry.getKey();
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> boundsMap = (Map<String, Object>) entry.getValue();
                        double min = boundsMap.containsKey("min")
                                ? ((Number) boundsMap.get("min")).doubleValue()
                                : globalMinPrice;
                        double max = boundsMap.containsKey("max")
                                ? ((Number) boundsMap.get("max")).doubleValue()
                                : globalMaxPrice;
                        setBounds(commodityId, min, max);
                    }
                }
            }
        }
    }

    public Map<String, Object> exportToConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", enabled);
        config.put("global-min", globalMinPrice);
        config.put("global-max", globalMaxPrice);

        Map<String, Object> commodities = new HashMap<>();
        for (Map.Entry<String, double[]> entry : bounds.entrySet()) {
            Map<String, Object> boundMap = new HashMap<>();
            boundMap.put("min", entry.getValue()[0]);
            boundMap.put("max", entry.getValue()[1]);
            commodities.put(entry.getKey(), boundMap);
        }
        config.put("commodities", commodities);

        return config;
    }
}
