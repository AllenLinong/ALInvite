package com.alinvite.listeners;

import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * 第三方充值插件适配监听器
 * 主动监听和适配常见的充值插件事件
 */
public class ThirdPartyRechargeListener implements Listener {

    private final ALInvite plugin;
    private boolean pointsPlusEnabled = false;
    private boolean playerPointsEnabled = false;
    private boolean tokenManagerEnabled = false;
    private boolean minePayEnabled = false;

    public ThirdPartyRechargeListener(ALInvite plugin) {
        this.plugin = plugin;
        detectThirdPartyPlugins();
    }

    /**
     * 检测已安装的第三方充值插件
     */
    private void detectThirdPartyPlugins() {
        // 检测 PointsPlus 插件
        if (Bukkit.getPluginManager().getPlugin("PointsPlus") != null) {
            pointsPlusEnabled = true;
            plugin.getLogger().info("检测到 PointsPlus 插件，已启用适配");
        }

        // 检测 PlayerPoints 插件
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            playerPointsEnabled = true;
            plugin.getLogger().info("检测到 PlayerPoints 插件，已启用适配");
        }

        // 检测 TokenManager 插件
        if (Bukkit.getPluginManager().getPlugin("TokenManager") != null) {
            tokenManagerEnabled = true;
            plugin.getLogger().info("检测到 TokenManager 插件，已启用适配");
        }

        // 检测 MinePay 插件
        if (Bukkit.getPluginManager().getPlugin("MinePay") != null) {
            minePayEnabled = true;
            plugin.getLogger().info("检测到 MinePay 插件，已启用适配");
        }

        if (!pointsPlusEnabled && !playerPointsEnabled && !tokenManagerEnabled && !minePayEnabled) {
            plugin.getLogger().info("未检测到支持的第三方充值插件，第三方联动功能将禁用");
        }
    }

    /**
     * 通用充值事件处理（反射方式适配各种插件）
     */
    @EventHandler
    public void onGenericPointsEvent(org.bukkit.event.Event event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("points_rebate.third_party.enabled", true)) {
            return;
        }

        String eventName = event.getClass().getSimpleName();
        
        // 适配 PointsPlus 插件事件
        if (pointsPlusEnabled && eventName.contains("PointsPlus")) {
            handlePointsPlusEvent(event);
            return;
        }

        // 适配 PlayerPoints 插件事件
        if (playerPointsEnabled && eventName.contains("PlayerPoints")) {
            handlePlayerPointsEvent(event);
            return;
        }

        // 适配 TokenManager 插件事件
        if (tokenManagerEnabled && eventName.contains("Token")) {
            handleTokenManagerEvent(event);
            return;
        }

        // 适配 MinePay 插件事件
        if (minePayEnabled && eventName.contains("MinePay")) {
            handleMinePayEvent(event);
            return;
        }
    }

    /**
     * 处理 PointsPlus 插件事件
     */
    private void handlePointsPlusEvent(org.bukkit.event.Event event) {
        try {
            // 使用反射获取事件中的玩家和金额信息
            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Method getAmountMethod = event.getClass().getMethod("getAmount");
            
            Object playerObj = getPlayerMethod.invoke(event);
            double amount = (double) getAmountMethod.invoke(event);
            
            if (playerObj instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) playerObj;
                processRecharge(player.getName(), amount, "PointsPlus");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理 PointsPlus 事件失败: " + e.getMessage());
        }
    }

    /**
     * 处理 PlayerPoints 插件事件
     */
    private void handlePlayerPointsEvent(org.bukkit.event.Event event) {
        try {
            // PlayerPoints 插件常用的事件结构
            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Method getPointsMethod = event.getClass().getMethod("getPoints");
            
            Object playerObj = getPlayerMethod.invoke(event);
            int points = (int) getPointsMethod.invoke(event);
            
            if (playerObj instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) playerObj;
                processRecharge(player.getName(), points, "PlayerPoints");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理 PlayerPoints 事件失败: " + e.getMessage());
        }
    }

    /**
     * 处理 TokenManager 插件事件
     */
    private void handleTokenManagerEvent(org.bukkit.event.Event event) {
        try {
            // TokenManager 插件事件处理
            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Method getTokensMethod = event.getClass().getMethod("getTokens");
            
            Object playerObj = getPlayerMethod.invoke(event);
            double tokens = (double) getTokensMethod.invoke(event);
            
            if (playerObj instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) playerObj;
                processRecharge(player.getName(), tokens, "TokenManager");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理 TokenManager 事件失败: " + e.getMessage());
        }
    }

    /**
     * 处理 MinePay 插件事件
     */
    private void handleMinePayEvent(org.bukkit.event.Event event) {
        try {
            // MinePay 插件可能有多种事件结构，尝试多种方式获取信息
            String playerName = null;
            double amount = 0.0;
            
            // 方式1：尝试获取玩家对象和金额
            try {
                Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                Method getAmountMethod = event.getClass().getMethod("getAmount");
                
                Object playerObj = getPlayerMethod.invoke(event);
                amount = (double) getAmountMethod.invoke(event);
                
                if (playerObj instanceof org.bukkit.entity.Player) {
                    org.bukkit.entity.Player player = (org.bukkit.entity.Player) playerObj;
                    playerName = player.getName();
                }
            } catch (NoSuchMethodException e) {
                // 方式2：尝试直接获取玩家名称和金额
                try {
                    Method getPlayerNameMethod = event.getClass().getMethod("getPlayerName");
                    Method getPointsMethod = event.getClass().getMethod("getPoints");
                    
                    playerName = (String) getPlayerNameMethod.invoke(event);
                    amount = (double) getPointsMethod.invoke(event);
                } catch (NoSuchMethodException e2) {
                    // 方式3：尝试从事件类名推断字段名
                    String eventName = event.getClass().getSimpleName().toLowerCase();
                    
                    if (eventName.contains("pay") || eventName.contains("recharge")) {
                        // 尝试常见的字段名
                        for (Method method : event.getClass().getMethods()) {
                            String methodName = method.getName().toLowerCase();
                            if (methodName.contains("player") && !methodName.contains("get")) {
                                try {
                                    Object result = method.invoke(event);
                                    if (result instanceof String) {
                                        playerName = (String) result;
                                    }
                                } catch (Exception ignored) {}
                            }
                            if (methodName.contains("amount") || methodName.contains("points")) {
                                try {
                                    Object result = method.invoke(event);
                                    if (result instanceof Number) {
                                        amount = ((Number) result).doubleValue();
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
            
            if (playerName != null && amount > 0) {
                processRecharge(playerName, amount, "MinePay");
            } else {
                plugin.getLogger().warning("无法从 MinePay 事件中提取有效的玩家名称或金额");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("处理 MinePay 事件失败: " + e.getMessage());
        }
    }

    /**
     * 统一处理充值返点逻辑
     */
    private void processRecharge(String playerName, double amount, String pluginName) {
        // 记录第三方充值日志
        if (plugin.getConfigManager().getConfig().getBoolean("points_rebate.third_party.log_third_party", true)) {
            plugin.getLogger().info("检测到第三方充值: " + playerName + " 通过 " + pluginName + " 充值 " + amount + " 点券");
        }

        // 调用返点处理API
        CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(playerName, amount);
        
        result.thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("第三方充值返点处理成功: " + playerName + " - " + pluginName);
            } else {
                plugin.getLogger().warning("第三方充值返点处理失败: " + playerName + " - " + pluginName);
            }
        });
    }

    /**
     * 防重复检查
     */
    private boolean checkAntiDuplicate(String playerName, double amount, String pluginName) {
        if (!plugin.getConfigManager().getConfig().getBoolean("points_rebate.third_party.anti_duplicate", true)) {
            return true;
        }

        // 生成交易标识（防止短时间内重复处理）
        String transactionKey = generateTransactionKey(playerName, amount, pluginName);
        
        // 检查缓存中是否已存在相同交易
        // 这里可以使用Caffeine缓存实现防重复检查
        
        return true; // 简化实现，实际应该检查缓存
    }

    /**
     * 生成交易标识
     */
    private String generateTransactionKey(String playerName, double amount, String pluginName) {
        long currentTime = System.currentTimeMillis();
        long timeWindow = plugin.getConfigManager().getConfig().getLong("points_rebate.third_party.duplicate_window", 300) * 1000;
        long windowStart = currentTime - (currentTime % timeWindow);
        
        return String.format("%s_%.2f_%s_%d", playerName, amount, pluginName, windowStart);
    }
}