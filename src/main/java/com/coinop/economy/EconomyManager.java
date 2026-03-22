package com.coinop.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Vault economy wrapper - handles all money operations
public class EconomyManager {

    private Economy economy;
    private boolean vaultEnabled;
    private final JavaPlugin plugin;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vaultEnabled = false;
    }

    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features disabled.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().warning("No economy plugin found! Install one like EssentialsX.");
            return false;
        }

        economy = rsp.getProvider();
        vaultEnabled = true;

        plugin.getLogger().info("Hooked into " + economy.getName() + " via Vault.");
        return true;
    }

    public boolean isEnabled() {
        return vaultEnabled && economy != null;
    }

    public double getBalance(UUID playerUuid) {
        if (!isEnabled())
            return 0;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.getBalance(player);
    }

    public CompletableFuture<Double> getBalanceAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> getBalance(playerUuid));
    }

    public boolean hasBalance(UUID playerUuid, double amount) {
        if (!isEnabled())
            return false;
        if (amount <= 0)
            return true;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        return economy.has(player, amount);
    }

    public TransactionResult withdraw(UUID playerUuid, double amount) {
        if (!isEnabled()) {
            return TransactionResult.failure("Economy not enabled");
        }

        if (amount < 0) {
            return TransactionResult.failure("Amount can't be negative");
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);

        if (!economy.has(player, amount)) {
            return TransactionResult.failure("Insufficient funds");
        }

        var response = economy.withdrawPlayer(player, amount);

        if (response.transactionSuccess()) {
            return TransactionResult.success(amount, getBalance(playerUuid));
        } else {
            return TransactionResult.failure(response.errorMessage);
        }
    }

    public CompletableFuture<TransactionResult> withdrawAsync(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> withdraw(playerUuid, amount));
    }

    public TransactionResult deposit(UUID playerUuid, double amount) {
        if (!isEnabled()) {
            return TransactionResult.failure("Economy not enabled");
        }

        if (amount < 0) {
            return TransactionResult.failure("Amount can't be negative");
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        var response = economy.depositPlayer(player, amount);

        if (response.transactionSuccess()) {
            return TransactionResult.success(amount, getBalance(playerUuid));
        } else {
            return TransactionResult.failure(response.errorMessage);
        }
    }

    public CompletableFuture<TransactionResult> depositAsync(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> deposit(playerUuid, amount));
    }

    public TransactionResult transfer(UUID fromUuid, UUID toUuid, double amount) {
        if (!isEnabled()) {
            return TransactionResult.failure("Economy not enabled");
        }

        if (amount < 0) {
            return TransactionResult.failure("Amount can't be negative");
        }

        if (!hasBalance(fromUuid, amount)) {
            return TransactionResult.failure("Insufficient funds");
        }

        // Withdraw from sender
        TransactionResult withdrawResult = withdraw(fromUuid, amount);
        if (!withdrawResult.isSuccess()) {
            return withdrawResult;
        }

        // Deposit to receiver
        TransactionResult depositResult = deposit(toUuid, amount);
        if (!depositResult.isSuccess()) {
            // Rollback on failure
            deposit(fromUuid, amount);
            return depositResult;
        }

        return TransactionResult.success(amount, getBalance(fromUuid));
    }

    public CompletableFuture<TransactionResult> transferAsync(UUID fromUuid, UUID toUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> transfer(fromUuid, toUuid, amount));
    }

    public String format(double amount) {
        if (!isEnabled())
            return String.format("%.2f", amount);
        return economy.format(amount);
    }

    public String getCurrencyNameSingular() {
        if (!isEnabled())
            return "Dollar";
        return economy.currencyNameSingular();
    }

    public String getCurrencyNamePlural() {
        if (!isEnabled())
            return "Dollars";
        return economy.currencyNamePlural();
    }

    public Economy getVaultEconomy() {
        return economy;
    }

    // Simple result wrapper for transactions
    public static class TransactionResult {
        private final boolean success;
        private final double amount;
        private final double newBalance;
        private final String errorMessage;

        private TransactionResult(boolean success, double amount, double newBalance, String errorMessage) {
            this.success = success;
            this.amount = amount;
            this.newBalance = newBalance;
            this.errorMessage = errorMessage;
        }

        public static TransactionResult success(double amount, double newBalance) {
            return new TransactionResult(true, amount, newBalance, null);
        }

        public static TransactionResult failure(String errorMessage) {
            return new TransactionResult(false, 0, 0, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public double getAmount() {
            return amount;
        }

        public double getNewBalance() {
            return newBalance;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
