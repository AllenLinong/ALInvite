package com.alinvite.integration;

import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletableFuture;

/**
 * MinePay插件集成支持
 * 监听MinePay充值成功事件，自动触发返点处理
 */
public class MinePayIntegration implements Listener {

    private final ALInvite plugin;
    private boolean minePayEnabled = false;

    public MinePayIntegration(ALInvite plugin) {
        this.plugin = plugin;
        detectMinePayPlugin();
    }

    /**
     * 检测MinePay插件是否已安装
     */
    private void detectMinePayPlugin() {
        if (Bukkit.getPluginManager().getPlugin("MinePay") != null) {
            minePayEnabled = true;
            plugin.getLogger().info("检测到 MinePay 插件，已启用充值返点集成");
        } else {
            plugin.getLogger().info("未检测到 MinePay 插件，充值返点集成功能将禁用");
        }
    }

    /**
     * 监听MinePay充值成功事件
     * 当玩家通过MinePay成功充值时，自动触发返点处理
     * 
     * @param event MinePay充值成功事件
     */
    @EventHandler
    public void onMinePaySuccess(org.bukkit.event.Event event) {
        if (!minePayEnabled) {
            return;
        }

        try {
            // 检查事件类型 - 使用更可靠的事件类名检测
            String eventName = event.getClass().getSimpleName();
            String eventPackage = event.getClass().getPackage().getName();
            
            // 检查是否是MinePay相关事件
            if (!eventPackage.contains("minepay") && !eventName.toLowerCase().contains("minepay")) {
                return;
            }
            
            // 检查事件名称是否包含成功标识
            if (!eventName.toLowerCase().contains("success") && !eventName.toLowerCase().contains("complete")) {
                return;
            }

            plugin.getLogger().info("检测到MinePay充值事件: " + eventName);
            
            // 尝试使用反射获取订单信息
            String playerName = null;
            double pointsAmount = 0.0;
            String tradeTypeName = null;
            
            try {
                // 尝试获取交易信息对象
                java.lang.reflect.Method getTradeInfoMethod = event.getClass().getMethod("getTradeInfo");
                Object tradeInfo = getTradeInfoMethod.invoke(event);
                
                // 获取玩家名称
                java.lang.reflect.Method getPlayerNameMethod = tradeInfo.getClass().getMethod("getPlayerName");
                playerName = (String) getPlayerNameMethod.invoke(tradeInfo);
                
                // 获取充值金额（单位：分，需要转换为点券）
                java.lang.reflect.Method getPriceMethod = tradeInfo.getClass().getMethod("getPrice");
                int priceInCents = (int) getPriceMethod.invoke(tradeInfo);
                pointsAmount = priceInCents / 100.0; // 转换为点券数量
                
                // 获取订单类型
                java.lang.reflect.Method getTradeTypeMethod = tradeInfo.getClass().getMethod("getTradeType");
                Object tradeType = getTradeTypeMethod.invoke(tradeInfo);
                
                // 检查是否为点券订单
                java.lang.reflect.Method nameMethod = tradeType.getClass().getMethod("name");
                tradeTypeName = (String) nameMethod.invoke(tradeType);
                
            } catch (Exception e) {
                plugin.getLogger().warning("使用反射获取MinePay事件信息失败，尝试备用方法: " + e.getMessage());
                
                // 备用方法：尝试从事件字段直接获取信息
                try {
                    // 尝试获取玩家名称字段
                    java.lang.reflect.Field playerField = event.getClass().getDeclaredField("player");
                    playerField.setAccessible(true);
                    Object playerObj = playerField.get(event);
                    if (playerObj instanceof org.bukkit.entity.Player) {
                        playerName = ((org.bukkit.entity.Player) playerObj).getName();
                    } else if (playerObj instanceof String) {
                        playerName = (String) playerObj;
                    }
                    
                    // 尝试获取金额字段
                    java.lang.reflect.Field amountField = event.getClass().getDeclaredField("amount");
                    amountField.setAccessible(true);
                    Object amountObj = amountField.get(event);
                    if (amountObj instanceof Number) {
                        pointsAmount = ((Number) amountObj).doubleValue();
                    }
                    
                } catch (Exception ex) {
                    plugin.getLogger().warning("备用方法也失败: " + ex.getMessage());
                    return;
                }
            }
            
            // 验证获取到的数据
            if (playerName == null || pointsAmount <= 0) {
                plugin.getLogger().warning("无法从MinePay事件中获取有效数据");
                return;
            }
            
            // 如果能够获取到订单类型，检查是否为点券订单
            if (tradeTypeName != null && !"POINT".equals(tradeTypeName)) {
                plugin.getLogger().info("非点券订单，跳过处理: " + tradeTypeName);
                return;
            }
            
            plugin.getLogger().info("检测到MinePay点券充值: " + playerName + " 充值 " + pointsAmount + " 点券");
            
            // 调用ALInvite API处理返点
            final String finalPlayerName = playerName;
            final double finalPointsAmount = pointsAmount;
            
            // 使用Folia兼容的异步调度
            plugin.getFoliaScheduler().runAsync(() -> {
                CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(finalPlayerName, finalPointsAmount);
                
                result.thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("MinePay充值返点处理成功: " + finalPlayerName);
                    } else {
                        plugin.getLogger().warning("MinePay充值返点处理失败: " + finalPlayerName);
                    }
                });
            });
            
        } catch (Exception e) {
            plugin.getLogger().warning("处理MinePay充值事件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查MinePay插件是否可用
     */
    public boolean isMinePayEnabled() {
        return minePayEnabled;
    }
}