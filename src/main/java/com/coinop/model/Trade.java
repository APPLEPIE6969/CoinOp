package com.coinop.model;

import java.util.UUID;

// Completed trade between buyer and seller
public class Trade {

    private final long tradeId;
    private final String commodityId;
    private final UUID buyerUuid;
    private final UUID sellerUuid;
    private final long amount;
    private final double price;
    private final double totalValue;
    private final long timestamp;

    public Trade(long tradeId, String commodityId, UUID buyerUuid, UUID sellerUuid,
            long amount, double price, long timestamp) {
        this.tradeId = tradeId;
        this.commodityId = commodityId;
        this.buyerUuid = buyerUuid;
        this.sellerUuid = sellerUuid;
        this.amount = amount;
        this.price = price;
        this.totalValue = amount * price;
        this.timestamp = timestamp;
    }

    public long getTradeId() {
        return tradeId;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public UUID getBuyerUuid() {
        return buyerUuid;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public long getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, commodity=%s, buyer=%s, seller=%s, amount=%d, price=%.2f}",
                tradeId, commodityId, buyerUuid, sellerUuid, amount, price);
    }
}
