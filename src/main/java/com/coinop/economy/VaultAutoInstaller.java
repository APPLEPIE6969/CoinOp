package com.coinop.economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Auto-downloads Vault if missing - saves server admins some work
public class VaultAutoInstaller {

    private static final String VAULT_DOWNLOAD_URL = "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar";
    private static final String VAULT_SPIGOT_URL = "https://www.spigotmc.org/resources/vault.34315/download?version=331880";

    private final JavaPlugin plugin;
    private final File pluginsFolder;

    public VaultAutoInstaller(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pluginsFolder = plugin.getDataFolder().getParentFile();
    }

    public boolean isVaultInstalled() {
        return getVaultJarFile() != null;
    }

    private File getVaultJarFile() {
        File[] files = pluginsFolder.listFiles();
        if (files == null)
            return null;

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if ((name.startsWith("vault") || name.contains("vaultunlocked")) && name.endsWith(".jar")) {
                return file;
            }
        }
        return null;
    }

    public boolean installVault() {
        plugin.getLogger().info("Vault not found! Attempting auto-install...");

        // Try GitHub first
        if (downloadVault(VAULT_DOWNLOAD_URL)) {
            return true;
        }

        // Fallback to SpigotMC
        plugin.getLogger().info("Primary download failed, trying SpigotMC...");
        if (downloadVault(VAULT_SPIGOT_URL)) {
            return true;
        }

        plugin.getLogger().severe("========================================");
        plugin.getLogger().severe(" Failed to auto-install Vault!");
        plugin.getLogger().severe(" Please download manually from:");
        plugin.getLogger().severe(" https://www.spigotmc.org/resources/vault.34315/");
        plugin.getLogger().severe("========================================");
        return false;
    }

    private boolean downloadVault(String downloadUrl) {
        try {
            plugin.getLogger().info("Downloading Vault from: " + downloadUrl);

            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "CoinOpPlugin/1.0");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                plugin.getLogger().warning("Download failed: HTTP " + responseCode);
                return false;
            }

            long fileSize = connection.getContentLengthLong();
            plugin.getLogger().info("Download size: " + (fileSize / 1024) + " KB");

            // Download to temp file first
            Path tempFile = Files.createTempFile("vault-download", ".jar");

            try (InputStream in = connection.getInputStream();
                    OutputStream out = Files.newOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                int lastPercent = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // Progress logging
                    if (fileSize > 0) {
                        int percent = (int) ((totalRead * 100) / fileSize);
                        if (percent >= lastPercent + 10) {
                            plugin.getLogger().info("Download: " + percent + "%");
                            lastPercent = percent;
                        }
                    }
                }
            }

            // Move to plugins folder
            File targetFile = new File(pluginsFolder, "Vault.jar");
            Files.copy(tempFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempFile);

            plugin.getLogger().info("========================================");
            plugin.getLogger().info(" Vault downloaded successfully!");
            plugin.getLogger().info(" Saved to: " + targetFile.getAbsolutePath());
            plugin.getLogger().info(" Restart server to enable Vault.");
            plugin.getLogger().info("========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Download failed: " + e.getMessage());
            return false;
        }
    }

    public boolean ensureVaultInstalled() {
        // Already loaded? (check both Vault and VaultUnlocked)
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null) {
            plugin.getLogger().info("VaultUnlocked detected - using as economy provider.");
            return true;
        }

        // Jar exists but not loaded?
        if (isVaultInstalled()) {
            plugin.getLogger().warning("Vault/VaultUnlocked jar found but not loaded. Restart server.");
            return false;
        }

        // Try to download
        return installVault();
    }
}
