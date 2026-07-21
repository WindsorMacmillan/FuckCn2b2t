package com.windsor.fuckCn2b2t;

import org.bukkit.plugin.java.JavaPlugin;

public final class FuckCn2b2t extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private PluginConfig pluginConfig;
    private TaskHandle reminderTask;
    private NewPlayerManager newPlayerManager;
    private ViolationManager violationManager;
    private ChatListener chatListener;
    private InteractionListener interactionListener;

    @Override
    public void onEnable() {
        // 必须在其他初始化之前创建调度器适配器
        scheduler = new SchedulerAdapter(this);

        // 加载配置
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);

        // 初始化管理器
        newPlayerManager = new NewPlayerManager(this, pluginConfig);
        violationManager = new ViolationManager(this, pluginConfig);
        chatListener = new ChatListener(newPlayerManager, violationManager, pluginConfig);
        interactionListener = new InteractionListener(violationManager, this, pluginConfig);

        // 注册事件和命令
        getServer().getPluginManager().registerEvents(new PlayerListener(newPlayerManager, pluginConfig), this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(interactionListener, this);

        getCommand("fkcn2b2t").setExecutor(new Fkcn2b2tCommand(this, violationManager, newPlayerManager));
        getCommand("shadowban").setExecutor(new ShadowBanCommand(violationManager));
        getCommand("newplayerstat").setExecutor(new NewPlayerStatCommand(newPlayerManager));

        // 启动定时提醒
        startReminderTask();

        getLogger().info("FuckCn2b2t 已启用，服务端类型: " + (scheduler.isFolia() ? "Folia" : "Paper"));
    }

    private void startReminderTask() {
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        if (!pluginConfig.isPeriodicReminderEnabled()) return;

        reminderTask = scheduler.runAtFixedRate(
                () -> newPlayerManager.checkAndRemindNewPlayers(),
                1L, pluginConfig.getReminderIntervalTicks()
        );
    }

    @Override
    public void onDisable() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
        if (violationManager != null) {
            violationManager.savePoints();
        }
        getLogger().info("FuckCn2b2t 插件已卸载");
    }

    // 重载配置（供命令调用）
    public void reloadPlugin() {
        reloadConfig();
        pluginConfig.load();              // 重新读取全部配置
        if (newPlayerManager != null) {
            newPlayerManager.reload();    // 重载统计项缓存
        }
        startReminderTask();              // 按新周期重新创建定时任务
        getLogger().info("配置已重载");
    }

    public boolean isDebugMode() {
        return pluginConfig.isDebugMode();
    }

    public SchedulerAdapter getScheduler() {
        return scheduler;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public NewPlayerManager getNewPlayerManager() {
        return newPlayerManager;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }
}
