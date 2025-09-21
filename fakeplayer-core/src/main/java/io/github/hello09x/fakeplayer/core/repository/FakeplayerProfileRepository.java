package io.github.hello09x.fakeplayer.core.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.devtools.database.jdbc.JdbcTemplate;
import io.github.hello09x.devtools.database.jdbc.rowmapper.BooleanRowMapper;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerDeathTracker;
import io.github.hello09x.fakeplayer.core.repository.model.FakePlayerProfile;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author tanyaofei
 * @since 2024/8/3
 **/
@Singleton
public class FakeplayerProfileRepository {

    private final JdbcTemplate jdbc;

    @Inject
    public FakeplayerProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.initTables();
    }

    public void insert(@NotNull String name, @NotNull UUID uuid) {
        var sql = "INSERT INTO fake_player_profile (name, uuid) VALUES (?, ?)";
        jdbc.update(sql, name, uuid.toString());
    }

    public boolean existsByUUID(@NotNull UUID uuid) {
        var sql = "SELECT EXISTS(SELECT 1 FROM fake_player_profile WHERE uuid = ?)";
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new BooleanRowMapper(), uuid.toString()));
    }

    public @Nullable UUID selectUUIDByName(@NotNull String name) {
        return Optional.ofNullable(this.selectByName(name)).map(FakePlayerProfile::uuid).map(UUID::fromString).orElse(null);
    }

    public @Nullable FakePlayerProfile selectByName(@NotNull String name) {
        var sql = "SELECT * FROM fake_player_profile WHERE name = ?";
        return jdbc.queryForObject(sql, FakePlayerProfile.FakePlayerProfileRowMapper.instance, name);
    }

    /**
     * Save the last location of a fake player for respawning
     */
    public void saveLastLocation(@NotNull UUID playerUuid, @NotNull Location location) {
        var sql = """
            INSERT OR REPLACE INTO fake_player_respawn
            (player_uuid, last_world, last_x, last_y, last_z, last_yaw, last_pitch, death_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbc.update(sql,
            playerUuid.toString(),
            location.getWorld() != null ? location.getWorld().getName() : "world",
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            System.currentTimeMillis()
        );
    }

    /**
     * Get the last saved location for a fake player
     */
    @Nullable
    public Location getLastLocation(@NotNull UUID playerUuid) {
        var sql = "SELECT * FROM fake_player_respawn WHERE player_uuid = ?";

        return jdbc.query(sql, (ResultSet rs) -> {
            if (rs.next()) {
                String worldName = rs.getString("last_world");
                if (worldName == null) return null;

                var world = Bukkit.getWorld(worldName);
                if (world == null) return null;

                return new Location(
                    world,
                    rs.getDouble("last_x"),
                    rs.getDouble("last_y"),
                    rs.getDouble("last_z"),
                    rs.getFloat("last_yaw"),
                    rs.getFloat("last_pitch")
                );
            }
            return null;
        }, playerUuid.toString());
    }

    /**
     * Set the death reason for a fake player
     */
    public void setDeathReason(@NotNull UUID playerUuid, @NotNull FakeplayerDeathTracker.DeathReason reason) {
        var sql = """
            UPDATE fake_player_respawn
            SET death_reason = ?, death_time = ?
            WHERE player_uuid = ?
            """;

        jdbc.update(sql, reason.name(), System.currentTimeMillis(), playerUuid.toString());
    }

    /**
     * Set whether a player should be respawned
     */
    public void setShouldRespawn(@NotNull UUID playerUuid, boolean shouldRespawn) {
        var sql = """
            UPDATE fake_player_respawn
            SET should_respawn = ?
            WHERE player_uuid = ?
            """;

        jdbc.update(sql, shouldRespawn ? 1 : 0, playerUuid.toString());
    }

    /**
     * Check if a player should be respawned
     */
    public boolean shouldRespawn(@NotNull UUID playerUuid) {
        var sql = "SELECT should_respawn FROM fake_player_respawn WHERE player_uuid = ?";

        Integer result = jdbc.queryForObject(sql, (rs, rowNum) -> rs.getInt("should_respawn"), playerUuid.toString());
        return result != null && result == 1;
    }

    /**
     * Clear respawn data for a player
     */
    public void clearRespawnData(@NotNull UUID playerUuid) {
        var sql = "DELETE FROM fake_player_respawn WHERE player_uuid = ?";
        jdbc.update(sql, playerUuid.toString());
    }

    /**
     * Get all players with respawn data
     */
    public List<FakePlayerRespawnData> getAllPlayersWithRespawnData() {
        var sql = """
            SELECT fp.name, fp.uuid, fr.*
            FROM fake_player_profile fp
            INNER JOIN fake_player_respawn fr ON fp.uuid = fr.player_uuid
            WHERE fr.should_respawn = 1
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            FakePlayerRespawnData data = new FakePlayerRespawnData();
            data.name = rs.getString("name");
            data.uuid = UUID.fromString(rs.getString("uuid"));
            data.worldName = rs.getString("last_world");
            data.x = rs.getDouble("last_x");
            data.y = rs.getDouble("last_y");
            data.z = rs.getDouble("last_z");
            data.yaw = rs.getFloat("last_yaw");
            data.pitch = rs.getFloat("last_pitch");
            return data;
        });
    }

    /**
     * Simple data class for respawn information
     */
    public static class FakePlayerRespawnData {
        public String name;
        public UUID uuid;
        public String worldName;
        public double x, y, z;
        public float yaw, pitch;
    }

    private void initTables() {
        jdbc.execute("""
                             create table if not exists fake_player_profile
                             (
                                 id   integer  not null primary key autoincrement,
                                 name text(32) not null,
                                 uuid text(36) not null
                             );
                             """);

        jdbc.execute("""
                             create unique index if not exists fake_player_profile_name
                                        on fake_player_profile (name);
                             """);

        jdbc.execute("""
                             create unique index if not exists fake_player_profile_uuid
                                        on fake_player_profile (uuid);
                             """);

        // Smart Auto-Respawn table for tracking death/respawn data
        jdbc.execute("""
                             create table if not exists fake_player_respawn
                             (
                                 player_uuid text(36) primary key,
                                 last_world text,
                                 last_x real,
                                 last_y real,
                                 last_z real,
                                 last_yaw real,
                                 last_pitch real,
                                 death_reason text,
                                 death_time integer,
                                 should_respawn integer default 1
                             );
                             """);
    }


}
