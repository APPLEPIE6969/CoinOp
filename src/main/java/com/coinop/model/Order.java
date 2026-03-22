package com.coinop.model;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

// Order in the order book - can be buy or sell
public class Order implements Comparable<Order> {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

    private final long orderId;
    private final UUID playerUuid;
    private final String commodityId;
    private final OrderType type;
    private final double pricePerUnit;
    private long amount;
    private long filledAmount;
    private final long createdAt;
    private long updatedAt;
    private volatile boolean active;
    private final boolean instant;

    public enum OrderType {
        BUY,
        SELL
    }

    private Order(Builder builder) {
        this.orderId = ID_GENERATOR.incrementAndGet();
        this.playerUuid = builder.playerUuid;
        this.commodityId = builder.commodityId;
        this.type = builder.type;
        this.pricePerUnit = builder.pricePerUnit;
        this.amount = builder.amount;
        this.filledAmount = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.active = true;
        this.instant = builder.instant;
    }

    public long getOrderId() {
        return orderId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public OrderType getType() {
        return type;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public synchronized long getAmount() {
        return amount;
    }

    public synchronized long getFilledAmount() {
        return filledAmount;
    }

    public synchronized long getRemainingAmount() {
        return amount - filledAmount;
    }

    public synchronized double getTotalValue() {
        return pricePerUnit * getRemainingAmount();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active && getRemainingAmount() > 0;
    }

    public boolean isInstant() {
        return instant;
    }

    public synchronized long fill(long fillAmount) {
        long remaining = getRemainingAmount();
        long actualFill = Math.min(fillAmount, remaining);
        this.filledAmount += actualFill;
        this.updatedAt = System.currentTimeMillis();

        if (getRemainingAmount() <= 0) {
            this.active = false;
        }

        return actualFill;
    }

    public synchronized void cancel() {
        this.active = false;
        this.updatedAt = System.currentTimeMillis();
    }

    public synchronized void reduceAmount(long reduceAmount) {
        this.amount = Math.max(filledAmount, this.amount - reduceAmount);
        this.updatedAt = System.currentTimeMillis();
    }

    // Price-time priority: best price first, then FIFO
    @Override
    public int compareTo(Order other) {
        if (this.type != other.type) {
            throw new IllegalArgumentException("Cannot compare BUY and SELL orders");
        }

        int priceComparison;
        if (this.type == OrderType.BUY) {
            // Buy orders: higher price = higher priority
            priceComparison = Double.compare(other.pricePerUnit, this.pricePerUnit);
        } else {
            // Sell orders: lower price = higher priority
            priceComparison = Double.compare(this.pricePerUnit, other.pricePerUnit);
        }

        if (priceComparison != 0) {
            return priceComparison;
        }

        // Same price: earlier time wins (FIFO)
        return Long.compare(this.createdAt, other.createdAt);
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, type=%s, commodity=%s, price=%.2f, remaining=%d, active=%s}",
                orderId, type, commodityId, pricePerUnit, getRemainingAmount(), active);
    }

    public static class Builder {
        private UUID playerUuid;
        private String commodityId;
        private OrderType type;
        private double pricePerUnit;
        private long amount;
        private boolean instant = false;

        public Builder playerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
            return this;
        }

        public Builder commodityId(String commodityId) {
            this.commodityId = commodityId;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder pricePerUnit(double pricePerUnit) {
            this.pricePerUnit = pricePerUnit;
            return this;
        }

        public Builder amount(long amount) {
            this.amount = amount;
            return this;
        }

        public Builder instant(boolean instant) {
            this.instant = instant;
            return this;
        }

        public Order build() {
            if (playerUuid == null) {
                throw new IllegalStateException("playerUuid is required");
            }
            if (commodityId == null || commodityId.isEmpty()) {
                throw new IllegalStateException("commodityId is required");
            }
            if (type == null) {
                throw new IllegalStateException("type is required");
            }
            if (pricePerUnit <= 0) {
                throw new IllegalStateException("pricePerUnit must be positive");
            }
            if (amount <= 0) {
                throw new IllegalStateException("amount must be positive");
            }
            return new Order(this);
        }
    }
}
