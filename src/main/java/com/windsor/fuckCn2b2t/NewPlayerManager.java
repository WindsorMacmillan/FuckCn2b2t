package com.windsor.fuckCn2b2t;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NewPlayerManager {

    private final FuckCn2b2t plugin;
    private final PluginConfig config;
    private List<PluginConfig.StatWeight> statWeights = new ArrayList<>();

    public NewPlayerManager(FuckCn2b2t plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        loadStatWeights();
    }

    public void reload() {
        loadStatWeights();  // 从 PluginConfig 重新缓存统计项列表
        plugin.getLogger().info("NewPlayerManager 配置已重载，统计项数量: " + statWeights.size());
    }

    private void loadStatWeights() {
        // PluginConfig 已从 config.yml 读取最新统计项
        List<PluginConfig.StatWeight> weights = config.getStatWeights();
        statWeights = new ArrayList<>(weights);
    }

    /**
     * 判断玩家是否为新玩家
     */
    public boolean isNewPlayer(Player player) {
        // 调试模式：所有玩家视为新玩家
        if (plugin.isDebugMode()) {
            return true;
        }

        // bypass 权限：永久跳过新玩家判定
        if (player.hasPermission("fuckcn2b2t.bypass")) {
            return false;
        }

        // 启用活跃度检查且配置有效
        if (config.isActiveScoreEnabled() && !statWeights.isEmpty()) {
            double score = calculateActiveScore(player);
            return score < config.getActiveScoreThreshold();
        }

        // 回退：使用在线时长判定
        int playedSeconds = getPlaytimeInSeconds(player);
        return playedSeconds < config.getPlaytimeThresholdSeconds();
    }

    /**
     * 计算玩家的活跃度积分
     */
    public double calculateActiveScore(Player player) {
        double total = 0.0;
        for (PluginConfig.StatWeight sw : statWeights) {
            try {
                String parsed = PlaceholderAPI.setPlaceholders(player, "%" + sw.getStat() + "%");
                double value = Double.parseDouble(parsed.replace(",", ""));
                total += value * sw.getWeight();
            } catch (NumberFormatException e) {
                // 忽略解析失败的项
            }
        }
        return total;
    }

    /**
     * 遍历所有在线玩家，对每个符合条件的玩家发送定时提醒
     */
    public void checkAndRemindNewPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            checkAndRemindPlayer(player);
        }
    }

    /**
     * 判断单个玩家是否需要发送提醒，并执行发送
     */
    public void checkAndRemindPlayer(Player player) {
        if (isNewPlayer(player)) {
            sendReminderMessage(player);
        }
    }

    /**
     * 获取玩家的游玩时间（秒）
     */
    private int getPlaytimeInSeconds(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;
    }

    /**
     * 向玩家发送聊天管制提醒
     */
    public void sendReminderMessage(Player player) {
        String rawMessage = config.getReminderMessage();
        String coloredMessage = PlaceholderAPI.setPlaceholders(player, rawMessage.replace('&', '§'));
        player.sendMessage(coloredMessage);
    }

    public boolean isLoginMessageEnabled() {
        return config.isLoginMessageEnabled();
    }

    public List<StatBreakdown> getBreakdown(Player player) {
        List<StatBreakdown> list = new ArrayList<>();
        if (!config.isActiveScoreEnabled() || statWeights.isEmpty()) {
            return list;
        }
        for (PluginConfig.StatWeight sw : statWeights) {
            double raw = 0.0;
            try {
                String parsed = PlaceholderAPI.setPlaceholders(player, "%" + sw.getStat() + "%");
                raw = Double.parseDouble(parsed.replace(",", ""));
            } catch (NumberFormatException ignored) {}
            double weighted = raw * sw.getWeight();
            list.add(new StatBreakdown(sw.getStat(), raw, sw.getWeight(), weighted));
        }
        return list;
    }

    public boolean isActiveCheckEnabled() {
        return config.isActiveScoreEnabled() && !statWeights.isEmpty();
    }

    public double getActiveThreshold() {
        return config.getActiveScoreThreshold();
    }

    // ---------- 内部类 ----------

    public static class StatBreakdown {
        public final String statName;
        public final double rawValue;
        public final double weight;
        public final double weightedValue;

        public StatBreakdown(String statName, double rawValue, double weight, double weightedValue) {
            this.statName = statName;
            this.rawValue = rawValue;
            this.weight = weight;
            this.weightedValue = weightedValue;
        }
    }
}
