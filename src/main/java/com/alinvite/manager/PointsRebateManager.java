package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 点券充值返点管理器
 * 负责处理玩家点券充值、计算返点比例、发放返点奖励等功能
 * 包含完善的防重复机制和跨服同步支持
 */
public class PointsRebateManager {
    private final ALInvite plugin;
    private final DatabaseManager database;
    
    /**
     * 构造函数
     * @param plugin 主插件实例
     */
    public PointsRebateManager(ALInvite plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabaseManager();
    }
    
    /**
     * 处理点券充值返点（核心方法）
     * 完整的充值返点流程：参数验证 → 防重复检查 → 点券发放 → 返点处理
     * 
     * @param operator 操作者名称（执行命令的玩家或控制台）
     * @param targetPlayer 目标玩家名称（接收点券的玩家）
     * @param amount 充值点券数量
     * @param skipRebate 是否跳过返点处理（用于测试或特殊情况）
     * @return CompletableFuture<Boolean> 处理结果，true表示成功
     */
    public CompletableFuture<Boolean> processRecharge(String operator, String targetPlayer, double amount, boolean skipRebate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 参数验证 - 检查玩家存在性和金额合理性
                if (!validateParameters(targetPlayer, amount)) {
                    return false;
                }
                
                // 2. 防重复检查 - 防止跨服重复充值和时间窗口内重复操作
                if (!checkAntiDuplicate(targetPlayer, amount)) {
                    plugin.getLogger().warning("防重复检查失败，可能是重复交易: " + targetPlayer);
                    return false;
                }
                
                // 3. 执行点券发放 - 调用实际的点券插件命令
                if (!executePointsCommand(targetPlayer, amount)) {
                    return false;
                }
                
                // 4. 处理返点 - 计算返点比例并发放给邀请人（如果不跳过）
                if (!skipRebate) {
                    return processRebate(operator, targetPlayer, amount);
                }
                
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().severe("处理充值返点失败: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 参数验证
     */
    private boolean validateParameters(String playerName, double amount) {
        // 检查玩家是否存在
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getLogger().warning("玩家不存在: " + playerName);
            return false;
        }
        
        // 检查金额范围
        double minAmount = plugin.getConfigManager().getConfig()
            .getDouble("points_rebate.limits.min_amount", 10.0);
        
        if (amount < minAmount) {
            plugin.getLogger().warning("充值金额过小: " + amount);
            return false;
        }
        
        return true;
    }
    
    /**
     * 防重复检查 - 跨服防重复，防止同一笔充值在不同服务器上重复发放返点
     * 使用玩家UUID和充值金额生成唯一标识，检查数据库中是否已存在相同交易
     * 默认启用跨服防重复功能，无需配置
     */
    private boolean checkAntiDuplicate(String playerName, double amount) {
        UUID playerUuid = getPlayerUuid(playerName);
        if (playerUuid == null) {
            return false;
        }
        
        // 检查是否启用防重复功能
        boolean antiDuplicateEnabled = plugin.getConfigManager().getConfig()
            .getBoolean("points_rebate.anti_duplicate.enabled", true);
        
        if (!antiDuplicateEnabled) {
            return true; // 防重复功能已禁用
        }
        
        // 使用全局交易标识检查跨服重复
        String transactionKey = generateTransactionKey(playerUuid, amount);
        boolean isDuplicate = database.checkCrossServerDuplicate(transactionKey).join();
        
        if (isDuplicate) {
            plugin.getLogger().warning("跨服防重复：检测到重复充值，玩家=" + playerName + ", 金额=" + amount);
            return false;
        }
        
        return true;
    }
    
    /**
     * 执行点券发放命令
     */
    private boolean executePointsCommand(String playerName, double amount) {
        try {
            // 获取配置的点券命令
            String command = plugin.getConfigManager().getConfig()
                .getString("points_rebate.points_command", "points give {player} {amount}");
            
            // 替换占位符
            command = command.replace("{player}", playerName)
                           .replace("{amount}", String.valueOf((int)amount));
            
            // 在Folia环境中，确保命令在主线程中执行
            if (Bukkit.isPrimaryThread()) {
                // 当前在主线程，直接执行
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                if (success) {
                    plugin.getLogger().info("点券发放成功: " + playerName + " 获得 " + amount + " 点券");
                } else {
                    plugin.getLogger().warning("点券发放失败: " + command);
                }
                
                return success;
            } else {
                // 当前在异步线程，使用SchedulerUtils在主线程执行
                // 创建final变量供lambda表达式使用
                final String finalCommand = command;
                final String finalPlayerName = playerName;
                final double finalAmount = amount;
                
                java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
                
                com.alinvite.utils.SchedulerUtils.runTask(plugin, () -> {
                    try {
                        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                        
                        if (success) {
                            plugin.getLogger().info("点券发放成功: " + finalPlayerName + " 获得 " + finalAmount + " 点券");
                        } else {
                            plugin.getLogger().warning("点券发放失败: " + finalCommand);
                        }
                        
                        future.complete(success);
                    } catch (Exception e) {
                        plugin.getLogger().severe("执行点券命令失败: " + e.getMessage());
                        future.complete(false);
                    }
                });
                
                return future.get(); // 等待命令执行完成
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("执行点券命令失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理返点逻辑 - 核心返点发放流程
     * 1. 查找充值玩家的邀请人
     * 2. 根据邀请人权限计算返点比例
     * 3. 检查每日返点上限
     * 4. 发放返点点券给邀请人
     * 
     * @param operator 操作者
     * @param targetPlayer 充值玩家
     * @param amount 充值金额
     * @return 返点处理是否成功
     */
    private boolean processRebate(String operator, String targetPlayer, double amount) {
        UUID targetUuid = getPlayerUuid(targetPlayer);
        if (targetUuid == null) {
            plugin.getLogger().warning("无法获取玩家UUID: " + targetPlayer);
            return false;
        }
        
        // 1. 查找邀请人 - 从数据库查询谁邀请了该玩家
        UUID inviterUuid = database.getInviter(targetUuid).join();
        if (inviterUuid == null) {
            plugin.getLogger().info("玩家 " + targetPlayer + " 没有邀请人，跳过返点");
            return true; // 没有邀请人，充值成功但无返点
        }
        
        // 2. 获取返点比例 - 根据邀请人的权限组确定返点比例
        Player inviter = Bukkit.getPlayer(inviterUuid);
        double rebateRate = getRebateRate(inviter);
        double rebateAmount = amount * rebateRate;
        
        // 检查返点金额是否有效
        if (rebateAmount <= 0) {
            plugin.getLogger().info("返点金额为0（可能是管理员或比例配置为0），跳过发放");
            return true;
        }
        
        // 3. 检查每日返点上限 - 防止单个玩家一天内获得过多返点
        if (exceedsDailyLimit(inviterUuid, rebateAmount)) {
            plugin.getLogger().warning("邀请人 " + inviterUuid + " 今日返点已达上限");
            return true; // 超过上限，充值成功但无返点
        }
        
        // 4. 发放返点 - 调用点券插件命令给邀请人发放返点
        return giveRebatePoints(inviterUuid, rebateAmount, operator, targetPlayer, amount);
    }
    
    /**
     * 根据玩家权限获取返点比例
     * 使用配置的权限前缀检查玩家拥有的权限组，按权重从高到低检查
     * base权限组是给所有玩家的默认返点，无需权限检查
     */
    private double getRebateRate(Player player) {
        if (player == null) {
            return getDefaultRate();
        }
        
        // 获取权限前缀配置
        String permissionPrefix = plugin.getConfigManager().getConfig()
            .getString("points_rebate.permission_prefix", "alinvite.rebate");
        
        // 按权重从高到低检查权限（从权重最高的开始检查）
        // 注意：base权限组是给所有玩家的默认返点，无需权限检查
        String[] groups = {"admin", "mvip", "svip", "vip", "default"};
        
        // 先检查高权重权限组
        for (String group : groups) {
            String permission = permissionPrefix + "." + group;
            if (player.hasPermission(permission)) {
                // 获取该权限组的返点比例
                String configPath = "points_rebate.rebate_rates." + group + ".rate";
                double rate = plugin.getConfigManager().getConfig().getDouble(configPath, 0.10);
                
                // 获取权重值用于日志记录
                String weightPath = "points_rebate.rebate_rates." + group + ".weight";
                int weight = plugin.getConfigManager().getConfig().getInt(weightPath, 1);
                
                plugin.getLogger().info("玩家 " + player.getName() + " 拥有权限 " + permission + 
                    " (权重: " + weight + ")，返点比例: " + (rate * 100) + "%");
                
                return rate;
            }
        }
        
        // 所有玩家默认获得基础返点（无需任何权限，无需检查 alinvite.rebate.base 权限）
        double baseRate = plugin.getConfigManager().getConfig()
            .getDouble("points_rebate.rebate_rates.base.rate", 0.05);
        
        // 获取基础返点的权重值用于日志记录
        String baseWeightPath = "points_rebate.rebate_rates.base.weight";
        int baseWeight = plugin.getConfigManager().getConfig().getInt(baseWeightPath, 0);
        
        plugin.getLogger().info("玩家 " + player.getName() + " 使用基础返点比例: " + (baseRate * 100) + 
            "% (权重: " + baseWeight + ")，无需权限检查");
        
        return baseRate;
    }
    
    /**
     * 检查每日返点上限
     */
    private boolean exceedsDailyLimit(UUID playerUuid, double newRebate) {
        double dailyLimit = plugin.getConfigManager().getConfig()
            .getDouble("points_rebate.limits.max_rebate_per_day", 1000.0);
        
        double todayRebate = database.getTodayRebateTotal(playerUuid).join();
        
        return (todayRebate + newRebate) > dailyLimit;
    }
    
    /**
     * 发放返点点券
     */
    private boolean giveRebatePoints(UUID playerUuid, double amount, String operator, String targetPlayer, double originalAmount) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            plugin.getLogger().warning("邀请人不在线，无法发放返点: " + playerUuid);
            return false;
        }
        
        String command = plugin.getConfigManager().getConfig()
            .getString("points_rebate.points_command", "points give {player} {amount}");
        
        command = command.replace("{player}", player.getName())
                       .replace("{amount}", String.valueOf((int)amount));
        
        // 确保命令在主线程中执行
        if (Bukkit.isPrimaryThread()) {
            // 当前在主线程，直接执行
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            if (success) {
                plugin.getLogger().info("返点发放成功: " + player.getName() + " 获得 " + amount + " 点券");
                
                // 发送消息给玩家
                String message = plugin.getConfigManager().getMessage("points_rebate.rebate_given")
                    .replace("{player}", targetPlayer)
                    .replace("{amount}", String.valueOf((int)originalAmount))
                    .replace("{rebate_amount}", String.valueOf((int)amount));
                
                player.sendMessage(message);
            }
            
            return success;
        } else {
            // 当前在异步线程，使用SchedulerUtils在主线程执行
            // 创建final变量供lambda表达式使用
            final String finalCommand = command;
            final Player finalPlayer = player;
            final String finalTargetPlayer = targetPlayer;
            final double finalOriginalAmount = originalAmount;
            final double finalAmount = amount;
            
            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            
            com.alinvite.utils.SchedulerUtils.runTask(plugin, () -> {
                try {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    
                    if (success) {
                        plugin.getLogger().info("返点发放成功: " + finalPlayer.getName() + " 获得 " + finalAmount + " 点券");
                        
                        // 发送消息给玩家
                        String message = plugin.getConfigManager().getMessage("points_rebate.rebate_given")
                            .replace("{player}", finalTargetPlayer)
                            .replace("{amount}", String.valueOf((int)finalOriginalAmount))
                            .replace("{rebate_amount}", String.valueOf((int)finalAmount));
                        
                        finalPlayer.sendMessage(message);
                    }
                    
                    future.complete(success);
                } catch (Exception e) {
                    plugin.getLogger().severe("执行返点命令失败: " + e.getMessage());
                    future.complete(false);
                }
            });
            
            try {
                return future.get(); // 等待命令执行完成
            } catch (Exception e) {
                plugin.getLogger().severe("等待返点命令执行失败: " + e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * 生成跨服交易标识（用于防重复检查）
     * 使用玩家UUID和充值金额生成唯一标识，确保同一笔充值在不同服务器上标识相同
     */
    private String generateTransactionKey(UUID playerUuid, double amount) {
        // 使用完整的UUID和精确金额，确保跨服一致性
        return String.format("rebate_%s_%.2f", 
            playerUuid.toString(),
            amount);
    }
    
    /**
     * 生成简单交易标识（用于日志记录）
     */
    private String generateTransactionLogId(UUID playerUuid, double amount) {
        return String.format("%s_%.2f", 
            playerUuid.toString().substring(0, 8), // 使用UUID前8位
            amount);
    }
    
    /**
     * 获取玩家UUID
     */
    private UUID getPlayerUuid(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        return player != null ? player.getUniqueId() : null;
    }
    
    /**
     * 获取默认返点比例
     */
    private double getDefaultRate() {
        return plugin.getConfigManager().getConfig()
            .getDouble("points_rebate.rebate_rates.default.rate", 0.10);
    }
}