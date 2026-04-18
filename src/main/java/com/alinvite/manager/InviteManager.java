package com.alinvite.manager;

import com.alinvite.ALInvite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class InviteManager {

    private final ALInvite plugin;
    private final SecureRandom random = new SecureRandom();
    private final Set<UUID> generatingCodes = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public InviteManager(ALInvite plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<String> generateInviteCode(UUID uuid) {
        if (generatingCodes.contains(uuid)) {
            return CompletableFuture.completedFuture(plugin.getCacheManager().getInviteCode(uuid));
        }
        generatingCodes.add(uuid);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                int length = plugin.getConfigManager().getConfig().getInt("invite_code.length", 6);
                String charset = plugin.getConfigManager().getConfig().getString("invite_code.charset", "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789");
                String prefix = plugin.getConfigManager().getConfig().getString("invite_code.prefix", "");

                String code;
                int attempts = 0;
                do {
                    StringBuilder sb = new StringBuilder(prefix);
                    for (int i = 0; i < length; i++) {
                        sb.append(charset.charAt(random.nextInt(charset.length())));
                    }
                    code = sb.toString();
                    attempts++;
                    if (attempts > 10) {
                        plugin.getLogger().warning("生成邀请码失败，已尝试 " + attempts + " 次");
                        break;
                    }
                } while (isCodeExists(code).join());

                return code;
            } finally {
                generatingCodes.remove(uuid);
            }
        }).thenCompose(code -> {
            // 检查玩家数据是否存在
            return plugin.getDatabaseManager().getPlayerData(uuid).thenCompose(data -> {
                if (data != null) {
                    // 玩家数据存在，更新邀请码
                    return plugin.getDatabaseManager().updateInviteCode(uuid, code).thenApply(v -> code);
                } else {
                    // 玩家数据不存在，创建新数据
                    return plugin.getDatabaseManager().createPlayerData(uuid, code).thenApply(v -> code);
                }
            });
        }).thenApply(code -> {
            plugin.getCacheManager().setInviteCode(uuid, code);
            return code;
        });
    }

    public CompletableFuture<Boolean> isCodeExists(String code) {
        return plugin.getDatabaseManager().isInviteCodeExists(code);
    }

    public CompletableFuture<UUID> getInviterByCode(String code) {
        return plugin.getDatabaseManager().getInviterUUIDByInviteCode(code);
    }

    public CompletableFuture<InviteResult> processInvite(Player invitee, String code) {
        String inviteeIp;
        UUID inviteeUuid;
        String inviteeName;
        try {
            inviteeIp = invitee.getAddress().getAddress().getHostAddress();
            inviteeUuid = invitee.getUniqueId();
            inviteeName = invitee.getName();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.UNKNOWN));
        }

        return plugin.getDatabaseManager().getIpInviteCount(inviteeIp)
            .thenCompose(currentCount -> {
                if (plugin.getConfigManager().getConfig().getBoolean("ip_restriction.enabled", true)) {
                    int maxInvites = plugin.getConfigManager().getConfig().getInt("ip_restriction.max_invites_per_ip", 1);
                    if (maxInvites > 0 && currentCount >= maxInvites) {
                        return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.IP_LIMIT));
                    }
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                
                return hasUsedInviteCode(inviteeUuid).thenCompose(hasUsed -> {
                    if (hasUsed) {
                        return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.ALREADY_USED));
                    }
                    return CompletableFuture.completedFuture(null);
                });
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                
                return getInviterByCode(code).thenCompose(inviterUuid -> {
                    if (inviterUuid == null) {
                        return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.INVALID_CODE));
                    }
                    
                    if (inviterUuid.equals(inviteeUuid)) {
                        return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.SELF_INVITE));
                    }
                    
                    if (plugin.getConfigManager().getConfig().getBoolean("ip_restriction.prevent_self_ip", true)) {
                        String inviterIp = getPlayerIp(inviterUuid);
                        if (inviterIp != null && inviterIp.equals(inviteeIp)) {
                            return CompletableFuture.completedFuture(new InviteResult(false, InviteResultType.SELF_INVITE));
                        }
                    }
                    
                    return plugin.getDatabaseManager().addInviteRecord(inviterUuid, inviteeUuid, inviteeIp, inviteeName)
                        .thenCompose(v -> plugin.getDatabaseManager().getPlayerData(inviterUuid))
                        .thenCompose(data -> {
                            if (data != null) {
                                int newTotal = data.totalInvites + 1;
                                return plugin.getDatabaseManager().updateInviteCount(inviterUuid, newTotal)
                                    .thenAccept(v -> {
                                        plugin.getCacheManager().invalidateStats(inviterUuid);
                                        plugin.getMilestoneManager().checkMilestones(inviterUuid, newTotal);
                                    })
                                    .thenApply(v -> new InviteResult(true, InviteResultType.SUCCESS, inviterUuid));
                            }
                            return CompletableFuture.completedFuture(new InviteResult(true, InviteResultType.SUCCESS, inviterUuid));
                        });
                });
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("处理邀请时发生错误: " + ex.getMessage());
                return new InviteResult(false, InviteResultType.UNKNOWN);
            });
    }

    private CompletableFuture<Boolean> hasUsedInviteCode(UUID uuid) {
        return plugin.getDatabaseManager().hasUsedInviteCode(uuid);
    }

    private String getPlayerIp(UUID uuid) {
        return Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getUniqueId().equals(uuid))
            .findFirst()
            .map(p -> p.getAddress().getAddress().getHostAddress())
            .orElse(null);
    }

    public CompletableFuture<Integer> getTotalInvites(UUID uuid) {
        return plugin.getCacheManager().getStatsAsync(uuid);
    }

    public CompletableFuture<String> getInviteCode(UUID uuid) {
        return plugin.getCacheManager().getInviteCodeAsync(uuid).thenApply(v -> plugin.getCacheManager().getInviteCode(uuid));
    }

    public CompletableFuture<BindResult> bindInviteCode(Player player, String code) {
        String playerIp;
        UUID playerUuid;
        String playerName;
        try {
            playerIp = player.getAddress().getAddress().getHostAddress();
            playerUuid = player.getUniqueId();
            playerName = player.getName();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new BindResult(false, BindResultType.UNKNOWN));
        }

        String usePerm = plugin.getConfigManager().getConfig()
            .getString("invite_code.use_permission", "alinvite.use");
        if (!player.hasPermission(usePerm)) {
            return CompletableFuture.completedFuture(new BindResult(false, BindResultType.NO_PERMISSION));
        }

        String trimmedCode = code.trim().toUpperCase();

        return isCodeExists(trimmedCode)
            .thenCompose(codeExists -> {
                if (!codeExists) {
                    return CompletableFuture.completedFuture(new BindResult(false, BindResultType.CODE_NOT_FOUND));
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                
                return plugin.getDatabaseManager().hasUsedInviteCode(playerUuid).thenCompose(hasUsed -> {
                    if (hasUsed) {
                        return CompletableFuture.completedFuture(new BindResult(false, BindResultType.ALREADY_USED));
                    }
                    return CompletableFuture.completedFuture(null);
                });
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                
                boolean allowVeteranToBind = plugin.getConfigManager().getConfig()
                    .getBoolean("invite_code.allow_veteran_to_bind", false);
                if (!allowVeteranToBind) {
                    String playerInviteCode = plugin.getCacheManager().getInviteCode(playerUuid);
                    if (playerInviteCode != null) {
                        return CompletableFuture.completedFuture(new BindResult(false, BindResultType.VETERAN_CANNOT_BIND));
                    }
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                
                return getInviterByCode(trimmedCode).thenCompose(inviterUuid -> {
                    if (inviterUuid != null && inviterUuid.equals(playerUuid)) {
                        return CompletableFuture.completedFuture(new BindResult(false, BindResultType.SELF_INVITE));
                    }
                    
                    if (plugin.getConfigManager().getConfig().getBoolean("ip_restriction.prevent_self_ip", true)) {
                        if (inviterUuid != null) {
                            String inviterIp = getPlayerIp(inviterUuid);
                            if (inviterIp != null && inviterIp.equals(playerIp)) {
                                return CompletableFuture.completedFuture(new BindResult(false, BindResultType.SELF_INVITE));
                            }
                        }
                    }
                    
                    return plugin.getDatabaseManager().getIpInviteCount(playerIp).thenCompose(currentCount -> {
                        if (plugin.getConfigManager().getConfig().getBoolean("ip_restriction.enabled", true)) {
                            int maxInvites = plugin.getConfigManager().getConfig().getInt("ip_restriction.max_invites_per_ip", 1);
                            if (maxInvites > 0 && currentCount >= maxInvites) {
                                return CompletableFuture.completedFuture(new BindResult(false, BindResultType.IP_LIMIT));
                            }
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .thenCompose(result2 -> {
                        if (result2 != null) return CompletableFuture.completedFuture(result2);
                        
                        return plugin.getDatabaseManager().updateInviteCode(playerUuid, trimmedCode)
                            .thenCompose(v -> {
                                plugin.getCacheManager().invalidateInviteCode(playerUuid);
                                return plugin.getDatabaseManager().addInviteRecord(inviterUuid, playerUuid, playerIp, playerName);
                            })
                            .thenCompose(v -> plugin.getDatabaseManager().getPlayerData(inviterUuid))
                            .thenCompose(data -> {
                                if (data != null) {
                                    int newTotal = data.totalInvites + 1;
                                    return plugin.getDatabaseManager().updateInviteCount(inviterUuid, newTotal)
                                        .thenAccept(v -> {
                                            plugin.getCacheManager().invalidateStats(inviterUuid);
                                            plugin.getMilestoneManager().checkMilestones(inviterUuid, newTotal);
                                        })
                                        .thenApply(v -> new BindResult(true, BindResultType.SUCCESS));
                                }
                                return CompletableFuture.completedFuture(new BindResult(true, BindResultType.SUCCESS));
                            })
                            .thenApply(result3 -> {
                                plugin.getGiftManager().giveGiftRewards(player, inviterUuid);
                                return result3;
                            });
                    });
                });
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("绑定邀请码时发生错误: " + ex.getMessage());
                return new BindResult(false, BindResultType.UNKNOWN);
            });
    }

    public enum BindResultType {
        SUCCESS,
        NO_PERMISSION,
        CODE_NOT_FOUND,
        ALREADY_USED,
        IP_LIMIT,
        SELF_INVITE,
        VETERAN_CANNOT_BIND,
        UNKNOWN
    }

    public static class BindResult {
        public final boolean success;
        public final BindResultType type;

        public BindResult(boolean success, BindResultType type) {
            this.success = success;
            this.type = type;
        }
    }

    public enum InviteResultType {
        SUCCESS,
        INVALID_CODE,
        ALREADY_USED,
        IP_LIMIT,
        SELF_INVITE,
        UNKNOWN
    }

    public static class InviteResult {
        public final boolean success;
        public final InviteResultType type;
        public final UUID inviterUuid;

        public InviteResult(boolean success, InviteResultType type) {
            this(success, type, null);
        }

        public InviteResult(boolean success, InviteResultType type, UUID inviterUuid) {
            this.success = success;
            this.type = type;
            this.inviterUuid = inviterUuid;
        }
    }
}
