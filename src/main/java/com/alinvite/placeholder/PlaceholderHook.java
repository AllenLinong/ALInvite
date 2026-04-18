package com.alinvite.placeholder;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.manager.GiftManager;
import com.alinvite.manager.MilestoneManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class PlaceholderHook extends PlaceholderExpansion {

    private final ALInvite plugin;

    public PlaceholderHook(ALInvite plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "alinvite";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Allen_Linong";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        UUID uuid = player.getUniqueId();

        switch (params.toLowerCase()) {
            case "code" -> {
                String code = plugin.getCacheManager().getInviteCode(uuid);
                if (code == null) {
                    code = plugin.getDatabaseManager().getInviteCodeByPlayer(uuid).join();
                    if (code != null) {
                        plugin.getCacheManager().setInviteCode(uuid, code);
                    }
                }
                return code != null ? code : "N/A";
            }

            case "total" -> {
                Integer total = plugin.getCacheManager().getStats(uuid);
                if (total == null) {
                    var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                    total = data != null ? data.totalInvites : 0;
                    plugin.getCacheManager().setStats(uuid, total);
                }
                return String.valueOf(total);
            }

            case "next_milestone" -> {
                Map<Integer, MilestoneManager.Milestone> milestones = plugin.getMilestoneManager().getMilestones();
                Integer currentTotal = plugin.getCacheManager().getStats(uuid);
                if (currentTotal == null) {
                    var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                    currentTotal = data != null ? data.totalInvites : 0;
                }

                for (Map.Entry<Integer, MilestoneManager.Milestone> entry : milestones.entrySet()) {
                    if (entry.getKey() > currentTotal) {
                        return String.valueOf(entry.getKey());
                    }
                }
                return "MAX";
            }

            case "next_milestone_name" -> {
                Map<Integer, MilestoneManager.Milestone> milestones = plugin.getMilestoneManager().getMilestones();
                Integer currentTotal = plugin.getCacheManager().getStats(uuid);
                if (currentTotal == null) {
                    var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                    currentTotal = data != null ? data.totalInvites : 0;
                }

                for (Map.Entry<Integer, MilestoneManager.Milestone> entry : milestones.entrySet()) {
                    if (entry.getKey() > currentTotal) {
                        return entry.getValue().name;
                    }
                }
                return "MAX";
            }

            case "gift_name" -> {
                String giftId = plugin.getCacheManager().getGiftId(uuid);
                if (giftId == null) {
                    giftId = plugin.getDatabaseManager().getGiftId(uuid).join();
                    if (giftId != null) {
                        plugin.getCacheManager().setGiftId(uuid, giftId);
                    }
                }

                if (giftId == null) {
                    return "无";
                }

                GiftManager.GiftConfig gift = plugin.getGiftManager().getGift(giftId);
                return gift != null ? ConfigManager.colorize(gift.name) : "无";
            }

            case "has_gift" -> {
                String giftId = plugin.getCacheManager().getGiftId(uuid);
                if (giftId == null) {
                    giftId = plugin.getDatabaseManager().getGiftId(uuid).join();
                }
                return giftId != null ? "true" : "false";
            }

            case "bind_status" -> {
                UUID inviterUuid = plugin.getDatabaseManager().getInviter(uuid).join();
                return inviterUuid != null ? "已绑定" : "未绑定";
            }

            case "inviter_name" -> {
                UUID inviterUuid = plugin.getDatabaseManager().getInviter(uuid).join();
                if (inviterUuid == null) {
                    return "无";
                }
                var data = plugin.getDatabaseManager().getPlayerData(inviterUuid).join();
                if (data == null) {
                    return "无";
                }
                Player onlineInviter = plugin.getServer().getPlayer(inviterUuid);
                if (onlineInviter != null && onlineInviter.isOnline()) {
                    return onlineInviter.getName();
                }
                return data.inviteCode != null ? data.inviteCode : "未知";
            }

            case "total_invites" -> {
                Integer total = plugin.getCacheManager().getStats(uuid);
                if (total == null) {
                    var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                    total = data != null ? data.totalInvites : 0;
                    plugin.getCacheManager().setStats(uuid, total);
                }
                return String.valueOf(total);
            }

            case "remaining_for_next_milestone" -> {
                Map<Integer, MilestoneManager.Milestone> milestones = plugin.getMilestoneManager().getMilestones();
                Integer currentTotal = plugin.getCacheManager().getStats(uuid);
                if (currentTotal == null) {
                    var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                    currentTotal = data != null ? data.totalInvites : 0;
                }

                for (Map.Entry<Integer, MilestoneManager.Milestone> entry : milestones.entrySet()) {
                    if (entry.getKey() > currentTotal) {
                        return String.valueOf(entry.getKey() - currentTotal);
                    }
                }
                return "0";
            }

            default -> {
                if (params.startsWith("milestone_")) {
                    String milestoneKey = params.substring("milestone_".length());
                    try {
                        int milestoneNum = Integer.parseInt(milestoneKey);
                        MilestoneManager.Milestone milestone = plugin.getMilestoneManager().getMilestone(milestoneNum);
                        if (milestone != null) {
                            Integer currentTotal = plugin.getCacheManager().getStats(uuid);
                            if (currentTotal == null) {
                                var data = plugin.getDatabaseManager().getPlayerData(uuid).join();
                                currentTotal = data != null ? data.totalInvites : 0;
                            }
                            return currentTotal >= milestoneNum ? "true" : "false";
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return null;
    }
}
