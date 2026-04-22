package com.alinvite.listeners;

import com.alinvite.ALInvite;
import com.alinvite.utils.SchedulerUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class InviteListener implements Listener {

    private final ALInvite plugin;

    public InviteListener(ALInvite plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        String veteranPerm = plugin.getConfigManager().getConfig()
            .getString("invite_code.veteran_permission", "alinvite.veteran");

        if (!player.hasPermission(veteranPerm)) {
            return;
        }

        SchedulerUtils.runTaskAsynchronously(plugin, () -> {
            String code = plugin.getDatabaseManager().getInviteCodeByPlayer(player.getUniqueId()).join();
            if (code == null) {
                plugin.getInviteManager().generateInviteCode(player.getUniqueId()).thenAccept(generatedCode -> {
                    // 邀请码生成完成后的处理
                });
            }
            plugin.getMilestoneManager().checkPendingMilestones(player);
        });
    }
}
