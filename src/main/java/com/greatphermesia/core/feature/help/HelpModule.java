package com.greatphermesia.core.feature.help;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class HelpModule implements PluginModule, Listener, CommandExecutor {

    private final PhermesiaCorePlugin plugin;

    public HelpModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Help";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("help");
        bindCommand("info");
        plugin.getLogger().info("[Help] Module enabled.");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("[Help] Module stopped.");
    }

    private void bindCommand(String commandName) {
        if (plugin.getCommand(commandName) == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + commandName);
            return;
        }
        plugin.getCommand(commandName).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        openMain(player);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHelpPreprocess(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!raw.equals("/help") && !raw.equals("/?") && !raw.equals("/info")) {
            return;
        }

        event.setCancelled(true);
        openMain(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HelpHolder(HelpPage page))) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        int slot = event.getRawSlot();
        int inventorySize = event.getInventory().getSize();
        if (slot < 0 || slot >= inventorySize) {
            return;
        }

        if (slot == closeSlot(inventorySize)) {
            player.closeInventory();
            return;
        }
        if (page != HelpPage.MAIN && slot == backSlot(inventorySize)) {
            openMain(player);
            return;
        }

        switch (page) {
            case MAIN -> handleMainClick(player, slot);
            case RANKS -> handleRanksClick(player, slot);
            case TOOLS -> handleToolsClick(player, slot);
            case SOCIAL -> handleSocialClick(player, slot);
            case TRAVEL -> handleTravelClick(player, slot);
            case GADGETS -> handleGadgetsClick(player, slot);
        }
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openRanks(player);
            case 13 -> openSocial(player);
            case 15 -> openTravel(player);
            case 21 -> openTools(player);
            case 23 -> openGadgets(player);
            default -> playClick(player, 0.75f);
        }
    }

    private void handleRanksClick(Player player, int slot) {
        if (slot == 31) {
            player.closeInventory();
            player.sendMessage(ChatUtil.color("&eOpen a ticket to request Builder, Architect, or Engineer."));
            playClick(player, 1.2f);
        }
    }

    private void handleToolsClick(Player player, int slot) {
        switch (slot) {
            case 11 -> runCommand(player, "freeze");
            case 12 -> runCommand(player, "unfreeze");
            case 13 -> runCommand(player, "tmenu");
            case 14 -> runCommand(player, "buffs");
            default -> playClick(player, 0.75f);
        }
    }

    private void handleSocialClick(Player player, int slot) {
        switch (slot) {
            case 11 -> runCommand(player, "gc create");
            case 12 -> runCommand(player, "gc join");
            case 13 -> runCommand(player, "gc");
            case 14 -> runCommand(player, "gc info");
            case 15 -> runCommand(player, "gc leave");
            case 20 -> runCommand(player, "religion");
            case 21 -> runCommand(player, "cosmetics");
            case 22 -> runCommand(player, "playtop");
            default -> playClick(player, 0.75f);
        }
    }

    private void handleTravelClick(Player player, int slot) {
        switch (slot) {
            case 11 -> runCommand(player, "rtp");
            case 12 -> runCommand(player, "rtprandom");
            case 13 -> runCommand(player, "rtpwarp");
            case 14 -> runCommand(player, "requestwarp");
            case 15 -> runCommand(player, "map");
            case 21 -> runCommand(player, "tpblock");
            case 22 -> runCommand(player, "tpblocklist");
            default -> playClick(player, 0.75f);
        }
    }

    private void handleGadgetsClick(Player player, int slot) {
        switch (slot) {
            case 11 -> runCommand(player, "gadgets");
            case 12 -> runCommand(player, "brush i");
            case 13 -> runCommand(player, "brush remover");
            case 14 -> runCommand(player, "door wand");
            case 15 -> runCommand(player, "brush course");
            default -> playClick(player, 0.75f);
        }
    }

    private void openMain(Player player) {
        Inventory inventory = createInventory(player, HelpPage.MAIN, 36, Component.text("Information", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.NETHER_STAR,
                Component.text("Information", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("A clean guide to the server's main systems.", NamedTextColor.GRAY),
                        Component.text("Pick a category, then click a command.", NamedTextColor.YELLOW)
                ),
                true));
        inventory.setItem(11, item(Material.AMETHYST_CLUSTER, Component.text("Ranks", TextColor.color(199, 161, 255)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Builder, Architect, and Engineer.", NamedTextColor.GRAY), Component.text("Click to read rank requirements.", NamedTextColor.YELLOW)), true));
        inventory.setItem(13, item(Material.WRITABLE_BOOK, Component.text("Community", TextColor.color(255, 220, 112)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Group chats, religions, cosmetics, and playtime.", NamedTextColor.GRAY), Component.text("Click to open community features.", NamedTextColor.YELLOW)), true));
        inventory.setItem(15, item(Material.ENDER_PEARL, Component.text("Travel", TextColor.color(99, 255, 216)).decorate(TextDecoration.BOLD),
                List.of(Component.text("RTP, random coordinates, random warps, and map.", NamedTextColor.GRAY), Component.text("Click to open travel shortcuts.", NamedTextColor.YELLOW)), true));
        inventory.setItem(21, item(Material.COMMAND_BLOCK, Component.text("Server Tools", TextColor.color(135, 255, 181)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Freeze, buffs, and tmenu.", NamedTextColor.GRAY), Component.text("Click to open tool shortcuts.", NamedTextColor.YELLOW)), true));
        inventory.setItem(23, item(Material.BRUSH, Component.text("Builder Gadgets", TextColor.color(255, 128, 218)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Brushes, doors, block gadgets, and parkour.", NamedTextColor.GRAY), Component.text("Click to open gadget features.", NamedTextColor.YELLOW)), true));
        addClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private void openSocial(Player player) {
        Inventory inventory = createInventory(player, HelpPage.SOCIAL, 36, Component.text("Information: Community", TextColor.color(255, 220, 112)).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.WRITABLE_BOOK, Component.text("Community Features", TextColor.color(255, 220, 112)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Social systems and profile-style menus.", NamedTextColor.GRAY)), true));
        inventory.setItem(11, shortcut(Material.LIME_DYE, "Create Group Chat", "/gc create", "Create a private group chat code."));
        inventory.setItem(12, shortcut(Material.NAME_TAG, "Join Group Chat", "/gc join", "Join a group chat using a code."));
        inventory.setItem(13, shortcut(Material.OAK_SIGN, "Toggle Group Chat", "/gc", "Send chat to your group or global chat."));
        inventory.setItem(14, shortcut(Material.PAPER, "Group Info", "/gc info", "Show your current group chat."));
        inventory.setItem(15, shortcut(Material.RED_DYE, "Leave Group Chat", "/gc leave", "Leave your current group chat."));
        inventory.setItem(20, shortcut(Material.BEACON, "Religions", "/religion", "Create, join, pray, and manage religions."));
        inventory.setItem(21, shortcut(Material.GLOW_INK_SAC, "Cosmetics", "/cosmetics", "Open trails, glows, and chat colors."));
        inventory.setItem(22, shortcut(Material.CLOCK, "Playtime", "/playtop", "View the playtime leaderboard."));
        addBackAndClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private void openTravel(Player player) {
        Inventory inventory = createInventory(player, HelpPage.TRAVEL, 36, Component.text("Information: Travel", TextColor.color(99, 255, 216)).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.ENDER_PEARL, Component.text("Travel Features", TextColor.color(99, 255, 216)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Teleport tools and map shortcuts.", NamedTextColor.GRAY)), true));
        inventory.setItem(11, shortcut(Material.ENDER_PEARL, "RTP Menu", "/rtp", "Open the teleport menu."));
        inventory.setItem(12, shortcut(Material.GRASS_BLOCK, "Random Coordinates", "/rtprandom", "Teleport to safe random terrain."));
        inventory.setItem(13, shortcut(Material.ENDER_EYE, "Random Warp", "/rtpwarp", "Teleport to a random configured warp."));
        inventory.setItem(14, shortcut(Material.NAME_TAG, "Request Warp", "/requestwarp <name>", "Ask staff to turn your current location into a warp."));
        inventory.setItem(15, shortcut(Material.FILLED_MAP, "Server Map", "/map", "Open the map link."));
        inventory.setItem(21, shortcut(Material.REDSTONE, "TP Block Player", "/tpblock", "Toggle teleport blocking for a player."));
        inventory.setItem(22, shortcut(Material.BOOK, "TP Block List", "/tpblocklist", "View your teleport block list."));
        addBackAndClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private void openGadgets(Player player) {
        Inventory inventory = createInventory(player, HelpPage.GADGETS, 45, Component.text("Information: Gadgets", TextColor.color(255, 128, 218)).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.BRUSH, Component.text("Gadget Features", TextColor.color(255, 128, 218)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Brushes, doors, block gadgets, parkour, and no-update building.", NamedTextColor.GRAY),
                        Component.text("Only Builders can use gadget commands.", NamedTextColor.RED)
                ), true));
        inventory.setItem(11, shortcut(Material.BRUSH, "Gadget Menu", "/gadgets", "Open the full gadget workshop.", true));
        inventory.setItem(12, shortcut(Material.SPYGLASS, "Brush Inspect", "/brush i", "Inspect brush and door logs.", true));
        inventory.setItem(13, shortcut(Material.GOLDEN_AXE, "Gadget Remover", "/brush remover", "Get the remover tool.", true));
        inventory.setItem(14, shortcut(Material.IRON_DOOR, "Hidden Doors", "/door wand", "Create hidden doors up to 5 blocks.", true));
        inventory.setItem(15, shortcut(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "Parkour Courses", "/brush course", "Set course IDs for parkour start/checkpoint/finish blocks.", true));
        inventory.setItem(20, item(Material.SLIME_BLOCK, Component.text("Bouncy Slime", TextColor.color(128, 255, 137)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Launches players, mobs, and boats from every side.", NamedTextColor.GRAY)), true));
        inventory.setItem(21, item(Material.MAGENTA_GLAZED_TERRACOTTA, Component.text("Dash Block", TextColor.color(255, 102, 214)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Uses glazed terracotta arrows to dash entities.", NamedTextColor.GRAY)), true));
        inventory.setItem(22, item(Material.POWERED_RAIL, Component.text("Super Rail", TextColor.color(255, 215, 92)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Boosts minecarts after they start moving.", NamedTextColor.GRAY)), true));
        inventory.setItem(23, item(Material.HONEY_BLOCK, Component.text("Gravity Block", TextColor.color(255, 179, 69)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Pulls entities smoothly like a black hole.", NamedTextColor.GRAY)), true));
        inventory.setItem(24, item(Material.CLOCK, Component.text("Time And Warp Brushes", TextColor.color(255, 232, 122)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Anvil-driven /ptime and warp anchor brushes.", NamedTextColor.GRAY)), true));
        addBackAndClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private void openRanks(Player player) {
        Inventory inventory = createInventory(player, HelpPage.RANKS, 45, Component.text("Information: Ranks", TextColor.color(199, 161, 255)).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.WRITABLE_BOOK, Component.text("Rank Requests", TextColor.color(199, 161, 255)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Ranks are acquired by requesting them via open-a-ticket.", NamedTextColor.GRAY),
                        Component.text("Higher privileges expect higher play time.", NamedTextColor.YELLOW),
                        Component.text("Ranks may be revoked for rule violations.", NamedTextColor.RED)
                ), true));
        inventory.setItem(20, item(Material.WOODEN_AXE, Component.text("Builder", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Requires basic trust, maturity, and WorldEdit understanding.", NamedTextColor.GRAY),
                        Component.text("Grants WorldEdit, VoxelSniper, and Axiom outside Editor mode.", NamedTextColor.YELLOW)
                ), true));
        inventory.setItem(22, item(Material.ARCHER_POTTERY_SHERD, Component.text("Architect", TextColor.color(255, 186, 249)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Requires Builder plus demonstrated tool competency.", NamedTextColor.GRAY),
                        Component.text("Requires a history of responsible permission use.", NamedTextColor.GRAY),
                        Component.text("Grants Axiom Editor mode.", NamedTextColor.YELLOW)
                ), true));
        inventory.setItem(24, item(Material.COMMAND_BLOCK, Component.text("Engineer", TextColor.color(46, 115, 69)).decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Permanent Engineer requires longer trust and activity.", NamedTextColor.GRAY),
                        Component.text("Ad-hoc access may be granted for specific projects.", NamedTextColor.GRAY),
                        Component.text("Grants command blocks and other powerful tools.", NamedTextColor.YELLOW)
                ), true));
        inventory.setItem(31, item(Material.NAME_TAG, Component.text("Request A Rank", TextColor.color(154, 255, 190)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Use open-a-ticket to request access.", NamedTextColor.GRAY)), true));
        addBackAndClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private void openTools(Player player) {
        Inventory inventory = createInventory(player, HelpPage.TOOLS, 36, Component.text("Information: Tools", TextColor.color(135, 255, 181)).decorate(TextDecoration.BOLD));
        inventory.setItem(4, item(Material.COMMAND_BLOCK, Component.text("Server Tools", TextColor.color(135, 255, 181)).decorate(TextDecoration.BOLD),
                List.of(Component.text("Public quality-of-life menus and commands.", NamedTextColor.GRAY)), true));
        inventory.setItem(11, shortcut(Material.SAND, "Freeze", "/freeze", "Place sand, liquids, and redstone without updates."));
        inventory.setItem(12, shortcut(Material.REDSTONE_TORCH, "Unfreeze", "/unfreeze", "Return your block updates to normal."));
        inventory.setItem(13, shortcut(Material.RECOVERY_COMPASS, "Tool Menu", "/tmenu", "Invisible item frames, barriers, and more."));
        inventory.setItem(14, shortcut(Material.POTION, "Buffs", "/buffs", "Open the potion effects menu."));
        addBackAndClose(inventory);
        player.openInventory(inventory);
        playOpen(player);
    }

    private ItemStack shortcut(Material icon, String name, String command, String description) {
        return shortcut(icon, name, command, description, false);
    }

    private ItemStack shortcut(Material icon, String name, String command, String description, boolean builderOnly) {
        List<Component> lore = builderOnly
                ? List.of(
                        Component.text(description, NamedTextColor.GRAY),
                        Component.text("Only Builders and above can use this.", NamedTextColor.RED),
                        Component.text("Click to run " + command + ".", NamedTextColor.YELLOW)
                )
                : List.of(
                        Component.text(description, NamedTextColor.GRAY),
                        Component.text("Click to run " + command + ".", NamedTextColor.YELLOW)
                );
        return item(icon, Component.text(name, TextColor.color(226, 255, 236)).decorate(TextDecoration.BOLD),
                lore, true);
    }

    private Inventory createInventory(Player player, HelpPage page, int size, Component title) {
        Inventory inventory = Bukkit.createInventory(new HelpHolder(page), size, title);
        if (isBedrockPlayer(player.getUniqueId())) {
            return inventory;
        }
        Material borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
        Material accentMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        ItemStack border = item(borderMaterial, Component.empty(), List.of(), false);
        ItemStack filler = item(fillerMaterial, Component.empty(), List.of(), false);
        ItemStack accent = item(accentMaterial, Component.empty(), List.of(), false);

        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            int lastRow = (size / 9) - 1;
            if (row == 0 || row == lastRow || column == 0 || column == 8) {
                inventory.setItem(slot, border.clone());
            }
        }

        int[] accents = {1, 7, size - 8, size - 2};
        for (int slot : accents) {
            if (slot >= 0 && slot < size && inventory.getItem(slot) != null) {
                inventory.setItem(slot, accent.clone());
            }
        }
        return inventory;
    }

    private void addBackAndClose(Inventory inventory) {
        inventory.setItem(backSlot(inventory.getSize()), item(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text("Return to the help menu.", NamedTextColor.GRAY)), false));
        addClose(inventory);
    }

    private void addClose(Inventory inventory) {
        inventory.setItem(closeSlot(inventory.getSize()), item(Material.BARRIER, Component.text("Close", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                List.of(Component.text("Close this menu.", NamedTextColor.GRAY)), false));
    }

    private int backSlot(int inventorySize) {
        return inventorySize - 9;
    }

    private int closeSlot(int inventorySize) {
        return inventorySize - 1;
    }

    private ItemStack item(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void runCommand(Player player, String command) {
        player.closeInventory();
        String commandLine = command.startsWith("/") ? command.substring(1) : command;
        player.performCommand(commandLine);
        playClick(player, 1.2f);
    }

    private void playOpen(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.15f);
    }

    private void playClick(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, pitch);
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

    private enum HelpPage {
        MAIN,
        RANKS,
        TOOLS,
        SOCIAL,
        TRAVEL,
        GADGETS
    }

    private record HelpHolder(HelpPage page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

}
