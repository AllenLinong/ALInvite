package com.alinvite;

import com.alinvite.commands.CommandHandler;
import com.alinvite.config.ConfigManager;
import com.alinvite.database.DatabaseManager;
import com.alinvite.gui.MenuManager;
import com.alinvite.integration.SafeMinePayListener;
import com.alinvite.integration.SafeSweetCheckoutListener;
import com.alinvite.listeners.InviteListener;
import com.alinvite.listeners.LuckPermsListener;
import com.alinvite.listeners.MenuListener;
import com.alinvite.listeners.PermissionGroupRewardListener;
import com.alinvite.manager.CacheManager;
import com.alinvite.manager.InviteManager;
import com.alinvite.manager.MilestoneManager;
import com.alinvite.manager.GiftManager;
import com.alinvite.manager.PointsRebateManager;
import com.alinvite.placeholder.PlaceholderHook;
import com.alinvite.api.ALInviteAPI;
import com.alinvite.utils.SchedulerUtils;
import com.alinvite.utils.ThreadPoolManager;
import com.alinvite.utils.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ALInvite extends JavaPlugin {

    private static ALInvite instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CacheManager cacheManager;
    private InviteManager inviteManager;
    private MilestoneManager milestoneManager;
    private GiftManager giftManager;
    private MenuManager menuManager;
    private MenuListener menuListener;
    private PermissionGroupRewardListener permissionGroupRewardListener;
    private CommandHandler commandHandler;
    private PointsRebateManager pointsRebateManager;
    private ThreadPoolManager threadPoolManager;
    private FoliaScheduler foliaScheduler;

    public static ALInvite getInstance() {
        return instance;
    }

    public MenuListener getMenuListener() {
        return menuListener;
    }

    public PermissionGroupRewardListener getPermissionGroupRewardListener() {
        return permissionGroupRewardListener;
    }

    public FoliaScheduler getFoliaScheduler() {
        return foliaScheduler;
    }

    @Override
    public void onEnable() {
        try {
            instance = this;
            getLogger().info("正在启动 ALInvite 插件...");

            if (!initConfig()) {
                getLogger().severe("配置文件加载失败！插件禁用。");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!initDatabase()) {
                getLogger().severe("数据库初始化失败！插件禁用。");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            initManagers();
            initCommands();
            initListeners();
            initPlaceholder();
            initAPI();

            scheduleAnnouncementSync();
            schedulePermissionGroupCheck();

            getLogger().info("ALInvite 插件已成功启用！版本: " + getDescription().getVersion());
        } catch (Exception e) {
            getLogger().severe("插件启动过程中发生严重错误！");
            getLogger().severe("错误信息: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (cacheManager != null) {
            cacheManager.clear();
        }
        getLogger().info("ALInvite 插件已禁用。");
    }

    private boolean initConfig() {
        try {
            configManager = new ConfigManager(this);
            configManager.loadAll();
            return true;
        } catch (Exception e) {
            getLogger().severe("加载配置文件时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean initDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.init();
            return true;
        } catch (Exception e) {
            getLogger().severe("初始化数据库时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void initManagers() {
        foliaScheduler = new FoliaScheduler(this);
        threadPoolManager = new ThreadPoolManager(this);
        cacheManager = new CacheManager(this);
        inviteManager = new InviteManager(this);
        milestoneManager = new MilestoneManager(this);
        giftManager = new GiftManager(this);
        menuManager = new MenuManager(this);
        pointsRebateManager = new PointsRebateManager(this);
    }

    private void initCommands() {
        commandHandler = new CommandHandler(this);
        getCommand("alinvite").setExecutor(commandHandler);
        getCommand("alinvite").setTabCompleter(commandHandler);
    }

    private void initListeners() {
        menuListener = new MenuListener(this);
        permissionGroupRewardListener = new PermissionGroupRewardListener(this);
        Bukkit.getPluginManager().registerEvents(new InviteListener(this), this);
        Bukkit.getPluginManager().registerEvents(menuListener, this);
        Bukkit.getPluginManager().registerEvents(permissionGroupRewardListener, this);
        Bukkit.getPluginManager().registerEvents(new LuckPermsListener(this, permissionGroupRewardListener), this);
        
        // 条件注册第三方充值插件监听器（避免使用抽象事件类）
        registerThirdPartyListeners();
    }
    
    private void registerThirdPartyListeners() {
        // 使用反射安全地注册第三方插件监听器
        try {
            // 检查MinePay插件是否存在
            if (Bukkit.getPluginManager().getPlugin("MinePay") != null) {
                // 使用安全的事件监听器
                Bukkit.getPluginManager().registerEvents(new SafeMinePayListener(this), this);
                getLogger().info("已注册 MinePay 事件监听器");
            }
            
            // 检查SweetCheckout插件是否存在
            if (Bukkit.getPluginManager().getPlugin("SweetCheckout") != null) {
                // 使用安全的事件监听器
                Bukkit.getPluginManager().registerEvents(new SafeSweetCheckoutListener(this), this);
                getLogger().info("已注册 SweetCheckout 事件监听器");
            }
        } catch (Exception e) {
            getLogger().warning("注册第三方插件监听器时出错: " + e.getMessage());
        }
    }

    private void initPlaceholder() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI 集成已启用。");
        }
    }

    private void initAPI() {
        ALInviteAPI.init(this);
    }

    private void scheduleAnnouncementSync() {
        if (!configManager.getConfig().getBoolean("announcements.cross_server_sync", true)) {
            return;
        }

        int interval = configManager.getConfig().getInt("announcements.sync_interval", 5) * 20;
        SchedulerUtils.runTaskTimerAsync(this, () -> {
            databaseManager.syncAnnouncements();
        }, interval, interval);
    }

    private void schedulePermissionGroupCheck() {
        if (!configManager.getConfig().getBoolean("permission_group_rewards.enabled", false)) {
            return;
        }

        int interval = configManager.getConfig().getInt("permission_group_rewards.check_interval", 10) * 20;
        SchedulerUtils.runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                permissionGroupRewardListener.checkOnlinePlayerPermissionGroup(player);
            }
        }, interval, interval);
    }

    public void reload() {
        databaseManager.close();
        configManager.loadAll();
        initDatabase();
        initManagers();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public MilestoneManager getMilestoneManager() {
        return milestoneManager;
    }

    public GiftManager getGiftManager() {
        return giftManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public PointsRebateManager getPointsRebateManager() {
        return pointsRebateManager;
    }
}
