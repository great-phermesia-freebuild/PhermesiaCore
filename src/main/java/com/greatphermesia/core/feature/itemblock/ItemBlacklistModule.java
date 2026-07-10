package com.greatphermesia.core.feature.itemblock;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ItemBlacklistModule implements PluginModule, Listener, CommandExecutor {

    private final PhermesiaCorePlugin plugin;
    private final Map<Integer, BlacklistEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentPage = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, Integer>> slotEntryMap = new ConcurrentHashMap<>();

    private File dataFile;
    private int nextId = 1;
    private int scanTaskId = -1;

    public ItemBlacklistModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "ItemBlacklist";
    }

    @Override
    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "itemblock.yml");
        loadData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("itemblock") != null) {
            plugin.getCommand("itemblock").setExecutor(this);
        }

        long intervalTicks = Math.max(1L, plugin.getConfig().getLong("itemblock.scan-interval-seconds", 5L)) * 20L;
        scanTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scanPlayer(player);
            }
        }, intervalTicks, intervalTicks);

        plugin.getLogger().info("[ItemBlacklist] Module enabled.");
    }

    @Override
    public void disable() {
        if (scanTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scanTaskId);
            scanTaskId = -1;
        }
        saveData();
        plugin.getLogger().info("[ItemBlacklist] Module stopped.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }
        openGui(player, 1);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayer(event.getPlayer()), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayer(player), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!event.getView().title().equals(guiTitle())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayer(player), 1L);
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        int page = currentPage.getOrDefault(player.getUniqueId(), 1);

        if (slot == 45) {
            if (page > 1) {
                openGui(player, page - 1);
            }
            return;
        }

        if (slot == 52) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                player.sendMessage(ChatUtil.color("&cYou are not holding anything."));
                return;
            }

            if (addEntry(held)) {
                player.sendMessage(ChatUtil.color("&aBlacklisted &e" + held.getType() + "&a."));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    scanPlayer(online);
                }
                saveData();
                openGui(player, page);
            } else {
                player.sendMessage(ChatUtil.color("&cThat item is already blacklisted."));
            }
            return;
        }

        if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil(Math.max(1.0D, entries.size()) / 45.0D));
            if (page < totalPages) {
                openGui(player, page + 1);
            } else {
                player.closeInventory();
            }
            return;
        }

        if (slot == 49) {
            return;
        }

        if (slot >= 0 && slot <= 44) {
            Map<Integer, Integer> mapped = slotEntryMap.get(player.getUniqueId());
            if (mapped == null) {
                return;
            }

            Integer entryId = mapped.get(slot);
            if (entryId == null) {
                return;
            }

            BlacklistEntry entry = entries.get(entryId);
            if (entry == null) {
                return;
            }

            String label = entry.label == null ? "item" : entry.label;
            if (event.isRightClick()) {
                player.getInventory().addItem(entry.item.clone());
                player.sendMessage(ChatUtil.color("&bGave you a copy of &e" + label + "&b."));
                return;
            }

            entries.remove(entryId);
            player.sendMessage(ChatUtil.color("&aRemoved &e" + label + "&a from the blacklist."));
            saveData();
            openGui(player, page);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayer(player), 1L);
    }

    private boolean addEntry(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (isBlacklisted(item)) {
            return false;
        }

        ItemStack clone = item.clone();
        clone.setAmount(1);
        int id = nextId++;
        entries.put(id, new BlacklistEntry(id, clone, clone.getType().toString()));
        return true;
    }

    private boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        for (BlacklistEntry entry : entries.values()) {
            if (isSameItem(entry.item, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        ItemStack ac = a.clone();
        ItemStack bc = b.clone();
        ac.setAmount(1);
        bc.setAmount(1);
        return ac.isSimilar(bc);
    }

    private void scanPlayer(Player player) {
        if (player.hasPermission("itemblock.whitelist")) {
            return;
        }
        if (entries.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        boolean hit = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (isBlacklisted(stack)) {
                inventory.setItem(i, null);
                hit = true;
            }
        }

        if (hit) {
            player.sendMessage(ChatUtil.color("&cA blacklisted item was removed from your inventory."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.9f);
        }
    }

    private void openGui(Player player, int requestedPage) {
        List<BlacklistEntry> sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator.comparingInt(entry -> entry.id));

        int perPage = 45;
        int total = sorted.size();
        int totalPages = Math.max(1, (int) Math.ceil(Math.max(1.0D, total) / perPage));
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        currentPage.put(player.getUniqueId(), page);
        Map<Integer, Integer> mapped = new HashMap<>();
        slotEntryMap.put(player.getUniqueId(), mapped);

        Inventory inv = Bukkit.createInventory(null, 54, guiTitle());
        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, total);
        int slot = 0;

        for (int i = startIndex; i < endIndex; i++) {
            BlacklistEntry entry = sorted.get(i);
            ItemStack display = entry.item.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.lore(ChatUtil.components(List.of(
                        ChatUtil.color("&7Left-click &c>> Remove from blacklist"),
                        ChatUtil.color("&7Right-click &b>> Get a copy")
                )));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                display.setItemMeta(meta);
            }

            inv.setItem(slot, display);
            mapped.put(slot, entry.id);
            slot++;
        }

        boolean bedrock = isFloodgatePlayer(player.getUniqueId());
        ItemStack cyan = bedrock ? null : namedItem(Material.CYAN_STAINED_GLASS_PANE, " ", List.of());
        ItemStack lightBlue = bedrock ? null : namedItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());

        if (!bedrock) {
            for (int i = slot; i < 45; i++) {
                inv.setItem(i, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }

        inv.setItem(45, page > 1 ? namedItem(Material.ARROW, "&b&l<< Previous", List.of()) : cyan);
        inv.setItem(46, lightBlue);
        inv.setItem(47, cyan);
        inv.setItem(48, lightBlue);

        ItemStack info = namedItem(Material.NETHER_STAR, "&f&lBlacklist Info", List.of(
                "&7Blocked: &c" + entries.size() + " item(s)",
                "&7Page &f" + page + " &7of &f" + totalPages
        ));
        inv.setItem(49, info);
        inv.setItem(50, lightBlue);
        inv.setItem(51, cyan);

        ItemStack add = namedItem(Material.LIME_DYE, "&a&l+ Add Item", List.of(
                "&7Hold the item in your &fhand",
                "&7then click this to blacklist it"
        ));
        inv.setItem(52, add);

        inv.setItem(53, page < totalPages
                ? namedItem(Material.ARROW, "&b&lNext >>", List.of())
                : namedItem(Material.BARRIER, "&c&lClose", List.of()));

        player.openInventory(inv);
    }

    private boolean isFloodgatePlayer(UUID uuid) {
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null) {
            floodgate = Bukkit.getPluginManager().getPlugin("Floodgate");
        }
        if (floodgate == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isFloodgatePlayer.invoke(instance, uuid);
            return result instanceof Boolean value && value;
        } catch (Exception ignored) {
            return false;
        }
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(ChatUtil.component(name));
            meta.lore(ChatUtil.components(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component guiTitle() {
        return ChatUtil.component(plugin.getConfig().getString("itemblock.gui-title", "&0Item Blacklist"));
    }

    private void loadData() {
        entries.clear();
        nextId = 1;

        ConfigurationSection root;
        boolean migrated = false;
        if (dataFile.exists()) {
            root = YamlConfiguration.loadConfiguration(dataFile);
            migrated = true;
        } else {
            root = plugin.getConfig().getConfigurationSection("itemblock-data");
        }

        if (root == null) {
            saveData();
            return;
        }

        nextId = Math.max(1, root.getInt("next-id", 1));

        ConfigurationSection section = root.getConfigurationSection("entries");
        if (section == null) {
            if (migrated) {
                if (!dataFile.delete()) {
                    plugin.getLogger().warning("Migrated itemblock.yml into config.yml, but could not delete the old file.");
                }
                saveData();
            }
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key + ".item");
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                item.setAmount(1);
                String label = section.getString(key + ".label", item.getType().toString());
                entries.put(id, new BlacklistEntry(id, item, label));
                nextId = Math.max(nextId, id + 1);
            } catch (NumberFormatException ignored) {
            }
        }

        if (migrated) {
            if (!dataFile.delete()) {
                plugin.getLogger().warning("Migrated itemblock.yml into config.yml, but could not delete the old file.");
            }
            saveData();
        }
    }

    private void saveData() {
        plugin.getConfig().set("itemblock-data.entries", null);
        plugin.getConfig().set("itemblock-data.next-id", nextId);
        for (BlacklistEntry entry : entries.values()) {
            String path = "itemblock-data.entries." + entry.id;
            plugin.getConfig().set(path + ".item", entry.item);
            plugin.getConfig().set(path + ".label", entry.label);
        }

        plugin.saveConfig();
    }

    private static final class BlacklistEntry {
        private final int id;
        private final ItemStack item;
        private final String label;

        private BlacklistEntry(int id, ItemStack item, String label) {
            this.id = id;
            this.item = item;
            this.label = label;
        }
    }
}
