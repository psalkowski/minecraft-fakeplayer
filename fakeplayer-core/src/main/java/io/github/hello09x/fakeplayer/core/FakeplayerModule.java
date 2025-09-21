package io.github.hello09x.fakeplayer.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.api.spi.NMSBridge;
import io.github.hello09x.fakeplayer.core.config.FakeplayerConfig;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerList;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerManager;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerRespawnManager;
import io.github.hello09x.fakeplayer.core.manager.action.ActionManager;
import io.github.hello09x.fakeplayer.core.manager.invsee.InvseeManager;
import io.github.hello09x.fakeplayer.core.manager.invsee.OpenInvInvseeManagerImpl;
import io.github.hello09x.fakeplayer.core.manager.invsee.SimpleInvseeManagerImpl;
import io.github.hello09x.fakeplayer.core.placeholder.FakeplayerPlaceholderExpansion;
import io.github.hello09x.fakeplayer.core.placeholder.FakeplayerPlaceholderExpansionImpl;
import io.github.hello09x.fakeplayer.core.util.ClassUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceLoader;
import java.util.logging.Logger;

public class FakeplayerModule extends AbstractModule {

    private final static Logger log = Main.getInstance().getLogger();

    @Override
    protected void configure() {
        super.bind(Plugin.class).toInstance(Main.getInstance());
    }

    @Provides
    @Singleton
    public @NotNull InvseeManager invseeManager(FakeplayerConfig config, FakeplayerManager fakeplayerManager, FakeplayerList fakeplayerList) {
        return switch (config.getInvseeImplement()) {
            case SIMPLE -> new SimpleInvseeManagerImpl(fakeplayerManager, fakeplayerList);
            case AUTO -> {
                if (Bukkit.getPluginManager().isPluginEnabled("OpenInv") && ClassUtils.isClassExists("com.lishid.openinv.IOpenInv")) {
                    log.info("Using OpenInv as invsee implement");
                    yield new OpenInvInvseeManagerImpl(fakeplayerManager, fakeplayerList);
                }
                log.info("Using simple invsee implement");
                yield new SimpleInvseeManagerImpl(fakeplayerManager, fakeplayerList);
            }
        };
    }

    @Provides
    @Singleton
    private @NotNull NMSBridge nmsBridge() {
        var bridge = ServiceLoader
                .load(NMSBridge.class, NMSBridge.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(NMSBridge::isSupported)
                .findAny()
                .orElse(null);

        if (bridge == null) {
            throw new ExceptionInInitializerError("Unsupported Minecraft version: " + Bukkit.getMinecraftVersion());
        }
        return bridge;
    }

    @Singleton
    @Provides
    private @Nullable FakeplayerPlaceholderExpansion fakeplayerPlaceholderExpansion(FakeplayerManager fakeplayerManager, ActionManager actionManager) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") || !ClassUtils.isClassExists("me.clip.placeholderapi.expansion.PlaceholderExpansion")) {
            return null;
        }
        return new FakeplayerPlaceholderExpansionImpl(fakeplayerManager, actionManager);
    }

}
