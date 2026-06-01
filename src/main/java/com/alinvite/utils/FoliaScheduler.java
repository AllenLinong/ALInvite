package com.alinvite.utils;

import com.alinvite.ALInvite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Folia兼容的调度器工具类
 * 提供统一的接口来处理Folia和传统服务器的调度差异
 */
public class FoliaScheduler {
    
    private final ALInvite plugin;
    private final boolean isFolia;
    
    public FoliaScheduler(ALInvite plugin) {
        this.plugin = plugin;
        // 检测是否为Folia服务器
        this.isFolia = checkFolia();
        
        if (isFolia) {
            plugin.getLogger().info("检测到Folia服务器，启用区域调度支持");
        } else {
            plugin.getLogger().info("检测到传统服务器，使用标准调度器");
        }
    }
    
    /**
     * 检测是否为Folia服务器
     */
    private boolean checkFolia() {
        try {
            // Folia特有的类
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 异步执行任务
     */
    public void runAsync(Runnable task) {
        if (isFolia) {
            // Folia：使用反射调用全局调度器
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("run", JavaPlugin.class, java.util.function.Consumer.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run());
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        } else {
            // 传统服务器
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * 在主线程执行任务
     */
    public void runSync(Runnable task) {
        if (isFolia) {
            // Folia：使用反射调用全局调度器
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("run", JavaPlugin.class, java.util.function.Consumer.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run());
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            // 传统服务器
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 在玩家所在区域执行任务（Folia专用）
     */
    public void runAtPlayer(Player player, Runnable task) {
        if (isFolia) {
            // Folia：使用反射调用玩家调度器
            try {
                Object playerScheduler = player.getClass().getMethod("getScheduler").invoke(player);
                playerScheduler.getClass().getMethod("run", JavaPlugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(playerScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), null);
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia玩家调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            // 传统服务器：在主线程执行
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 延迟执行任务
     */
    public void runLater(Runnable task, long delayTicks) {
        if (isFolia) {
            // Folia：使用反射调用全局调度器
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, java.util.function.Consumer.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), delayTicks);
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            // 传统服务器
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * 在玩家区域延迟执行任务（Folia专用）
     */
    public void runAtPlayerLater(Player player, Runnable task, long delayTicks) {
        if (isFolia) {
            // Folia：使用反射调用玩家调度器
            try {
                Object playerScheduler = player.getClass().getMethod("getScheduler").invoke(player);
                playerScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, java.util.function.Consumer.class, Runnable.class, long.class)
                    .invoke(playerScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), null, delayTicks);
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia玩家调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            }
        } else {
            // 传统服务器：在主线程延迟执行
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * 定时执行任务
     */
    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            // Folia：使用反射调用全局调度器
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                globalScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, java.util.function.Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<?>) t -> task.run(), delayTicks, periodTicks);
            } catch (Exception e) {
                // 如果反射失败，回退到传统调度
                plugin.getLogger().warning("Folia调度器调用失败，回退到传统调度: " + e.getMessage());
                Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            }
        } else {
            // 传统服务器
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }
    
    /**
     * 是否为Folia服务器
     */
    public boolean isFolia() {
        return isFolia;
    }
    
    /**
     * 获取服务器类型信息
     */
    public String getServerType() {
        return isFolia ? "Folia" : "传统服务器";
    }
    
    /**
     * 检查当前线程是否在正确的区域执行
     */
    public boolean isInCorrectRegion(Player player) {
        if (!isFolia) {
            return true; // 传统服务器不需要区域检查
        }
        
        try {
            // Folia：检查当前线程是否在玩家所在区域
            Class<?> regionizedServerClass = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Object currentRegion = regionizedServerClass.getMethod("getCurrentRegion").invoke(null);
            
            if (currentRegion == null) {
                // 不在任何区域，可能是全局任务
                return true;
            }
            
            // TODO: 更精确的区域检查
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("区域检查失败: " + e.getMessage());
            return true; // 检查失败时默认允许执行
        }
    }
}