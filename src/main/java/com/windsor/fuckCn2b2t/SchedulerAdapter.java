package com.windsor.fuckCn2b2t;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 调度器适配器，运行时自动检测 Folia 并切换到对应的调度 API。
 *
 * <p>Folia 使用 {@code Bukkit.getGlobalRegionScheduler()} 等区域化调度器；</p>
 * <p>Paper（非 Folia）使用传统的 {@code Bukkit.getScheduler()}。</p>
 *
 * <p>在编译期仅依赖 Paper API（已包含 Folia 类型），运行时通过反射检测
 * {@code io.papermc.paper.threadedregions.RegionizedServer} 类是否存在来判断是否为 Folia。</p>
 */
public class SchedulerAdapter {

    private final JavaPlugin plugin;
    private final boolean folia;

    public SchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    // ---------- 检测 ----------

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 当前服务端是否为 Folia。
     */
    public boolean isFolia() {
        return folia;
    }

    // ---------- 全局调度 ----------

    /**
     * 在下一个 tick 执行一次任务。
     * Folia：GlobalRegionScheduler#run | Paper：BukkitScheduler#runTask
     */
    public void runGlobal(Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 延迟指定 tick 后执行一次任务。
     */
    public TaskHandle runDelayed(Runnable task, long delayTicks) {
        if (folia) {
            var scheduledTask = Bukkit.getGlobalRegionScheduler()
                    .runDelayed(plugin, t -> task.run(), delayTicks);
            return new TaskHandle(scheduledTask::cancel);
        } else {
            var bukkitTask = Bukkit.getScheduler()
                    .runTaskLater(plugin, task, delayTicks);
            return new TaskHandle(bukkitTask::cancel);
        }
    }

    /**
     * 以固定周期重复执行任务。
     *
     * @param task              要执行的任务
     * @param initialDelayTicks 首次执行前的等待 tick
     * @param periodTicks       执行间隔 tick
     * @return 可用于取消任务的句柄
     */
    public TaskHandle runAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        if (folia) {
            var scheduledTask = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, t -> task.run(), initialDelayTicks, periodTicks);
            return new TaskHandle(scheduledTask::cancel);
        } else {
            var bukkitTask = Bukkit.getScheduler()
                    .runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
            return new TaskHandle(bukkitTask::cancel);
        }
    }
}
