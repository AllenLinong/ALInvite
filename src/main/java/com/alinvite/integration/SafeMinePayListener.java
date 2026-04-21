package com.alinvite.integration;

import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletableFuture;

/**
 * 安全的MinePay事件监听器
 * 避免使用抽象事件类，确保Paper服务器兼容性
 */
public class SafeMinePayListener implements Listener {

    private final ALInvite plugin;

    public SafeMinePayListener(ALInvite plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听MinePay充值成功事件
     * 使用反射安全地处理事件
     */
    @EventHandler
    public void onMinePaySuccess(org.bukkit.event.Event event) {
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
            
            try {
                // 尝试获取玩家名称
                Object tradeInfo = event.getClass().getMethod("getTradeInfo").invoke(event);
                if (tradeInfo != null) {
                    playerName = (String) tradeInfo.getClass().getMethod("getPlayerName").invoke(tradeInfo);
                    int priceInCents = (int) tradeInfo.getClass().getMethod("getPrice").invoke(tradeInfo);
                    pointsAmount = priceInCents / 100.0;
                    
                    // 检查交易类型
                    Object tradeType = tradeInfo.getClass().getMethod("getTradeType").invoke(tradeInfo);
                    String tradeTypeName = tradeType.toString();
                    
                    // 只处理点券充值订单
                    if (!tradeTypeName.toLowerCase().contains("point")) {
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("使用反射获取MinePay事件信息失败，尝试备用方法: " + e.getMessage());
                
                // 备用方法：从事件字段中获取信息
                try {
                    playerName = (String) event.getClass().getField("playerName").get(event);
                    pointsAmount = (double) event.getClass().getField("amount").get(event);
                } catch (Exception ex) {
                    plugin.getLogger().warning("无法从MinePay事件中获取有效数据");
                    return;
                }
            }
            
            if (playerName != null && pointsAmount > 0) {
                plugin.getLogger().info("检测到MinePay点券充值: " + playerName + " 充值 " + pointsAmount + " 点券");
                
                // 调用 ALInvite API 处理返点
                CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
                    playerName, 
                    pointsAmount
                );
                
                final String finalPlayerName = playerName;
                result.thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("MinePay充值返点处理成功: " + finalPlayerName);
                    } else {
                        plugin.getLogger().warning("MinePay充值返点处理失败: " + finalPlayerName);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理MinePay充值事件失败: " + e.getMessage());
        }
    }
}