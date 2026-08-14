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
    private static final int Il1 = 0x5A5A;
    private static final int[][] I1l = {
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
    private static final Pattern lI1 = Pattern.compile(lI1l());
    private static final Pattern ll1 = Pattern.compile("\\.{2,}");
    private static final int IIl = 0x1ABBC;
    private static final int IlI = 0x1AB6A;
    private static final int l1I = 0x5A3B;
    private static final int I1I = 0x5A74;
    private static final int[][] llI = {
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

    private static String lI1l() {
        StringBuilder llIl = new StringBuilder();
        for (int l1lI = 0; l1lI < I1l.length; l1lI++) {
            if (l1lI > 0) {
                llIl.append('|');
            }
            for (int I1ll : I1l[l1lI]) {
                llIl.appendCodePoint(I1ll ^ Il1);
            }
        }
        return llIl.toString();
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
        boolean oldPlayer = !newPlayerManager.isNewPlayer(player);

        // 老玩家手动隐形禁言期间仅拦截发言，不累计违规次数
        if (!oldPlayer && config.isChatCheckEnabled()) {
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
        if (config.isLinkDetectionEnabled() && I1lI(plainMessage)) {
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
        if (config.isLinkDetectionEnabled() && I1lI(plainMessage)) {
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
        String format = config.getSilentChatFormat()
                .replace("{player}", player.getName())
                .replace("{message}", messageContent);
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
    // 检测方法
    // ==================================================================

    private boolean isSpamming(Player player) {
        Queue<Long> timestamps = messageTimestamps.computeIfAbsent(player, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - config.getSpamTimeWindowSeconds() * 1000L;
        while (!timestamps.isEmpty() && timestamps.peek() < cutoff) {
            timestamps.poll();
        }
        return timestamps.size() >= config.getSpamMaxMessages();
    }

    private boolean I1lI(String l11I) {
        if (l11I == null || l11I.isEmpty()) return false;

        String IlII = IIl1(l11I);

        if (config.getDomainPortPattern().matcher(IlII).find()) {
            return true;
        }

        String lIll = ll1I(config.getValidUrlChars().matcher(IlII).replaceAll(""));
        if (lIll.length() < 5) return false;
        return config.getUrlPattern().matcher(lIll).find();
    }

    private String ll1I(String I11l) {
        int ll11 = Math.max(I11l.lastIndexOf(':'), I11l.lastIndexOf('：'));
        if (ll11 < 0) {
            return I11l;
        }

        StringBuilder I1I1 = new StringBuilder(I11l.length());
        for (int l1I1 = 0; l1I1 < I11l.length(); l1I1++) {
            char Il1I = I11l.charAt(l1I1);
            if (l1I1 < ll11 && (Il1I == ':' || Il1I == '：')) {
                continue;
            }
            I1I1.append(Il1I);
        }
        return I1I1.toString();
    }

    private String IIl1(String I111) {
        String lIII = Normalizer.normalize(I111, Normalizer.Form.NFKC);
        lIII = lIl1(lIII);
        lIII = lI1.matcher(lIII).replaceAll(".");

        StringBuilder llII = new StringBuilder(lIII.length());
        for (int I1l1 = 0; I1l1 < lIII.length(); ) {
            int lI11 = lIII.codePointAt(I1l1);
            llII.appendCodePoint(l1Il(lI11) ? '.' : lI11);
            I1l1 += Character.charCount(lI11);
        }
        return ll1.matcher(llII).replaceAll(".");
    }

    private String lIl1(String lllI) {
        StringBuilder IIll = new StringBuilder(lllI.length());
        for (int l11l = 0; l11l < lllI.length(); ) {
            int I11I = lllI.codePointAt(l11l);
            int IlIl = l11l + Character.charCount(I11I);

            if ((I11I == (0x5A79 ^ Il1) || I11I == (0x5A70 ^ Il1)
                    || (I11I >= (0x5A6A ^ Il1) && I11I <= (0x5A63 ^ Il1))) && IlIl < lllI.length()) {
                int lIlI = lllI.codePointAt(IlIl);
                int II1l = IlIl + Character.charCount(lIlI);
                if (lIlI == 0xFE0F && II1l < lllI.length()) {
                    lIlI = lllI.codePointAt(II1l);
                    II1l += Character.charCount(lIlI);
                }
                if (lIlI == 0x20E3) {
                    IIll.appendCodePoint(I11I);
                    l11l = II1l;
                    continue;
                }
            }

            String ll1l = I1Il(I11I);
            if (ll1l != null) {
                IIll.append(ll1l);
            } else if (I11I != 0xFE0F && I11I != 0xFE0E && I11I != 0x20E3) {
                IIll.appendCodePoint(I11I);
            }
            l11l = IlIl;
        }
        return IIll.toString();
    }

    private String I1Il(int I1II) {
        int l1ll = IIl ^ Il1;
        if (I1II >= l1ll && I1II <= l1ll + 25) {
            return llI1((l1I ^ Il1) + I1II - l1ll);
        }
        int Il11 = IlI ^ Il1;
        if (I1II >= Il11 && I1II <= Il11 + 25) {
            return llI1((l1I ^ Il1) + I1II - Il11);
        }
        if (I1II >= 0x1F7E0 && I1II <= 0x1F7EB) {
            return IlI1(I1I);
        }

        for (int[] IIlI : llI) {
            if ((IIlI[0] ^ Il1) == I1II) {
                StringBuilder l1II = new StringBuilder(IIlI.length - 1);
                for (int I1I1 = 1; I1I1 < IIlI.length; I1I1++) {
                    l1II.appendCodePoint(IIlI[I1I1] ^ Il1);
                }
                return l1II.toString();
            }
        }
        return null;
    }

    private String llI1(int lIIl) {
        return new String(Character.toChars(lIIl));
    }

    private String IlI1(int lI1I) {
        return llI1(lI1I ^ Il1);
    }

    private boolean l1Il(int I1iI) {
        return switch (I1iI) {
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
