package com.coinop;

import com.coinop.anvil.AnvilReworkListener;
import com.coinop.api.CoinOpAPI;
import com.coinop.command.CoinOpCommand;
import com.coinop.command.CoinOpAdminCommand;
import com.coinop.config.CoinOpConfig;
import com.coinop.database.DatabaseManager;
import com.coinop.economy.EconomyManager;
import com.coinop.economy.VaultAutoInstaller;
import com.coinop.gui.CoinOpGUI;
import com.coinop.lore.LoreManager;
import com.coinop.manager.CoinOpManager;
import com.coinop.sync.SyncManager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

// Main plugin class - order-book economy system
public class CoinOpPlugin extends JavaPlugin {

    private static CoinOpPlugin instance;

    private CoinOpConfig config;
    private EconomyManager economyManager;
    private CoinOpManager CoinOpManager;
    private LoreManager loreManager;
    private SyncManager syncManager;
    private AnvilReworkListener anvilListener;
    private DatabaseManager databaseManager;
    private CoinOpGUI guiManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("========================================");
        getLogger().info(" CoinOp Economy Plugin v" + getDescription().getVersion());
        getLogger().info(" Initializing...");
        getLogger().info("========================================");

        // Config
        config = new CoinOpConfig(this);
        config.load();
        getLogger().info("Configuration loaded.");

        // Economy
        economyManager = new EconomyManager(this);
        if (!economyManager.initialize()) {
            VaultAutoInstaller vaultInstaller = new VaultAutoInstaller(this);
            if (vaultInstaller.ensureVaultInstalled()) {
                getLogger().severe("========================================");
                getLogger().severe(" Vault downloaded! Restart server to load.");
                getLogger().severe("========================================");
            } else {
                getLogger().severe("========================================");
                getLogger().severe(" ECONOMY SETUP REQUIRED!");
                getLogger().severe(" Install one of:");
                getLogger().severe(" - EssentialsX: https://essentialsx.net/");
                getLogger().severe(" - CMI: https://www.spigotmc.org/resources/cmi.3742/");
                getLogger().severe("========================================");
            }
            getLogger().warning("Running without economy features.");
        }

        // Database
        databaseManager = new DatabaseManager(this, config);
        if (databaseManager.initialize()) {
            getLogger().info("Database initialized.");
        } else {
            getLogger().warning("Database failed. Running without persistence.");
        }

        // Market manager
        CoinOpManager = new CoinOpManager(this, economyManager, config, databaseManager);
        getLogger().info("Market manager initialized.");

        // Load orders from database
        if (databaseManager.isConnected()) {
            databaseManager.loadAllOrders().thenAccept(orders -> {
                if (!orders.isEmpty()) {
                    CoinOpManager.loadOrders(orders);
                    getLogger().info("Loaded " +
                            orders.values().stream().mapToInt(List::size).sum() + " orders.");
                }
            });
        }

        // Multi-server sync
        if (config.isSyncEnabled()) {
            syncManager = new SyncManager(this, config, CoinOpManager.getMatchingEngine());
            if (syncManager.initialize()) {
                CoinOpManager.setSyncManager(syncManager);
                getLogger().info("Multi-server sync enabled.");
            } else {
                getLogger().warning("Sync failed. Running in single-server mode.");
            }
        }

        // Lore manager
        loreManager = new LoreManager(this, CoinOpManager, config);
        loreManager.start();
        getLogger().info("Lore manager initialized.");

        // Anvil rework
        if (config.isAnvilReworkEnabled()) {
            anvilListener = new AnvilReworkListener(
                    this,
                    config.isAnvilReworkEnabled(),
                    config.isEnchantmentExtractionEnabled());
            Bukkit.getPluginManager().registerEvents(anvilListener, this);
            getLogger().info("Anvil rework enabled.");
        }

        // GUI
        if (config.isGuiEnabled()) {
            guiManager = new CoinOpGUI(this, config, CoinOpManager);
            getLogger().info("GUI manager initialized.");
        }

        // API service
        Bukkit.getServicesManager().register(
                CoinOpAPI.class,
                CoinOpManager,
                this,
                ServicePriority.Normal);
        getLogger().info("API service registered.");

        // Events
        Bukkit.getPluginManager().registerEvents(loreManager, this);

        // Commands
        CoinOpCommand CoinOpCommand = new CoinOpCommand(this);
        CoinOpAdminCommand adminCommand = new CoinOpAdminCommand(this);

        // Register main commands
        if (getCommand("coinop") != null) {
            getCommand("coinop").setExecutor(CoinOpCommand);
            getCommand("coinop").setTabCompleter(CoinOpCommand);
            getLogger().info("Registered command: coinop");
        } else {
            getLogger().warning("Failed to register command: coinop");
        }

        if (getCommand("coinadmin") != null) {
            getCommand("coinadmin").setExecutor(adminCommand);
            getCommand("coinadmin").setTabCompleter(adminCommand);
            getLogger().info("Registered command: coinadmin");
        } else {
            getLogger().warning("Failed to register command: coinadmin");
        }

        // Register additional commands (all use CoinOpCommand)
        String[] commands = { "coinbuy", "coinsell", "coininstant", "coinorders", "coinprice", "coinhistory" };
        for (String cmd : commands) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(CoinOpCommand);
                getCommand(cmd).setTabCompleter(CoinOpCommand);
                getLogger().info("Registered command: " + cmd);
            } else {
                getLogger().warning("Failed to register command: " + cmd + " (not found in plugin.yml)");
            }
        }

        getLogger().info("Commands registered.");

        // Periodic tasks
        scheduleTasks();

        getLogger().info("========================================");
        getLogger().info(" CoinOp Economy Plugin enabled!");
        getLogger().info(" API: CoinOpAPI service");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down CoinOp...");

        if (config != null) {
            config.save();
            getLogger().info("Config saved.");
        }

        if (CoinOpManager != null) {
            CoinOpManager.shutdown();
            getLogger().info("Market manager shutdown.");
        }

        if (syncManager != null) {
            syncManager.shutdown();
            getLogger().info("Sync manager shutdown.");
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
            getLogger().info("Database shutdown.");
        }

        if (loreManager != null) {
            loreManager.clearCache();
        }

        getLogger().info("CoinOp disabled.");
    }

    private void scheduleTasks() {
        // Daily reset (every 24 hours)
        new BukkitRunnable() {
            @Override
            public void run() {
                CoinOpManager.resetDailyVolumes();
                getLogger().info("Daily volumes reset.");
            }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);

        // Cleanup (every 5 minutes)
        new BukkitRunnable() {
            @Override
            public void run() {
                int removed = CoinOpManager.getMatchingEngine().cleanupInactiveOrders();
                if (removed > 0) {
                    getLogger().fine("Cleaned " + removed + " inactive orders.");
                }
            }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 5);

        // 24h stats reset
        new BukkitRunnable() {
            @Override
            public void run() {
                for (var book : CoinOpManager.getMatchingEngine().getAllOrderBooks().values()) {
                    book.reset24hStats();
                }
                getLogger().fine("24h stats reset.");
            }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    // Getters

    public static CoinOpPlugin getInstance() {
        return instance;
    }

    public CoinOpConfig getCoinOpConfig() {
        return config;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public CoinOpManager getCoinOpManager() {
        return CoinOpManager;
    }

    public LoreManager getLoreManager() {
        return loreManager;
    }

    public CoinOpGUI getGuiManager() {
        return guiManager;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public CoinOpAPI getAPI() {
        return CoinOpManager;
    }
}
