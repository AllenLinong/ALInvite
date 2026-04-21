package com.alinvite.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SchedulerUtils {

    private static final boolean IS_FOLIA;

    static {
        String serverName = Bukkit.getServer().getName();
        String serverVersion = Bukkit.getVersion();
        
        // 检测Folia及其衍生版本（包括Luminol）
        IS_FOLIA = (serverName != null && (serverName.equalsIgnoreCase("Folia") || 
                   serverName.toLowerCase().contains("folia") ||
                   serverName.toLowerCase().contains("luminol"))) ||
                   (serverVersion != null && serverVersion.toLowerCase().contains("folia"));
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static <T> T runTaskSupplied(Plugin plugin, Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
    }

    public static void runTask(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            runTaskFolia(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runTaskLater(Plugin plugin, Runnable runnable, long delay) {
        if (isFolia()) {
            runTaskLaterFolia(plugin, runnable, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    public static void runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            // Folia中直接在主线程执行，避免异步问题
            runTask(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runTaskTimer(Plugin plugin, Runnable runnable, long delay, long period) {
        if (isFolia()) {
            // Folia中直接使用延迟任务循环替代定时任务
            runTaskLater(plugin, () -> {
                runnable.run();
                runTaskTimer(plugin, runnable, period, period);
            }, delay);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        }
    }

    public static void runTaskLaterAsync(Plugin plugin, Runnable runnable, long delay) {
        if (isFolia()) {
            // Folia中直接使用同步延迟任务，避免异步问题
            runTaskLater(plugin, runnable, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    public static void runTaskTimerAsync(Plugin plugin, Runnable runnable, long delay, long period) {
        if (isFolia()) {
            // Folia不支持异步定时任务，使用同步定时任务替代
            runTaskTimer(plugin, runnable, delay, period);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period);
        }
    }

    // Folia专用方法 - 完全避免传统调度器
    private static void runTaskFolia(Plugin plugin, Runnable runnable) {
        try {
            // 直接在主线程执行
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
            } else {
                // 使用Folia的GlobalRegionScheduler
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                scheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class)
                    .invoke(scheduler, plugin, (java.util.function.Consumer<Object>) task -> runnable.run());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Folia任务执行失败: " + e.getMessage());
            // 备用方案：直接在新线程执行
            new Thread(runnable).start();
        }
    }

    private static void runTaskLaterFolia(Plugin plugin, Runnable runnable, long delay) {
        try {
            // 使用Folia的GlobalRegionScheduler的延迟任务
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class)
                .invoke(scheduler, plugin, (java.util.function.Consumer<Object>) task -> runnable.run(), delay);
        } catch (Exception e) {
            plugin.getLogger().warning("Folia延迟任务执行失败: " + e.getMessage());
            // 备用方案：使用Thread.sleep
            new Thread(() -> {
                try {
                    Thread.sleep(delay * 50); // 转换为毫秒
                    runnable.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }
}