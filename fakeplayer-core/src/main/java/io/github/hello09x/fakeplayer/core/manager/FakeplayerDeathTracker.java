package io.github.hello09x.fakeplayer.core.manager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Smart Auto-Respawn Death Tracker
 * Fork enhancement for tracking death causes and determining respawn eligibility
 */
@Singleton
public class FakeplayerDeathTracker {

    private final static Logger log = Logger.getLogger(FakeplayerDeathTracker.class.getName());

    public enum DeathReason {
        HOSTILE_MOB,     // Killed by hostile mob
        ENVIRONMENT,     // Environmental death (fall, lava, drowning)
        COMMAND,         // Killed via /fp kill command
        PLAYER,          // Killed by another player
        UNKNOWN          // Unknown cause
    }

    // Track death info for each player
    private final Map<UUID, DeathInfo> deathTracker = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final FakeplayerConfig config;

    @Inject
    public FakeplayerDeathTracker(FakeplayerConfig config) {
        this.config = config;
    }

    /**
     * Record that a player was killed via command
     */
    public void markCommandKill(@NotNull UUID playerUuid) {
        deathTracker.put(playerUuid, new DeathInfo(DeathReason.COMMAND, System.currentTimeMillis()));
        log.info("Marked " + playerUuid + " as killed by command - will NOT auto-respawn");
    }

    /**
     * Analyze death event and determine the cause
     */
    public DeathReason analyzeDeathReason(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        Component deathMessage = event.deathMessage();

        // Check if already marked as command kill
        DeathInfo existing = deathTracker.get(player.getUniqueId());
        if (existing != null && existing.reason == DeathReason.COMMAND &&
            (System.currentTimeMillis() - existing.timestamp < 1000)) {
            return DeathReason.COMMAND;
        }

        // Get death message as plain text
        String deathText = "";
        if (deathMessage != null) {
            deathText = PlainTextComponentSerializer.plainText().serialize(deathMessage);
        }

        // Check last damage cause
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage != null) {
            EntityDamageEvent.DamageCause cause = lastDamage.getCause();

            // Environmental causes
            if (isEnvironmentalCause(cause)) {
                return DeathReason.ENVIRONMENT;
            }

            // Entity damage - check if hostile mob
            if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {

                if (isHostileMobDeath(deathText)) {
                    return DeathReason.HOSTILE_MOB;
                }

                // Check if killed by player
                if (deathText.contains("was slain by") && !isHostileMobDeath(deathText)) {
                    return DeathReason.PLAYER;
                }
            }
        }

        // Fallback to text analysis
        if (isHostileMobDeath(deathText)) {
            return DeathReason.HOSTILE_MOB;
        }

        if (isEnvironmentalDeath(deathText)) {
            return DeathReason.ENVIRONMENT;
        }

        return DeathReason.UNKNOWN;
    }

    /**
     * Check if death was caused by hostile mob based on death message
     */
    private boolean isHostileMobDeath(@NotNull String deathMessage) {
        String hostileMobs = "Zombie|Skeleton|Spider|Cave Spider|Creeper|Enderman|" +
                            "Witch|Pillager|Vindicator|Evoker|Ravager|Phantom|" +
                            "Drowned|Husk|Stray|Wither Skeleton|Blaze|Ghast|Magma Cube|" +
                            "Silverfish|Endermite|Guardian|Elder Guardian|Shulker|Vex|" +
                            "Piglin|Piglin Brute|Hoglin|Zoglin|Warden";

        return deathMessage.matches(".*was slain by (" + hostileMobs + ").*") ||
               deathMessage.matches(".*was shot by (" + hostileMobs + ").*") ||
               deathMessage.matches(".*was fireballed by (" + hostileMobs + ").*") ||
               deathMessage.matches(".*was killed by (" + hostileMobs + ").*");
    }

    /**
     * Check if death was environmental
     */
    private boolean isEnvironmentalDeath(@NotNull String deathMessage) {
        return deathMessage.contains("drowned") ||
               deathMessage.contains("fell") ||
               deathMessage.contains("burned to death") ||
               deathMessage.contains("suffocated") ||
               deathMessage.contains("starved") ||
               deathMessage.contains("froze to death") ||
               deathMessage.contains("lava") ||
               deathMessage.contains("hit the ground") ||
               deathMessage.contains("fell out of the world") ||
               deathMessage.contains("withered away") ||
               deathMessage.contains("was pricked to death") ||
               deathMessage.contains("walked into fire") ||
               deathMessage.contains("was struck by lightning") ||
               deathMessage.contains("discovered the floor was lava");
    }

    /**
     * Check if damage cause is environmental
     */
    private boolean isEnvironmentalCause(@NotNull EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FALL ||
               cause == EntityDamageEvent.DamageCause.FIRE ||
               cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
               cause == EntityDamageEvent.DamageCause.LAVA ||
               cause == EntityDamageEvent.DamageCause.DROWNING ||
               cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
               cause == EntityDamageEvent.DamageCause.STARVATION ||
               cause == EntityDamageEvent.DamageCause.VOID ||
               cause == EntityDamageEvent.DamageCause.LIGHTNING ||
               cause == EntityDamageEvent.DamageCause.FREEZE ||
               cause == EntityDamageEvent.DamageCause.FALLING_BLOCK ||
               cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL ||
               cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
               cause == EntityDamageEvent.DamageCause.CRAMMING ||
               cause == EntityDamageEvent.DamageCause.DRYOUT;
    }

    /**
     * Record a death with its reason
     */
    public void recordDeath(@NotNull UUID playerUuid, @NotNull DeathReason reason) {
        deathTracker.put(playerUuid, new DeathInfo(reason, System.currentTimeMillis()));
    }

    /**
     * Check if a player should be auto-respawned based on death reason
     */
    public boolean shouldAutoRespawn(@NotNull UUID playerUuid, @NotNull DeathReason reason) {
        if (!config.isAutoRespawn()) {
            return false;
        }

        // Check cooldown
        Long lastRespawn = cooldowns.get(playerUuid);
        if (lastRespawn != null) {
            long cooldownMs = config.getRespawnCooldownMinutes() * 60 * 1000L;
            if (System.currentTimeMillis() - lastRespawn < cooldownMs) {
                log.info("Player " + playerUuid + " is on respawn cooldown");
                return false;
            }
        }

        // Check death reason
        boolean shouldRespawn = switch (reason) {
            case HOSTILE_MOB -> config.isRespawnOnHostileDeath();
            case ENVIRONMENT -> config.isRespawnOnEnvironmentDeath();
            case COMMAND -> config.isRespawnOnCommandKill();
            case PLAYER -> false; // Never auto-respawn if killed by player
            case UNKNOWN -> false; // Don't respawn for unknown causes
        };

        if (shouldRespawn) {
            cooldowns.put(playerUuid, System.currentTimeMillis());
        }

        return shouldRespawn;
    }

    /**
     * Clear death tracking data for a player
     */
    public void clearDeathData(@NotNull UUID playerUuid) {
        deathTracker.remove(playerUuid);
    }

    /**
     * Get the last death reason for a player
     */
    @Nullable
    public DeathReason getLastDeathReason(@NotNull UUID playerUuid) {
        DeathInfo info = deathTracker.get(playerUuid);
        return info != null ? info.reason : null;
    }

    private static class DeathInfo {
        final DeathReason reason;
        final long timestamp;

        DeathInfo(DeathReason reason, long timestamp) {
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }
}