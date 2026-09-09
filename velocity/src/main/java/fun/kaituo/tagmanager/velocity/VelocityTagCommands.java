package fun.kaituo.tagmanager.velocity;

import com.mojang.brigadier.Message;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fun.kaituo.tagmanager.TagCommands;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagsMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.concurrent.Executor;

final class VelocityTagCommands extends TagCommands<CommandSource> {
    private final ProxyServer server;

    VelocityTagCommands(TagsMap tags, Logger logger, TagDialogRenderer dialogs, ProxyServer server) {
        super(tags, logger, dialogs);
        this.server = server;
    }

    @Override
    protected Predicate<CommandSource> checkPermission(String permission) {
        return source -> source.hasPermission(permission);
    }

    @Override
    protected Audience audience(CommandSource source) {
        return source;
    }

    @Override
    protected Optional<UUID> playerId(CommandSource source) {
        return source instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    @Override
    protected Optional<String> playerName(CommandSource source) {
        return source instanceof Player player ? Optional.of(player.getUsername()) : Optional.empty();
    }

    @Override
    protected boolean isOperator(CommandSource source) {
        return source.hasPermission("kaituotags.admin");
    }

    @Override
    protected Collection<String> playerNames(CommandSource source) {
        return server.getAllPlayers().stream().map(Player::getUsername).toList();
    }

    @Override
    protected Message commandTooltip(Component component) {
        return () -> PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Override
    protected Executor mainThreadExecutor() {
        // Velocity has no single server thread; its command and Audience APIs are thread-safe.
        return Runnable::run;
    }
}
