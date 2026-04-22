package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GiftManager {
    private final ALInvite plugin;
    private final Map<String, GiftConfig> gifts;
    private final List<String> giftOrder;

    public GiftManager(ALInvite plugin) {
        this.plugin = plugin;
        this.gifts = new LinkedHashMap<>();
        this.giftOrder = new ArrayList<>();
        loadGifts();
    }

    public static class GiftConfig {
        public final String id;
        public final String name;
        public final List<MilestoneManager.Reward> rewards;
        public final Material material;
        public final int customModelData;
        public final List<String> lore;
        public final double priceMoney;
        public final int pricePoints;

        public GiftConfig(String id, String name, List<MilestoneManager.Reward> rewards, Material material, int customModelData, List<String> lore, double priceMoney, int pricePoints) {
            this.id = id;
            this.name = name;
            this.rewards = rewards;
            this.material = material;
            this.customModelData = customModelData;
            this.lore = lore;
            this.priceMoney = priceMoney;
            this.pricePoints = pricePoints;
        }
    }

    private void loadGifts() {
        ConfigurationSection giftsConfig = plugin.getConfigManager().getConfig().getConfigurationSection("gift_shop.gifts");
        if (giftsConfig == null) return;

        // 清空现有数据
        gifts.clear();
        giftOrder.clear();

        // 按照配置文件中的顺序加载礼包
        for (String giftId : giftsConfig.getKeys(false)) {
            ConfigurationSection giftConfig = giftsConfig.getConfigurationSection(giftId);
            if (giftConfig != null) {
                String name = giftConfig.getString("name", "礼包");
                List<MilestoneManager.Reward> rewards = parseRewards(giftConfig.getStringList("rewards"));
                Material material = Material.valueOf(giftConfig.getString("material", "CHEST"));
                int customModelData = giftConfig.getInt("custom_model_data", 0);
                List<String> lore = giftConfig.getStringList("lore");
                double priceMoney = giftConfig.getDouble("price_money", 0.0);
                int pricePoints = giftConfig.getInt("price_points", 0);
                gifts.put(giftId, new GiftConfig(giftId, name, rewards, material, customModelData, lore, priceMoney, pricePoints));
                giftOrder.add(giftId);
            }
        }
    }

    private List<MilestoneManager.Reward> parseRewards(List<String> rewardStrings) {
        List<MilestoneManager.Reward> rewards = new ArrayList<>();
        for (String rewardStr : rewardStrings) {
            String[] parts = rewardStr.split(" ", 2);
            if (parts.length >= 2) {
                String type = parts[0];
                String value = parts[1];
                rewards.add(new MilestoneManager.Reward(type, value));
            }
        }
        return rewards;
    }

    public void giveGiftRewards(Player player, UUID inviterUuid) {
        // 检查玩家是否启用了礼包功能
        plugin.getDatabaseManager().getPlayerData(player.getUniqueId()).thenAccept(playerData -> {
            if (playerData != null && !playerData.giftEnabled) {
                // 玩家礼包功能被禁用，不发放礼包
                return;
            }
            
            plugin.getDatabaseManager().getGiftId(inviterUuid).thenAccept(giftId -> {
                String defaultGiftId = plugin.getConfigManager().getConfig()
                    .getString("new_player_reward.default_gift_id", "default");
                boolean requireGift = plugin.getConfigManager().getConfig()
                    .getBoolean("new_player_reward.require_gift", false);

                String finalGiftId = giftId;
                if (finalGiftId == null) {
                    if (requireGift) {
                        return;
                    }
                    finalGiftId = defaultGiftId;
                }

                GiftConfig gift = gifts.get(finalGiftId);
                if (gift == null) {
                    gift = gifts.get(defaultGiftId);
                }

                if (gift == null) {
                    return;
                }

                for (MilestoneManager.Reward reward : gift.rewards) {
                    switch (reward.type) {
                        case "command" -> {
                            String command = reward.value.toString()
                                .replace("%player%", player.getName());
                            SchedulerUtils.runTask(plugin, () ->
                                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
                        }
                        case "money" -> {
                            net.milkbowl.vault.economy.Economy economy = plugin.getServer().getServicesManager()
                                .getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
                            if (economy != null) {
                                economy.depositPlayer(player, Double.parseDouble(reward.value.toString()));
                            }
                        }
                        case "points" -> {
                            givePoints(player, reward.value);
                        }
                        case "item" -> {
                            giveItem(player, reward.value);
                        }
                    }
                }
            });
        });
    }

    private void givePoints(Player player, Object value) {
        // 实现点数发放逻辑
        String pointsStr = value.toString();
        try {
            int points = Integer.parseInt(pointsStr);
            // 这里需要根据你的点数系统实现具体逻辑
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("无效的点数格式: " + pointsStr);
        }
    }

    private void giveItem(Player player, Object value) {
        String[] parts = value.toString().split(" ");
        if (parts.length >= 2) {
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                ItemStack item = new ItemStack(mat, amount);
                
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack leftItem : leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftItem);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的物品格式: " + value);
            }
        }
    }

    public Map<String, GiftConfig> getGifts() {
        return gifts;
    }

    public GiftConfig getGift(String giftId) {
        return gifts.get(giftId);
    }

    public Map<String, GiftConfig> getAllGifts() {
        return gifts;
    }

    public CompletableFuture<Void> switchGift(Player player, String giftId) {
        return plugin.getDatabaseManager().setGiftId(player.getUniqueId(), giftId).thenAccept(v -> {});
    }

    public enum BuyGiftResultType {
        INSUFFICIENT_MONEY,
        INSUFFICIENT_POINTS,
        NOT_FOUND,
        SUCCESS
    }

    public static class BuyGiftResult {
        public final boolean success;
        public final BuyGiftResultType type;

        public BuyGiftResult(boolean success, BuyGiftResultType type) {
            this.success = success;
            this.type = type;
        }
    }

    public CompletableFuture<BuyGiftResult> buyGift(Player player, String giftId) {
        return CompletableFuture.supplyAsync(() -> {
            GiftConfig gift = gifts.get(giftId);
            if (gift == null) {
                return new BuyGiftResult(false, BuyGiftResultType.NOT_FOUND);
            }

            return SchedulerUtils.runTaskSupplied(plugin, () -> {
                net.milkbowl.vault.economy.Economy economy = plugin.getServer().getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();

                if (economy != null && gift.priceMoney > 0) {
                    if (!economy.has(player, gift.priceMoney)) {
                        return new BuyGiftResult(false, BuyGiftResultType.INSUFFICIENT_MONEY);
                    }
                    economy.withdrawPlayer(player, gift.priceMoney);
                }

                if (gift.pricePoints > 0) {
                    boolean enoughPoints = hasEnoughPoints(player, gift.pricePoints);
                    if (!enoughPoints) {
                        if (economy != null && gift.priceMoney > 0) {
                            economy.depositPlayer(player, gift.priceMoney);
                        }
                        return new BuyGiftResult(false, BuyGiftResultType.INSUFFICIENT_POINTS);
                    }
                    takePoints(player, gift.pricePoints);
                }

                plugin.getDatabaseManager().addPurchasedGift(player.getUniqueId(), giftId).join();
                switchGift(player, giftId).join();

                return new BuyGiftResult(true, BuyGiftResultType.SUCCESS);
            });
        });
    }

    private boolean hasEnoughPoints(Player player, int amount) {
        String pointsType = plugin.getConfigManager().getConfig()
            .getString("economy.points_type", "NONE");
        if ("NONE".equals(pointsType)) return true;

        switch (pointsType) {
            case "PLAYERPOINTS" -> {
                try {
                    Object api = getPlayerPointsAPI();
                    if (api == null) {
                        return false;
                    }

                    int playerPoints = getPlayerPoints(api, player);
                    return playerPoints >= amount;
                } catch (Exception e) {
                    plugin.getLogger().warning("PlayerPoints 检查失败: " + e.getClass().getName() + ": " + e.getMessage());
                    return false;
                }
            }
            case "CUSTOM" -> {
                return true;
            }
            default -> {
                plugin.getLogger().warning("未知的点券类型: " + pointsType + "，请检查配置文件中的 economy.points_type");
                return false;
            }
        }
    }

    private int getPlayerPoints(Object api, Player player) {
        try {
            java.lang.reflect.Method lookMethod = api.getClass().getMethod("look", java.util.UUID.class);
            Object result = lookMethod.invoke(api, player.getUniqueId());
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
            return 0;
        } catch (Exception e) {
            plugin.getLogger().warning("获取玩家点券失败: " + e.getClass().getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private void takePoints(Player player, int amount) {
        String pointsType = plugin.getConfigManager().getConfig()
            .getString("economy.points_type", "NONE");
        if ("NONE".equals(pointsType)) return;

        plugin.getLogger().info("[DEBUG] 正在扣除点券: " + player.getName() + ", 数量: " + amount + ", 类型: " + pointsType);

        switch (pointsType) {
            case "CUSTOM" -> {
                String takeCmd = plugin.getConfigManager().getConfig()
                    .getString("economy.points_command.take", "")
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));
                plugin.getLogger().info("[DEBUG] 执行命令: " + takeCmd);
                SchedulerUtils.runTask(plugin, () ->
                    org.bukkit.Bukkit.getServer().dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), takeCmd));
            }
            case "PLAYERPOINTS" -> {
                try {
                    Object api = getPlayerPointsAPI();
                    if (api == null) return;
                    java.lang.reflect.Method takeMethod = api.getClass().getMethod("take", java.util.UUID.class, int.class);
                    takeMethod.invoke(api, player.getUniqueId(), amount);
                } catch (Exception e) {
                    plugin.getLogger().warning("PlayerPoints 扣除失败: " + e.getMessage());
                }
            }
        }
    }

    private Object getPlayerPointsAPI() {
        try {
            Class<?> ppClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object ppInstance = ppClass.getMethod("getInstance").invoke(null);
            if (ppInstance == null) {
                plugin.getLogger().warning("PlayerPoints.getInstance() 返回 null！");
                plugin.getLogger().warning("PlayerPoints 插件是否正确加载？");
                return null;
            }
            Object api = ppClass.getMethod("getAPI").invoke(ppInstance);
            if (api == null) {
                plugin.getLogger().warning("PlayerPoints.getAPI() 返回 null！");
                return null;
            }
            return api;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("找不到 PlayerPoints 类！请确保 PlayerPoints 插件已安装。");
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("获取 PlayerPoints API 失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}