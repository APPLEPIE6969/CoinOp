package com.coinop.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

/**
 * Unit tests for the Order class.
 */
class OrderTest {

    @Test
    @DisplayName("Order builder creates valid order")
    void testOrderBuilder() {
        UUID playerUuid = UUID.randomUUID();
        Order order = new Order.Builder()
                .playerUuid(playerUuid)
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(64)
                .build();

        assertNotNull(order);
        assertEquals(playerUuid, order.getPlayerUuid());
        assertEquals("DIAMOND", order.getCommodityId());
        assertEquals(Order.OrderType.BUY, order.getType());
        assertEquals(100.0, order.getPricePerUnit());
        assertEquals(64, order.getAmount());
        assertEquals(64, order.getRemainingAmount());
        assertTrue(order.isActive());
    }

    @Test
    @DisplayName("Order fill reduces remaining amount")
    void testOrderFill() {
        Order order = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("COBBLESTONE")
                .type(Order.OrderType.SELL)
                .pricePerUnit(1.0)
                .amount(100)
                .build();

        long filled = order.fill(30);
        assertEquals(30, filled);
        assertEquals(70, order.getRemainingAmount());
        assertTrue(order.isActive());

        filled = order.fill(70);
        assertEquals(70, filled);
        assertEquals(0, order.getRemainingAmount());
        assertFalse(order.isActive());
    }

    @Test
    @DisplayName("Order fill handles overfill gracefully")
    void testOrderOverfill() {
        Order order = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("IRON_INGOT")
                .type(Order.OrderType.BUY)
                .pricePerUnit(10.0)
                .amount(50)
                .build();

        // Try to fill more than available
        long filled = order.fill(100);
        assertEquals(50, filled); // Only fills what's available
        assertEquals(0, order.getRemainingAmount());
        assertFalse(order.isActive());
    }

    @Test
    @DisplayName("Buy orders compare correctly (higher price = higher priority)")
    void testBuyOrderComparison() {
        Order order1 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        // Wait a bit to ensure different timestamp
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        Order order2 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(200.0)
                .amount(10)
                .build();

        // Higher price should have higher priority (negative comparison)
        assertTrue(order2.compareTo(order1) < 0, "Higher price buy order should have higher priority");
    }

    @Test
    @DisplayName("Sell orders compare correctly (lower price = higher priority)")
    void testSellOrderComparison() {
        Order order1 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        Order order2 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(50.0)
                .amount(10)
                .build();

        // Lower price should have higher priority (negative comparison)
        assertTrue(order2.compareTo(order1) < 0, "Lower price sell order should have higher priority");
    }

    @Test
    @DisplayName("FIFO ordering for same price orders")
    void testFIFOOrdering() {
        Order order1 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        Order order2 = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        // Earlier order should have higher priority
        assertTrue(order1.compareTo(order2) < 0, "Earlier order should have higher priority (FIFO)");
    }

    @Test
    @DisplayName("Cannot compare BUY and SELL orders")
    void testCannotCompareDifferentTypes() {
        Order buyOrder = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        Order sellOrder = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.SELL)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            buyOrder.compareTo(sellOrder);
        });
    }

    @Test
    @DisplayName("Order cancel marks as inactive")
    void testOrderCancel() {
        Order order = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(10)
                .build();

        assertTrue(order.isActive());
        order.cancel();
        assertFalse(order.isActive());
    }

    @Test
    @DisplayName("Total value calculation is correct")
    void testTotalValue() {
        Order order = new Order.Builder()
                .playerUuid(UUID.randomUUID())
                .commodityId("DIAMOND")
                .type(Order.OrderType.BUY)
                .pricePerUnit(100.0)
                .amount(64)
                .build();

        assertEquals(6400.0, order.getTotalValue());

        order.fill(32);
        assertEquals(3200.0, order.getTotalValue());
    }
}
