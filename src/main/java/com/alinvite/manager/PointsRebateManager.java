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
        
        // 检查邀请人是否启用了返点功能
        DatabaseManager.PlayerData inviterData = database.getPlayerData(inviterUuid).join();
        if (inviterData != null && !inviterData.rebateEnabled) {
            plugin.getLogger().info("邀请人 " + inviterUuid + " 返点功能被禁用，跳过返点");
            return true; // 返点功能被禁用，充值成功但无返点
        }
        
        // 2. 获取返点比例 - 根据邀请人的权限组确定返点比例
        Player inviter = Bukkit.getPlayer(inviterUuid);
        double rebateRate = getRebateRate(inviter);
        
        // 检查是否为贡献返点模式（负值表示贡献模式）
        boolean isContributionMode = (rebateRate < 0);
        if (isContributionMode) {
            rebateRate = -rebateRate; // 转换为正的比例值
        }
        
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
        
        // 4. 根据模式发放返点
        if (isContributionMode) {
            // 贡献返点模式：只记录不发放点券
            return recordContributionRebate(inviterUuid, rebateAmount, operator, targetPlayer, amount);
        } else {
            // 正常模式：发放点券返点
            return giveRebatePoints(inviterUuid, rebateAmount, operator, targetPlayer, amount);
        }
    }
    
    /**
     * 获取实际返点比例（忽略现金模式设置）
     */
    private double getActualRebateRate(Player player) {
        if (player == null) {
            return getDefaultRate();
        }
        
        String permissionPrefix = plugin.getConfigManager().getConfig()
            .getString("points_rebate.permission_prefix", "alinvite.rebate");
        
        String[] groups = {"contribution", "admin", "mvip", "svip", "vip", "default"};
        
        for (String group : groups) {
            String permission = permissionPrefix + "." + group;
            if (player.hasPermission(permission)) {
                String configPath = "points_rebate.rebate_rates." + group + ".rate";
                return plugin.getConfigManager().getConfig().getDouble(configPath, 0.10);
            }
        }
        
        return getDefaultRate();
    }
    
    /**
     * 记录贡献返点（不发放点券，只记录到数据库）
     */
    private boolean recordContributionRebate(UUID playerUuid, double amount, String operator, String targetPlayer, double originalAmount) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            plugin.getLogger().warning("邀请人不在线，无法记录贡献返点: " + playerUuid);
            return false;
        }
        
        try {
            // 更新数据库中的贡献返点金额
            boolean success = database.addContributionAmount(playerUuid, amount).join();
            
            if (success) {
                plugin.getLogger().info("贡献返点记录成功: " + player.getName() + " 获得 " + amount + " 点券（贡献模式）");
                
                // 发送消息给玩家
                String message = plugin.getConfigManager().getMessage("points_rebate.contribution_rebate_recorded")
                    .replace("{player}", targetPlayer)
                    .replace("{amount}", String.valueOf((int)originalAmount))
                    .replace("{rebate_amount}", String.valueOf((int)amount));
                
                player.sendMessage(message);
                return true;
            } else {
                plugin.getLogger().warning("贡献返点记录失败: " + player.getName());
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("记录贡献返点失败: " + e.getMessage());
            return false;
        }
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
        
        // 定义所有权限组及其配置路径
        String[] groups = {"contribution", "admin", "mvip", "svip", "vip", "default"};
        
        // 按权重从高到低排序权限组
        java.util.List<String> sortedGroups = new java.util.ArrayList<>();
        java.util.Map<String, Integer> groupWeights = new java.util.HashMap<>();
        
        for (String group : groups) {
            String weightPath = "points_rebate.rebate_rates." + group + ".weight";
            int weight = plugin.getConfigManager().getConfig().getInt(weightPath, 1);
            groupWeights.put(group, weight);
        }
        
        // 按权重从高到低排序
        sortedGroups.addAll(java.util.Arrays.asList(groups));
        sortedGroups.sort((g1, g2) -> {
            int weight1 = groupWeights.get(g1);
            int weight2 = groupWeights.get(g2);
            return Integer.compare(weight2, weight1); // 降序排序
        });
        
        plugin.getLogger().info("权限组权重排序结果: " + sortedGroups);
        
        // 按权重从高到低检查权限
        for (String group : sortedGroups) {
            String permission = permissionPrefix + "." + group;
            if (player.hasPermission(permission)) {
                // 获取该权限组的返点比例
                String configPath = "points_rebate.rebate_rates." + group + ".rate";
                double rate = plugin.getConfigManager().getConfig().getDouble(configPath, 0.10);
                
                // 检查是否为贡献返点模式
                String contributionModePath = "points_rebate.rebate_rates." + group + ".contribution_mode";
                boolean isContributionMode = plugin.getConfigManager().getConfig().getBoolean(contributionModePath, false);
                
                // 获取权重值用于日志记录
                String weightPath = "points_rebate.rebate_rates." + group + ".weight";
                int weight = plugin.getConfigManager().getConfig().getInt(weightPath, 1);
                
                plugin.getLogger().info("玩家 " + player.getName() + " 拥有权限 " + permission + 
                    " (权重: " + weight + ", 贡献模式: " + isContributionMode + ")，返点比例: " + (rate * 100) + "%");
                
                // 如果是贡献返点模式，返回实际比例（但标记为贡献模式）
                if (isContributionMode) {
                    // 返回负值表示贡献返点模式，processRebate方法会处理
                    return -rate; // 贡献返点模式只记录不发放点券
                }
                
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