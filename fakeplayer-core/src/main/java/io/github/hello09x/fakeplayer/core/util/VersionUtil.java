package io.github.hello09x.fakeplayer.core.util;

import org.bukkit.Bukkit;

/**
 * Utility class for version detection and compatibility checks
 */
public class VersionUtil {

    private static final String MINECRAFT_VERSION = Bukkit.getMinecraftVersion();
    private static final boolean IS_1_21_6_OR_NEWER = checkIfVersion1_21_6OrNewer();

    static {
        System.out.println("[Fakeplayer] Detected Minecraft version: " + MINECRAFT_VERSION);
        System.out.println("[Fakeplayer] Is 1.21.6 or newer: " + IS_1_21_6_OR_NEWER);
    }

    /**
     * Check if the server is running Minecraft 1.21.6 or newer
     * This is important because PlayerLoginEvent is deprecated since 1.21.6
     *
     * @return true if version is 1.21.6 or newer, false otherwise
     */
    public static boolean isVersion1_21_6OrNewer() {
        return IS_1_21_6_OR_NEWER;
    }

    private static boolean checkIfVersion1_21_6OrNewer() {
        try {
            System.out.println("[Fakeplayer] Parsing version: " + MINECRAFT_VERSION);
            String[] parts = MINECRAFT_VERSION.split("\\.");
            System.out.println("[Fakeplayer] Version parts: " + java.util.Arrays.toString(parts));
            if (parts.length < 2) {
                return false;
            }

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            System.out.println("[Fakeplayer] Major: " + major + ", Minor: " + minor);

            // If major version > 1, definitely newer
            if (major > 1) {
                return true;
            }

            // If minor version > 21, definitely newer
            if (minor > 21) {
                return true;
            }

            // If minor version < 21, definitely older
            if (minor < 21) {
                return false;
            }

            // Minor version is 21, check patch version
            if (parts.length >= 3) {
                // Handle versions like "1.21.6" or "1.21.8-R0.1-SNAPSHOT"
                String patchStr = parts[2].split("-")[0];
                System.out.println("[Fakeplayer] Patch string: " + patchStr);
                int patch = Integer.parseInt(patchStr);
                System.out.println("[Fakeplayer] Patch number: " + patch);
                boolean result = patch >= 6;
                System.out.println("[Fakeplayer] Is patch >= 6? " + result);
                return result;
            }

            // Version is exactly 1.21 (no patch), which is older than 1.21.6
            return false;

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // If we can't parse the version, assume it's newer to use safer event
            return true;
        }
    }

    /**
     * Get the current Minecraft version string
     *
     * @return the Minecraft version (e.g., "1.21.8")
     */
    public static String getMinecraftVersion() {
        return MINECRAFT_VERSION;
    }
}