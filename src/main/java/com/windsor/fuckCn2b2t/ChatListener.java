package com.windsor.fuckCn2b2t;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final NewPlayerManager newPlayerManager;
    private final ViolationManager violationManager;
    private final PluginConfig config;
    private final Map<Player, Queue<Long>> messageTimestamps = new ConcurrentHashMap<>();

    // MiniMessage 解析器（复用，线程安全）
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // 匹配传统颜色代码 &a, &l, &#RRGGBB 等（不变，纯工具性）
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or]|#[0-9a-fA-F]{6})");

    public ChatListener(NewPlayerManager newPlayerManager, ViolationManager violationManager, PluginConfig config) {
        this.newPlayerManager = newPlayerManager;
        this.violationManager = violationManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // InteractiveChat 兼容：移除聊天组件标记
        String stripedPlainMessage = plainMessage;
        if (config.isInteractiveChatCompatEnabled()) {
            stripedPlainMessage = config.getInteractiveChatStripPattern().matcher(plainMessage).replaceAll("");
        }

        // 优先处理禁言状态（无论新老玩家）
        if (violationManager.isMuted(player)) {
            handleMutedPlayerChat(event, player, plainMessage, stripedPlainMessage);
            return;
        }

        // 非新玩家，放行
        if (!newPlayerManager.isNewPlayer(player)) {
            return;
        }

        // 聊天检查功能总开关
        if (!config.isChatCheckEnabled()) {
            recordMessage(player);
            return;
        }

        // 检查违规（包括频率）
        String reason = getViolationReason(stripedPlainMessage, player);
        if (reason == null) {
            // 合法消息：记录时间戳，放行
            recordMessage(player);
            return;
        }

        // 违规处理
        event.setCancelled(true);
        violationManager.addViolation(player, reason, plainMessage);

        // 仅自己可见模式（silent-mode）则向玩家发送假消息
        if (config.isSilentMode()) {
            sendFormattedMessageToPlayer(player, plainMessage);
        }
    }

    /**
     * 处理已禁言玩家的聊天消息
     */
    private void handleMutedPlayerChat(AsyncChatEvent event, Player player,
                                        String plainMessage, String stripedPlainMessage) {
        event.setCancelled(true);

        // 检查内容违规（忽略频率）
        if (config.isChatCheckEnabled()) {
            String reason = getViolationReasonForMuted(stripedPlainMessage);
            if (reason != null) {
                violationManager.addViolation(player, reason, plainMessage);
                // 禁言期间每次额外增加禁言时长
                if (config.getMuteAdditionalDurationMinutes() > 0) {
                    violationManager.addMuteTime(player, config.getMuteAdditionalDurationMinutes());
                }
            }
        }

        String logMsg = String.format("玩家 %s 在隐形禁言期间尝试发送：%s", player.getName(), plainMessage);
        Bukkit.getLogger().info(logMsg);

        // OP 通知
        if (config.isNotifyOp()) {
            Component opMsg = Component.text("玩家 ")
                    .append(Component.text(player.getName()))
                    .append(Component.text(" 在隐形禁言期间尝试发送："))
                    .append(Component.text(plainMessage));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.isOp()) {
                    online.sendMessage(opMsg);
                }
            }
        }

        // 仅自己可见模式
        if (config.isSilentMode()) {
            sendFormattedMessageToPlayer(player, plainMessage);
        }
    }

    /**
     * 获取违规原因，若不违规返回 null
     * 用于新玩家（含频率检测）
     */
    private String getViolationReason(String plainMessage, Player player) {
        if (config.isLongMessageEnabled() && plainMessage.length() > config.getMaxMessageLength()) {
            return "发送超长消息";
        }
        if (config.isSpamDetectionEnabled() && isSpamming(player)) {
            return "频繁发送消息";
        }
        if (config.isLinkDetectionEnabled() && containsUrl(plainMessage)) {
            return "发送链接";
        }
        if (config.isExcessiveDigitsEnabled() && containsExcessiveDigits(plainMessage)) {
            return "发送过多数字";
        }
        return null;
    }

    /**
     * 获取违规原因（禁言版本，忽略频率）
     */
    private String getViolationReasonForMuted(String plainMessage) {
        if (config.isLongMessageEnabled() && plainMessage.length() > config.getMaxMessageLength()) {
            return "发送超长消息";
        }
        if (config.isLinkDetectionEnabled() && containsUrl(plainMessage)) {
            return "发送链接";
        }
        if (config.isExcessiveDigitsEnabled() && containsExcessiveDigits(plainMessage)) {
            return "发送过多数字";
        }
        return null;
    }

    // ==================================================================
    // MiniMessage 转换（纯工具方法，不变）
    // ==================================================================

    private String legacyToMiniMessage(String input) {
        Matcher matcher = LEGACY_COLOR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            String replacement = convertColorCode(code);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String convertColorCode(String code) {
        if (code.startsWith("#")) {
            return "<color:" + code + ">";
        }
        return switch (code) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            case "r" -> "<reset>";
            default -> "";
        };
    }

    private String convertToMiniMessage(String input) {
        String withAmpersand = input.replace('§', '&');
        return legacyToMiniMessage(withAmpersand);
    }

    // ==================================================================
    // 消息发送
    // ==================================================================

    private void sendFormattedMessageToPlayer(Player player, String messageContent) {
        String modifiedContent = appendMeow(messageContent);
        String format = config.getSilentChatFormat()
                .replace("{player}", player.getName())
                .replace("{message}", modifiedContent);
        String parsed = PlaceholderAPI.setPlaceholders(player, format);
        String miniMessageString = convertToMiniMessage(parsed);
        try {
            Component formatted = miniMessage.deserialize(miniMessageString);
            player.sendMessage(formatted);
        } catch (Exception e) {
            String plainFallback = PlainTextComponentSerializer.plainText().serialize(
                    Component.text(parsed.replaceAll("[&§][0-9a-fk-or#]", ""))
            );
            player.sendMessage(Component.text(plainFallback));
            Bukkit.getLogger().warning("[FuckCn2b2t] MiniMessage 解析失败: " + e.getMessage() + "，已降级为纯文本");
        }
    }

    // ==================================================================
    // "喵" 附加（用于伪装消息，不变）
    // ==================================================================

    private String appendMeow(String original) {
        if (original == null || original.isEmpty()) {
            return "喵";
        }
        String punctuations = "。，！？；：“”‘’、,.!?;:";
        boolean onlyPunctuation = true;
        for (char c : original.toCharArray()) {
            if (punctuations.indexOf(c) == -1) {
                onlyPunctuation = false;
                break;
            }
        }
        if (onlyPunctuation) {
            return "喵" + original;
        }
        int len = original.length();
        int index = len - 1;
        while (index >= 0 && punctuations.indexOf(original.charAt(index)) != -1) {
            index--;
        }
        if (index < 0) {
            return original + "喵";
        } else if (index == len - 1) {
            return original + "喵";
        } else {
            String before = original.substring(0, index + 1);
            String after = original.substring(index + 1);
            return before + "喵" + after;
        }
    }

    // ==================================================================
    // 检测方法
    // ==================================================================

    private boolean isSpamming(Player player) {
        Queue<Long> timestamps = messageTimestamps.computeIfAbsent(player, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - config.getSpamTimeWindowSeconds() * 1000;
        while (!timestamps.isEmpty() && timestamps.peek() < cutoff) {
            timestamps.poll();
        }
        return timestamps.size() >= config.getSpamMaxMessages();
    }

    private boolean containsUrl(String text) {
        if (text == null || text.isEmpty()) return false;

        // 域名+端口号检测（匹配 "domain:port" 或 "domain：port"）
        if (config.getDomainPortPattern().matcher(text).find()) {
            return true;
        }

        // 净化后匹配标准 URL
        String sanitized = config.getValidUrlChars().matcher(text).replaceAll("");
        if (sanitized.length() < 5) return false;
        return config.getUrlPattern().matcher(sanitized).find();
    }

    private boolean containsExcessiveDigits(String text) {
        if (text == null || text.isEmpty()) return false;
        int digitCount = 0;
        int maxDigits = config.getExcessiveDigitCount();
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') {
                digitCount++;
                if (digitCount >= maxDigits) {
                    return true;
                }
            }
        }
        return false;
    }

    private void recordMessage(Player player) {
        Queue<Long> timestamps = messageTimestamps.computeIfAbsent(player, k -> new ArrayDeque<>());
        timestamps.offer(System.currentTimeMillis());
    }
}
