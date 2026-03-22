package com.coinop.command;

import com.coinop.CoinOpPlugin;
import com.coinop.api.CoinOpAPI;
import com.coinop.config.CoinOpConfig;
import com.coinop.config.PriceBounds;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// Admin commands for managing the bazaar
public class CoinOpAdminCommand implements CommandExecutor, TabCompleter {

    private final CoinOpPlugin plugin;
    private final CoinOpAPI api;
    private final CoinOpConfig config;

    public CoinOpAdminCommand(CoinOpPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getAPI();
        this.config = plugin.getCoinOpConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coinop.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "bounds":
                return handleBounds(sender, args);
            case "limits":
                return handleLimits(sender, args);
            case "stats":
                return handleStats(sender, args);
            case "cleanup":
                return handleCleanup(sender);
            case "reset":
                return handleReset(sender, args);
            case "sync":
                return handleSync(sender, args);
            default:
                sendAdminHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
        return true;
    }

    private boolean handleBounds(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coa bounds <list|set|remove|enable> [args...]");
            return true;
        }

        String action = args[1].toLowerCase();
        PriceBounds bounds = config.getPriceBounds();

        switch (action) {
            case "list":
                sender.sendMessage(ChatColor.GOLD + "=== Price Bounds ===");
                sender.sendMessage(ChatColor.YELLOW + "Global: " + ChatColor.WHITE +
                        String.format("%.2f", bounds.getGlobalMinPrice()) + " - " +
                        String.format("%.2f", bounds.getGlobalMaxPrice()));
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " +
                        (bounds.isEnabled() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                // Show commodity-specific bounds
                var allBounds = bounds.getAllBounds();
                if (!allBounds.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Commodities:");
                    for (var entry : allBounds.entrySet()) {
                        double[] b = entry.getValue();
                        sender.sendMessage(ChatColor.WHITE + "  " + entry.getKey() + ": " +
                                String.format("%.2f", b[0]) + " - " + String.format("%.2f", b[1]));
                    }
                }
                break;

            case "set":
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /coa bounds set <commodity> <min> <max>");
                    return true;
                }

                String commodity = args[2].toUpperCase();
                double min, max;

                try {
                    min = Double.parseDouble(args[3]);
                    max = Double.parseDouble(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid prices.");
                    return true;
                }

                if (min > max) {
                    sender.sendMessage(ChatColor.RED + "Min can't exceed max.");
                    return true;
                }

                bounds.setBounds(commodity, min, max);
                sender.sendMessage(ChatColor.GREEN + "Bounds set for " + commodity + ": " + min + " - " + max);
                break;

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /coa bounds remove <commodity>");
                    return true;
                }

                bounds.removeBounds(args[2].toUpperCase());
                sender.sendMessage(ChatColor.GREEN + "Bounds removed for " + args[2].toUpperCase());
                break;

            case "enable":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /coa bounds enable <true|false>");
                    return true;
                }

                boolean enabled = Boolean.parseBoolean(args[2]);
                bounds.setEnabled(enabled);
                sender.sendMessage(ChatColor.GREEN + "Bounds " + (enabled ? "enabled" : "disabled") + ".");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Use: list, set, remove, enable");
                return true;
        }

        return true;
    }

    private boolean handleLimits(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coa limits <list|set|remove> [args...]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                sender.sendMessage(ChatColor.GOLD + "=== Daily Trade Limits ===");
                sender.sendMessage(ChatColor.YELLOW + "Global: " + ChatColor.WHITE +
                        (config.getGlobalDailyTradeLimit() > 0 ? formatNumber(config.getGlobalDailyTradeLimit())
                                : "Unlimited"));
                // Show commodity-specific limits
                var allLimits = config.getAllDailyTradeLimits();
                if (!allLimits.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Commodities:");
                    for (var entry : allLimits.entrySet()) {
                        sender.sendMessage(ChatColor.WHITE + "  " + entry.getKey() + ": " +
                                formatNumber(entry.getValue()));
                    }
                }
                break;

            case "set":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /coa limits set <commodity> <limit>");
                    return true;
                }

                String commodity = args[2].toUpperCase();
                long limit;

                try {
                    limit = Long.parseLong(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid limit.");
                    return true;
                }

                if (limit < 0) {
                    sender.sendMessage(ChatColor.RED + "Limit can't be negative.");
                    return true;
                }

                config.setDailyTradeLimit(commodity, limit);
                sender.sendMessage(ChatColor.GREEN + "Limit set for " + commodity + ": " + formatNumber(limit));
                break;

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /coa limits remove <commodity>");
                    return true;
                }

                config.setDailyTradeLimit(args[2].toUpperCase(), 0);
                sender.sendMessage(ChatColor.GREEN + "Limit removed for " + args[2].toUpperCase());
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Use: list, set, remove");
                return true;
        }

        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Global stats
            var orderBooks = plugin.getCoinOpManager().getMatchingEngine().getAllOrderBooks();
            sender.sendMessage(ChatColor.GOLD + "=== CoinOp Stats ===");
            sender.sendMessage(ChatColor.YELLOW + "Order Books: " + ChatColor.WHITE + orderBooks.size());

            int totalBuyOrders = 0;
            int totalSellOrders = 0;
            long totalBuyVolume = 0;
            long totalSellVolume = 0;

            for (var book : orderBooks.values()) {
                totalBuyOrders += book.getBuyOrderCount();
                totalSellOrders += book.getSellOrderCount();
                totalBuyVolume += book.getTotalBuyVolume();
                totalSellVolume += book.getTotalSellVolume();
            }

            sender.sendMessage(ChatColor.YELLOW + "Buy Orders: " + ChatColor.WHITE + totalBuyOrders +
                    ChatColor.GRAY + " (" + formatNumber(totalBuyVolume) + " items)");
            sender.sendMessage(ChatColor.YELLOW + "Sell Orders: " + ChatColor.WHITE + totalSellOrders +
                    ChatColor.GRAY + " (" + formatNumber(totalSellVolume) + " items)");

            if (plugin.getSyncManager() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Sync: " + ChatColor.GREEN + "Connected");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Sync: " + ChatColor.GRAY + "Disabled");
            }

            return true;
        }

        // Commodity-specific stats
        String commodity = args[1].toUpperCase();
        var book = api.getOrderBook(commodity);

        if (book == null) {
            sender.sendMessage(ChatColor.RED + "No order book for " + commodity);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + commodity + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Buy Orders: " + ChatColor.WHITE + book.getBuyOrderCount() +
                ChatColor.GRAY + " (" + formatNumber(book.getTotalBuyVolume()) + " items)");
        sender.sendMessage(ChatColor.YELLOW + "Sell Orders: " + ChatColor.WHITE + book.getSellOrderCount() +
                ChatColor.GRAY + " (" + formatNumber(book.getTotalSellVolume()) + " items)");
        sender.sendMessage(ChatColor.YELLOW + "Best Buy: " + ChatColor.WHITE +
                (book.getBestBuyPrice() > 0 ? String.format("%.2f", book.getBestBuyPrice()) : "N/A"));
        sender.sendMessage(ChatColor.YELLOW + "Best Sell: " + ChatColor.WHITE +
                (book.getBestSellPrice() < Double.MAX_VALUE ? String.format("%.2f", book.getBestSellPrice()) : "N/A"));
        sender.sendMessage(ChatColor.YELLOW + "24h Volume: " + ChatColor.WHITE +
                formatNumber(book.get24hVolume()));
        sender.sendMessage(ChatColor.YELLOW + "24h VWAP: " + ChatColor.WHITE +
                (book.get24hVWAP() > 0 ? String.format("%.2f", book.get24hVWAP()) : "N/A"));

        return true;
    }

    private boolean handleCleanup(CommandSender sender) {
        int removed = plugin.getCoinOpManager().getMatchingEngine().cleanupInactiveOrders();
        sender.sendMessage(ChatColor.GREEN + "Cleaned " + removed + " inactive orders.");
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coa reset <daily|stats|all>");
            return true;
        }

        String target = args[1].toLowerCase();
        var orderBooks = plugin.getCoinOpManager().getMatchingEngine().getAllOrderBooks();

        switch (target) {
            case "daily":
                plugin.getCoinOpManager().resetDailyVolumes();
                sender.sendMessage(ChatColor.GREEN + "Daily volumes reset.");
                break;

            case "stats":
                for (var book : orderBooks.values()) {
                    book.reset24hStats();
                }
                sender.sendMessage(ChatColor.GREEN + "24h stats reset.");
                break;

            case "all":
                plugin.getCoinOpManager().resetDailyVolumes();
                for (var book : orderBooks.values()) {
                    book.reset24hStats();
                }
                sender.sendMessage(ChatColor.GREEN + "All stats reset.");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Use: daily, stats, all");
                return true;
        }

        return true;
    }

    private boolean handleSync(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coa sync <status|enable|disable>");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status":
                if (plugin.getSyncManager() != null && plugin.getSyncManager().isEnabled()) {
                    sender.sendMessage(ChatColor.GREEN + "Sync enabled and connected.");
                    sender.sendMessage(ChatColor.YELLOW + "Server ID: " + plugin.getSyncManager().getServerId());
                } else {
                    sender.sendMessage(ChatColor.RED + "Sync not enabled or not connected.");
                }
                break;

            case "enable":
                config.setSyncEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Sync enabled. Restart to apply.");
                break;

            case "disable":
                config.setSyncEnabled(false);
                sender.sendMessage(ChatColor.GREEN + "Sync disabled. Restart to apply.");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Use: status, enable, disable");
                return true;
        }

        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== CoinOp Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/coa reload" + ChatColor.GRAY + " - Reload config");
        sender.sendMessage(ChatColor.YELLOW + "/coa bounds <list|set|remove|enable>" +
                ChatColor.GRAY + " - Price bounds");
        sender.sendMessage(ChatColor.YELLOW + "/coa limits <list|set|remove>" +
                ChatColor.GRAY + " - Trade limits");
        sender.sendMessage(ChatColor.YELLOW + "/coa stats [commodity]" +
                ChatColor.GRAY + " - View stats");
        sender.sendMessage(ChatColor.YELLOW + "/coa cleanup" + ChatColor.GRAY + " - Clean inactive orders");
        sender.sendMessage(ChatColor.YELLOW + "/coa reset <daily|stats|all>" +
                ChatColor.GRAY + " - Reset stats");
        sender.sendMessage(ChatColor.YELLOW + "/coa sync <status|enable|disable>" +
                ChatColor.GRAY + " - Manage sync");
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("coinop.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "bounds", "limits", "stats", "cleanup", "reset", "sync"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "bounds":
                    completions.addAll(Arrays.asList("list", "set", "remove", "enable"));
                    break;
                case "limits":
                    completions.addAll(Arrays.asList("list", "set", "remove"));
                    break;
                case "reset":
                    completions.addAll(Arrays.asList("daily", "stats", "all"));
                    break;
                case "sync":
                    completions.addAll(Arrays.asList("status", "enable", "disable"));
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();

            if ((subCommand.equals("bounds") || subCommand.equals("limits")) &&
                    (action.equals("set") || action.equals("remove"))) {
                completions.addAll(Arrays.stream(Material.values())
                        .map(Material::name)
                        .collect(Collectors.toList()));
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
