package com.coinop.command;

import com.coinop.CoinOpPlugin;
import com.coinop.api.CoinOpAPI;
import com.coinop.config.CoinOpConfig;
import com.coinop.engine.MatchingEngine.MatchResult;
import com.coinop.model.Order;
import com.coinop.model.OrderBook;
import com.coinop.model.Trade;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

// Main player command handler
public class CoinOpCommand implements CommandExecutor, TabCompleter {

    private final CoinOpPlugin plugin;
    private final CoinOpAPI api;
    private final CoinOpConfig config;

    public CoinOpCommand(CoinOpPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getAPI();
        this.config = plugin.getCoinOpConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // Handle direct commands (coinbuy, coinsell, etc.)
        switch (cmdName) {
            case "coinbuy":
                return handleBuyDirect(sender, args);
            case "coinsell":
                return handleSellDirect(sender, args);
            case "coininstant":
                return handleInstantDirect(sender, args);
            case "coinorders":
                return handleOrders(sender, new String[0]);
            case "coinprice":
                return handlePrice(sender, args);
            case "coinhistory":
                return handleHistory(sender, args);
        }

        // Handle /coinop subcommands
        if (args.length == 0) {
            // Open GUI if enabled
            if (sender instanceof Player && config.isGuiEnabled() && config.isGuiOpenOnCommand()) {
                Player player = (Player) sender;
                if (plugin.getGuiManager() != null) {
                    plugin.getGuiManager().openMainMenu(player);
                    return true;
                }
            }
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "buy":
                return handleBuy(sender, args);
            case "sell":
                return handleSell(sender, args);
            case "instant":
                return handleInstant(sender, args);
            case "orders":
                return handleOrders(sender, args);
            case "price":
            case "p":
                return handlePrice(sender, args);
            case "history":
            case "h":
                return handleHistory(sender, args);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /coinop help");
                return true;
        }
    }

    private boolean handleBuyDirect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("coinop.buy")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /coinbuy <commodity> <amount> <price>");
            return true;
        }
        // Rebuild args array for handleBuy
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "buy";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return handleBuy(sender, newArgs);
    }

    private boolean handleSellDirect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("coinop.sell")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /coinsell <commodity> <amount> <price>");
            return true;
        }
        // Rebuild args array for handleSell
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "sell";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return handleSell(sender, newArgs);
    }

    private boolean handleInstantDirect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("coinop.instant")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /coininstant <buy|sell> <commodity> <amount>");
            return true;
        }
        // Rebuild args array for handleInstant
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "instant";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return handleInstant(sender, newArgs);
    }

    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coinop.buy")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /coinop buy <commodity> <amount> <price>");
            return true;
        }

        String commodity = args[1].toUpperCase();
        long amount;
        double price;

        try {
            amount = Long.parseLong(args[2]);
            price = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount or price.");
            return true;
        }

        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Amount must be 1-" + Integer.MAX_VALUE);
            return true;
        }

        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Invalid price.");
            return true;
        }

        Material material = Material.getMaterial(commodity);
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Unknown item: " + commodity);
            return true;
        }

        MatchResult result = api.placeBuyOrder(player.getUniqueId(), commodity, amount, price);

        if (result.isRejected()) {
            player.sendMessage(ChatColor.RED + "Rejected: " + result.getRejectionReason());
        } else if (result.isFullyFilled()) {
            player.sendMessage(ChatColor.GREEN + "Bought " + result.getFilledAmount() + "x " + commodity +
                    " at " + String.format("%.2f", result.getAveragePrice()) + " each.");
        } else if (result.isPartiallyFilled()) {
            player.sendMessage(ChatColor.YELLOW + "Partial fill: " + result.getFilledAmount() + "/" + amount +
                    " at " + String.format("%.2f", result.getAveragePrice()) + " each.");
            player.sendMessage(ChatColor.YELLOW + "Remaining order in book.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Buy order placed: " + amount + "x " + commodity + " @ " + price);
        }

        return true;
    }

    private boolean handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coinop.sell")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /coinop sell <commodity> <amount> <price>");
            return true;
        }

        String commodity = args[1].toUpperCase();
        long amount;
        double price;

        try {
            amount = Long.parseLong(args[2]);
            price = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount or price.");
            return true;
        }

        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Amount must be 1-" + Integer.MAX_VALUE);
            return true;
        }

        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "Invalid price.");
            return true;
        }

        Material material = Material.getMaterial(commodity);
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Unknown item: " + commodity);
            return true;
        }

        int itemCount = countItems(player, material);
        if (itemCount < amount) {
            player.sendMessage(ChatColor.RED + "You only have " + itemCount + "x " + commodity);
            return true;
        }

        MatchResult result = api.placeSellOrder(player.getUniqueId(), commodity, amount, price);

        if (result.isRejected()) {
            player.sendMessage(ChatColor.RED + "Rejected: " + result.getRejectionReason());
        } else {
            removeItems(player, material, (int) result.getFilledAmount());

            if (result.isFullyFilled()) {
                player.sendMessage(ChatColor.GREEN + "Sold " + result.getFilledAmount() + "x " + commodity +
                        " at " + String.format("%.2f", result.getAveragePrice()) + " each.");
            } else if (result.isPartiallyFilled()) {
                player.sendMessage(ChatColor.YELLOW + "Partial fill: " + result.getFilledAmount() + "/" + amount +
                        " at " + String.format("%.2f", result.getAveragePrice()) + " each.");
                player.sendMessage(ChatColor.YELLOW + "Remaining order in book.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Sell order placed: " + amount + "x " + commodity + " @ " + price);
            }
        }

        return true;
    }

    private boolean handleInstant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coinop.instant")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /coinop instant <buy|sell> <commodity> <amount>");
            return true;
        }

        String action = args[1].toLowerCase();
        String commodity = args[2].toUpperCase();
        long amount;

        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount.");
            return true;
        }

        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Amount must be 1-" + Integer.MAX_VALUE);
            return true;
        }

        Material material = Material.getMaterial(commodity);
        if (material == null) {
            player.sendMessage(ChatColor.RED + "Unknown item: " + commodity);
            return true;
        }

        MatchResult result;

        if (action.equals("buy")) {
            result = api.instantBuy(player.getUniqueId(), commodity, amount);
            if (!result.isRejected()) {
                giveItems(player, material, (int) result.getFilledAmount());
            }
        } else if (action.equals("sell")) {
            int itemCount = countItems(player, material);
            if (itemCount < amount) {
                player.sendMessage(ChatColor.RED + "You only have " + itemCount + "x " + commodity);
                return true;
            }
            result = api.instantSell(player.getUniqueId(), commodity, amount);
            if (!result.isRejected()) {
                removeItems(player, material, (int) result.getFilledAmount());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Use 'buy' or 'sell'.");
            return true;
        }

        if (result.isRejected()) {
            player.sendMessage(ChatColor.RED + "Rejected: " + result.getRejectionReason());
        } else {
            player.sendMessage(ChatColor.GREEN + "Instant " + action + ": " + result.getFilledAmount() +
                    "x " + commodity + " @ " + String.format("%.2f", result.getAveragePrice()));
        }

        return true;
    }

    private boolean handleOrders(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coinop.orders")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length > 1 && args[1].equalsIgnoreCase("cancel")) {
            if (!player.hasPermission("coinop.cancel")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 4) {
                player.sendMessage(ChatColor.RED + "Usage: /coinop orders cancel <commodity> <orderId>");
                return true;
            }

            String commodity = args[2].toUpperCase();
            long orderId;

            try {
                orderId = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid order ID.");
                return true;
            }

            // Verify ownership
            OrderBook book = api.getOrderBook(commodity);
            if (book == null) {
                player.sendMessage(ChatColor.RED + "No order book for " + commodity);
                return true;
            }

            Order order = book.getOrder(orderId);
            if (order == null) {
                player.sendMessage(ChatColor.RED + "Order not found.");
                return true;
            }

            if (!order.getPlayerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Not your order.");
                return true;
            }

            if (api.cancelOrder(commodity, orderId)) {
                player.sendMessage(ChatColor.GREEN + "Order cancelled.");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to cancel.");
            }
            return true;
        }

        // Show player's orders
        Map<String, List<Order>> orders = api.getPlayerOrders(player.getUniqueId());

        if (orders.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No active orders.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "=== Your Orders ===");

        for (Map.Entry<String, List<Order>> entry : orders.entrySet()) {
            String commodity = entry.getKey();
            player.sendMessage(ChatColor.YELLOW + commodity + ":");

            for (Order order : entry.getValue()) {
                String typeStr = order.getType() == Order.OrderType.BUY ? ChatColor.GREEN + "BUY"
                        : ChatColor.RED + "SELL";
                player.sendMessage("  " + typeStr + ChatColor.GRAY + " - " +
                        order.getRemainingAmount() + "x @ " +
                        String.format("%.2f", order.getPricePerUnit()) +
                        " (ID: " + order.getOrderId() + ")");
            }
        }

        return true;
    }

    private boolean handlePrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coinop price <commodity>");
            return true;
        }

        String commodity = args[1].toUpperCase();

        double buyPrice = api.getBestBuyPrice(commodity);
        double sellPrice = api.getBestSellPrice(commodity);
        long volume = api.get24hVolume(commodity);
        double vwap = api.get24hVWAP(commodity);

        sender.sendMessage(ChatColor.GOLD + "=== " + commodity + " ===");

        if (buyPrice > 0) {
            sender.sendMessage(ChatColor.GREEN + "Best Buy: " + ChatColor.WHITE +
                    String.format("%.2f", buyPrice));
        } else {
            sender.sendMessage(ChatColor.GRAY + "Best Buy: No orders");
        }

        if (sellPrice > 0) {
            sender.sendMessage(ChatColor.RED + "Best Sell: " + ChatColor.WHITE +
                    String.format("%.2f", sellPrice));
        } else {
            sender.sendMessage(ChatColor.GRAY + "Best Sell: No orders");
        }

        sender.sendMessage(ChatColor.AQUA + "24h Volume: " + ChatColor.WHITE + formatNumber(volume));

        if (vwap > 0) {
            sender.sendMessage(ChatColor.BLUE + "24h VWAP: " + ChatColor.WHITE +
                    String.format("%.2f", vwap));
        }

        OrderBook book = api.getOrderBook(commodity);
        if (book != null) {
            sender.sendMessage(ChatColor.YELLOW + "Buy Orders: " + ChatColor.WHITE +
                    book.getBuyOrderCount() + ChatColor.GRAY +
                    " (" + formatNumber(book.getTotalBuyVolume()) + " items)");
            sender.sendMessage(ChatColor.YELLOW + "Sell Orders: " + ChatColor.WHITE +
                    book.getSellOrderCount() + ChatColor.GRAY +
                    " (" + formatNumber(book.getTotalSellVolume()) + " items)");
        }

        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coinop history <commodity>");
            return true;
        }

        String commodity = args[1].toUpperCase();
        List<Trade> trades = api.getRecentTrades(commodity, 10);

        if (trades.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No recent trades for " + commodity);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Recent: " + commodity + " ===");

        for (Trade trade : trades) {
            String timeAgo = formatTimeAgo(trade.getTimestamp());
            sender.sendMessage(ChatColor.GRAY + timeAgo + ": " + ChatColor.WHITE +
                    trade.getAmount() + "x @ " + String.format("%.2f", trade.getPrice()));
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== CoinOp Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/coinop buy <item> <amount> <price>" +
                ChatColor.GRAY + " - Place buy order");
        sender.sendMessage(ChatColor.YELLOW + "/coinop sell <item> <amount> <price>" +
                ChatColor.GRAY + " - Place sell order");
        sender.sendMessage(ChatColor.YELLOW + "/coinop instant <buy|sell> <item> <amount>" +
                ChatColor.GRAY + " - Market order");
        sender.sendMessage(ChatColor.YELLOW + "/coinop orders" +
                ChatColor.GRAY + " - View your orders");
        sender.sendMessage(ChatColor.YELLOW + "/coinop price <item>" +
                ChatColor.GRAY + " - Check prices");
        sender.sendMessage(ChatColor.YELLOW + "/coinop history <item>" +
                ChatColor.GRAY + " - Recent trades");
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    player.getInventory().setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }

        player.updateInventory();
    }

    private void giveItems(Player player, Material material, int amount) {
        int stackSize = material.getMaxStackSize();
        int fullStacks = amount / stackSize;
        int remainder = amount % stackSize;

        for (int i = 0; i < fullStacks; i++) {
            player.getInventory().addItem(new ItemStack(material, stackSize));
        }

        if (remainder > 0) {
            player.getInventory().addItem(new ItemStack(material, remainder));
        }

        player.updateInventory();
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

    private String formatTimeAgo(long timestamp) {
        long seconds = (System.currentTimeMillis() - timestamp) / 1000;

        if (seconds < 60)
            return seconds + "s ago";
        if (seconds < 3600)
            return (seconds / 60) + "m ago";
        if (seconds < 86400)
            return (seconds / 3600) + "h ago";
        return (seconds / 86400) + "d ago";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("buy", "sell", "instant", "orders", "price", "history", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("instant")) {
                completions.addAll(Arrays.asList("buy", "sell"));
            } else if (subCommand.equals("orders")) {
                completions.add("cancel");
            } else if (!subCommand.equals("help")) {
                completions.addAll(Arrays.stream(Material.values())
                        .map(Material::name)
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("instant") || subCommand.equals("buy") || subCommand.equals("sell")) {
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
