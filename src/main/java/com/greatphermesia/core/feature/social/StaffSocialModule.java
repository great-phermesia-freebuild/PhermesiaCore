package com.greatphermesia.core.feature.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.greatphermesia.core.PhermesiaCorePlugin;
import com.greatphermesia.core.module.PluginModule;
import com.greatphermesia.core.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class StaffSocialModule implements PluginModule, Listener, CommandExecutor, TabCompleter {

    private final PhermesiaCorePlugin plugin;
    private final Set<UUID> staffChatToggled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> tpBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PunishmentType>> activePunishments = new ConcurrentHashMap<>();
    private final Map<UUID, WarpRequest> warpRequests = new ConcurrentHashMap<>();
    private File dataFile;
    private int punishmentTaskId = -1;

    public StaffSocialModule(PhermesiaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "StaffSocial";
    }

    @Override
    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "social.yml");
        loadData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindCommand("sc");
        bindCommand("map");
        bindCommand("tpblock");
        bindCommand("tpblocklist");
        bindCommand("playtop");
        bindCommand("scrutv");
        bindCommand("clearchat");
        bindCommand("staffinfo");
        bindCommand("requestwarp");
        bindCommand("warprequests");
        bindCommand("punish");
        bindCommand("puns");

        startPunishmentTask();

        plugin.getLogger().info("[StaffSocial] Module enabled.");
    }

    @Override
    public void disable() {
        stopPunishmentTask();
        saveData();
        plugin.getLogger().info("[StaffSocial] Module stopped.");
    }

    public boolean isStaffChatEnabled(UUID uuid) {
        return staffChatToggled.contains(uuid);
    }

    public boolean isTeleportBlocked(UUID targetUuid, UUID requesterUuid) {
        Set<UUID> blocked = tpBlocks.get(targetUuid);
        return blocked != null && blocked.contains(requesterUuid);
    }

    private void bindCommand(String name) {
        if (plugin.getCommand(name) == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        plugin.getCommand(name).setExecutor(this);
        plugin.getCommand(name).setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("playtop")) {
            sendPlayTop(sender);
            return true;
        }

        if (commandName.equals("puns")) {
            handlePunishmentStatusCommand(sender, args);
            return true;
        }

        if (commandName.equals("punish")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
                return true;
            }
            handlePunishCommand(player, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        return switch (commandName) {
            case "sc" -> {
                handleStaffChatCommand(player, args);
                yield true;
            }
            case "map" -> {
                handleMapCommand(player);
                yield true;
            }
            case "tpblock" -> {
                handleTpBlockCommand(player, args);
                yield true;
            }
            case "tpblocklist" -> {
                handleTpBlockList(player);
                yield true;
            }
            case "scrutv" -> {
                handleScrutvCommand(player);
                yield true;
            }
            case "clearchat" -> {
                handleClearChatCommand(player);
                yield true;
            }
            case "staffinfo" -> {
                handleStaffInfoCommand(player);
                yield true;
            }
            case "requestwarp" -> {
                handleRequestWarpCommand(player, args);
                yield true;
            }
            case "warprequests" -> {
                handleWarpRequestsCommand(player);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (!commandName.equals("tpblock") && !commandName.equals("punish") && !commandName.equals("puns")) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                suggestions.add(online.getName());
            }
        }
        return suggestions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStaffChatMessage(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!staffChatToggled.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        Component displayName = player.displayName();
        Component message = event.message();
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("[Staff] " + ChatUtil.plainText(displayName) + ": " + ChatUtil.plainText(message));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("staff.chat") || hasHelperAccess(online)) {
                    online.sendMessage(Component.text("[Staff] ", NamedTextColor.RED)
                            .append(displayName)
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(message));
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMentionPing(AsyncChatEvent event) {
        String updatedMessage = ChatUtil.plainText(event.message());
        for (Object audience : event.viewers()) {
            if (!(audience instanceof Player recipient)) {
                continue;
            }
            boolean pinged = false;
            String[] checks = {recipient.getName(), ChatUtil.plainText(recipient.displayName())};
            for (String candidate : checks) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                String cleanCandidate = candidate.replace(".", "");
                if (containsWord(updatedMessage, cleanCandidate)) {
                    updatedMessage = highlightWord(updatedMessage, cleanCandidate);
                    if (!pinged) {
                        Player target = recipient;
                        Bukkit.getScheduler().runTask(plugin,
                                () -> target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f));
                        pinged = true;
                    }
                }
            }
        }
        event.message(ChatUtil.component(updatedMessage));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPunishedEchoChat(AsyncChatEvent event) {
        if (!isPunishmentActive(event.getPlayer().getUniqueId(), PunishmentType.ECHO_CHAT)) {
            return;
        }

        String original = ChatUtil.plainText(event.message());
        event.message(ChatUtil.component(original + " | " + original));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreprocessCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().trim();
        if (raw.isEmpty()) {
            return;
        }
        String[] split = raw.split("\\s+");
        String base = split[0].toLowerCase(Locale.ROOT);
        Player player = event.getPlayer();

        if (isPunishmentActive(player.getUniqueId(), PunishmentType.COMMAND_SHACKLE)
                && isCommandShackled(base)) {
            event.setCancelled(true);
            player.sendMessage(ChatUtil.color(getPunishPrefix() + " &cPunishment active: that command is locked."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        if (base.equals("/ignore") && split.length >= 2) {
            Player target = Bukkit.getPlayerExact(split[1]);
            if (target != null) {
                getOrCreateTpBlockSet(player.getUniqueId()).add(target.getUniqueId());
            }
            return;
        }

        if (base.equals("/unignore") && split.length >= 2) {
            Player target = Bukkit.getPlayerExact(split[1]);
            if (target != null) {
                getOrCreateTpBlockSet(player.getUniqueId()).remove(target.getUniqueId());
            }
            return;
        }

        if ((base.equals("/tpa") || base.equals("/tp") || base.equals("/teleport")) && split.length >= 2) {
            Player target = Bukkit.getPlayerExact(split[1]);
            if (target != null && isTeleportBlocked(target.getUniqueId(), player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatUtil.color(getBlockedTeleportMessage()));
            }
            return;
        }

        if (base.equals("/playtime")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                OfflinePlayer target = player;
                if (split.length >= 2) {
                    target = Bukkit.getOfflinePlayer(split[1]);
                }
                sendPlaytimeDetails(player, target);
            }, 2L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        if (isPunishmentActive(shooter.getUniqueId(), PunishmentType.PROJECTILE_LOCK)) {
            event.setCancelled(true);
            shooter.sendActionBar(Component.text("Projectile punishment active", NamedTextColor.RED));
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.45f, 1.55f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStaffInfoMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StaffInfoHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!hasHelperAccess(player)) {
            player.closeInventory();
            player.sendMessage(ChatUtil.color("&cYou need Helper access for this menu."));
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> runStaffMenuCommand(player, "clearchat");
            case 12 -> runStaffMenuCommand(player, "staffchat");
            case 14 -> runStaffMenuCommand(player, "vanish");
            case 16 -> runStaffMenuCommand(player, "warprequests");
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWarpRequestsMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WarpRequestsHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!hasHelperAccess(player)) {
            player.closeInventory();
            player.sendMessage(ChatUtil.color("&cYou need Helper access for warp requests."));
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        UUID requestId = holder.slotRequestIds.get(slot);
        if (requestId == null) {
            return;
        }

        WarpRequest request = warpRequests.get(requestId);
        if (request == null) {
            openWarpRequestsMenu(player);
            return;
        }

        if (event.isRightClick() && event.isShiftClick()) {
            warpRequests.remove(requestId);
            saveData();
            player.sendMessage(ChatUtil.color("&cDeleted warp request &f" + request.name() + "&c."));
            openWarpRequestsMenu(player);
            return;
        }

        if (event.isRightClick()) {
            approveWarpRequest(player, request);
            return;
        }

        teleportToWarpRequest(player, request);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPunishMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PunishMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player moderator)) {
            return;
        }
        if (!moderator.hasPermission("punish.manage")) {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &cYou do not have permission to manage punishments."));
            moderator.closeInventory();
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            moderator.closeInventory();
            return;
        }

        PunishmentType punishmentType = PunishmentType.bySlot(slot);
        if (punishmentType == null) {
            return;
        }

        boolean active = togglePunishment(holder.targetUuid, punishmentType);
        saveData();

        String targetName = resolvePlayerName(holder.targetUuid, holder.targetName);
        event.getInventory().setItem(4, buildPunishTargetInfo(holder.targetUuid, targetName));
        event.getInventory().setItem(punishmentType.slot, buildPunishmentButton(holder.targetUuid, punishmentType));

        if (active) {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &e" + punishmentType.display + " &aenabled for &f" + targetName));
            moderator.playSound(moderator.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            Player target = Bukkit.getPlayer(holder.targetUuid);
            if (target != null) {
                target.sendMessage(ChatUtil.color(getPunishPrefix() + " &cA punishment was activated: &f" + punishmentType.display));
                target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.2f);
            }
        } else {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &e" + punishmentType.display + " &7removed from &f" + targetName));
            moderator.playSound(moderator.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.2f);
        }
    }

    private void handleStaffChatCommand(Player player, String[] args) {
        if (!player.hasPermission("staff.chat") && !hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cNo permission."));
            return;
        }

        if (args.length > 0) {
            String message = String.join(" ", args);
            Component displayName = player.displayName();
            Component chatMessage = ChatUtil.component(message);
            plugin.getLogger().info("[Staff] " + ChatUtil.plainText(displayName) + ": " + ChatUtil.plainText(chatMessage));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("staff.chat") || hasHelperAccess(online)) {
                    online.sendMessage(Component.text("[Staff] ", NamedTextColor.RED)
                            .append(displayName)
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(chatMessage));
                }
            }
            return;
        }

        UUID uuid = player.getUniqueId();
        if (staffChatToggled.remove(uuid)) {
            player.sendMessage(ChatUtil.color("&c[Staff] &7Staff chat disabled."));
        } else {
            staffChatToggled.add(uuid);
            player.sendMessage(ChatUtil.color("&c[Staff] &aStaff chat enabled."));
        }
    }

    private void handlePunishCommand(Player moderator, String[] args) {
        if (!moderator.hasPermission("punish.manage")) {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &cYou do not have permission to use /punish."));
            return;
        }
        if (args.length < 1) {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &cUsage: /punish <playername>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            moderator.sendMessage(ChatUtil.color(getPunishPrefix() + " &cThat player must be online for /punish."));
            return;
        }

        openPunishMenu(moderator, target.getUniqueId(), target.getName());
    }

    private void handlePunishmentStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("punish.view") && !sender.hasPermission("punish.manage")) {
            sender.sendMessage(ChatUtil.color(getPunishPrefix() + " &cYou do not have permission to use /puns."));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatUtil.color(getPunishPrefix() + " &cUsage: /puns <playername>"));
            return;
        }

        OfflinePlayer target = findPlayerByName(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(ChatUtil.color(getPunishPrefix() + " &cUnknown player: &f" + args[0]));
            return;
        }

        String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        Set<PunishmentType> active = activePunishments.getOrDefault(target.getUniqueId(), Collections.emptySet());

        sender.sendMessage(ChatUtil.color("&8&m--------------------------------"));
        sender.sendMessage(ChatUtil.color(getPunishPrefix() + " &eStatus for &f" + targetName));
        for (PunishmentType punishmentType : PunishmentType.values()) {
            boolean enabled = active.contains(punishmentType);
            sender.sendMessage(ChatUtil.color((enabled ? "&a[ACTIVE] " : "&7[INACTIVE] ") + "&f"
                    + punishmentType.display + (enabled ? " &aPunishment active" : "")));
        }
        sender.sendMessage(ChatUtil.color("&8&m--------------------------------"));
    }

    private void openPunishMenu(Player moderator, UUID targetUuid, String targetName) {
        PunishMenuHolder holder = new PunishMenuHolder(targetUuid, targetName);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            ChatUtil.component("&0[Punishments] &7-> &f" + targetName));
        holder.inventory = inventory;

        decoratePunishMenu(inventory, moderator);
        inventory.setItem(4, buildPunishTargetInfo(targetUuid, targetName));
        for (PunishmentType punishmentType : PunishmentType.values()) {
            inventory.setItem(punishmentType.slot, buildPunishmentButton(targetUuid, punishmentType));
        }
        inventory.setItem(49, namedItem(Material.BARRIER, "&c&lClose Menu", List.of("&7Click to close")));

        moderator.openInventory(inventory);
        moderator.playSound(moderator.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.1f);
    }

    private void decoratePunishMenu(Inventory inventory, Player moderator) {
        if (isFloodgatePlayer(moderator.getUniqueId())) {
            return;
        }
        ItemStack darkPane = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.<String>of());
        ItemStack cyanPane = namedItem(Material.CYAN_STAINED_GLASS_PANE, " ", List.<String>of());
        ItemStack bluePane = namedItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.<String>of());

        int[] border = {
                0, 1, 2, 3, 5, 6, 7, 8,
                9, 17,
                18, 26,
                27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44,
                45, 53
        };
        for (int slot : border) {
            inventory.setItem(slot, darkPane);
        }

        int[] cyanSlots = {10, 12, 14, 16, 28, 30, 32, 34, 46, 48, 50, 52};
        for (int slot : cyanSlots) {
            inventory.setItem(slot, cyanPane);
        }

        int[] blueSlots = {11, 13, 15, 29, 31, 33, 47, 51};
        for (int slot : blueSlots) {
            inventory.setItem(slot, bluePane);
        }
    }

    private ItemStack buildPunishTargetInfo(UUID targetUuid, String targetName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
            skullMeta.customName(ChatUtil.component("&e&lTarget: &f" + targetName));
            skullMeta.lore(ChatUtil.components(List.of(
                    ChatUtil.color("&7Click a punishment below"),
                    ChatUtil.color("&7to toggle it on or off")
            )));
            skullMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack buildPunishmentButton(UUID targetUuid, PunishmentType punishmentType) {
        boolean active = isPunishmentActive(targetUuid, punishmentType);
        List<String> lore = new ArrayList<>();
        lore.add(ChatUtil.color("&7" + punishmentType.description));
        lore.add(ChatUtil.color("&8" + punishmentType.behavior));
        lore.add(ChatUtil.color(""));
        if (active) {
            lore.add(ChatUtil.color("&aPunishment active"));
            lore.add(ChatUtil.color("&7Click to remove"));
        } else {
            lore.add(ChatUtil.color("&7Click to activate"));
        }

        ItemStack item = namedItem(punishmentType.material, punishmentType.displayName, lore);
        if (active) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
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

    private boolean togglePunishment(UUID uuid, PunishmentType punishmentType) {
        Set<PunishmentType> punishments = getOrCreatePunishments(uuid);
        if (punishments.remove(punishmentType)) {
            if (punishments.isEmpty()) {
                activePunishments.remove(uuid);
            }
            return false;
        }

        punishments.add(punishmentType);
        return true;
    }

    private Set<PunishmentType> getOrCreatePunishments(UUID uuid) {
        return activePunishments.computeIfAbsent(uuid, unused -> ConcurrentHashMap.newKeySet());
    }

    private boolean isPunishmentActive(UUID uuid, PunishmentType punishmentType) {
        Set<PunishmentType> punishments = activePunishments.get(uuid);
        return punishments != null && punishments.contains(punishmentType);
    }

    private boolean isCommandShackled(String commandBase) {
        return commandBase.equals("/tp")
                || commandBase.equals("/tpa")
                || commandBase.equals("/teleport")
                || commandBase.equals("/warp")
                || commandBase.equals("/home")
                || commandBase.equals("/spawn")
                || commandBase.equals("/back")
                || commandBase.equals("/rtp")
                || commandBase.equals("/rtprandom")
                || commandBase.equals("/rtpwarp");
    }

    private void startPunishmentTask() {
        stopPunishmentTask();
        punishmentTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPunishments, 20L, 40L);
    }

    private void stopPunishmentTask() {
        if (punishmentTaskId != -1) {
            Bukkit.getScheduler().cancelTask(punishmentTaskId);
            punishmentTaskId = -1;
        }
    }

    private void tickPunishments() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isPunishmentActive(online.getUniqueId(), PunishmentType.FOG_CURSE)) {
                continue;
            }

            online.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false, true));
            online.getWorld().spawnParticle(Particle.SMOKE, online.getLocation().add(0.0D, 1.0D, 0.0D),
                    10, 0.25D, 0.45D, 0.25D, 0.01D);
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 0.6f);
        }
    }

    private String resolvePlayerName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            return offline.getName();
        }
        return fallback;
    }

    private OfflinePlayer findPlayerByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline;
            }
        }
        return null;
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

    private void handleClearChatCommand(Player player) {
        if (!hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cYou need Helper access to clear chat."));
            return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 120; i++) {
                online.sendMessage(Component.empty());
            }
            online.sendMessage(ChatUtil.color("&7[&6Staff&7] &eChat was cleared by &f" + player.getName() + "&e."));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
    }

    private void handleStaffInfoCommand(Player player) {
        if (!hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cYou need Helper access to open staff info."));
            return;
        }
        openStaffInfoMenu(player);
    }

    private void handleRequestWarpCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatUtil.color("&cUsage: /requestwarp <name>"));
            return;
        }

        String warpName = cleanWarpName(args[0]);
        if (warpName == null) {
            player.sendMessage(ChatUtil.color("&cWarp names can only use letters, numbers, underscores, and hyphens."));
            return;
        }

        for (WarpRequest request : warpRequests.values()) {
            if (request.requesterUuid().equals(player.getUniqueId()) && request.name().equalsIgnoreCase(warpName)) {
                player.sendMessage(ChatUtil.color("&eYou already requested a warp named &f" + warpName + "&e."));
                return;
            }
        }

        WarpRequest request = new WarpRequest(
                UUID.randomUUID(),
                warpName,
                player.getUniqueId(),
                player.getName(),
                player.getLocation().clone(),
                Instant.now().getEpochSecond());
        warpRequests.put(request.id(), request);
        saveData();

        player.sendMessage(ChatUtil.color("&aRequested warp &f" + warpName + "&a at your current location."));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (hasHelperAccess(online)) {
                online.sendMessage(ChatUtil.color("&7[&6Warp Requests&7] &f" + player.getName()
                        + " &erequested warp &f" + warpName + "&e. &7Use &f/warprequests&7."));
            }
        }
    }

    private void handleWarpRequestsCommand(Player player) {
        if (!hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cYou need Helper access to manage warp requests."));
            return;
        }
        openWarpRequestsMenu(player);
    }

    private void openStaffInfoMenu(Player player) {
        StaffInfoHolder holder = new StaffInfoHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatUtil.component("&6Staff Info"));
        holder.inventory = inventory;

        if (!isFloodgatePlayer(player.getUniqueId())) {
            ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(10, namedItem(Material.BELL, "&6&lClear Chat", List.of(
                "&7Command: &f/clearchat &8or &f/cc",
                "&7Clears public chat for everyone.",
                "",
                "&eClick to run.")));
        inventory.setItem(12, namedItem(Material.ECHO_SHARD, "&c&lStaff Chat", List.of(
                "&7Command: &f/staffchat",
                "&7Toggle or send staff-only chat.",
                "",
                "&eClick to toggle.")));
        inventory.setItem(14, namedItem(Material.ENDER_EYE, "&b&lVanish", List.of(
                "&7Command: &f/vanish",
                "&7Toggle staff vanish.",
                "",
                "&eClick to toggle.")));
        inventory.setItem(16, namedItem(Material.COMPASS, "&d&lWarp Requests", List.of(
                "&7Command: &f/warprequests &8or &f/wr",
                "&7Review and approve requested warps.",
                "",
                "&eClick to open.")));
        inventory.setItem(22, namedItem(Material.BARRIER, "&c&lClose", List.of("&7Close this menu.")));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private void openWarpRequestsMenu(Player player) {
        WarpRequestsHolder holder = new WarpRequestsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatUtil.component("&6Warp Requests"));
        holder.inventory = inventory;

        if (!isFloodgatePlayer(player.getUniqueId())) {
            ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
            for (int slot : List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53)) {
                inventory.setItem(slot, border);
            }
        }

        inventory.setItem(4, namedItem(Material.COMPASS, "&6&lWarp Requests", List.of(
                "&7Left-click a request to teleport.",
                "&7Right-click to approve and create the EssentialsX warp.",
                "&7Shift-right-click to delete.")));

        List<WarpRequest> sorted = new ArrayList<>(warpRequests.values());
        sorted.sort(Comparator.comparingLong(WarpRequest::createdAtEpochSeconds));
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int index = 0;
        for (WarpRequest request : sorted) {
            if (index >= slots.length) {
                break;
            }
            int slot = slots[index++];
            holder.slotRequestIds.put(slot, request.id());
            inventory.setItem(slot, buildWarpRequestItem(request));
        }

        if (warpRequests.isEmpty()) {
            inventory.setItem(22, namedItem(Material.LIGHT_GRAY_DYE, "&7&lNo Requests", List.of("&7There are no pending warp requests.")));
        }

        inventory.setItem(49, namedItem(Material.BARRIER, "&c&lClose", List.of("&7Close this menu.")));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private ItemStack buildWarpRequestItem(WarpRequest request) {
        Location location = request.location();
        String worldName = location.getWorld() == null ? "Unknown" : location.getWorld().getName();
        return namedItem(Material.ENDER_PEARL, "&d&l" + request.name(), List.of(
                "&7Requested by: &f" + request.requesterName(),
                "&7World: &f" + worldName,
                "&7Location: &f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
                "&7Created: &f" + ago(request.createdAtEpochSeconds()) + " ago",
                "",
                "&eLeft-click &7teleport",
                "&aRight-click &7approve warp",
                "&cShift-right-click &7delete request"));
    }

    private void runStaffMenuCommand(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private void teleportToWarpRequest(Player player, WarpRequest request) {
        Location location = request.location();
        if (location.getWorld() == null) {
            player.sendMessage(ChatUtil.color("&cThat request's world is not loaded."));
            return;
        }

        player.teleport(location);
        player.sendMessage(ChatUtil.color("&aTeleported to requested warp &f" + request.name() + "&a."));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.1f);
    }

    private void approveWarpRequest(Player player, WarpRequest request) {
        if (createEssentialsWarp(request.name(), request.location())) {
            warpRequests.remove(request.id());
            saveData();
            player.sendMessage(ChatUtil.color("&aCreated EssentialsX warp &f" + request.name() + "&a."));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.1f);
            openWarpRequestsMenu(player);
        } else {
            player.sendMessage(ChatUtil.color("&cCould not create that EssentialsX warp. Check the console for details."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        }
    }

    private boolean createEssentialsWarp(String name, Location location) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            essentials = Bukkit.getPluginManager().getPlugin("EssentialsX");
        }
        if (essentials == null || location.getWorld() == null) {
            return false;
        }

        try {
            Object warps = essentials.getClass().getMethod("getWarps").invoke(essentials);
            try {
                Method getWarp = warps.getClass().getMethod("getWarp", String.class);
                Object existing = getWarp.invoke(warps, name);
                if (existing instanceof Location) {
                    plugin.getLogger().warning("Warp request approval blocked because warp already exists: " + name);
                    return false;
                }
            } catch (ReflectiveOperationException ignored) {
            }

            Method setWarp = warps.getClass().getMethod("setWarp", String.class, Location.class);
            setWarp.invoke(warps, name, location);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create EssentialsX warp " + name + ": " + ex.getMessage());
            return false;
        }
    }

    private String cleanWarpName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isBlank() || cleaned.length() > 32) {
            return null;
        }
        return cleaned;
    }

    private boolean hasHelperAccess(Player player) {
        return player != null && (player.isOp() || player.hasPermission("group.helper"));
    }

    private String ago(long epochSeconds) {
        long seconds = Math.max(0L, Duration.between(Instant.ofEpochSecond(epochSeconds), Instant.now()).getSeconds());
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

    private void handleMapCommand(Player player) {
        Component message = Component.text("Click ", NamedTextColor.AQUA)
                .append(Component.text("[HERE]", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl("http://play.greatphermesia.com:8367/"))
                        .hoverEvent(HoverEvent.showText(Component.text("Server Map", NamedTextColor.DARK_AQUA))))
                .append(Component.text(" to open the server map!", NamedTextColor.AQUA));
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private void handleTpBlockCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatUtil.color(getTpPrefix() + " &cUsage: /tpblock <player>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatUtil.color(getTpPrefix() + " &cThat player is not online."));
            return;
        }

        Set<UUID> blocked = getOrCreateTpBlockSet(player.getUniqueId());
        if (blocked.remove(target.getUniqueId())) {
            player.sendMessage(ChatUtil.color(getTpPrefix() + " &aYou can now receive teleport requests from &e"
                    + target.getName() + "&a."));
        } else {
            blocked.add(target.getUniqueId());
            player.sendMessage(ChatUtil.color(getTpPrefix() + " &cYou have blocked teleport requests from &e"
                    + target.getName() + "&c."));
        }
    }

    private void handleTpBlockList(Player player) {
        player.sendMessage(ChatUtil.color(getTpPrefix() + " &eYour blocked players:"));
        int count = 0;
        Set<UUID> blocked = tpBlocks.get(player.getUniqueId());
        if (blocked != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (blocked.contains(online.getUniqueId())) {
                    player.sendMessage(ChatUtil.color("&7- &e" + online.getName()));
                    count++;
                }
            }
        }

        if (count == 0) {
            player.sendMessage(ChatUtil.color("&7You haven't blocked anyone."));
        }
    }

    private void handleScrutvCommand(Player player) {
        if (!player.hasPermission("supervanish.use") && !hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cYou don't have permission to use this command!"));
            return;
        }
        if (!player.hasPermission("supervanish.vanish") && !hasHelperAccess(player)) {
            player.sendMessage(ChatUtil.color("&cYou don't have permission to vanish!"));
            return;
        }

        boolean wasVanished = player.hasMetadata("vanished");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sv " + player.getName());
        if (wasVanished) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.sendMessage(ChatUtil.color("&eYou are cool"));
            }, 2L);
        }
    }

    private void sendPlaytimeDetails(Player sender, OfflinePlayer target) {
        long ticksPlayed = getPlayTicks(target);
        long totalHours = ticksPlayed / (20L * 60L * 60L);

        long firstJoin = target.getFirstPlayed();
        if (firstJoin <= 0L) {
            sender.sendMessage(ChatUtil.color("&7(" + totalHours + " hours)"));
            return;
        }

        Duration sinceFirstJoin = Duration.between(Instant.ofEpochMilli(firstJoin), Instant.now());
        double totalDays = Math.max(0.0D, sinceFirstJoin.toMillis() / 86_400_000.0D);
        if (totalDays > 0.0D) {
            double avgHours = Math.min(24.0D, totalHours / totalDays);
            avgHours = Math.round(avgHours * 100.0D) / 100.0D;
            sender.sendMessage(ChatUtil.color("&7(" + totalHours + " hours, " + avgHours + "h/day avg)"));
        } else {
            sender.sendMessage(ChatUtil.color("&7(" + totalHours + " hours)"));
        }
    }

    private void sendPlayTop(CommandSender sender) {
        sender.sendMessage(ChatUtil.color("&e---- &6Playtime Top &e----"));
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        List<OfflinePlayer> ordered = new ArrayList<>();
        Map<UUID, Long> cache = new HashMap<>();

        for (OfflinePlayer offlinePlayer : players) {
            ordered.add(offlinePlayer);
            cache.put(offlinePlayer.getUniqueId(), getPlayTicks(offlinePlayer));
        }

        ordered.sort(Comparator.comparingLong((OfflinePlayer p) -> cache.getOrDefault(p.getUniqueId(), 0L)).reversed());

        int rank = 1;
        for (OfflinePlayer offlinePlayer : ordered) {
            if (rank > 10) {
                break;
            }
            long ticks = cache.getOrDefault(offlinePlayer.getUniqueId(), 0L);
            long totalHours = ticks / (20L * 60L * 60L);
            long totalDays = totalHours / 24L;
            long remHours = totalHours % 24L;
            String name = offlinePlayer.getName() == null ? offlinePlayer.getUniqueId().toString() : offlinePlayer.getName();
            sender.sendMessage(ChatUtil.color("&f" + rank + ". &a" + name + "&f: &e" + totalDays
                    + " days " + remHours + " hours &7(" + totalHours + " hours)"));
            rank++;
        }
    }

    private long getPlayTicks(OfflinePlayer offlinePlayer) {
        Player online = offlinePlayer.getPlayer();
        if (online != null) {
            return online.getStatistic(Statistic.PLAY_ONE_MINUTE);
        }

        for (World world : Bukkit.getWorlds()) {
            File statsFile = new File(world.getWorldFolder(), "stats" + File.separator + offlinePlayer.getUniqueId() + ".json");
            if (!statsFile.exists()) {
                continue;
            }

            try (FileReader reader = new FileReader(statsFile)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("stats")) {
                    continue;
                }
                JsonObject stats = root.getAsJsonObject("stats");
                if (!stats.has("minecraft:custom")) {
                    continue;
                }
                JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                if (custom.has("minecraft:play_time")) {
                    return custom.get("minecraft:play_time").getAsLong();
                }
                if (custom.has("minecraft:play_one_minute")) {
                    return custom.get("minecraft:play_one_minute").getAsLong();
                }
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private boolean containsWord(String input, String word) {
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
        return pattern.matcher(input).find();
    }

    private String highlightWord(String input, String word) {
        Pattern pattern = Pattern.compile("(?i)\\b(" + Pattern.quote(word) + ")\\b");
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = ChatUtil.color("&e" + matcher.group(1) + "&r");
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private Set<UUID> getOrCreateTpBlockSet(UUID owner) {
        return tpBlocks.computeIfAbsent(owner, unused -> ConcurrentHashMap.newKeySet());
    }

    private String getTpPrefix() {
        return plugin.getConfig().getString("social.tp-block-prefix", "&7[&6TP&7]");
    }

    private String getBlockedTeleportMessage() {
        return plugin.getConfig().getString("social.tp-blocked-message",
                plugin.getConfig().getString("social.tp-blocked", "&4You aren't able to teleport to this player right now"));
    }

    private String getPunishPrefix() {
        return plugin.getConfig().getString("social.punish-prefix", "&7[&cPunish&7]");
    }

    private void loadData() {
        staffChatToggled.clear();
        tpBlocks.clear();
        activePunishments.clear();
        warpRequests.clear();

        ConfigurationSection root;
        boolean migrated = false;
        if (dataFile.exists()) {
            root = YamlConfiguration.loadConfiguration(dataFile);
            migrated = true;
        } else {
            root = plugin.getConfig().getConfigurationSection("social-data");
        }

        if (root == null) {
            saveData();
            return;
        }

        ConfigurationSection staffSection = root.getConfigurationSection("staffchat");
        if (staffSection != null) {
            for (String key : staffSection.getKeys(false)) {
                try {
                    if (staffSection.getBoolean(key, false)) {
                        staffChatToggled.add(UUID.fromString(key));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection blockSection = root.getConfigurationSection("tpblock");
        if (blockSection != null) {
            for (String ownerKey : blockSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(ownerKey);
                    Set<UUID> blocked = new HashSet<>();
                    for (String blockedKey : blockSection.getStringList(ownerKey)) {
                        try {
                            blocked.add(UUID.fromString(blockedKey));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    tpBlocks.put(owner, blocked);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection punishmentSection = root.getConfigurationSection("punishments");
        if (punishmentSection != null) {
            for (String key : punishmentSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Set<PunishmentType> loaded = ConcurrentHashMap.newKeySet();
                    for (String punishmentName : punishmentSection.getStringList(key)) {
                        PunishmentType punishmentType = PunishmentType.byName(punishmentName);
                        if (punishmentType != null) {
                            loaded.add(punishmentType);
                        }
                    }
                    if (!loaded.isEmpty()) {
                        activePunishments.put(uuid, loaded);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection warpRequestSection = root.getConfigurationSection("warp-requests");
        if (warpRequestSection != null) {
            for (String key : warpRequestSection.getKeys(false)) {
                WarpRequest request = WarpRequest.parse(key, warpRequestSection);
                if (request != null) {
                    warpRequests.put(request.id(), request);
                }
            }
        }

        if (migrated) {
            if (!dataFile.delete()) {
                plugin.getLogger().warning("Migrated social.yml into config.yml, but could not delete the old file.");
            }
            saveData();
        }
    }

    private void saveData() {
        plugin.getConfig().set("social-data.staffchat", null);
        plugin.getConfig().set("social-data.tpblock", null);
        plugin.getConfig().set("social-data.punishments", null);
        plugin.getConfig().set("social-data.warp-requests", null);

        for (UUID uuid : staffChatToggled) {
            plugin.getConfig().set("social-data.staffchat." + uuid, true);
        }

        for (Map.Entry<UUID, Set<UUID>> entry : tpBlocks.entrySet()) {
            List<String> blocked = new ArrayList<>();
            for (UUID uuid : entry.getValue()) {
                blocked.add(uuid.toString());
            }
            plugin.getConfig().set("social-data.tpblock." + entry.getKey(), blocked);
        }

        for (Map.Entry<UUID, Set<PunishmentType>> entry : activePunishments.entrySet()) {
            List<String> active = new ArrayList<>();
            for (PunishmentType punishmentType : entry.getValue()) {
                active.add(punishmentType.name());
            }
            plugin.getConfig().set("social-data.punishments." + entry.getKey(), active);
        }

        for (WarpRequest request : warpRequests.values()) {
            String base = "social-data.warp-requests." + request.id();
            plugin.getConfig().set(base + ".name", request.name());
            plugin.getConfig().set(base + ".requester-uuid", request.requesterUuid().toString());
            plugin.getConfig().set(base + ".requester-name", request.requesterName());
            plugin.getConfig().set(base + ".world", request.location().getWorld() == null ? "" : request.location().getWorld().getUID().toString());
            plugin.getConfig().set(base + ".x", request.location().getX());
            plugin.getConfig().set(base + ".y", request.location().getY());
            plugin.getConfig().set(base + ".z", request.location().getZ());
            plugin.getConfig().set(base + ".yaw", request.location().getYaw());
            plugin.getConfig().set(base + ".pitch", request.location().getPitch());
            plugin.getConfig().set(base + ".created-at", request.createdAtEpochSeconds());
        }

        plugin.saveConfig();
    }

    private record WarpRequest(UUID id, String name, UUID requesterUuid, String requesterName, Location location, long createdAtEpochSeconds) {

        private static WarpRequest parse(String key, ConfigurationSection root) {
            try {
                UUID id = UUID.fromString(key);
                String base = key + ".";
                String name = root.getString(base + "name");
                UUID requesterUuid = UUID.fromString(root.getString(base + "requester-uuid", ""));
                String requesterName = root.getString(base + "requester-name", "Unknown");
                UUID worldId = UUID.fromString(root.getString(base + "world", ""));
                World world = Bukkit.getWorld(worldId);
                if (name == null || name.isBlank() || world == null) {
                    return null;
                }

                Location location = new Location(
                        world,
                        root.getDouble(base + "x"),
                        root.getDouble(base + "y"),
                        root.getDouble(base + "z"),
                        (float) root.getDouble(base + "yaw"),
                        (float) root.getDouble(base + "pitch"));
                long createdAt = root.getLong(base + "created-at", Instant.now().getEpochSecond());
                return new WarpRequest(id, name, requesterUuid, requesterName, location, createdAt);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private static final class StaffInfoHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class WarpRequestsHolder implements InventoryHolder {
        private final Map<Integer, UUID> slotRequestIds = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum PunishmentType {
        PROJECTILE_LOCK(20, Material.CROSSBOW, "&c&lProjectile Lock", "Stop all projectile launches",
                "Cancels arrows, tridents, and other shots."),
        COMMAND_SHACKLE(22, Material.CHAIN, "&6&lCommand Shackle", "Lock movement utility commands",
                "Blocks /tp, /warp, /home, /spawn and RTP commands."),
        FOG_CURSE(24, Material.TINTED_GLASS, "&5&lFog Curse", "Short blindness pulses",
                "Applies a recurring fog effect with smoky visuals."),
        ECHO_CHAT(31, Material.GOAT_HORN, "&d&lEcho Chat", "Echoes the player's chat",
                "Duplicates each message they send in chat.");

        private final int slot;
        private final Material material;
        private final String displayName;
        private final String display;
        private final String description;
        private final String behavior;

        PunishmentType(int slot, Material material, String displayName, String description, String behavior) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
            this.display = ChatUtil.plainText(ChatUtil.component(displayName));
            this.description = description;
            this.behavior = behavior;
        }

        private static PunishmentType bySlot(int slot) {
            for (PunishmentType punishmentType : values()) {
                if (punishmentType.slot == slot) {
                    return punishmentType;
                }
            }
            return null;
        }

        private static PunishmentType byName(String name) {
            for (PunishmentType punishmentType : values()) {
                if (punishmentType.name().equalsIgnoreCase(name)) {
                    return punishmentType;
                }
            }
            return null;
        }
    }

    private static final class PunishMenuHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private Inventory inventory;

        private PunishMenuHolder(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
