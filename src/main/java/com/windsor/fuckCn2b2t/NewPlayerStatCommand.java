package com.windsor.fuckCn2b2t;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class NewPlayerStatCommand implements TabExecutor {

    private final NewPlayerManager newPlayerManager;

    private static final DecimalFormat DF = new DecimalFormat("#.##");

    public NewPlayerStatCommand(NewPlayerManager newPlayerManager) {
        this.newPlayerManager = newPlayerManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NonNull [] args) {
        // 权限检查：仅 OP 或控制台
        if (sender instanceof Player && !sender.isOp()) {
            return false; // 普通玩家显示未知命令
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

        // 获取数据
        boolean isNew = newPlayerManager.isNewPlayer(target);
        long playSeconds = target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;
        double playHours = playSeconds / 3600.0;
        double activeScore = newPlayerManager.calculateActiveScore(target);
        List<NewPlayerManager.StatBreakdown> breakdowns = newPlayerManager.getBreakdown(target);

        // 构建消息
        StringBuilder sb = new StringBuilder();
        sb.append("§6===== §e").append(target.getName()).append(" §6玩家活跃数据 =====\n");
        sb.append(isNew ? "§c新玩家§7（" : "§a老玩家§7（").append("§7游玩时长: §f").append(DF.format(playHours)).append("§7小时 ")
                .append("§7活跃分: §b").append(DF.format(activeScore)).append(" ");

        // 是否启用活跃检查
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterTab(args[0], onlinePlayerNames());
        }
        return List.of();
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filterTab(String prefix, List<String> candidates) {
        if (prefix.isEmpty()) return candidates;
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(c);
            }
        }
        return result;
    }
}