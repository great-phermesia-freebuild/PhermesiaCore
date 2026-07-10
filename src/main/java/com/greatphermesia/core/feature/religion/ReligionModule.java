package com.greatphermesia.core.feature.religion;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import com.greatphermesia.core.util.DurationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ReligionModule implements PluginModule, Listener, CommandExecutor {

    private final PhermesiaCorePlugin plugin;
    private final Set<String> religions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> playerReligion = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRank = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPrayers = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> playerLastPrayer = new ConcurrentHashMap<>();

    private final Map<String, Material> religionIcon = new ConcurrentHashMap<>();
    private final Map<String, UUID> religionFounder = new ConcurrentHashMap<>();
    private final Map<String, Integer> religionPrayers = new ConcurrentHashMap<>();

    private final Set<UUID> awaitingReligionName = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingReligionName = new ConcurrentHashMap<>();

    private final Set<Integer> milestoneTotals = Set.of(10, 25, 50, 100, 250, 500, 1000);
    private File dataFile;

    public ReligionModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Religion";
    }

    @Override
    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "religion.yml");
        loadData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("religion");
        bindCommand("staffreligion");

        plugin.getLogger().info("[Religion] Module enabled.");
    }

    @Override
    public void disable() {
        saveData();
        plugin.getLogger().info("[Religion] Module stopped.");
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
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("religion")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
                return true;
            }
            openMainGui(player);
            return true;
        }

        if (commandName.equals("staffreligion")) {
            handleStaffReligionCommand(sender, args);
            return true;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onReligionNameInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!awaitingReligionName.remove(uuid)) {
            return;
        }

        event.setCancelled(true);
        String requestedName = ChatUtil.plainText(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleReligionNameInput(player, requestedName));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Component title = event.getView().title();
        if (!isManagedTitle(title)) {
            return;
        }

        event.setCancelled(true);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        int slot = event.getRawSlot();

        if (title.equals(mainTitle())) {
            handleMainGuiClick(player, slot);
            return;
        }

        if (title.equals(convertTitle())) {
            handleConvertGuiClick(player, clicked);
            return;
        }

        if (title.equals(allTitle())) {
            if (isBack(clicked)) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGui(player), 1L);
            }
            return;
        }

        if (title.equals(iconTitle())) {
            handleIconGuiClick(player, clicked);
        }
    }

    private void handleMainGuiClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        switch (slot) {
            case 20 -> {
                if (!playerReligion.containsKey(uuid)) {
                    player.sendMessage(ChatUtil.color("&cYou must be part of a religion to pray!"));
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    return;
                }

                Duration cooldown = Duration.ofHours(plugin.getConfig().getLong("religion.daily-prayer-cooldown-hours", 24L));
                Instant now = Instant.now();
                Instant lastPrayer = playerLastPrayer.get(uuid);
                if (lastPrayer != null) {
                    Duration elapsed = Duration.between(lastPrayer, now);
                    if (elapsed.compareTo(cooldown) < 0) {
                        Duration left = cooldown.minus(elapsed);
                        player.sendMessage(ChatUtil.color("&cYou have already prayed today! Come back in &e"
                                + DurationUtil.hoursMinutes(left) + "&c."));
                        playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                        return;
                    }
                }

                String religion = playerReligion.get(uuid);
                int personal = playerPrayers.getOrDefault(uuid, 0) + 1;
                int total = religionPrayers.getOrDefault(religion, 0) + 1;

                playerPrayers.put(uuid, personal);
                religionPrayers.put(religion, total);
                playerLastPrayer.put(uuid, now);

                player.sendMessage(ChatUtil.color("&6You have prayed to the god(s) of &e" + religion + "&6!"));
                player.sendMessage(ChatUtil.color("&7Personal prayers: &e" + personal));

                if (milestoneTotals.contains(total)) {
                    Bukkit.broadcast(ChatUtil.component("&e" + religion + " &6has reached &e" + total + " &6total prayers"));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        playSound(online, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                    }
                }

                playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
                player.closeInventory();
                saveData();
            }
            case 22 -> {
                if (religions.isEmpty()) {
                    player.sendMessage(ChatUtil.color("&cNo religions exist yet!"));
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    player.closeInventory();
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openReligionsList(player, true), 1L);
            }
            case 24 -> {
                if (playerReligion.containsKey(uuid)) {
                    player.sendMessage(ChatUtil.color("&cYou must leave your current religion before creating a new one!"));
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    player.closeInventory();
                    return;
                }
                player.closeInventory();
                awaitingReligionName.add(uuid);
                player.sendMessage(ChatUtil.color("&6Enter the name of your new religion in chat:"));
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            case 29 -> {
                if (religions.isEmpty()) {
                    player.sendMessage(ChatUtil.color("&cNo religions exist yet!"));
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    player.closeInventory();
                    return;
                }
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openReligionsList(player, false), 1L);
            }
            case 33 -> {
                String currentReligion = playerReligion.remove(uuid);
                if (currentReligion == null) {
                    player.sendMessage(ChatUtil.color("&cYou are not part of any religion!"));
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    player.closeInventory();
                    return;
                }

                playerRank.remove(uuid);
                if (memberCount(currentReligion) <= 0) {
                    disbandReligion(currentReligion);
                    Bukkit.broadcast(ChatUtil.component("&6The religion &e" + currentReligion + " &6has been disbanded"));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        playSound(online, Sound.ENTITY_WITHER_DEATH, 0.3f, 1.4f);
                    }

                    player.sendMessage(ChatUtil.color("&6You have left &e" + currentReligion + "&6, and it has been disbanded!"));
                } else {
                    Bukkit.broadcast(ChatUtil.component("&e" + player.getName() + " &6has left &e" + currentReligion));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        playSound(online, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.9f, 0.9f);
                    }

                    player.sendMessage(ChatUtil.color("&6You have left &e" + currentReligion + "&6!"));
                }

                playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
                saveData();
            }
            default -> {
            }
        }
    }

    private void handleConvertGuiClick(Player player, ItemStack clicked) {
        if (isBack(clicked)) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGui(player), 1L);
            return;
        }

        String religionName = extractReligionName(clicked);
        if (religionName == null) {
            return;
        }
        String canonical = findReligionName(religionName);
        if (canonical == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (canonical.equalsIgnoreCase(playerReligion.get(uuid))) {
            player.sendMessage(ChatUtil.color("&cYou are already part of " + canonical + "!"));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            player.closeInventory();
            return;
        }

        playerReligion.put(uuid, canonical);
        playerRank.put(uuid, "Follower");
        playerPrayers.putIfAbsent(uuid, 0);

        Bukkit.broadcast(ChatUtil.component("&e" + player.getName() + " &6has converted to &e" + canonical));
        for (Player online : Bukkit.getOnlinePlayers()) {
            playSound(online, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }

        player.sendMessage(ChatUtil.color("&6You have converted to &e" + canonical + "&6!"));
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        player.closeInventory();
        saveData();
    }

    private void handleIconGuiClick(Player player, ItemStack clicked) {
        UUID uuid = player.getUniqueId();
        if (isBack(clicked)) {
            pendingReligionName.remove(uuid);
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openMainGui(player), 1L);
            return;
        }

        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (clicked.getType().toString().endsWith("STAINED_GLASS_PANE")) {
            return;
        }

        String requestedName = pendingReligionName.remove(uuid);
        if (requestedName == null || requestedName.isBlank()) {
            player.sendMessage(ChatUtil.color("&cError creating religion!"));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            player.closeInventory();
            return;
        }

        if (findReligionName(requestedName) != null) {
            player.sendMessage(ChatUtil.color("&cA religion with that name already exists!"));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            player.closeInventory();
            return;
        }

        religions.add(requestedName);
        religionIcon.put(requestedName, clicked.getType());
        religionFounder.put(requestedName, uuid);
        religionPrayers.put(requestedName, 0);
        playerReligion.put(uuid, requestedName);
        playerRank.put(uuid, "Pope");
        playerPrayers.putIfAbsent(uuid, 0);

        Bukkit.broadcast(ChatUtil.component("&e" + player.getName() + " &6has founded a new religion: &e" + requestedName));
        for (Player online : Bukkit.getOnlinePlayers()) {
            playSound(online, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }

        player.sendMessage(ChatUtil.color("&6You have created the religion &e" + requestedName + "&6!"));
        player.sendMessage(ChatUtil.color("&6You are now the &ePope &6of this religion!"));
        player.closeInventory();
        saveData();
    }

    private void handleReligionNameInput(Player player, String requestedName) {
        if (requestedName.isBlank()) {
            player.sendMessage(ChatUtil.color("&cReligion name cannot be empty."));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            return;
        }

        if (findReligionName(requestedName) != null) {
            player.sendMessage(ChatUtil.color("&cA religion with that name already exists!"));
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            return;
        }

        pendingReligionName.put(player.getUniqueId(), requestedName);
        openIconGui(player);
    }

    private void handleStaffReligionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.color("&6&m---&r &eStaff Religion Commands &6&m---"));
            sender.sendMessage(ChatUtil.color("&e/staffreligion <religion> delete &7- Delete a religion"));
            sender.sendMessage(ChatUtil.color("&e/staffreligion <religion> info &7- View religion info"));
            if (sender instanceof Player player) {
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            return;
        }

        String religionArg = args[0];
        String action = args[1].toLowerCase(Locale.ROOT);
        String religion = findReligionName(religionArg);
        if (religion == null) {
            sender.sendMessage(ChatUtil.color("&cThat religion does not exist!"));
            if (sender instanceof Player player) {
                playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            }
            return;
        }

        if (action.equals("delete")) {
            disbandReligion(religion);

            Bukkit.broadcast(ChatUtil.component("&6The religion &e" + religion + " &6has been disbanded"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                playSound(online, Sound.ENTITY_WITHER_DEATH, 0.3f, 1.4f);
            }

            sender.sendMessage(ChatUtil.color("&6Successfully deleted religion &e" + religion + "&6!"));
            if (sender instanceof Player player) {
                playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            saveData();
            return;
        }

        if (action.equals("info")) {
            UUID founderUuid = religionFounder.get(religion);
            OfflinePlayer founder = founderUuid == null ? null : Bukkit.getOfflinePlayer(founderUuid);
            int totalPrayers = religionPrayers.getOrDefault(religion, 0);

            List<String> memberNames = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : playerReligion.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(religion)) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                    memberNames.add(offlinePlayer.getName() == null ? entry.getKey().toString() : offlinePlayer.getName());
                }
            }

            sender.sendMessage(ChatUtil.color("&6&m---&r &e" + religion + " Info &6&m---"));
            sender.sendMessage(ChatUtil.color("&eFounder: &f"
                    + (founder == null || founder.getName() == null ? "Unknown" : founder.getName())));
            sender.sendMessage(ChatUtil.color("&eTotal Prayers: &f" + totalPrayers));
            sender.sendMessage(ChatUtil.color("&eMembers (&f" + memberNames.size() + "&e): &f" + String.join(", ", memberNames)));
            if (sender instanceof Player player) {
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            return;
        }

        sender.sendMessage(ChatUtil.color("&6&m---&r &eStaff Religion Commands &6&m---"));
        sender.sendMessage(ChatUtil.color("&e/staffreligion <religion> delete &7- Delete a religion"));
        sender.sendMessage(ChatUtil.color("&e/staffreligion <religion> info &7- View religion info"));
        if (sender instanceof Player player) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void openMainGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, mainTitle());
        if (!isFloodgatePlayer(player.getUniqueId())) {
            fill(gui, Material.BLACK_STAINED_GLASS_PANE);
        }

        UUID uuid = player.getUniqueId();
        String religion = playerReligion.get(uuid);
        if (religion != null) {
            int prayers = playerPrayers.getOrDefault(uuid, 0);
            String rank = playerRank.getOrDefault(uuid, "Follower");
            int members = memberCount(religion);
            int totalPrayers = religionPrayers.getOrDefault(religion, 0);
            Material icon = religionIcon.getOrDefault(religion, Material.BEACON);

            gui.setItem(13, namedItem(icon, "&6" + religion, List.of(
                    "&7Your Current Religion",
                    "",
                    "&eRank: &f" + rank,
                    "&ePersonal Prayers: &f" + prayers,
                    "&eMembers: &f" + members,
                    "&eTotal Prayers: &f" + totalPrayers
            )));
        } else {
            gui.setItem(13, namedItem(Material.BARRIER, "&cNo Religion", List.of(
                    "&7You are not part of a religion",
                    "&7Convert or create one"
            )));
        }

        gui.setItem(20, namedItem(Material.NETHER_STAR, "&6Pray", List.of("&7Pray to your god", "", "&eClick to pray!")));
        gui.setItem(22, namedItem(Material.OAK_DOOR, "&6Convert Religion", List.of("&7Join an existing religion", "", "&eClick to view religions!")));
        gui.setItem(24, namedItem(Material.WRITABLE_BOOK, "&6Create Religion", List.of("&7Start your own religion", "", "&eClick to create!")));
        gui.setItem(29, namedItem(Material.PAPER, "&6All Religions", List.of("&7View all existing religions", "", "&eClick to view!")));
        gui.setItem(33, namedItem(Material.BARRIER, "&cLeave Religion", List.of("&7Leave your current religion", "", "&eClick to leave!")));

        player.openInventory(gui);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    private void openReligionsList(Player player, boolean convertMode) {
        List<String> sorted = new ArrayList<>(religions);
        sorted.sort(Comparator.comparingInt((String religion) -> religionPrayers.getOrDefault(religion, 0)).reversed());

        int rows = (int) Math.ceil(sorted.size() / 9.0D) + 2;
        rows = Math.max(2, Math.min(6, rows));
        int size = rows * 9;

        Inventory gui = Bukkit.createInventory(null, size, convertMode ? convertTitle() : allTitle());
        if (!isFloodgatePlayer(player.getUniqueId())) {
            fill(gui, Material.BLACK_STAINED_GLASS_PANE);
        }
        int backSlot = size - 5;
        gui.setItem(backSlot, namedItem(Material.ARROW, "&6Back", List.of("&7Return to main menu")));

        int slot = 9;
        int lastSlot = size - 1;
        for (String religion : sorted) {
            if (slot >= lastSlot) {
                break;
            }
            if (slot == backSlot) {
                slot++;
                if (slot >= lastSlot) {
                    break;
                }
            }

            MemberInfo members = memberInfo(religion);
            UUID founderUuid = religionFounder.get(religion);
            OfflinePlayer founder = founderUuid == null ? null : Bukkit.getOfflinePlayer(founderUuid);
            String founderName = founder == null || founder.getName() == null ? "Unknown" : founder.getName();
            List<String> lore = new ArrayList<>();
            lore.add("&7Founded by: &e" + founderName);
            lore.add("");
            lore.add("&eMembers (" + members.count + "): &f" + members.preview);
            lore.add("&eTotal Prayers: &f" + religionPrayers.getOrDefault(religion, 0));
            if (convertMode) {
                lore.add("");
                lore.add("&aClick to convert!");
            }

            gui.setItem(slot, namedItem(religionIcon.getOrDefault(religion, Material.BEACON), "&6" + religion, lore));
            slot++;
        }

        player.openInventory(gui);
    }

    private void openIconGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, iconTitle());
        if (!isFloodgatePlayer(player.getUniqueId())) {
            fill(gui, Material.BLACK_STAINED_GLASS_PANE);
        }

        gui.setItem(49, namedItem(Material.ARROW, "&6Back", List.of("&7Return to main menu")));
        gui.setItem(10, namedItem(Material.GRASS_BLOCK, "&6Grass Block", List.of("&eClick to select!")));
        gui.setItem(11, namedItem(Material.STONE, "&6Stone", List.of("&eClick to select!")));
        gui.setItem(12, namedItem(Material.OAK_LOG, "&6Oak Log", List.of("&eClick to select!")));
        gui.setItem(13, namedItem(Material.GOLD_BLOCK, "&6Gold Block", List.of("&eClick to select!")));
        gui.setItem(14, namedItem(Material.DIAMOND_BLOCK, "&6Diamond Block", List.of("&eClick to select!")));
        gui.setItem(15, namedItem(Material.EMERALD_BLOCK, "&6Emerald Block", List.of("&eClick to select!")));
        gui.setItem(16, namedItem(Material.NETHER_STAR, "&6Nether Star", List.of("&eClick to select!")));
        gui.setItem(19, namedItem(Material.BEACON, "&6Beacon", List.of("&eClick to select!")));
        gui.setItem(20, namedItem(Material.ENCHANTING_TABLE, "&6Enchanting Table", List.of("&eClick to select!")));
        gui.setItem(21, namedItem(Material.TOTEM_OF_UNDYING, "&6Totem of Undying", List.of("&eClick to select!")));
        gui.setItem(22, namedItem(Material.END_CRYSTAL, "&6End Crystal", List.of("&eClick to select!")));
        gui.setItem(23, namedItem(Material.DRAGON_EGG, "&6Dragon Egg", List.of("&eClick to select!")));
        gui.setItem(24, namedItem(Material.ELYTRA, "&6Elytra", List.of("&eClick to select!")));
        gui.setItem(25, namedItem(Material.WHITE_BANNER, "&6Banner", List.of("&eClick to select!")));

        player.openInventory(gui);
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

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = namedItem(material, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
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

    private int memberCount(String religion) {
        int count = 0;
        for (String value : playerReligion.values()) {
            if (value.equalsIgnoreCase(religion)) {
                count++;
            }
        }
        return count;
    }

    private MemberInfo memberInfo(String religion) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerReligion.entrySet()) {
            if (!entry.getValue().equalsIgnoreCase(religion)) {
                continue;
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            names.add(offlinePlayer.getName() == null ? entry.getKey().toString() : offlinePlayer.getName());
        }

        int count = names.size();
        if (names.isEmpty()) {
            return new MemberInfo(0, "None");
        }
        if (names.size() > 8) {
            List<String> preview = names.subList(0, 8);
            return new MemberInfo(count, String.join(", ", preview) + " ...");
        }
        return new MemberInfo(count, String.join(", ", names));
    }

    private boolean isManagedTitle(Component title) {
        return title.equals(mainTitle())
                || title.equals(convertTitle())
                || title.equals(allTitle())
                || title.equals(iconTitle());
    }

    private boolean isBack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.customName() == null) {
            return false;
        }
        String plain = ChatUtil.plainText(meta.customName());
        return plain != null && plain.toLowerCase(Locale.ROOT).contains("back");
    }

    private String extractReligionName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.customName() == null) {
            return null;
        }
        String plain = ChatUtil.plainText(meta.customName());
        if (plain == null || plain.isBlank() || plain.equalsIgnoreCase("back")) {
            return null;
        }
        return plain;
    }

    private String findReligionName(String requested) {
        for (String religion : religions) {
            if (religion.equalsIgnoreCase(requested)) {
                return religion;
            }
        }
        return null;
    }

    private Component mainTitle() {
        return ChatUtil.component(plugin.getConfig().getString("religion.gui-title", "&8Religions"));
    }

    private Component convertTitle() {
        return ChatUtil.component("&8Convert to Religion");
    }

    private Component allTitle() {
        return ChatUtil.component("&8All Religions");
    }

    private Component iconTitle() {
        return ChatUtil.component("&8Choose Religion Icon");
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void loadData() {
        clearAll();

        ConfigurationSection root;
        boolean migrated = false;
        if (dataFile.exists()) {
            root = YamlConfiguration.loadConfiguration(dataFile);
            migrated = true;
        } else {
            root = plugin.getConfig();
        }

        for (Map<?, ?> raw : root.getMapList("religion-data")) {
            Object nameObj = raw.get("name");
            if (!(nameObj instanceof String name) || name.isBlank()) {
                continue;
            }

            religions.add(name);
            try {
                Object iconObj = raw.containsKey("icon") ? raw.get("icon") : "BEACON";
                String iconName = String.valueOf(iconObj);
                religionIcon.put(name, Material.valueOf(iconName));
            } catch (IllegalArgumentException ex) {
                religionIcon.put(name, Material.BEACON);
            }

            Object founderObj = raw.get("founder");
            if (founderObj instanceof String founderText) {
                try {
                    religionFounder.put(name, UUID.fromString(founderText));
                } catch (IllegalArgumentException ignored) {
                }
            }

            Object prayersObj = raw.get("prayers");
            int prayers = prayersObj instanceof Number number ? number.intValue() : 0;
            religionPrayers.put(name, Math.max(0, prayers));
        }

        ConfigurationSection players = root.getConfigurationSection(migrated ? "players" : "religion-players");
        if (players == null) {
            if (migrated) {
                if (!dataFile.delete()) {
                    plugin.getLogger().warning("Migrated religion.yml into config.yml, but could not delete the old file.");
                }
                saveData();
            }
            return;
        }

        for (String uuidText : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            String religion = players.getString(uuidText + ".religion");
            if (religion != null && findReligionName(religion) != null) {
                playerReligion.put(uuid, findReligionName(religion));
            }

            String rank = players.getString(uuidText + ".rank");
            if (rank != null) {
                playerRank.put(uuid, rank);
            }

            playerPrayers.put(uuid, Math.max(0, players.getInt(uuidText + ".prayers", 0)));
            long lastPrayer = players.getLong(uuidText + ".last-prayer", -1L);
            if (lastPrayer > 0) {
                playerLastPrayer.put(uuid, Instant.ofEpochMilli(lastPrayer));
            }
        }

        if (migrated) {
            if (!dataFile.delete()) {
                plugin.getLogger().warning("Migrated religion.yml into config.yml, but could not delete the old file.");
            }
            saveData();
        }
    }

    private void saveData() {
        List<Map<String, Object>> religionDataList = new ArrayList<>();
        for (String religion : religions) {
            Map<String, Object> node = new HashMap<>();
            node.put("name", religion);
            node.put("icon", religionIcon.getOrDefault(religion, Material.BEACON).name());
            UUID founder = religionFounder.get(religion);
            if (founder != null) {
                node.put("founder", founder.toString());
            }
            node.put("prayers", religionPrayers.getOrDefault(religion, 0));
            religionDataList.add(node);
        }

        plugin.getConfig().set("religion-data", religionDataList);
        plugin.getConfig().set("religion-players", null);

        Set<UUID> allUuids = new HashSet<>();
        allUuids.addAll(playerReligion.keySet());
        allUuids.addAll(playerRank.keySet());
        allUuids.addAll(playerPrayers.keySet());
        allUuids.addAll(playerLastPrayer.keySet());

        for (UUID uuid : allUuids) {
            String base = "religion-players." + uuid;
            String religion = playerReligion.get(uuid);
            if (religion != null) {
                plugin.getConfig().set(base + ".religion", religion);
            }
            String rank = playerRank.get(uuid);
            if (rank != null) {
                plugin.getConfig().set(base + ".rank", rank);
            }
            plugin.getConfig().set(base + ".prayers", playerPrayers.getOrDefault(uuid, 0));
            Instant lastPrayer = playerLastPrayer.get(uuid);
            if (lastPrayer != null) {
                plugin.getConfig().set(base + ".last-prayer", lastPrayer.toEpochMilli());
            }
        }

        plugin.saveConfig();
    }

    private void clearAll() {
        religions.clear();
        playerReligion.clear();
        playerRank.clear();
        playerPrayers.clear();
        playerLastPrayer.clear();
        religionIcon.clear();
        religionFounder.clear();
        religionPrayers.clear();
        awaitingReligionName.clear();
        pendingReligionName.clear();
    }

    private void disbandReligion(String religion) {
        List<UUID> affected = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerReligion.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(religion)) {
                affected.add(entry.getKey());
            }
        }

        for (UUID uuid : affected) {
            playerReligion.remove(uuid);
            playerRank.remove(uuid);
            playerPrayers.remove(uuid);
            playerLastPrayer.remove(uuid);
        }

        religions.remove(religion);
        religionIcon.remove(religion);
        religionFounder.remove(religion);
        religionPrayers.remove(religion);
    }

    private record MemberInfo(int count, String preview) {
    }
}
