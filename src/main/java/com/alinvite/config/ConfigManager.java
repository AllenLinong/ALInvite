package com.alinvite.config;

import com.alinvite.ALInvite;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {

    private final ALInvite plugin;
    private FileConfiguration config;
    private FileConfiguration menusConfig;
    private FileConfiguration langConfig;

    private File configFile;
    private File menusFile;
    private File langFile;

    public ConfigManager(ALInvite plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadConfig();
        loadMenus();
        loadLang();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadMenus() {
        menusFile = new File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        menusConfig = YamlConfiguration.loadConfiguration(menusFile);
    }

    private void loadLang() {
        String locale = config.getString("language.locale", "zh_cn");
        String langFileName = locale + ".yml";

        File languagesFolder = new File(plugin.getDataFolder(), "languages");
        langFile = new File(languagesFolder, langFileName);

        if (!langFile.exists()) {
            if (!languagesFolder.exists()) {
                languagesFolder.mkdirs();
            }
            plugin.saveResource("languages/" + langFileName, false);
        }

        if (!langFile.exists()) {
            langFile = new File(languagesFolder, "zh_cn.yml");
            if (!langFile.exists()) {
                plugin.saveResource("languages/zh_cn.yml", false);
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 config.yml: " + e.getMessage());
        }
    }

    public void saveMenus() {
        try {
            menusConfig.save(menusFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 menus.yml: " + e.getMessage());
        }
    }

    public void saveLang() {
        try {
            langConfig.save(langFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 lang.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMenusConfig() {
        return menusConfig;
    }

    public FileConfiguration getLangConfig() {
        return langConfig;
    }

    public String getMessage(String path) {
        String prefix = langConfig.getString("prefix", config.getString("prefix", "&6[ALInvite] &r"));
        String msg = langConfig.getString(path, "&cMessage not found: " + path);
        return colorize(prefix + msg);
    }

    public String getMessageRaw(String path) {
        return colorize(langConfig.getString(path, "&cMessage not found: " + path));
    }

    public List<String> getMessageList(String path) {
        List<String> list = langConfig.getStringList(path);
        String prefix = langConfig.getString("prefix", config.getString("prefix", "&6[ALInvite] &r"));
        return list.stream().map(s -> colorize(prefix + s)).toList();
    }

    public static String colorize(String text) {
        if (text == null) return "";
        text = text.replace("&", "§");
        try {
            net.kyori.adventure.text.Component component = miniMessage.deserialize(text);
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
        } catch (Exception e) {
            return text;
        }
    }

    public static net.kyori.adventure.text.Component miniMessage(String text) {
        if (text == null) return net.kyori.adventure.text.Component.empty();
        text = text.replace("&", "§");
        try {
            return miniMessage.deserialize(text);
        } catch (Exception e) {
            return net.kyori.adventure.text.Component.text(text);
        }
    }

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
}
