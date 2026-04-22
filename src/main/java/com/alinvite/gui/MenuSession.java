package com.alinvite.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

public class MenuSession {

    private final UUID playerId;
    private final String menuType;
    private final Player player;
    private final MenuManager.MenuConfig menuConfig;
    private final Map<Integer, String> slotActions;

    public MenuSession(Player player, String menuType, MenuManager.MenuConfig menuConfig, Map<Integer, String> slotActions) {
        this.playerId = player.getUniqueId();
        this.player = player;
        this.menuType = menuType;
        this.menuConfig = menuConfig;
        this.slotActions = slotActions;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getMenuType() {
        return menuType;
    }

    public MenuManager.MenuConfig getMenuConfig() {
        return menuConfig;
    }

    public String getAction(int slot) {
        return slotActions.get(slot);
    }

    public Inventory getInventory() {
        return player.getOpenInventory().getTopInventory();
    }

    public boolean isValid() {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return player.getOpenInventory().getTopInventory() != null;
    }

    public void close() {
    }
}
