package com.coinop.sync;

import com.coinop.model.Order;
import com.coinop.model.Trade;
import com.coinop.engine.MatchingEngine;
import com.coinop.config.CoinOpConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Multi-server sync via Redis Pub/Sub - keeps order books in sync across servers
public class SyncManager {

    private static final String TRADE_CHANNEL = "coinop:trades";
    private static final String ORDER_CHANNEL = "coinop:orders";
    private static final String CANCEL_CHANNEL = "coinop:cancel";
    private static final String SYNC_CHANNEL = "coinop:sync";

    private final JavaPlugin plugin;
    private final CoinOpConfig config;
    private final MatchingEngine matchingEngine;
    private JedisPool jedisPool;
    private final ExecutorService executor;
    private final Gson gson;
    private final String serverId;
    private volatile boolean enabled;
    private Thread subscriberThread;

    private Consumer<SyncTradeMessage> tradeHandler;
    private Consumer<SyncOrderMessage> orderHandler;

    public SyncManager(JavaPlugin plugin, CoinOpConfig config, MatchingEngine matchingEngine) {
        this.plugin = plugin;
        this.config = config;
        this.matchingEngine = matchingEngine;
        this.executor = Executors.newFixedThreadPool(5);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        this.serverId = UUID.randomUUID().toString();
        this.enabled = false;
    }

    public boolean initialize() {
        if (!config.isSyncEnabled()) {
            plugin.getLogger().info("Multi-server sync is disabled.");
            return false;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMaxWaitMillis(5000);

            String password = config.getRedisPassword();
            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(),
                        config.getRedisPort(), 5000, password);
            } else {
                jedisPool = new JedisPool(poolConfig, config.getRedisHost(),
                        config.getRedisPort(), 5000);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            enabled = true;
            startSubscriber();

            plugin.getLogger().info("Connected to Redis at " + config.getRedisHost() + ":" + config.getRedisPort());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Redis: " + e.getMessage());
            return false;
        }
    }

    private void startSubscriber() {
        subscriberThread = new Thread(() -> {
            while (enabled && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            handleMessage(channel, message);
                        }
                    }, TRADE_CHANNEL, ORDER_CHANNEL, CANCEL_CHANNEL, SYNC_CHANNEL);
                } catch (Exception e) {
                    if (enabled) {
                        plugin.getLogger().warning("Redis connection lost, reconnecting in 5 seconds...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }, "CoinOp-Sync-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void handleMessage(String channel, String message) {
        executor.submit(() -> {
            try {
                switch (channel) {
                    case TRADE_CHANNEL:
                        SyncTradeMessage tradeMsg = gson.fromJson(message, SyncTradeMessage.class);
                        if (!tradeMsg.serverId.equals(serverId) && tradeHandler != null) {
                            tradeHandler.accept(tradeMsg);
                        }
                        break;

                    case ORDER_CHANNEL:
                        SyncOrderMessage orderMsg = gson.fromJson(message, SyncOrderMessage.class);
                        if (!orderMsg.serverId.equals(serverId) && orderHandler != null) {
                            orderHandler.accept(orderMsg);
                        }
                        break;

                    case CANCEL_CHANNEL:
                        SyncCancelMessage cancelMsg = gson.fromJson(message, SyncCancelMessage.class);
                        if (!cancelMsg.serverId.equals(serverId)) {
                            matchingEngine.cancelOrder(cancelMsg.commodityId, cancelMsg.orderId);
                        }
                        break;

                    case SYNC_CHANNEL:
                        // Full sync requests - not implemented yet
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error handling sync message: " + e.getMessage());
            }
        });
    }

    public void broadcastTrade(Trade trade) {
        if (!enabled)
            return;

        SyncTradeMessage message = new SyncTradeMessage(
                serverId,
                trade.getTradeId(),
                trade.getCommodityId(),
                trade.getBuyerUuid(),
                trade.getSellerUuid(),
                trade.getAmount(),
                trade.getPrice(),
                trade.getTimestamp());

        publish(TRADE_CHANNEL, gson.toJson(message));
    }

    public void broadcastOrder(Order order) {
        if (!enabled)
            return;

        SyncOrderMessage message = new SyncOrderMessage(
                serverId,
                order.getOrderId(),
                order.getPlayerUuid(),
                order.getCommodityId(),
                order.getType().name(),
                order.getPricePerUnit(),
                order.getAmount(),
                order.getCreatedAt());

        publish(ORDER_CHANNEL, gson.toJson(message));
    }

    public void broadcastCancel(String commodityId, long orderId) {
        if (!enabled)
            return;

        SyncCancelMessage message = new SyncCancelMessage(serverId, commodityId, orderId);
        publish(CANCEL_CHANNEL, gson.toJson(message));
    }

    private void publish(String channel, String message) {
        executor.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to publish to Redis: " + e.getMessage());
            }
        });
    }

    public void setTradeHandler(Consumer<SyncTradeMessage> handler) {
        this.tradeHandler = handler;
    }

    public void setOrderHandler(Consumer<SyncOrderMessage> handler) {
        this.orderHandler = handler;
    }

    public void shutdown() {
        enabled = false;

        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    public boolean isEnabled() {
        return enabled && jedisPool != null && !jedisPool.isClosed();
    }

    public String getServerId() {
        return serverId;
    }

    // Trade sync message
    public static class SyncTradeMessage {
        public String serverId;
        public long tradeId;
        public String commodityId;
        public String buyerUuid;
        public String sellerUuid;
        public long amount;
        public double price;
        public long timestamp;

        public SyncTradeMessage(String serverId, long tradeId, String commodityId,
                UUID buyerUuid, UUID sellerUuid, long amount,
                double price, long timestamp) {
            this.serverId = serverId;
            this.tradeId = tradeId;
            this.commodityId = commodityId;
            this.buyerUuid = buyerUuid.toString();
            this.sellerUuid = sellerUuid.toString();
            this.amount = amount;
            this.price = price;
            this.timestamp = timestamp;
        }

        public UUID getBuyerUuid() {
            return UUID.fromString(buyerUuid);
        }

        public UUID getSellerUuid() {
            return UUID.fromString(sellerUuid);
        }
    }

    // Order sync message
    public static class SyncOrderMessage {
        public String serverId;
        public long orderId;
        public String playerUuid;
        public String commodityId;
        public String type;
        public double pricePerUnit;
        public long amount;
        public long createdAt;

        public SyncOrderMessage(String serverId, long orderId, UUID playerUuid,
                String commodityId, String type, double pricePerUnit,
                long amount, long createdAt) {
            this.serverId = serverId;
            this.orderId = orderId;
            this.playerUuid = playerUuid.toString();
            this.commodityId = commodityId;
            this.type = type;
            this.pricePerUnit = pricePerUnit;
            this.amount = amount;
            this.createdAt = createdAt;
        }

        public UUID getPlayerUuid() {
            return UUID.fromString(playerUuid);
        }

        public Order.OrderType getOrderType() {
            return Order.OrderType.valueOf(type);
        }
    }

    // Cancel sync message
    public static class SyncCancelMessage {
        public String serverId;
        public String commodityId;
        public long orderId;

        public SyncCancelMessage(String serverId, String commodityId, long orderId) {
            this.serverId = serverId;
            this.commodityId = commodityId;
            this.orderId = orderId;
        }
    }
}
