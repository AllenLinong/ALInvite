package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.utils.SchedulerUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GiftManager {

    private final ALInvite plugin;
    private final Map<String, GiftConfig> gifts;

    public GiftManager(ALInvite plugin) {
        this.plugin = plugin;
        this.gifts = new LinkedHashMap<>();
        loadGifts();
    }

    private boolean isDebug() {
        return plugin.getConfigManager().getConfig().getBoolean("debug", false);
    }

    private void loadGifts() {
        if (!plugin.getConfigManager().getConfig().getBoolean("gift_shop.enabled", true)) {
            return;
        }

        Objects.requireNonNull(plugin.getConfigManager().getConfig().getConfigurationSection("gift_shop.gifts"))
            .getKeys(false)
            .forEach(giftId -> {
                String path = "gift_shop.gifts." + giftId;
                String name = plugin.getConfigManager().getConfig().getString(path + ".name", "礼包");
                String material = plugin.getConfigManager().getConfig().getString(path + ".material", "CHEST");
                double priceMoney = plugin.getConfigManager().getConfig().getDouble(path + ".price_money", 0.0);
                int pricePoints = plugin.getConfigManager().getConfig().getInt(path + ".price_points", 0);
                List<String> lore = plugin.getConfigManager().getConfig().getStringList(path + ".lore");
                int customModelData = plugin.getConfigManager().getConfig().getInt(path + ".custom-model-data", 0);
                List<MilestoneManager.Reward> rewards = loadRewards(path + ".rewards");

                gifts.put(giftId, new GiftConfig(giftId, name, material, priceMoney, pricePoints, lore, customModelData, rewards));
            });
    }

    private List<MilestoneManager.Reward> loadRewards(String rewardsPath) {
        List<MilestoneManager.Reward> rewards = new ArrayList<>();
        List<Map<?, ?>> rewardList = plugin.getConfigManager().getConfig().getMapList(rewardsPath);

        for (Map<?, ?> rewardMap : rewardList) {
            String type = (String) rewardMap.get("type");
            Object value = rewardMap.get("value");
            rewards.add(new MilestoneManager.Reward(type, value));
        }
        return rewards;
    }

    public CompletableFuture<BuyGiftResult> buyGift(Player player, String giftId) {
        return CompletableFuture.supplyAsync(() -> {
            GiftConfig gift = gifts.get(giftId);
            if (gift == null) {
                return new BuyGiftResult(false, BuyGiftResultType.NOT_FOUND);
            }

            return SchedulerUtils.runTaskSupplied(plugin, () -> {
                Economy economy = plugin.getServer().getServicesManager()
                    .getRegistration(Economy.class).getProvider();

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

                plugin.getDatabaseManager().updateGiftId(player.getUniqueId(), giftId).join();
                plugin.getDatabaseManager().addPurchasedGift(player.getUniqueId(), giftId).join();
                plugin.getCacheManager().invalidateGiftId(player.getUniqueId());

                return new BuyGiftResult(true, BuyGiftResultType.SUCCESS, gift);
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
            Method lookMethod = api.getClass().getMethod("look", UUID.class);
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
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), takeCmd));
            }
            case "PLAYERPOINTS" -> {
                try {
                    Object api = getPlayerPointsAPI();
                    if (api == null) return;
                    Method takeMethod = api.getClass().getMethod("take", UUID.class, int.class);
                    takeMethod.invoke(api, player.getUniqueId(), amount);
                } catch (Exception e) {
                    plugin.getLogger().warning("PlayerPoints 扣除失败: " + e.getMessage());
                }
            }
        }
    }

    public void giveGiftRewards(Player player, UUID inviterUuid) {
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
                        Economy economy = plugin.getServer().getServicesManager()
                            .getRegistration(Economy.class).getProvider();
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
    }

    private void giveItem(Player player, Object value) {
        String[] parts = value.toString().split(" ");
        if (parts.length >= 2) {
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                ItemStack item = new ItemStack(mat, amount);
                player.getInventory().addItem(item);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void givePoints(Player player, Object value) {
        String pointsType = plugin.getConfigManager().getConfig()
            .getString("economy.points_type", "NONE");
        if ("NONE".equals(pointsType)) return;

        int amount = Integer.parseInt(value.toString());

        if ("CUSTOM".equals(pointsType)) {
            String giveCmd = plugin.getConfigManager().getConfig()
                .getString("economy.points_command.give", "")
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(amount));
            SchedulerUtils.runTask(plugin, () ->
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), giveCmd));
        } else if ("PLAYERPOINTS".equals(pointsType)) {
            try {
                Object api = getPlayerPointsAPI();
                if (api == null) return;
                Method giveMethod = api.getClass().getMethod("give", UUID.class, int.class);
                giveMethod.invoke(api, player.getUniqueId(), amount);
                plugin.getLogger().info("[DEBUG] PlayerPoints 发放成功: " + player.getName() + ", 数量: " + amount);
            } catch (Exception e) {
                plugin.getLogger().warning("PlayerPoints 发放失败: " + e.getMessage());
            }
        }
    }

    public GiftConfig getGift(String giftId) {
        return gifts.get(giftId);
    }

    public Map<String, GiftConfig> getAllGifts() {
        return gifts;
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
            if (isDebug()) e.printStackTrace();
            return null;
        }
    }

    public CompletableFuture<Void> switchGift(Player player, String giftId) {
        return plugin.getDatabaseManager().updateGiftId(player.getUniqueId(), giftId).thenRun(() -> {
            plugin.getCacheManager().invalidateGiftId(player.getUniqueId());
        });
    }

    public static class GiftConfig {
        public final String id;
        public final String name;
        public final String material;
        public final double priceMoney;
        public final int pricePoints;
        public final List<String> lore;
        public final int customModelData;
        public final List<MilestoneManager.Reward> rewards;

        public GiftConfig(String id, String name, String material, double priceMoney,
                         int pricePoints, List<String> lore, int customModelData, List<MilestoneManager.Reward> rewards) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.priceMoney = priceMoney;
            this.pricePoints = pricePoints;
            this.lore = lore;
            this.customModelData = customModelData;
            this.rewards = rewards;
        }
    }

    public enum BuyGiftResultType {
        SUCCESS,
        NOT_FOUND,
        INSUFFICIENT_MONEY,
        INSUFFICIENT_POINTS
    }

    public static class BuyGiftResult {
        public final boolean success;
        public final BuyGiftResultType type;
        public final GiftConfig gift;

        public BuyGiftResult(boolean success, BuyGiftResultType type) {
            this(success, type, null);
        }

        public BuyGiftResult(boolean success, BuyGiftResultType type, GiftConfig gift) {
            this.success = success;
            this.type = type;
            this.gift = gift;
        }
    }
}
