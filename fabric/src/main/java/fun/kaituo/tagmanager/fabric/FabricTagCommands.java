package fun.kaituo.tagmanager.fabric;

import com.mojang.brigadier.Message;
import fun.kaituo.tagmanager.TagCommands;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagsMap;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.Executor;

/**
 * Fabric bindings for the command source; common command behavior stays in :common.
 */
final class FabricTagCommands extends TagCommands<CommandSourceStack> {
    private final Supplier<MinecraftServer> server;

    FabricTagCommands(TagsMap tags, Logger logger, TagDialogRenderer dialogs, Supplier<MinecraftServer> server) {
        super(tags, logger, dialogs);
        this.server = server;
    }

    @Override
    protected Predicate<CommandSourceStack> checkPermission(String permission) {
        return source -> Permissions.check(source, permission);
    }

    @Override
    protected Audience audience(CommandSourceStack source) {
        return MinecraftServerAudiences.of(server.get()).audience(source);
    }

    @Override
    protected Optional<UUID> playerId(CommandSourceStack source) {
        return Optional.ofNullable(source.getPlayer()).map(ServerPlayer::getUUID);
    }

    @Override
    protected Optional<String> playerName(CommandSourceStack source) {
        return Optional.ofNullable(source.getPlayer()).map(ServerPlayer::getPlainTextName);
    }

    @Override
    protected boolean isOperator(CommandSourceStack source) {
        return source.getPlayer() != null && source.getPlayer().permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS));
    }

    @Override
    protected Collection<String> playerNames(CommandSourceStack source) {
        return java.util.List.of(source.getServer().getPlayerNames());
    }

    @Override
    protected Message commandTooltip(Component component) {
        return MinecraftServerAudiences.of(server.get()).nonWrappingSerializer().serialize(component);
    }

    @Override
    protected Executor mainThreadExecutor() {
        return runnable -> server.get().execute(runnable);
    }
}
