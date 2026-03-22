package com.coinop.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Unit tests for the OrderBook class.
 */
class OrderBookTest {

    private OrderBook orderBook;
    private UUID player1;
    private UUID player2;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("DIAMOND");
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
    }

    @Test
    @DisplayName("OrderBook initializes correctly")
    void testInitialization() {
        assertEquals("DIAMOND", orderBook.getCommodityId());
        assertEquals(0, orderBook.getBestBuyPrice());
        assertEquals(Double.MAX_VALUE, orderBook.getBestSellPrice());
        assertEquals(0, orderBook.getBuyOrderCount());
        assertEquals(0, orderBook.getSellOrderCount());
    }

    @Test
    @DisplayName("Adding buy orders updates best price")
    void testAddBuyOrder() {
        Order buy1 = createBuyOrder(player1, 100.0, 10);
        Order buy2 = createBuyOrder(player2, 150.0, 5);

        orderBook.addOrder(buy1);
        assertEquals(100.0, orderBook.getBestBuyPrice());

        orderBook.addOrder(buy2);
        assertEquals(150.0, orderBook.getBestBuyPrice()); // Higher price is best
    }

    @Test
    @DisplayName("Adding sell orders updates best price")
    void testAddSellOrder() {
        Order sell1 = createSellOrder(player1, 200.0, 10);
        Order sell2 = createSellOrder(player2, 150.0, 5);

        orderBook.addOrder(sell1);
        assertEquals(200.0, orderBook.getBestSellPrice());

        orderBook.addOrder(sell2);
        assertEquals(150.0, orderBook.getBestSellPrice()); // Lower price is best
    }

    @Test
    @DisplayName("Spread calculation is correct")
    void testSpread() {
        Order buy = createBuyOrder(player1, 100.0, 10);
        Order sell = createSellOrder(player2, 120.0, 10);

        orderBook.addOrder(buy);
        orderBook.addOrder(sell);

        assertEquals(20.0, orderBook.getSpread());
    }

    @Test
    @DisplayName("Mid price calculation is correct")
    void testMidPrice() {
        Order buy = createBuyOrder(player1, 100.0, 10);
        Order sell = createSellOrder(player2, 110.0, 10);

        orderBook.addOrder(buy);
        orderBook.addOrder(sell);

        assertEquals(105.0, orderBook.getMidPrice());
    }

    @Test
    @DisplayName("Remove order works correctly")
    void testRemoveOrder() {
        Order buy = createBuyOrder(player1, 100.0, 10);
        orderBook.addOrder(buy);

        assertEquals(100.0, orderBook.getBestBuyPrice());

        Order removed = orderBook.removeOrder(buy.getOrderId());

        assertNotNull(removed);
        assertEquals(buy.getOrderId(), removed.getOrderId());
        assertEquals(0, orderBook.getBestBuyPrice());
    }

    @Test
    @DisplayName("Get player orders returns correct orders")
    void testGetPlayerOrders() {
        Order buy1 = createBuyOrder(player1, 100.0, 10);
        Order buy2 = createBuyOrder(player1, 90.0, 5);
        Order buy3 = createBuyOrder(player2, 80.0, 8);

        orderBook.addOrder(buy1);
        orderBook.addOrder(buy2);
        orderBook.addOrder(buy3);

        var player1Orders = orderBook.getPlayerOrders(player1);
        assertEquals(2, player1Orders.size());

        var player2Orders = orderBook.getPlayerOrders(player2);
        assertEquals(1, player2Orders.size());
    }

    @Test
    @DisplayName("Total volume calculation is correct")
    void testTotalVolume() {
        orderBook.addOrder(createBuyOrder(player1, 100.0, 10));
        orderBook.addOrder(createBuyOrder(player2, 90.0, 20));
        orderBook.addOrder(createSellOrder(player1, 110.0, 15));
        orderBook.addOrder(createSellOrder(player2, 120.0, 25));

        assertEquals(30, orderBook.getTotalBuyVolume());
        assertEquals(40, orderBook.getTotalSellVolume());
    }

    @Test
    @DisplayName("Trade recording updates statistics")
    void testTradeRecording() {
        assertEquals(0, orderBook.get24hVolume());
        assertEquals(0, orderBook.get24hVWAP());

        orderBook.recordTrade(100.0, 10);
        orderBook.recordTrade(110.0, 10);

        assertEquals(20, orderBook.get24hVolume());
        assertEquals(105.0, orderBook.get24hVWAP()); // (100*10 + 110*10) / 20
    }

    @Test
    @DisplayName("Cannot add order for different commodity")
    void testWrongCommodity() {
        Order wrongOrder = new Order.Builder()
                .playerUuid(player1)
                .commodityId("IRON_INGOT")
                .type(Order.OrderType.BUY)
                .pricePerUnit(10.0)
                .amount(10)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            orderBook.addOrder(wrongOrder);
        });
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
