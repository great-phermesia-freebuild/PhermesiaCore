package me.greatphermesia.core.feature.groupchat;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.greatphermesia.core.PhermesiaCorePlugin;
import me.greatphermesia.core.feature.social.StaffSocialModule;
import me.greatphermesia.core.module.PluginModule;
import me.greatphermesia.core.util.ChatUtil;
import org.bukkit.Bukkit;
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
import io.papermc.paper.event.player.AsyncChatEvent;

public final class GroupChatModule implements PluginModule, Listener, CommandExecutor {

    private final PhermesiaCorePlugin plugin;
    private final StaffSocialModule staffSocialModule;
    private final Map<String, GroupChatData> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGroups = new ConcurrentHashMap<>();
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();
    private File dataFile;
    private int cleanupTaskId = -1;

    public GroupChatModule(PhermesiaCorePlugin plugin, StaffSocialModule staffSocialModule) {
        this.plugin = plugin;
        this.staffSocialModule = staffSocialModule;
    }

    @Override
    public String name() {
        return "GroupChat";
    }

    @Override
    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "groupchat.yml");
        loadData();

        if (plugin.getCommand("gc") != null) {
            plugin.getCommand("gc").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::cleanupInactiveGroups,
                20L * 60L * 5L, 20L * 60L * 5L);

        plugin.getLogger().info("[GroupChat] Module enabled.");
    }

    @Override
    public void disable() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        saveData();
        plugin.getLogger().info("[GroupChat] Module stopped.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (args.length == 0) {
            if (!playerGroups.containsKey(uuid)) {
                player.sendMessage(prefix() + ChatUtil.color(" &cYou're not in a group chat!"));
                player.sendMessage(prefix() + ChatUtil.color(" &7Use /gc info for help."));
                return true;
            }

            if (toggled.remove(uuid)) {
                player.sendMessage(prefix() + ChatUtil.color(" &7Group chat disabled. Messages will go to global chat."));
            } else {
                toggled.add(uuid);
                player.sendMessage(prefix() + ChatUtil.color(" &aGroup chat enabled. Messages will go to your group chat."));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                player.sendMessage(prefix() + ChatUtil.color(" &7Usage:"));
                player.sendMessage(ChatUtil.color("&7/gc &8- &7Toggle between group chat and global chat"));
                player.sendMessage(ChatUtil.color("&7/gc create <code> &8- &7Create a group chat"));
                player.sendMessage(ChatUtil.color("&7/gc join <code> &8- &7Join a group chat"));
                player.sendMessage(ChatUtil.color("&7/gc leave &8- &7Leave your current group chat"));
                player.sendMessage(ChatUtil.color("&7/gc delete &8- &7Delete your group chat (leader only)"));
                player.sendMessage(ChatUtil.color("&7/gc list &8- &7List members in your group chat"));
                return true;
            }
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cPlease specify a code for your group chat!"));
                    player.sendMessage(prefix() + ChatUtil.color(" &7Usage: /gc create <code>"));
                    return true;
                }
                String code = args[1];
                if (playerGroups.containsKey(uuid)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cYou're already in a group chat! Leave it first with /gc leave"));
                    return true;
                }
                if (groups.containsKey(code)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cA group chat with that code already exists!"));
                    return true;
                }

                GroupChatData data = new GroupChatData(uuid);
                data.members.add(uuid);
                data.lastUsed = Instant.now();
                groups.put(code, data);
                playerGroups.put(uuid, code);
                toggled.add(uuid);

                player.sendMessage(prefix() + ChatUtil.color(" &aGroup chat created with code: &f" + code));
                player.sendMessage(prefix() + ChatUtil.color(" &7Others can join with: &f/gc join " + code));
                player.sendMessage(prefix() + ChatUtil.color(" &7Your messages will now go to the group chat. Use /gc to toggle."));
                saveData();
                return true;
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cPlease specify a group chat code!"));
                    player.sendMessage(prefix() + ChatUtil.color(" &7Usage: /gc join <code>"));
                    return true;
                }
                String code = args[1];
                if (playerGroups.containsKey(uuid)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cYou're already in a group chat! Leave it first with /gc leave"));
                    return true;
                }

                GroupChatData data = groups.get(code);
                if (data == null) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cThat group chat doesn't exist!"));
                    return true;
                }

                data.members.add(uuid);
                data.lastUsed = Instant.now();
                playerGroups.put(uuid, code);
                toggled.add(uuid);

                player.sendMessage(prefix() + ChatUtil.color(" &aYou joined the group chat!"));
                player.sendMessage(prefix() + ChatUtil.color(" &7Your messages will now go to the group chat. Use /gc to toggle."));
                for (UUID memberUuid : data.members) {
                    if (memberUuid.equals(uuid)) {
                        continue;
                    }
                    Player online = Bukkit.getPlayer(memberUuid);
                    if (online != null && online.isOnline()) {
                        online.sendMessage(prefix() + ChatUtil.color(" &e" + player.getName() + " &7joined the group chat!"));
                        online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.3f);
                    }
                }
                saveData();
                return true;
            }
            case "leave" -> {
                handleLeave(player);
                return true;
            }
            case "delete" -> {
                if (!playerGroups.containsKey(uuid)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cYou're not in a group chat!"));
                    return true;
                }

                String code = playerGroups.get(uuid);
                GroupChatData data = groups.get(code);
                if (data == null) {
                    cleanupPlayerState(uuid);
                    player.sendMessage(prefix() + ChatUtil.color(" &cYour group chat no longer exists."));
                    saveData();
                    return true;
                }

                if (!uuid.equals(data.leader)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cOnly the group chat leader can delete it!"));
                    return true;
                }

                disbandGroup(code, "&cThe group chat was disbanded by the leader!");
                player.sendMessage(prefix() + ChatUtil.color(" &7Group chat deleted."));
                saveData();
                return true;
            }
            case "list" -> {
                if (!playerGroups.containsKey(uuid)) {
                    player.sendMessage(prefix() + ChatUtil.color(" &cYou're not in a group chat!"));
                    return true;
                }
                String code = playerGroups.get(uuid);
                GroupChatData data = groups.get(code);
                if (data == null) {
                    cleanupPlayerState(uuid);
                    player.sendMessage(prefix() + ChatUtil.color(" &cYour group chat no longer exists."));
                    saveData();
                    return true;
                }

                player.sendMessage(prefix() + ChatUtil.color(" &7Members in your group chat:"));
                for (UUID memberUuid : data.members) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
                    String name = offlinePlayer.getName() == null ? memberUuid.toString() : offlinePlayer.getName();
                    if (memberUuid.equals(data.leader)) {
                        player.sendMessage(ChatUtil.color("&8- &f" + name + " &7(Leader)"));
                    } else {
                        player.sendMessage(ChatUtil.color("&8- &f" + name));
                    }
                }
                return true;
            }
            default -> {
                player.sendMessage(prefix() + ChatUtil.color(" &cUnknown subcommand! Use /gc info for help."));
                return true;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGroupChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String code = playerGroups.get(uuid);
        if (code == null || !toggled.contains(uuid)) {
            return;
        }

        if (staffSocialModule != null && staffSocialModule.isStaffChatEnabled(uuid)) {
            return;
        }

        GroupChatData data = groups.get(code);
        if (data == null) {
            cleanupPlayerState(uuid);
            saveData();
            return;
        }

        event.setCancelled(true);
        data.lastUsed = Instant.now();
        String message = ChatUtil.plainText(event.message());

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getConsoleSender().sendMessage("[GC:" + code + "] " + player.getName() + ": " + message);
            for (UUID memberUuid : data.members) {
                Player online = Bukkit.getPlayer(memberUuid);
                if (online != null && online.isOnline()) {
                    online.sendMessage(ChatUtil.color("&8[&bGC&8] &f" + player.getName() + "&7: &f" + message));
                }
            }
        });
    }

    private void handleLeave(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerGroups.containsKey(uuid)) {
            player.sendMessage(prefix() + ChatUtil.color(" &cYou're not in a group chat!"));
            return;
        }

        String code = playerGroups.get(uuid);
        GroupChatData data = groups.get(code);
        if (data == null) {
            cleanupPlayerState(uuid);
            player.sendMessage(prefix() + ChatUtil.color(" &7You left the group chat."));
            saveData();
            return;
        }

        for (UUID memberUuid : data.members) {
            if (memberUuid.equals(uuid)) {
                continue;
            }
            Player online = Bukkit.getPlayer(memberUuid);
            if (online != null && online.isOnline()) {
                online.sendMessage(prefix() + ChatUtil.color(" &e" + player.getName() + " &7left the group chat!"));
            }
        }

        data.members.remove(uuid);
        cleanupPlayerState(uuid);
        player.sendMessage(prefix() + ChatUtil.color(" &7You left the group chat."));

        if (uuid.equals(data.leader)) {
            disbandGroup(code, "&cThe group chat was disbanded by the leader!");
        } else if (data.members.isEmpty()) {
            groups.remove(code);
        }
        saveData();
    }

    private void cleanupInactiveGroups() {
        Duration inactivityLimit = Duration.ofMinutes(plugin.getConfig().getLong("groupchat.inactivity-minutes", 60L));
        Instant now = Instant.now();
        List<String> toDisband = new ArrayList<>();

        for (Map.Entry<String, GroupChatData> entry : groups.entrySet()) {
            GroupChatData data = entry.getValue();
            if (data.lastUsed == null) {
                data.lastUsed = now;
                continue;
            }
            if (Duration.between(data.lastUsed, now).compareTo(inactivityLimit) > 0) {
                toDisband.add(entry.getKey());
            }
        }

        if (toDisband.isEmpty()) {
            return;
        }

        for (String code : toDisband) {
            disbandGroup(code, "&cYour group chat was disbanded due to inactivity.");
        }
        saveData();
    }

    private void disbandGroup(String code, String message) {
        GroupChatData data = groups.remove(code);
        if (data == null) {
            return;
        }

        for (UUID memberUuid : new HashSet<>(data.members)) {
            cleanupPlayerState(memberUuid);
            Player online = Bukkit.getPlayer(memberUuid);
            if (online != null && online.isOnline()) {
                online.sendMessage(prefix() + ChatUtil.color(" " + message));
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
        }
    }

    private void cleanupPlayerState(UUID uuid) {
        playerGroups.remove(uuid);
        toggled.remove(uuid);
    }

    private String prefix() {
        return ChatUtil.color(plugin.getConfig().getString("groupchat.prefix", "&8[&bGC&8]&r"));
    }

    private void loadData() {
        groups.clear();
        playerGroups.clear();
        toggled.clear();

        ConfigurationSection root;
        boolean migrated = false;
        if (dataFile.exists()) {
            root = YamlConfiguration.loadConfiguration(dataFile);
            migrated = true;
        } else {
            root = plugin.getConfig().getConfigurationSection("groupchat-data");
        }

        if (root == null) {
            saveData();
            return;
        }

        ConfigurationSection groupsSection = root.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String code : groupsSection.getKeys(false)) {
                ConfigurationSection section = groupsSection.getConfigurationSection(code);
                if (section == null) {
                    continue;
                }

                String leaderText = section.getString("leader");
                if (leaderText == null) {
                    continue;
                }
                UUID leader;
                try {
                    leader = UUID.fromString(leaderText);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                GroupChatData data = new GroupChatData(leader);
                long lastUsedEpoch = section.getLong("last-used", System.currentTimeMillis());
                data.lastUsed = Instant.ofEpochMilli(lastUsedEpoch);
                for (String memberText : section.getStringList("members")) {
                    try {
                        data.members.add(UUID.fromString(memberText));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                groups.put(code, data);
            }
        }

        ConfigurationSection playerSection = root.getConfigurationSection("player-groups");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String code = playerSection.getString(key);
                    if (code != null && groups.containsKey(code)) {
                        playerGroups.put(uuid, code);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (String toggleUuid : root.getStringList("toggled")) {
            try {
                UUID uuid = UUID.fromString(toggleUuid);
                if (playerGroups.containsKey(uuid)) {
                    toggled.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (migrated) {
            if (!dataFile.delete()) {
                plugin.getLogger().warning("Migrated groupchat.yml into config.yml, but could not delete the old file.");
            }
            saveData();
        }
    }

    private void saveData() {
        plugin.getConfig().set("groupchat-data.groups", null);
        plugin.getConfig().set("groupchat-data.player-groups", null);
        plugin.getConfig().set("groupchat-data.toggled", null);

        for (Map.Entry<String, GroupChatData> entry : groups.entrySet()) {
            String code = entry.getKey();
            GroupChatData data = entry.getValue();
            plugin.getConfig().set("groupchat-data.groups." + code + ".leader", data.leader.toString());
            plugin.getConfig().set("groupchat-data.groups." + code + ".last-used", data.lastUsed.toEpochMilli());

            List<String> members = new ArrayList<>();
            for (UUID member : data.members) {
                members.add(member.toString());
            }
            plugin.getConfig().set("groupchat-data.groups." + code + ".members", members);
        }

        for (Map.Entry<UUID, String> entry : playerGroups.entrySet()) {
            plugin.getConfig().set("groupchat-data.player-groups." + entry.getKey(), entry.getValue());
        }

        List<String> toggledList = new ArrayList<>();
        for (UUID uuid : toggled) {
            toggledList.add(uuid.toString());
        }
        plugin.getConfig().set("groupchat-data.toggled", toggledList);

        plugin.saveConfig();
    }

    private static final class GroupChatData {
        private final UUID leader;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private volatile Instant lastUsed = Instant.now();

        private GroupChatData(UUID leader) {
            this.leader = leader;
        }
    }
}
