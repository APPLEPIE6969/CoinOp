package com.coinop.gui;

import com.coinop.config.CoinOpConfig;
import com.coinop.manager.CoinOpManager;
import com.coinop.model.Order;
import com.coinop.model.OrderBook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// GUI for the CoinOp market interface
public class CoinOpGUI implements Listener {

    private final CoinOpConfig config;
    private final CoinOpManager CoinOpManager;
    private final Map<UUID, GUISession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public enum GUIType {
        MAIN_MENU,
        CATEGORY_VIEW,
        COMMODITY_VIEW,
        ORDERS_VIEW,
        CONFIRM_PURCHASE,
        CONFIRM_SELL
    }

    public CoinOpGUI(JavaPlugin plugin, CoinOpConfig config, CoinOpManager CoinOpManager) {
        this.config = config;
        this.CoinOpManager = CoinOpManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        if (!config.isGuiEnabled()) {
            player.sendMessage(ChatColor.RED + "GUI is disabled. Use commands instead.");
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', config.getGuiTitle());
        int size = config.getGuiMainMenuSize();
        Inventory inventory = Bukkit.createInventory(new CoinOpHolder(GUIType.MAIN_MENU), size, title);

        addCategoryIcons(inventory);
        addUtilityIcons(inventory, player);

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), new GUISession(GUIType.MAIN_MENU, null, 0));
        playSound(player, "open");
    }

    public void openCategoryView(Player player, String category) {
        List<String> commodities = config.getCommoditiesInCategory(category);
        if (commodities.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Category not found or empty.");
            return;
        }

        String title = ChatColor.GOLD + category.substring(0, 1).toUpperCase() + category.substring(1);
        int size = config.getGuiMainMenuSize();
        Inventory inventory = Bukkit.createInventory(new CoinOpHolder(GUIType.CATEGORY_VIEW, category), size, title);

        int slot = 0;
        for (String commodityId : commodities) {
            if (slot >= size - 9)
                break;

            ItemStack item = createCommodityIcon(commodityId);
            inventory.setItem(slot, item);
            slot++;
        }

        inventory.setItem(size - 5, createBackButton());

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), new GUISession(GUIType.CATEGORY_VIEW, category, 0));
        playSound(player, "click");
    }

    public void openCommodityView(Player player, String commodity, String category) {
        String title = ChatColor.GOLD + commodity.replace("_", " ");
        Inventory inventory = Bukkit.createInventory(new CoinOpHolder(GUIType.COMMODITY_VIEW, commodity), 45, title);

        OrderBook orderBook = CoinOpManager.getOrderBook(commodity);
        double bestBuyPrice = orderBook != null && orderBook.getBestSellOrder() != null
                ? orderBook.getBestSellOrder().getPricePerUnit()
                : 0;
        double bestSellPrice = orderBook != null && orderBook.getBestBuyOrder() != null
                ? orderBook.getBestBuyOrder().getPricePerUnit()
                : 0;

        // Instant Buy
        ItemStack instantBuy = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta buyMeta = instantBuy.getItemMeta();
        buyMeta.setDisplayName(ChatColor.GREEN + "Instant Buy");
        List<String> buyLore = new ArrayList<>();
        buyLore.add(ChatColor.GRAY + "Buy immediately at best price");
        buyLore.add(ChatColor.YELLOW + "Price: " + ChatColor.GREEN + "$" + String.format("%.2f", bestBuyPrice));
        buyLore.add("");
        buyLore.add(ChatColor.AQUA + "Left-click to buy 1");
        buyLore.add(ChatColor.AQUA + "Right-click to buy 64");
        buyLore.add(ChatColor.AQUA + "Shift+click to buy stack amount");
        buyMeta.setLore(buyLore);
        instantBuy.setItemMeta(buyMeta);
        inventory.setItem(11, instantBuy);

        // Instant Sell
        ItemStack instantSell = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta sellMeta = instantSell.getItemMeta();
        sellMeta.setDisplayName(ChatColor.RED + "Instant Sell");
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Sell immediately at best price");
        sellLore.add(ChatColor.YELLOW + "Price: " + ChatColor.RED + "$" + String.format("%.2f", bestSellPrice));
        sellLore.add("");
        sellLore.add(ChatColor.AQUA + "Left-click to sell 1");
        sellLore.add(ChatColor.AQUA + "Right-click to sell 64");
        sellLore.add(ChatColor.AQUA + "Shift+click to sell all");
        sellMeta.setLore(sellLore);
        instantSell.setItemMeta(sellMeta);
        inventory.setItem(15, instantSell);

        // Buy Order
        ItemStack buyOrder = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta buyOrderMeta = buyOrder.getItemMeta();
        buyOrderMeta.setDisplayName(ChatColor.GREEN + "Place Buy Order");
        List<String> buyOrderLore = new ArrayList<>();
        buyOrderLore.add(ChatColor.GRAY + "Set your own price");
        buyOrderLore.add(ChatColor.GRAY + "Order will fill when a seller matches");
        buyOrderLore.add("");
        buyOrderLore.add(ChatColor.AQUA + "Click to create order");
        buyOrderMeta.setLore(buyOrderLore);
        buyOrder.setItemMeta(buyOrderMeta);
        inventory.setItem(29, buyOrder);

        // Sell Order
        ItemStack sellOrder = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta sellOrderMeta = sellOrder.getItemMeta();
        sellOrderMeta.setDisplayName(ChatColor.RED + "Place Sell Order");
        List<String> sellOrderLore = new ArrayList<>();
        sellOrderLore.add(ChatColor.GRAY + "Set your own price");
        sellOrderLore.add(ChatColor.GRAY + "Order will fill when a buyer matches");
        sellOrderLore.add("");
        sellOrderLore.add(ChatColor.AQUA + "Click to create order");
        sellOrderMeta.setLore(sellOrderLore);
        sellOrder.setItemMeta(sellOrderMeta);
        inventory.setItem(33, sellOrder);

        // Market info
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Market Info");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Best Buy Price: " + ChatColor.GREEN + "$" + String.format("%.2f", bestBuyPrice));
        infoLore.add(ChatColor.GRAY + "Best Sell Price: " + ChatColor.RED + "$" + String.format("%.2f", bestSellPrice));
        if (orderBook != null) {
            infoLore.add(ChatColor.GRAY + "Buy Orders: " + ChatColor.AQUA + orderBook.getBuyOrderCount());
            infoLore.add(ChatColor.GRAY + "Sell Orders: " + ChatColor.AQUA + orderBook.getSellOrderCount());
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(13, info);

        inventory.setItem(36, createBackButton());

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), new GUISession(GUIType.COMMODITY_VIEW, commodity, 0));
        playSound(player, "click");
    }

    public void openOrdersView(Player player) {
        String title = ChatColor.GOLD + "Your Orders";
        Inventory inventory = Bukkit.createInventory(new CoinOpHolder(GUIType.ORDERS_VIEW), 54, title);

        List<Order> orders = new ArrayList<>();
        Map<String, List<Order>> ordersByCommodity = CoinOpManager.getPlayerOrders(player.getUniqueId());
        for (List<Order> orderList : ordersByCommodity.values()) {
            orders.addAll(orderList);
        }

        int slot = 0;
        for (Order order : orders) {
            if (slot >= 45)
                break;

            ItemStack item = createOrderIcon(order);
            inventory.setItem(slot, item);
            slot++;
        }

        if (orders.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName(ChatColor.RED + "No Active Orders");
            empty.setItemMeta(emptyMeta);
            inventory.setItem(22, empty);
        }

        inventory.setItem(49, createBackButton());

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), new GUISession(GUIType.ORDERS_VIEW, null, 0));
        playSound(player, "click");
    }

    private void addCategoryIcons(Inventory inventory) {
        Map<String, String> categories = config.getCategories();
        int slot = 10;

        for (Map.Entry<String, String> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            Material iconMaterial = getIconMaterial(categoryName);

            ItemStack item = new ItemStack(iconMaterial);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + capitalize(categoryName));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to browse " + categoryName);
            meta.setLore(lore);
            item.setItemMeta(meta);

            inventory.setItem(slot, item);
            slot++;
            if (slot == 17)
                slot = 19;
            if (slot == 26)
                slot = 28;
            if (slot == 35)
                slot = 37;
        }
    }

    private void addUtilityIcons(Inventory inventory, Player player) {
        int size = inventory.getSize();

        ItemStack orders = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta ordersMeta = orders.getItemMeta();
        ordersMeta.setDisplayName(ChatColor.YELLOW + "Your Orders");
        List<String> ordersLore = new ArrayList<>();
        ordersLore.add(ChatColor.GRAY + "View and manage your orders");
        ordersMeta.setLore(ordersLore);
        orders.setItemMeta(ordersMeta);
        inventory.setItem(size - 5, orders);
    }

    private ItemStack createCommodityIcon(String commodityId) {
        Material material = Material.getMaterial(commodityId);
        if (material == null)
            material = Material.STONE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + capitalize(commodityId.replace("_", " ").toLowerCase()));

        OrderBook orderBook = CoinOpManager.getOrderBook(commodityId);
        List<String> lore = new ArrayList<>();

        if (orderBook != null) {
            double bestBuy = orderBook.getBestSellOrder() != null ? orderBook.getBestSellOrder().getPricePerUnit() : 0;
            double bestSell = orderBook.getBestBuyOrder() != null ? orderBook.getBestBuyOrder().getPricePerUnit() : 0;

            lore.add(ChatColor.GRAY + "Buy: " + ChatColor.GREEN + "$" + String.format("%.2f", bestBuy));
            lore.add(ChatColor.GRAY + "Sell: " + ChatColor.RED + "$" + String.format("%.2f", bestSell));
        } else {
            lore.add(ChatColor.GRAY + "No orders available");
        }

        lore.add("");
        lore.add(ChatColor.AQUA + "Click to view details");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createOrderIcon(Order order) {
        Material material = Material.getMaterial(order.getCommodityId());
        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String typeStr = order.getType() == Order.OrderType.BUY ? "Buy Order" : "Sell Order";
        meta.setDisplayName((order.getType() == Order.OrderType.BUY ? ChatColor.GREEN : ChatColor.RED) + typeStr);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Item: " + ChatColor.YELLOW + order.getCommodityId());
        lore.add(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + order.getRemainingAmount() + "/" + order.getAmount());
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GREEN + "$" + String.format("%.2f", order.getPricePerUnit()));
        lore.add(ChatColor.GRAY + "Total Value: " + ChatColor.GOLD + "$"
                + String.format("%.2f", order.getPricePerUnit() * order.getRemainingAmount()));
        lore.add("");
        lore.add(ChatColor.RED + "Shift+click to cancel");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Back");
        item.setItemMeta(meta);
        return item;
    }

    private Material getIconMaterial(String category) {
        String materialName = config.getGuiCategoryIcon(category);
        if (materialName != null) {
            Material material = Material.getMaterial(materialName);
            if (material != null)
                return material;
        }

        switch (category.toLowerCase()) {
            case "ores":
                return Material.DIAMOND;
            case "crops":
                return Material.WHEAT;
            case "mob-drops":
                return Material.BLAZE_ROD;
            case "building":
                return Material.COBBLESTONE;
            default:
                return Material.CHEST;
        }
    }

    private void playSound(Player player, String sound) {
        try {
            String soundName = config.getGuiSound(sound);
            if (soundName != null && !soundName.isEmpty()) {
                Sound soundEffect = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), soundEffect, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException e) {
            // Sound not found
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CoinOpHolder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();
        CoinOpHolder holder = (CoinOpHolder) event.getInventory().getHolder();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        GUISession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        playSound(player, "click");

        switch (holder.getType()) {
            case MAIN_MENU:
                handleMainMenuClick(player, clicked, session);
                break;
            case CATEGORY_VIEW:
                handleCategoryClick(player, clicked, holder, session);
                break;
            case COMMODITY_VIEW:
                handleCommodityClick(player, clicked, holder, event.getClick(), session);
                break;
            case ORDERS_VIEW:
                handleOrdersClick(player, clicked, event.getClick(), session);
                break;
            default:
                break;
        }
    }

    private void handleMainMenuClick(Player player, ItemStack clicked, GUISession session) {
        String displayName = clicked.getItemMeta().getDisplayName();

        Map<String, String> categories = config.getCategories();
        for (String category : categories.keySet()) {
            if (displayName.contains(capitalize(category))) {
                openCategoryView(player, category);
                return;
            }
        }

        if (clicked.getType() == Material.WRITABLE_BOOK && displayName.contains("Orders")) {
            openOrdersView(player);
        }
    }

    private void handleCategoryClick(Player player, ItemStack clicked, CoinOpHolder holder, GUISession session) {
        if (clicked.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }

        String commodity = getCommodityFromItem(clicked);
        if (commodity != null) {
            openCommodityView(player, commodity, holder.getData());
        }
    }

    private void handleCommodityClick(Player player, ItemStack clicked, CoinOpHolder holder, ClickType clickType,
            GUISession session) {
        String commodity = holder.getData();

        if (clicked.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }

        // Instant Buy
        if (clicked.getType() == Material.EMERALD_BLOCK) {
            int amount = clickType == ClickType.RIGHT ? 64 : 1;
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                amount = 2304;
            }
            handleInstantBuy(player, commodity, amount);
        }
        // Instant Sell
        else if (clicked.getType() == Material.REDSTONE_BLOCK) {
            int amount = clickType == ClickType.RIGHT ? 64 : 1;
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                amount = -1;
            }
            handleInstantSell(player, commodity, amount);
        }
        // Buy Order
        else if (clicked.getType() == Material.WRITABLE_BOOK
                && clicked.getItemMeta().getDisplayName().contains("Buy Order")) {
            player.closeInventory();
            player.sendMessage(
                    ChatColor.YELLOW + "Use /coinopbuy " + commodity + " <amount> <price> to place a buy order");
        }
        // Sell Order
        else if (clicked.getType() == Material.WRITABLE_BOOK
                && clicked.getItemMeta().getDisplayName().contains("Sell Order")) {
            player.closeInventory();
            player.sendMessage(
                    ChatColor.YELLOW + "Use /coinopsell " + commodity + " <amount> <price> to place a sell order");
        }
    }

    private void handleOrdersClick(Player player, ItemStack clicked, ClickType clickType, GUISession session) {
        if (clicked.getType() == Material.ARROW) {
            openMainMenu(player);
            return;
        }

        if (clicked.getType() == Material.BARRIER)
            return;

        if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
            player.sendMessage(ChatColor.RED + "Use /coinoporders cancel <orderId> to cancel an order");
        }
    }

    private void handleInstantBuy(Player player, String commodity, int amount) {
        player.closeInventory();
        player.performCommand("coinopinstant buy " + commodity + " " + amount);
    }

    private void handleInstantSell(Player player, String commodity, int amount) {
        player.closeInventory();
        String amountStr = amount == -1 ? "all" : String.valueOf(amount);
        player.performCommand("coinopinstant sell " + commodity + " " + amountStr);
    }

    private String getCommodityFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return null;
        return item.getType().name();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    public void shutdown() {
        sessions.clear();
    }

    // Custom inventory holder for tracking
    private static class CoinOpHolder implements InventoryHolder {
        private final GUIType type;
        private final String data;

        public CoinOpHolder(GUIType type) {
            this(type, null);
        }

        public CoinOpHolder(GUIType type, String data) {
            this.type = type;
            this.data = data;
        }

        public GUIType getType() {
            return type;
        }

        public String getData() {
            return data;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // Session tracking
    private static class GUISession {
        private final GUIType type;
        private final String data;
        private final int page;

        public GUISession(GUIType type, String data, int page) {
            this.type = type;
            this.data = data;
            this.page = page;
        }

        public GUIType getType() {
            return type;
        }

        public String getData() {
            return data;
        }

        public int getPage() {
            return page;
        }
    }
}
