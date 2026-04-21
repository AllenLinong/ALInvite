package com.alinvite.integration;

import com.alinvite.api.ALInviteAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.CompletableFuture;

/**
 * 第三方插件集成示例
 * 演示如何与ALInvite插件进行联动
 */
public class ThirdPartyIntegrationExample implements Listener {

    /**
     * 方式一：直接API调用（推荐）
     * 当第三方插件发放点券时，自动触发返点处理
     * 
     * @param player 充值玩家
     * @param amount 充值金额
     */
    public void onPointsGiven(Player player, double amount) {
        CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
            player.getName(), 
            amount
        );
        
        result.thenAccept(success -> {
            if (success) {
                Bukkit.getLogger().info("第三方充值返点处理成功: " + player.getName() + " 充值 " + amount + " 点券");
            } else {
                Bukkit.getLogger().warning("第三方充值返点处理失败: " + player.getName());
            }
        });
    }

    /**
     * 方式二：事件监听模式
     * 监听第三方充值事件，自动处理返点
     * 
     * @param event 第三方充值事件（需要第三方插件提供）
     */
    @EventHandler
    public void onThirdPartyRecharge(ThirdPartyPointsRechargeEvent event) {
        // 假设第三方插件提供了这样的自定义事件
        String playerName = event.getPlayerName();
        double amount = event.getAmount();
        
        ALInviteAPI.processPointsRecharge(playerName, amount)
                .thenAccept(success -> {
                    if (success) {
                        event.setRebateProcessed(true);
                    }
                });
    }

    /**
     * 方式三：定时批量处理
     * 适用于需要批量处理充值记录的场景
     * 
     * @param rechargeRecords 充值记录列表
     */
    public void processBatchRecharges(java.util.List<RechargeRecord> rechargeRecords) {
        for (RechargeRecord record : rechargeRecords) {
            ALInviteAPI.processPointsRecharge(record.getPlayerName(), record.getAmount())
                .thenAccept(success -> {
                    if (success) {
                        record.markAsProcessed();
                    }
                });
        }
    }
}

/**
 * 第三方充值事件（示例）
 * 第三方插件需要提供类似的事件
 */
class ThirdPartyPointsRechargeEvent extends org.bukkit.event.Event {
    private final String playerName;
    private final double amount;
    private boolean rebateProcessed = false;
    
    public ThirdPartyPointsRechargeEvent(String playerName, double amount) {
        this.playerName = playerName;
        this.amount = amount;
    }
    
    public String getPlayerName() { return playerName; }
    public double getAmount() { return amount; }
    public boolean isRebateProcessed() { return rebateProcessed; }
    public void setRebateProcessed(boolean processed) { this.rebateProcessed = processed; }
    
    @Override
    public org.bukkit.event.HandlerList getHandlers() {
        return getHandlerList();
    }
    
    public static org.bukkit.event.HandlerList getHandlerList() {
        return new org.bukkit.event.HandlerList();
    }
}

/**
 * 充值记录（示例）
 * 用于批量处理场景
 */
class RechargeRecord {
    private final String playerName;
    private final double amount;
    private boolean processed = false;
    
    public RechargeRecord(String playerName, double amount) {
        this.playerName = playerName;
        this.amount = amount;
    }
    
    public String getPlayerName() { return playerName; }
    public double getAmount() { return amount; }
    public boolean isProcessed() { return processed; }
    public void markAsProcessed() { this.processed = true; }
}