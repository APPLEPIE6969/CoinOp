package com.coinop.database;

import com.coinop.config.CoinOpConfig;
import com.coinop.model.Order;
import com.coinop.model.Trade;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Handles database persistence for orders and trades
// Supports SQLite (embedded) and MySQL (remote)
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final CoinOpConfig config;
    private HikariDataSource dataSource;
    private final ExecutorService executor;

    private static final String ORDERS_TABLE = "coinop_orders";
    private static final String TRADES_TABLE = "coinop_trades";
    private static final String STATS_TABLE = "coinop_stats";

    public DatabaseManager(JavaPlugin plugin, CoinOpConfig config) {
        this.plugin = plugin;
        this.config = config;
        // Fixed pool to prevent resource exhaustion
        this.executor = Executors.newFixedThreadPool(10);
    }

    public boolean initialize() {
        try {
            HikariConfig hikariConfig = new HikariConfig();

            String dbType = config.getDatabaseType();

            if (dbType.equalsIgnoreCase("mysql")) {
                String url = String.format("jdbc:mysql://%s:%d/%s",
                        config.getDatabaseHost(),
                        config.getDatabasePort(),
                        config.getDatabaseName());

                hikariConfig.setJdbcUrl(url);
                hikariConfig.setUsername(config.getDatabaseUser());
                hikariConfig.setPassword(config.getDatabasePassword());
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

                // MySQL performance tweaks
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
                hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
                hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
                hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
                hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
                hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

            } else {
                // SQLite - simple file-based storage
                String path = plugin.getDataFolder().getAbsolutePath();
                String url = "jdbc:sqlite:" + path + "/coinop.db";

                hikariConfig.setJdbcUrl(url);
                hikariConfig.setDriverClassName("org.sqlite.JDBC");

                // SQLite performance tweaks
                hikariConfig.addDataSourceProperty("journal_mode", "WAL");
                hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
                hikariConfig.addDataSourceProperty("foreign_keys", "ON");
            }

            // Connection pool settings
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("CoinOpPool");

            dataSource = new HikariDataSource(hikariConfig);

            createTables();

            plugin.getLogger().info("Database connected (" + dbType + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Database init failed: " + e.getMessage());
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            // Orders table - stores all buy/sell orders
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + ORDERS_TABLE + " (" +
                            "order_id BIGINT PRIMARY KEY, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "commodity_id VARCHAR(64) NOT NULL, " +
                            "order_type VARCHAR(4) NOT NULL, " +
                            "price_per_unit DOUBLE NOT NULL, " +
                            "amount BIGINT NOT NULL, " +
                            "filled_amount BIGINT DEFAULT 0, " +
                            "created_at BIGINT NOT NULL, " +
                            "updated_at BIGINT NOT NULL, " +
                            "active BOOLEAN DEFAULT TRUE, " +
                            "INDEX idx_player (player_uuid), " +
                            "INDEX idx_commodity (commodity_id), " +
                            "INDEX idx_active (active))");

            // Trades table - completed transactions
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + TRADES_TABLE + " (" +
                            "trade_id BIGINT PRIMARY KEY, " +
                            "commodity_id VARCHAR(64) NOT NULL, " +
                            "buyer_uuid VARCHAR(36) NOT NULL, " +
                            "seller_uuid VARCHAR(36) NOT NULL, " +
                            "amount BIGINT NOT NULL, " +
                            "price DOUBLE NOT NULL, " +
                            "total_value DOUBLE NOT NULL, " +
                            "timestamp BIGINT NOT NULL, " +
                            "INDEX idx_commodity (commodity_id), " +
                            "INDEX idx_timestamp (timestamp))");

            // Stats table - 24h volume and price tracking
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + STATS_TABLE + " (" +
                            "commodity_id VARCHAR(64) PRIMARY KEY, " +
                            "volume_24h BIGINT DEFAULT 0, " +
                            "value_24h DOUBLE DEFAULT 0, " +
                            "last_trade_price DOUBLE DEFAULT 0, " +
                            "last_update BIGINT NOT NULL)");
        }
    }

    public CompletableFuture<Void> saveOrder(Order order) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO " + ORDERS_TABLE +
                    " (order_id, player_uuid, commodity_id, order_type, price_per_unit, " +
                    "amount, filled_amount, created_at, updated_at, active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, order.getOrderId());
                stmt.setString(2, order.getPlayerUuid().toString());
                stmt.setString(3, order.getCommodityId());
                stmt.setString(4, order.getType().name());
                stmt.setDouble(5, order.getPricePerUnit());
                stmt.setLong(6, order.getAmount());
                stmt.setLong(7, order.getFilledAmount());
                stmt.setLong(8, order.getCreatedAt());
                stmt.setLong(9, order.getUpdatedAt());
                stmt.setBoolean(10, order.isActive());

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save order: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Void> saveTrade(Trade trade) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TRADES_TABLE +
                    " (trade_id, commodity_id, buyer_uuid, seller_uuid, amount, price, " +
                    "total_value, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, trade.getTradeId());
                stmt.setString(2, trade.getCommodityId());
                stmt.setString(3, trade.getBuyerUuid().toString());
                stmt.setString(4, trade.getSellerUuid().toString());
                stmt.setLong(5, trade.getAmount());
                stmt.setDouble(6, trade.getPrice());
                stmt.setDouble(7, trade.getTotalValue());
                stmt.setLong(8, trade.getTimestamp());

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save trade: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Map<String, List<Order>>> loadAllOrders() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<Order>> orders = new HashMap<>();
            String sql = "SELECT * FROM " + ORDERS_TABLE + " WHERE active = TRUE";

            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Order order = new Order.Builder()
                            .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                            .commodityId(rs.getString("commodity_id"))
                            .type(Order.OrderType.valueOf(rs.getString("order_type")))
                            .pricePerUnit(rs.getDouble("price_per_unit"))
                            .amount(rs.getLong("amount"))
                            .build();

                    order.fill(rs.getLong("filled_amount"));

                    orders.computeIfAbsent(order.getCommodityId(), k -> new ArrayList<>())
                            .add(order);
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load orders: " + e.getMessage());
            }

            return orders;
        }, executor);
    }

    public CompletableFuture<Void> updateOrderStatus(long orderId, boolean active) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + ORDERS_TABLE +
                    " SET active = ?, updated_at = ? WHERE order_id = ?";

            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setBoolean(1, active);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setLong(3, orderId);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update order: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<List<Trade>> getTradeHistory(String commodityId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Trade> trades = new ArrayList<>();
            String sql = "SELECT * FROM " + TRADES_TABLE +
                    " WHERE commodity_id = ? ORDER BY timestamp DESC LIMIT ?";

            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, commodityId);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Trade trade = new Trade(
                                rs.getLong("trade_id"),
                                rs.getString("commodity_id"),
                                UUID.fromString(rs.getString("buyer_uuid")),
                                UUID.fromString(rs.getString("seller_uuid")),
                                rs.getLong("amount"),
                                rs.getDouble("price"),
                                rs.getLong("timestamp"));
                        trades.add(trade);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get trade history: " + e.getMessage());
            }

            return trades;
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();

        if (dataSource != null) {
            dataSource.close();
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
