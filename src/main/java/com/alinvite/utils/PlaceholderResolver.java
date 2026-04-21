package com.alinvite.utils;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlaceholderResolver {

    private final ALInvite plugin;
    private final Map<String, String> customReplacements;

    public PlaceholderResolver(ALInvite plugin) {
        this.plugin = plugin;
        this.customReplacements = new HashMap<>();
    }

    public void registerCustomReplacement(String placeholder, String value) {
        customReplacements.put(placeholder, value);
    }

    public void unregisterCustomReplacement(String placeholder) {
        customReplacements.remove(placeholder);
    }

    public CompletableFuture<Map<String, String>> resolveAllPlaceholders(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, String> placeholders = new HashMap<>();

        CompletableFuture<Map<String, String>> codeFuture = getInviteCodeAsync(uuid)
            .thenApply(code -> {
                placeholders.put("{invite_code}", code);
                placeholders.put("%alinvite_code%", code);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> bindFuture = getBindStatusAsync(uuid)
            .thenApply(bind -> {
                placeholders.put("{bind_status}", bind);
                placeholders.put("%alinvite_bind_status%", bind);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> inviterFuture = getInviterNameAsync(uuid)
            .thenApply(name -> {
                placeholders.put("{inviter_name}", name);
                placeholders.put("%alinvite_inviter_name%", name);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> totalFuture = getTotalInvitesAsync(uuid)
            .thenApply(total -> {
                placeholders.put("{total_invites}", total);
                placeholders.put("%alinvite_total%", total);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> giftFuture = getGiftNameAsync(uuid)
            .thenApply(gift -> {
                placeholders.put("{gift_name}", gift);
                placeholders.put("%alinvite_gift_name%", gift);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> hasGiftFuture = hasActiveGiftAsync(uuid)
            .thenApply(has -> {
                placeholders.put("{has_gift}", has);
                placeholders.put("%alinvite_has_gift%", has);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> nextMilestoneFuture = getNextMilestoneAsync(uuid)
            .thenApply(milestone -> {
                placeholders.put("{next_milestone}", milestone);
                placeholders.put("%alinvite_next_milestone%", milestone);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> totalRebateFuture = getTotalRebateAsync(uuid)
            .thenApply(totalRebate -> {
                placeholders.put("{total_rebate}", totalRebate);
                placeholders.put("%alinvite_total_rebate%", totalRebate);
                return placeholders;
            });

        CompletableFuture<Map<String, String>> cashRebateFuture = getCashRebateAsync(uuid)
            .thenApply(cashRebate -> {
                placeholders.put("{cash_rebate}", cashRebate);
                placeholders.put("%alinvite_cash_rebate%", cashRebate);
                return placeholders;
            });

        return CompletableFuture.allOf(codeFuture, bindFuture, inviterFuture, totalFuture, giftFuture, hasGiftFuture, nextMilestoneFuture, totalRebateFuture, cashRebateFuture)
            .thenApply(v -> {
                customReplacements.forEach((key, value) -> placeholders.put(key, value));
                return placeholders;
            });
    }

    public String applyPlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        UUID uuid = player.getUniqueId();

        String code = getInviteCodeSync(uuid);
        result = result.replace("{invite_code}", code)
                      .replace("%alinvite_code%", code);

        String bind = getBindStatusSync(uuid);
        result = result.replace("{bind_status}", bind)
                      .replace("%alinvite_bind_status%", bind);

        String inviter = getInviterNameSync(uuid);
        result = result.replace("{inviter_name}", inviter)
                      .replace("%alinvite_inviter_name%", inviter);

        String total = getTotalInvitesSync(uuid);
        result = result.replace("{total_invites}", total)
                      .replace("%alinvite_total%", total);

        String gift = getGiftNameSync(uuid);
        result = result.replace("{gift_name}", gift)
                      .replace("%alinvite_gift_name%", gift);

        String hasGift = hasActiveGiftSync(uuid);
        result = result.replace("{has_gift}", hasGift)
                      .replace("%alinvite_has_gift%", hasGift);

        String nextMilestone = getNextMilestoneSync(uuid);
        result = result.replace("{next_milestone}", nextMilestone)
                     .replace("%alinvite_next_milestone%", nextMilestone);

        String totalRebate = getTotalRebateSync(uuid);
        result = result.replace("{total_rebate}", totalRebate)
                     .replace("%alinvite_total_rebate%", totalRebate);

        String cashRebate = getCashRebateSync(uuid);
        result = result.replace("{cash_rebate}", cashRebate)
                     .replace("%alinvite_cash_rebate%", cashRebate);

        for (Map.Entry<String, String> entry : customReplacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public CompletableFuture<String> applyPlaceholdersAsync(String text, Player player) {
        return resolveAllPlaceholders(player).thenApply(placeholders -> {
            if (text == null || text.isEmpty()) {
                return text;
            }
            String result = text;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        });
    }

    public CompletableFuture<String> getInviteCodeAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getInviteCodeSync(uuid));
    }

    public String getInviteCodeSync(UUID uuid) {
        String code = plugin.getCacheManager().getInviteCode(uuid);
        if (code == null) {
            code = plugin.getDatabaseManager().getInviteCodeByPlayer(uuid).join();
            if (code != null) {
                plugin.getCacheManager().setInviteCode(uuid, code);
            }
        }
        boolean hasPermission = plugin.getServer().getPlayer(uuid) != null &&
            plugin.getServer().getPlayer(uuid).hasPermission(
                plugin.getConfigManager().getConfig().getString("invite_code.veteran_permission", "alinvite.veteran"));
        if (!hasPermission) {
            return "未解锁";
        }
        return code != null ? code : "生成中...";
    }

    public CompletableFuture<String> getBindStatusAsync(UUID uuid) {
        return plugin.getDatabaseManager().getInviter(uuid)
            .thenApply(inviterUuid -> inviterUuid != null ? "已绑定" : "未绑定");
    }

    public String getBindStatusSync(UUID uuid) {
        UUID inviterUuid = plugin.getDatabaseManager().getInviter(uuid).join();
        return inviterUuid != null ? "已绑定" : "未绑定";
    }

    public CompletableFuture<String> getInviterNameAsync(UUID uuid) {
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

    public String getInviterNameSync(UUID uuid) {
        UUID inviterUuid = plugin.getDatabaseManager().getInviter(uuid).join();
        if (inviterUuid == null) {
            return "无";
        }
        var data = plugin.getDatabaseManager().getPlayerData(inviterUuid).join();
        if (data == null) {
            return "无";
        }
        Player onlineInviter = plugin.getServer().getPlayer(inviterUuid);
        if (onlineInviter != null && onlineInviter.isOnline()) {
            return onlineInviter.getName();
        }
        return data.inviteCode != null ? data.inviteCode : "未知";
    }

    public CompletableFuture<String> getTotalInvitesAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getTotalInvitesSync(uuid));
    }

    public String getTotalInvitesSync(UUID uuid) {
        Integer total = plugin.getCacheManager().getStats(uuid);
        if (total == null) {
            var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
            total = data != null ? data.totalInvites : 0;
            plugin.getCacheManager().setStats(uuid, total);
        }
        return String.valueOf(total);
    }

    public CompletableFuture<String> getGiftNameAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getGiftNameSync(uuid));
    }

    public String getGiftNameSync(UUID uuid) {
        String giftId = plugin.getCacheManager().getGiftId(uuid);
        if (giftId == null) {
            giftId = plugin.getDatabaseManager().getGiftId(uuid).join();
            if (giftId != null) {
                plugin.getCacheManager().setGiftId(uuid, giftId);
            }
        }

        if (giftId == null) {
            boolean requireGift = plugin.getConfigManager().getConfig()
                .getBoolean("new_player_reward.require_gift", false);
            if (requireGift) {
                return "无";
            }
            giftId = plugin.getConfigManager().getConfig()
                .getString("new_player_reward.default_gift_id", "default");
        }

        var gift = plugin.getGiftManager().getGift(giftId);
        return gift != null ? ConfigManager.colorize(gift.name) : "无";
    }

    public CompletableFuture<String> hasActiveGiftAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> hasActiveGiftSync(uuid));
    }

    public String hasActiveGiftSync(UUID uuid) {
        String giftId = plugin.getCacheManager().getGiftId(uuid);
        if (giftId == null) {
            giftId = plugin.getDatabaseManager().getGiftId(uuid).join();
        }
        return giftId != null ? "true" : "false";
    }

    public CompletableFuture<String> getNextMilestoneAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getNextMilestoneSync(uuid));
    }

    public String getNextMilestoneSync(UUID uuid) {
        Map<Integer, com.alinvite.manager.MilestoneManager.Milestone> milestones = plugin.getMilestoneManager().getMilestones();
        Integer currentTotal = plugin.getCacheManager().getStats(uuid);
        if (currentTotal == null) {
            var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
            currentTotal = data != null ? data.totalInvites : 0;
        }

        for (Map.Entry<Integer, com.alinvite.manager.MilestoneManager.Milestone> entry : milestones.entrySet()) {
            if (entry.getKey() > currentTotal) {
                return String.valueOf(entry.getKey());
            }
        }
        return "MAX";
    }

    public CompletableFuture<String> getTotalRebateAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getTotalRebateSync(uuid));
    }

    public String getTotalRebateSync(UUID uuid) {
        try {
            // 从数据库获取累计返点总额
            Double totalRebate = plugin.getDatabaseManager().getTotalRebateAmount(uuid).join();
            // 如果为null或NaN，返回0.00
            if (totalRebate == null || totalRebate.isNaN()) {
                return "0.00";
            }
            // 格式化显示，保留2位小数
            return String.format("%.2f", totalRebate);
        } catch (Exception e) {
            plugin.getLogger().warning("获取累计返点总额失败: " + e.getMessage());
            return "0.00";
        }
    }

    public CompletableFuture<String> getCashRebateAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getCashRebateSync(uuid));
    }

    public String getCashRebateSync(UUID uuid) {
        try {
            // 从数据库获取现金返点金额
            Double cashRebate = plugin.getDatabaseManager().getCashRebateAmount(uuid).join();
            // 如果为null或NaN，返回0.00
            if (cashRebate == null || cashRebate.isNaN()) {
                return "0.00";
            }
            // 格式化显示，保留2位小数
            return String.format("%.2f", cashRebate);
        } catch (Exception e) {
            plugin.getLogger().warning("获取现金返点金额失败: " + e.getMessage());
            return "0.00";
        }
    }
}