package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.database.DatabaseManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CacheManager {

    private final ALInvite plugin;
    private Cache<String, String> inviteCodeCache;
    private Cache<String, Integer> statsCache;
    private Cache<String, String> giftCache;
    private Cache<String, Boolean> ipCheckCache;

    public CacheManager(ALInvite plugin) {
        this.plugin = plugin;
        initCaches();
    }

    private void initCaches() {
        int codeTtl = plugin.getConfigManager().getConfig().getInt("performance.cache_invite_code_ttl", 30);
        int statsTtl = plugin.getConfigManager().getConfig().getInt("performance.cache_stats_ttl", 30);
        int giftTtl = plugin.getConfigManager().getConfig().getInt("performance.cache_gift_ttl", 60);
        
        // 动态计算缓存大小，基于服务器规模
        int cacheSize = calculateOptimalCacheSize();
        
        plugin.getLogger().info("初始化缓存 - 缓存大小: " + cacheSize);

        inviteCodeCache = Caffeine.newBuilder()
            .expireAfterWrite(codeTtl, TimeUnit.MINUTES)
            .maximumSize(cacheSize)
            .recordStats() // 启用统计信息
            .build();

        statsCache = Caffeine.newBuilder()
            .expireAfterWrite(statsTtl, TimeUnit.MINUTES)
            .maximumSize(cacheSize)
            .recordStats()
            .build();

        giftCache = Caffeine.newBuilder()
            .expireAfterWrite(giftTtl, TimeUnit.MINUTES)
            .maximumSize(cacheSize)
            .recordStats()
            .build();

        ipCheckCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(Math.max(500, cacheSize / 2))
            .recordStats()
            .build();
    }
    
    /**
     * 计算最优缓存大小
     */
    private int calculateOptimalCacheSize() {
        int maxPlayers = org.bukkit.Bukkit.getMaxPlayers();
        
        // 基础缓存大小
        int baseSize = 1000;
        
        // 根据服务器规模调整
        if (maxPlayers > 100) {
            // 大型服务器：缓存大小为最大玩家数的3倍
            return Math.max(baseSize, maxPlayers * 3);
        } else if (maxPlayers > 50) {
            // 中型服务器：缓存大小为最大玩家数的2倍
            return Math.max(baseSize, maxPlayers * 2);
        } else {
            // 小型服务器：使用基础大小
            return baseSize;
        }
    }

    public String getInviteCode(UUID uuid) {
        return inviteCodeCache.getIfPresent(uuid.toString());
    }

    public void setInviteCode(UUID uuid, String code) {
        inviteCodeCache.put(uuid.toString(), code);
    }

    public void invalidateInviteCode(UUID uuid) {
        inviteCodeCache.invalidate(uuid.toString());
    }

    public Integer getStats(UUID uuid) {
        return statsCache.getIfPresent(uuid.toString());
    }

    public void setStats(UUID uuid, int stats) {
        statsCache.put(uuid.toString(), stats);
    }

    public void invalidateStats(UUID uuid) {
        statsCache.invalidate(uuid.toString());
    }

    public String getGiftId(UUID uuid) {
        return giftCache.getIfPresent(uuid.toString());
    }

    public void setGiftId(UUID uuid, String giftId) {
        giftCache.put(uuid.toString(), giftId);
    }

    public void invalidateGiftId(UUID uuid) {
        giftCache.invalidate(uuid.toString());
    }

    public Boolean getIpCheckResult(String ip) {
        return ipCheckCache.getIfPresent(ip);
    }

    public void setIpCheckResult(String ip, boolean result) {
        ipCheckCache.put(ip, result);
    }

    public CompletableFuture<String> getInviteCodeAsync(UUID uuid) {
        String cached = getInviteCode(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getDatabaseManager().getInviteCodeByPlayer(uuid).thenApply(code -> {
            if (code != null) {
                setInviteCode(uuid, code);
            }
            return code;
        });
    }

    public CompletableFuture<Integer> getStatsAsync(UUID uuid) {
        Integer cached = getStats(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getDatabaseManager().getPlayerData(uuid).thenApply(data -> {
            if (data != null) {
                int stats = data.totalInvites;
                setStats(uuid, stats);
                return stats;
            }
            return 0;
        });
    }

    public CompletableFuture<String> getGiftIdAsync(UUID uuid) {
        String cached = getGiftId(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getDatabaseManager().getGiftId(uuid).thenApply(giftId -> {
            if (giftId != null) {
                setGiftId(uuid, giftId);
            }
            return giftId;
        });
    }

    public void clear() {
        inviteCodeCache.invalidateAll();
        statsCache.invalidateAll();
        giftCache.invalidateAll();
        ipCheckCache.invalidateAll();
    }
}