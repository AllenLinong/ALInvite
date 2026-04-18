package com.alinvite.listeners;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.gui.MenuSession;
import com.alinvite.gui.MenuSessionManager;
import com.alinvite.manager.GiftManager;
import com.alinvite.manager.MilestoneManager;
import com.alinvite.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MenuListener implements Listener {

    private final ALInvite plugin;
    private final ConcurrentHashMap<UUID, InputState> inputStates = new ConcurrentHashMap<>();

    public MenuListener(ALInvite plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        MenuSession session = MenuSessionManager.getInstance().getSession(player);
        
        // 检查是否是我们的菜单界面
        Inventory clickedInventory = event.getInventory();
        if (clickedInventory.getHolder() instanceof com.alinvite.gui.MenuHolder || 
            clickedInventory.getHolder() instanceof com.alinvite.gui.MenuManager.MenuHolder) {
            // 对于我们的菜单，总是取消事件
            event.setCancelled(true);
            
            if (session == null || !session.isValid()) {
                MenuSessionManager.getInstance().removeSession(player);
                return;
            }

            if (session.getInventory() != clickedInventory) {
                return;
            }
        } else {
            // 不是我们的菜单，不处理
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= clickedInventory.getSize()) {
            return;
        }

        ItemStack item = clickedInventory.getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        String menuType = session.getMenuType();
        if (isDebug()) plugin.getLogger().info("[DEBUG] Clicked - menuType: " + menuType + ", slot: " + slot);

        switch (menuType) {
            case "main" -> handleMainMenuClick(player, slot, session);
            case "veteran" -> handleVeteranMenuClick(player, slot, session);
            case "shop" -> handleShopMenuClick(player, slot, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        MenuSession session = MenuSessionManager.getInstance().getSession(player);

        // 检查是否是我们的菜单界面
        Inventory draggedInventory = event.getInventory();
        if (draggedInventory.getHolder() instanceof com.alinvite.gui.MenuHolder || 
            draggedInventory.getHolder() instanceof com.alinvite.gui.MenuManager.MenuHolder) {
            // 对于我们的菜单，总是取消事件
            event.setCancelled(true);
            
            if (session == null || !session.isValid()) {
                MenuSessionManager.getInstance().removeSession(player);
                return;
            }

            if (session.getInventory() != draggedInventory) {
                return;
            }
        } else {
            // 不是我们的菜单，不处理
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        MenuSession session = MenuSessionManager.getInstance().getSession(player);

        if (session != null) {
            MenuSessionManager.getInstance().removeSession(player);
        }
    }

    private boolean isDebug() {
        return plugin.getConfigManager().getConfig().getBoolean("debug", false);
    }

    private void handleMainMenuClick(Player player, int slot, MenuSession session) {
        if (isDebug()) plugin.getLogger().info("[DEBUG] handleMainMenuClick - slot: " + slot);
        String action = session.getAction(slot);
        if (isDebug()) plugin.getLogger().info("[DEBUG] Action for slot " + slot + ": " + action);

        if (action == null) {
            return;
        }

        switch (action) {
            case "INPUT_CODE" -> startCodeInput(player);
            case "OPEN_VETERAN" -> plugin.getMenuManager().openVeteranMenu(player);
            case "OPEN_SHOP" -> plugin.getMenuManager().openShopMenu(player);
        }
    }

    private void handleVeteranMenuClick(Player player, int slot, MenuSession session) {
        if (isDebug()) plugin.getLogger().info("[DEBUG] handleVeteranMenuClick - slot: " + slot);
        String action = session.getAction(slot);
        if (isDebug()) plugin.getLogger().info("[DEBUG] Action for slot " + slot + ": " + action);

        if (action == null) {
            return;
        }

        switch (action) {
            case "OPEN_SHOP" -> plugin.getMenuManager().openShopMenu(player);
            case "CLOSE" -> player.closeInventory();
            case "CLAIM_MILESTONE" -> handleClaimMilestone(player, slot);
        }
    }

    private void handleClaimMilestone(Player player, int slot) {
        int milestoneIndex = slot - 10;
        var milestones = plugin.getMilestoneManager().getMilestones();
        if (milestoneIndex < 0 || milestoneIndex >= milestones.size()) {
            return;
        }

        var entry = milestones.entrySet().stream().skip(milestoneIndex).findFirst().orElse(null);
        if (entry == null) return;

        int required = entry.getKey();
        MilestoneManager.Milestone milestone = entry.getValue();

        plugin.getDatabaseManager().getClaimedMilestones(player.getUniqueId()).thenAccept(claimedJson -> {
            plugin.getDatabaseManager().getPlayerData(player.getUniqueId()).thenAccept(data -> {
                int total = data != null ? data.totalInvites : 0;

                // 解析已领取的里程碑
                Set<String> claimedSet = new HashSet<>();
                if (claimedJson != null && !claimedJson.trim().isEmpty() && !claimedJson.equals("[]")) {
                    try {
                        String[] parts = claimedJson.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                claimedSet.add(part);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已领取里程碑失败: " + claimedJson);
                    }
                }

                if (total >= required) {
                    if (claimedSet.contains(String.valueOf(required))) {
                        SchedulerUtils.runTask(plugin, () ->
                            player.sendMessage(plugin.getConfigManager().getMessage("milestone.already_claimed")));
                    } else {
                        SchedulerUtils.runTask(plugin, () -> {
                            plugin.getMilestoneManager().giveRewards(player, milestone);
                            plugin.getDatabaseManager().claimMilestone(player.getUniqueId(), String.valueOf(required)).join();
                            player.sendMessage(plugin.getConfigManager().getMessage("milestone.claim_success")
                                .replace("{name}", milestone.name));
                            
                            // 发送里程碑公告
                            if (plugin.getConfigManager().getConfig().getBoolean("announcements.enabled", true)) {
                                boolean sendOnlyOnce = plugin.getConfigManager().getConfig().getBoolean("announcements.send_only_once", true);
                                if (sendOnlyOnce) {
                                    plugin.getDatabaseManager().getAnnouncedMilestones(player.getUniqueId()).thenAccept(announced -> {
                                        if (!announced.contains(String.valueOf(required))) {
                                            plugin.getMilestoneManager().sendAnnouncement(player, milestone, total);
                                            plugin.getDatabaseManager().addAnnouncedMilestone(player.getUniqueId(), String.valueOf(required));
                                        }
                                    });
                                } else {
                                    plugin.getMilestoneManager().sendAnnouncement(player, milestone, total);
                                }
                            }
                            
                            // 强制刷新当前菜单状态
                            player.closeInventory();
                            SchedulerUtils.runTaskLater(plugin, () -> {
                                plugin.getMenuManager().openVeteranMenu(player);
                            }, 1L);
                        });
                    }
                } else {
                    SchedulerUtils.runTask(plugin, () ->
                        player.sendMessage(plugin.getConfigManager().getMessage("milestone.not_unlocked")
                            .replace("{name}", milestone.name)
                            .replace("{remaining}", String.valueOf(required - total))));
                }
            });
        });
    }

    private void handleShopMenuClick(Player player, int slot, MenuSession session) {
        if (isDebug()) plugin.getLogger().info("[DEBUG] handleShopMenuClick - slot: " + slot);
        String action = session.getAction(slot);
        if (isDebug()) plugin.getLogger().info("[DEBUG] Action for slot " + slot + ": " + action);

        if (action == null) {
            return;
        }

        switch (action) {
            case "BACK_TO_VETERAN" -> plugin.getMenuManager().openVeteranMenu(player);
            case "CLOSE" -> player.closeInventory();
            case "BUY_GIFT" -> handleBuyGift(player, slot);
        }
    }

    private void handleBuyGift(Player player, int slot) {
        List<GiftManager.GiftConfig> giftList = new ArrayList<>(plugin.getGiftManager().getAllGifts().values());
        int giftIndex = slot - 10;
        if (giftIndex < 0 || giftIndex >= giftList.size()) {
            return;
        }

        GiftManager.GiftConfig gift = giftList.get(giftIndex);
        plugin.getDatabaseManager().getGiftId(player.getUniqueId()).thenAccept(currentGiftId -> {
            if (gift.id.equals(currentGiftId)) {
                player.sendMessage(plugin.getConfigManager().getMessage("commands.buygift.already_active")
                    .replace("{gift_name}", ConfigManager.colorize(gift.name)));
                return;
            }

            plugin.getDatabaseManager().getPurchasedGifts(player.getUniqueId()).thenAccept(purchasedGifts -> {
                boolean isPurchased = purchasedGifts.contains(gift.id);
                if (isPurchased) {
                    plugin.getGiftManager().switchGift(player, gift.id).thenAccept(v -> {
                        player.sendMessage(plugin.getConfigManager().getMessage("commands.buygift.switch_success")
                            .replace("{gift_name}", ConfigManager.colorize(gift.name)));
                        plugin.getMenuManager().openVeteranMenu(player);
                    });
                } else {
                    plugin.getGiftManager().buyGift(player, gift.id).thenAccept(result -> {
                        if (result.success) {
                            player.sendMessage(plugin.getConfigManager().getMessage("commands.buygift.success")
                                .replace("{gift_name}", ConfigManager.colorize(gift.name)));
                            plugin.getMenuManager().openShopMenu(player);
                        } else {
                            String reason = switch (result.type) {
                                case INSUFFICIENT_MONEY -> plugin.getConfigManager()
                                    .getMessage("commands.buygift.fail_money").replace("{price}", String.valueOf(gift.priceMoney));
                                case INSUFFICIENT_POINTS -> plugin.getConfigManager()
                                    .getMessage("commands.buygift.fail_points").replace("{price}", String.valueOf(gift.pricePoints));
                                case NOT_FOUND -> "&c礼包不存在！";
                                default -> plugin.getConfigManager().getMessage("errors.unknown");
                            };
                            player.sendMessage(reason);
                        }
                    });
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        InputState state = inputStates.get(player.getUniqueId());

        if (state == null) {
            return;
        }

        event.setCancelled(true);

        if ("CODE".equals(state.type)) {
            if (state.timestamp > 0 && System.currentTimeMillis() > state.timestamp) {
                if (state.timeoutCallback != null) {
                    state.timeoutCallback.accept(player);
                } else {
                    inputStates.remove(player.getUniqueId());
                    player.sendMessage(plugin.getConfigManager().getMessage("dialog.timeout"));
                }
                return;
            }

            String cancelKey = plugin.getConfigManager().getConfig().getString("input_dialog.cancel_key", "Q");
            String code = event.getMessage().trim();

            if (code.equalsIgnoreCase(cancelKey)) {
                player.sendMessage(plugin.getConfigManager().getMessage("dialog.cancel"));
                inputStates.remove(player.getUniqueId());
                return;
            }

            if (code.isEmpty()) {
                player.sendMessage(ConfigManager.colorize("&c邀请码不能为空！"));
                return;
            }

            plugin.getInviteManager().bindInviteCode(player, code).thenAccept(result -> {
                if (result.success) {
                    player.sendMessage(plugin.getConfigManager().getMessage("dialog.success"));
                } else {
                    String reason = switch (result.type) {
                        case NO_PERMISSION -> plugin.getConfigManager().getMessage("errors.no_permission");
                        case CODE_NOT_FOUND -> plugin.getConfigManager().getMessage("dialog.fail");
                        case ALREADY_USED -> plugin.getConfigManager().getMessage("errors.already_used");
                        case IP_LIMIT -> plugin.getConfigManager().getMessage("dialog.ip_limit");
                        case SELF_INVITE -> plugin.getConfigManager().getMessage("dialog.self_invite");
                        case VETERAN_CANNOT_BIND -> plugin.getConfigManager().getMessage("errors.veteran_cannot_bind");
                        default -> plugin.getConfigManager().getMessage("dialog.fail");
                    };
                    player.sendMessage(reason);
                }
            });
            inputStates.remove(player.getUniqueId());
        }
    }

    public void startCodeInput(Player player) {
        String usePerm = plugin.getConfigManager().getConfig()
            .getString("invite_code.use_permission", "alinvite.use");
        if (!player.hasPermission(usePerm)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        int timeoutSeconds = plugin.getConfigManager().getConfig().getInt("input_dialog.timeout", 30);
        long timeoutTimestamp = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        Consumer<Player> timeoutCallback = p -> {
            inputStates.remove(p.getUniqueId());
            p.sendMessage(plugin.getConfigManager().getMessage("dialog.timeout"));
        };

        inputStates.put(player.getUniqueId(), new InputState("CODE", timeoutTimestamp, timeoutCallback));
        player.closeInventory();
        String cancelKey = plugin.getConfigManager().getConfig().getString("input_dialog.cancel_key", "Q");
        String prompt = plugin.getConfigManager().getMessage("dialog.prompt").replace("{cancel_key}", cancelKey);
        player.sendMessage(prompt);
    }

    public void handleCodeInput(Player player, String code) {
        InputState state = inputStates.remove(player.getUniqueId());
        if (state != null && "CODE".equals(state.type)) {
            plugin.getInviteManager().bindInviteCode(player, code).thenAccept(result -> {
                if (result.success) {
                    player.sendMessage(plugin.getConfigManager().getMessage("dialog.success"));
                    SchedulerUtils.runTask(plugin, () -> {
                        plugin.getMenuManager().openMainMenu(player);
                    });
                } else {
                    String reason = switch (result.type) {
                        case NO_PERMISSION -> plugin.getConfigManager().getMessage("errors.no_permission");
                        case CODE_NOT_FOUND -> plugin.getConfigManager().getMessage("dialog.fail");
                        case ALREADY_USED -> plugin.getConfigManager().getMessage("errors.already_used");
                        case IP_LIMIT -> plugin.getConfigManager().getMessage("dialog.ip_limit");
                        case SELF_INVITE -> plugin.getConfigManager().getMessage("dialog.self_invite");
                        case VETERAN_CANNOT_BIND -> plugin.getConfigManager().getMessage("errors.veteran_cannot_bind");
                        default -> plugin.getConfigManager().getMessage("dialog.fail");
                    };
                    player.sendMessage(reason);
                }
                inputStates.remove(player.getUniqueId());
            });
        }
    }

    private static class InputState {
        final String type;
        final long timestamp;
        final Consumer<Player> timeoutCallback;

        InputState(String type) {
            this(type, -1, null);
        }

        InputState(String type, long timestamp, Consumer<Player> timeoutCallback) {
            this.type = type;
            this.timestamp = timestamp;
            this.timeoutCallback = timeoutCallback;
        }
    }
}
