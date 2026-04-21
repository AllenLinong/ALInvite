package com.alinvite.integration;

import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletableFuture;

/**
 * 安全的SweetCheckout事件监听器
 * 避免使用抽象事件类，确保Paper服务器兼容性
 */
public class SafeSweetCheckoutListener implements Listener {

    private final ALInvite plugin;

    public SafeSweetCheckoutListener(ALInvite plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听SweetCheckout支付完成事件
     * 使用反射安全地处理事件
     */
    @EventHandler
    public void onSweetCheckoutPayment(org.bukkit.event.Event event) {
        try {
            // 检查事件类型 - 使用更可靠的事件类名检测
            String eventName = event.getClass().getSimpleName();
            String eventPackage = event.getClass().getPackage().getName();
            
            // 检查是否是SweetCheckout相关事件
            if (!eventPackage.contains("sweetcheckout") && !eventName.toLowerCase().contains("sweetcheckout")) {
                return;
            }
            
            // 检查事件名称是否包含支付完成标识
            if (!eventName.toLowerCase().contains("payment") && !eventName.toLowerCase().contains("complete")) {
                return;
            }

            plugin.getLogger().info("检测到SweetCheckout事件: " + eventName);
            
            // 尝试使用反射获取订单信息
            String playerName = null;
            double pointsAmount = 0.0;
            
            try {
                // 尝试获取玩家对象
                Object playerObj = event.getClass().getMethod("getPlayer").invoke(event);
                if (playerObj != null) {
                    playerName = (String) playerObj.getClass().getMethod("getName").invoke(playerObj);
                    pointsAmount = (double) event.getClass().getMethod("getAmount").invoke(event);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("使用反射获取SweetCheckout事件信息失败，尝试备用方法: " + e.getMessage());
                
                // 备用方法：从事件字段中获取信息
                try {
                    playerName = (String) event.getClass().getField("playerName").get(event);
                    pointsAmount = (double) event.getClass().getField("amount").get(event);
                } catch (Exception ex) {
                    plugin.getLogger().warning("无法从SweetCheckout事件中获取有效数据");
                    return;
                }
            }
            
            if (playerName != null && pointsAmount > 0) {
                plugin.getLogger().info("检测到SweetCheckout充值: " + playerName + " 充值 " + pointsAmount + " 点券");
                
                // 调用 ALInvite API 处理返点
                CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
                    playerName, 
                    pointsAmount
                );
                
                final String finalPlayerName = playerName;
                result.thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("SweetCheckout充值返点处理成功: " + finalPlayerName);
                    } else {
                        plugin.getLogger().warning("SweetCheckout充值返点处理失败: " + finalPlayerName);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理SweetCheckout支付事件失败: " + e.getMessage());
        }
    }
}