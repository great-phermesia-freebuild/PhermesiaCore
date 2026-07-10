package com.greatphermesia.core.feature.gadgets;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class GadgetsModule implements PluginModule, Listener, CommandExecutor {

    private static final String PERMISSION = "gadgets.use";
    private static final long EFFECT_SCAN_INTERVAL_TICKS = 2L;
    private static final int ENTITY_GADGET_SCAN_INTERVAL_TICKS = 4;
    private static final int PARTICLE_SCAN_INTERVAL_TICKS = 10;
    private static final int EFFECT_REFRESH_TICKS = 60;
    private static final long BLOCK_GADGET_COOLDOWN_MS = 500L;
    private static final long DASH_GADGET_COOLDOWN_MS = 150L;
    private static final double DASH_SPEED = 2.8D;
    private static final double BOUNCE_UP_SPEED = 1.35D;
    private static final double BOUNCE_SIDE_SPEED = 1.75D;
    private static final double BOUNCE_SIDE_LIFT = 0.52D;
    private static final double BOUNCE_DOWN_SPEED = -2.25D;
    private static final double SUPER_RAIL_MAX_SPEED = 2.4D;
    private static final double SUPER_RAIL_BOOST_MULTIPLIER = 1.35D;
    private static final double SUPER_RAIL_MIN_BOOST = 0.18D;
    private static final double SUPER_RAIL_MIN_INPUT_SPEED = 0.04D;
    private static final double HORIZONTAL_DASH_LIFT = 0.72D;
    private static final double GRAVITY_BLOCK_RANGE = 4.0D;
    private static final double GRAVITY_BLOCK_MAX_PULL_SPEED = 1.10D;
    private static final int DOOR_MAX_SIZE = 5;
    private static final int DOOR_PROXIMITY_PADDING = 2;
    private static final int MENU_SIZE = 54;
    private static final Component MAIN_MENU_TITLE = Component.text("Gadget Workshop", TextColor.color(116, 232, 255)).decorate(TextDecoration.BOLD);
    private static final Component BRUSH_MENU_TITLE = Component.text("Brush Atelier", TextColor.color(154, 255, 190)).decorate(TextDecoration.BOLD);
    private static final Component BLOCK_GADGETS_TITLE = Component.text("Block Gadget Vault", TextColor.color(255, 128, 218)).decorate(TextDecoration.BOLD);
    private static final int MAIN_BRUSHES_SLOT = 20;
    private static final int MAIN_DOOR_SLOT = 22;
    private static final int MAIN_BLOCK_GADGETS_SLOT = 24;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final int RECEIVE_BRUSH_SLOT = 31;
    private static final int[] BORDER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int[] ACCENT_SLOTS = {10, 11, 12, 14, 15, 16, 28, 29, 30, 32, 33, 34};
    private static final int[] HIGHLIGHT_SLOTS = {19, 25};

    private final PhermesiaCorePlugin plugin;
    private final NamespacedKey brushKey;
    private final NamespacedKey doorToolKey;
    private final NamespacedKey blockGadgetKey;
    private final NamespacedKey parkourControlKey;
    private final NamespacedKey gadgetRemoverKey;
    private final NamespacedKey parkourCourseKey;
    private final Map<BlockKey, GadgetState> blockStates = new HashMap<>();
    private final Map<BlockKey, BlockGadgetState> blockGadgetStates = new HashMap<>();
    private final Map<ChunkKey, Set<BlockKey>> brushStatesByChunk = new HashMap<>();
    private final Map<ChunkKey, Set<BlockKey>> blockGadgetsByChunk = new HashMap<>();
    private final Map<UUID, ParkourSession> parkourSessions = new HashMap<>();
    private final Set<UUID> inspectMode = new HashSet<>();
    private final Map<UUID, String> selectedParkourCourses = new HashMap<>();
    private final Map<BlockKey, GadgetAuditEntry> blockAudit = new HashMap<>();
    private final Map<UUID, GadgetAuditEntry> doorAudit = new HashMap<>();
    private final Map<UUID, DoorSelection> doorSelections = new HashMap<>();
    private final Map<UUID, DoorDefinition> doors = new HashMap<>();
    private final Map<UUID, PendingWarpBrush> pendingWarpBrushes = new HashMap<>();
    private final Map<UUID, PendingTimeBrush> pendingTimeBrushes = new HashMap<>();
    private final Map<UUID, BlockKey> lastActionTriggers = new HashMap<>();
    private final Map<UUID, Long> blockGadgetCooldowns = new HashMap<>();
    private final Set<UUID> frozenBuilders = new HashSet<>();
    private final Map<BlockKey, UUID> frozenUpdateBlocks = new HashMap<>();
    private final Set<UUID> hiddenDoors = new HashSet<>();

    private int effectTaskId = -1;
    private int effectTick = 0;

    public GadgetsModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
        this.brushKey = new NamespacedKey(plugin, "gadget_brush");
        this.doorToolKey = new NamespacedKey(plugin, "door_tool");
        this.blockGadgetKey = new NamespacedKey(plugin, "block_gadget");
        this.parkourControlKey = new NamespacedKey(plugin, "parkour_control");
        this.gadgetRemoverKey = new NamespacedKey(plugin, "gadget_remover");
        this.parkourCourseKey = new NamespacedKey(plugin, "parkour_course");
    }

    @Override
    public String name() {
        return "Gadgets";
    }

    @Override
    public void enable() {
        plugin.getDataFolder().mkdirs();
        loadData();
        loadAuditData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("gadgets");
        bindCommand("brush");
        bindCommand("door");
        bindCommand("freeze");
        bindCommand("unfreeze");
        startEffectTask();

        plugin.getLogger().info("[Gadgets] Module enabled.");
    }

    @Override
    public void disable() {
        stopEffectTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreParkourHotbar(player);
        }
        saveData();
        saveAuditData();
        plugin.getLogger().info("[Gadgets] Module stopped.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ("gadgets".equals(commandName)) {
            if (!canUseGadgets(player)) {
                player.sendMessage(ChatUtil.color("&cOnly builders can use /gadgets."));
                return true;
            }

            openMenu(player);
            return true;
        }

        if ("brush".equals(commandName)) {
            return handleBrushCommand(player, args);
        }

        if ("door".equals(commandName)) {
            return handleDoorCommand(player, args);
        }

        if ("freeze".equals(commandName) || "unfreeze".equals(commandName)) {
            return handleFreezeCommand(player, "freeze".equals(commandName));
        }

        return false;
    }

    private boolean handleFreezeCommand(Player player, boolean toggle) {
        UUID uuid = player.getUniqueId();
        boolean enable = toggle ? !frozenBuilders.contains(uuid) : false;
        if (enable) {
            frozenBuilders.add(uuid);
            player.sendMessage(ChatUtil.color("&bFreeze enabled. &7Your block changes will not trigger physics updates."));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.1f);
        } else {
            frozenBuilders.remove(uuid);
            int updates = thawFrozenUpdates(uuid);
            player.sendMessage(ChatUtil.color("&eFreeze disabled. &7Physics updates resumed" + (updates == 0 ? "." : " for " + updates + " protected blocks.")));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.9f);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBrushInspectPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (!isBrushInspectCommand(raw)) {
            return;
        }

        event.setCancelled(true);
        handleBrushCommand(event.getPlayer(), new String[]{"i"});
    }

    private boolean isBrushInspectCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String[] split = raw.split("\\s+");
        if (split.length != 2) {
            return false;
        }

        String command = split[0].toLowerCase(Locale.ROOT);
        String subCommand = split[1].toLowerCase(Locale.ROOT);
        return command.equals("/brush") && (subCommand.equals("i") || subCommand.equals("inspect"));
    }

    private boolean handleBrushCommand(Player player, String[] args) {
        if (!canUseGadgets(player)) {
            player.sendMessage(ChatUtil.color("&cOnly builders can use /brush."));
            return true;
        }

        if (args.length == 0) {
            sendBrushHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inspect", "i" -> {
                UUID uuid = player.getUniqueId();
                if (inspectMode.remove(uuid)) {
                    player.sendMessage(ChatUtil.color("&eBrush inspect disabled."));
                } else {
                    inspectMode.add(uuid);
                    player.sendMessage(ChatUtil.color("&aBrush inspect enabled. &7Click a gadget block or door."));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
                return true;
            }
            case "remover", "remove", "eraser" -> {
                giveGadgetRemover(player);
                return true;
            }
            case "course" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatUtil.color("&eCurrent parkour course: &f" + selectedCourse(player)));
                    player.sendMessage(ChatUtil.color("&7Use &f/brush course <id>&7 before taking parkour blocks."));
                    return true;
                }

                String courseId = sanitizeCourseId(args[1]);
                selectedParkourCourses.put(player.getUniqueId(), courseId);
                player.sendMessage(ChatUtil.color("&aParkour course set to &f" + courseId + "&a."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
                return true;
            }
            default -> {
                sendBrushHelp(player);
                return true;
            }
        }
    }

    private void sendBrushHelp(Player player) {
        player.sendMessage(ChatUtil.color("&eBrush commands:"));
        player.sendMessage(ChatUtil.color("&7/brush inspect &f- toggle gadget/door inspection"));
        player.sendMessage(ChatUtil.color("&7/brush i &f- shortcut for inspect"));
        player.sendMessage(ChatUtil.color("&7/brush remover &f- get the gadget remover tool"));
        player.sendMessage(ChatUtil.color("&7/brush course <id> &f- set course ID for parkour blocks"));
    }

    private void giveGadgetRemover(Player player) {
        ItemStack tool = createItem(
                Material.GOLDEN_AXE,
                Component.text("Gadget Remover", TextColor.color(255, 220, 112)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Click a gadget block to unregister it.", NamedTextColor.GRAY),
                        Component.text("Works on brushes, block gadgets, and doors.", NamedTextColor.YELLOW),
                        Component.text("Brushes and doors are kept in inspect history.", NamedTextColor.YELLOW)
                ),
                true,
                false,
                gadgetRemoverKey,
                "remover");
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tool);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(ChatUtil.color("&aYou received the &fGadget Remover&a."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    private boolean isGadgetRemover(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE || !item.hasItemMeta()) {
            return false;
        }

        String value = item.getItemMeta().getPersistentDataContainer().get(gadgetRemoverKey, PersistentDataType.STRING);
        return "remover".equalsIgnoreCase(value);
    }

    private void inspectGadget(Player player, Block block) {
        BlockKey key = BlockKey.from(block.getLocation());
        if (key == null) {
            return;
        }

        DoorDefinition door = findDoorContaining(block.getLocation());
        if (door != null) {
            sendAuditInspection(player, "Door " + door.bounds().sizeLabel(), doorAudit.get(door.id()));
            return;
        }

        GadgetState brushState = blockStates.get(key);
        if (brushState != null) {
            sendAuditInspection(player, brushState.type().auditLabel(brushState), blockAudit.get(key));
            return;
        }

        GadgetAuditEntry audit = blockAudit.get(key);
        if (audit != null) {
            sendAuditInspection(player, audit.typeLabel(), audit);
            return;
        }

        player.sendMessage(ChatUtil.color("&7No registered gadget data found on this block."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
    }

    private void sendAuditInspection(Player player, String fallbackType, GadgetAuditEntry audit) {
        String type = audit != null ? audit.typeLabel() : fallbackType;
        player.sendMessage(ChatUtil.color("&bGadget Inspect &8- &f" + type));
        if (audit == null) {
            player.sendMessage(ChatUtil.color("&7Created by: &fUnknown &8(old data)"));
            return;
        }

        player.sendMessage(ChatUtil.color("&7Created by: &f" + audit.createdByName()));
        player.sendMessage(ChatUtil.color("&7Created: &f" + ago(audit.createdAtEpochSeconds()) + " ago"));
        if (audit.removedByName() != null) {
            player.sendMessage(ChatUtil.color("&7Removed by: &f" + audit.removedByName()));
            player.sendMessage(ChatUtil.color("&7Removed: &f" + ago(audit.removedAtEpochSeconds()) + " ago"));
        }
    }

    private void removeGadgetWithTool(Player player, Block block) {
        DoorDefinition door = findDoorContaining(block.getLocation());
        if (door != null) {
            restoreDoor(door);
            doors.remove(door.id());
            hiddenDoors.remove(door.id());
            markDoorRemoved(door.id(), player);
            saveData();
            player.sendMessage(ChatUtil.color("&aRemoved registered door data."));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.0f);
            return;
        }

        BlockKey key = BlockKey.from(block.getLocation());
        if (key == null) {
            return;
        }

        if (removeBrushState(key) != null) {
            markBlockRemoved(key, player);
            saveData();
            player.sendMessage(ChatUtil.color("&aRemoved gadget data from this block."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.9f);
            return;
        }

        if (removeBlockGadgetState(key) != null) {
            saveData();
            player.sendMessage(ChatUtil.color("&aRemoved block gadget data from this block."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.9f);
            return;
        }

        player.sendMessage(ChatUtil.color("&cThis block has no registered gadget data."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (handleWarpAnvilClick(event) || handleTimeAnvilClick(event)) {
            return;
        }

        if (handleParkourInventoryClick(event)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MENU_SIZE) {
            return;
        }

        if (holder.view() != MenuView.MAIN && slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        switch (holder.view()) {
            case MAIN -> handleMainMenuClick(player, slot);
            case BRUSHES -> handleBrushMenuClick(player, slot);
            case BRUSH_DETAIL -> handleBrushDetailClick(player, slot, holder.brushType());
            case BLOCK_GADGETS -> handleBlockGadgetMenuClick(player, slot);
        }
    }

    private boolean handleParkourInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !parkourSessions.containsKey(player.getUniqueId())) {
            return false;
        }

        if (parkourControlFromItem(event.getCurrentItem()) != null || parkourControlFromItem(event.getCursor()) != null) {
            event.setCancelled(true);
            return true;
        }

        if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() >= 0 && event.getSlot() < 9) {
            event.setCancelled(true);
            return true;
        }

        if (event.getHotbarButton() >= 0) {
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (slot == MAIN_BRUSHES_SLOT) {
            openBrushMenu(player);
            return;
        }

        if (slot == MAIN_DOOR_SLOT) {
            player.closeInventory();
            giveDoorTool(player);
            return;
        }

        if (slot == MAIN_BLOCK_GADGETS_SLOT) {
            openBlockGadgetMenu(player);
        }
    }

    private void handleBrushMenuClick(Player player, int slot) {
        if (slot == BACK_SLOT) {
            openMenu(player);
            return;
        }

        BrushType brushType = BrushType.bySlot(slot);
        if (brushType == null) {
            return;
        }

        openBrushDetailMenu(player, brushType);
    }

    private void handleBrushDetailClick(Player player, int slot, BrushType brushType) {
        if (slot == BACK_SLOT) {
            openBrushMenu(player);
            return;
        }

        if (brushType == null || slot != RECEIVE_BRUSH_SLOT) {
            return;
        }

        giveBrush(player, brushType);
    }

    private void handleBlockGadgetMenuClick(Player player, int slot) {
        if (slot == BACK_SLOT) {
            openMenu(player);
            return;
        }

        BlockGadgetMenuItem item = BlockGadgetMenuItem.bySlot(slot);
        if (item == null) {
            return;
        }

        BlockGadgetType type = item.blockGadgetType();
        if (type == null) {
            player.sendMessage(ChatUtil.color("&d" + item.plainName() + " &7is staged for a later step."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
            return;
        }

        giveBlockGadget(player, type);
    }

    private void handleInteractiveGadgetBlock(Player player, Block clickedBlock) {
        if (!isInteractiveTriggerBlock(clickedBlock.getType())) {
            return;
        }

        BlockKey key = BlockKey.from(clickedBlock.getLocation());
        if (key == null) {
            return;
        }

        GadgetState state = blockStates.get(key);
        if (state == null) {
            return;
        }
        if (!state.matches(clickedBlock)) {
            removeBrushState(key);
            saveData();
            return;
        }
        if (state.material() == null) {
            state = state.withMaterial(clickedBlock.getType());
            putBrushState(key, state);
            saveData();
        }

        activateState(player, key, state, true);
    }

    private void handleWarpBrushClick(Player player, BlockKey key, boolean rightClick) {
        if (!rightClick) {
            GadgetState current = blockStates.get(key);
            if (current != null && current.type() == BrushType.WARP) {
                removeBrushState(key);
                markBlockRemoved(key, player);
                saveData();
                player.sendMessage(ChatUtil.color("&eRemoved the warp brush from this block."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.9f);
            }
            return;
        }

        openWarpAnvil(player, key);
    }

    private void openWarpAnvil(Player player, BlockKey key) {
        pendingWarpBrushes.put(player.getUniqueId(), new PendingWarpBrush(key));
        InventoryView view = player.openAnvil(null, true);
        if (view == null) {
            pendingWarpBrushes.remove(player.getUniqueId());
            player.sendMessage(ChatUtil.color("&cCould not open the warp anvil menu."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        view.getTopInventory().setItem(0, createItem(
                Material.PAPER,
                Component.text("warp-name", NamedTextColor.AQUA),
                List.of(Component.text("Rename this to a warp.", NamedTextColor.GRAY)),
                false,
                false));
        player.sendMessage(ChatUtil.color("&bType a warp name in the anvil."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
    }

    private void openTimeAnvil(Player player, BlockKey key) {
        pendingTimeBrushes.put(player.getUniqueId(), new PendingTimeBrush(key));
        InventoryView view = player.openAnvil(null, true);
        if (view == null) {
            pendingTimeBrushes.remove(player.getUniqueId());
            player.sendMessage(ChatUtil.color("&cCould not open the time anvil menu."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        view.getTopInventory().setItem(0, createItem(
                Material.PAPER,
                Component.text("12:00", NamedTextColor.GOLD),
                List.of(Component.text("Type a time like 12:00 or 12.", NamedTextColor.GRAY)),
                false,
                false));
        player.sendMessage(ChatUtil.color("&bType a time like &f12:00 &bor &f12&b in the anvil."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
    }

    private boolean handleWarpAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        PendingWarpBrush pending = pendingWarpBrushes.get(player.getUniqueId());
        if (pending == null || event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return false;
        }

        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return true;
        }

        String warpName = null;
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory instanceof AnvilInventory anvilInventory) {
            warpName = cleanWarpName(anvilInventory.getRenameText());
        }

        if (warpName == null) {
            player.sendMessage(ChatUtil.color("&cType a warp name into the anvil first."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            clearAnvilResult(event);
            return true;
        }

        String matchedWarp = findEssentialsWarp(warpName);
        if (matchedWarp == null) {
            player.sendMessage(ChatUtil.color("&cThere is no warp named &f" + warpName + "&c."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            pendingWarpBrushes.remove(player.getUniqueId());
            clearAnvilResult(event);
            player.closeInventory();
            return true;
        }

        setState(pending.blockKey(), new GadgetState(BrushType.WARP, 1, matchedWarp, materialFor(pending.blockKey())), player, "Warp Anchor Brush");
        pendingWarpBrushes.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatUtil.color("&aWarp brush linked this block to &f/warp " + matchedWarp + "&a."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
        return true;
    }

    private boolean handleTimeAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        PendingTimeBrush pending = pendingTimeBrushes.get(player.getUniqueId());
        if (pending == null || event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return false;
        }

        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return true;
        }

        String timeValue = null;
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory instanceof AnvilInventory anvilInventory) {
            timeValue = cleanTimeValue(anvilInventory.getRenameText());
        }

        if (timeValue == null) {
            player.sendMessage(ChatUtil.color("&cType a valid time like &f12:00 &cor &f12&c."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            clearAnvilResult(event);
            return true;
        }

        setState(pending.blockKey(), new GadgetState(BrushType.TIME, 1, timeValue, materialFor(pending.blockKey())), player, "Personal Time Brush (" + timeValue + ")");
        clearAnvilResult(event);
        event.getView().getTopInventory().clear();
        Bukkit.getScheduler().runTask(plugin, () -> removeAnvilPlaceholderPapers(player));
        pendingTimeBrushes.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatUtil.color("&aTime brush linked this block to &f/ptime " + timeValue + "&a."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
        return true;
    }

    private void clearAnvilResult(InventoryClickEvent event) {
        event.setCurrentItem(null);
        event.getView().setCursor(null);
        event.getView().getTopInventory().setItem(2, null);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        if (pendingWarpBrushes.containsKey(player.getUniqueId())) {
            prepareWarpAnvilResult(event);
            return;
        }

        if (pendingTimeBrushes.containsKey(player.getUniqueId())) {
            prepareTimeAnvilResult(event);
        }
    }

    private void prepareWarpAnvilResult(PrepareAnvilEvent event) {
        String warpName = cleanWarpName(event.getInventory().getRenameText());
        if (warpName == null) {
            event.setResult(createItem(
                    Material.PAPER,
                    Component.text("Type a warp name", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text("Rename the paper to a warp.", NamedTextColor.GRAY)),
                    false,
                    false));
            return;
        }

        event.setResult(createItem(
                Material.ENDER_PEARL,
                Component.text("Set Warp: ", NamedTextColor.GREEN)
                        .append(Component.text(warpName, TextColor.color(214, 255, 246)))
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text("Click the result to bind this block.", NamedTextColor.YELLOW)),
                true,
                false));
    }

    private void prepareTimeAnvilResult(PrepareAnvilEvent event) {
        String timeValue = cleanTimeValue(event.getInventory().getRenameText());
        if (timeValue == null) {
            event.setResult(createItem(
                    Material.PAPER,
                    Component.text("Type a time", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    List.of(Component.text("Use a value like 12:00 or 12.", NamedTextColor.GRAY)),
                    false,
                    false));
            return;
        }

        event.setResult(createItem(
                Material.CLOCK,
                Component.text("Set Time: ", NamedTextColor.GREEN)
                        .append(Component.text(timeValue, TextColor.color(255, 232, 122)))
                        .decorate(TextDecoration.BOLD),
                List.of(Component.text("Click the result to bind this block.", NamedTextColor.YELLOW)),
                true,
                false));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getType() == InventoryType.ANVIL) {
            boolean hadPendingAnvil = pendingWarpBrushes.containsKey(player.getUniqueId()) || pendingTimeBrushes.containsKey(player.getUniqueId());
            if (hadPendingAnvil) {
                event.getInventory().clear();
                Bukkit.getScheduler().runTask(plugin, () -> removeAnvilPlaceholderPapers(player));
            }
            pendingWarpBrushes.remove(player.getUniqueId());
            pendingTimeBrushes.remove(player.getUniqueId());
        }
    }

    private void removeAnvilPlaceholderPapers(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isAnvilPlaceholderPaper(item)) {
                continue;
            }
            item.setAmount(0);
        }
        if (isAnvilPlaceholderPaper(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private boolean isAnvilPlaceholderPaper(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.customName() == null) {
            return false;
        }

        String name = ChatUtil.plainText(meta.customName());
        return name.equalsIgnoreCase("12:00")
                || name.equalsIgnoreCase("warp-name")
                || name.equalsIgnoreCase("Type a time")
                || name.equalsIgnoreCase("Type a warp name");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        doorSelections.remove(event.getPlayer().getUniqueId());
        pendingWarpBrushes.remove(event.getPlayer().getUniqueId());
        pendingTimeBrushes.remove(event.getPlayer().getUniqueId());
        lastActionTriggers.remove(event.getPlayer().getUniqueId());
        blockGadgetCooldowns.remove(event.getPlayer().getUniqueId());
        inspectMode.remove(event.getPlayer().getUniqueId());
        selectedParkourCourses.remove(event.getPlayer().getUniqueId());
        frozenBuilders.remove(event.getPlayer().getUniqueId());
        frozenUpdateBlocks.entrySet().removeIf(entry -> entry.getValue().equals(event.getPlayer().getUniqueId()));
        restoreParkourHotbar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onParkourControlInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (parkourControlFromItem(event.getItem()) == null) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        handleParkourControl(player, event.getItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (inspectMode.contains(player.getUniqueId()) && event.getClickedBlock() != null) {
            event.setCancelled(true);
            inspectGadget(player, event.getClickedBlock());
            return;
        }

        if (isGadgetRemover(event.getItem()) && event.getClickedBlock() != null) {
            event.setCancelled(true);
            removeGadgetWithTool(player, event.getClickedBlock());
            return;
        }

        if (handleParkourControl(player, event.getItem())) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (isDoorTool(event.getItem())) {
            if (!canUseGadgets(player)) {
                player.sendMessage(ChatUtil.color("&cOnly builders can use the door tool."));
                event.setCancelled(true);
                return;
            }
            handleDoorToolInteract(player, event);
            return;
        }

        BrushType brushType = brushTypeFromItem(event.getItem());
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (brushType == null) {
            handleInteractiveGadgetBlock(player, clickedBlock);
            return;
        }

        if (!canUseGadgets(player)) {
            player.sendMessage(ChatUtil.color("&cOnly builders can use gadget brushes."));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        BlockKey key = BlockKey.from(clickedBlock.getLocation());
        if (key == null) {
            return;
        }

        if (brushType == BrushType.WARP) {
            handleWarpBrushClick(player, key, event.getAction() == Action.RIGHT_CLICK_BLOCK);
            return;
        }

        if (brushType == BrushType.TIME) {
            handleTimeBrushClick(player, key, event.getAction() == Action.RIGHT_CLICK_BLOCK);
            return;
        }

        GadgetState current = blockStates.get(key);
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK;

        if (current == null || current.type() != brushType) {
            if (rightClick) {
                setState(key, new GadgetState(brushType, 1, brushType.defaultValue(), clickedBlock.getType()), player, brushType.auditLabel(null));
                player.sendMessage(ChatUtil.color("&aSet this block to &f" + brushType.labelForLevel(1) + "&a."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
            }
            return;
        }

        int nextLevel = brushType.nextLevel(current.level(), rightClick);
        setState(key, new GadgetState(brushType, nextLevel, brushType.defaultValue(), clickedBlock.getType()), player, brushType.auditLabel(null));
        player.sendMessage(ChatUtil.color("&aSet this block to &f" + brushType.labelForLevel(nextLevel) + "&a."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    @EventHandler
    public void onParkourControlDrop(PlayerDropItemEvent event) {
        if (parkourControlFromItem(event.getItemDrop().getItemStack()) == null) {
            return;
        }

        event.setCancelled(true);
        handleParkourControl(event.getPlayer(), event.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onParkourControlSwap(PlayerSwapHandItemsEvent event) {
        if (parkourControlFromItem(event.getMainHandItem()) == null && parkourControlFromItem(event.getOffHandItem()) == null) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleTimeBrushClick(Player player, BlockKey key, boolean rightClick) {
        if (!rightClick) {
            GadgetState current = blockStates.get(key);
            if (current != null && current.type() == BrushType.TIME) {
                removeBrushState(key);
                markBlockRemoved(key, player);
                saveData();
                player.sendMessage(ChatUtil.color("&eRemoved the time brush from this block."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.9f);
            }
            return;
        }

        openTimeAnvil(player, key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isDoorTool(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }

        if (isDoorBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        rememberFrozenChange(event.getPlayer(), event.getBlock());
        removeState(event.getBlock().getLocation(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        rememberFrozenChange(event.getPlayer(), event.getBlockPlaced());

        BlockGadgetType type = blockGadgetTypeFromItem(event.getItemInHand());
        if (type == null) {
            removeState(event.getBlockPlaced().getLocation(), event.getPlayer());
            return;
        }

        if (!canUseGadgets(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatUtil.color("&cOnly builders can place block gadgets."));
            event.setCancelled(true);
            return;
        }

        Block placedBlock = event.getBlockPlaced();
        BlockFace facing = type == BlockGadgetType.DASH_BLOCK ? placedFacing(event) : BlockFace.SELF;

        BlockKey key = BlockKey.from(placedBlock.getLocation());
        if (key == null) {
            return;
        }

        String courseId = type.parkourType() ? parkourCourseFromItem(event.getItemInHand(), event.getPlayer()) : null;
        putBlockGadgetState(key, new BlockGadgetState(type, facing, courseId));
        saveData();
        event.getPlayer().sendMessage(ChatUtil.color("&aPlaced &f" + type.plainName() + (courseId == null ? "" : " &7(course " + courseId + ")") + "&a."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isFrozenProtected(event.getBlock()) || isFrozenProtected(event.getSourceBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isFrozenProtected(event.getBlock()) || isFrozenProtected(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (isFrozenProtected(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isFrozenProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        if ((dx * dx) + (dy * dy) + (dz * dz) < 0.0001D) {
            return;
        }

        applyBlockState(event.getPlayer());
        applyBlockGadgetState(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isDoorBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        removeState(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (containsDoorBlock(event.blockList())) {
            event.setCancelled(true);
            return;
        }

        for (Block block : new ArrayList<>(event.blockList())) {
            removeState(block.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (containsDoorBlock(event.blockList())) {
            event.setCancelled(true);
            return;
        }

        for (Block block : new ArrayList<>(event.blockList())) {
            removeState(block.getLocation());
        }
    }

    private void bindCommand(String commandName) {
        if (plugin.getCommand(commandName) == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + commandName);
            return;
        }
        plugin.getCommand(commandName).setExecutor(this);
    }

    private boolean canUseGadgets(Player player) {
        return player.isOp() || player.hasPermission(PERMISSION) || player.hasPermission("group.builder");
    }

    private void openMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Inventory inventory = createMainMenu(player);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.12f);
    }

    private void openBrushMenu(Player player) {
        Inventory inventory = createBrushMenu(player);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
    }

    private void openBrushDetailMenu(Player player, BrushType brushType) {
        Inventory inventory = createBrushDetailMenu(player, brushType);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
    }

    private void openBlockGadgetMenu(Player player) {
        Inventory inventory = createBlockGadgetMenu(player);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
    }

    private Inventory createMainMenu(Player player) {
        Inventory inventory = createMenu(MenuView.MAIN, player, MAIN_MENU_TITLE, null);

        inventory.setItem(MAIN_BRUSHES_SLOT, buildBrushesButton());
        inventory.setItem(MAIN_DOOR_SLOT, buildDoorToolItem());
        inventory.setItem(MAIN_BLOCK_GADGETS_SLOT, buildBlockGadgetsButton());
        return inventory;
    }

    private Inventory createBrushMenu(Player player) {
        Inventory inventory = createMenu(MenuView.BRUSHES, player, BRUSH_MENU_TITLE, null);

        inventory.setItem(4, buildBrushHeaderItem());
        for (BrushType brushType : BrushType.values()) {
            inventory.setItem(brushType.slot(), buildBrushMenuItem(brushType));
        }
        inventory.setItem(BACK_SLOT, buildBackButton());
        inventory.setItem(CLOSE_SLOT, buildCloseButton());
        return inventory;
    }

    private Inventory createBrushDetailMenu(Player player, BrushType brushType) {
        Component title = Component.text(ChatUtil.plainText(brushType.displayName()).replace(" Brush", ""), TextColor.color(154, 255, 190))
                .decorate(TextDecoration.BOLD);
        Inventory inventory = createMenu(MenuView.BRUSH_DETAIL, player, title, brushType);

        inventory.setItem(13, buildBrushDetailItem(brushType));
        inventory.setItem(RECEIVE_BRUSH_SLOT, buildReceiveBrushButton(brushType));
        inventory.setItem(BACK_SLOT, buildBackButton());
        inventory.setItem(CLOSE_SLOT, buildCloseButton());
        return inventory;
    }

    private Inventory createBlockGadgetMenu(Player player) {
        Inventory inventory = createMenu(MenuView.BLOCK_GADGETS, player, BLOCK_GADGETS_TITLE, null);

        inventory.setItem(4, buildBlockGadgetsHeaderItem());
        for (BlockGadgetMenuItem item : BlockGadgetMenuItem.values()) {
            inventory.setItem(item.slot(), buildBlockGadgetItem(item));
        }
        inventory.setItem(BACK_SLOT, buildBackButton());
        inventory.setItem(CLOSE_SLOT, buildCloseButton());
        return inventory;
    }

    private Inventory createMenu(MenuView view, Player player, Component title, BrushType brushType) {
        MenuHolder holder = new MenuHolder(view, brushType);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title);
        holder.inventory = inventory;

        decorateMenu(inventory, player);
        return inventory;
    }

    private void decorateMenu(Inventory inventory, Player player) {
        if (isBedrockPlayer(player.getUniqueId())) {
            return;
        }
        MenuTheme theme = themeFor(player);
        ItemStack base = decorativePane(theme.base());
        ItemStack accent = decorativePane(theme.accent());
        ItemStack highlight = decorativePane(theme.highlight());

        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, base.clone());
        }

        for (int slot : ACCENT_SLOTS) {
            inventory.setItem(slot, accent.clone());
        }

        for (int slot : HIGHLIGHT_SLOTS) {
            inventory.setItem(slot, highlight.clone());
        }
    }

    private MenuTheme themeFor(Player player) {
        return new MenuTheme(Material.BLACK_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE);
    }

    private ItemStack buildBrushesButton() {
        return createItem(
                Material.BRUSH,
                Component.text("Brush Atelier", TextColor.color(154, 255, 190)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Curated block brushes for effect zones.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Effects, warps, and personal-time triggers live here.", NamedTextColor.AQUA),
                        Component.text("Click to open the brush collection.", NamedTextColor.YELLOW)
                ),
                true,
                false);
    }

    private ItemStack buildBlockGadgetsButton() {
        return createItem(
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("Block Gadget Vault", TextColor.color(255, 128, 218)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Experimental custom blocks staged for builders.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Slime launchers, rails, parkour markers, and dash pads.", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Click to preview the block lineup.", NamedTextColor.YELLOW)
                ),
                true,
                false);
    }

    private ItemStack buildBrushHeaderItem() {
        return createItem(
                Material.ENCHANTED_BOOK,
                Component.text("Choose a Brush", TextColor.color(226, 255, 236)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Each brush opens its own detail page.", NamedTextColor.GRAY),
                        Component.text("Collect one, then paint blocks, levers, or buttons.", NamedTextColor.GRAY)
                ),
                true,
                false);
    }

    private ItemStack buildBlockGadgetsHeaderItem() {
        return createItem(
                Material.TRIAL_KEY,
                Component.text("Upcoming Block Gadgets", TextColor.color(255, 218, 244)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("The menu structure is ready.", NamedTextColor.GRAY),
                        Component.text("Behavior will be added step by step.", NamedTextColor.GRAY)
                ),
                true,
                false);
    }

    private ItemStack buildDoorToolItem() {
        return createItem(
            Material.STONE_AXE,
            Component.text("Doorwright Tool", TextColor.color(255, 205, 140)).decorate(TextDecoration.BOLD),
                List.of(
                Component.text("A polished selection wand for hidden doors.", NamedTextColor.GRAY),
                        Component.empty(),
                Component.text("Click to receive the door tool and close this menu.", NamedTextColor.YELLOW)
                ),
            true,
            false,
            null,
            "door_tool");
    }

    private ItemStack buildCloseButton() {
        return createItem(
                Material.BARRIER,
                Component.text("Close Menu", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                List.of(Component.text("Leave the gadgets menu.", NamedTextColor.GRAY)),
                false,
                false);
    }

    private ItemStack buildBackButton() {
        return createItem(
                Material.ARROW,
                Component.text("Back to Workshop", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text("Return to the main gadgets menu.", NamedTextColor.GRAY)),
                false,
                false);
    }

    private ItemStack buildBrushMenuItem(BrushType brushType) {
        List<Component> lore = new ArrayList<>(brushType.description());
        lore.add(Component.empty());
        lore.add(Component.text("Click to open this brush menu.", NamedTextColor.YELLOW));

        return createItem(
                brushType.menuIcon(),
                brushType.displayName(),
                lore,
                true,
                false);
    }

    private ItemStack buildBrushDetailItem(BrushType brushType) {
        List<Component> lore = new ArrayList<>(brushType.description());
        lore.add(Component.empty());
        lore.add(Component.text("This page is ready for future brush settings.", NamedTextColor.AQUA));

        return createItem(
                brushType.menuIcon(),
                brushType.displayName(),
                lore,
                true,
                false);
    }

    private ItemStack buildReceiveBrushButton(BrushType brushType) {
        return createItem(
                Material.BRUSH,
                Component.text("Collect ", NamedTextColor.GREEN)
                        .append(Component.text(ChatUtil.plainText(brushType.displayName()).replace(" Brush", ""), TextColor.color(226, 255, 236)))
                        .decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Add this brush to your inventory.", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to receive the brush.", NamedTextColor.YELLOW)
                ),
                true,
                false);
    }

    private ItemStack buildBlockGadgetItem(BlockGadgetMenuItem item) {
        List<Component> lore = new ArrayList<>(item.description());
        lore.add(Component.empty());
        lore.add(Component.text(item.blockGadgetType() == null ? "Preview only for this step." : "Click to receive this block gadget.", NamedTextColor.YELLOW));

        return createItem(
                item.icon(),
                item.displayName(),
                lore,
                true,
                false);
    }

    private void giveBlockGadget(Player player, BlockGadgetType type) {
        ItemStack block = createBlockGadgetItem(type, type.parkourType() ? selectedCourse(player) : null);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(block);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(ChatUtil.color("&aYou received &f" + type.plainName() + "&a."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    private ItemStack createBlockGadgetItem(BlockGadgetType type, String courseId) {
        List<Component> lore = new ArrayList<>(type.itemLore());
        String normalizedCourseId = normalizeCourseId(courseId);
        if (type.parkourType()) {
            lore.add(Component.empty());
            lore.add(Component.text("Course: ", NamedTextColor.GRAY).append(Component.text(normalizedCourseId, NamedTextColor.AQUA)));
        }

        ItemStack item = createItem(
                type.material(),
                type.displayName(),
                lore,
                true,
                false,
                blockGadgetKey,
                type.id());
        if (type.parkourType() && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(parkourCourseKey, PersistentDataType.STRING, normalizedCourseId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void giveBrush(Player player, BrushType brushType) {
        ItemStack brush = createBrushItem(brushType);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(brush);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(ChatUtil.color("&aYou received the &f" + brushType.brushLabel() + "&a brush."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    private ItemStack createBrushItem(BrushType brushType) {
        return createItem(
                Material.BRUSH,
                brushType.displayName(),
                brushType.description(),
                true,
                false,
                brushType.id());
    }

    private boolean handleDoorCommand(Player player, String[] args) {
        if (!canUseGadgets(player)) {
            player.sendMessage(ChatUtil.color("&cOnly builders can use /door."));
            return true;
        }

        if (args.length == 0) {
            sendDoorHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                createDoorFromSelection(player);
                return true;
            }
            case "delete" -> {
                deleteDoorFacing(player);
                return true;
            }
            case "wand", "tool", "give" -> {
                giveDoorTool(player);
                return true;
            }
            default -> {
                sendDoorHelp(player);
                return true;
            }
        }
    }

    private void sendDoorHelp(Player player) {
        player.sendMessage(ChatUtil.color("&eDoor tool commands:"));
        player.sendMessage(ChatUtil.color("&7/door wand &f- get the door selection tool"));
        player.sendMessage(ChatUtil.color("&7/door create &f- create a door from your current selection"));
        player.sendMessage(ChatUtil.color("&7/door delete &f- delete the door you are facing"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.1f);
    }

    private void giveDoorTool(Player player) {
        ItemStack tool = createDoorToolItem();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(tool);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(ChatUtil.color("&aYou received the &fDoor Tool&a."));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);
    }

    private ItemStack createDoorToolItem() {
        return createItem(
                Material.STONE_AXE,
                Component.text("Door Tool", TextColor.color(255, 205, 140)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Use this like a WorldEdit wand.", NamedTextColor.GRAY),
                        Component.text("Left-click sets the first corner.", NamedTextColor.YELLOW),
                        Component.text("Right-click sets the second corner.", NamedTextColor.YELLOW),
                        Component.text("Run /door create to turn the selection into a door.", NamedTextColor.YELLOW),
                        Component.text("Run /door delete while facing a door to remove it.", NamedTextColor.YELLOW)
                ),
                true,
                false,
                doorToolKey,
                "door_tool");
    }

    private void handleDoorToolInteract(Player player, PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        BlockKey clicked = BlockKey.from(clickedBlock.getLocation());
        if (clicked == null) {
            return;
        }

        DoorSelection selection = doorSelections.get(player.getUniqueId());
        if (selection == null) {
            selection = new DoorSelection(null, null);
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection = selection.withFirst(clicked);
            player.sendMessage(ChatUtil.color("&aDoor corner 1 set to &f" + formatBlockKey(clicked) + "&a."));
        } else {
            selection = selection.withSecond(clicked);
            player.sendMessage(ChatUtil.color("&aDoor corner 2 set to &f" + formatBlockKey(clicked) + "&a."));
        }

        doorSelections.put(player.getUniqueId(), selection);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.15f);

        DoorBounds bounds = selection.bounds();
        if (bounds != null) {
            player.sendMessage(ChatUtil.color("&7Current selection: &f" + bounds.sizeLabel() + "&7. Run &f/door create&7 when ready."));
        }
    }

    private void createDoorFromSelection(Player player) {
        DoorSelection selection = doorSelections.get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            player.sendMessage(ChatUtil.color("&cSelect both corners with the door tool first."));
            return;
        }

        DoorBounds bounds = selection.bounds();
        if (bounds == null) {
            player.sendMessage(ChatUtil.color("&cYour selection must be in a single world."));
            return;
        }

        if (!bounds.isValidDoor()) {
            player.sendMessage(ChatUtil.color("&cDoors must be one block thick and no larger than 5x5."));
            return;
        }

        if (findDoorIntersecting(bounds) != null) {
            player.sendMessage(ChatUtil.color("&cThat selection overlaps an existing door."));
            return;
        }

        World world = Bukkit.getWorld(bounds.worldId());
        if (world == null) {
            player.sendMessage(ChatUtil.color("&cThat world is not currently available."));
            return;
        }

        List<DoorBlockSnapshot> blocks = captureDoorBlocks(world, bounds);
        if (blocks.isEmpty()) {
            player.sendMessage(ChatUtil.color("&cThat selection did not contain any blocks to turn into a door."));
            return;
        }

        UUID doorId = UUID.randomUUID();
        DoorDefinition door = new DoorDefinition(doorId, bounds, blocks);
        doors.put(doorId, door);
        markDoorCreated(doorId, "Door " + bounds.sizeLabel(), player);
        hiddenDoors.remove(doorId);
        doorSelections.remove(player.getUniqueId());
        saveData();

        player.sendMessage(ChatUtil.color("&aCreated a &f" + bounds.sizeLabel() + "&a door."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.0f);
    }

    private void deleteDoorFacing(Player player) {
        Block target = player.getTargetBlockExact(10);
        if (target == null) {
            player.sendMessage(ChatUtil.color("&cFace a door block to delete it."));
            return;
        }

        DoorDefinition door = findDoorContaining(target.getLocation());
        if (door == null) {
            player.sendMessage(ChatUtil.color("&cYou are not facing a registered door."));
            return;
        }

        restoreDoor(door);
        doors.remove(door.id());
        hiddenDoors.remove(door.id());
        markDoorRemoved(door.id(), player);
        saveData();

        player.sendMessage(ChatUtil.color("&aDeleted the &f" + door.bounds().sizeLabel() + "&a door."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.0f);
    }

    private boolean isDoorTool(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_AXE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String toolId = meta.getPersistentDataContainer().get(doorToolKey, PersistentDataType.STRING);
        return "door_tool".equalsIgnoreCase(toolId);
    }

    private boolean isDoorBlock(Location location) {
        return findDoorContaining(location) != null;
    }

    private boolean containsDoorBlock(List<Block> blocks) {
        for (Block block : blocks) {
            if (isDoorBlock(block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private DoorDefinition findDoorContaining(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (DoorDefinition door : doors.values()) {
            if (door.contains(location)) {
                return door;
            }
        }

        return null;
    }

    private DoorDefinition findDoorIntersecting(DoorBounds bounds) {
        if (bounds == null) {
            return null;
        }

        for (DoorDefinition door : doors.values()) {
            if (door.bounds().intersects(bounds)) {
                return door;
            }
        }

        return null;
    }

    private List<DoorBlockSnapshot> captureDoorBlocks(World world, DoorBounds bounds) {
        List<DoorBlockSnapshot> snapshots = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    snapshots.add(new DoorBlockSnapshot(x, y, z, block.getBlockData().getAsString()));
                }
            }
        }
        return snapshots;
    }

    private void restoreDoor(DoorDefinition door) {
        World world = Bukkit.getWorld(door.bounds().worldId());
        if (world == null) {
            return;
        }

        for (DoorBlockSnapshot snapshot : door.blocks()) {
            try {
                BlockData blockData = Bukkit.createBlockData(snapshot.blockData());
                world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z()).setBlockData(blockData, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Doors] Failed to restore block data for door " + door.id() + ": " + snapshot.blockData());
            }
        }

        hiddenDoors.remove(door.id());
    }

    private void hideDoor(DoorDefinition door) {
        World world = Bukkit.getWorld(door.bounds().worldId());
        if (world == null) {
            return;
        }

        for (DoorBlockSnapshot snapshot : door.blocks()) {
            world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z()).setType(Material.AIR, false);
        }

        hiddenDoors.add(door.id());
    }

    private boolean shouldHideDoor(DoorDefinition door) {
        World world = Bukkit.getWorld(door.bounds().worldId());
        if (world == null) {
            return false;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getUID().equals(world.getUID()) && door.isNear(player.getLocation(), DOOR_PROXIMITY_PADDING)) {
                return true;
            }
        }

        return false;
    }

    private void tickDoors() {
        if (doors.isEmpty()) {
            return;
        }

        for (DoorDefinition door : doors.values()) {
            boolean shouldHide = shouldHideDoor(door);
            boolean hidden = hiddenDoors.contains(door.id());

            if (shouldHide && !hidden) {
                hideDoor(door);
            } else if (!shouldHide && hidden) {
                restoreDoor(door);
            }
        }
    }

    private String formatBlockKey(BlockKey key) {
        return key.x() + ", " + key.y() + ", " + key.z();
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean glint, boolean hideTooltip) {
        return createItem(material, name, lore, glint, hideTooltip, null);
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean glint, boolean hideTooltip, String brushId) {
        return createItem(material, name, lore, glint, hideTooltip, brushId, null, null);
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean glint, boolean hideTooltip, NamespacedKey persistentKey, String persistentValue) {
        return createItem(material, name, lore, glint, hideTooltip, null, persistentKey, persistentValue);
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean glint, boolean hideTooltip, String brushId, NamespacedKey persistentKey, String persistentValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (hideTooltip) {
                meta.setHideTooltip(true);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (brushId != null) {
                meta.getPersistentDataContainer().set(brushKey, PersistentDataType.STRING, brushId);
            }
            if (persistentKey != null && persistentValue != null) {
                meta.getPersistentDataContainer().set(persistentKey, PersistentDataType.STRING, persistentValue);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack decorativePane(Material material) {
        return createItem(material, Component.empty(), List.of(), false, true);
    }

    private BrushType brushTypeFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.BRUSH) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String brushId = meta.getPersistentDataContainer().get(brushKey, PersistentDataType.STRING);
        return BrushType.byId(brushId);
    }

    private BlockGadgetType blockGadgetTypeFromItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String typeId = meta.getPersistentDataContainer().get(blockGadgetKey, PersistentDataType.STRING);
        return BlockGadgetType.byId(typeId);
    }

    private String parkourCourseFromItem(ItemStack item, Player fallbackPlayer) {
        if (item != null && item.hasItemMeta()) {
            String courseId = item.getItemMeta().getPersistentDataContainer().get(parkourCourseKey, PersistentDataType.STRING);
            if (courseId != null && !courseId.isBlank()) {
                return normalizeCourseId(courseId);
            }
        }
        return selectedCourse(fallbackPlayer);
    }

    private void rememberFrozenChange(Player player, Block block) {
        if (player == null || block == null || !frozenBuilders.contains(player.getUniqueId())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Location origin = block.getLocation();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockKey key = BlockKey.from(origin.clone().add(dx, dy, dz));
                    if (key != null) {
                        frozenUpdateBlocks.put(key, uuid);
                    }
                }
            }
        }
    }

    private boolean isFrozenProtected(Block block) {
        BlockKey key = block == null ? null : BlockKey.from(block.getLocation());
        return key != null && frozenUpdateBlocks.containsKey(key);
    }

    private int thawFrozenUpdates(UUID uuid) {
        List<BlockKey> thawed = new ArrayList<>();
        frozenUpdateBlocks.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(uuid)) {
                return false;
            }
            thawed.add(entry.getKey());
            return true;
        });

        for (BlockKey key : thawed) {
            triggerPhysicsAround(key);
        }
        return thawed.size();
    }

    private void triggerPhysicsAround(BlockKey key) {
        Block block = blockForIfLoaded(key);
        if (block == null) {
            return;
        }

        List<Block> blocks = new ArrayList<>();
        blocks.add(block);
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            blocks.add(block.getRelative(face));
        }

        for (Block target : blocks) {
            target.setBlockData(target.getBlockData(), true);
        }
    }

    private BlockFace placedFacing(BlockPlaceEvent event) {
        BlockData blockData = event.getBlockPlaced().getBlockData();
        if (blockData instanceof Directional directional && isHorizontal(directional.getFacing())) {
            return directional.getFacing();
        }

        return yawToFace(event.getPlayer().getLocation().getYaw());
    }

    private BlockFace yawToFace(float yaw) {
        float wrapped = (yaw % 360.0F + 360.0F) % 360.0F;
        if (wrapped >= 45.0F && wrapped < 135.0F) {
            return BlockFace.WEST;
        }
        if (wrapped >= 135.0F && wrapped < 225.0F) {
            return BlockFace.NORTH;
        }
        if (wrapped >= 225.0F && wrapped < 315.0F) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private BlockFace nearestHorizontalFace(Block block, Location location) {
        double centerX = block.getX() + 0.5D;
        double centerZ = block.getZ() + 0.5D;
        double dx = location.getX() - centerX;
        double dz = location.getZ() - centerZ;

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0D ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0.0D ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace opposite(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            case UP -> BlockFace.DOWN;
            case DOWN -> BlockFace.UP;
            default -> BlockFace.SELF;
        };
    }

    private boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private BlockFace counterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private Vector vectorFor(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0.0D, 0.0D, -1.0D);
            case SOUTH -> new Vector(0.0D, 0.0D, 1.0D);
            case EAST -> new Vector(1.0D, 0.0D, 0.0D);
            case WEST -> new Vector(-1.0D, 0.0D, 0.0D);
            case UP -> new Vector(0.0D, 1.0D, 0.0D);
            case DOWN -> new Vector(0.0D, -1.0D, 0.0D);
            default -> new Vector(0.0D, 1.0D, 0.0D);
        };
    }

    private void startEffectTask() {
        stopEffectTask();
        effectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickEffects, EFFECT_SCAN_INTERVAL_TICKS, EFFECT_SCAN_INTERVAL_TICKS);
    }

    private void stopEffectTask() {
        if (effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(effectTaskId);
            effectTaskId = -1;
        }
    }

    private void tickEffects() {
        effectTick += (int) EFFECT_SCAN_INTERVAL_TICKS;
        if (effectTick % 20 == 0) {
            pruneMissingLoadedStates();
        }
        tickDoors();

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyBlockState(player);
            applyBlockGadgetState(player);
        }

        if (effectTick % ENTITY_GADGET_SCAN_INTERVAL_TICKS == 0) {
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (!(entity instanceof Player)) {
                        applyBlockGadgetState(entity);
                    }
                }

                for (Minecart minecart : world.getEntitiesByClass(Minecart.class)) {
                    applySuperRailState(minecart);
                }

                for (Boat boat : world.getEntitiesByClass(Boat.class)) {
                    applyBlockGadgetState(boat);
                }
            }

            applyGravityBlocks();
        }

        if (effectTick % PARTICLE_SCAN_INTERVAL_TICKS == 0) {
            spawnBlockGadgetParticles();
        }
        sendParkourTimers();
    }

    private void pruneMissingLoadedStates() {
        boolean changed = false;
        changed |= pruneMissingBrushes();
        changed |= pruneMissingBlockGadgets();
        if (changed) {
            saveData();
        }
    }

    private boolean pruneMissingBrushes() {
        boolean changed = false;
        for (BlockKey key : loadedIndexedKeys(brushStatesByChunk)) {
            GadgetState state = blockStates.get(key);
            if (state == null) {
                continue;
            }

            Block block = blockForIfLoaded(key);
            if (block == null || !state.matches(block)) {
                removeBrushState(key);
                changed = true;
            } else if (state.material() == null) {
                putBrushState(key, state.withMaterial(block.getType()));
                changed = true;
            }
        }
        return changed;
    }

    private boolean pruneMissingBlockGadgets() {
        boolean changed = false;
        for (BlockKey key : loadedIndexedKeys(blockGadgetsByChunk)) {
            BlockGadgetState state = blockGadgetStates.get(key);
            if (state == null) {
                continue;
            }

            Block block = blockForIfLoaded(key);
            if (block == null || block.getType() != state.type().material()) {
                removeBlockGadgetState(key);
                changed = true;
            }
        }
        return changed;
    }

    private void sendParkourTimers() {
        for (Map.Entry<UUID, ParkourSession> entry : parkourSessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            ParkourSession session = entry.getValue();
            NamedTextColor timeColor = session.running() ? NamedTextColor.AQUA : NamedTextColor.YELLOW;
            player.sendActionBar(Component.text("Parkour ", NamedTextColor.GREEN)
                    .append(Component.text(session.courseId(), NamedTextColor.WHITE))
                    .append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(formatParkourTime(session.elapsedMillis()), timeColor)));
        }
    }

    private void spawnBlockGadgetParticles() {
        Map<ParticleChunkKey, Integer> particleBlocksPerChunk = particleBlocksPerChunk();
        for (BlockKey key : loadedIndexedKeys(blockGadgetsByChunk)) {
            BlockGadgetState state = blockGadgetStates.get(key);
            if (state == null) {
                continue;
            }

            Block block = blockForIfLoaded(key);
            if (block == null || block.getWorld() == null) {
                continue;
            }

            Location center = block.getLocation().add(0.5D, 0.55D, 0.5D);
            double particleScale = particleScale(block, particleBlocksPerChunk);
            switch (state.type()) {
                case BOUNCY_SLIME -> spawnDust(
                        center,
                        5,
                        particleScale,
                        0.45D,
                        0.45D,
                        0.45D,
                        Color.fromRGB(116, 255, 122),
                        1.1F);
                case GRAVITY_BLOCK -> spawnDust(
                        center,
                        6,
                        particleScale,
                        0.9D,
                        0.9D,
                        0.9D,
                        Color.fromRGB(255, 184, 76),
                        1.2F);
                case PARKOUR_START -> spawnDust(
                        center,
                        3,
                        particleScale,
                        0.25D,
                        0.08D,
                        0.25D,
                        Color.fromRGB(92, 209, 255),
                        1.0F);
                case PARKOUR_CHECKPOINT -> spawnDust(
                        center,
                        3,
                        particleScale,
                        0.25D,
                        0.16D,
                        0.25D,
                        Color.fromRGB(169, 136, 255),
                        1.0F);
                case PARKOUR_FINISH -> spawnDust(
                        center,
                        3,
                        particleScale,
                        0.25D,
                        0.08D,
                        0.25D,
                        Color.fromRGB(118, 255, 187),
                        1.0F);
                case DASH_BLOCK, SUPER_RAIL -> {
                }
            }
        }
    }

    private Map<ParticleChunkKey, Integer> particleBlocksPerChunk() {
        Map<ParticleChunkKey, Integer> counts = new HashMap<>();
        for (BlockKey key : loadedIndexedKeys(blockGadgetsByChunk)) {
            BlockGadgetState state = blockGadgetStates.get(key);
            if (state == null || !state.type().emitsParticles()) {
                continue;
            }

            Block block = blockForIfLoaded(key);
            if (block == null || block.getWorld() == null) {
                continue;
            }

            ParticleChunkKey chunkKey = ParticleChunkKey.from(block);
            counts.put(chunkKey, counts.getOrDefault(chunkKey, 0) + 1);
        }
        return counts;
    }

    private double particleScale(Block block, Map<ParticleChunkKey, Integer> particleBlocksPerChunk) {
        int count = particleBlocksPerChunk.getOrDefault(ParticleChunkKey.from(block), 0);
        if (count <= 30) {
            return 1.0D;
        }
        if (count <= 45) {
            return 0.5D;
        }
        if (count <= 60) {
            return 0.35D;
        }
        if (count <= 90) {
            return 0.25D;
        }
        return 0.15D;
    }

    private void spawnDust(Location center, int baseCount, double scale, double offsetX, double offsetY, double offsetZ, Color color, float size) {
        if (center.getWorld() == null) {
            return;
        }

        int count = Math.max(1, (int) Math.round(baseCount * scale));
        center.getWorld().spawnParticle(
                Particle.DUST,
                center,
                count,
                offsetX,
                offsetY,
                offsetZ,
                new DustOptions(color, size));
    }

    private void applyBlockGadgetState(Entity entity) {
        TouchedBlockGadget touched = touchedBlockGadget(entity);
        if (touched == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastUse = blockGadgetCooldowns.getOrDefault(entity.getUniqueId(), 0L);
        long cooldown = touched.state().type() == BlockGadgetType.DASH_BLOCK ? DASH_GADGET_COOLDOWN_MS : BLOCK_GADGET_COOLDOWN_MS;
        if (now - lastUse < cooldown) {
            return;
        }
        blockGadgetCooldowns.put(entity.getUniqueId(), now);

        switch (touched.state().type()) {
            case BOUNCY_SLIME -> bounceEntity(entity, touched);
            case DASH_BLOCK -> dashEntity(entity, touched);
            case PARKOUR_START -> {
                if (entity instanceof Player player) {
                    startParkour(player, touched.key(), touched.state().courseId());
                }
            }
            case PARKOUR_CHECKPOINT -> {
                if (entity instanceof Player player) {
                    checkpointParkour(player, touched.key(), touched.state().courseId());
                }
            }
            case PARKOUR_FINISH -> {
                if (entity instanceof Player player) {
                    finishParkour(player, touched.state().courseId());
                }
            }
            case SUPER_RAIL, GRAVITY_BLOCK -> {
            }
        }
    }

    private TouchedBlockGadget touchedBlockGadget(Entity entity) {
        Location location = entity.getLocation();
        TouchedBlockGadget touched = touchedBlockGadgetAt(entity, location);
        if (touched != null) {
            return touched;
        }

        if (entity instanceof Boat) {
            Vector velocity = entity.getVelocity();
            Vector horizontal = new Vector(velocity.getX(), 0.0D, velocity.getZ());
            if (horizontal.lengthSquared() > 0.0025D) {
                Vector step = horizontal.normalize().multiply(0.85D);
                touched = touchedBlockGadgetAt(entity, location.clone().add(step));
                if (touched != null) {
                    return touched;
                }
                touched = touchedBlockGadgetAt(entity, location.clone().add(step.multiply(0.75D)));
                if (touched != null) {
                    return touched;
                }
            }
        }

        return null;
    }

    private TouchedBlockGadget touchedBlockGadgetAt(Entity entity, Location location) {
        Block below = location.clone().subtract(0.0D, 0.05D, 0.0D).getBlock();
        TouchedBlockGadget underfoot = touchedBlockGadget(below, BlockFace.UP);
        if (underfoot != null) {
            return underfoot;
        }

        Block feet = location.getBlock();
        TouchedBlockGadget insideFeet = touchedBlockGadget(feet, nearestHorizontalFace(feet, location));
        if (insideFeet != null) {
            return insideFeet;
        }

        Block head = location.clone().add(0.0D, headCheckHeight(entity), 0.0D).getBlock();
        TouchedBlockGadget insideHead = touchedBlockGadget(head, BlockFace.DOWN);
        if (insideHead != null) {
            return insideHead;
        }

        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (!isNearSide(feet, location, face)) {
                continue;
            }

            TouchedBlockGadget side = touchedBlockGadget(feet.getRelative(face), opposite(face));
            if (side != null) {
                return side;
            }

            TouchedBlockGadget upperSide = touchedBlockGadget(head.getRelative(face), opposite(face));
            if (upperSide != null) {
                return upperSide;
            }
        }

        return null;
    }

    private double headCheckHeight(Entity entity) {
        if (entity instanceof Player) {
            return 1.45D;
        }
        return Math.max(0.45D, Math.min(1.8D, entity.getBoundingBox().getHeight() * 0.75D));
    }

    private boolean isNearSide(Block block, Location location, BlockFace face) {
        return switch (face) {
            case NORTH -> location.getZ() - block.getZ() < 0.35D;
            case SOUTH -> block.getZ() + 1.0D - location.getZ() < 0.35D;
            case EAST -> block.getX() + 1.0D - location.getX() < 0.35D;
            case WEST -> location.getX() - block.getX() < 0.35D;
            default -> false;
        };
    }

    private TouchedBlockGadget touchedBlockGadget(Block block, BlockFace contactFace) {
        BlockKey key = BlockKey.from(block.getLocation());
        if (key == null) {
            return null;
        }

        BlockGadgetState state = blockGadgetStates.get(key);
        if (state == null) {
            return null;
        }
        if (block.getType() != state.type().material()) {
            removeBlockGadgetState(key);
            saveData();
            return null;
        }

        return new TouchedBlockGadget(key, state, contactFace);
    }

    private void bounceEntity(Entity entity, TouchedBlockGadget touched) {
        Vector velocity;
        if (touched.contactFace() == BlockFace.DOWN) {
            velocity = new Vector(0.0D, BOUNCE_DOWN_SPEED, 0.0D);
        } else if (touched.contactFace() == BlockFace.UP) {
            velocity = entity.getVelocity().setY(BOUNCE_UP_SPEED);
        } else {
            Vector direction = vectorFor(touched.contactFace());
            velocity = direction.multiply(BOUNCE_SIDE_SPEED).setY(Math.max(entity.getVelocity().getY() * 0.35D, BOUNCE_SIDE_LIFT));
        }

        entity.setVelocity(velocity);
        playWorldSound(entity.getLocation(), Sound.BLOCK_SLIME_BLOCK_HIT, 0.75f, 1.2f);
    }

    private void dashEntity(Entity entity, TouchedBlockGadget touched) {
        BlockFace dashFace = dashFaceFor(touched);
        Vector direction = vectorFor(dashFace);

        Vector velocity = direction.multiply(DASH_SPEED);
        if (dashFace == BlockFace.UP) {
            velocity.setY(DASH_SPEED);
        } else if (dashFace == BlockFace.DOWN) {
            velocity.setY(-DASH_SPEED);
        } else {
            velocity.setY(Math.max(entity.getVelocity().getY(), HORIZONTAL_DASH_LIFT));
        }

        entity.setVelocity(velocity);
        playWorldSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.65f, 1.35f);
    }

    private void playWorldSound(Location location, Sound sound, float volume, float pitch) {
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }

    private BlockFace dashFaceFor(TouchedBlockGadget touched) {
        BlockFace base = currentDashFacing(touched);
        BlockFace contact = touched.contactFace();
        if (contact == BlockFace.UP) {
            return opposite(base);
        }
        if (contact == BlockFace.DOWN) {
            return base;
        }
        if (contact == base) {
            return clockwise(base);
        }
        if (contact == opposite(base)) {
            return clockwise(base);
        }
        if (contact == clockwise(base)) {
            return BlockFace.UP;
        }
        if (contact == counterClockwise(base)) {
            return BlockFace.DOWN;
        }
        return base;
    }

    private BlockFace currentDashFacing(TouchedBlockGadget touched) {
        if (isHorizontal(touched.state().facing())) {
            return touched.state().facing();
        }

        Block block = blockFor(touched.key());
        if (block != null) {
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Directional directional && isHorizontal(directional.getFacing())) {
                return directional.getFacing();
            }
        }

        return BlockFace.SOUTH;
    }

    private Block blockFor(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private Block blockForIfLoaded(BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private void applySuperRailState(Minecart minecart) {
        TouchedBlockGadget touched = minecartSuperRail(minecart);
        if (touched == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastUse = blockGadgetCooldowns.getOrDefault(minecart.getUniqueId(), 0L);
        if (now - lastUse < BLOCK_GADGET_COOLDOWN_MS) {
            return;
        }
        blockGadgetCooldowns.put(minecart.getUniqueId(), now);

        Vector velocity = minecart.getVelocity();
        Vector horizontal = new Vector(velocity.getX(), 0.0D, velocity.getZ());
        if (horizontal.lengthSquared() < SUPER_RAIL_MIN_INPUT_SPEED * SUPER_RAIL_MIN_INPUT_SPEED) {
            return;
        }

        double currentSpeed = horizontal.length();
        double nextSpeed = Math.min(SUPER_RAIL_MAX_SPEED, Math.max(currentSpeed + SUPER_RAIL_MIN_BOOST, currentSpeed * SUPER_RAIL_BOOST_MULTIPLIER));
        Vector boosted = horizontal.normalize().multiply(nextSpeed);
        boosted.setY(velocity.getY());
        minecart.setMaxSpeed(SUPER_RAIL_MAX_SPEED);
        minecart.setVelocity(boosted);
        playWorldSound(minecart.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.35f);
    }

    private void applyGravityBlocks() {
        for (BlockKey key : loadedIndexedKeys(blockGadgetsByChunk)) {
            BlockGadgetState state = blockGadgetStates.get(key);
            if (state == null || state.type() != BlockGadgetType.GRAVITY_BLOCK) {
                continue;
            }

            Block block = blockForIfLoaded(key);
            if (block == null || block.getWorld() == null) {
                continue;
            }

            Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
            for (Entity entity : block.getWorld().getNearbyEntities(center, GRAVITY_BLOCK_RANGE, GRAVITY_BLOCK_RANGE, GRAVITY_BLOCK_RANGE)) {
                if (!canGravityAffect(entity)) {
                    continue;
                }

                Vector pull = center.toVector().subtract(entity.getLocation().toVector());
                double distance = pull.length();
                if (distance < 0.15D || distance > GRAVITY_BLOCK_RANGE) {
                    continue;
                }

                Vector velocity = gravityVelocity(entity.getVelocity(), pull.normalize(), distance);
                entity.setVelocity(velocity);
            }
        }
    }

    private Vector gravityVelocity(Vector current, Vector pullDirection, double distance) {
        Vector velocity = current.clone();
        double pullStrength;

        if (distance > 3.0D) {
            pullStrength = 0.012D;
            if (velocity.getY() < 0.0D) {
                velocity.setY(velocity.getY() * 0.94D);
            }
            velocity.multiply(0.985D);
        } else if (distance > 2.0D) {
            pullStrength = 0.032D;
            if (velocity.getY() < 0.0D) {
                velocity.setY(velocity.getY() * 0.86D);
            }
            velocity.multiply(0.94D);
        } else if (distance > 1.0D) {
            pullStrength = 0.07D;
            if (velocity.getY() < 0.0D) {
                velocity.setY(velocity.getY() * 0.62D);
            }
            velocity.multiply(0.72D);
        } else {
            pullStrength = 0.20D;
            velocity.multiply(0.74D);
        }

        velocity.add(pullDirection.multiply(pullStrength));
        if (velocity.length() > GRAVITY_BLOCK_MAX_PULL_SPEED) {
            velocity.normalize().multiply(GRAVITY_BLOCK_MAX_PULL_SPEED);
        }
        return velocity;
    }

    private boolean canGravityAffect(Entity entity) {
        if (!(entity instanceof LivingEntity) && !(entity instanceof Boat) && !(entity instanceof Minecart)) {
            return false;
        }

        if (entity instanceof Player player && player.isFlying()) {
            return false;
        }

        return true;
    }

    private void startParkour(Player player, BlockKey startKey, String rawCourseId) {
        String courseId = normalizeCourseId(rawCourseId);
        ParkourSession existing = parkourSessions.get(player.getUniqueId());
        if (existing != null) {
            if (!courseId.equals(existing.courseId())) {
                restoreParkourHotbar(player);
                startParkour(player, startKey, courseId);
                return;
            }

            if (courseId.equals(existing.courseId()) && startKey.equals(existing.startKey()) && startKey.equals(existing.checkpointKey()) && existing.running()) {
                return;
            }

            existing.startKey(startKey);
            existing.checkpointKey(startKey);
            existing.running(true);
            existing.startedAtMillis(System.currentTimeMillis());
            existing.pausedElapsedMillis(0L);
            giveParkourHotbar(player, true);
            teleportToBlock(player, startKey);
            player.sendMessage(ChatUtil.color("&aParkour restarted. &7Course: &f" + courseId));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
            return;
        }

        ItemStack[] savedHotbar = new ItemStack[9];
        for (int slot = 0; slot < savedHotbar.length; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            savedHotbar[slot] = item == null ? null : item.clone();
        }

        ParkourSession session = new ParkourSession(savedHotbar, courseId, startKey, startKey, true, System.currentTimeMillis(), 0L);
        parkourSessions.put(player.getUniqueId(), session);
        giveParkourHotbar(player, true);
        player.sendMessage(ChatUtil.color("&aParkour started. &7Course: &f" + courseId + "&7. Your hotbar is saved until you finish or exit."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    private void checkpointParkour(Player player, BlockKey checkpointKey, String rawCourseId) {
        ParkourSession session = parkourSessions.get(player.getUniqueId());
        if (session == null || !session.running() || !session.courseId().equals(normalizeCourseId(rawCourseId))) {
            return;
        }

        if (!checkpointKey.equals(session.checkpointKey())) {
            session.checkpointKey(checkpointKey);
            player.sendMessage(ChatUtil.color("&bCheckpoint saved."));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.35f);
        }
    }

    private void finishParkour(Player player, String rawCourseId) {
        ParkourSession session = parkourSessions.get(player.getUniqueId());
        if (session == null || !session.running() || !session.courseId().equals(normalizeCourseId(rawCourseId))) {
            return;
        }

        String formattedTime = formatParkourTime(session.elapsedMillis());
        restoreParkourHotbar(player);
        player.sendMessage(ChatUtil.color("&aParkour finished in &f" + formattedTime + "&a. &7Your hotbar has been restored."));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
    }

    private boolean handleParkourControl(Player player, ItemStack item) {
        ParkourControl control = parkourControlFromItem(item);
        if (control == null) {
            return false;
        }

        ParkourSession session = parkourSessions.get(player.getUniqueId());
        if (session == null) {
            return true;
        }

        switch (control) {
            case START_STOP -> {
                if (session.running()) {
                    session.pausedElapsedMillis(session.elapsedMillis());
                    session.running(false);
                } else {
                    session.startedAtMillis(System.currentTimeMillis());
                    session.running(true);
                }
                giveParkourHotbar(player, session.running());
                player.sendMessage(ChatUtil.color(session.running() ? "&aParkour resumed." : "&eParkour paused."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, session.running() ? 1.2f : 0.85f);
            }
            case CHECKPOINT -> {
                teleportToBlock(player, session.checkpointKey());
                player.sendMessage(ChatUtil.color("&bReturned to checkpoint."));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.15f);
            }
            case RESET -> {
                session.checkpointKey(session.startKey());
                session.running(true);
                session.startedAtMillis(System.currentTimeMillis());
                session.pausedElapsedMillis(0L);
                giveParkourHotbar(player, true);
                teleportToBlock(player, session.startKey());
                player.sendMessage(ChatUtil.color("&eParkour reset."));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.9f);
            }
            case EXIT -> {
                restoreParkourHotbar(player);
                player.sendMessage(ChatUtil.color("&cExited parkour. &7Your hotbar has been restored."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 0.75f);
            }
        }
        return true;
    }

    private void giveParkourHotbar(Player player, boolean running) {
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItem(slot, null);
        }

        player.getInventory().setItem(0, createParkourControlItem(ParkourControl.START_STOP, running));
        player.getInventory().setItem(3, createParkourControlItem(ParkourControl.CHECKPOINT, running));
        player.getInventory().setItem(5, createParkourControlItem(ParkourControl.RESET, running));
        player.getInventory().setItem(8, createParkourControlItem(ParkourControl.EXIT, running));
    }

    private ItemStack createParkourControlItem(ParkourControl control, boolean running) {
        Material material = switch (control) {
            case START_STOP -> running ? Material.REDSTONE_TORCH : Material.LEVER;
            case CHECKPOINT -> Material.ENDER_PEARL;
            case RESET -> Material.COMPASS;
            case EXIT -> Material.BARRIER;
        };

        Component name = switch (control) {
            case START_STOP -> Component.text(running ? "Stop Parkour" : "Start Parkour", running ? NamedTextColor.RED : NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
            case CHECKPOINT -> Component.text("Return to Checkpoint", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
            case RESET -> Component.text("Reset Parkour", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
            case EXIT -> Component.text("Exit Parkour", NamedTextColor.RED).decorate(TextDecoration.BOLD);
        };

        List<Component> lore = switch (control) {
            case START_STOP -> List.of(Component.text("Toggle your parkour run.", NamedTextColor.GRAY));
            case CHECKPOINT -> List.of(Component.text("Teleport to your saved checkpoint.", NamedTextColor.GRAY));
            case RESET -> List.of(Component.text("Return to the start plate.", NamedTextColor.GRAY));
            case EXIT -> List.of(Component.text("Leave parkour and restore your hotbar.", NamedTextColor.GRAY));
        };

        return createItem(material, name, lore, true, false, parkourControlKey, control.id());
    }

    private ParkourControl parkourControlFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String id = item.getItemMeta().getPersistentDataContainer().get(parkourControlKey, PersistentDataType.STRING);
        return ParkourControl.byId(id);
    }

    private void restoreParkourHotbar(Player player) {
        ParkourSession session = parkourSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack saved = session.savedHotbar()[slot];
            player.getInventory().setItem(slot, saved == null ? null : saved.clone());
        }
    }

    private void teleportToBlock(Player player, BlockKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return;
        }

        Location destination = new Location(world, key.x() + 0.5D, key.y() + 1.05D, key.z() + 0.5D, player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleport(destination);
    }

    private String selectedCourse(Player player) {
        return selectedParkourCourses.getOrDefault(player.getUniqueId(), "default");
    }

    private String sanitizeCourseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "default";
        }

        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        while (cleaned.contains("--")) {
            cleaned = cleaned.replace("--", "-");
        }
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        return cleaned.isBlank() ? "default" : cleaned;
    }

    private String normalizeCourseId(String courseId) {
        return sanitizeCourseId(courseId);
    }

    private String formatParkourTime(long elapsedMillis) {
        long minutes = elapsedMillis / 60000L;
        long seconds = (elapsedMillis % 60000L) / 1000L;
        long tenths = (elapsedMillis % 1000L) / 100L;
        if (minutes > 0L) {
            return minutes + "m " + seconds + "." + tenths + "s";
        }
        return seconds + "." + tenths + "s";
    }

    private String ago(long epochSeconds) {
        long seconds = Math.max(0L, Duration.between(Instant.ofEpochSecond(epochSeconds), Instant.now()).getSeconds());
        if (seconds >= 31_536_000L) {
            return plural(seconds / 31_536_000L, "year");
        }
        if (seconds >= 2_592_000L) {
            return plural(seconds / 2_592_000L, "month");
        }
        if (seconds >= 86_400L) {
            return plural(seconds / 86_400L, "day");
        }
        if (seconds >= 3_600L) {
            return plural(seconds / 3_600L, "hour");
        }
        if (seconds >= 60L) {
            return plural(seconds / 60L, "minute");
        }
        return plural(seconds, "second");
    }

    private String plural(long amount, String unit) {
        return amount + " " + unit + (amount == 1L ? "" : "s");
    }

    private TouchedBlockGadget minecartSuperRail(Minecart minecart) {
        Location location = minecart.getLocation();
        for (Block block : List.of(location.getBlock(), location.clone().subtract(0.0D, 0.35D, 0.0D).getBlock())) {
            TouchedBlockGadget touched = touchedBlockGadget(block, BlockFace.UP);
            if (touched != null && touched.state().type() == BlockGadgetType.SUPER_RAIL) {
                return touched;
            }
        }
        return null;
    }

    private void applyBlockState(Player player) {
        Block triggerBlock = triggerBlock(player.getLocation());
        if (triggerBlock == null) {
            lastActionTriggers.remove(player.getUniqueId());
            return;
        }

        BlockKey key = BlockKey.from(triggerBlock.getLocation());
        if (key == null) {
            lastActionTriggers.remove(player.getUniqueId());
            return;
        }

        GadgetState state = blockStates.get(key);
        if (state == null) {
            lastActionTriggers.remove(player.getUniqueId());
            return;
        }
        if (!state.matches(triggerBlock)) {
            removeBrushState(key);
            saveData();
            lastActionTriggers.remove(player.getUniqueId());
            return;
        }
        if (state.material() == null) {
            putBrushState(key, state.withMaterial(triggerBlock.getType()));
            saveData();
        }

        activateState(player, key, state, false);
    }

    private void activateState(Player player, BlockKey key, GadgetState state, boolean directInteraction) {
        if (state.type().singleTrigger()) {
            if (!directInteraction && key.equals(lastActionTriggers.get(player.getUniqueId()))) {
                return;
            }
            lastActionTriggers.put(player.getUniqueId(), key);
        } else {
            lastActionTriggers.remove(player.getUniqueId());
        }

        if (state.type() == BrushType.WARP) {
            activateWarpState(player, state);
            return;
        }

        if (state.type() == BrushType.TIME) {
            String timeValue = state.value();
            if (timeValue == null || timeValue.isBlank()) {
                player.sendMessage(ChatUtil.color("&cThis time brush is missing a time value."));
                return;
            }
            Bukkit.dispatchCommand(player, "ptime " + timeValue);
            return;
        }

        state.type().apply(player, state.level());
    }

    private void activateWarpState(Player player, GadgetState state) {
        String warpName = state.value();
        if (warpName == null || warpName.isBlank()) {
            player.sendMessage(ChatUtil.color("&cThis warp brush is missing a warp name."));
            return;
        }

        String matchedWarp = findEssentialsWarp(warpName);
        if (matchedWarp == null) {
            player.sendMessage(ChatUtil.color("&cThere is no warp named &f" + warpName + "&c."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        Bukkit.dispatchCommand(player, "warp " + matchedWarp);
    }

    private Block triggerBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.clone().subtract(0.0D, 0.05D, 0.0D).getBlock();
    }

    private boolean isInteractiveTriggerBlock(Material material) {
        return material == Material.LEVER || material.name().endsWith("_BUTTON");
    }

    private String cleanWarpName(String raw) {
        if (raw == null) {
            return null;
        }

        String cleaned = raw.trim();
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("warp-name")) {
            return null;
        }

        if (cleaned.startsWith("/warp ")) {
            cleaned = cleaned.substring(6).trim();
        }

        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanTimeValue(String raw) {
        if (raw == null) {
            return null;
        }

        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty() || cleaned.equals("12:00") || cleaned.equals("time")) {
            if (cleaned.equals("12:00")) {
                return cleaned;
            }
            return null;
        }

        if (!cleaned.matches("\\d{1,2}(:\\d{1,2})?")) {
            return null;
        }

        String[] parts = cleaned.split(":", -1);
        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(parts[0]);
            if (parts.length == 2) {
                minute = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        if (hour < 0 || hour > 24 || minute < 0 || minute > 59 || (hour == 24 && minute != 0)) {
            return null;
        }

        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private String findEssentialsWarp(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return null;
        }

        for (String warpName : getEssentialsWarpNames()) {
            if (warpName.equalsIgnoreCase(requestedName.trim())) {
                return warpName;
            }
        }
        return null;
    }

    private List<String> getEssentialsWarpNames() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            essentials = Bukkit.getPluginManager().getPlugin("EssentialsX");
        }
        if (essentials == null) {
            return List.of();
        }

        try {
            Method getWarpsMethod = essentials.getClass().getMethod("getWarps");
            Object warps = getWarpsMethod.invoke(essentials);
            Method getListMethod = warps.getClass().getMethod("getList");
            Object response = getListMethod.invoke(warps);

            List<String> names = new ArrayList<>();
            if (response instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    if (entry != null) {
                        names.add(String.valueOf(entry));
                    }
                }
            } else if (response instanceof Object[] array) {
                for (Object entry : array) {
                    if (entry != null) {
                        names.add(String.valueOf(entry));
                    }
                }
            }
            return names;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read Essentials warps: " + ex.getMessage());
            return List.of();
        }
    }

    private void setState(BlockKey key, GadgetState state, Player creator, String typeLabel) {
        putBrushState(key, state);
        markBlockCreated(key, typeLabel, creator);
        saveData();
    }

    private void putBrushState(BlockKey key, GadgetState state) {
        blockStates.put(key, state);
        indexKey(brushStatesByChunk, key);
    }

    private GadgetState removeBrushState(BlockKey key) {
        GadgetState removed = blockStates.remove(key);
        if (removed != null) {
            unindexKey(brushStatesByChunk, key);
        }
        return removed;
    }

    private void putBlockGadgetState(BlockKey key, BlockGadgetState state) {
        blockGadgetStates.put(key, state);
        indexKey(blockGadgetsByChunk, key);
    }

    private BlockGadgetState removeBlockGadgetState(BlockKey key) {
        BlockGadgetState removed = blockGadgetStates.remove(key);
        if (removed != null) {
            unindexKey(blockGadgetsByChunk, key);
        }
        return removed;
    }

    private void indexKey(Map<ChunkKey, Set<BlockKey>> index, BlockKey key) {
        index.computeIfAbsent(ChunkKey.from(key), ignored -> new HashSet<>()).add(key);
    }

    private void unindexKey(Map<ChunkKey, Set<BlockKey>> index, BlockKey key) {
        ChunkKey chunkKey = ChunkKey.from(key);
        Set<BlockKey> keys = index.get(chunkKey);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            index.remove(chunkKey);
        }
    }

    private List<BlockKey> loadedIndexedKeys(Map<ChunkKey, Set<BlockKey>> index) {
        List<BlockKey> keys = new ArrayList<>();
        for (Map.Entry<ChunkKey, Set<BlockKey>> entry : index.entrySet()) {
            if (!entry.getKey().isLoaded()) {
                continue;
            }
            keys.addAll(entry.getValue());
        }
        return keys;
    }

    private Material materialFor(BlockKey key) {
        Block block = blockForIfLoaded(key);
        return block == null ? null : block.getType();
    }

    private void removeState(Location location) {
        removeState(location, null);
    }

    private void removeState(Location location, Player remover) {
        BlockKey key = BlockKey.from(location);
        if (key == null) {
            return;
        }

        if (removeBrushState(key) != null) {
            if (remover != null) {
                markBlockRemoved(key, remover);
            }
            saveData();
            return;
        }

        if (removeBlockGadgetState(key) != null) {
            saveData();
        }
    }

    private void markBlockCreated(BlockKey key, String typeLabel, Player player) {
        if (!shouldAuditBlockType(typeLabel)) {
            return;
        }
        blockAudit.put(key, GadgetAuditEntry.created(typeLabel, player));
        saveAuditData();
    }

    private void markBlockRemoved(BlockKey key, Player player) {
        GadgetAuditEntry current = blockAudit.get(key);
        blockAudit.put(key, (current == null ? GadgetAuditEntry.created("Unknown Gadget", player) : current).removed(player));
        saveAuditData();
    }

    private void markDoorCreated(UUID doorId, String typeLabel, Player player) {
        doorAudit.put(doorId, GadgetAuditEntry.created(typeLabel, player));
        saveAuditData();
    }

    private void markDoorRemoved(UUID doorId, Player player) {
        GadgetAuditEntry current = doorAudit.get(doorId);
        doorAudit.put(doorId, (current == null ? GadgetAuditEntry.created("Door", player) : current).removed(player));
        saveAuditData();
    }

    private void loadData() {
        plugin.reloadConfig();
        blockStates.clear();
        blockGadgetStates.clear();
        brushStatesByChunk.clear();
        blockGadgetsByChunk.clear();
        doors.clear();
        hiddenDoors.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gadgets.brushes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                if (blockKey == null) {
                    continue;
                }

                GadgetState state = GadgetState.parse(section.getString(key));
                if (state != null) {
                    putBrushState(blockKey, state);
                    continue;
                }

                String typeId = section.getString(key + ".type");
                BrushType type = BrushType.byId(typeId);
                if (type == null) {
                    continue;
                }

                int level = section.getInt(key + ".level", 0);
                String value = section.getString(key + ".value");
                Material material = null;
                String materialName = section.getString(key + ".material");
                if (materialName != null && !materialName.isBlank()) {
                    try {
                        material = Material.valueOf(materialName);
                    } catch (IllegalArgumentException ignored) {
                        material = null;
                    }
                }
                putBrushState(blockKey, new GadgetState(type, level, value, material));
            }
        }

        ConfigurationSection gadgetSection = plugin.getConfig().getConfigurationSection("gadgets.block-gadgets");
        if (gadgetSection != null) {
            for (String key : gadgetSection.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                if (blockKey == null) {
                    continue;
                }

                BlockGadgetState state = BlockGadgetState.parse(gadgetSection.getString(key));
                if (state != null) {
                    putBlockGadgetState(blockKey, state);
                    continue;
                }
            }
        }

        loadDoors();
        migrateLegacyGadgetFilesIfNeeded();
        restoreAllDoors();
    }

    private void loadDoors() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gadgets.doors");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            DoorDefinition compactDoor = DoorDefinition.parse(key, section.getString(key));
            if (compactDoor != null) {
                doors.put(compactDoor.id(), compactDoor);
                continue;
            }

            UUID id;
            UUID worldId;
            try {
                id = UUID.fromString(key);
                worldId = UUID.fromString(section.getString(key + ".world", ""));
            } catch (IllegalArgumentException ex) {
                continue;
            }

            int minX = section.getInt(key + ".minX");
            int minY = section.getInt(key + ".minY");
            int minZ = section.getInt(key + ".minZ");
            int maxX = section.getInt(key + ".maxX");
            int maxY = section.getInt(key + ".maxY");
            int maxZ = section.getInt(key + ".maxZ");

            List<DoorBlockSnapshot> snapshots = new ArrayList<>();
            for (String raw : section.getStringList(key + ".blocks")) {
                DoorBlockSnapshot snapshot = DoorBlockSnapshot.parse(raw);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }

            if (snapshots.isEmpty()) {
                continue;
            }

            DoorBounds bounds = new DoorBounds(worldId, minX, minY, minZ, maxX, maxY, maxZ);
            doors.put(id, new DoorDefinition(id, bounds, List.copyOf(snapshots)));
        }
    }

    private void restoreAllDoors() {
        for (DoorDefinition door : doors.values()) {
            restoreDoor(door);
        }
    }

    private void saveData() {
        plugin.getConfig().set("gadgets.brushes", null);
        for (Map.Entry<BlockKey, GadgetState> entry : blockStates.entrySet()) {
            plugin.getConfig().set("gadgets.brushes." + entry.getKey().serialize(), entry.getValue().serialize());
        }

        plugin.getConfig().set("gadgets.block-gadgets", null);
        for (Map.Entry<BlockKey, BlockGadgetState> entry : blockGadgetStates.entrySet()) {
            plugin.getConfig().set("gadgets.block-gadgets." + entry.getKey().serialize(), entry.getValue().serialize());
        }

        plugin.getConfig().set("gadgets.doors", null);
        for (DoorDefinition door : doors.values()) {
            plugin.getConfig().set("gadgets.doors." + door.id(), door.serialize());
        }

        plugin.saveConfig();
    }

    private void loadAuditData() {
        blockAudit.clear();
        doorAudit.clear();

        YamlConfiguration logs = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gadget-logs.yml"));
        ConfigurationSection blocks = logs.getConfigurationSection("brushes");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                GadgetAuditEntry entry = GadgetAuditEntry.parse(blocks.getString(key));
                if (blockKey != null && entry != null) {
                    blockAudit.put(blockKey, entry);
                }
            }
        }

        ConfigurationSection doorSection = logs.getConfigurationSection("doors");
        if (doorSection != null) {
            for (String key : doorSection.getKeys(false)) {
                try {
                    UUID doorId = UUID.fromString(key);
                    GadgetAuditEntry entry = GadgetAuditEntry.parse(doorSection.getString(key));
                    if (entry != null) {
                        doorAudit.put(doorId, entry);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveAuditData() {
        YamlConfiguration logs = new YamlConfiguration();
        for (Map.Entry<BlockKey, GadgetAuditEntry> entry : blockAudit.entrySet()) {
            logs.set("brushes." + entry.getKey().serialize(), entry.getValue().serialize());
        }
        for (Map.Entry<UUID, GadgetAuditEntry> entry : doorAudit.entrySet()) {
            logs.set("doors." + entry.getKey(), entry.getValue().serialize());
        }

        try {
            logs.save(new File(plugin.getDataFolder(), "gadget-logs.yml"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save gadget-logs.yml: " + ex.getMessage());
        }
        plugin.getConfig().set("gadgets.inspect", null);
        plugin.saveConfig();
    }

    private void migrateLegacyGadgetFilesIfNeeded() {
        boolean migrated = false;
        File legacyDataFile = new File(plugin.getDataFolder(), "gadgets.yml");
        if (legacyDataFile.isFile()) {
            migrated = true;
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyDataFile);
            loadLegacyGadgetData(legacy);
            if (!legacyDataFile.delete()) {
                plugin.getLogger().warning("Migrated gadgets.yml into config.yml, but could not delete the old file.");
            }
        }

        File legacyAuditFile = new File(plugin.getDataFolder(), "gadget-audit.yml");
        if (legacyAuditFile.isFile()) {
            migrated = true;
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyAuditFile);
            loadLegacyAuditData(legacy);
            if (!legacyAuditFile.delete()) {
                plugin.getLogger().warning("Migrated gadget-audit.yml into gadget-logs.yml, but could not delete the old file.");
            }
        }

        ConfigurationSection oldConfigLogs = plugin.getConfig().getConfigurationSection("gadgets.inspect");
        if (oldConfigLogs != null) {
            migrated = true;
            ConfigurationSection oldBrushes = oldConfigLogs.getConfigurationSection("brushes");
            if (oldBrushes != null) {
                for (String key : oldBrushes.getKeys(false)) {
                    BlockKey blockKey = BlockKey.parse(key);
                    GadgetAuditEntry entry = GadgetAuditEntry.parse(oldBrushes.getString(key));
                    if (blockKey != null && entry != null) {
                        blockAudit.putIfAbsent(blockKey, entry);
                    }
                }
            }

            ConfigurationSection oldDoors = oldConfigLogs.getConfigurationSection("doors");
            if (oldDoors != null) {
                for (String key : oldDoors.getKeys(false)) {
                    try {
                        UUID doorId = UUID.fromString(key);
                        GadgetAuditEntry entry = GadgetAuditEntry.parse(oldDoors.getString(key));
                        if (entry != null) {
                            doorAudit.putIfAbsent(doorId, entry);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            plugin.getConfig().set("gadgets.inspect", null);
        }

        if (migrated) {
            saveData();
            saveAuditData();
        }
    }

    private void loadLegacyGadgetData(YamlConfiguration legacy) {
        ConfigurationSection section = legacy.getConfigurationSection("blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                BrushType type = BrushType.byId(section.getString(key + ".type"));
                if (blockKey != null && type != null) {
                    if (!blockStates.containsKey(blockKey)) {
                        putBrushState(blockKey, new GadgetState(type, section.getInt(key + ".level", 0), section.getString(key + ".value"), null));
                    }
                }
            }
        }

        ConfigurationSection gadgetSection = legacy.getConfigurationSection("block-gadgets");
        if (gadgetSection != null) {
            for (String key : gadgetSection.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                BlockGadgetType type = BlockGadgetType.byId(gadgetSection.getString(key + ".type"));
                if (blockKey == null || type == null) {
                    continue;
                }

                BlockFace facing;
                try {
                    facing = BlockFace.valueOf(gadgetSection.getString(key + ".facing", BlockFace.SELF.name()));
                } catch (IllegalArgumentException ex) {
                    facing = BlockFace.SELF;
                }
                if (!blockGadgetStates.containsKey(blockKey)) {
                    putBlockGadgetState(blockKey, new BlockGadgetState(type, facing, gadgetSection.getString(key + ".course")));
                }
            }
        }

        ConfigurationSection doorSection = legacy.getConfigurationSection("doors");
        if (doorSection != null) {
            for (String key : doorSection.getKeys(false)) {
                DoorDefinition door = DoorDefinition.parseLegacy(doorSection, key);
                if (door != null) {
                    doors.putIfAbsent(door.id(), door);
                }
            }
        }
    }

    private void loadLegacyAuditData(YamlConfiguration legacy) {
        ConfigurationSection blocks = legacy.getConfigurationSection("blocks");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                BlockKey blockKey = BlockKey.parse(key);
                GadgetAuditEntry entry = GadgetAuditEntry.loadLegacy(blocks, key);
                if (blockKey != null && entry != null && shouldAuditBlockType(entry.typeLabel())) {
                    blockAudit.putIfAbsent(blockKey, entry);
                }
            }
        }

        ConfigurationSection doorSection = legacy.getConfigurationSection("doors");
        if (doorSection != null) {
            for (String key : doorSection.getKeys(false)) {
                try {
                    UUID doorId = UUID.fromString(key);
                    GadgetAuditEntry entry = GadgetAuditEntry.loadLegacy(doorSection, key);
                    if (entry != null) {
                        doorAudit.putIfAbsent(doorId, entry);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private boolean shouldAuditBlockType(String typeLabel) {
        return typeLabel != null && typeLabel.toLowerCase(Locale.ROOT).contains("brush");
    }

    private boolean isBedrockPlayer(UUID uuid) {
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

    private static void addEffect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, EFFECT_REFRESH_TICKS, amplifier, false, false, false));
    }

    private static void removeEffect(Player player, PotionEffectType... types) {
        for (PotionEffectType type : types) {
            player.removePotionEffect(type);
        }
    }

    private record DoorSelection(BlockKey first, BlockKey second) {

        private DoorSelection withFirst(BlockKey newFirst) {
            return new DoorSelection(newFirst, second);
        }

        private DoorSelection withSecond(BlockKey newSecond) {
            return new DoorSelection(first, newSecond);
        }

        private boolean isComplete() {
            return first != null && second != null;
        }

        private DoorBounds bounds() {
            return DoorBounds.from(first, second);
        }
    }

    private record DoorDefinition(UUID id, DoorBounds bounds, List<DoorBlockSnapshot> blocks) {

        private boolean contains(Location location) {
            return bounds.contains(location);
        }

        private boolean isNear(Location location, int padding) {
            return bounds.isNear(location, padding);
        }

        private String serialize() {
            List<String> pieces = new ArrayList<>();
            pieces.add(bounds.serialize());
            for (DoorBlockSnapshot block : blocks) {
                pieces.add(block.serialize());
            }
            return String.join(";", pieces);
        }

        private static DoorDefinition parse(String idString, String raw) {
            if (idString == null || raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split(";");
            if (pieces.length < 2) {
                return null;
            }

            try {
                UUID id = UUID.fromString(idString);
                DoorBounds bounds = DoorBounds.parse(pieces[0]);
                if (bounds == null) {
                    return null;
                }

                List<DoorBlockSnapshot> blocks = new ArrayList<>();
                for (int i = 1; i < pieces.length; i++) {
                    DoorBlockSnapshot snapshot = DoorBlockSnapshot.parse(pieces[i]);
                    if (snapshot != null) {
                        blocks.add(snapshot);
                    }
                }
                return blocks.isEmpty() ? null : new DoorDefinition(id, bounds, List.copyOf(blocks));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private static DoorDefinition parseLegacy(ConfigurationSection section, String key) {
            UUID id;
            UUID worldId;
            try {
                id = UUID.fromString(key);
                worldId = UUID.fromString(section.getString(key + ".world", ""));
            } catch (IllegalArgumentException ex) {
                return null;
            }

            DoorBounds bounds = new DoorBounds(
                    worldId,
                    section.getInt(key + ".minX"),
                    section.getInt(key + ".minY"),
                    section.getInt(key + ".minZ"),
                    section.getInt(key + ".maxX"),
                    section.getInt(key + ".maxY"),
                    section.getInt(key + ".maxZ"));
            List<DoorBlockSnapshot> snapshots = new ArrayList<>();
            for (String raw : section.getStringList(key + ".blocks")) {
                DoorBlockSnapshot snapshot = DoorBlockSnapshot.parse(raw);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
            return snapshots.isEmpty() ? null : new DoorDefinition(id, bounds, List.copyOf(snapshots));
        }
    }

    private record DoorBounds(UUID worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        private static DoorBounds from(BlockKey first, BlockKey second) {
            if (first == null || second == null || !first.worldId().equals(second.worldId())) {
                return null;
            }

            return new DoorBounds(
                    first.worldId(),
                    Math.min(first.x(), second.x()),
                    Math.min(first.y(), second.y()),
                    Math.min(first.z(), second.z()),
                    Math.max(first.x(), second.x()),
                    Math.max(first.y(), second.y()),
                    Math.max(first.z(), second.z()));
        }

        private int sizeX() {
            return maxX - minX + 1;
        }

        private int sizeY() {
            return maxY - minY + 1;
        }

        private int sizeZ() {
            return maxZ - minZ + 1;
        }

        private int minDimension() {
            return Math.min(sizeX(), Math.min(sizeY(), sizeZ()));
        }

        private int maxDimension() {
            return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
        }

        private boolean isValidDoor() {
            return minDimension() == 1 && maxDimension() <= DOOR_MAX_SIZE;
        }

        private String sizeLabel() {
            int smallest = minDimension();
            int largest = maxDimension();
            int middle = sizeX() + sizeY() + sizeZ() - smallest - largest;
            return middle + "x" + largest;
        }

        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
                return false;
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        private boolean isNear(Location location, int padding) {
            if (location == null || location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
                return false;
            }

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= minX - padding && x <= maxX + padding
                    && y >= minY - padding && y <= maxY + padding
                    && z >= minZ - padding && z <= maxZ + padding;
        }

        private boolean intersects(DoorBounds other) {
            if (other == null || !worldId.equals(other.worldId)) {
                return false;
            }

            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        private String serialize() {
            return worldId + "," + minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;
        }

        private static DoorBounds parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split(",");
            if (pieces.length != 7) {
                return null;
            }

            try {
                return new DoorBounds(
                        UUID.fromString(pieces[0]),
                        Integer.parseInt(pieces[1]),
                        Integer.parseInt(pieces[2]),
                        Integer.parseInt(pieces[3]),
                        Integer.parseInt(pieces[4]),
                        Integer.parseInt(pieces[5]),
                        Integer.parseInt(pieces[6]));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record DoorBlockSnapshot(int x, int y, int z, String blockData) {

        private String serialize() {
            return x + "," + y + "," + z + "|" + blockData;
        }

        private static DoorBlockSnapshot parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split("\\|", 2);
            if (pieces.length != 2) {
                return null;
            }

            String[] coords = pieces[0].split(",");
            if (coords.length != 3) {
                return null;
            }

            try {
                return new DoorBlockSnapshot(
                        Integer.parseInt(coords[0]),
                        Integer.parseInt(coords[1]),
                        Integer.parseInt(coords[2]),
                        pieces[1]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private record MenuTheme(Material base, Material accent, Material highlight) {
    }

    private record GadgetState(BrushType type, int level, String value, Material material) {

        private String serialize() {
            return type.id() + "|" + level + "|" + (material == null ? "" : material.name()) + "|" + (value == null ? "" : value);
        }

        private static GadgetState parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split("\\|", 4);
            if (pieces.length < 2) {
                return null;
            }

            BrushType type = BrushType.byId(pieces[0]);
            if (type == null) {
                return null;
            }

            try {
                int level = Integer.parseInt(pieces[1]);
                if (pieces.length == 3) {
                    String value = !pieces[2].isBlank() ? pieces[2] : null;
                    return new GadgetState(type, level, value, null);
                }

                Material material = null;
                if (pieces.length >= 3 && !pieces[2].isBlank()) {
                    try {
                        material = Material.valueOf(pieces[2]);
                    } catch (IllegalArgumentException ignored) {
                        material = null;
                    }
                }
                String value = pieces.length == 4 && !pieces[3].isBlank() ? pieces[3] : null;
                return new GadgetState(type, level, value, material);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private boolean matches(Block block) {
            if (block == null || block.getType().isAir()) {
                return false;
            }
            if (material == null) {
                return block.getType() != Material.WATER && block.getType() != Material.LAVA;
            }
            return block.getType() == material;
        }

        private GadgetState withMaterial(Material material) {
            return new GadgetState(type, level, value, material);
        }
    }

    private record BlockGadgetState(BlockGadgetType type, BlockFace facing, String courseId) {

        private String serialize() {
            return type.id() + "|" + facing.name() + "|" + (courseId == null ? "" : courseId);
        }

        private static BlockGadgetState parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split("\\|", 3);
            if (pieces.length < 2) {
                return null;
            }

            BlockGadgetType type = BlockGadgetType.byId(pieces[0]);
            if (type == null) {
                return null;
            }

            try {
                BlockFace facing = BlockFace.valueOf(pieces[1]);
                String course = pieces.length == 3 && !pieces[2].isBlank() ? pieces[2] : null;
                return new BlockGadgetState(type, facing, course);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record TouchedBlockGadget(BlockKey key, BlockGadgetState state, BlockFace contactFace) {
    }

    private record ParticleChunkKey(UUID worldId, int x, int z) {

        private static ParticleChunkKey from(Block block) {
            return new ParticleChunkKey(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ());
        }
    }

    private record PendingWarpBrush(BlockKey blockKey) {
    }

    private record PendingTimeBrush(BlockKey blockKey) {
    }

    private record GadgetAuditEntry(
            String typeLabel,
            String createdByName,
            long createdAtEpochSeconds,
            String removedByName,
            long removedAtEpochSeconds) {

        private static GadgetAuditEntry created(String typeLabel, Player player) {
            return new GadgetAuditEntry(
                    typeLabel,
                    player.getName(),
                    Instant.now().getEpochSecond(),
                    null,
                    0L);
        }

        private GadgetAuditEntry removed(Player player) {
            return new GadgetAuditEntry(typeLabel, createdByName, createdAtEpochSeconds,
                    player.getName(), Instant.now().getEpochSecond());
        }

        private String serialize() {
            StringBuilder builder = new StringBuilder();
            builder.append(typeLabel).append('|')
                    .append(createdByName).append('|')
                    .append(createdAtEpochSeconds);
            if (removedByName != null) {
                builder.append('|')
                        .append(removedByName).append('|')
                        .append(removedAtEpochSeconds);
            }
            return builder.toString();
        }

        private static GadgetAuditEntry parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] pieces = raw.split("\\|", 5);
            if (pieces.length < 3) {
                return null;
            }

            try {
                return new GadgetAuditEntry(
                        pieces[0],
                        pieces[1],
                        Long.parseLong(pieces[2]),
                        pieces.length >= 5 && !pieces[3].isBlank() ? pieces[3] : null,
                        pieces.length >= 5 ? Long.parseLong(pieces[4]) : 0L);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static GadgetAuditEntry loadLegacy(ConfigurationSection section, String key) {
            String type = section.getString(key + ".type");
            String createdByName = section.getString(key + ".createdByName");
            String createdAt = section.getString(key + ".createdAt");
            if (type == null || createdByName == null || createdAt == null) {
                return null;
            }

            return new GadgetAuditEntry(
                    type,
                    createdByName,
                    parseLegacyEpoch(createdAt),
                    section.getString(key + ".removedByName"),
                    parseLegacyEpoch(section.getString(key + ".removedAt")));
        }

        private static long parseLegacyEpoch(String raw) {
            if (raw == null || raw.isBlank()) {
                return 0L;
            }

            try {
                return Instant.parse(raw).getEpochSecond();
            } catch (Exception ignored) {
                return Instant.now().getEpochSecond();
            }
        }
    }

    private static final class ParkourSession {
        private final ItemStack[] savedHotbar;
        private final String courseId;
        private BlockKey startKey;
        private BlockKey checkpointKey;
        private boolean running;
        private long startedAtMillis;
        private long pausedElapsedMillis;

        private ParkourSession(ItemStack[] savedHotbar, String courseId, BlockKey startKey, BlockKey checkpointKey, boolean running, long startedAtMillis, long pausedElapsedMillis) {
            this.savedHotbar = savedHotbar;
            this.courseId = courseId;
            this.startKey = startKey;
            this.checkpointKey = checkpointKey;
            this.running = running;
            this.startedAtMillis = startedAtMillis;
            this.pausedElapsedMillis = pausedElapsedMillis;
        }

        private ItemStack[] savedHotbar() {
            return savedHotbar;
        }

        private String courseId() {
            return courseId;
        }

        private BlockKey startKey() {
            return startKey;
        }

        private void startKey(BlockKey startKey) {
            this.startKey = startKey;
        }

        private BlockKey checkpointKey() {
            return checkpointKey;
        }

        private void checkpointKey(BlockKey checkpointKey) {
            this.checkpointKey = checkpointKey;
        }

        private boolean running() {
            return running;
        }

        private void running(boolean running) {
            this.running = running;
        }

        private long startedAtMillis() {
            return startedAtMillis;
        }

        private void startedAtMillis(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }

        private long pausedElapsedMillis() {
            return pausedElapsedMillis;
        }

        private void pausedElapsedMillis(long pausedElapsedMillis) {
            this.pausedElapsedMillis = pausedElapsedMillis;
        }

        private long elapsedMillis() {
            if (!running) {
                return pausedElapsedMillis;
            }
            return pausedElapsedMillis + Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        }
    }

    private enum ParkourControl {
        START_STOP("start_stop"),
        CHECKPOINT("checkpoint"),
        RESET("reset"),
        EXIT("exit");

        private final String id;

        ParkourControl(String id) {
            this.id = id;
        }

        private static ParkourControl byId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }

            for (ParkourControl control : values()) {
                if (control.id.equalsIgnoreCase(id)) {
                    return control;
                }
            }
            return null;
        }

        private String id() {
            return id;
        }
    }

    private enum MenuView {
        MAIN,
        BRUSHES,
        BRUSH_DETAIL,
        BLOCK_GADGETS
    }

    private enum BlockGadgetMenuItem {
        BOUNCY_SLIME(
                10,
                Material.SLIME_BLOCK,
                Component.text("Bouncy Slime", TextColor.color(128, 255, 137)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Extra spring for walls, floors, and ceilings.", NamedTextColor.GRAY),
                        Component.text("Pushes players away from the side they touch.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.BOUNCY_SLIME),
        SUPER_RAIL(
                12,
                Material.POWERED_RAIL,
                Component.text("Super Rail", TextColor.color(255, 215, 92)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A charged rail for faster minecart rides.", NamedTextColor.GRAY),
                        Component.text("Boosts minecarts that roll across it.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.SUPER_RAIL),
        DASH_BLOCK(
                14,
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("Dash Block", TextColor.color(255, 102, 214)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Directional launch pad with glazed terracotta facing.", NamedTextColor.GRAY),
                        Component.text("Each touched side follows that face's arrow direction.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.DASH_BLOCK),
        GRAVITY_BLOCK(
                16,
                Material.HONEY_BLOCK,
                Component.text("Gravity Block", TextColor.color(255, 179, 69)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A sticky black-hole block with a short pull radius.", NamedTextColor.GRAY),
                        Component.text("Pulls nearby entities from up to four blocks away.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.GRAVITY_BLOCK),
        PARKOUR_START(
                29,
                Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                Component.text("Parkour Start Plate", TextColor.color(92, 209, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Begins a personal parkour run.", NamedTextColor.GRAY),
                        Component.text("Saves the player's hotbar and gives parkour controls.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.PARKOUR_START),
        PARKOUR_CHECKPOINT(
                31,
                Material.LODESTONE,
                Component.text("Parkour Checkpoint", TextColor.color(169, 136, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Marks the player's latest return point.", NamedTextColor.GRAY),
                        Component.text("The checkpoint button returns them here.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.PARKOUR_CHECKPOINT),
        PARKOUR_FINISH(
                33,
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Component.text("Parkour Finish Plate", TextColor.color(118, 255, 187)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Completes a personal parkour run.", NamedTextColor.GRAY),
                        Component.text("Restores the hotbar saved at the start.", NamedTextColor.YELLOW)
                ),
                BlockGadgetType.PARKOUR_FINISH);

        private final int slot;
        private final Material icon;
        private final Component displayName;
        private final List<Component> description;
        private final BlockGadgetType blockGadgetType;

        BlockGadgetMenuItem(int slot, Material icon, Component displayName, List<Component> description, BlockGadgetType blockGadgetType) {
            this.slot = slot;
            this.icon = icon;
            this.displayName = displayName;
            this.description = description;
            this.blockGadgetType = blockGadgetType;
        }

        private static BlockGadgetMenuItem bySlot(int slot) {
            for (BlockGadgetMenuItem item : values()) {
                if (item.slot == slot) {
                    return item;
                }
            }
            return null;
        }

        private int slot() {
            return slot;
        }

        private Material icon() {
            return icon;
        }

        private Component displayName() {
            return displayName;
        }

        private List<Component> description() {
            return description;
        }

        private BlockGadgetType blockGadgetType() {
            return blockGadgetType;
        }

        private String plainName() {
            return ChatUtil.plainText(displayName);
        }
    }

    private enum BlockGadgetType {
        BOUNCY_SLIME(
                "bouncy_slime",
                Material.SLIME_BLOCK,
                Component.text("Bouncy Slime", TextColor.color(128, 255, 137)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("An extra-springy slime gadget block.", NamedTextColor.GRAY),
                        Component.text("Stand on it to launch up.", NamedTextColor.YELLOW),
                        Component.text("Hit the side to rebound horizontally.", NamedTextColor.YELLOW),
                        Component.text("Touch the underside to get pushed down.", NamedTextColor.YELLOW)
                )),
        DASH_BLOCK(
                "dash_block",
                Material.MAGENTA_GLAZED_TERRACOTTA,
                Component.text("Dash Block", TextColor.color(255, 102, 214)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A directional magenta launch block.", NamedTextColor.GRAY),
                        Component.text("The side touched decides which arrow launches you.", NamedTextColor.YELLOW),
                        Component.text("Horizontal arrows also lift you a few blocks.", NamedTextColor.YELLOW)
                )),
        SUPER_RAIL(
                "super_rail",
                Material.POWERED_RAIL,
                Component.text("Super Rail", TextColor.color(255, 215, 92)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A powered rail tuned for fast minecarts.", NamedTextColor.GRAY),
                        Component.text("Place it like a normal powered rail.", NamedTextColor.YELLOW),
                        Component.text("Minecarts riding it accelerate harder.", NamedTextColor.YELLOW)
                )),
        GRAVITY_BLOCK(
                "gravity_block",
                Material.HONEY_BLOCK,
                Component.text("Gravity Block", TextColor.color(255, 179, 69)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("An enchanted honey block with a black-hole pull.", NamedTextColor.GRAY),
                        Component.text("Pulls entities within four blocks toward its center.", NamedTextColor.YELLOW),
                        Component.text("Flying players are ignored.", NamedTextColor.YELLOW)
                )),
        PARKOUR_START(
                "parkour_start",
                Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                Component.text("Parkour Start Plate", TextColor.color(92, 209, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Starts a personal parkour run.", NamedTextColor.GRAY),
                        Component.text("Saves the current hotbar before replacing it.", NamedTextColor.YELLOW),
                        Component.text("Step on it again to restart the run.", NamedTextColor.YELLOW)
                )),
        PARKOUR_CHECKPOINT(
                "parkour_checkpoint",
                Material.LODESTONE,
                Component.text("Parkour Checkpoint", TextColor.color(169, 136, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Stores the player's latest checkpoint.", NamedTextColor.GRAY),
                        Component.text("Only works while the player is in parkour mode.", NamedTextColor.YELLOW)
                )),
        PARKOUR_FINISH(
                "parkour_finish",
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Component.text("Parkour Finish Plate", TextColor.color(118, 255, 187)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Finishes the parkour run.", NamedTextColor.GRAY),
                        Component.text("Restores the player's saved hotbar.", NamedTextColor.YELLOW)
                ));

        private final String id;
        private final Material material;
        private final Component displayName;
        private final List<Component> itemLore;

        BlockGadgetType(String id, Material material, Component displayName, List<Component> itemLore) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.itemLore = itemLore;
        }

        private static BlockGadgetType byId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }

            for (BlockGadgetType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }

        private String id() {
            return id;
        }

        private Material material() {
            return material;
        }

        private Component displayName() {
            return displayName;
        }

        private List<Component> itemLore() {
            return itemLore;
        }

        private String plainName() {
            return ChatUtil.plainText(displayName);
        }

        private boolean parkourType() {
            return this == PARKOUR_START || this == PARKOUR_CHECKPOINT || this == PARKOUR_FINISH;
        }

        private boolean emitsParticles() {
            return this == BOUNCY_SLIME
                    || this == GRAVITY_BLOCK
                    || this == PARKOUR_START
                    || this == PARKOUR_CHECKPOINT
                    || this == PARKOUR_FINISH;
        }

        private String auditLabel(String courseId) {
            String label = plainName();
            if (parkourType()) {
                return label + " (course " + (courseId == null || courseId.isBlank() ? "default" : courseId) + ")";
            }
            return label;
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {

        private static BlockKey from(Location location) {
            if (location == null || location.getWorld() == null) {
                return null;
            }

            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static BlockKey parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String[] parts = raw.split(":");
            if (parts.length != 4) {
                return null;
            }

            try {
                return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private String serialize() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {

        private static ChunkKey from(BlockKey key) {
            return new ChunkKey(key.worldId(), key.x() >> 4, key.z() >> 4);
        }

        private boolean isLoaded() {
            World world = Bukkit.getWorld(worldId);
            return world != null && world.isChunkLoaded(chunkX, chunkZ);
        }
    }

    private enum BrushType {
        SPEED_SLOWNESS(
                "speed_slowness",
                19,
                Material.SUGAR,
                Component.text("Velocity Brush", TextColor.color(122, 255, 150)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Paints movement speed into a block.", NamedTextColor.GRAY),
                        Component.text("Positive levels grant Speed.", NamedTextColor.GREEN),
                        Component.text("Negative levels apply Slowness.", NamedTextColor.RED),
                        Component.empty(),
                        Component.text("Right-click to raise, left-click to lower.", NamedTextColor.YELLOW)
                )),
        JUMP_BOOST(
                "jump_boost",
                20,
                Material.RABBIT_FOOT,
                Component.text("Springstep Brush", TextColor.color(120, 230, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Turns blocks into clean jump-boost pads.", NamedTextColor.GRAY),
                        Component.text("Higher levels launch jumps higher.", NamedTextColor.AQUA),
                        Component.empty(),
                        Component.text("Right-click to raise, left-click to lower.", NamedTextColor.YELLOW)
                )),
        GLOWING(
                "glowing",
                21,
                Material.GLOW_INK_SAC,
                Component.text("Spotlight Brush", TextColor.color(255, 166, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Makes players glow when they trigger the block.", NamedTextColor.GRAY),
                        Component.text("Useful for reveals, tags, and minigame moments.", NamedTextColor.LIGHT_PURPLE),
                        Component.empty(),
                        Component.text("Right-click to enable, left-click to clear.", NamedTextColor.YELLOW)
                )),
        NIGHT_VISION(
                "night_vision",
                22,
                Material.ENDER_EYE,
                Component.text("Vision Brush", TextColor.color(255, 214, 92)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Controls what players can see.", NamedTextColor.GRAY),
                        Component.text("Positive gives Night Vision.", NamedTextColor.GOLD),
                        Component.text("-1 clears vision effects; -2 blinds.", NamedTextColor.RED),
                        Component.empty(),
                        Component.text("Right-click to raise, left-click to lower.", NamedTextColor.YELLOW)
                )),
        SLOW_FALLING(
                "slow_falling",
                23,
                Material.FEATHER,
                Component.text("Gravity Brush", TextColor.color(245, 245, 245)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Softens falls or lifts players upward.", NamedTextColor.GRAY),
                        Component.text("Positive grants Slow Falling.", NamedTextColor.WHITE),
                        Component.text("Negative grants Levitation.", NamedTextColor.AQUA),
                        Component.empty(),
                        Component.text("Right-click to raise, left-click to lower.", NamedTextColor.YELLOW)
                )),
        WARP(
                "warp",
                24,
                Material.ENDER_PEARL,
                Component.text("Warp Anchor Brush", TextColor.color(99, 255, 216)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Links a block to a warp.", NamedTextColor.GRAY),
                        Component.text("Right-click a block, then type the warp name in an anvil.", NamedTextColor.AQUA),
                        Component.empty(),
                        Component.text("Left-click a linked block to remove the warp anchor.", NamedTextColor.YELLOW)
                )),
        TIME(
                "time",
                25,
                Material.CLOCK,
                Component.text("Personal Time Brush", TextColor.color(255, 232, 122)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Links a block to a personal time command.", NamedTextColor.GRAY),
                        Component.text("Right-click a block, then type 12:00 or 12 in an anvil.", NamedTextColor.YELLOW),
                        Component.empty(),
                        Component.text("Left-click a linked block to remove the time anchor.", NamedTextColor.YELLOW)
                ));

        private final String id;
        private final int slot;
        private final Material menuIcon;
        private final Component displayName;
        private final List<Component> description;

        BrushType(String id, int slot, Material menuIcon, Component displayName, List<Component> description) {
            this.id = id;
            this.slot = slot;
            this.menuIcon = menuIcon;
            this.displayName = displayName;
            this.description = description;
        }

        private static BrushType bySlot(int slot) {
            for (BrushType type : values()) {
                if (type.slot == slot) {
                    return type;
                }
            }
            return null;
        }

        private static BrushType byId(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }

            id = legacyTimeId(id);
            for (BrushType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }

        private static String legacyTimeId(String id) {
            if (id == null) {
                return null;
            }
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "time_morning", "time_day", "time_sunset", "time_midnight" -> "time";
                default -> id;
            };
        }

        private String id() {
            return id;
        }

        private int slot() {
            return slot;
        }

        private Material menuIcon() {
            return menuIcon;
        }

        private Component displayName() {
            return displayName;
        }

        private List<Component> description() {
            return description;
        }

        private String brushLabel() {
            return ChatUtil.plainText(displayName).replace(" Brush", "");
        }

        private String auditLabel(GadgetState state) {
            if (this == WARP && state != null && state.value() != null) {
                return "Warp Anchor Brush (/warp " + state.value() + ")";
            }
            if (this == TIME && state != null && state.value() != null) {
                return "Personal Time Brush (/ptime " + state.value() + ")";
            }
            String label = ChatUtil.plainText(displayName);
            return label.isBlank() ? id : label;
        }

        private String labelForLevel(int level) {
            return switch (this) {
                case SPEED_SLOWNESS -> level > 0 ? "Speed " + level : level < 0 ? "Slowness " + Math.abs(level) : "Neutral";
                case JUMP_BOOST -> level > 0 ? "Jump Boost " + level : "Neutral";
                case GLOWING -> level > 0 ? "Glowing" : "Neutral";
                case NIGHT_VISION -> {
                    if (level > 0) {
                        yield "Night Vision";
                    }
                    if (level == -1) {
                        yield "Remove Night Vision";
                    }
                    if (level <= -2) {
                        yield "Blindness";
                    }
                    yield "Neutral";
                }
                case SLOW_FALLING -> level > 0 ? "Slow Falling " + level : level < 0 ? "Levitation " + Math.abs(level) : "Neutral";
                case WARP -> "Warp Anchor";
                case TIME -> "Personal Time";
            };
        }

        private int nextLevel(int current, boolean increase) {
            return switch (this) {
                case SPEED_SLOWNESS, SLOW_FALLING -> increase ? current + 1 : current - 1;
                case JUMP_BOOST -> increase ? current + 1 : Math.max(0, current - 1);
                case GLOWING -> increase ? Math.min(1, current + 1) : Math.max(0, current - 1);
                case NIGHT_VISION -> increase ? 1 : current - 1;
                case WARP, TIME -> 1;
            };
        }

        private String defaultValue() {
            return null;
        }

        private boolean singleTrigger() {
            return this == WARP || this == TIME;
        }

        private void apply(Player player, int level) {
            switch (this) {
                case SPEED_SLOWNESS -> applySpeed(player, level);
                case JUMP_BOOST -> applyJump(player, level);
                case GLOWING -> applyGlow(player, level);
                case NIGHT_VISION -> applyNightVision(player, level);
                case SLOW_FALLING -> applySlowFalling(player, level);
                case WARP, TIME -> {
                }
            }
        }

        private void applySpeed(Player player, int level) {
            if (level > 0) {
                removeEffect(player, PotionEffectType.SLOWNESS);
                addEffect(player, PotionEffectType.SPEED, level - 1);
                return;
            }

            if (level < 0) {
                removeEffect(player, PotionEffectType.SPEED);
                addEffect(player, PotionEffectType.SLOWNESS, Math.abs(level) - 1);
            }
        }

        private void applyJump(Player player, int level) {
            if (level > 0) {
                addEffect(player, PotionEffectType.JUMP_BOOST, level - 1);
            }
        }

        private void applyGlow(Player player, int level) {
            if (level > 0) {
                addEffect(player, PotionEffectType.GLOWING, 0);
            }
        }

        private void applyNightVision(Player player, int level) {
            if (level > 0) {
                removeEffect(player, PotionEffectType.BLINDNESS);
                addEffect(player, PotionEffectType.NIGHT_VISION, 0);
                return;
            }

            if (level == -1) {
                removeEffect(player, PotionEffectType.NIGHT_VISION, PotionEffectType.BLINDNESS);
                return;
            }

            if (level <= -2) {
                removeEffect(player, PotionEffectType.NIGHT_VISION);
                addEffect(player, PotionEffectType.BLINDNESS, 0);
            }
        }

        private void applySlowFalling(Player player, int level) {
            if (level > 0) {
                removeEffect(player, PotionEffectType.LEVITATION);
                addEffect(player, PotionEffectType.SLOW_FALLING, level - 1);
                return;
            }

            if (level < 0) {
                removeEffect(player, PotionEffectType.SLOW_FALLING);
                addEffect(player, PotionEffectType.LEVITATION, Math.abs(level) - 1);
            }
        }
    }

    private static final class MenuHolder implements InventoryHolder {

        private final MenuView view;
        private final BrushType brushType;
        private Inventory inventory;

        private MenuHolder(MenuView view, BrushType brushType) {
            this.view = view;
            this.brushType = brushType;
        }

        private MenuView view() {
            return view;
        }

        private BrushType brushType() {
            return brushType;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
