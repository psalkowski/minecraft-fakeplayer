package io.github.hello09x.fakeplayer.core.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerRespawnManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.logging.Logger;

/**
 * Server Startup Listener for Auto-Respawn
 * Fork enhancement to respawn fake players on server startup
 */
@Singleton
public class ServerStartupListener implements Listener {

    private final static Logger log = Main.getInstance().getLogger();

    private final FakeplayerRespawnManager respawnManager;
    private final FakeplayerConfig config;

    @Inject
    public ServerStartupListener(FakeplayerRespawnManager respawnManager, FakeplayerConfig config) {
        this.respawnManager = respawnManager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (!config.isAutoRespawn()) {
            return;
        }

        // Wait a bit for server to fully stabilize
        int delayTicks = 200; // 10 seconds

        log.info("Scheduling fake player auto-respawn check in " + (delayTicks/20) + " seconds...");

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (Main.getInstance().isEnabled()) {
                log.info("Starting fake player auto-respawn check...");
                respawnManager.respawnOfflinePlayers();
            }
        }, delayTicks);
    }
}