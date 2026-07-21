package com.windsor.fuckCn2b2t;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.*;

public class InteractionListener implements Listener {

    private final ViolationManager violationManager;
    private final FuckCn2b2t plugin;
    private final PluginConfig config;

    // 记录铁砧界面中原始物品，用于还原 (Inventory -> 原始物品)
    private final Map<AnvilInventory, ItemStack> originalItems = new HashMap<>();

    public InteractionListener(ViolationManager violationManager, FuckCn2b2t plugin, PluginConfig config) {
        this.violationManager = violationManager;
        this.plugin = plugin;
        this.config = config;
    }

    // --- 私聊命令拦截 ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!config.isInterceptPrivateMessage()) return;
        Player player = event.getPlayer();
        if (!violationManager.isMuted(player)) return;

        String command = event.getMessage().toLowerCase();
        // 取命令名（去掉开头的 /）
        String cmdName = command.contains(" ") ? command.substring(1, command.indexOf(' ')) : command.substring(1);
        if (config.getPrivateMessageCommands().contains(cmdName)) {
            event.setCancelled(true);
            Bukkit.getLogger().info(String.format("[聊天管制-私聊拦截] 玩家 %s 在隐形禁言期间尝试私聊: %s",
                    player.getName(), command));
            notifyOps(String.format("玩家 %s 在隐形禁言期间尝试私聊: %s", player.getName(), command));
        }
    }

    // --- 告示牌拦截 ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (!config.isInterceptSign()) return;
        Player player = event.getPlayer();
        if (!violationManager.isMuted(player)) return;

        List<Component> components = event.lines();
        StringBuilder content = new StringBuilder();
        for (Component comp : components) {
            String text = PlainTextComponentSerializer.plainText().serialize(comp);
            if (text != null && !text.isEmpty()) {
                content.append(text).append(" ");
            }
        }
        String signText = content.toString().trim();

        event.setCancelled(true);
        Bukkit.getLogger().info(String.format("[聊天管制-告示牌拦截] 玩家 %s 在隐形禁言期间尝试编辑告示牌，内容: %s",
                player.getName(), signText));
        notifyOps(String.format("玩家 %s 在隐形禁言期间尝试编辑告示牌，内容: %s", player.getName(), signText));
    }

    // --- 铁砧重命名：准备阶段（记录原始物品，允许预览） ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        if (!config.isInterceptAnvil()) return;
        HumanEntity human = event.getView().getPlayer();
        if (!(human instanceof Player player)) return;
        if (!violationManager.isMuted(player)) return;
        if (!(event.getView() instanceof AnvilView)) return;

        AnvilInventory inventory = event.getInventory();
        ItemStack inputItem = inventory.getItem(0);
        if (inputItem == null || inputItem.getType() == Material.AIR) {
            originalItems.remove(inventory);
            return;
        }

        String newName = event.getView().getRenameText();
        if (newName == null || newName.isEmpty()) {
            originalItems.remove(inventory);
            return;
        }

        if (!originalItems.containsKey(inventory)) {
            originalItems.put(inventory, inputItem.clone());
            Bukkit.getLogger().info(String.format("[聊天管制-铁砧尝试] 玩家 %s 在隐形禁言期间尝试将物品重命名为: %s",
                    player.getName(), newName));
            notifyOps(String.format("玩家 %s 在隐形禁言期间尝试将物品重命名为: %s", player.getName(), newName));
        }
    }

    // --- 铁砧重命名：点击输出槽时还原物品名称 ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.isInterceptAnvil()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!violationManager.isMuted(player)) return;
        if (!(event.getClickedInventory() instanceof AnvilInventory inventory)) return;
        if (event.getSlot() != 2) return;

        ItemStack original = originalItems.remove(inventory);
        if (original == null) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ItemStack modified = current.clone();
        ItemMeta meta = modified.getItemMeta();
        if (meta != null) {
            String originalDisplayName = null;
            ItemMeta originalMeta = original.getItemMeta();
            if (originalMeta != null && originalMeta.hasDisplayName()) {
                originalDisplayName = originalMeta.getDisplayName();
            }
            if (originalDisplayName != null) {
                meta.setDisplayName(originalDisplayName);
            } else {
                meta.setDisplayName(null);
            }
            modified.setItemMeta(meta);
        }

        event.setCurrentItem(modified);

        Bukkit.getLogger().info(String.format("[聊天管制-铁砧还原] 玩家 %s 取走了重命名物品，已还原为原始名称。",
                player.getName()));
    }

    // --- 清理记录：关闭铁砧界面时移除记录 ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!config.isInterceptAnvil()) return;
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory() instanceof AnvilInventory inventory)) return;
        originalItems.remove(inventory);
    }

    // ================== 书与笔拦截（延迟清空） ==================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!config.isInterceptBook()) return;
        Player player = event.getPlayer();
        if (!violationManager.isMuted(player)) return;

        BookMeta newBookMeta = event.getNewBookMeta();
        List<Component> pages = newBookMeta.pages();
        StringBuilder content = new StringBuilder();
        if (!pages.isEmpty()) {
            for (int i = 0; i < pages.size(); i++) {
                String text = PlainTextComponentSerializer.plainText().serialize(pages.get(i));
                content.append("第").append(i + 1).append("页: ").append(text).append("\n");
            }
        } else {
            content.append("(空书)");
        }

        Bukkit.getLogger().info(String.format("[聊天管制-书与笔拦截] 玩家 %s 在隐形禁言期间尝试编辑书与笔，内容:\n%s",
                player.getName(), content));
        notifyOps(String.format("玩家 %s 在隐形禁言期间尝试编辑书与笔，内容:\n%s", player.getName(), content));

        plugin.getScheduler().runGlobal(() -> {
            ItemStack book = player.getInventory().getItemInMainHand();
            if (book == null || (book.getType() != Material.WRITABLE_BOOK && book.getType() != Material.WRITTEN_BOOK)) {
                book = player.getInventory().getItemInOffHand();
                if (book == null || (book.getType() != Material.WRITABLE_BOOK && book.getType() != Material.WRITTEN_BOOK)) {
                    return;
                }
            }
            ItemMeta meta = book.getItemMeta();
            if (!(meta instanceof BookMeta bookMeta)) return;
            bookMeta.pages(Collections.emptyList());
            book.setItemMeta(bookMeta);
            Bukkit.getLogger().info(String.format("[聊天管制-书与笔清空] 玩家 %s 的书已被清空。", player.getName()));
        });
    }

    // --- 辅助方法：通知在线OP ---
    private void notifyOps(String message) {
        if (!config.isNotifyOp()) return;
        Component notification = Component.text("§f[§4聊天管制§f] " + message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) {
                online.sendMessage(notification);
            }
        }
    }
}
