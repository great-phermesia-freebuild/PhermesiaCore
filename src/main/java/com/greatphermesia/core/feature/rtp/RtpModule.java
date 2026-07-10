package com.greatphermesia.core.feature.rtp;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class RtpModule implements PluginModule, Listener, CommandExecutor {

    private static final List<String> DEFAULT_WARP_OF_THE_DAY_WARPS = List.of(
            "Visby",
            "Arkavia",
            "Blue",
            "Cookieville",
            "Luminara",
            "Kazooville",
            "Egalia",
            "Orthodoksia",
            "Beeville",
            "Midnight_Hills",
            "Vortex_Heights",
            "Spruce_Gardens_South",
            "Rivermount",
            "Northernlight",
            "Pineapple_Bay",
            "Omoshiro",
            "Ostfjord",
            "Melonia",
            "Seafoam_Isles",
            "Yellingmare",
            "Falkenhayn",
            "Koyo",
            "PortUnion",
            "Zelkland",
            "Rondeland",
            "Valeronne",
            "Stellamaris",
            "Concord",
            "New_Worcester",
            "Wexfordale",
            "Aquor",
            "Vatnaland",
            "Amberia",
            "Administra",
            "traffic_signs",
            "aloquin"
    );
    private static final ZoneId DEFAULT_WARP_OF_THE_DAY_ZONE = ZoneId.of("Europe/Brussels");
    private static final LocalTime WARP_OF_THE_DAY_CHANGE_TIME = LocalTime.of(11, 0);
    private static final DateTimeFormatter NEXT_CHANGE_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final PhermesiaCorePlugin plugin;
    private final Random random = new Random();

    public RtpModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Rtp";
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("rtp");
        bindCommand("rtprandom");
        bindCommand("rtpwarp");
        plugin.getLogger().info("[Rtp] Module enabled.");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("[Rtp] Module stopped.");
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

        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "rtp" -> {
                openRtpGui(player);
                yield true;
            }
            case "rtprandom" -> {
                randomTeleport(player);
                yield true;
            }
            case "rtpwarp" -> {
                randomWarp(player);
                yield true;
            }
            default -> false;
        };
    }

    @EventHandler
    public void onRtpGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().title().equals(guiTitle())) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        if (slot == 11 && item.getType() == Material.GRASS_BLOCK) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> randomTeleport(player), 1L);
            return;
        }

        if (slot == 13 && item.getType() == Material.CLOCK) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> warpOfTheDay(player), 1L);
            return;
        }

        if (slot == 15 && item.getType() == Material.LODESTONE) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> randomWarp(player), 1L);
        }
    }

    private void openRtpGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, guiTitle());

        if (!isFloodgatePlayer(player.getUniqueId())) {
            for (int i = 0; i < 27; i++) {
                gui.setItem(i, namedItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
            int[] cyanSlots = {0, 1, 7, 8, 18, 19, 25, 26};
            for (int cyanSlot : cyanSlots) {
                gui.setItem(cyanSlot, namedItem(Material.CYAN_STAINED_GLASS_PANE, " ", List.of()));
            }
        }
        gui.setItem(4, namedItem(Material.NETHER_STAR, "&e&lTeleportation", List.of("&7Choose your destination")));

        gui.setItem(11, namedItem(Material.GRASS_BLOCK, "&a&lRandom Teleport", List.of(
                "&7",
                "&6Teleport to a random location",
                "&6and start your adventure!",
                "&7",
                "&8&l> &eClick to teleport"
        )));
        String warpOfTheDay = getWarpOfTheDay();
        String nextChange = getNextWarpOfTheDayChangeText();
        gui.setItem(13, namedItem(Material.CLOCK, "&d&lWarp of the Day", List.of(
                "&7",
                "&5Today's warp: &d" + warpOfTheDay,
                "&5Changes at &d" + nextChange,
                "&7",
                "&8&l> &eClick to teleport"
        )));
        gui.setItem(15, namedItem(Material.LODESTONE, "&b&lRandom Warp", List.of(
                "&7",
                "&2Get teleported to a random",
                "&2warp on the server!",
                "&7",
                "&8&l> &eClick to teleport"
        )));

        player.openInventory(gui);
    }

    private void randomTeleport(Player player) {
        player.sendMessage(ChatUtil.color("&aSearching for a safe location..."));

        World world = player.getWorld();
        int min = Math.max(1, plugin.getConfig().getInt("rtp.min-radius", 2000));
        int max = Math.max(min, plugin.getConfig().getInt("rtp.max-radius", 10000));
        int maxAttempts = Math.max(1, plugin.getConfig().getInt("rtp.max-attempts", 24));

        for (int i = 0; i < maxAttempts; i++) {
            int x = randomSigned(min, max);
            int z = randomSigned(min, max);
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);

            if (!isSafeLocation(location)) {
                continue;
            }

            player.teleportAsync(location).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> player.sendMessage(ChatUtil.color("&cFailed to teleport. Please try again.")));
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatUtil.color("&aYou have been teleported to a random location!"));
                    player.sendMessage(ChatUtil.color("&7Coordinates: &e" + x + ", " + location.getBlockY() + ", " + z));
                    world.spawnParticle(Particle.PORTAL, location, 36, 0.4, 0.8, 0.4, 0.02);
                    play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.05f);
                });
            });
            return;
        }

        player.sendMessage(ChatUtil.color("&cNo safe location found after " + maxAttempts + " attempts. Try again."));
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
    }

    private void randomWarp(Player player) {
        List<String> warps = getEssentialsWarpNames();
        if (warps.isEmpty()) {
            player.sendMessage(ChatUtil.color("&cNo warps available on the server!"));
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        String warp = warps.get(random.nextInt(warps.size()));
        player.sendMessage(ChatUtil.color("&aTeleporting you to a random warp..."));
        Bukkit.dispatchCommand(player, "warp " + warp);
    }

    private void warpOfTheDay(Player player) {
        String warp = getWarpOfTheDay();
        String matchedWarp = findEssentialsWarp(warp);
        if (matchedWarp == null) {
            player.sendMessage(ChatUtil.color("&cToday's warp, &f" + warp + "&c, is not available right now."));
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.8f);
            return;
        }

        player.sendMessage(ChatUtil.color("&dWarp of the Day: &f" + matchedWarp));
        Bukkit.dispatchCommand(player, "warp " + matchedWarp);
    }

    private String getWarpOfTheDay() {
        List<String> warps = getWarpOfTheDayWarps();
        long dayIndex = getWarpOfTheDayPeriod();
        return warps.get(Math.floorMod(dayIndex, warps.size()));
    }

    private List<String> getWarpOfTheDayWarps() {
        List<String> configured = plugin.getConfig().getStringList("rtp.warp-of-the-day.warps");
        if (configured.isEmpty()) {
            return DEFAULT_WARP_OF_THE_DAY_WARPS;
        }

        List<String> cleaned = new ArrayList<>();
        for (String warp : configured) {
            if (warp != null && !warp.isBlank()) {
                cleaned.add(warp.trim());
            }
        }
        return cleaned.isEmpty() ? DEFAULT_WARP_OF_THE_DAY_WARPS : cleaned;
    }

    private long getWarpOfTheDayPeriod() {
        ZoneId zone = getWarpOfTheDayZone();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate activeDate = now.toLocalTime().isBefore(WARP_OF_THE_DAY_CHANGE_TIME)
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
        return activeDate.toEpochDay();
    }

    private String getNextWarpOfTheDayChangeText() {
        ZoneId zone = getWarpOfTheDayZone();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime nextChange = now.toLocalTime().isBefore(WARP_OF_THE_DAY_CHANGE_TIME)
                ? LocalDateTime.of(now.toLocalDate(), WARP_OF_THE_DAY_CHANGE_TIME)
                : LocalDateTime.of(now.toLocalDate().plusDays(1), WARP_OF_THE_DAY_CHANGE_TIME);
        return nextChange.format(NEXT_CHANGE_FORMAT);
    }

    private ZoneId getWarpOfTheDayZone() {
        String configured = plugin.getConfig().getString("rtp.warp-of-the-day.timezone", DEFAULT_WARP_OF_THE_DAY_ZONE.getId());
        try {
            return ZoneId.of(configured);
        } catch (Exception ex) {
            plugin.getLogger().warning("Invalid rtp.warp-of-the-day.timezone '" + configured + "', using Europe/Brussels.");
            return DEFAULT_WARP_OF_THE_DAY_ZONE;
        }
    }

    private String findEssentialsWarp(String requestedName) {
        for (String warpName : getEssentialsWarpNames()) {
            if (warpName.equalsIgnoreCase(requestedName)) {
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

    private int randomSigned(int min, int max) {
        int value = min + random.nextInt(max - min + 1);
        return random.nextBoolean() ? value : -value;
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(location)) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = location.clone().add(0.0, 1.0, 0.0).getBlock();
        Block below = location.clone().subtract(0.0, 1.0, 0.0).getBlock();
        return below.getType().isSolid()
                && !below.isLiquid()
                && feet.isPassable()
                && head.isPassable();
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

    private Component guiTitle() {
        return ChatUtil.component("&b&lTeleportation Menu");
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

    private void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
