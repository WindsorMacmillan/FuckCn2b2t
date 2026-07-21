package com.windsor.fuckCn2b2t;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Fkcn2b2tCommand implements TabExecutor {

    private final FuckCn2b2t plugin;
    private final ViolationManager violationManager;
    private final NewPlayerManager newPlayerManager;

    private static final DecimalFormat DF = new DecimalFormat("#.##");

    private static final List<String> SUBCOMMANDS = List.of("mute", "unmute", "stat", "reload");

    public Fkcn2b2tCommand(FuckCn2b2t plugin, ViolationManager violationManager, NewPlayerManager newPlayerManager) {
        this.plugin = plugin;
        this.violationManager = violationManager;
        this.newPlayerManager = newPlayerManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c用法: /fkcn2b2t <mute|unmute|stat|reload> [args]");
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);

        return switch (sub) {
            case "mute" -> handleMute(sender, subArgs);
            case "unmute" -> handleUnmute(sender, subArgs);
            case "stat" -> handleStat(sender, subArgs);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage("§c未知子命令: " + sub + "。可用: mute, unmute, stat, reload");
                yield true;
            }
        };
    }

    // ---------- mute ----------

    private boolean handleMute(CommandSender sender, String[] args) {
        if (sender instanceof Player && !sender.isOp()) {
            return false;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /fkcn2b2t mute <玩家名> <分钟/off>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }

        String secondArg = args[1].toLowerCase();
        if (secondArg.equals("off") || secondArg.equals("false")) {
            violationManager.removeMute(target);
            sender.sendMessage("§a已解除玩家 " + target.getName() + " 的隐形禁言。");
            return true;
        }

        long minutes;
        try {
            minutes = Long.parseLong(args[1]);
            if (minutes <= 0) {
                sender.sendMessage("§c分钟数必须为正整数。");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c分钟数必须为数字。");
            return true;
        }

        violationManager.addMuteTime(target, minutes);
        sender.sendMessage("§6已将玩家 " + target.getName() + " 隐形禁言 " + minutes + " 分钟。");
        return true;
    }

    // ---------- unmute ----------

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (sender instanceof Player && !sender.isOp()) {
            return false;
        }
        if (args.length < 1) {
            sender.sendMessage("§c用法: /fkcn2b2t unmute <玩家名>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }

        violationManager.removeMute(target);
        sender.sendMessage("§a已解除玩家 " + target.getName() + " 的隐形禁言。");
        return true;
    }

    // ---------- stat ----------

    private boolean handleStat(CommandSender sender, String[] args) {
        if (sender instanceof Player && !sender.isOp()) {
            return false;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§c玩家 " + args[0] + " 不在线或不存在。");
                return true;
            }
        } else {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("§c请指定玩家名。");
                return true;
            }
        }

        boolean isNew = newPlayerManager.isNewPlayer(target);
        long playSeconds = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;
        double playHours = playSeconds / 3600.0;
        double activeScore = newPlayerManager.calculateActiveScore(target);
        List<NewPlayerManager.StatBreakdown> breakdowns = newPlayerManager.getBreakdown(target);

        StringBuilder sb = new StringBuilder();
        sb.append("§6===== §e").append(target.getName()).append(" §6玩家活跃数据 =====\n");
        sb.append(isNew ? "§c新玩家§7（" : "§a老玩家§7（")
                .append("§7游玩时长: §f").append(DF.format(playHours)).append("§7小时 ")
                .append("§7活跃分: §b").append(DF.format(activeScore)).append(" ");

        if (newPlayerManager.isActiveCheckEnabled()) {
            sb.append("§7判定方式: §a").append(DF.format(newPlayerManager.getActiveThreshold())).append("活跃分§7）\n");
            for (NewPlayerManager.StatBreakdown bd : breakdowns) {
                sb.append("  §7").append(bd.statName).append(": §f")
                        .append(DF.format(bd.rawValue))
                        .append(" §7× §f").append(DF.format(bd.weight))
                        .append(" §7= §b").append(DF.format(bd.weightedValue))
                        .append("\n");
            }
        } else {
            sb.append("§7判定方式: §e在线时长（≤24小时）\n");
        }

        sender.sendMessage(sb.toString());
        return true;
    }

    // ---------- reload ----------

    private boolean handleReload(CommandSender sender) {
        if (sender instanceof Player && !sender.isOp()) {
            return false;
        }
        plugin.reloadPlugin();
        sender.sendMessage("§aFuckCn2b2t 配置已重载。");
        return true;
    }

    // ========== Tab 补全 ==========

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterTab(args[0], SUBCOMMANDS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("mute".equals(sub) || "unmute".equals(sub) || "stat".equals(sub)) {
                // 补全在线玩家名
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filterTab(args[1], names);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mute")) {
            return filterTab(args[2], List.of("off", "30", "60", "120", "1440"));
        }
        return List.of();
    }

    private static List<String> filterTab(String prefix, List<String> candidates) {
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(c);
            }
        }
        return result;
    }
}
