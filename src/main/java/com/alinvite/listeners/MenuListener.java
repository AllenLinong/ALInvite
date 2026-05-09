package com.alinvite.listeners;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.gui.MenuManager;
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
        
        // 检查是否是我们的菜单界面 - 通过 holder 检查
        Inventory clickedInventory = event.getInventory();
        boolean hasValidHolder = clickedInventory.getHolder() instanceof com.alinvite.gui.MenuHolder;
        
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG] onInventoryClick - player: " + player.getName());
            plugin.getLogger().info("[DEBUG] hasValidHolder: " + hasValidHolder);
            plugin.getLogger().info("[DEBUG] session != null: " + (session != null));
            if (session != null) {
                plugin.getLogger().info("[DEBUG] session menuType: " + session.getMenuType());
                plugin.getLogger().info("[DEBUG] session.isValid(): " + session.isValid());
            }
            plugin.getLogger().info("[DEBUG] clickedInventory: " + clickedInventory);
            plugin.getLogger().info("[DEBUG] event.getRawSlot(): " + event.getRawSlot());
            plugin.getLogger().info("[DEBUG] event.getSlot(): " + event.getSlot());
        }
        
        // 如果玩家打开了我们的菜单，取消所有点击事件（防止操作玩家背包）
        if (hasValidHolder) {
            event.setCancelled(true);
            
            // 检查 session 是否有效
            if (session == null || !session.isValid()) {
                if (isDebug()) plugin.getLogger().info("[DEBUG] session is null or invalid, removing");
                MenuSessionManager.getInstance().removeSession(player);
                return;
            }
            
            // 使用 rawSlot 来确定点击位置，确保点击在菜单区域内
            int rawSlot = event.getRawSlot();
            // 检查点击是否发生在菜单库存区域（rawSlot < 菜单大小）
            if (rawSlot < 0 || rawSlot >= clickedInventory.getSize()) {
                if (isDebug()) plugin.getLogger().info("[DEBUG] slot out of bounds: " + rawSlot + ", menu size: " + clickedInventory.getSize());
                return;
            }

            ItemStack item = clickedInventory.getItem(rawSlot);
            if (item == null || item.getType() == Material.AIR) {
                if (isDebug()) plugin.getLogger().info("[DEBUG] item is null or air at slot " + rawSlot);
                return;
            }

            String menuType = session.getMenuType();
            if (isDebug()) plugin.getLogger().info("[DEBUG] Clicked - menuType: " + menuType + ", slot: " + rawSlot);

            switch (menuType) {
                case "main" -> handleMainMenuClick(player, rawSlot, session);
                case "veteran" -> handleVeteranMenuClick(player, rawSlot, session);
                case "shop" -> handleShopMenuClick(player, rawSlot, session);
            }
            return;
        }
        
        // 如果不是我们的菜单但玩家有打开的菜单会话，也取消点击（阻止操作背包）
        if (session != null && session.isValid()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        MenuSession session = MenuSessionManager.getInstance().getSession(player);

        // 检查是否是我们的菜单界面 - 先检查 session
        if (session != null && session.isValid()) {
            // 对于我们的菜单，总是取消事件
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 暂时不移除 session，让它自然过期或被新菜单覆盖
        // 移除 session 会导致菜单按钮点击时找不到 session
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
            case "OPEN_MAIN" -> plugin.getMenuManager().openMainMenu(player);
            case "CLOSE" -> player.closeInventory();
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
            case "OPEN_MAIN" -> plugin.getMenuManager().openMainMenu(player);
            case "CLOSE" -> player.closeInventory();
            case "CLAIM_MILESTONE" -> handleClaimMilestone(player, slot);
            case "PREV_PAGE" -> handlePrevPage(player, session);
            case "NEXT_PAGE" -> handleNextPage(player, session);
        }
    }

    private void handlePrevPage(Player player, MenuSession session) {
        int currentPage = session.getPage();
        if (currentPage > 0) {
            session.prevPage();
            // 根据菜单类型打开相应的菜单
            String menuType = session.getMenuType();
            switch (menuType) {
                case "veteran" -> plugin.getMenuManager().openVeteranMenu(player);
                case "shop" -> plugin.getMenuManager().openShopMenu(player);
            }
        }
    }

    private void handleNextPage(Player player, MenuSession session) {
        int currentPage = session.getPage();
        String menuType = session.getMenuType();
        
        // 根据菜单类型计算总页数
        int totalPages;
        if ("veteran".equals(menuType)) {
            int totalMilestones = plugin.getMilestoneManager().getMilestones().size();
            int milestonesPerPage = 14;
            totalPages = (int) Math.ceil((double) totalMilestones / milestonesPerPage);
        } else if ("shop".equals(menuType)) {
            int totalGifts = plugin.getGiftManager().getAllGifts().size();
            int giftsPerPage = 14;
            totalPages = (int) Math.ceil((double) totalGifts / giftsPerPage);
        } else {
            totalPages = 1;
        }
        // 确保总页数至少为1
        totalPages = Math.max(totalPages, 1);
        
        if (currentPage < totalPages - 1) {
            session.nextPage();
            // 根据菜单类型打开相应的菜单
            switch (menuType) {
                case "veteran" -> plugin.getMenuManager().openVeteranMenu(player);
                case "shop" -> plugin.getMenuManager().openShopMenu(player);
            }
        }
    }

    private void handleClaimMilestone(Player player, int slot) {
        // 获取当前页码
        MenuSession session = MenuSessionManager.getInstance().getSession(player);
        int page = session != null ? session.getPage() : 0;
        
        // 计算每页显示的里程碑数量和当前页的里程碑
        int milestonesPerPage = 14;
        var milestones = plugin.getMilestoneManager().getMilestones();
        List<Map.Entry<Integer, MilestoneManager.Milestone>> milestoneList = new ArrayList<>(milestones.entrySet());
        int startIndex = page * milestonesPerPage;
        
        // 收集所有 M 位置的槽位
        List<Integer> milestoneSlots = new ArrayList<>();
        MenuManager.MenuConfig config = session.getMenuConfig();
        List<String> currentShape = page == 0 ? config.shape : (config.shape2 != null && !config.shape2.isEmpty() ? config.shape2 : config.shape);
        int shapeSlot = 0;
        for (String row : currentShape) {
            for (char c : row.toCharArray()) {
                if (c == 'M') {
                    milestoneSlots.add(shapeSlot);
                }
                shapeSlot++;
            }
        }
        
        // 找到点击的槽位在里程碑槽位列表中的索引
        int slotIndex = milestoneSlots.indexOf(slot);
        if (slotIndex == -1) {
            return;
        }
        
        // 计算实际的里程碑索引
        int milestoneIndex = startIndex + slotIndex;
        if (milestoneIndex < 0 || milestoneIndex >= milestoneList.size()) {
            return;
        }

        var entry = milestoneList.get(milestoneIndex);
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
            case "OPEN_MAIN" -> plugin.getMenuManager().openMainMenu(player);
            case "CLOSE" -> player.closeInventory();
            case "BUY_GIFT" -> handleBuyGift(player, slot);
            case "PREV_PAGE" -> handlePrevPage(player, session);
            case "NEXT_PAGE" -> handleNextPage(player, session);
        }
    }

    private void handleBuyGift(Player player, int slot) {
        // 获取当前页码
        MenuSession session = MenuSessionManager.getInstance().getSession(player);
        int page = session != null ? session.getPage() : 0;
        
        // 计算每页显示的礼包数量和当前页的礼包
        int giftsPerPage = 14;
        List<GiftManager.GiftConfig> giftList = new ArrayList<>(plugin.getGiftManager().getAllGifts().values());
        int startIndex = page * giftsPerPage;
        
        // 收集所有 G 位置的槽位
        List<Integer> giftSlots = new ArrayList<>();
        MenuManager.MenuConfig config = session.getMenuConfig();
        List<String> currentShape = page == 0 ? config.shape : (config.shape2 != null && !config.shape2.isEmpty() ? config.shape2 : config.shape);
        int shapeSlot = 0;
        for (String row : currentShape) {
            for (char c : row.toCharArray()) {
                if (c == 'G') {
                    giftSlots.add(shapeSlot);
                }
                shapeSlot++;
            }
        }
        
        // 找到点击的槽位在礼包槽位列表中的索引
        int slotIndex = giftSlots.indexOf(slot);
        if (slotIndex == -1) {
            return;
        }
        
        // 计算实际的礼包索引
        int giftIndex = startIndex + slotIndex;
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
                                case NO_PERMISSION -> plugin.getConfigManager()
                                    .getMessage("commands.buygift.no_permission");
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
    
    private void handleLeaderboardMenuClick(Player player, int slot, InventoryClickEvent event) {
        ItemStack item = event.getInventory().getItem(slot);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 处理返回按钮
        if (displayName.contains("返回")) {
            // 返回主菜单
            plugin.getMenuManager().openMainMenu(player);
        }
    }
}
