package fun.kaituo.tagmanager.paper;

import com.mojang.brigadier.Message;
import fun.kaituo.tagmanager.TagCommands;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagsMap;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

final class PaperTagCommands extends TagCommands<CommandSourceStack> {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final TagManagerPlugin plugin;

    PaperTagCommands(TagsMap tags, Logger logger, TagDialogRenderer dialogs, TagManagerPlugin plugin) {
        super(tags, logger, dialogs);
        this.plugin = plugin;
    }

    @Override
    protected Predicate<CommandSourceStack> checkPermission(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    @Override
    protected Audience audience(CommandSourceStack source) {
        return source.getSender();
    }

    @Override
    protected Optional<UUID> playerId(CommandSourceStack source) {
        return player(source).map(Player::getUniqueId);
    }

    @Override
    protected Optional<String> playerName(CommandSourceStack source) {
        return player(source).map(Player::getName);
    }

    @Override
    protected boolean isOperator(CommandSourceStack source) {
        return source.getSender().isOp();
    }

    @Override
    protected Collection<String> playerNames(CommandSourceStack source) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    @Override
    protected Message commandTooltip(Component component) {
        String text = PLAIN.serialize(component);
        return () -> text;
    }

    @Override
    protected Executor mainThreadExecutor() {
        return command -> {
            if (Bukkit.isPrimaryThread()) command.run();
            else Bukkit.getScheduler().runTask(plugin, command);
        };
    }

    private Optional<Player> player(CommandSourceStack source) {
        return source.getSender() instanceof Player player ? Optional.of(player) : Optional.empty();
    }
}
