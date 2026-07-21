package com.windsor.fuckCn2b2t;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ShadowBanCommand implements TabExecutor {

    private final ViolationManager violationManager;

    public ShadowBanCommand(ViolationManager violationManager) {
        this.violationManager = violationManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NonNull [] args) {
        // 普通玩家：返回 false 显示“未知命令”
        if (sender instanceof Player && !sender.isOp()) {
            return false;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /shadowban <玩家名> <分钟/off/false>");
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterTab(args[0], onlinePlayerNames());
        }
        if (args.length == 2) {
            return filterTab(args[1], List.of("off", "30", "60", "120", "1440"));
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