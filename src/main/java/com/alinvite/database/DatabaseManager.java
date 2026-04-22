package com.alinvite.database;

import com.alinvite.ALInvite;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final ALInvite plugin;
    private String tablePrefix;
    private HikariDataSource dataSource;

    public DatabaseManager(ALInvite plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String type = plugin.getConfigManager().getConfig().getString("database.type", "sqlite");
        this.tablePrefix = plugin.getConfigManager().getConfig().getString("database.table_prefix", "alinvite_");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("ALInvite-Pool");

        if (type.equalsIgnoreCase("mysql")) {
            String host = plugin.getConfigManager().getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfigManager().getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfigManager().getConfig().getString("database.mysql.database", "minecraft");
            String user = plugin.getConfigManager().getConfig().getString("database.mysql.user", "root");
            String password = plugin.getConfigManager().getConfig().getString("database.mysql.password", "");
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(10);
        } else {
            String dbFile = plugin.getConfigManager().getConfig().getString("database.sqlite_file", "data.db");
            File file = new File(plugin.getDataFolder(), dbFile);
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(1);
        }

        this.dataSource = new HikariDataSource(hikariConfig);

        createTables();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    private void createTables() {
        String type = plugin.getConfigManager().getConfig().getString("database.type", "sqlite");
        boolean isMySQL = type.equalsIgnoreCase("mysql");
        
        String autoIncrementSyntax = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        
        String playersTable = """
            CREATE TABLE IF NOT EXISTS `{prefix}players` (
                `uuid` VARCHAR(36) PRIMARY KEY,
                `invite_code` VARCHAR(16) NOT NULL UNIQUE,
                `total_invites` INT NOT NULL DEFAULT 0,
                `claimed_milestones` TEXT,
                `announced_milestones` TEXT,
                `gift_id` VARCHAR(32),
                `purchased_gifts` TEXT,
                `money` REAL NOT NULL DEFAULT 0,
                `contribution_amount` REAL NOT NULL DEFAULT 0,
                `total_rebate_points` REAL NOT NULL DEFAULT 0,
                `last_invite_time` BIGINT,
                `last_code_change` BIGINT,
                `last_permission_group` VARCHAR(32),
                `created_at` BIGINT NOT NULL
            )
            """;
            
        String recordsTable = """
            CREATE TABLE IF NOT EXISTS `{prefix}records` (
                `id` INTEGER PRIMARY KEY {autoIncrement},
                `inviter_uuid` VARCHAR(36) NOT NULL,
                `invitee_uuid` VARCHAR(36) NOT NULL,
                `invitee_ip` VARCHAR(45) NOT NULL,
                `invitee_name` VARCHAR(16) NOT NULL,
                `claimed_permission_groups` TEXT,
                `invited_at` BIGINT NOT NULL
            )
            """.replace("{autoIncrement}", autoIncrementSyntax);

        String announcementsTable = """
            CREATE TABLE IF NOT EXISTS `{prefix}announcements` (
                `id` INTEGER PRIMARY KEY {autoIncrement},
                `server_id` VARCHAR(64) NOT NULL DEFAULT 'all',
                `message` TEXT NOT NULL,
                `created_at` BIGINT NOT NULL,
                `broadcasted` BOOLEAN DEFAULT FALSE,
                `broadcasted_at` BIGINT DEFAULT NULL
            )
            """.replace("{autoIncrement}", autoIncrementSyntax);

        String pendingMilestonesTable = """
            CREATE TABLE IF NOT EXISTS `{prefix}pending_milestones` (
                `id` INTEGER PRIMARY KEY {autoIncrement},
                `player_uuid` VARCHAR(36) NOT NULL,
                `milestone_key` VARCHAR(16) NOT NULL,
                `created_at` BIGINT NOT NULL,
                UNIQUE(`player_uuid`, `milestone_key`)
            )
            """.replace("{autoIncrement}", autoIncrementSyntax);

        executeUpdate(playersTable.replace("{prefix}", tablePrefix));
        executeUpdate(recordsTable.replace("{prefix}", tablePrefix));
        executeUpdate(announcementsTable.replace("{prefix}", tablePrefix));
        executeUpdate(pendingMilestonesTable.replace("{prefix}", tablePrefix));
        
        // 创建点券返点记录表（用于跨服防重复）
        String pointsRebateTable = """
            CREATE TABLE IF NOT EXISTS `{prefix}points_rebate` (
                `id` INTEGER PRIMARY KEY {autoIncrement},
                `transaction_key` VARCHAR(128) NOT NULL UNIQUE,
                `player_uuid` VARCHAR(36) NOT NULL,
                `amount` DECIMAL(10,2) NOT NULL,
                `inviter_uuid` VARCHAR(36),
                `rebate_amount` DECIMAL(10,2),
                `created_at` BIGINT NOT NULL,
                `processed_at` BIGINT DEFAULT NULL,
                `status` VARCHAR(16) DEFAULT 'PENDING'
            )
            """.replace("{autoIncrement}", autoIncrementSyntax);
        
        executeUpdate(pointsRebateTable.replace("{prefix}", tablePrefix));
        
        // 更新现有表结构（如果缺少字段）
        updateTableStructure();
        
        // 创建必要的索引以提升查询性能
        createIndexes();
    }
    
    /**
     * 创建数据库索引以提升查询性能
     */
    private void createIndexes() {
        plugin.getLogger().info("正在创建数据库索引...");
        
        // 玩家表索引
        String idxPlayersInviteCode = "CREATE INDEX IF NOT EXISTS idx_{prefix}players_invite_code ON {prefix}players(invite_code)";
        String idxPlayersTotalInvites = "CREATE INDEX IF NOT EXISTS idx_{prefix}players_total_invites ON {prefix}players(total_invites)";
        
        // 记录表索引
        String idxRecordsInviterUuid = "CREATE INDEX IF NOT EXISTS idx_{prefix}records_inviter_uuid ON {prefix}records(inviter_uuid)";
        String idxRecordsInviteeUuid = "CREATE INDEX IF NOT EXISTS idx_{prefix}records_invitee_uuid ON {prefix}records(invitee_uuid)";
        String idxRecordsInvitedAt = "CREATE INDEX IF NOT EXISTS idx_{prefix}records_invited_at ON {prefix}records(invited_at)";
        
        // 点券返点表索引
        String idxPointsRebateTransactionKey = "CREATE INDEX IF NOT EXISTS idx_{prefix}points_rebate_transaction_key ON {prefix}points_rebate(transaction_key)";
        String idxPointsRebatePlayerUuid = "CREATE INDEX IF NOT EXISTS idx_{prefix}points_rebate_player_uuid ON {prefix}points_rebate(player_uuid)";
        String idxPointsRebateCreatedAt = "CREATE INDEX IF NOT EXISTS idx_{prefix}points_rebate_created_at ON {prefix}points_rebate(created_at)";
        
        // 公告表索引
        String idxAnnouncementsCreatedAt = "CREATE INDEX IF NOT EXISTS idx_{prefix}announcements_created_at ON {prefix}announcements(created_at)";
        
        // 待处理里程碑表索引
        String idxPendingMilestonesPlayerUuid = "CREATE INDEX IF NOT EXISTS idx_{prefix}pending_milestones_player_uuid ON {prefix}pending_milestones(player_uuid)";
        
        // 执行索引创建
        executeUpdate(idxPlayersInviteCode.replace("{prefix}", tablePrefix));
        executeUpdate(idxPlayersTotalInvites.replace("{prefix}", tablePrefix));
        executeUpdate(idxRecordsInviterUuid.replace("{prefix}", tablePrefix));
        executeUpdate(idxRecordsInviteeUuid.replace("{prefix}", tablePrefix));
        executeUpdate(idxRecordsInvitedAt.replace("{prefix}", tablePrefix));
        executeUpdate(idxPointsRebateTransactionKey.replace("{prefix}", tablePrefix));
        executeUpdate(idxPointsRebatePlayerUuid.replace("{prefix}", tablePrefix));
        executeUpdate(idxPointsRebateCreatedAt.replace("{prefix}", tablePrefix));
        executeUpdate(idxAnnouncementsCreatedAt.replace("{prefix}", tablePrefix));
        executeUpdate(idxPendingMilestonesPlayerUuid.replace("{prefix}", tablePrefix));
        
        plugin.getLogger().info("数据库索引创建完成！");
    }
    
    /**
     * 更新现有表结构（添加缺失的字段）
     */
    private void updateTableStructure() {
        String type = plugin.getConfigManager().getConfig().getString("database.type", "sqlite");
        boolean isMySQL = type.equalsIgnoreCase("mysql");
        
        try {
            // 检查 players 表是否缺少 contribution_amount 字段
            boolean columnExists = checkColumnExists("players", "contribution_amount", isMySQL);
            
            if (!columnExists) {
                // 添加缺失的字段
                String alterTableSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN contribution_amount REAL NOT NULL DEFAULT 0";
                
                // 对于MySQL，需要特殊处理
                if (isMySQL) {
                    alterTableSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN contribution_amount DECIMAL(10,2) NOT NULL DEFAULT 0";
                }
                
                executeUpdate(alterTableSql);
                plugin.getLogger().info("已为数据库表添加 contribution_amount 字段");
            }
            
            // 检查 players 表是否缺少功能权限字段
            boolean milestoneEnabledExists = checkColumnExists("players", "milestone_enabled", isMySQL);
            boolean rebateEnabledExists = checkColumnExists("players", "rebate_enabled", isMySQL);
            boolean giftEnabledExists = checkColumnExists("players", "gift_enabled", isMySQL);
            boolean bindIpExists = checkColumnExists("players", "bind_ip", isMySQL);
            
            if (!milestoneEnabledExists) {
                String alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN milestone_enabled BOOLEAN NOT NULL DEFAULT 1";
                if (isMySQL) alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN milestone_enabled TINYINT(1) NOT NULL DEFAULT 1";
                executeUpdate(alterSql);
                plugin.getLogger().info("已为数据库表添加 milestone_enabled 字段");
            }
            
            if (!rebateEnabledExists) {
                String alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN rebate_enabled BOOLEAN NOT NULL DEFAULT 1";
                if (isMySQL) alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN rebate_enabled TINYINT(1) NOT NULL DEFAULT 1";
                executeUpdate(alterSql);
                plugin.getLogger().info("已为数据库表添加 rebate_enabled 字段");
            }
            
            if (!giftEnabledExists) {
                String alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN gift_enabled BOOLEAN NOT NULL DEFAULT 1";
                if (isMySQL) alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN gift_enabled TINYINT(1) NOT NULL DEFAULT 1";
                executeUpdate(alterSql);
                plugin.getLogger().info("已为数据库表添加 gift_enabled 字段");
            }
            
            if (!bindIpExists) {
                String alterSql = "ALTER TABLE " + tablePrefix + "players ADD COLUMN bind_ip VARCHAR(45)";
                executeUpdate(alterSql);
                plugin.getLogger().info("已为数据库表添加 bind_ip 字段");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("更新数据库表结构失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查表中是否存在指定字段
     */
    private boolean checkColumnExists(String tableName, String columnName, boolean isMySQL) {
        try {
            String checkColumnSql;
            if (isMySQL) {
                checkColumnSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            } else {
                checkColumnSql = "PRAGMA table_info(\"" + tablePrefix + tableName + "\")";
            }
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkColumnSql)) {
                
                if (isMySQL) {
                    stmt.setString(1, tablePrefix + tableName);
                    stmt.setString(2, columnName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                } else {
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String existingColumnName = rs.getString("name");
                        if (columnName.equals(existingColumnName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("检查字段是否存在失败: " + e.getMessage());
        }
        
        return false;
    }

    private int executeUpdate(String sql) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL执行失败: " + e.getMessage());
            plugin.getLogger().severe("SQL语句: " + sql);
            e.printStackTrace();
            return -1;
        }
    }

    public CompletableFuture<PlayerData> getPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    PlayerData data = new PlayerData();
                    data.uuid = uuid;
                    data.inviteCode = rs.getString("invite_code");
                    data.totalInvites = rs.getInt("total_invites");
                    data.claimedMilestones = rs.getString("claimed_milestones");
                    data.announcedMilestones = rs.getString("announced_milestones");
                    data.giftId = rs.getString("gift_id");
                    data.purchasedGifts = rs.getString("purchased_gifts");
                    data.money = rs.getDouble("money");
                    data.lastInviteTime = rs.getLong("last_invite_time");
                    data.lastCodeChange = rs.getLong("last_code_change");
                    data.lastPermissionGroup = rs.getString("last_permission_group");
                    data.createdAt = rs.getLong("created_at");
                    data.milestoneEnabled = rs.getBoolean("milestone_enabled");
                    data.rebateEnabled = rs.getBoolean("rebate_enabled");
                    data.giftEnabled = rs.getBoolean("gift_enabled");
                    data.contributionAmount = rs.getDouble("contribution_amount");
                    data.totalRebatePoints = rs.getDouble("total_rebate_points");
                    return data;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家数据失败: " + e.getMessage());
                plugin.getLogger().severe("SQL语句: " + sql);
                plugin.getLogger().severe("玩家UUID: " + uuid);
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<Void> createPlayerData(UUID uuid, String inviteCode) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "players (uuid, invite_code, total_invites, created_at) VALUES (?, ?, 0, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, inviteCode);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("创建玩家数据失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<String> getInviteCodeByPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT invite_code FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("invite_code");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取邀请码失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<String> getPlayerByInviteCode(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid FROM " + tablePrefix + "players WHERE invite_code = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, code);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("uuid");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("通过邀请码获取玩家失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<UUID> getInviterUUIDByInviteCode(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String uuid = getPlayerByInviteCode(code).join();
            return uuid != null ? UUID.fromString(uuid) : null;
        });
    }

    public CompletableFuture<UUID> getInviterByInvitee(UUID inviteeUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT inviter_uuid FROM " + tablePrefix + "records WHERE invitee_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviteeUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return UUID.fromString(rs.getString("inviter_uuid"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取邀请人失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordInvite(UUID inviterUuid, UUID inviteeUuid, String inviteeIp, String inviteeName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "records (inviter_uuid, invitee_uuid, invitee_ip, invitee_name, invited_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviterUuid.toString());
                stmt.setString(2, inviteeUuid.toString());
                stmt.setString(3, inviteeIp);
                stmt.setString(4, inviteeName);
                stmt.setLong(5, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("记录邀请失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> updateTotalInvites(UUID uuid, int total) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET total_invites = ?, last_invite_time = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, total);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("更新邀请次数失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<String> getClaimedMilestones(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT claimed_milestones FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("claimed_milestones");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取已领取里程碑失败: " + e.getMessage());
            }
            return "[]";
        });
    }

    public CompletableFuture<Void> claimMilestone(UUID uuid, String milestoneId) {
        return CompletableFuture.runAsync(() -> {
            getClaimedMilestones(uuid).thenAccept(claimed -> {
                // 解析现有的已领取里程碑
                Set<String> claimedSet = new HashSet<>();
                if (claimed != null && !claimed.trim().isEmpty() && !claimed.equals("[]")) {
                    try {
                        String[] parts = claimed.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                claimedSet.add(part);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已领取里程碑失败，重置为空列表: " + claimed);
                        claimedSet = new HashSet<>();
                    }
                }
                
                // 添加新的里程碑ID
                claimedSet.add(milestoneId);
                
                // 构建新的JSON数组
                String newClaimed = "[\"" + String.join("\",\"", claimedSet) + "\"]";
                
                String sql = "UPDATE " + tablePrefix + "players SET claimed_milestones = ? WHERE uuid = ?";
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, newClaimed);
                    stmt.setString(2, uuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("领取里程碑失败: " + e.getMessage());
                    plugin.getLogger().severe("SQL语句: " + sql);
                    plugin.getLogger().severe("玩家UUID: " + uuid);
                    plugin.getLogger().severe("里程碑ID: " + milestoneId);
                    e.printStackTrace();
                }
            });
        });
    }

    public CompletableFuture<String> getAnnouncedMilestones(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT announced_milestones FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("announced_milestones");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取已公告里程碑失败: " + e.getMessage());
            }
            return "[]";
        });
    }

    public CompletableFuture<Void> addAnnouncedMilestone(UUID uuid, String milestoneId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET announced_milestones = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                String current = getAnnouncedMilestones(uuid).join();
                
                // 解析现有的已公告里程碑
                Set<String> announcedSet = new HashSet<>();
                if (current != null && !current.trim().isEmpty() && !current.equals("[]")) {
                    try {
                        String[] parts = current.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                announcedSet.add(part);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已公告里程碑失败，重置为空列表: " + current);
                        announcedSet = new HashSet<>();
                    }
                }
                
                // 添加新的里程碑ID
                announcedSet.add(milestoneId);
                
                // 构建新的JSON数组
                String newValue = "[\"" + String.join("\",\"", announcedSet) + "\"]";
                
                stmt.setString(1, newValue);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("添加已公告里程碑失败: " + e.getMessage());
                plugin.getLogger().severe("SQL语句: " + sql);
                plugin.getLogger().severe("玩家UUID: " + uuid);
                plugin.getLogger().severe("里程碑ID: " + milestoneId);
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Void> addPendingMilestone(UUID uuid, String milestoneKey) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR IGNORE INTO " + tablePrefix + "pending_milestones (player_uuid, milestone_key, created_at) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, milestoneKey);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("添加待领取里程碑失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<String>> getPendingMilestones(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> milestones = new ArrayList<>();
            String sql = "SELECT milestone_key FROM " + tablePrefix + "pending_milestones WHERE player_uuid = ? ORDER BY created_at ASC";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    milestones.add(rs.getString("milestone_key"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取待领取里程碑失败: " + e.getMessage());
            }
            return milestones;
        });
    }

    public CompletableFuture<Void> removePendingMilestone(UUID uuid, String milestoneKey) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "pending_milestones WHERE player_uuid = ? AND milestone_key = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, milestoneKey);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("删除待领取里程碑失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<String> getGiftId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT gift_id FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("gift_id");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取礼包ID失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> setGiftId(UUID uuid, String giftId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET gift_id = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, giftId);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("设置礼包ID失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> updateInviteCode(UUID uuid, String newCode) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET invite_code = ?, last_code_change = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newCode);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("更新邀请码失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> clearInviteCode(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET invite_code = '', last_code_change = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("清除邀请码失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> addPurchasedGift(UUID uuid, String giftId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "SELECT purchased_gifts FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                String current = null;
                if (rs.next()) {
                    current = rs.getString("purchased_gifts");
                }
                
                // 处理 null 或空值的情况
                if (current == null || current.trim().isEmpty()) {
                    current = "[]";
                }
                
                // 解析现有的礼包列表
                Set<String> purchasedGifts = new HashSet<>();
                if (!current.equals("[]")) {
                    try {
                        String[] gifts = current.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String gift : gifts) {
                            if (!gift.trim().isEmpty()) {
                                purchasedGifts.add(gift.trim());
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已购买礼包失败，重置为空列表: " + current);
                        purchasedGifts = new HashSet<>();
                    }
                }
                
                // 添加新的礼包ID
                purchasedGifts.add(giftId);
                
                // 构建新的JSON数组
                String newValue = "[\"" + String.join("\",\"", purchasedGifts) + "\"]";

                String updateSql = "UPDATE " + tablePrefix + "players SET purchased_gifts = ? WHERE uuid = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, newValue);
                    updateStmt.setString(2, uuid.toString());
                    updateStmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("添加已购买礼包失败: " + e.getMessage());
                plugin.getLogger().severe("SQL语句: " + sql);
                plugin.getLogger().severe("玩家UUID: " + uuid);
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Set<String>> getClaimedPermissionGroups(UUID inviterUuid, UUID inviteeUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> groups = new HashSet<>();
            String sql = "SELECT claimed_permission_groups FROM " + tablePrefix + "records WHERE inviter_uuid = ? AND invitee_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviterUuid.toString());
                stmt.setString(2, inviteeUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String data = rs.getString("claimed_permission_groups");
                    if (data != null && !data.isEmpty()) {
                        String[] parts = data.substring(1, data.length() - 1).split(",");
                        for (String part : parts) {
                            part = part.trim().replace("\"", "");
                            if (!part.isEmpty()) {
                                groups.add(part);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取已领取权限组失败: " + e.getMessage());
            }
            return groups;
        });
    }

    public CompletableFuture<Void> claimPermissionGroup(UUID inviterUuid, UUID inviteeUuid, String group) {
        return CompletableFuture.runAsync(() -> {
            String sql = "SELECT claimed_permission_groups FROM " + tablePrefix + "records WHERE inviter_uuid = ? AND invitee_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviterUuid.toString());
                stmt.setString(2, inviteeUuid.toString());
                ResultSet rs = stmt.executeQuery();
                String current = rs.next() ? rs.getString("claimed_permission_groups") : "[]";
                
                // 解析现有的已领取权限组
                Set<String> claimedGroups = new HashSet<>();
                if (current != null && !current.trim().isEmpty() && !current.equals("[]")) {
                    try {
                        String[] parts = current.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                claimedGroups.add(part);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已领取权限组失败，重置为空列表: " + current);
                        claimedGroups = new HashSet<>();
                    }
                }
                
                // 添加新的权限组
                claimedGroups.add(group);
                
                // 构建新的JSON数组
                String newValue = "[\"" + String.join("\",\"", claimedGroups) + "\"]";

                String updateSql = "UPDATE " + tablePrefix + "records SET claimed_permission_groups = ? WHERE inviter_uuid = ? AND invitee_uuid = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, newValue);
                    updateStmt.setString(2, inviterUuid.toString());
                    updateStmt.setString(3, inviteeUuid.toString());
                    updateStmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("领取权限组失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> addAnnouncement(String serverId, String message) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "announcements (server_id, message, created_at) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, serverId);
                stmt.setString(2, message);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("添加公告失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<Announcement>> getPendingAnnouncements(String serverId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Announcement> announcements = new ArrayList<>();
            String sql = "SELECT * FROM " + tablePrefix + "announcements WHERE broadcasted = FALSE AND (server_id = 'all' OR server_id != ?) LIMIT 10";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, serverId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Announcement ann = new Announcement();
                    ann.id = rs.getInt("id");
                    ann.serverId = rs.getString("server_id");
                    ann.message = rs.getString("message");
                    ann.createdAt = rs.getLong("created_at");
                    announcements.add(ann);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取待发送公告失败: " + e.getMessage());
            }
            return announcements;
        });
    }

    public CompletableFuture<Void> markAnnouncementSent(int id) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "announcements SET broadcasted = TRUE, broadcasted_at = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setInt(2, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("标记公告已发送失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> updatePermissionGroup(UUID uuid, String group) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET last_permission_group = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, group);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("更新权限组失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<String> getLastPermissionGroup(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_permission_group FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("last_permission_group");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取最后权限组失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Set<String>> getPurchasedGifts(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> gifts = new HashSet<>();
            String sql = "SELECT purchased_gifts FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String data = rs.getString("purchased_gifts");
                    
                    // 处理 null 或空值的情况
                    if (data == null || data.trim().isEmpty() || data.equals("[]")) {
                        return gifts;
                    }
                    
                    // 解析 JSON 数组
                    try {
                        String[] parts = data.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String part : parts) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                gifts.add(part);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("解析已购买礼包数据失败: " + data);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取已购买礼包失败: " + e.getMessage());
                plugin.getLogger().severe("SQL语句: " + sql);
                plugin.getLogger().severe("玩家UUID: " + uuid);
                e.printStackTrace();
            }
            return gifts;
        });
    }

    public CompletableFuture<Integer> getInviteCount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT total_invites FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("total_invites");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取邀请数量失败: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Void> resetPlayerData(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql1 = "DELETE FROM " + tablePrefix + "records WHERE inviter_uuid = ? OR invitee_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("重置玩家数据(记录)失败: " + e.getMessage());
            }

            String sql2 = "DELETE FROM " + tablePrefix + "pending_milestones WHERE player_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql2)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("重置玩家数据(待领取)失败: " + e.getMessage());
            }

            // 重置玩家数据但保留邀请码和贡献点
            String sql3 = "UPDATE " + tablePrefix + "players SET total_invites = 0, claimed_milestones = '[]', announced_milestones = '[]', gift_id = NULL, purchased_gifts = '[]', money = 0, last_permission_group = NULL WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql3)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("重置玩家数据(玩家表)失败: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<UUID> getInviter(UUID inviteeUuid) {
        return getInviterByInvitee(inviteeUuid);
    }

    public CompletableFuture<Void> updateInviteCount(UUID uuid, int count) {
        return updateTotalInvites(uuid, count);
    }

    public CompletableFuture<Void> updateGiftId(UUID uuid, String giftId) {
        return setGiftId(uuid, giftId);
    }

    public CompletableFuture<Void> insertAnnouncement(String serverId, String message) {
        return addAnnouncement(serverId, message);
    }

    public CompletableFuture<Boolean> isInviteCodeExists(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tablePrefix + "players WHERE invite_code = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, code);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("检查邀请码是否存在失败: " + e.getMessage());
            }
            return false;
        });
    }

    public CompletableFuture<Integer> getIpInviteCount(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tablePrefix + "records WHERE invitee_ip = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ip);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取IP邀请次数失败: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Boolean> hasUsedInviteCode(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + tablePrefix + "records WHERE invitee_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("检查是否使用过邀请码失败: " + e.getMessage());
            }
            return false;
        });
    }

    /**
     * 更新玩家功能权限
     */
    public CompletableFuture<Void> updateFunctionPermissions(UUID playerUuid, String bindIp, boolean milestoneEnabled, boolean rebateEnabled, boolean giftEnabled) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET bind_ip = ?, milestone_enabled = ?, rebate_enabled = ?, gift_enabled = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, bindIp);
                stmt.setBoolean(2, milestoneEnabled);
                stmt.setBoolean(3, rebateEnabled);
                stmt.setBoolean(4, giftEnabled);
                stmt.setString(5, playerUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("更新功能权限失败: " + e.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> addInviteRecord(UUID inviterUuid, UUID inviteeUuid, String inviteeIp, String inviteeName) {
        return recordInvite(inviterUuid, inviteeUuid, inviteeIp, inviteeName);
    }

    public CompletableFuture<Void> addClaimedPermissionGroup(UUID inviterUuid, UUID inviteeUuid, String group) {
        return claimPermissionGroup(inviterUuid, inviteeUuid, group);
    }

    /**
     * 检查跨服重复交易
     * 使用全局交易标识防止同一笔充值在不同服务器上重复发放返点
     * 
     * @param transactionKey 全局交易标识（格式：rebate_<UUID>_<金额>）
     * @return CompletableFuture<Boolean> true表示存在重复交易
     */
    public CompletableFuture<Boolean> checkCrossServerDuplicate(String transactionKey) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM " + tablePrefix + "points_rebate WHERE transaction_key = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, transactionKey);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("检查跨服重复交易失败: " + e.getMessage());
            }
            return false;
        });
    }

    /**
     * 获取玩家累计返点总额
     * 
     * @param inviterUuid 邀请人UUID
     * @return CompletableFuture<Double> 累计返点总额
     */
    public CompletableFuture<Double> getTotalRebateAmount(UUID inviterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT SUM(rebate_amount) as total FROM " + tablePrefix + "points_rebate " +
                        "WHERE inviter_uuid = ? AND status = 'COMPLETED'";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviterUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double total = rs.getDouble("total");
                    // 如果返回值为0或NULL，确保返回0.0而不是null
                    return rs.wasNull() ? 0.0 : total;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取累计返点总额失败: " + e.getMessage());
            }
            return 0.0;
        });
    }

    /**
     * 获取玩家的贡献返点金额
     */
    public CompletableFuture<Double> getContributionAmount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT contribution_amount FROM " + tablePrefix + "players WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getDouble("contribution_amount");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取贡献返点金额失败: " + e.getMessage());
            }
            return 0.0;
        });
    }
    
    /**
     * 增加贡献返点金额
     */
    public CompletableFuture<Boolean> addContributionAmount(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET contribution_amount = contribution_amount + ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, amount);
                stmt.setString(2, uuid.toString());
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("增加贡献返点金额失败: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 扣除贡献返点金额
     */
    public CompletableFuture<Boolean> deductContributionAmount(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET contribution_amount = contribution_amount - ? WHERE uuid = ? AND contribution_amount >= ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, amount);
                stmt.setString(2, uuid.toString());
                stmt.setDouble(3, amount);
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("扣除贡献返点金额失败: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> setContributionAmount(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET contribution_amount = ? WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, amount);
                stmt.setString(2, uuid.toString());
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("设置贡献返点金额失败: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> clearContributionAmount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET contribution_amount = 0 WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("清空贡献返点金额失败: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deductPoints(UUID uuid, int points) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "players SET total_rebate_points = total_rebate_points - ? WHERE uuid = ? AND total_rebate_points >= ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setDouble(1, points);
                stmt.setString(2, uuid.toString());
                stmt.setDouble(3, points);
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                plugin.getLogger().severe("扣除点券失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 检查跨服重复交易
     * 使用玩家UUID和金额生成唯一标识检查重复
     * 
     * @param playerUuid 玩家UUID
     * @param amount 充值金额
     * @return CompletableFuture<Boolean> true表示存在重复交易
     */
    public CompletableFuture<Boolean> checkRebateDuplicate(UUID playerUuid, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String transactionKey = "rebate_" + playerUuid.toString() + "_" + amount;
            String sql = "SELECT COUNT(*) as count FROM " + tablePrefix + "points_rebate WHERE transaction_key = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, transactionKey);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("检查返点重复交易失败: " + e.getMessage());
            }
            return false;
        });
    }

    /**
     * 获取今日返点总额
     * 用于检查每日返点上限
     * 
     * @param inviterUuid 邀请人UUID
     * @return CompletableFuture<Double> 今日返点总额
     */
    public CompletableFuture<Double> getTodayRebateTotal(UUID inviterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            long todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000));
            
            String sql = "SELECT SUM(rebate_amount) as total FROM " + tablePrefix + "points_rebate " +
                        "WHERE inviter_uuid = ? AND created_at >= ? AND status = 'COMPLETED'";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, inviterUuid.toString());
                stmt.setLong(2, todayStart);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double total = rs.getDouble("total");
                    // 如果返回值为0或NULL，确保返回0.0而不是null
                    return rs.wasNull() ? 0.0 : total;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取今日返点总额失败: " + e.getMessage());
            }
            return 0.0;
        });
    }

    /**
     * 记录点券返点交易
     * 用于跨服防重复和统计
     * 
     * @param transactionKey 全局交易标识
     * @param playerUuid 玩家UUID
     * @param amount 充值金额
     * @param inviterUuid 邀请人UUID（可为null）
     * @param rebateAmount 返点金额（可为null）
     * @return CompletableFuture<Boolean> 记录是否成功
     */
    public CompletableFuture<Boolean> recordRebateTransaction(String transactionKey, UUID playerUuid, double amount, 
                                                             UUID inviterUuid, Double rebateAmount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "points_rebate " +
                        "(transaction_key, player_uuid, amount, inviter_uuid, rebate_amount, created_at, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, transactionKey);
                stmt.setString(2, playerUuid.toString());
                stmt.setDouble(3, amount);
                
                if (inviterUuid != null) {
                    stmt.setString(4, inviterUuid.toString());
                } else {
                    stmt.setNull(4, java.sql.Types.VARCHAR);
                }
                
                if (rebateAmount != null) {
                    stmt.setDouble(5, rebateAmount);
                } else {
                    stmt.setNull(5, java.sql.Types.DECIMAL);
                }
                
                stmt.setLong(6, System.currentTimeMillis());
                stmt.setString(7, rebateAmount != null ? "COMPLETED" : "PENDING");
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("记录返点交易失败: " + e.getMessage());
                return false;
            }
        });
    }

    public void syncAnnouncements() {
        String serverId = plugin.getConfigManager().getConfig().getString("database.server_id", "default");
        
        // 1. 获取其他服务器的未广播公告
        getPendingAnnouncements(serverId).thenAccept(announcements -> {
            for (Announcement announcement : announcements) {
                // 2. 在当前服务器上播放公告
                String mode = plugin.getConfigManager().getConfig()
                    .getString("announcements.mode", "BROADCAST");
                
                switch (mode) {
                    case "BROADCAST" -> plugin.getServer().broadcastMessage(announcement.message);
                    case "CONSOLE" -> plugin.getLogger().info(announcement.message);
                    // WORLD模式无法处理跨服公告，因为玩家不在当前服务器
                }
                
                // 3. 标记公告为已广播
                markAnnouncementSent(announcement.id).join();
            }
        });
        
        // 4. 清理旧公告
        String sql = "DELETE FROM " + tablePrefix + "announcements WHERE broadcasted = TRUE AND broadcasted_at < ?";
        long cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, cutoff);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("同步公告失败: " + e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static class PlayerData {
        public UUID uuid;
        public String inviteCode;
        public int totalInvites;
        public String claimedMilestones;
        public String announcedMilestones;
        public String giftId;
        public String purchasedGifts;
        public double money;
        public long lastInviteTime;
        public long lastCodeChange;
        public String lastPermissionGroup;
        public long createdAt;
        public boolean milestoneEnabled;
        public boolean rebateEnabled;
        public boolean giftEnabled;
        public double contributionAmount;
        public double totalRebatePoints;
    }

    public static class Announcement {
        public int id;
        public String serverId;
        public String message;
        public long createdAt;
    }
}