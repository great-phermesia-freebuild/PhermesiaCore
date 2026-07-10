package com.greatphermesia.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.greatphermesia.core.feature.worldedit.TypereplaceModule;
import com.greatphermesia.core.feature.cosmetics.CosmeticsModule;
import com.greatphermesia.core.feature.gadgets.GadgetsModule;
import com.greatphermesia.core.feature.groupchat.GroupChatModule;
import com.greatphermesia.core.feature.help.HelpModule;
import com.greatphermesia.core.feature.itemblock.ItemBlacklistModule;
import com.greatphermesia.core.feature.religion.ReligionModule;
import com.greatphermesia.core.feature.rtp.RtpModule;
import com.greatphermesia.core.feature.social.StaffSocialModule;
import com.greatphermesia.core.module.PluginModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhermesiaCorePlugin extends JavaPlugin {

    private final List<PluginModule> modules = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateMessagesFile();

        StaffSocialModule staffSocialModule = new StaffSocialModule(this);

        modules.add(staffSocialModule);
        modules.add(new GroupChatModule(this, staffSocialModule));
        modules.add(new ItemBlacklistModule(this));
        modules.add(new ReligionModule(this));
        modules.add(new CosmeticsModule(this));
        modules.add(new GadgetsModule(this));
        modules.add(new HelpModule(this));
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            modules.add(new TypereplaceModule(this));
        } else {
            getLogger().info("[Typereplace] WorldEdit/FAWE not detected; typereplace command is disabled.");
        }
        modules.add(new RtpModule(this));

        for (PluginModule module : modules) {
            module.enable();
        }
        cleanupLegacyDataFiles();

        getLogger().info("Scrutti++ enabled. Skript migration modules are being initialized.");
    }

    @Override
    public void onDisable() {
        List<PluginModule> reversed = new ArrayList<>(modules);
        Collections.reverse(reversed);
        for (PluginModule module : reversed) {
            module.disable();
        }
        modules.clear();

        getLogger().info("Scrutti++ disabled.");
    }

    private void migrateMessagesFile() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.isFile()) {
            return;
        }

        YamlConfiguration messages = YamlConfiguration.loadConfiguration(messagesFile);
        ConfigurationSection section = messages.getConfigurationSection("");
        if (section != null) {
            copySection(section, "messages");
            saveConfig();
        }

        if (!messagesFile.delete()) {
            getLogger().warning("Migrated messages.yml into config.yml, but could not delete the old file.");
        }
    }

    private void copySection(ConfigurationSection source, String targetPath) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            String childPath = targetPath + "." + key;
            if (value instanceof ConfigurationSection child) {
                copySection(child, childPath);
            } else {
                getConfig().set(childPath, value);
            }
        }
    }

    private void cleanupLegacyDataFiles() {
        for (String fileName : List.of(
                "gadgets.yml",
                "groupchat.yml",
                "itemblock.yml",
                "messages.yml",
                "religion.yml",
                "social.yml",
                "gadget-audit.yml")) {
            File file = new File(getDataFolder(), fileName);
            if (file.isFile() && !file.delete()) {
                getLogger().warning("Could not delete legacy data file " + fileName + ".");
            }
        }
    }
}
