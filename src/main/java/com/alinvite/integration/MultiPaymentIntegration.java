package com.alinvite.integration;

import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.CompletableFuture;

/**
 * 多支付插件集成支持
 * 支持MinePay和SweetCheckout等主流支付插件
 */
public class MultiPaymentIntegration implements Listener {

    private final ALInvite plugin;
    private boolean minePayEnabled = false;
    private boolean sweetCheckoutEnabled = false;

    public MultiPaymentIntegration(ALInvite plugin) {
        this.plugin = plugin;
        detectPaymentPlugins();
    }

    /**
     * 检测已安装的支付插件
     */
    private void detectPaymentPlugins() {
        // 检测MinePay插件
        if (Bukkit.getPluginManager().getPlugin("MinePay") != null) {
            minePayEnabled = true;
            plugin.getLogger().info("检测到 MinePay 插件，已启用充值返点集成");
        }

        // 检测SweetCheckout插件
        if (Bukkit.getPluginManager().getPlugin("SweetCheckout") != null) {
            sweetCheckoutEnabled = true;
            plugin.getLogger().info("检测到 SweetCheckout 插件，已启用充值返点集成");
        }

        if (!minePayEnabled && !sweetCheckoutEnabled) {
            plugin.getLogger().info("未检测到支持的支付插件，充值返点集成功能将禁用");
        }
    }

    // ==================== MinePay 集成 ====================

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
            // 检查事件类型
            String eventName = event.getClass().getSimpleName();
            if (!eventName.contains("MinePaySuccess")) {
                return;
            }

            // 使用反射获取订单信息
            java.lang.reflect.Method getTradeInfoMethod = event.getClass().getMethod("getTradeInfo");
            Object tradeInfo = getTradeInfoMethod.invoke(event);
            
            // 获取玩家名称
            java.lang.reflect.Method getPlayerNameMethod = tradeInfo.getClass().getMethod("getPlayerName");
            String playerName = (String) getPlayerNameMethod.invoke(tradeInfo);
            
            // 获取充值金额（单位：分，需要转换为点券）
            java.lang.reflect.Method getPriceMethod = tradeInfo.getClass().getMethod("getPrice");
            int priceInCents = (int) getPriceMethod.invoke(tradeInfo);
            double pointsAmount = priceInCents / 100.0; // 转换为点券数量
            
            // 获取订单类型
            java.lang.reflect.Method getTradeTypeMethod = tradeInfo.getClass().getMethod("getTradeType");
            Object tradeType = getTradeTypeMethod.invoke(tradeInfo);
            
            // 检查是否为点券订单
            java.lang.reflect.Method nameMethod = tradeType.getClass().getMethod("name");
            String tradeTypeName = (String) nameMethod.invoke(tradeType);
            
            // 只处理点券订单，过滤掉礼包、VIP等非点券订单
            if ("POINT".equals(tradeTypeName)) {
                plugin.getLogger().info("检测到MinePay点券充值: " + playerName + " 充值 " + pointsAmount + " 点券");
                
                // 调用ALInvite API处理返点
                processRecharge(playerName, pointsAmount, "MinePay");
            } else {
                plugin.getLogger().info("非点券订单，跳过处理: " + tradeTypeName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("处理MinePay充值事件失败: " + e.getMessage());
        }
    }

    // ==================== SweetCheckout 集成 ====================

    /**
     * 监听SweetCheckout支付完成事件
     * 当玩家通过SweetCheckout成功支付时，自动触发返点处理
     * 
     * @param event SweetCheckout支付完成事件
     */
    @EventHandler
    public void onSweetCheckoutPayment(org.bukkit.event.Event event) {
        if (!sweetCheckoutEnabled) {
            return;
        }

        try {
            // 检查事件类型
            String eventName = event.getClass().getSimpleName();
            if (!eventName.contains("Payment") && !eventName.contains("Checkout")) {
                return;
            }

            // 使用反射获取玩家和金额信息
            String playerName = null;
            double pointsAmount = 0.0;
            
            // 尝试多种方法获取信息
            for (java.lang.reflect.Method method : event.getClass().getMethods()) {
                String methodName = method.getName().toLowerCase();
                
                if (methodName.contains("player") && !methodName.contains("get")) {
                    try {
                        Object result = method.invoke(event);
                        if (result instanceof String) {
                            playerName = (String) result;
                        }
                    } catch (Exception ignored) {}
                }
                
                if (methodName.contains("amount") || methodName.contains("money")) {
                    try {
                        Object result = method.invoke(event);
                        if (result instanceof Number) {
                            pointsAmount = ((Number) result).doubleValue();
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            if (playerName != null && pointsAmount > 0) {
                plugin.getLogger().info("检测到SweetCheckout充值: " + playerName + " 充值 " + pointsAmount + " 点券");
                
                // 调用ALInvite API处理返点
                processRecharge(playerName, pointsAmount, "SweetCheckout");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("处理SweetCheckout支付事件失败: " + e.getMessage());
        }
    }

    // ==================== 通用处理逻辑 ====================

    /**
     * 统一处理充值返点逻辑
     */
    private void processRecharge(String playerName, double amount, String pluginName) {
        // 记录充值日志
        plugin.getLogger().info("检测到第三方充值: " + playerName + " 通过 " + pluginName + " 充值 " + amount + " 点券");

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
     * 检查支付插件是否可用
     */
    public boolean isAnyPaymentPluginEnabled() {
        return minePayEnabled || sweetCheckoutEnabled;
    }

    /**
     * 获取支持的插件列表
     */
    public String getEnabledPlugins() {
        StringBuilder plugins = new StringBuilder();
        if (minePayEnabled) plugins.append("MinePay ");
        if (sweetCheckoutEnabled) plugins.append("SweetCheckout ");
        return plugins.toString().trim();
    }
}