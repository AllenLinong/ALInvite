package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.utils.SchedulerUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.*;

public class MilestoneManager {

    private final ALInvite plugin;
    private final Map<Integer, Milestone> milestones;

    public MilestoneManager(ALInvite plugin) {
        this.plugin = plugin;
        this.milestones = new HashMap<>();
        loadMilestones();
    }

    private void loadMilestones() {
        Objects.requireNonNull(plugin.getConfigManager().getConfig().getConfigurationSection("milestones"))
            .getKeys(false)
            .forEach(key -> {
                if (key.equals("auto_claim")) return;
                int required = Integer.parseInt(key);
                String name = plugin.getConfigManager().getConfig().getString("milestones." + key + ".name", "里程碑");
                List<Reward> rewards = loadRewards(key);
                List<String> lore = plugin.getConfigManager().getConfig().getStringList("milestones." + key + ".lore");
                milestones.put(required, new Milestone(required, name, rewards, lore));
            });
    }

    private List<Reward> loadRewards(String milestoneKey) {
        List<Reward> rewards = new ArrayList<>();
        List<Map<?, ?>> rewardList = plugin.getConfigManager().getConfig()
            .getMapList("milestones." + milestoneKey + ".rewards");

        for (Map<?, ?> rewardMap : rewardList) {
            String type = (String) rewardMap.get("type");
            Object value = rewardMap.get("value");
            rewards.add(new Reward(type, value));
        }
        return rewards;
    }

    public void checkMilestones(UUID playerUuid, int totalInvites) {
        Player player = Bukkit.getPlayer(playerUuid);
        boolean autoClaim = plugin.getConfigManager().getConfig().getBoolean("milestones.auto_claim", true);
        boolean sendOnlyOnce = plugin.getConfigManager().getConfig().getBoolean("announcements.send_only_once", true);

        plugin.getDatabaseManager().getClaimedMilestones(playerUuid).thenAccept(claimedJson -> {
            Set<String> claimed = parseJsonArray(claimedJson);

            for (Map.Entry<Integer, Milestone> entry : milestones.entrySet()) {
                int required = entry.getKey();
                Milestone milestone = entry.getValue();

                if (totalInvites >= required && !claimed.contains(String.valueOf(required))) {
                    if (autoClaim) {
                        if (player != null) {
                            giveRewards(player, milestone);
                            plugin.getDatabaseManager().claimMilestone(playerUuid, String.valueOf(required));
                        } else {
                            plugin.getDatabaseManager().addPendingMilestone(playerUuid, String.valueOf(required));
                        }
                    } else {
                        if (player != null) {
                            String msg = plugin.getConfigManager().getMessage("milestone.unlocked")
                                .replace("{name}", milestone.name);
                            player.sendMessage(msg);
                        }
                    }

                    if (plugin.getConfigManager().getConfig().getBoolean("announcements.enabled", true)) {
                        if (player != null) {
                            if (sendOnlyOnce) {
                                plugin.getDatabaseManager().getAnnouncedMilestones(playerUuid).thenAccept(announced -> {
                                    if (!announced.contains(String.valueOf(required))) {
                                        sendAnnouncement(player, milestone, totalInvites);
                                        plugin.getDatabaseManager().addAnnouncedMilestone(playerUuid, String.valueOf(required));
                                    }
                                });
                            } else {
                                sendAnnouncement(player, milestone, totalInvites);
                            }
                        }
                    }
                }
            }
        });
    }

    public void checkPendingMilestones(Player player) {
        plugin.getDatabaseManager().getPendingMilestones(player.getUniqueId()).thenCompose(pending -> {
            return plugin.getDatabaseManager().getClaimedMilestones(player.getUniqueId()).thenAccept(claimedJson -> {
                Set<String> claimed = parseJsonArray(claimedJson);
                
                for (String milestoneKey : pending) {
                    // 检查里程碑是否已经领取过
                    if (claimed.contains(milestoneKey)) {
                        plugin.getDatabaseManager().removePendingMilestone(player.getUniqueId(), milestoneKey);
                        continue;
                    }
                    
                    Milestone milestone = milestones.get(Integer.parseInt(milestoneKey));
                    if (milestone != null) {
                        giveRewards(player, milestone);
                        plugin.getDatabaseManager().claimMilestone(player.getUniqueId(), milestoneKey);
                        plugin.getDatabaseManager().removePendingMilestone(player.getUniqueId(), milestoneKey);
                    }
                }
            });
        });
    }

    private Set<String> parseJsonArray(String json) {
        if (json == null || json.equals("[]")) {
            return new HashSet<>();
        }
        String trimmed = json.substring(1, json.length() - 1);
        if (trimmed.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        for (String s : trimmed.split(",")) {
            result.add(s.replace("\"", "").trim());
        }
        return result;
    }

    public void giveRewards(Player player, Milestone milestone) {
        for (Reward reward : milestone.rewards) {
            switch (reward.type) {
                case "command" -> {
                    String command = reward.value.toString()
                        .replace("%player%", player.getName());
                    SchedulerUtils.runTask(plugin, () ->
                        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command));
                }
                case "money" -> {
                    var registration = plugin.getServer().getServicesManager()
                        .getRegistration(Economy.class);
                    if (registration != null) {
                        Economy economy = registration.getProvider();
                        if (economy != null) {
                            economy.depositPlayer(player, Double.parseDouble(reward.value.toString()));
                        } else {
                            plugin.getLogger().warning("经济服务提供者为null，无法发放金币奖励");
                        }
                    } else {
                        plugin.getLogger().warning("未找到经济服务注册，请安装Vault和经济插件");
                    }
                }
                case "points" -> {
                    givePoints(player, reward.value);
                }
                case "item" -> {
                    giveItem(player, reward.value);
                }
            }
        }

        String message = plugin.getConfigManager().getMessage("milestone.reached")
            .replace("{name}", milestone.name);
        player.sendMessage(message);
    }

    private void giveItem(Player player, Object value) {
        String[] parts = value.toString().split(" ");
        if (parts.length >= 2) {
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                ItemStack item = new ItemStack(mat, amount);
                player.getInventory().addItem(item);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("礼包物品创建失败: " + value + " - " + e.getMessage());
            }
        }
    }

    private void givePoints(Player player, Object value) {
        String pointsType = plugin.getConfigManager().getConfig()
            .getString("economy.points_type", "NONE");
        if ("NONE".equals(pointsType)) return;

        int amount = Integer.parseInt(value.toString());

        switch (pointsType) {
            case "PLAYERPOINTS" -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        Class<?> ppClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
                        Object ppInstance = ppClass.getMethod("getInstance").invoke(null);
                        if (ppInstance == null) return;
                        Object api = ppClass.getMethod("getAPI").invoke(ppInstance);
                        if (api == null) return;
                        Method giveMethod = api.getClass().getMethod("give", UUID.class, int.class);
                        giveMethod.invoke(api, player.getUniqueId(), amount);
                    } catch (Exception e) {
                        plugin.getLogger().warning("PlayerPoints 发放失败: " + e.getMessage());
                    }
                });
            }
            case "CUSTOM" -> {
                String giveCmd = plugin.getConfigManager().getConfig()
                    .getString("economy.points_command.give", "")
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));
                SchedulerUtils.runTask(plugin, () ->
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), giveCmd));
            }
        }
    }

    public void sendAnnouncement(Player player, Milestone milestone, int totalInvites) {
        String messageTemplate = plugin.getConfigManager().getConfig()
            .getString("announcements.messages." + milestone.required,
                plugin.getConfigManager().getConfig()
                    .getString("announcements.messages.default",
                        "&6[邀请系统] &e{player} &a累计邀请人数达到 &6{total} &a人，获得里程碑 &6{milestone_name}&a！"));

        String message = messageTemplate
            .replace("{player}", player.getName())
            .replace("{total}", String.valueOf(totalInvites))
            .replace("{milestone_name}", milestone.name);

        message = ConfigManager.colorize(message);

        String serverId = plugin.getConfigManager().getConfig()
            .getString("database.server_id", "default");
        boolean crossServerSync = plugin.getConfigManager().getConfig()
            .getBoolean("announcements.cross_server_sync", true);

        if (crossServerSync) {
            plugin.getDatabaseManager().insertAnnouncement("all", message).join();
        }

        String finalMessage = message;

        String mode = plugin.getConfigManager().getConfig()
            .getString("announcements.mode", "BROADCAST");

        switch (mode) {
            case "BROADCAST" -> plugin.getServer().broadcastMessage(finalMessage);
            case "WORLD" -> {
                if (player.getWorld() != null) {
                    player.getWorld().getPlayers().forEach(p -> p.sendMessage(finalMessage));
                }
            }
            case "CONSOLE" -> plugin.getLogger().info(finalMessage);
        }
    }

    public Map<Integer, Milestone> getMilestones() {
        return milestones;
    }

    public Milestone getMilestone(int required) {
        return milestones.get(required);
    }

    public static class Milestone {
        public final int required;
        public final String name;
        public final List<Reward> rewards;
        public final List<String> lore;

        public Milestone(int required, String name, List<Reward> rewards, List<String> lore) {
            this.required = required;
            this.name = name;
            this.rewards = rewards;
            this.lore = lore;
        }
    }

    public static class Reward {
        public final String type;
        public final Object value;

        public Reward(String type, Object value) {
            this.type = type;
            this.value = value;
        }
    }
}
