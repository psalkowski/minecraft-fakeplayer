package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerProfileRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Smart Auto-Respawn Manager
 * Fork enhancement for handling intelligent fake player respawning
 */
@Singleton
public class FakeplayerRespawnManager {

    private final static Logger log = Main.getInstance().getLogger();

    private final FakeplayerManager manager;
    private final FakeplayerProfileRepository profileRepository;
    private final FakeplayerConfig config;
    private final FakeplayerDeathTracker deathTracker;

    @Inject
    public FakeplayerRespawnManager(
            FakeplayerManager manager,
            FakeplayerProfileRepository profileRepository,
            FakeplayerConfig config,
            FakeplayerDeathTracker deathTracker
    ) {
        this.manager = manager;
        this.profileRepository = profileRepository;
        this.config = config;
        this.deathTracker = deathTracker;
    }

    /**
     * Schedule a respawn for a fake player after the configured delay
     */
    public void scheduleRespawn(@NotNull String playerName, @NotNull UUID playerUuid,
                                @NotNull FakeplayerDeathTracker.DeathReason reason) {
        if (!config.isAutoRespawn()) {
            return;
        }

        int delayTicks = config.getRespawnDelaySeconds() * 20;

        log.info(String.format("Scheduling respawn for %s in %d seconds (reason: %s)",
                playerName, config.getRespawnDelaySeconds(), reason));

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            respawnPlayer(playerName, playerUuid);
        }, delayTicks);
    }

    /**
     * Immediately respawn a fake player at their last location
     */
    public void respawnPlayer(@NotNull String playerName, @NotNull UUID playerUuid) {
        try {
            // Get the last saved location
            Location lastLocation = profileRepository.getLastLocation(playerUuid);

            // Find the original creator (if online)
            Player creator = findOriginalCreator(playerName);
            CommandSender spawner = creator != null ? creator : Bukkit.getConsoleSender();

            log.info(String.format("Respawning %s at %s",
                    playerName, lastLocation != null ? formatLocation(lastLocation) : "spawn"));

            // Determine spawn location
            Location spawnLocation = lastLocation != null ? lastLocation : spawner.getServer().getWorlds().get(0).getSpawnLocation();

            // Spawn the player with no lifespan limit (0 = permanent)
            var result = manager.spawnAsync(spawner, playerName, spawnLocation, 0L);

            result.thenAccept(player -> {
                if (player != null) {
                    // No need to teleport again since we spawned at the right location

                    // Clear death tracking data
                    deathTracker.clearDeathData(playerUuid);
                    profileRepository.clearRespawnData(playerUuid);

                    // Notify creator if online
                    if (creator != null) {
                        creator.sendMessage(translatable(
                                "fakeplayer.respawn.success.auto",
                                text(playerName, GOLD)
                        ).color(GREEN));
                    }

                    log.info(String.format("Successfully respawned %s", playerName));
                }
            }).exceptionally(ex -> {
                log.warning(String.format("Failed to respawn %s: %s", playerName, ex.getMessage()));
                return null;
            });

        } catch (Exception e) {
            log.severe(String.format("Error respawning %s: %s", playerName, e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * Respawn all eligible offline players on server startup
     */
    public void respawnOfflinePlayers() {
        if (!config.isAutoRespawn()) {
            return;
        }

        log.info("Checking for fake players to auto-respawn...");

        var playersToRespawn = profileRepository.getAllPlayersWithRespawnData();

        if (playersToRespawn.isEmpty()) {
            log.info("No fake players need respawning");
            return;
        }

        log.info(String.format("Found %d fake players to respawn", playersToRespawn.size()));

        // Respawn each player with a small delay between each
        int delay = 0;
        for (var data : playersToRespawn) {
            final var playerData = data;

            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                // Check if player is already online
                if (Bukkit.getPlayer(playerData.name) != null) {
                    log.info(String.format("%s is already online, skipping", playerData.name));
                    return;
                }

                // Check if should still respawn
                if (profileRepository.shouldRespawn(playerData.uuid)) {
                    respawnPlayer(playerData.name, playerData.uuid);
                }
            }, delay);

            delay += 40; // 2 seconds between each respawn
        }
    }

    /**
     * Find the original creator of a fake player if they're online
     */
    @Nullable
    private Player findOriginalCreator(@NotNull String fakeName) {
        // Try to find by name pattern (if using default naming)
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (fakeName.startsWith(player.getName())) {
                return player;
            }
        }

        // TODO: Could enhance this by storing creator UUID in database
        return null;
    }

    /**
     * Format a location for logging
     */
    private String formatLocation(@NotNull Location loc) {
        return String.format("%s at (%.1f, %.1f, %.1f)",
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                loc.getX(), loc.getY(), loc.getZ());
    }
}