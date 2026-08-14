package com.windsor.fuckCn2b2t;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 集中加载和管理插件配置。
 *
 * <p>所有字段在 {@link #load()} 中从 {@code config.yml} 读取。
 * 子类持有本对象引用即可获得最新值，热重载时只需调用 {@code load()}。</p>
 */
public class PluginConfig {

    private final FuckCn2b2t plugin;

    // ---------- 调试 ----------
    private boolean debugMode;

    // ========== 一、新玩家聊天检查功能类 ==========
    private boolean chatCheckEnabled;

    private boolean directKeywordsEnabled;
    private List<String> directKeywords;

    private boolean longMessageEnabled;
    private int maxMessageLength;

    private boolean interactiveChatCompatEnabled;
    private Pattern interactiveChatStripPattern;

    private boolean linkDetectionEnabled;
    private Pattern urlPattern;
    private Pattern validUrlChars;
    private Pattern domainPortPattern;

    private boolean spamDetectionEnabled;
    private int spamTimeWindowSeconds;
    private int spamMaxMessages;

    private boolean excessiveDigitsEnabled;
    private int excessiveDigitCount;

    private boolean keywordFilterEnabled;
    private java.util.List<String> keywordFilterKeywords;

    private boolean rareCharCheckEnabled;
    private int maxRareCharCount;

    // ========== 二、聊天违规处置措施类 ==========
    private boolean penaltiesEnabled;
    private boolean notifyOp;
    private boolean silentMode;
    private String silentChatFormat;

    private int muteThresholdPoints;
    private int muteBaseDurationMinutes;
    private int muteAdditionalDurationMinutes;

    private int warningPointsMultiple;
    private String warningMessage;

    private int kickPoints;
    private String kickMessage;

    private int banPoints;
    private String banMessage;

    private boolean interceptPrivateMessage;
    private java.util.List<String> privateMessageCommands;
    private boolean interceptSign;
    private boolean interceptAnvil;
    private boolean interceptBook;

    // ========== 三、新玩家判定规则类 ==========
    private boolean activeScoreEnabled;
    private double activeScoreThreshold;
    private List<StatWeight> statWeights;

    private int playtimeThresholdSeconds;

    // ========== 四、新玩家提醒 ==========
    private boolean loginMessageEnabled;
    private boolean periodicReminderEnabled;
    private int reminderIntervalTicks;
    private String reminderMessage;

    // ==================================================================

    public PluginConfig(FuckCn2b2t plugin) {
        this.plugin = plugin;
        this.statWeights = new ArrayList<>();
        load();
    }

    /**
     * 从 config.yml 重新读取全部配置。
     * 热重载时调用此方法即可。
     */
    public void load() {
        FileConfiguration cfg = plugin.getConfig();

        // ---------- 调试 ----------
        debugMode = cfg.getBoolean("debug-mode", false);

        // ========== 一、新玩家聊天检查功能类 ==========
        ConfigurationSection chatCheck = getSection(cfg, "new-player-chat-check");
        chatCheckEnabled = chatCheck.getBoolean("enabled", true);

        ConfigurationSection directKeywordsSection = getSection(chatCheck, "direct-keywords");
        directKeywordsEnabled = directKeywordsSection.getBoolean("enabled", true);
        directKeywords = directKeywordsSection.getStringList("keywords").stream()
                .filter(keyword -> keyword != null && !keyword.isEmpty())
                .toList();

        ConfigurationSection longMsg = getSection(chatCheck, "long-message");
        longMessageEnabled = longMsg.getBoolean("enabled", true);
        maxMessageLength = longMsg.getInt("max-length", 40);

        ConfigurationSection icc = getSection(chatCheck, "interactive-chat-compat");
        interactiveChatCompatEnabled = icc.getBoolean("enabled", true);
        interactiveChatStripPattern = Pattern.compile(icc.getString("strip-pattern", "<chat=[^>]+>"));

        ConfigurationSection link = getSection(chatCheck, "link-detection");
        linkDetectionEnabled = link.getBoolean("enabled", true);
        urlPattern = Pattern.compile(link.getString("pattern",
                "(?i)(?:https?://|ftp://|www\\.)[a-zA-Z0-9\\-.]+\\.(?:[a-zA-Z]{2,}(?:/\\S*)?)|" +
                        "(?i)(?:[a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,}(?:/\\S*)?"));
        validUrlChars = Pattern.compile(link.getString("valid-url-chars", "[^a-zA-Z0-9.\\-/:?&=#]"));
        domainPortPattern = Pattern.compile(link.getString("domain-port-pattern",
                "[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+\\s*[:：]\\s*\\d+"));

        ConfigurationSection spam = getSection(chatCheck, "spam-detection");
        spamDetectionEnabled = spam.getBoolean("enabled", true);
        spamTimeWindowSeconds = spam.getInt("time-window-seconds", 15);
        spamMaxMessages = spam.getInt("max-messages", 3);

        ConfigurationSection digits = getSection(chatCheck, "excessive-digits");
        excessiveDigitsEnabled = digits.getBoolean("enabled", true);
        excessiveDigitCount = digits.getInt("max-digit-count", 9);

        ConfigurationSection kw = getSection(chatCheck, "keyword-filter");
        keywordFilterEnabled = kw.getBoolean("enabled", false);
        keywordFilterKeywords = kw.getStringList("keywords");

        ConfigurationSection rare = getSection(chatCheck, "rare-char-check");
        rareCharCheckEnabled = rare.getBoolean("enabled", true);
        maxRareCharCount = rare.getInt("max-rare-count", 5);

        // ========== 二、聊天违规处置措施类 ==========
        ConfigurationSection penalties = getSection(cfg, "violation-penalties");
        penaltiesEnabled = penalties.getBoolean("enabled", true);
        notifyOp = penalties.getBoolean("notify-op", true);
        silentMode = penalties.getBoolean("silent-mode", true);
        silentChatFormat = penalties.getString("silent-chat-format",
                "&f[%tab_placeholder_condition:posname%&f]%tab_replace_playerTitle_use%&r" +
                        "<gradient:%tab_placeholder_condition:titlecolor%%tab_placeholder_condition:postype%>" +
                        "{player}&f: {message}");

        ConfigurationSection mute = getSection(penalties, "mute");
        muteThresholdPoints = mute.getInt("threshold-points", 5);
        muteBaseDurationMinutes = mute.getInt("base-duration-minutes", 5);
        muteAdditionalDurationMinutes = mute.getInt("additional-duration-minutes", 1);

        ConfigurationSection warn = getSection(penalties, "warning");
        warningPointsMultiple = warn.getInt("points-multiple", 10);
        warningMessage = warn.getString("message",
                "&f[&4聊天&f] &c你聊天管制期内违规行为较多，如违规严重会导致踢出或封禁。");

        ConfigurationSection kick = getSection(penalties, "kick");
        kickPoints = kick.getInt("points", 41);
        kickMessage = kick.getString("message",
                "&c严管期内聊天频繁违规，继续违规会导致封禁！");

        ConfigurationSection ban = getSection(penalties, "ban");
        banPoints = ban.getInt("points", 61);
        banMessage = ban.getString("message",
                "&c严管期内聊天严重违规，详情请联系腐竹");

        ConfigurationSection intercept = getSection(penalties, "intercept");
        interceptPrivateMessage = intercept.getBoolean("private-message", true);
        privateMessageCommands = intercept.getStringList("private-message-commands");
        if (privateMessageCommands.isEmpty()) {
            privateMessageCommands = java.util.List.of("tell", "msg", "w", "message", "whisper", "m");
        }
        interceptSign = intercept.getBoolean("sign", true);
        interceptAnvil = intercept.getBoolean("anvil", true);
        interceptBook = intercept.getBoolean("book", true);

        // ========== 三、新玩家判定规则类 ==========
        ConfigurationSection detection = getSection(cfg, "new-player-detection");

        ConfigurationSection activeScore = getSection(detection, "active-score");
        activeScoreEnabled = activeScore.getBoolean("enabled", true);
        activeScoreThreshold = activeScore.getDouble("threshold", 70000.0);
        statWeights.clear();
        List<?> statsList = activeScore.getList("statistics");
        if (statsList != null) {
            for (Object obj : statsList) {
                String stat = null;
                double weight = 1.0;
                if (obj instanceof ConfigurationSection sec) {
                    stat = sec.getString("stat");
                    weight = sec.getDouble("weight", 1.0);
                } else if (obj instanceof Map<?, ?> map) {
                    stat = (String) map.get("stat");
                    Object wObj = map.get("weight");
                    if (wObj instanceof Number) {
                        weight = ((Number) wObj).doubleValue();
                    }
                }
                if (stat != null && !stat.isEmpty()) {
                    statWeights.add(new StatWeight(stat, weight));
                }
            }
        }
        plugin.getLogger().info("活跃检查启用: " + activeScoreEnabled +
                ", 统计项数量: " + statWeights.size() +
                ", 阈值: " + activeScoreThreshold);

        ConfigurationSection playtime = getSection(detection, "playtime");
        playtimeThresholdSeconds = playtime.getInt("threshold-seconds", 86400);

        // ========== 四、新玩家提醒 ==========
        ConfigurationSection reminder = getSection(cfg, "new-player-reminder");
        loginMessageEnabled = reminder.getBoolean("login-message", true);

        ConfigurationSection periodic = getSection(reminder, "periodic-reminder");
        periodicReminderEnabled = periodic.getBoolean("enabled", true);
        reminderIntervalTicks = periodic.getInt("interval-ticks", 18000);

        reminderMessage = reminder.getString("message",
                "&f[&4聊天&f] &c新玩家有一段时间的严管聊天行为，" +
                        "&4期间不允许频繁发送消息、发送长消息、发送链接。" +
                        "&d正常游玩即可结束聊天严管。");
    }

    // -------- 便捷取 ConfigurationSection（避免 NPE） --------

    private static ConfigurationSection getSection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        return section != null ? section : parent.createSection(key);
    }

    // ==================================================================
    // Getter
    // ==================================================================

    // ---------- 调试 ----------
    public boolean isDebugMode() {
        return debugMode;
    }

    // ========== 一、新玩家聊天检查功能类 ==========
    public boolean isChatCheckEnabled() {
        return chatCheckEnabled;
    }

    public boolean isDirectKeywordsEnabled() {
        return directKeywordsEnabled;
    }

    public List<String> getDirectKeywords() {
        return directKeywords;
    }

    public boolean isLongMessageEnabled() {
        return longMessageEnabled;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isInteractiveChatCompatEnabled() {
        return interactiveChatCompatEnabled;
    }

    public Pattern getInteractiveChatStripPattern() {
        return interactiveChatStripPattern;
    }

    public boolean isLinkDetectionEnabled() {
        return linkDetectionEnabled;
    }

    public Pattern getUrlPattern() {
        return urlPattern;
    }

    public Pattern getValidUrlChars() {
        return validUrlChars;
    }

    public Pattern getDomainPortPattern() {
        return domainPortPattern;
    }

    public boolean isSpamDetectionEnabled() {
        return spamDetectionEnabled;
    }

    public int getSpamTimeWindowSeconds() {
        return spamTimeWindowSeconds;
    }

    public int getSpamMaxMessages() {
        return spamMaxMessages;
    }

    public boolean isExcessiveDigitsEnabled() {
        return excessiveDigitsEnabled;
    }

    public int getExcessiveDigitCount() {
        return excessiveDigitCount;
    }

    public boolean isKeywordFilterEnabled() {
        return keywordFilterEnabled;
    }

    public java.util.List<String> getKeywordFilterKeywords() {
        return keywordFilterKeywords;
    }

    public boolean isRareCharCheckEnabled() {
        return rareCharCheckEnabled;
    }

    public int getMaxRareCharCount() {
        return maxRareCharCount;
    }

    // ========== 二、聊天违规处置措施类 ==========
    public boolean isPenaltiesEnabled() {
        return penaltiesEnabled;
    }

    public boolean isNotifyOp() {
        return notifyOp;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public String getSilentChatFormat() {
        return silentChatFormat;
    }

    public int getMuteThresholdPoints() {
        return muteThresholdPoints;
    }

    public int getMuteBaseDurationMinutes() {
        return muteBaseDurationMinutes;
    }

    public int getMuteAdditionalDurationMinutes() {
        return muteAdditionalDurationMinutes;
    }

    public int getWarningPointsMultiple() {
        return warningPointsMultiple;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public int getKickPoints() {
        return kickPoints;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public int getBanPoints() {
        return banPoints;
    }

    public String getBanMessage() {
        return banMessage;
    }

    public boolean isInterceptPrivateMessage() {
        return interceptPrivateMessage;
    }

    public java.util.List<String> getPrivateMessageCommands() {
        return privateMessageCommands;
    }

    public boolean isInterceptSign() {
        return interceptSign;
    }

    public boolean isInterceptAnvil() {
        return interceptAnvil;
    }

    public boolean isInterceptBook() {
        return interceptBook;
    }

    // ========== 三、新玩家判定规则类 ==========
    public boolean isActiveScoreEnabled() {
        return activeScoreEnabled;
    }

    public double getActiveScoreThreshold() {
        return activeScoreThreshold;
    }

    public List<StatWeight> getStatWeights() {
        return statWeights;
    }

    public int getPlaytimeThresholdSeconds() {
        return playtimeThresholdSeconds;
    }

    // ========== 四、新玩家提醒 ==========
    public boolean isLoginMessageEnabled() {
        return loginMessageEnabled;
    }

    public boolean isPeriodicReminderEnabled() {
        return periodicReminderEnabled;
    }

    public int getReminderIntervalTicks() {
        return reminderIntervalTicks;
    }

    public String getReminderMessage() {
        return reminderMessage;
    }

    // ==================================================================
    // 内部类
    // ==================================================================

    public static class StatWeight {
        private final String stat;
        private final double weight;

        StatWeight(String stat, double weight) {
            this.stat = stat;
            this.weight = weight;
        }

        public String getStat() {
            return stat;
        }

        public double getWeight() {
            return weight;
        }
    }
}
