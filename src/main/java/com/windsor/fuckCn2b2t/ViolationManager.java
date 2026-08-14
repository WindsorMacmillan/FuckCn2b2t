package com.windsor.fuckCn2b2t;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationManager {

    private final FuckCn2b2t plugin;
    private final PluginConfig config;
    private final Map<UUID, Integer> violationPoints = new ConcurrentHashMap<>();
    private final Map<UUID, Long> muteEndTime = new ConcurrentHashMap<>();
    private final File dataFile;
    private org.bukkit.configuration.file.YamlConfiguration dataConfig;

    public ViolationManager(FuckCn2b2t plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        dataFile = new File(plugin.getDataFolder(), "violations.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("violations.yml", false);
        }
        dataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        loadPoints();
        loadMuteData();
    }

    private void loadPoints() {
        for (String key : dataConfig.getKeys(false)) {
            if (key.equals("mute_data")) continue;
            try {
                UUID uuid = UUID.fromString(key);
                int points = dataConfig.getInt(key);
                violationPoints.put(uuid, points);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("已加载 " + violationPoints.size() + " 个玩家的违规积分");
    }

    private void loadMuteData() {
        if (dataConfig.contains("mute_data")) {
            for (String key : dataConfig.getConfigurationSection("mute_data").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long endTime = dataConfig.getLong("mute_data." + key);
                    if (endTime > System.currentTimeMillis()) {
                        muteEndTime.put(uuid, endTime);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("已加载 " + muteEndTime.size() + " 个玩家的禁言数据");
    }

    public void savePoints() {
        for (Map.Entry<UUID, Integer> entry : violationPoints.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        dataConfig.set("mute_data", null);
        for (Map.Entry<UUID, Long> entry : muteEndTime.entrySet()) {
            if (entry.getValue() > System.currentTimeMillis()) {
                dataConfig.set("mute_data." + entry.getKey().toString(), entry.getValue());
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("保存违规积分/禁言数据失败: " + e.getMessage());
        }
    }

    public boolean isMuted(Player player) {
        UUID uuid = player.getUniqueId();
        Long end = muteEndTime.get(uuid);
        if (end == null) return false;
        if (end > System.currentTimeMillis()) {
            return true;
        } else {
            muteEndTime.remove(uuid);
            savePoints();
            checkPointsOnMuteEnd(player);
            return false;
        }
    }

    private void checkPointsOnMuteEnd(Player player) {
        int points = getPoints(player);
        if (points >= config.getBanPoints()) {
            String reason = config.getBanMessage().replace('&', '§');
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), reason, null, null);
            player.kick(Component.text(reason));
        } else if (points >= config.getKickPoints()) {
            String reason = config.getKickMessage().replace('&', '§');
            player.kick(Component.text(reason));
        }
    }

    public void addMuteTime(Player player, long minutes) {
        UUID uuid = player.getUniqueId();
        long currentEnd = muteEndTime.getOrDefault(uuid, System.currentTimeMillis());
        long newEnd = currentEnd + minutes * 60 * 1000;
        muteEndTime.put(uuid, newEnd);
        Bukkit.getLogger().info("玩家 " + player.getName() + " 隐形禁言时间增加 " + minutes + " 分钟，结束时间: " + newEnd);
        savePoints();
    }

    public void removeMute(Player player) {
        UUID uuid = player.getUniqueId();
        if (muteEndTime.remove(uuid) != null) {
            savePoints();
            plugin.getLogger().info("玩家 " + player.getName() + " 的隐形禁言已被管理员手动解除。");
        }
    }

    /**
     * 增加玩家的违规积分，并返回新的总分
     */
    public void addViolation(Player player, String violationReason, String messageContent) {
        UUID uuid = player.getUniqueId();
        int oldPoints = violationPoints.getOrDefault(uuid, 0);
        int newPoints = oldPoints + 1;
        violationPoints.put(uuid, newPoints);

        savePoints();

        // 控制台日志
        Bukkit.getLogger().warning(String.format("玩家 %s 尝试%s（违规次数%d），被隐形拦截：%s",
                player.getName(), violationReason, newPoints, messageContent));

        // OP 通知（根据配置）
        if (config.isNotifyOp()) {
            Component notification = Component.text("玩家 ")
                    .append(Component.text(player.getName()))
                    .append(Component.text(" 尝试" + violationReason + "（违规次数" + newPoints + "），被隐形拦截：" + messageContent));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.isOp()) {
                    online.sendMessage(notification);
                }
            }
        }

        // 禁言检查
        if (config.getMuteThresholdPoints() > 0 && newPoints % config.getMuteThresholdPoints() == 0) {
            long muteMinutes = (long) config.getMuteBaseDurationMinutes() * (newPoints / config.getMuteThresholdPoints());
            addMuteTime(player, muteMinutes);
        }

        // 警告消息（达到 points-multiple 倍数时发送）
        if (newPoints % config.getWarningPointsMultiple() == 0) {
            String warnMsg = config.getWarningMessage().replace('&', '§');
            player.sendMessage(Component.text(warnMsg));
        }

        // 踢出/封禁检查
        int finalNewPoints = newPoints;
        plugin.getScheduler().runGlobal(() -> {
            if (finalNewPoints >= config.getBanPoints()) {
                String reason = config.getBanMessage().replace('&', '§');
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), reason, null, null);
                player.kick(Component.text(reason));
                muteEndTime.remove(uuid);
            } else if (finalNewPoints >= config.getKickPoints()) {
                String reason = config.getKickMessage().replace('&', '§');
                player.kick(Component.text(reason));
                muteEndTime.remove(uuid);
            }
            savePoints();
        });

    }

    public int getPoints(Player player) {
        return violationPoints.getOrDefault(player.getUniqueId(), 0);
    }
}
