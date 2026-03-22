package com.coinop.anvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Anvil rework - fixes "Too Expensive!" cap
// Replaces exponential cost (2^n - 1) with linear cost based on material rarity
public class AnvilReworkListener implements Listener {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final boolean extractionEnabled;
    private final int maxCost;

    // Material rarity costs
    private static final Map<Material, Integer> MATERIAL_RARITY_COST = new HashMap<>();
    // Enchantment rarity costs (per level)
    private static final Map<Enchantment, Integer> ENCHANTMENT_RARITY_COST = new HashMap<>();

    private final Map<UUID, AnvilSession> activeSessions;

    static {
        // Materials
        MATERIAL_RARITY_COST.put(Material.NETHERITE_INGOT, 15);
        MATERIAL_RARITY_COST.put(Material.DIAMOND, 10);
        MATERIAL_RARITY_COST.put(Material.EMERALD, 8);
        MATERIAL_RARITY_COST.put(Material.GOLD_INGOT, 5);
        MATERIAL_RARITY_COST.put(Material.IRON_INGOT, 3);
        MATERIAL_RARITY_COST.put(Material.COPPER_INGOT, 2);
        MATERIAL_RARITY_COST.put(Material.STONE, 1);

        // Enchantments (cost per level)
        registerEnchantmentCost("MENDING", 10);
        registerEnchantmentCost("SILK_TOUCH", 8);
        registerEnchantmentCost("FORTUNE", 6);
        registerEnchantmentCost("LOOTING", 5);
        registerEnchantmentCost("SHARPNESS", 3);
        registerEnchantmentCost("EFFICIENCY", 3);
        registerEnchantmentCost("UNBREAKING", 4);
        registerEnchantmentCost("PROTECTION", 3);
        registerEnchantmentCost("FEATHER_FALLING", 3);
        registerEnchantmentCost("FIRE_PROTECTION", 3);
        registerEnchantmentCost("BLAST_PROTECTION", 3);
        registerEnchantmentCost("PROJECTILE_PROTECTION", 3);
        registerEnchantmentCost("RESPIRATION", 4);
        registerEnchantmentCost("DEPTH_STRIDER", 4);
        registerEnchantmentCost("AQUA_AFFINITY", 5);
        registerEnchantmentCost("THORNS", 6);
        registerEnchantmentCost("SWEEPING", 3);
        registerEnchantmentCost("KNOCKBACK", 2);
        registerEnchantmentCost("FIRE_ASPECT", 4);
        registerEnchantmentCost("POWER", 3);
        registerEnchantmentCost("PUNCH", 2);
        registerEnchantmentCost("FLAME", 4);
        registerEnchantmentCost("INFINITY", 8);
        registerEnchantmentCost("LUCK_OF_THE_SEA", 4);
        registerEnchantmentCost("LURE", 4);
        registerEnchantmentCost("QUICK_CHARGE", 3);
        registerEnchantmentCost("PIERCING", 3);
        registerEnchantmentCost("MULTISHOT", 5);
        registerEnchantmentCost("SOUL_SPEED", 8);
        registerEnchantmentCost("SWIFT_SNEAK", 6);
    }

    private static void registerEnchantmentCost(String name, int cost) {
        Enchantment enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(name.toLowerCase()));
        if (enchantment != null) {
            ENCHANTMENT_RARITY_COST.put(enchantment, cost);
        }
    }

    public AnvilReworkListener(JavaPlugin plugin, boolean enabled, boolean extractionEnabled) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.extractionEnabled = extractionEnabled;
        this.maxCost = 0; // Unlimited
        this.activeSessions = new HashMap<>();
    }

    public AnvilReworkListener(JavaPlugin plugin, boolean enabled, boolean extractionEnabled, int maxCost) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.extractionEnabled = extractionEnabled;
        this.maxCost = maxCost;
        this.activeSessions = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled)
            return;

        AnvilInventory anvil = event.getInventory();
        ItemStack leftItem = anvil.getItem(0);
        ItemStack rightItem = anvil.getItem(1);
        ItemStack resultItem = event.getResult();

        if (leftItem == null || leftItem.getType() == Material.AIR) {
            return;
        }

        // Calculate new repair cost
        int newCost = calculateNewCost(leftItem, rightItem, resultItem);

        if (resultItem != null && resultItem.getType() != Material.AIR) {
            ItemMeta meta = resultItem.getItemMeta();
            if (meta instanceof Repairable) {
                Repairable repairable = (Repairable) meta;

                // Reset work penalty (remove exponential scaling)
                repairable.setRepairCost(0);

                // Set new cost
                anvil.setRepairCost(Math.min(newCost, maxCost > 0 ? maxCost : Integer.MAX_VALUE));
            }
            resultItem.setItemMeta(meta);
            event.setResult(resultItem);
        }

        // Enchantment extraction
        if (extractionEnabled && rightItem != null && rightItem.getType() == Material.BOOK) {
            handleEnchantmentExtraction(event, leftItem, rightItem);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!enabled)
            return;
        if (event.getInventory().getType() != InventoryType.ANVIL)
            return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT)
            return;
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    // Linear cost: base + material + enchantment (instead of 2^n - 1)
    private int calculateNewCost(ItemStack leftItem, ItemStack rightItem, ItemStack resultItem) {
        int baseCost = 0;

        // Prior work penalty (linear instead of exponential)
        int priorWork = 0;
        if (leftItem.hasItemMeta() && leftItem.getItemMeta() instanceof Repairable) {
            priorWork = ((Repairable) leftItem.getItemMeta()).getRepairCost();
        }

        // Material cost
        if (rightItem != null) {
            baseCost += getMaterialCost(rightItem.getType());
        }

        // Enchantment cost
        if (rightItem != null && rightItem.hasItemMeta()) {
            ItemMeta rightMeta = rightItem.getItemMeta();

            if (rightMeta instanceof EnchantmentStorageMeta) {
                // Enchanted book
                EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) rightMeta;
                for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
                    baseCost += getEnchantmentCost(entry.getKey(), entry.getValue());
                }
            } else {
                // Regular item
                for (Map.Entry<Enchantment, Integer> entry : rightMeta.getEnchants().entrySet()) {
                    baseCost += getEnchantmentCost(entry.getKey(), entry.getValue());
                }
            }
        }

        // Linear increment for prior work
        baseCost += priorWork * 2;

        return Math.max(1, baseCost);
    }

    private int getMaterialCost(Material material) {
        return MATERIAL_RARITY_COST.getOrDefault(material, 1);
    }

    private int getEnchantmentCost(Enchantment enchantment, int level) {
        int baseCost = ENCHANTMENT_RARITY_COST.getOrDefault(enchantment, 2);
        return baseCost * level;
    }

    // Extract enchants from item to book
    private void handleEnchantmentExtraction(PrepareAnvilEvent event, ItemStack leftItem, ItemStack rightItem) {
        if (!leftItem.hasItemMeta())
            return;

        ItemMeta leftMeta = leftItem.getItemMeta();
        Map<Enchantment, Integer> enchants = leftMeta.getEnchants();

        if (enchants.isEmpty())
            return;

        // Create enchanted book
        ItemStack resultBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) resultBook.getItemMeta();

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            bookMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
        }

        resultBook.setItemMeta(bookMeta);

        // Calculate extraction cost
        int extractionCost = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            extractionCost += getEnchantmentCost(entry.getKey(), entry.getValue());
        }

        AnvilInventory anvil = event.getInventory();
        anvil.setRepairCost(extractionCost);
        event.setResult(resultBook);
    }

    public boolean canExtractEnchantments(ItemStack item) {
        if (!extractionEnabled)
            return false;
        if (item == null || !item.hasItemMeta())
            return false;

        ItemMeta meta = item.getItemMeta();
        return meta != null && !meta.getEnchants().isEmpty();
    }

    public int getExtractionCost(ItemStack item) {
        if (!canExtractEnchantments(item))
            return 0;

        int cost = 0;
        ItemMeta meta = item.getItemMeta();

        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            cost += getEnchantmentCost(entry.getKey(), entry.getValue());
        }

        return cost;
    }

    // Track active anvil sessions
    private static class AnvilSession {
        final UUID playerUuid;
        final long startTime;
        ItemStack originalItem;
        int originalCost;

        AnvilSession(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.startTime = System.currentTimeMillis();
        }
    }
}
