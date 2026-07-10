package com.greatphermesia.core.feature.cosmetics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class CosmeticsModule implements PluginModule, Listener, CommandExecutor {

    private final PhermesiaCorePlugin plugin;
    private final NamespacedKey trailKey;
    private final NamespacedKey glowKey;
    private final NamespacedKey chatKey;
    private final Map<UUID, Location> lastTrailLocations = new ConcurrentHashMap<>();
    private final Map<UUID, ChatColorPreset> selectedChatColors = new ConcurrentHashMap<>();
    private int effectTaskId = -1;

    public CosmeticsModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
        this.trailKey = new NamespacedKey(plugin, "cosmetics_trail");
        this.glowKey = new NamespacedKey(plugin, "cosmetics_glow");
        this.chatKey = new NamespacedKey(plugin, "cosmetics_chat");
    }

    @Override
    public String name() {
        return "Cosmetics";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("cosmetics");
        startEffectTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            syncStoredState(player);
            applyGlowState(player);
        }

        plugin.getLogger().info("[Cosmetics] Module enabled.");
    }

    @Override
    public void disable() {
        stopEffectTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearGlowTeam(player);
            if (getSelectedGlow(player) != null) {
                player.setGlowing(false);
            }
        }

        lastTrailLocations.clear();
        selectedChatColors.clear();
        plugin.getLogger().info("[Cosmetics] Module stopped.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        if (command.getName().equalsIgnoreCase("cosmetics")) {
            openMainMenu(player);
            return true;
        }

        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (holder.type) {
            case MAIN -> handleMainClick(player, slot);
            case TRAILS -> handleTrailClick(player, slot);
            case GLOW -> handleGlowClick(player, slot);
            case CHAT -> handleChatClick(player, slot);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCosmeticChat(AsyncChatEvent event) {
        ChatColorPreset preset = selectedChatColors.get(event.getPlayer().getUniqueId());
        if (preset == null) {
            return;
        }

        event.message(event.message().color(preset.color()));
        ChatRenderer currentRenderer = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> currentRenderer.render(source, sourceDisplayName, message, viewer).color(preset.color()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        syncStoredState(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyGlowState(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTrailLocations.remove(event.getPlayer().getUniqueId());
        clearGlowTeam(event.getPlayer());
        selectedChatColors.remove(event.getPlayer().getUniqueId());
    }

    private void bindCommand(String commandName) {
        if (plugin.getCommand(commandName) == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + commandName);
            return;
        }
        plugin.getCommand(commandName).setExecutor(this);
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openTrailMenu(player);
            case 13 -> openGlowMenu(player);
            case 15 -> {
                openChatMenu(player);
            }
            case 22 -> {
                clearAllCosmetics(player);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
                openMainMenu(player);
            }
            case 35 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleTrailClick(Player player, int slot) {
        if (slot == 45) {
            clearTrail(player);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
            openTrailMenu(player);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        TrailPreset preset = TrailPreset.bySlot(slot);
        if (preset == null) {
            return;
        }

        TrailPreset current = getSelectedTrail(player);
        if (preset == current) {
            clearTrail(player);
            player.sendMessage(Component.text("Trail disabled.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
        } else {
            selectTrail(player, preset);
            player.sendMessage(Component.text("Trail set to ", NamedTextColor.GRAY).append(preset.displayName()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
        }
        openTrailMenu(player);
    }

    private void handleGlowClick(Player player, int slot) {
        if (slot == 45) {
            clearGlow(player);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
            openGlowMenu(player);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        GlowPreset preset = GlowPreset.bySlot(slot);
        if (preset == null) {
            return;
        }

        GlowPreset current = getSelectedGlow(player);
        if (preset == current) {
            clearGlow(player);
            player.sendMessage(Component.text("Glow effect disabled.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
        } else {
            selectGlow(player, preset);
            player.sendMessage(Component.text("Glow effect set to ", NamedTextColor.GRAY).append(preset.displayName()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
        }
        openGlowMenu(player);
    }

    private void handleChatClick(Player player, int slot) {
        if (!canUseChatColors(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        if (slot == 45) {
            clearChatColor(player);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
            openChatMenu(player);
            return;
        }

        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        ChatColorPreset preset = ChatColorPreset.bySlot(slot);
        if (preset == null) {
            return;
        }

        ChatColorPreset current = getSelectedChatColor(player);
        if (preset == current) {
            clearChatColor(player);
            player.sendMessage(Component.text("Chat colour cleared.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
        } else {
            selectChatColor(player, preset);
            player.sendMessage(Component.text("Chat colour set to ", NamedTextColor.GRAY).append(preset.displayName()));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
        }
        openChatMenu(player);
    }

    private void openMainMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        syncStoredState(player);

        Inventory inventory = createMenu(MenuType.MAIN, player);
        inventory.setItem(4, buildOverviewItem(player));
        inventory.setItem(11, buildTrailButton(player));
        inventory.setItem(13, buildGlowButton(player));
        inventory.setItem(15, buildChatButton(player));
        inventory.setItem(22, buildResetButton());
        inventory.setItem(35, buildCloseButton());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.1f);
    }

    private void openTrailMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Inventory inventory = createMenu(MenuType.TRAILS, player);
        inventory.setItem(4, buildMenuHeader(
                Material.END_ROD,
                Component.text("Particle Trails", TextColor.color(205, 255, 231)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Choose a soft trail effect that follows your movement.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click the same trail again to disable it.", NamedTextColor.YELLOW)
                )));

        for (TrailPreset preset : TrailPreset.values()) {
            inventory.setItem(preset.slot(), buildTrailItem(player, preset));
        }

        inventory.setItem(45, buildToggleOffButton(
                Component.text("Disable Trails", NamedTextColor.GRAY),
                List.of(Component.text("Remove your active trail.", NamedTextColor.GRAY))));
        inventory.setItem(49, buildBackButton());
        inventory.setItem(53, buildCloseButton());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.15f);
    }

    private void openGlowMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Inventory inventory = createMenu(MenuType.GLOW, player);
        inventory.setItem(4, buildMenuHeader(
                Material.BEACON,
                Component.text("Glow Effects", TextColor.color(255, 241, 200)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A soft aura and glowing outline for your character.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click the same glow again to disable it.", NamedTextColor.YELLOW)
                )));

        for (GlowPreset preset : GlowPreset.values()) {
            inventory.setItem(preset.slot(), buildGlowItem(player, preset));
        }

        inventory.setItem(45, buildToggleOffButton(
                Component.text("Disable Glow", NamedTextColor.GRAY),
                List.of(Component.text("Remove your active glow effect.", NamedTextColor.GRAY))));
        inventory.setItem(49, buildBackButton());
        inventory.setItem(53, buildCloseButton());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.15f);
    }

    private void openChatMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        syncStoredState(player);

        if (!canUseChatColors(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        Inventory inventory = createMenu(MenuType.CHAT, player);
        inventory.setItem(4, buildMenuHeader(
                Material.WRITABLE_BOOK,
                Component.text("Pastel Chat Colours", TextColor.color(255, 224, 243)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("These colours are soft, readable, and available to everyone.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Your message text changes, not your name.", NamedTextColor.YELLOW)
                )));

        for (ChatColorPreset preset : ChatColorPreset.values()) {
            inventory.setItem(preset.slot(), buildChatColorItem(player, preset));
        }

        inventory.setItem(45, buildToggleOffButton(
                Component.text("Reset Chat Colour", NamedTextColor.GRAY),
                List.of(Component.text("Return your chat to the default colour.", NamedTextColor.GRAY))));
        inventory.setItem(49, buildBackButton());
        inventory.setItem(53, buildCloseButton());
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.15f);
    }

    private Inventory createMenu(MenuType type, Player player) {
        MenuHolder holder = new MenuHolder(type);
        Inventory inventory = Bukkit.createInventory(holder, type.size(), type.title());
        holder.inventory = inventory;

        if (!isFloodgatePlayer(player.getUniqueId())) {
            decorateMenu(inventory, type);
        }

        return inventory;
    }

    private void decorateMenu(Inventory inventory, MenuType type) {
        ItemStack base = decorativePane(type.basePane());
        ItemStack accent = decorativePane(type.accentPane());
        ItemStack highlight = decorativePane(type.highlightPane());

        int[] border = {
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 17,
                18, 26,
                27, 35,
                36, 44,
                45, 46, 47, 48, 49, 50, 51, 52, 53
        };
        int[] accentSlots = {10, 11, 12, 13, 14, 15, 16, 19, 25, 28, 34, 37, 38, 39, 40, 41, 42, 43};
        int[] highlightSlots = {21, 22, 23, 30, 31, 32};

        for (int slot : border) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, base.clone());
            }
        }

        for (int slot : accentSlots) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, accent.clone());
            }
        }

        for (int slot : highlightSlots) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, highlight.clone());
            }
        }
    }

    private ItemStack buildOverviewItem(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Your active selections are shown below.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Trail: ", NamedTextColor.GRAY).append(selectionText(getSelectedTrail(player))));
        lore.add(Component.text("Glow: ", NamedTextColor.GRAY).append(selectionText(getSelectedGlow(player))));
        lore.add(Component.text("Chat: ", NamedTextColor.GRAY).append(selectionText(getSelectedChatColor(player), canUseChatColors(player))));
        lore.add(Component.empty());
        lore.add(Component.text("Use the glowing buttons to open each menu.", NamedTextColor.YELLOW));

        return createItem(
                Material.NETHER_STAR,
                Component.text("Cosmetics Hub", TextColor.color(247, 236, 255)).decorate(TextDecoration.BOLD),
                lore,
                false,
                false);
    }

    private ItemStack buildTrailButton(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Movement trails made of soft particles.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Current trail: ", NamedTextColor.GRAY).append(selectionText(getSelectedTrail(player))));
        lore.add(Component.text("Click to browse the trail menu.", NamedTextColor.YELLOW));

        return createItem(
                Material.FEATHER,
                Component.text("Particle Trails", TextColor.color(201, 255, 231)).decorate(TextDecoration.BOLD),
                lore,
                false,
                false);
    }

    private ItemStack buildGlowButton(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A gentle aura and outline glow.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Current glow: ", NamedTextColor.GRAY).append(selectionText(getSelectedGlow(player))));
        lore.add(Component.text("Click to browse the glow menu.", NamedTextColor.YELLOW));

        return createItem(
                Material.BEACON,
                Component.text("Glow Effects", TextColor.color(255, 240, 202)).decorate(TextDecoration.BOLD),
                lore,
                false,
                false);
    }

    private ItemStack buildChatButton(Player player) {
        boolean allowed = canUseChatColors(player);
        List<Component> lore = new ArrayList<>();
        if (allowed) {
            lore.add(Component.text("Soft pastel chat colours for everyone.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Current chat colour: ", NamedTextColor.GRAY).append(selectionText(getSelectedChatColor(player), true)));
            lore.add(Component.text("Click to browse the chat colour menu.", NamedTextColor.YELLOW));
        } else {
            lore.add(ChatUtil.component("&7Sorry, only staff can use this for the moment"));
        }

        return createItem(
                allowed ? Material.WRITABLE_BOOK : Material.BARRIER,
                Component.text("Chat Colours", allowed ? TextColor.color(255, 224, 243) : NamedTextColor.RED).decorate(TextDecoration.BOLD),
                lore,
                false,
                false);
    }

    private ItemStack buildResetButton() {
        return createItem(
                Material.BARRIER,
                Component.text("Reset Cosmetics", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Clear your trail, glow, and chat colour.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to wipe all active cosmetics.", NamedTextColor.YELLOW)
                ),
                false,
                false);
    }

    private ItemStack buildCloseButton() {
        return createItem(
                Material.OAK_DOOR,
                Component.text("Close Menu", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text("Leave the cosmetics menu.", NamedTextColor.GRAY)),
                false,
                false);
    }

    private ItemStack buildBackButton() {
        return createItem(
                Material.ARROW,
                Component.text("Back", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text("Return to the main cosmetics hub.", NamedTextColor.GRAY)),
                false,
                false);
    }

    private ItemStack buildToggleOffButton(Component title, List<Component> lore) {
        return createItem(Material.GRAY_DYE, title, lore, false, false);
    }

    private ItemStack buildTrailItem(Player player, TrailPreset preset) {
        boolean selected = preset == getSelectedTrail(player);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(preset.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Preview: ", NamedTextColor.GRAY).append(Component.text("soft movement trail", preset.color())));
        lore.add(Component.empty());
        lore.add(selected
                ? Component.text("Currently selected", NamedTextColor.GREEN)
                : Component.text("Click to equip", NamedTextColor.YELLOW));

        return createItem(preset.material(), preset.displayName(), lore, selected, false);
    }

    private ItemStack buildGlowItem(Player player, GlowPreset preset) {
        boolean selected = preset == getSelectedGlow(player);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(preset.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Outline colour: ", NamedTextColor.GRAY).append(Component.text("glow", preset.outlineColor())));
        lore.add(Component.empty());
        lore.add(selected
                ? Component.text("Currently selected", NamedTextColor.GREEN)
                : Component.text("Click to equip", NamedTextColor.YELLOW));

        return createItem(preset.material(), preset.displayName(), lore, selected, false);
    }

    private ItemStack buildChatColorItem(Player player, ChatColorPreset preset) {
        boolean selected = preset == getSelectedChatColor(player);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(preset.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Sample: ", NamedTextColor.GRAY).append(Component.text("This is your chat colour.", preset.color())));
        lore.add(Component.empty());
        lore.add(selected
                ? Component.text("Currently selected", NamedTextColor.GREEN)
                : Component.text("Click to equip", NamedTextColor.YELLOW));

        return createItem(preset.material(), preset.displayName(), lore, selected, false);
    }

    private ItemStack buildMenuHeader(Material material, Component title, List<Component> lore) {
        return createItem(material, title, lore, false, false);
    }

    private ItemStack decorativePane(Material material) {
        return createItem(material, Component.empty(), List.of(), false, true);
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean selected, boolean hideTooltip) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (hideTooltip) {
                meta.setHideTooltip(true);
            }
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startEffectTask() {
        stopEffectTask();
        effectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickEffects, 20L, 5L);
    }

    private void stopEffectTask() {
        if (effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(effectTaskId);
            effectTaskId = -1;
        }
    }

    private void tickEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TrailPreset trail = getSelectedTrail(player);
            if (trail != null) {
                Location current = player.getLocation().clone().add(0.0D, 0.05D, 0.0D);
                Location previous = lastTrailLocations.put(player.getUniqueId(), current.clone());
                if (previous == null || previous.getWorld() == null || current.getWorld() == null
                        || !previous.getWorld().equals(current.getWorld()) || previous.distanceSquared(current) > 0.03D) {
                    trail.spawn(player, current);
                }
            } else {
                lastTrailLocations.remove(player.getUniqueId());
            }

            GlowPreset glow = getSelectedGlow(player);
            if (glow != null) {
                if (!player.isGlowing()) {
                    player.setGlowing(true);
                }
                glow.spawn(player, player.getLocation().clone().add(0.0D, 1.0D, 0.0D));
            } else {
                clearGlowTeam(player);
            }
        }
    }

    private void applyGlowState(Player player) {
        applyGlowState(player, getSelectedGlow(player));
    }

    private void applyGlowState(Player player, GlowPreset glow) {
        if (glow != null) {
            applyGlowTeam(player, glow);
            player.setGlowing(true);
        } else {
            clearGlowTeam(player);
        }
    }

    private void selectTrail(Player player, TrailPreset preset) {
        player.getPersistentDataContainer().set(trailKey, PersistentDataType.STRING, preset.id());
    }

    private void clearTrail(Player player) {
        player.getPersistentDataContainer().remove(trailKey);
        lastTrailLocations.remove(player.getUniqueId());
    }

    private TrailPreset getSelectedTrail(Player player) {
        return TrailPreset.fromId(getString(player, trailKey));
    }

    private void selectGlow(Player player, GlowPreset preset) {
        player.getPersistentDataContainer().set(glowKey, PersistentDataType.STRING, preset.id());
        applyGlowTeam(player, preset);
        player.setGlowing(true);
    }

    private void clearGlow(Player player) {
        player.getPersistentDataContainer().remove(glowKey);
        clearGlowTeam(player);
        player.setGlowing(false);
    }

    private GlowPreset getSelectedGlow(Player player) {
        return GlowPreset.fromId(getString(player, glowKey));
    }

    private void selectChatColor(Player player, ChatColorPreset preset) {
        player.getPersistentDataContainer().set(chatKey, PersistentDataType.STRING, preset.id());
        selectedChatColors.put(player.getUniqueId(), preset);
    }

    private void clearChatColor(Player player) {
        player.getPersistentDataContainer().remove(chatKey);
        selectedChatColors.remove(player.getUniqueId());
    }

    private void applyGlowTeam(Player player, GlowPreset preset) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            return;
        }

        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();
        String teamName = glowTeamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.color(preset.outlineColor());
        team.addEntity(player);
    }

    private void clearGlowTeam(Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            return;
        }

        Scoreboard scoreboard = scoreboardManager.getMainScoreboard();
        Team team = scoreboard.getTeam(glowTeamName(player.getUniqueId()));
        if (team == null) {
            return;
        }

        team.removeEntity(player);
        if (team.getSize() == 0) {
            team.unregister();
        }
    }

    private String glowTeamName(UUID uuid) {
        String cleaned = uuid.toString().replace("-", "");
        return "sg" + cleaned.substring(0, 12);
    }

    private ChatColorPreset getSelectedChatColor(Player player) {
        return selectedChatColors.get(player.getUniqueId());
    }

    private void clearAllCosmetics(Player player) {
        clearTrail(player);
        clearGlow(player);
        clearChatColor(player);
        player.sendMessage(Component.text("Your cosmetics have been reset.", NamedTextColor.GREEN));
    }

    private String getString(Player player, NamespacedKey key) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        return container.get(key, PersistentDataType.STRING);
    }

    private void syncStoredState(Player player) {
        if (!canUseChatColors(player)) {
            selectedChatColors.remove(player.getUniqueId());
            return;
        }

        ChatColorPreset preset = ChatColorPreset.fromId(getString(player, chatKey));
        if (preset == null) {
            selectedChatColors.remove(player.getUniqueId());
            return;
        }

        selectedChatColors.put(player.getUniqueId(), preset);
    }

    private Component selectionText(TrailPreset preset) {
        if (preset == null) {
            return Component.text("None", NamedTextColor.GRAY);
        }
        return preset.displayName();
    }

    private Component selectionText(GlowPreset preset) {
        if (preset == null) {
            return Component.text("None", NamedTextColor.GRAY);
        }
        return preset.displayName();
    }

    private Component selectionText(ChatColorPreset preset, boolean allowed) {
        if (!allowed) {
            return Component.text("Locked", NamedTextColor.RED);
        }
        if (preset == null) {
            return Component.text("None", NamedTextColor.GRAY);
        }
        return preset.displayName();
    }

    private boolean canUseChatColors(Player player) {
        return player != null;
    }

    private boolean isFloodgatePlayer(UUID uuid) {
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
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

    private enum MenuType {
        MAIN(Component.text("Cosmetics Hub", TextColor.color(246, 232, 255)).decorate(TextDecoration.BOLD), 36,
                Material.GRAY_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE),
        TRAILS(Component.text("Particle Trails", TextColor.color(205, 255, 231)).decorate(TextDecoration.BOLD), 54,
                Material.BLACK_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE),
        GLOW(Component.text("Glow Effects", TextColor.color(255, 240, 202)).decorate(TextDecoration.BOLD), 54,
                Material.GRAY_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE),
        CHAT(Component.text("Pastel Chat Colours", TextColor.color(255, 224, 243)).decorate(TextDecoration.BOLD), 54,
                Material.WHITE_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        private final Component title;
        private final int size;
        private final Material basePane;
        private final Material accentPane;
        private final Material highlightPane;

        MenuType(Component title, int size, Material basePane, Material accentPane, Material highlightPane) {
            this.title = title;
            this.size = size;
            this.basePane = basePane;
            this.accentPane = accentPane;
            this.highlightPane = highlightPane;
        }

        private Component title() {
            return title;
        }

        private int size() {
            return size;
        }

        private Material basePane() {
            return basePane;
        }

        private Material accentPane() {
            return accentPane;
        }

        private Material highlightPane() {
            return highlightPane;
        }
    }

    private enum TrailPreset {
        BREEZE("breeze", 19, Material.FEATHER,
                Component.text("Breeze Trail", TextColor.color(170, 235, 255)).decorate(TextDecoration.BOLD),
                TextColor.color(170, 235, 255),
                "An airy rod sparkle that feels clean and light.",
                Particle.END_ROD, null, 3, 0.08D, 0.02D, 0.08D, 0.0D),
        BLOOM("bloom", 20, Material.PINK_PETALS,
                Component.text("Bloom Trail", TextColor.color(255, 205, 225)).decorate(TextDecoration.BOLD),
                TextColor.color(255, 205, 225),
                "A soft pink blossom trail.",
                Particle.DUST, new DustOptions(Color.fromRGB(255, 205, 225), 1.15F), 6, 0.08D, 0.05D, 0.08D, 0.0D),
        MINT("mint", 21, Material.LIME_DYE,
                Component.text("Mint Trail", TextColor.color(181, 246, 215)).decorate(TextDecoration.BOLD),
                TextColor.color(181, 246, 215),
                "A fresh mint particle trail.",
                Particle.DUST, new DustOptions(Color.fromRGB(181, 246, 215), 1.1F), 6, 0.08D, 0.05D, 0.08D, 0.0D),
        SKY("sky", 22, Material.LIGHT_BLUE_DYE,
                Component.text("Sky Trail", TextColor.color(171, 219, 255)).decorate(TextDecoration.BOLD),
                TextColor.color(171, 219, 255),
                "A cool sky-blue shimmer.",
                Particle.DUST, new DustOptions(Color.fromRGB(171, 219, 255), 1.1F), 6, 0.08D, 0.05D, 0.08D, 0.0D),
        SUNSET("sunset", 23, Material.ORANGE_DYE,
                Component.text("Sunset Trail", TextColor.color(255, 209, 183)).decorate(TextDecoration.BOLD),
                TextColor.color(255, 209, 183),
                "A warm peach trail with a soft glow.",
                Particle.DUST, new DustOptions(Color.fromRGB(255, 209, 183), 1.1F), 6, 0.08D, 0.05D, 0.08D, 0.0D),
        CLOUD("cloud", 24, Material.WHITE_DYE,
                Component.text("Cloud Trail", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                NamedTextColor.WHITE,
                "A light white cloud puff trail.",
                Particle.CLOUD, null, 2, 0.08D, 0.02D, 0.08D, 0.0D);

        private final String id;
        private final int slot;
        private final Material material;
        private final Component displayName;
        private final TextColor color;
        private final String description;
        private final Particle particle;
        private final DustOptions dustOptions;
        private final int count;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final double extra;

        TrailPreset(String id, int slot, Material material, Component displayName, TextColor color, String description,
                    Particle particle, DustOptions dustOptions, int count,
                    double offsetX, double offsetY, double offsetZ, double extra) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.color = color;
            this.description = description;
            this.particle = particle;
            this.dustOptions = dustOptions;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.extra = extra;
        }

        private static TrailPreset bySlot(int slot) {
            for (TrailPreset preset : values()) {
                if (preset.slot == slot) {
                    return preset;
                }
            }
            return null;
        }

        private static TrailPreset fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (TrailPreset preset : values()) {
                if (preset.id.equalsIgnoreCase(id)) {
                    return preset;
                }
            }
            return null;
        }

        private void spawn(Player player, Location location) {
            if (location.getWorld() == null) {
                return;
            }
            if (dustOptions != null) {
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, dustOptions);
                return;
            }
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }

        private String id() {
            return id;
        }

        private int slot() {
            return slot;
        }

        private Material material() {
            return material;
        }

        private Component displayName() {
            return displayName;
        }

        private String description() {
            return description;
        }

        private TextColor color() {
            return color;
        }
    }

        private enum GlowPreset {
            MINT("mint", 19, Material.BEACON,
                Component.text("Mint Glow", TextColor.color(180, 246, 225)).decorate(TextDecoration.BOLD),
                NamedTextColor.GREEN,
                "A minty glow aura with gentle particles.",
                Particle.DUST, new DustOptions(Color.fromRGB(180, 246, 225), 1.2F), 2, 0.22D, 0.40D, 0.22D, 0.0D),
            ROSE("rose", 20, Material.PINK_DYE,
                Component.text("Rose Glow", TextColor.color(222, 204, 255)).decorate(TextDecoration.BOLD),
                NamedTextColor.LIGHT_PURPLE,
                "A soft rose aura.",
                Particle.DUST, new DustOptions(Color.fromRGB(222, 204, 255), 1.2F), 2, 0.22D, 0.40D, 0.22D, 0.0D),
            LAVENDER("lavender", 21, Material.PURPLE_DYE,
                Component.text("Lavender Glow", TextColor.color(255, 200, 220)).decorate(TextDecoration.BOLD),
                NamedTextColor.RED,
                "A calm lavender aura.",
                Particle.DUST, new DustOptions(Color.fromRGB(255, 200, 220), 1.2F), 2, 0.22D, 0.40D, 0.22D, 0.0D),
            PEACH("peach", 22, Material.ORANGE_DYE,
                Component.text("Peach Glow", TextColor.color(255, 214, 188)).decorate(TextDecoration.BOLD),
                NamedTextColor.GOLD,
                "A warm peach aura.",
                Particle.DUST, new DustOptions(Color.fromRGB(255, 214, 188), 1.2F), 2, 0.22D, 0.40D, 0.22D, 0.0D),
            SKY("sky", 23, Material.LIGHT_BLUE_DYE,
                Component.text("Sky Glow", TextColor.color(172, 219, 255)).decorate(TextDecoration.BOLD),
                NamedTextColor.AQUA,
                "A soft blue aura.",
                Particle.DUST, new DustOptions(Color.fromRGB(172, 219, 255), 1.2F), 2, 0.22D, 0.40D, 0.22D, 0.0D),
            SNOW("snow", 24, Material.WHITE_DYE,
                Component.text("Snow Glow", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                NamedTextColor.WHITE,
                "A crisp white aura.",
                Particle.END_ROD, null, 2, 0.16D, 0.32D, 0.16D, 0.0D);

        private final String id;
        private final int slot;
        private final Material material;
        private final Component displayName;
        private final NamedTextColor outlineColor;
        private final String description;
        private final Particle particle;
        private final DustOptions dustOptions;
        private final int count;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final double extra;

        GlowPreset(String id, int slot, Material material, Component displayName, NamedTextColor outlineColor, String description,
               Particle particle, DustOptions dustOptions, int count,
               double offsetX, double offsetY, double offsetZ, double extra) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.outlineColor = outlineColor;
            this.description = description;
            this.particle = particle;
            this.dustOptions = dustOptions;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.extra = extra;
        }

        private static GlowPreset bySlot(int slot) {
            for (GlowPreset preset : values()) {
                if (preset.slot == slot) {
                    return preset;
                }
            }
            return null;
        }

        private static GlowPreset fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            if ("aurora".equalsIgnoreCase(id)) {
                return MINT;
            }
            for (GlowPreset preset : values()) {
                if (preset.id.equalsIgnoreCase(id)) {
                    return preset;
                }
            }
            return null;
        }

        private void spawn(Player player, Location location) {
            if (location.getWorld() == null) {
                return;
            }
            if (dustOptions != null) {
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, dustOptions);
                return;
            }
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }

        private String id() {
            return id;
        }

        private int slot() {
            return slot;
        }

        private Material material() {
            return material;
        }

        private Component displayName() {
            return displayName;
        }

        private String description() {
            return description;
        }

        private NamedTextColor outlineColor() {
            return outlineColor;
        }
    }

    private enum ChatColorPreset {
        MINT("mint", 10, Material.LIME_DYE,
                Component.text("Mint", TextColor.color(176, 246, 220)).decorate(TextDecoration.BOLD),
                TextColor.color(176, 246, 220), "A soft mint pastel."),
        ROSE("rose", 11, Material.PINK_DYE,
                Component.text("Rose", TextColor.color(255, 198, 220)).decorate(TextDecoration.BOLD),
                TextColor.color(255, 198, 220), "A soft rose pastel."),
        SKY("sky", 12, Material.LIGHT_BLUE_DYE,
                Component.text("Sky", TextColor.color(174, 219, 255)).decorate(TextDecoration.BOLD),
                TextColor.color(174, 219, 255), "A soft sky pastel."),
        LAVENDER("lavender", 13, Material.PURPLE_DYE,
                Component.text("Lavender", TextColor.color(223, 205, 255)).decorate(TextDecoration.BOLD),
                TextColor.color(223, 205, 255), "A soft lavender pastel."),
        PEACH("peach", 14, Material.ORANGE_DYE,
                Component.text("Peach", TextColor.color(255, 214, 189)).decorate(TextDecoration.BOLD),
                TextColor.color(255, 214, 189), "A soft peach pastel."),
        BUTTER("butter", 15, Material.YELLOW_DYE,
                Component.text("Butter", TextColor.color(255, 244, 192)).decorate(TextDecoration.BOLD),
                TextColor.color(255, 244, 192), "A soft butter pastel."),
        SEAFOAM("seafoam", 16, Material.CYAN_DYE,
                Component.text("Seafoam", TextColor.color(194, 244, 238)).decorate(TextDecoration.BOLD),
                TextColor.color(194, 244, 238), "A soft seafoam pastel."),
        CLOUD("cloud", 19, Material.WHITE_DYE,
                Component.text("Cloud", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                TextColor.color(250, 250, 250), "A bright cloud white.");

        private final String id;
        private final int slot;
        private final Material material;
        private final Component displayName;
        private final TextColor color;
        private final String description;

        ChatColorPreset(String id, int slot, Material material, Component displayName, TextColor color, String description) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.color = color;
            this.description = description;
        }

        private static ChatColorPreset bySlot(int slot) {
            for (ChatColorPreset preset : values()) {
                if (preset.slot == slot) {
                    return preset;
                }
            }
            return null;
        }

        private static ChatColorPreset fromId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (ChatColorPreset preset : values()) {
                if (preset.id.equalsIgnoreCase(id)) {
                    return preset;
                }
            }
            return null;
        }

        private String id() {
            return id;
        }

        private int slot() {
            return slot;
        }

        private Material material() {
            return material;
        }

        private Component displayName() {
            return displayName;
        }

        private TextColor color() {
            return color;
        }

        private String description() {
            return description;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private Inventory inventory;

        private MenuHolder(MenuType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
