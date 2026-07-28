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

import java.text.Normalizer;
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
    private static final int Z0 = 0x5A5A;
    private static final int[][] Z1 = {
            {0x0655, 0x3F22, 0xC484},
            {0x0655, 0x3F2A, 0x2AE3},
            {0x09BF, 0xDC05},
            {0x09BF, 0x09AD},
            {0x09BF, 0xC484},
            {0x09BF, 0x2AE3},
            {0x0D49, 0xC484},
            {0x0D5C, 0x2AE3},
            {0x1477, 0xC484},
            {0x1477, 0x2AE3},
            {0xC249, 0xDC05},
            {0xC225, 0x09AD},
            {0xC484, 0x0B08},
            {0x2AE3, 0x0B65},
            {0xC484},
            {0x2AE3}
    };
    private static final Pattern Z2 = Pattern.compile(z3());
    private static final Pattern Z4 = Pattern.compile("\\.{2,}");
    private static final int Z5 = 0x1ABBC;
    private static final int Z6 = 0x1AB6A;
    private static final int Z7 = 0x5A3B;
    private static final int Z8 = 0x5A74;
    private static final int[][] Z9 = {
            {0x1AB2A, 0x5A3B},
            {0x1AB2B, 0x5A38},
            {0x1AB24, 0x5A35},
            {0x1AB25, 0x5A2A},
            {0x1ABD4, 0x5A3B, 0x5A38},
            {0x1ABCB, 0x5A39, 0x5A36},
            {0x1ABC8, 0x5A39, 0x5A35, 0x5A35, 0x5A36},
            {0x1ABC9, 0x5A3C, 0x5A28, 0x5A3F, 0x5A3F},
            {0x1ABCE, 0x5A33, 0x5A3E},
            {0x1ABCF, 0x5A34, 0x5A3F, 0x5A2D},
            {0x1ABCC, 0x5A34, 0x5A3D},
            {0x1ABCD, 0x5A35, 0x5A31},
            {0x1ABC2, 0x5A29, 0x5A35, 0x5A29},
            {0x1ABC3, 0x5A2F, 0x5A2A},
            {0x1ABC0, 0x5A2C, 0x5A29},
            {0x1AF45, 0x5A6B, 0x5A6A},
            {0x7DCF, 0x5A71},
            {0x7DCC, 0x5A77},
            {0x7DCD, 0x5A75},
            {0x7D4C, 0x5A22},
            {0x7D16, 0x5A22},
            {0x7D14, 0x5A22},
            {0x710F, 0x5A35},
            {0x1AF6E, 0x5A74},
            {0x1AF6F, 0x5A74},
            {0x7CF0, 0x5A74},
            {0x7CF1, 0x5A74}
    };

    public ChatListener(NewPlayerManager newPlayerManager, ViolationManager violationManager, PluginConfig config) {
        this.newPlayerManager = newPlayerManager;
        this.violationManager = violationManager;
        this.config = config;
    }

    private static String z3() {
        StringBuilder z5 = new StringBuilder();
        for (int z6 = 0; z6 < Z1.length; z6++) {
            if (z6 > 0) {
                z5.append('|');
            }
            for (int z7 : Z1[z6]) {
                z5.appendCodePoint(z7 ^ Z0);
            }
        }
        return z5.toString();
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
        if (config.isKeywordFilterEnabled() && containsKeyword(plainMessage)) {
            return "包含违规关键字";
        }
        if (config.isRareCharCheckEnabled() && containsExcessiveRareChars(plainMessage)) {
            return "包含过多生僻字";
        }
        if (config.isLongMessageEnabled() && plainMessage.length() > config.getMaxMessageLength()) {
            return "发送超长消息";
        }
        if (config.isSpamDetectionEnabled() && isSpamming(player)) {
            return "频繁发送消息";
        }
        if (config.isLinkDetectionEnabled() && z8(plainMessage)) {
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
        if (config.isKeywordFilterEnabled() && containsKeyword(plainMessage)) {
            return "包含违规关键字";
        }
        if (config.isRareCharCheckEnabled() && containsExcessiveRareChars(plainMessage)) {
            return "包含过多生僻字";
        }
        if (config.isLongMessageEnabled() && plainMessage.length() > config.getMaxMessageLength()) {
            return "发送超长消息";
        }
        if (config.isLinkDetectionEnabled() && z8(plainMessage)) {
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
        String modifiedContent = config.isSweetMeowCompatEnabled() ? appendMeow(messageContent) : messageContent;
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

    private boolean z8(String z9) {
        if (z9 == null || z9.isEmpty()) return false;

        String za = zb(z9);

        if (config.getDomainPortPattern().matcher(za).find()) {
            return true;
        }

        String zc = z14(config.getValidUrlChars().matcher(za).replaceAll(""));
        if (zc.length() < 5) return false;
        return config.getUrlPattern().matcher(zc).find();
    }

    private String z14(String z15) {
        int z16 = Math.max(z15.lastIndexOf(':'), z15.lastIndexOf('：'));
        if (z16 < 0) {
            return z15;
        }

        StringBuilder z17 = new StringBuilder(z15.length());
        for (int z18 = 0; z18 < z15.length(); z18++) {
            char z19 = z15.charAt(z18);
            if (z18 < z16 && (z19 == ':' || z19 == '：')) {
                continue;
            }
            z17.append(z19);
        }
        return z17.toString();
    }

    private String zb(String zd) {
        String ze = Normalizer.normalize(zd, Normalizer.Form.NFKC);
        ze = z20(ze);
        ze = Z2.matcher(ze).replaceAll(".");

        StringBuilder zf = new StringBuilder(ze.length());
        for (int z10 = 0; z10 < ze.length(); ) {
            int z11 = ze.codePointAt(z10);
            zf.appendCodePoint(z12(z11) ? '.' : z11);
            z10 += Character.charCount(z11);
        }
        return Z4.matcher(zf).replaceAll(".");
    }

    private String z20(String z21) {
        StringBuilder z22 = new StringBuilder(z21.length());
        for (int z23 = 0; z23 < z21.length(); ) {
            int z24 = z21.codePointAt(z23);
            int z25 = z23 + Character.charCount(z24);

            if ((z24 == (0x5A79 ^ Z0) || z24 == (0x5A70 ^ Z0)
                    || (z24 >= (0x5A6A ^ Z0) && z24 <= (0x5A63 ^ Z0))) && z25 < z21.length()) {
                int z26 = z21.codePointAt(z25);
                int z27 = z25 + Character.charCount(z26);
                if (z26 == 0xFE0F && z27 < z21.length()) {
                    z26 = z21.codePointAt(z27);
                    z27 += Character.charCount(z26);
                }
                if (z26 == 0x20E3) {
                    z22.appendCodePoint(z24);
                    z23 = z27;
                    continue;
                }
            }

            String z28 = z29(z24);
            if (z28 != null) {
                z22.append(z28);
            } else if (z24 != 0xFE0F && z24 != 0xFE0E && z24 != 0x20E3) {
                z22.appendCodePoint(z24);
            }
            z23 = z25;
        }
        return z22.toString();
    }

    private String z29(int z2a) {
        int z2b = Z5 ^ Z0;
        if (z2a >= z2b && z2a <= z2b + 25) {
            return z2c((Z7 ^ Z0) + z2a - z2b);
        }
        int z2d = Z6 ^ Z0;
        if (z2a >= z2d && z2a <= z2d + 25) {
            return z2c((Z7 ^ Z0) + z2a - z2d);
        }
        if (z2a >= 0x1F7E0 && z2a <= 0x1F7EB) {
            return z2e(Z8);
        }

        for (int[] z2f : Z9) {
            if ((z2f[0] ^ Z0) == z2a) {
                StringBuilder z30 = new StringBuilder(z2f.length - 1);
                for (int z31 = 1; z31 < z2f.length; z31++) {
                    z30.appendCodePoint(z2f[z31] ^ Z0);
                }
                return z30.toString();
            }
        }
        return null;
    }

    private String z2c(int z32) {
        return new String(Character.toChars(z32));
    }

    private String z2e(int z33) {
        return z2c(z33 ^ Z0);
    }

    private boolean z12(int z13) {
        return switch (z13) {
            case '.',
                    // CJK/common full-stop variants and separators.
                    0x3002, 0xFF61, 0xFF0E, 0xFE52, 0xFE12, 0x30FB, 0xFF65,
                    0x4E36, 0x3001, 0xFF64, 0xFE51,
                    // Unicode confusables that map to full stop or repeated full stops.
                    0x2024, 0x2025, 0x2026, 0x0701, 0x0702, 0xA60E, 0x10A50,
                    0xA4F8, 0xA4FA, 0xA4FB, 0x1D16D,
                    // Other full-stop punctuation used in non-Latin scripts.
                    0x06D4, 0x1362, 0x166E, 0x1803, 0x1809,
                    // Middle-dot, bullet, dot-operator and small round punctuation often used as separators.
                    0x00B7, 0x0387, 0x16EB, 0x2E31, 0x10101, 0x2022, 0x2027,
                    0x2219, 0x22C5, 0x25CF, 0x25E6, 0x2981, 0x2E30, 0xFE45, 0xFE46,
                    // Ring-like dot substitutions that are visually close in chat fonts.
                    0x00B0, 0x02DA, 0x2218, 0x25CB, 0x25C9, 0x25CC, 0x25D8,
                    0x25D9, 0x26AC -> true;
            default -> false;
        };
    }

    private boolean containsKeyword(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        for (String keyword : config.getKeywordFilterKeywords()) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExcessiveRareChars(String text) {
        if (text == null || text.isEmpty()) return false;
        int count = 0;
        int max = config.getMaxRareCharCount();
        for (char c : text.toCharArray()) {
            if (CommonChineseChars.isRare(c)) {
                count++;
                if (count >= max) return true;
            }
        }
        return false;
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
