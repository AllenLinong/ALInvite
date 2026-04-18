package com.alinvite.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SchedulerUtils {

    private static final boolean IS_FOLIA;

    static {
        String serverName = Bukkit.getServer().getName();
        IS_FOLIA = serverName != null && serverName.equalsIgnoreCase("Folia");
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
            runTaskAsyncFolia(plugin, runnable);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runTaskTimer(Plugin plugin, Runnable runnable, long delay, long period) {
        if (isFolia()) {
            runTaskTimerFolia(plugin, runnable, delay, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        }
    }

    public static void runTaskTimerAsync(Plugin plugin, Runnable runnable, long delay, long period) {
        if (isFolia()) {
            runTaskTimerAsyncFolia(plugin, runnable, delay, period);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period);
        }
    }

    private static void runTaskFolia(Plugin plugin, Runnable runnable) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runTask", Plugin.class, Runnable.class).invoke(scheduler, plugin, runnable);
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private static void runTaskLaterFolia(Plugin plugin, Runnable runnable, long delay) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runTaskLater", Plugin.class, Runnable.class, long.class).invoke(scheduler, plugin, runnable, delay);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    private static void runTaskAsyncFolia(Plugin plugin, Runnable runnable) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runTaskAsynchronously", Plugin.class, Runnable.class).invoke(scheduler, plugin, runnable);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    private static void runTaskTimerFolia(Plugin plugin, Runnable runnable, long delay, long period) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runTaskTimer", Plugin.class, Runnable.class, long.class, long.class).invoke(scheduler, plugin, runnable, delay, period);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        }
    }

    private static void runTaskTimerAsyncFolia(Plugin plugin, Runnable runnable, long delay, long period) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("runTaskTimerAsynchronously", Plugin.class, Runnable.class, long.class, long.class).invoke(scheduler, plugin, runnable, delay, period);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period);
        }
    }
}
