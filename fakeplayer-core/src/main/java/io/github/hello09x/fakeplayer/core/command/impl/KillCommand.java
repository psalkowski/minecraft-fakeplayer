package io.github.hello09x.fakeplayer.core.command.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import io.github.hello09x.fakeplayer.core.manager.FakeplayerDeathTracker;
import io.github.hello09x.fakeplayer.core.repository.FakeplayerProfileRepository;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;
import java.util.UUID;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;

@Singleton
public class KillCommand extends AbstractCommand {

    private final FakeplayerDeathTracker deathTracker;
    private final FakeplayerProfileRepository profileRepository;

    @Inject
    public KillCommand(FakeplayerDeathTracker deathTracker, FakeplayerProfileRepository profileRepository) {
        this.deathTracker = deathTracker;
        this.profileRepository = profileRepository;
    }

    /**
     * 移除假人 - Enhanced with Smart Auto-Respawn prevention
     */
    public void kill(@NotNull CommandSender sender, @NotNull CommandArguments args) throws WrapperCommandSyntaxException {
        var fakes = super.getFakeplayers(sender, args);

        if (fakes.isEmpty()) {
            sender.sendMessage(translatable("fakeplayer.command.kill.error.non-removed", GRAY));
            return;
        }

        var names = new StringJoiner(", ");
        for (var fake : fakes) {
            UUID uuid = fake.getUniqueId();

            // Mark as command kill to prevent auto-respawn
            deathTracker.markCommandKill(uuid);
            profileRepository.setShouldRespawn(uuid, false);
            profileRepository.setDeathReason(uuid, FakeplayerDeathTracker.DeathReason.COMMAND);

            if (manager.remove(fake.getName(), "command kill")) {
                names.add(fake.getName());
            }
        }
        sender.sendMessage(textOfChildren(
                translatable("fakeplayer.command.kill.success.removed", GRAY),
                space(),
                text(names.toString())
        ));
    }


}
