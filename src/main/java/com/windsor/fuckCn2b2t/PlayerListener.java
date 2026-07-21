package com.windsor.fuckCn2b2t;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final NewPlayerManager newPlayerManager;
    private final PluginConfig config;

    public PlayerListener(NewPlayerManager newPlayerManager, PluginConfig config) {
        this.newPlayerManager = newPlayerManager;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isLoginMessageEnabled()) return;
        newPlayerManager.checkAndRemindPlayer(event.getPlayer());
    }
}
