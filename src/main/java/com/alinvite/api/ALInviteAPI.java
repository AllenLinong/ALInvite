package com.alinvite.api;

import com.alinvite.ALInvite;
import com.alinvite.manager.GiftManager;
import com.alinvite.manager.MilestoneManager;
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
}
