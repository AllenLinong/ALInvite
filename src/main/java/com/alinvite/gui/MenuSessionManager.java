package com.alinvite.gui;

import com.alinvite.ALInvite;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuSessionManager {

    private static final MenuSessionManager instance = new MenuSessionManager();
    private final ConcurrentHashMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private ALInvite plugin;

    private MenuSessionManager() {}

    public static MenuSessionManager getInstance() {
        return instance;
    }
    
    public void setPlugin(ALInvite plugin) {
        this.plugin = plugin;
    }

    public void createSession(Player player, String menuType, MenuSession session) {
        plugin.getLogger().info("[DEBUG] createSession called for player " + player.getName() + ", menuType: " + menuType);
        plugin.getLogger().info("[DEBUG] Sessions map size before: " + sessions.size());
        sessions.put(player.getUniqueId(), session);
        plugin.getLogger().info("[DEBUG] Sessions map size after: " + sessions.size());
        plugin.getLogger().info("[DEBUG] Session stored for UUID: " + player.getUniqueId());
    }

    public MenuSession getSession(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        plugin.getLogger().info("[DEBUG] getSession for player " + player.getName() + ", session: " + (session != null ? session.getMenuType() : "null"));
        plugin.getLogger().info("[DEBUG] Sessions map size: " + sessions.size());
        plugin.getLogger().info("[DEBUG] Looking for UUID: " + player.getUniqueId());
        return session;
    }

    public MenuSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void removeSession(Player player) {
        if (plugin != null && plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Removing session for player " + player.getName());
        }
        sessions.remove(player.getUniqueId());
    }

    public void closeAll() {
        sessions.values().forEach(MenuSession::close);
        sessions.clear();
    }
}
