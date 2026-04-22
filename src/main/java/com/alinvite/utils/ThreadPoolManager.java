package com.alinvite.utils;

import com.alinvite.ALInvite;
import org.bukkit.Bukkit;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器
 * 提供专用的线程池用于异步任务处理，避免使用默认的ForkJoinPool
 */
public class ThreadPoolManager {
    
    private final ALInvite plugin;
    private ExecutorService asyncExecutor;
    private ScheduledExecutorService scheduledExecutor;
    
    public ThreadPoolManager(ALInvite plugin) {
        this.plugin = plugin;
        initThreadPools();
    }
    
    /**
     * 初始化线程池
     */
    private void initThreadPools() {
        // 计算合适的线程池大小
        int corePoolSize = calculateOptimalPoolSize();
        int maxPoolSize = corePoolSize * 2;
        
        // 创建异步任务线程池
        asyncExecutor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 队列容量
            new NamedThreadFactory("alinvite-async"),
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
        );
        
        // 创建定时任务线程池
        scheduledExecutor = Executors.newScheduledThreadPool(
            Math.max(2, corePoolSize / 2),
            new NamedThreadFactory("alinvite-scheduled")
        );
        
        plugin.getLogger().info("线程池初始化完成 - 核心线程数: " + corePoolSize + ", 最大线程数: " + maxPoolSize);
    }
    
    /**
     * 计算最优线程池大小
     */
    private int calculateOptimalPoolSize() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxPlayers = Bukkit.getMaxPlayers();
        
        // 基础大小：CPU核心数
        int baseSize = Math.max(2, availableProcessors);
        
        // 根据服务器规模调整
        if (maxPlayers > 100) {
            // 大型服务器：增加线程数
            return Math.min(baseSize * 2, 16);
        } else if (maxPlayers > 50) {
            // 中型服务器
            return Math.min(baseSize + 2, 12);
        } else {
            // 小型服务器
            return Math.min(baseSize, 8);
        }
    }
    
    /**
     * 获取异步任务执行器
     */
    public ExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }
    
    /**
     * 获取定时任务执行器
     */
    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }
    
    /**
     * 提交异步任务
     */
    public <T> CompletableFuture<T> submitAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }
    
    /**
     * 提交异步任务（无返回值）
     */
    public CompletableFuture<Void> submitAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }
    
    /**
     * 获取线程池状态信息
     */
    public String getPoolStatus() {
        if (asyncExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) asyncExecutor;
            return String.format(
                "线程池状态: 活跃线程=%d, 核心线程=%d, 最大线程=%d, 队列大小=%d, 完成任务=%d",
                executor.getActiveCount(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
            );
        }
        return "线程池状态: 未知类型";
    }
    
    /**
     * 优雅关闭线程池
     */
    public void shutdown() {
        plugin.getLogger().info("正在关闭线程池...");
        
        // 先关闭定时任务
        scheduledExecutor.shutdown();
        
        // 然后关闭异步任务线程池
        asyncExecutor.shutdown();
        
        try {
            // 等待任务完成
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("线程池关闭超时，强制关闭...");
                asyncExecutor.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            plugin.getLogger().info("线程池关闭完成");
        } catch (InterruptedException e) {
            plugin.getLogger().warning("线程池关闭被中断");
            Thread.currentThread().interrupt();
            asyncExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
    }
    
    /**
     * 命名的线程工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        NamedThreadFactory(String poolName) {
            namePrefix = poolName + "-";
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true); // 设置为守护线程
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}