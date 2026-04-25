package com.alinvite.api;

import com.alinvite.ALInvite;
import com.alinvite.manager.GiftManager;
import com.alinvite.manager.MilestoneManager;
import com.alinvite.manager.PointsRebateManager;
import com.alinvite.manager.LeaderboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ALInviteAPI {

    private static ALInvite plugin;

    private ALInviteAPI() {}

    public static void init(ALInvite plugin) {
        ALInviteAPI.plugin = plugin;
    }

    public static CompletableFuture<String> getInviteCode(Player player) {
        return getInviteCode(player.getUniqueId());
    }

    public static CompletableFuture<String> getInviteCode(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String code = plugin.getCacheManager().getInviteCode(uuid);
            if (code == null) {
                code = plugin.getDatabaseManager().getInviteCodeByPlayer(uuid).join();
                if (code != null) {
                    plugin.getCacheManager().setInviteCode(uuid, code);
                }
            }
            return code;
        });
    }

    public static CompletableFuture<Integer> getTotalInvites(Player player) {
        return getTotalInvites(player.getUniqueId());
    }

    public static CompletableFuture<Integer> getTotalInvites(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Integer total = plugin.getCacheManager().getStats(uuid);
            if (total == null) {
                var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                total = data != null ? data.totalInvites : 0;
                plugin.getCacheManager().setStats(uuid, total);
            }
            return total;
        });
    }

    public static CompletableFuture<String> getPurchasedGift(Player player) {
        return getPurchasedGift(player.getUniqueId());
    }

    public static CompletableFuture<String> getPurchasedGift(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String giftId = plugin.getCacheManager().getGiftId(uuid);
            if (giftId == null) {
                giftId = plugin.getDatabaseManager().getGiftId(uuid).join();
                if (giftId != null) {
                    plugin.getCacheManager().setGiftId(uuid, giftId);
                }
            }
            return giftId;
        });
    }

    public static CompletableFuture<GiftManager.GiftConfig> getActiveGift(Player player) {
        return getActiveGift(player.getUniqueId());
    }

    public static CompletableFuture<GiftManager.GiftConfig> getActiveGift(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String giftId = getPurchasedGift(uuid).join();

            if (giftId == null) {
                boolean requireGift = plugin.getConfigManager().getConfig()
                    .getBoolean("new_player_reward.require_gift", false);
                if (requireGift) {
                    return null;
                }
                giftId = plugin.getConfigManager().getConfig()
                    .getString("new_player_reward.default_gift_id", "default");
            }

            return plugin.getGiftManager().getGift(giftId);
        });
    }

    public static CompletableFuture<Boolean> isSameIp(Player p1, Player p2) {
        return CompletableFuture.supplyAsync(() -> {
            if (p1 == null || p2 == null) {
                return false;
            }
            String ip1 = p1.getAddress().getAddress().getHostAddress();
            String ip2 = p2.getAddress().getAddress().getHostAddress();
            return ip1 != null && ip1.equals(ip2);
        });
    }

    public static CompletableFuture<List<UUID>> getInvitees(UUID inviterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> result = new ArrayList<>();
            return result;
        });
    }

    public static CompletableFuture<Boolean> isPlayerBound(Player player) {
        return isPlayerBound(player.getUniqueId());
    }

    public static CompletableFuture<Boolean> isPlayerBound(UUID uuid) {
        return plugin.getDatabaseManager().getInviter(uuid)
            .thenApply(inviterUuid -> inviterUuid != null);
    }

    public static CompletableFuture<String> getBindStatus(Player player) {
        return getBindStatus(player.getUniqueId());
    }

    public static CompletableFuture<String> getBindStatus(UUID uuid) {
        return isPlayerBound(uuid)
            .thenApply(bound -> bound ? "已绑定" : "未绑定");
    }

    public static CompletableFuture<String> getInviterName(Player player) {
        return getInviterName(player.getUniqueId());
    }

    public static CompletableFuture<String> getInviterName(UUID uuid) {
        return plugin.getDatabaseManager().getInviter(uuid)
            .thenCompose(inviterUuid -> {
                if (inviterUuid == null) {
                    return CompletableFuture.completedFuture("无");
                }
                return plugin.getDatabaseManager().getPlayerData(inviterUuid)
                    .thenApply(data -> {
                        if (data == null) {
                            return "无";
                        }
                        Player onlineInviter = plugin.getServer().getPlayer(inviterUuid);
                        if (onlineInviter != null && onlineInviter.isOnline()) {
                            return onlineInviter.getName();
                        }
                        return data.inviteCode != null ? data.inviteCode : "未知";
                    });
            });
    }

    public static CompletableFuture<UUID> getInviterUuid(Player player) {
        return getInviterUuid(player.getUniqueId());
    }

    public static CompletableFuture<UUID> getInviterUuid(UUID uuid) {
        return plugin.getDatabaseManager().getInviter(uuid);
    }

    public static CompletableFuture<String> resolvePlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return CompletableFuture.completedFuture(text);
        }
        return plugin.getMenuManager().getPlaceholderResolver().applyPlaceholdersAsync(text, player);
    }

    public static void registerInviteListener(InviteSuccessListener listener) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        }, 1L);
    }

    @FunctionalInterface
    public interface InviteSuccessListener {
        void onInviteSuccess(UUID inviterUuid, UUID inviteeUuid);
    }

    // ==================== 点券充值返点系统 API ====================

    /**
     * 处理点券充值返点
     * 通过API调用触发返点处理
     * 
     * @param targetPlayer 目标玩家名称（接收点券的玩家）
     * @param amount 充值点券数量
     * @return CompletableFuture<Boolean> 处理结果，true表示成功
     */
    public static CompletableFuture<Boolean> processPointsRecharge(String targetPlayer, double amount) {
        return plugin.getPointsRebateManager().processRecharge("ThirdParty", targetPlayer, amount, false);
    }

    /**
     * 处理点券充值返点（带操作者信息）
     * 适用于管理员命令调用
     * 
     * @param operator 操作者名称（执行命令的玩家或控制台）
     * @param targetPlayer 目标玩家名称（接收点券的玩家）
     * @param amount 充值点券数量
     * @param skipRebate 是否跳过返点处理（用于测试或特殊情况）
     * @return CompletableFuture<Boolean> 处理结果，true表示成功
     */
    public static CompletableFuture<Boolean> processPointsRecharge(
            String operator, String targetPlayer, double amount, boolean skipRebate) {
        return plugin.getPointsRebateManager().processRecharge(operator, targetPlayer, amount, skipRebate);
    }

    /**
     * 获取玩家累计返点总额
     * @param player 玩家对象
     * @return CompletableFuture<Double> 累计返点总额
     */
    public static CompletableFuture<Double> getTotalRebateAmount(Player player) {
        return getTotalRebateAmount(player.getUniqueId());
    }

    /**
     * 获取玩家累计返点总额
     * @param uuid 玩家UUID
     * @return CompletableFuture<Double> 累计返点总额
     */
    public static CompletableFuture<Double> getTotalRebateAmount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // 从数据库获取累计返点总额
            return plugin.getDatabaseManager().getTotalRebateAmount(uuid).join();
        });
    }

    /**
     * 检查跨服重复交易
     * @param playerUuid 玩家UUID
     * @param amount 充值金额
     * @return CompletableFuture<Boolean> 是否为重复交易
     */
    public static CompletableFuture<Boolean> checkRebateDuplicate(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            // 检查数据库中是否存在相同交易记录
            return plugin.getDatabaseManager().checkRebateDuplicate(playerUuid, amount).join();
        });
    }

    // ==================== 排行榜系统 API ====================

    /**
     * 获取排行榜类型枚举
     * @param key 类型键值
     * @return LeaderboardType 排行榜类型
     */
    public static LeaderboardManager.LeaderboardType getLeaderboardType(String key) {
        return LeaderboardManager.LeaderboardType.fromKey(key);
    }

    /**
     * 获取排行榜变量
     * 用于其他插件获取排行榜数据变量
     * 
     * @param type 排行榜类型
     * @return Map<String, String> 变量映射
     */
    public static java.util.Map<String, String> getLeaderboardVariables(LeaderboardManager.LeaderboardType type) {
        return plugin.getLeaderboardManager().getLeaderboardVariables(type);
    }

    /**
     * 获取排行榜变量（通过字符串键值）
     * 用于其他插件获取排行榜数据变量
     * 
     * @param typeKey 排行榜类型键值
     * @return Map<String, String> 变量映射
     */
    public static java.util.Map<String, String> getLeaderboardVariables(String typeKey) {
        LeaderboardManager.LeaderboardType type = getLeaderboardType(typeKey);
        if (type == null) {
            return new java.util.HashMap<>();
        }
        return getLeaderboardVariables(type);
    }

    /**
     * 替换字符串中的排行榜变量
     * 用于其他插件处理包含排行榜变量的文本
     * 
     * @param input 输入文本
     * @param type 排行榜类型
     * @return String 替换后的文本
     */
    public static String replaceLeaderboardVariables(String input, LeaderboardManager.LeaderboardType type) {
        return plugin.getLeaderboardManager().replaceLeaderboardVariables(input, type);
    }

    /**
     * 替换字符串中的排行榜变量（通过字符串键值）
     * 用于其他插件处理包含排行榜变量的文本
     * 
     * @param input 输入文本
     * @param typeKey 排行榜类型键值
     * @return String 替换后的文本
     */
    public static String replaceLeaderboardVariables(String input, String typeKey) {
        LeaderboardManager.LeaderboardType type = getLeaderboardType(typeKey);
        if (type == null) {
            return input;
        }
        return replaceLeaderboardVariables(input, type);
    }

    /**
     * 手动更新排行榜数据
     * 用于其他插件在需要时触发排行榜更新
     * 
     * @param type 排行榜类型
     */
    public static void updateLeaderboard(LeaderboardManager.LeaderboardType type) {
        plugin.getLeaderboardManager().updateLeaderboard(type);
    }

    /**
     * 手动更新排行榜数据（通过字符串键值）
     * 用于其他插件在需要时触发排行榜更新
     * 
     * @param typeKey 排行榜类型键值
     */
    public static void updateLeaderboard(String typeKey) {
        LeaderboardManager.LeaderboardType type = getLeaderboardType(typeKey);
        if (type != null) {
            updateLeaderboard(type);
        }
    }
}
