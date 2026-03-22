package com.coinop.engine;

import com.coinop.config.PriceBounds;
import com.coinop.model.Order;
import com.coinop.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Unit tests for the MatchingEngine class.
 */
class MatchingEngineTest {

    private MatchingEngine engine;
    private UUID buyer;
    private UUID seller;
    private PriceBounds priceBounds;

    @BeforeEach
    void setUp() {
        priceBounds = new PriceBounds();
        priceBounds.setEnabled(true);
        priceBounds.setGlobalMinPrice(1.0);
        priceBounds.setGlobalMaxPrice(1000000.0);

        engine = new MatchingEngine(null, priceBounds);
        buyer = UUID.randomUUID();
        seller = UUID.randomUUID();
    }

    @Test
    @DisplayName("Simple buy order matches against existing sell order")
    void testSimpleMatch() {
        // Place a sell order first
        Order sellOrder = new Order.Builder()
                .playerUuid(seller)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        engine.processOrder(sellOrder);

        // Place a buy order that matches
        Order buyOrder = new Order.Builder()
                .playerUuid(buyer)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(buyOrder);

        assertFalse(result.isRejected());
        assertTrue(result.isFullyFilled());
        assertEquals(10, result.getFilledAmount());
        assertEquals(100.0, result.getAveragePrice());
    }

    @Test
    @DisplayName("Buy order at higher price still matches at sell price")
    void testPriceImprovement() {
        // Sell at 100
        Order sellOrder = new Order.Builder()
                .playerUuid(seller)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        engine.processOrder(sellOrder);

        // Buy at 110 (willing to pay more)
        Order buyOrder = new Order.Builder()
                .playerUuid(buyer)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(110.0)
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(buyOrder);

        assertTrue(result.isFullyFilled());
        // Should execute at the sell price (100), not the buy price (110)
        assertEquals(100.0, result.getAveragePrice());
    }

    @Test
    @DisplayName("Partial fill when order sizes differ")
    void testPartialFill() {
        // Sell 5 items
        Order sellOrder = new Order.Builder()
                .playerUuid(seller)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(5)
                .build();

        engine.processOrder(sellOrder);

        // Try to buy 10 items
        Order buyOrder = new Order.Builder()
                .playerUuid(buyer)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(buyOrder);

        assertTrue(result.isPartiallyFilled());
        assertEquals(5, result.getFilledAmount());
        assertEquals(5, result.getUnfilledAmount());
    }

    @Test
    @DisplayName("No match when prices don't cross")
    void testNoMatch() {
        // Sell at 100
        Order sellOrder = new Order.Builder()
                .playerUuid(seller)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        engine.processOrder(sellOrder);

        // Buy at 90 (below sell price)
        Order buyOrder = new Order.Builder()
                .playerUuid(buyer)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(90.0)
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(buyOrder);

        // Should not match, but should be placed in order book
        assertFalse(result.isRejected());
        assertEquals(0, result.getFilledAmount());
        assertEquals(10, result.getUnfilledAmount());
    }

    @Test
    @DisplayName("Order rejected when price outside bounds")
    void testPriceBoundsRejection() {
        priceBounds.setBounds("DIAMOND", 50.0, 200.0);

        // Try to sell below min
        Order lowSell = new Order.Builder()
                .playerUuid(seller)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(30.0)
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(lowSell);
        assertTrue(result.isRejected());
        assertEquals("Price outside allowed bounds", result.getRejectionReason());
    }

    @Test
    @DisplayName("Instant buy executes at best available price")
    void testInstantBuy() {
        // Place multiple sell orders at different prices
        engine.processOrder(createSellOrder(seller, 110.0, 5));
        engine.processOrder(createSellOrder(seller, 100.0, 5));
        engine.processOrder(createSellOrder(seller, 120.0, 5));

        // Instant buy should match against the best (lowest) price first
        MatchingEngine.MatchResult result = engine.instantBuy(buyer, "DIAMOND", 5);

        assertTrue(result.isFullyFilled());
        assertEquals(100.0, result.getAveragePrice()); // Best price
    }

    @Test
    @DisplayName("Instant sell executes at best available price")
    void testInstantSell() {
        // Place multiple buy orders at different prices
        engine.processOrder(createBuyOrder(buyer, 90.0, 5));
        engine.processOrder(createBuyOrder(buyer, 100.0, 5));
        engine.processOrder(createBuyOrder(buyer, 80.0, 5));

        // Instant sell should match against the best (highest) price first
        MatchingEngine.MatchResult result = engine.instantSell(seller, "DIAMOND", 5);

        assertTrue(result.isFullyFilled());
        assertEquals(100.0, result.getAveragePrice()); // Best price
    }

    @Test
    @DisplayName("Instant buy rejected when no sell orders")
    void testInstantBuyNoSellers() {
        MatchingEngine.MatchResult result = engine.instantBuy(buyer, "DIAMOND", 10);

        assertTrue(result.isRejected());
        assertEquals("No sell orders available", result.getRejectionReason());
    }

    @Test
    @DisplayName("Cancel order removes from book")
    void testCancelOrder() {
        Order sellOrder = createSellOrder(seller, 100.0, 10);
        MatchingEngine.MatchResult result = engine.processOrder(sellOrder);

        var book = engine.getOrderBook("DIAMOND");
        assertEquals(100.0, book.getBestSellPrice());

        // The order in the book has a different ID (created during processing)
        // Get the actual order from the book
        var orders = book.getPlayerOrders(seller);
        assertFalse(orders.isEmpty(), "Order should be in the book");

        Order bookOrder = orders.iterator().next();
        boolean cancelled = engine.cancelOrder("DIAMOND", bookOrder.getOrderId());
        assertTrue(cancelled);
        assertEquals(Double.MAX_VALUE, book.getBestSellPrice());
    }

    @Test
    @DisplayName("Multiple matches at different price levels")
    void testMultiplePriceLevels() {
        // Place sell orders at different prices
        engine.processOrder(createSellOrder(seller, 100.0, 5));
        engine.processOrder(createSellOrder(seller, 110.0, 5));
        engine.processOrder(createSellOrder(seller, 120.0, 5));

        // Buy order that matches multiple levels
        Order buyOrder = new Order.Builder()
                .playerUuid(buyer)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(115.0) // Can match 100 and 110, but not 120
                .amount(10)
                .build();

        MatchingEngine.MatchResult result = engine.processOrder(buyOrder);

        assertTrue(result.isFullyFilled());
        // Average price: (5*100 + 5*110) / 10 = 105
        assertEquals(105.0, result.getAveragePrice());
    }

    @Test
    @DisplayName("FIFO ordering for same price")
    void testFIFOOrdering() {
        UUID seller1 = UUID.randomUUID();
        UUID seller2 = UUID.randomUUID();

        // Place two sell orders at same price
        Order sell1 = createSellOrder(seller1, 100.0, 5);
        engine.processOrder(sell1);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        Order sell2 = createSellOrder(seller2, 100.0, 5);
        engine.processOrder(sell2);

        // Buy should match first seller first (FIFO)
        MatchingEngine.MatchResult result = engine.instantBuy(buyer, "DIAMOND", 5);

        assertTrue(result.isFullyFilled());
        // First seller's order should be filled
        var trades = result.getTrades();
        assertEquals(1, trades.size());
        assertEquals(seller1, trades.get(0).getSellerUuid());
    }

    private Order createBuyOrder(UUID player, double price, long amount) {
        return new Order.Builder()
                .playerUuid(player)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(price)
                .amount(amount)
                .build();
    }

    private Order createSellOrder(UUID player, double price, long amount) {
        return new Order.Builder()
                .playerUuid(player)
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(price)
                .amount(amount)
                .build();
    }
}
