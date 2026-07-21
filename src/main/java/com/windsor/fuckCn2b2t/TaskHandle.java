package com.windsor.fuckCn2b2t;

/**
 * 统一的任务句柄，屏蔽 Folia 的 {@code ScheduledTask} 与 Paper 的 {@code BukkitTask} 差异。
 * 内部仅持有取消动作的引用，不依赖任何服务端特有的任务类型。
 */
public class TaskHandle {

    private final Runnable cancelAction;
    private boolean cancelled;

    public TaskHandle(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    /**
     * 取消定时任务。可多次调用，只有第一次生效。
     */
    public void cancel() {
        if (!cancelled) {
            cancelled = true;
            cancelAction.run();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
