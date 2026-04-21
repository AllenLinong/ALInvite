package com.alinvite.commands;

import com.alinvite.ALInvite;
import com.alinvite.config.ConfigManager;
import com.alinvite.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final ALInvite plugin;

    public CommandHandler(ALInvite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("errors.player_only"));
                return true;
            }
            plugin.getMenuManager().openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "code" -> handleCode(sender);
            case "stats" -> handleStats(sender);
            case "cashrebate" -> handleCashRebate(sender);
            case "buygift" -> handleBuyGift(sender);
            case "help" -> handleHelp(sender);
            case "admin" -> handleAdmin(sender, args);
            case "givedj" -> handleGiveDj(sender, args);
            default -> sender.sendMessage(plugin.getConfigManager().getMessage("errors.unknown"));
        }

        return true;
    }

    private void handleCode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.player_only"));
            return;
        }

        if (!player.hasPermission("alinvite.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        // 检查玩家是否有老玩家权限
        String veteranPermission = plugin.getConfigManager().getConfig()
            .getString("invite_code.veteran_permission", "alinvite.veteran");
        
        if (!player.hasPermission(veteranPermission)) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_invite_code"));
            return;
        }

        plugin.getInviteManager().getInviteCode(player.getUniqueId()).thenCompose(code -> {
            if (code == null) {
                return plugin.getInviteManager().generateInviteCode(player.getUniqueId());
            }
            return CompletableFuture.completedFuture(code);
        }).thenAccept(code -> {
            String message = plugin.getConfigManager().getMessage("commands.code").replace("{invite_code}", code);
            player.sendMessage(message);
        });
    }

    private void handleHelp(CommandSender sender) {
        String message = plugin.getConfigManager().getMessage("commands.help");
        sender.sendMessage(message);
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.player_only"));
            return;
        }

        if (!player.hasPermission("alinvite.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        plugin.getInviteManager().getTotalInvites(player.getUniqueId()).thenAccept(total -> {
            String claimed = plugin.getDatabaseManager().getClaimedMilestones(player.getUniqueId()).join();
            String giftId = plugin.getDatabaseManager().getGiftId(player.getUniqueId()).join();
            String giftName = "无";

            if (giftId != null) {
                var gift = plugin.getGiftManager().getGift(giftId);
                if (gift != null) {
                    giftName = ConfigManager.colorize(gift.name);
                }
            }

            String message = plugin.getConfigManager().getMessage("commands.stats")
                .replace("{total}", String.valueOf(total))
                .replace("{claimed_milestones}", claimed)
                .replace("{gift_name}", giftName);

            player.sendMessage(message);
        });
    }

    private void handleBuyGift(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.player_only"));
            return;
        }

        if (!player.hasPermission("alinvite.buygift")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        plugin.getMenuManager().openShopMenu(player);
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alinvite.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("&6===== ALInvite 管理指令 =====");
            sender.sendMessage("&e/alinvite admin help &7- 显示帮助信息");
            sender.sendMessage("&e/alinvite admin reload &7- 重载插件配置");
            sender.sendMessage("&e/alinvite admin givecode <玩家> &7- 生成邀请码");
            sender.sendMessage("&e/alinvite admin clearcode <玩家> &7- 清除玩家邀请码");
            sender.sendMessage("&e/alinvite admin addinvite <玩家> <数量> &7- 增加邀请次数");
            sender.sendMessage("&e/alinvite admin reset <玩家> &7- 重置玩家邀请数据");
            sender.sendMessage("&e/alinvite admin announce &7- 发送全服公告");
            sender.sendMessage("&6============================");
            return;
        }

        String adminSub = args[1].toLowerCase();

        switch (adminSub) {
            case "help" -> {
                sender.sendMessage(plugin.getConfigManager().getMessageRaw("commands.admin.help"));
            }
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMessage("commands.reload"));
            }
            case "givecode" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /alinvite admin givecode <玩家>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                plugin.getInviteManager().generateInviteCode(target.getUniqueId()).thenAccept(code -> {
                    String message = plugin.getConfigManager().getMessage("commands.admin.givecode_success")
                        .replace("{player}", target.getName())
                        .replace("{invite_code}", code);
                    sender.sendMessage(message);
                });
            }
            case "clearcode" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /alinvite admin clearcode <玩家>");
                    return;
                }
                UUID targetUuid = getPlayerUuid(args[2]);
                if (targetUuid == null) {
                    sender.sendMessage("玩家不存在");
                    return;
                }

                plugin.getDatabaseManager().clearInviteCode(targetUuid).join();
                plugin.getCacheManager().invalidateInviteCode(targetUuid);

                String msg = plugin.getConfigManager().getMessage("commands.admin.clearcode_success")
                    .replace("{player}", args[2]);
                sender.sendMessage(msg);
            }
            case "cashrebate" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /alinvite admin cashrebate <玩家> [扣除金额]");
                    return;
                }
                
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                
                if (args.length == 3) {
                    // 查询现金返点
                    plugin.getDatabaseManager().getCashRebateAmount(target.getUniqueId()).thenAccept(amount -> {
                        String message = "§a玩家 " + target.getName() + " 的现金返点余额: §e" + amount + " §a点券";
                        sender.sendMessage(message);
                    });
                } else {
                    // 扣除现金返点
                    double deductAmount;
                    try {
                        deductAmount = Double.parseDouble(args[3]);
                        if (deductAmount <= 0) {
                            sender.sendMessage("扣除金额必须大于0");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage("扣除金额必须是数字");
                        return;
                    }
                    
                    plugin.getDatabaseManager().deductCashRebateAmount(target.getUniqueId(), deductAmount).thenAccept(success -> {
                        if (success) {
                            String message = plugin.getConfigManager().getMessage("commands.admin.cash_rebate_deducted")
                                .replace("{player}", target.getName())
                                .replace("{amount}", String.valueOf((int)deductAmount));
                            sender.sendMessage(message);
                            
                            // 发送消息给玩家
                            String playerMsg = plugin.getConfigManager().getMessage("commands.admin.cash_rebate_player_deducted")
                                .replace("{amount}", String.valueOf((int)deductAmount));
                            target.sendMessage(playerMsg);
                        } else {
                            sender.sendMessage("§c扣除失败: 玩家现金返点余额不足");
                        }
                    });
                }
            }
            case "exchange" -> {
                if (args.length < 4) {
                    sender.sendMessage("用法: /alinvite admin exchange <玩家> <兑换金额>");
                    return;
                }
                
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                
                double exchangeAmount;
                try {
                    exchangeAmount = Double.parseDouble(args[3]);
                    if (exchangeAmount <= 0) {
                        sender.sendMessage("兑换金额必须大于0");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("兑换金额必须是数字");
                    return;
                }
                
                // 先扣除现金返点
                plugin.getDatabaseManager().deductCashRebateAmount(target.getUniqueId(), exchangeAmount).thenAccept(success -> {
                    if (success) {
                        // 扣除成功，自动发放等额点券（必须在主线程中执行）
                        String pointsCommand = plugin.getConfigManager().getConfig()
                            .getString("points_rebate.points_command", "points give {player} {amount}")
                            .replace("{player}", target.getName())
                            .replace("{amount}", String.valueOf((int)exchangeAmount));
                        
                        final String finalPointsCommand = pointsCommand;
                        plugin.getLogger().info("执行兑换命令: " + finalPointsCommand);
                        
                        SchedulerUtils.runTask(plugin, () -> {
                            boolean pointsSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalPointsCommand);
                            
                            if (pointsSuccess) {
                                plugin.getLogger().info("点券发放成功: " + target.getName() + " 获得 " + exchangeAmount + " 点券");
                                String message = plugin.getConfigManager().getMessage("commands.admin.cash_rebate_exchanged")
                                    .replace("{player}", target.getName())
                                    .replace("{amount}", String.valueOf((int)exchangeAmount));
                                sender.sendMessage(message);
                                
                                // 发送消息给玩家
                                String playerMsg = plugin.getConfigManager().getMessage("commands.admin.cash_rebate_player_exchanged")
                                    .replace("{amount}", String.valueOf((int)exchangeAmount));
                                target.sendMessage(playerMsg);
                            } else {
                                plugin.getLogger().warning("点券发放失败: " + finalPointsCommand);
                                String message = plugin.getConfigManager().getMessage("commands.admin.cash_rebate_exchange_failed")
                                    .replace("{player}", target.getName())
                                    .replace("{amount}", String.valueOf((int)exchangeAmount));
                                sender.sendMessage(message);
                                sender.sendMessage("§c请手动发放点券: " + finalPointsCommand);
                                
                                // 发送消息给玩家
                                String playerMsg = "§c现金返点兑换失败，请联系管理员";
                                target.sendMessage(playerMsg);
                            }
                        });
                    } else {
                        sender.sendMessage("§c兑换失败: 玩家现金返点余额不足");
                    }
                });
            }
            case "addinvite" -> {
                if (args.length < 4) {
                    sender.sendMessage("用法: /alinvite admin addinvite <玩家> <数量>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("数量必须是数字");
                    return;
                }

                plugin.getDatabaseManager().getPlayerData(target.getUniqueId()).thenAccept(data -> {
                    if (data != null) {
                        int newTotal = data.totalInvites + amount;
                        plugin.getDatabaseManager().updateInviteCount(target.getUniqueId(), newTotal).join();
                        plugin.getCacheManager().invalidateStats(target.getUniqueId());
                        plugin.getMilestoneManager().checkMilestones(target.getUniqueId(), newTotal);

                        String message = plugin.getConfigManager().getMessage("commands.admin.addinvite_success")
                            .replace("{player}", target.getName())
                            .replace("{amount}", String.valueOf(amount));
                        sender.sendMessage(message);
                    }
                });
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /alinvite admin reset <玩家>");
                    return;
                }
                UUID targetUuid = getPlayerUuid(args[2]);
                if (targetUuid == null) {
                    sender.sendMessage("玩家不存在");
                    return;
                }

                plugin.getDatabaseManager().resetPlayerData(targetUuid).join();
                plugin.getCacheManager().invalidateStats(targetUuid);
                plugin.getCacheManager().invalidateInviteCode(targetUuid);
                plugin.getCacheManager().invalidateGiftId(targetUuid);

                String message = plugin.getConfigManager().getMessage("commands.admin.reset_success")
                    .replace("{player}", args[2]);
                sender.sendMessage(message);
            }
            case "announce" -> {
                if (args.length < 4) {
                    sender.sendMessage("用法: /alinvite admin announce <玩家> <里程碑值>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                int milestoneValue;
                try {
                    milestoneValue = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("里程碑值必须是数字");
                    return;
                }

                var milestone = plugin.getMilestoneManager().getMilestone(milestoneValue);
                if (milestone != null) {
                    String message = plugin.getConfigManager().getConfig()
                        .getString("announcements.messages." + milestoneValue,
                            plugin.getConfigManager().getConfig()
                                .getString("announcements.messages.default", ""));
                    message = message.replace("{player}", target.getName())
                        .replace("{total}", String.valueOf(milestoneValue))
                        .replace("{milestone_name}", milestone.name);
                    plugin.getServer().broadcastMessage(ConfigManager.colorize(message));

                    sender.sendMessage(plugin.getConfigManager().getMessage("commands.admin.announce_success"));
                }
            }
            case "checkgroup" -> {
                if (args.length < 3) {
                    sender.sendMessage("用法: /alinvite admin checkgroup <玩家>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("玩家不存在或不在线");
                    return;
                }
                SchedulerUtils.runTask(plugin, () -> {
                    plugin.getPermissionGroupRewardListener().manualCheck(target);
                    sender.sendMessage("已为玩家 " + target.getName() + " 检查权限组奖励");
                });
            }
            default -> {
                sender.sendMessage("&6===== ALInvite 管理指令 =====");
                sender.sendMessage("&e/alinvite admin help &7- 显示帮助信息");
                sender.sendMessage("&e/alinvite admin reload &7- 重载插件配置");
                sender.sendMessage("&e/alinvite admin givecode <玩家> &7- 生成邀请码");
                sender.sendMessage("&e/alinvite admin addinvite <玩家> <数量> &7- 增加邀请次数");
                sender.sendMessage("&e/alinvite admin reset <玩家> &7- 重置玩家邀请数据");
                sender.sendMessage("&e/alinvite admin announce <玩家> <里程碑值> &7- 发送全服公告");
                sender.sendMessage("&6============================");
            }
        }
    }

    private UUID getPlayerUuid(String name) {
        var offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("code", "stats", "buygift", "help", "admin", "givedj"));
            return filterByInput(completions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            completions.addAll(Arrays.asList("reload", "givecode", "addinvite", "reset", "announce"));
            return filterByInput(completions, args[1]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("givecode")) {
            return filterByInput(
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                args[2]
            );
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("addinvite")) {
            return filterByInput(
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                args[2]
            );
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
            return filterByInput(
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                args[2]
            );
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("addinvite")) {
            return filterByInput(Arrays.asList("1", "5", "10", "100"), args[3]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("announce")) {
            return filterByInput(
                plugin.getMilestoneManager().getMilestones().keySet().stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList()),
                args[3]
            );
        }

        // givedj 命令补全
        if (args.length == 2 && args[0].equalsIgnoreCase("givedj")) {
            return filterByInput(
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                args[1]
            );
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givedj")) {
            return filterByInput(Arrays.asList("100", "500", "1000", "5000"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("givedj")) {
            return filterByInput(Arrays.asList("-norebate"), args[3]);
        }

        return completions;
    }

    private List<String> filterByInput(List<String> list, String input) {
        if (input.isEmpty()) return list;
        String lowerInput = input.toLowerCase();
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(lowerInput))
            .collect(Collectors.toList());
    }

    private void handleGiveDj(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alinvite.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("用法: /alinvite givedj <玩家> <点券数量> [-norebate]");
            sender.sendMessage("参数说明:");
            sender.sendMessage("  - 玩家: 接收点券的玩家名称");
            sender.sendMessage("  - 点券数量: 充值的点券数量");
            sender.sendMessage("  - -norebate: 可选参数，跳过返点处理");
            return;
        }

        String targetPlayer = args[1];
        double amount;
        
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("点券数量必须是数字");
            return;
        }

        boolean skipRebate = args.length >= 4 && "-norebate".equalsIgnoreCase(args[3]);

        plugin.getPointsRebateManager().processRecharge(sender.getName(), targetPlayer, amount, skipRebate)
            .thenAccept(success -> {
                if (success) {
                    String message = skipRebate 
                        ? "已成功为玩家 " + targetPlayer + " 充值 " + amount + " 点券（跳过返点）"
                        : "已成功为玩家 " + targetPlayer + " 充值 " + amount + " 点券，返点已处理";
                    sender.sendMessage(message);
                } else {
                    sender.sendMessage("充值失败，请检查玩家名称和金额");
                }
            });
    }
    
    /**
     * 处理玩家查询现金返点
     */
    private void handleCashRebate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.player_only"));
            return;
        }
        
        if (!player.hasPermission("alinvite.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no_permission"));
            return;
        }
        
        // 检查是否拥有现金返点权限
        String cashPermission = plugin.getConfigManager().getConfig()
            .getString("cash_rebate.permission", "alinvite.rebate.cash");
        
        if (!player.hasPermission(cashPermission)) {
            player.sendMessage("§c您没有现金返点权限");
            return;
        }
        
        plugin.getDatabaseManager().getCashRebateAmount(player.getUniqueId()).thenAccept(amount -> {
            String message = "§a您的现金返点余额: §e" + amount + " §a点券";
            player.sendMessage(message);
            player.sendMessage("§7提示: 现金返点用于后期兑换，不会自动发放点券");
        });
    }
}
