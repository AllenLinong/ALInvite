package com.alinvite.manager;

import com.alinvite.ALInvite;
import com.alinvite.database.DatabaseManager;
import com.alinvite.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MilestoneManager {
    private final ALInvite plugin;
    private final Map<Integer, Milestone> milestones;

    public MilestoneManager(ALInvite plugin) {
        this.plugin = plugin;
        this.milestones = new LinkedHashMap<>();
        loadMilestones();
    }

    public static class Milestone {
        public final String name;
        public final List<Reward> rewards;
        public final List<String> lore;

        public Milestone(String name, List<Reward> rewards, List<String> lore) {
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

    private void loadMilestones() {
        ConfigurationSection milestonesConfig = plugin.getConfigManager().getConfig().getConfigurationSection("milestones");
        if (milestonesConfig == null) return;

        for (String key : milestonesConfig.getKeys(false)) {
            // 跳过非数字键（如 auto_claim）
            if (!key.matches("\\d+")) {
                continue;
            }
            
            try {
                int required = Integer.parseInt(key);
                ConfigurationSection milestoneConfig = milestonesConfig.getConfigurationSection(key);
                if (milestoneConfig != null) {
                    String name = milestoneConfig.getString("name", "里程碑 " + required);
                    List<Reward> rewards = parseRewards(milestoneConfig.getStringList("rewards"));
                    List<String> lore = milestoneConfig.getStringList("lore");
                    milestones.put(required, new Milestone(name, rewards, lore));
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("无效的里程碑配置: " + key);
            }
        }
    }

    private List<Reward> parseRewards(List<String> rewardStrings) {
        List<Reward> rewards = new ArrayList<>();
        for (String rewardStr : rewardStrings) {
            String[] parts = rewardStr.split(" ", 2);
            if (parts.length >= 2) {
                String type = parts[0];
                String value = parts[1];
                rewards.add(new Reward(type, value));
            }
        }
        return rewards;
    }

    public void checkMilestones(UUID playerUuid, int totalInvites) {
        Player player = Bukkit.getPlayer(playerUuid);
        boolean autoClaim = plugin.getConfigManager().getConfig().getBoolean("milestones.auto_claim", true);
        boolean sendOnlyOnce = plugin.getConfigManager().getConfig().getBoolean("announcements.send_only_once", true);

        // 检查玩家是否启用了里程碑功能
        plugin.getDatabaseManager().getPlayerData(playerUuid).thenAccept(playerData -> {
            if (playerData != null && !playerData.milestoneEnabled) {
                // 玩家里程碑功能被禁用，不处理里程碑
                return;
            }

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
        });
    }

    public void checkPendingMilestones(Player player) {
        plugin.getDatabaseManager().getPendingMilestones(player.getUniqueId()).thenAccept(pending -> {
            plugin.getDatabaseManager().getClaimedMilestones(player.getUniqueId()).thenAccept(claimedJson -> {
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
        String[] parts = json.replace("[", "").replace("]", "").replace("\"", "").split(",");
        Set<String> result = new HashSet<>();
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
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
                    net.milkbowl.vault.economy.Economy economy = plugin.getServer().getServicesManager()
                        .getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
                    if (economy != null) {
                        economy.depositPlayer(player, Double.parseDouble(reward.value.toString()));
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
    }

    private void givePoints(Player player, Object value) {
        // 实现点数发放逻辑
        String pointsStr = value.toString();
        try {
            int points = Integer.parseInt(pointsStr);
            // 这里需要根据你的点数系统实现具体逻辑
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("无效的点数格式: " + pointsStr);
        }
    }

    private void giveItem(Player player, Object value) {
        String[] parts = value.toString().split(" ");
        if (parts.length >= 2) {
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                ItemStack item = new ItemStack(mat, amount);
                
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack leftItem : leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftItem);
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的物品格式: " + value);
            }
        }
    }

    public void sendAnnouncement(Player player, Milestone milestone, int totalInvites) {
        String announcement = plugin.getConfigManager().getMessage("announcements.milestone")
            .replace("{player}", player.getName())
            .replace("{name}", milestone.name)
            .replace("{total}", String.valueOf(totalInvites));
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(announcement);
        }
    }

    public Map<Integer, Milestone> getMilestones() {
        return milestones;
    }

    public Milestone getMilestone(int milestone) {
        return milestones.get(milestone);
    }
}