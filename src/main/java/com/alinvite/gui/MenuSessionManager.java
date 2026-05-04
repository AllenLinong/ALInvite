package com.alinvite.gui;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuSessionManager {

    private static final MenuSessionManager instance = new MenuSessionManager();
    private final ConcurrentHashMap<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    private MenuSessionManager() {}

    public static MenuSessionManager getInstance() {
        return instance;
    }

    public void createSession(Player player, String menuType, MenuSession session) {
        sessions.put(player.getUniqueId(), session);
    }

    public MenuSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public MenuSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void closeAll() {
        sessions.values().forEach(MenuSession::close);
        sessions.clear();
    }
}
