package io.github.hello09x.fakeplayer.core.util;

import io.github.hello09x.fakeplayer.core.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TranslationHelper {

    private static final ConcurrentHashMap<String, Properties> TRANSLATION_CACHE = new ConcurrentHashMap<>();
    private static final Properties DEFAULT_TRANSLATIONS = new Properties();
    private static boolean initialized = false;

    static {
        loadDefaultTranslations();
    }

    private static void loadDefaultTranslations() {
        try (InputStream stream = Main.getInstance().getResource("message/message.properties")) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    DEFAULT_TRANSLATIONS.load(reader);
                    initialized = true;
                }
            }
        } catch (IOException e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Failed to load default translations", e);
        }
    }

    /**
     * Gets a plain text translation for use during plugin initialization.
     * This method avoids using Adventure components to prevent StackOverflowError.
     *
     * @param key The translation key
     * @return The translated string or the key if translation not found
     */
    public static String getPlainText(String key) {
        if (!initialized) {
            loadDefaultTranslations();
        }
        return DEFAULT_TRANSLATIONS.getProperty(key, key);
    }

    /**
     * Gets a plain text translation with fallback.
     *
     * @param key The translation key
     * @param fallback The fallback text if translation not found
     * @return The translated string or fallback
     */
    public static String getPlainText(String key, String fallback) {
        if (!initialized) {
            loadDefaultTranslations();
        }
        return DEFAULT_TRANSLATIONS.getProperty(key, fallback);
    }

    /**
     * Creates a translatable component for runtime use with player context.
     * This should only be used when sending messages to players, not during initialization.
     *
     * @param key The translation key
     * @return A translatable component
     */
    public static Component translatable(String key) {
        return Component.translatable(key);
    }

    /**
     * Creates a translatable component with arguments for runtime use.
     *
     * @param key The translation key
     * @param args The arguments for the translation
     * @return A translatable component with arguments
     */
    public static TranslatableComponent translatable(String key, Component... args) {
        return Component.translatable(key, args);
    }

    /**
     * Gets a translation for a specific locale.
     *
     * @param key The translation key
     * @param locale The locale to use
     * @return The translated string or the key if not found
     */
    public static String getTranslation(String key, Locale locale) {
        String langTag = locale.toLanguageTag().replace("-", "_").toLowerCase();
        Properties translations = TRANSLATION_CACHE.computeIfAbsent(langTag, lang -> {
            Properties props = new Properties();
            String resourcePath = "message/message_" + lang + ".properties";
            try (InputStream stream = Main.getInstance().getResource(resourcePath)) {
                if (stream != null) {
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                }
            } catch (IOException e) {
                Main.getInstance().getLogger().log(Level.FINE, "Could not load translations for locale: " + lang);
            }
            return props;
        });

        String translation = translations.getProperty(key);
        if (translation == null) {
            translation = DEFAULT_TRANSLATIONS.getProperty(key, key);
        }
        return translation;
    }

    /**
     * Gets a translation for a player's locale.
     *
     * @param key The translation key
     * @param player The player whose locale to use
     * @return The translated string
     */
    public static String getTranslation(String key, Player player) {
        return getTranslation(key, player.locale());
    }
}