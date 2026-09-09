package fun.kaituo.tagmanager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Platform-neutral commands. Platform modules provide source access and dialog rendering.
 */
public abstract class TagCommands<S> {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final Logger logger;
    private final TagsMap config;
    private final TagDialogRenderer dialogs;

    protected TagCommands(TagsMap config, Logger logger, TagDialogRenderer dialogs) {
        this.config = config;
        this.logger = logger;
        this.dialogs = dialogs;
    }

    protected abstract Predicate<S> checkPermission(String permission);

    protected abstract Audience audience(S source);

    protected abstract Optional<UUID> playerId(S source);

    protected abstract Optional<String> playerName(S source);

    protected abstract boolean isOperator(S source);

    protected abstract Collection<String> playerNames(S source);

    protected abstract Message commandTooltip(Component component);

    /** Schedules game/platform API interactions on that platform's safe thread. */
    protected abstract Executor mainThreadExecutor();

    public final void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(literal("tags").executes(this::ui)
                .then(literal("help").executes(this::usage))
                .then(literal("list").executes(this::list))
                .then(literal("listall").requires(checkPermission("kaituotags.command.listall")).executes(this::listAll))
                .then(literal("reload").requires(checkPermission("kaituotags.command.reload")).executes(this::reload))
                .then(literal("create").requires(checkPermission("kaituotags.command.create")).then(argument("id", StringArgumentType.word()).then(argument("format", StringArgumentType.string()).executes(this::createWithFormat).then(argument("description", StringArgumentType.greedyString()).executes(this::createWithFormatDesc)))))
                .then(literal("delete").requires(checkPermission("kaituotags.command.create")).then(argument("id", StringArgumentType.word()).suggests(this::suggestAllTagIds).executes(this::delete)))
                .then(literal("grant").requires(checkPermission("kaituotags.command.grant")).then(argument("player", StringArgumentType.word()).suggests(this::suggestAllPlayerNames).then(argument("id", StringArgumentType.word()).suggests(this::suggestAllTagIds).executes(this::grant))))
                .then(literal("revoke").requires(checkPermission("kaituotags.command.revoke")).then(argument("player", StringArgumentType.word()).suggests(this::suggestAllPlayerNames).executes(this::revokeAll).then(argument("id", StringArgumentType.word()).suggests(this::suggestObtainedTagIds).executes(this::revokeOne))))
                .then(literal("assign").then(argument("id", StringArgumentType.word()).suggests(this::suggestObtainedTagIds).executes(this::assignSelf).then(argument("player", StringArgumentType.word()).requires(checkPermission("kaituotags.command.assign")).suggests(this::suggestAllPlayerNames).executes(this::assignPlayer))))
                .then(literal("unassign").executes(this::unassignSelf).then(argument("player", StringArgumentType.word()).requires(checkPermission("kaituotags.command.unassign")).suggests(this::suggestAllPlayerNames).executes(this::unassignPlayer))));
    }

    private LiteralArgumentBuilder<S> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private RequiredArgumentBuilder<S, ?> argument(String name, ArgumentType<?> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private void send(S source, String message) {
        audience(source).sendMessage(MINI_MESSAGE.deserialize(message));
    }

    private Component component(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    private String plain(String value) {
        return MINI_MESSAGE.escapeTags(value);
    }


    private CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }

    private <T> CompletableFuture<Suggestions> suggest(Iterable<T> values, SuggestionsBuilder builder, Function<T, String> name, Function<T, String> tooltip) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (T value : values) {
            String suggestion = name.apply(value);
            if (suggestion.toLowerCase(Locale.ROOT).contains(prefix))
                builder.suggest(suggestion, commandTooltip(component(tooltip.apply(value))));
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAllTagIds(CommandContext<S> context, SuggestionsBuilder builder) {
        return suggest(config.tags.entrySet(), builder, Map.Entry::getKey, entry -> entry.getValue().prefix);
    }

    private CompletableFuture<Suggestions> suggestObtainedTagIds(CommandContext<S> context, SuggestionsBuilder builder) {
        S source = context.getSource();
        Optional<UUID> player = playerId(source);
        if (isOperator(source) || player.isEmpty()) return suggestAllTagIds(context, builder);
        return getUserManager().loadUser(player.orElseThrow())
                .thenCompose(user -> suggest(getUserObtainedTags(user).entrySet(), builder, Map.Entry::getKey, e -> e.getValue().prefix));
    }

    private CompletableFuture<Suggestions> suggestAllPlayerNames(CommandContext<S> context, SuggestionsBuilder builder) {
        return suggest(playerNames(context.getSource()), builder, Function.identity(), ignored -> "");
    }

    private int ui(CommandContext<S> context) {
        S source = context.getSource();
        Optional<UUID> player = playerId(source);
        if (player.isEmpty()) return usage(context);
        getUserManager().loadUser(player.orElseThrow()).whenCompleteAsync((user, failure) -> {
            if (user == null || failure != null) usage(context);
            else dialogs.showTagSelector(player.orElseThrow(), Map.copyOf(getUserObtainedTags(user)));
        }, mainThreadExecutor());
        return Command.SINGLE_SUCCESS;
    }

    private int usage(CommandContext<S> context) {
        S source = context.getSource();
        send(source, "<aqua>Usage:");
        send(source, "<aqua>/tags");
        send(source, "<aqua>/tags list");
        if (checkPermission("kaituotags.command.listall").test(source)) send(source, "<aqua>/tags listall");
        if (checkPermission("kaituotags.command.reload").test(source)) send(source, "<aqua>/tags reload");
        if (checkPermission("kaituotags.command.create").test(source))
            send(source, "<aqua>/tags create <id> <format> [description]");
        if (checkPermission("kaituotags.command.delete").test(source)) send(source, "<aqua>/tags delete <id>");
        if (checkPermission("kaituotags.command.grant").test(source)) send(source, "<aqua>/tags grant <player> <id>");
        if (checkPermission("kaituotags.command.revoke").test(source)) send(source, "<aqua>/tags revoke <player> [id]");
        send(source, "<aqua>/tags assign <id> [player]");
        send(source, "<aqua>/tags unassign [player]");
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        S source = context.getSource();
        Optional<UUID> player = playerId(source);
        if (player.isEmpty()) throw error("The list command can only be run by a player!");
        getUserManager().loadUser(player.orElseThrow()).whenCompleteAsync((user, failure) -> {
            if (user == null || failure != null) {
                send(source, "<red>The list command can only be run by a player!");
                return;
            }
            Map<String, Tag> tags = getUserObtainedTags(user);
            if (tags.isEmpty()) {
                send(source, "You have no obtained tags");
                return;
            }
            send(source, "All tags obtained:");
            tags.forEach((id, tag) -> send(source, preview(id, tag)));
        }, mainThreadExecutor());
        return Command.SINGLE_SUCCESS;
    }

    private int listAll(CommandContext<S> context) {
        S source = context.getSource();
        Optional<UUID> player = playerId(source);
        if (player.isPresent()) {
            dialogs.showAllTags(player.orElseThrow(), Map.copyOf(config.tags));
        } else {
            send(source, "All tags registered:");
            config.tags.forEach((id, tag) -> send(source, preview(id, tag)));
        }
        return config.tags.size();
    }

    private int reload(CommandContext<S> context) {
        S source = context.getSource();
        config.reloadAsync().whenCompleteAsync((newTags, failure) -> {
            if (failure != null) {
                logger.error("Failed to reload tags", failure);
                send(source, "<red>Failed to reload tags; see the server log for details.");
                return;
            }
            config.replace(newTags);
            send(source, "<yellow>Tags reloaded (loaded " + config.tags.size() + " tags)");
        }, mainThreadExecutor());
        return Command.SINGLE_SUCCESS;
    }

    private int createWithFormat(CommandContext<S> context) throws CommandSyntaxException {
        return create(context, null);
    }

    private int createWithFormatDesc(CommandContext<S> context) throws CommandSyntaxException {
        return create(context, StringArgumentType.getString(context, "description"));
    }

    private int create(CommandContext<S> context, String description) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id"), format = StringArgumentType.getString(context, "format");
        if (config.tags.containsKey(id)) throw error("Tag " + id + " already exists");
        config.tags.put(id, new Tag(format, description));
        send(context.getSource(), "Successfully created tag " + format + (description == null ? "" : "<reset> (" + description + "<reset>)") + " with id " + plain(id));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<S> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        Tag removed = config.tags.remove(id);
        if (removed == null) throw error("No tag with id " + id + " exists");
        send(context.getSource(), "Successfully deleted tag " + removed.prefix + "<reset>, id " + plain(id));
        return Command.SINGLE_SUCCESS;
    }

    private int grant(CommandContext<S> context) throws CommandSyntaxException {
        String player = StringArgumentType.getString(context, "player");
        String id = requiredTag(context);
        modifyOfflineUser(context.getSource(), player, user -> user.data().add(PermissionNode.builder("kaituotags.tag." + id)
                        .withContext(DefaultContextKeys.SERVER_KEY, "global")
                        .build()),
                playerName -> "Successfully granted tag " + config.tags.get(id).prefix + "<reset> to " + plain(playerName));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeAll(CommandContext<S> context) {
        String player = StringArgumentType.getString(context, "player");
        modifyOfflineUser(context.getSource(), player, user -> user.data().clear(node -> node.getType() == NodeType.PERMISSION && tagNodeFilter((PermissionNode) node)),
                name -> "Successfully revoked all tags granted to " + plain(name));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeOne(CommandContext<S> context) throws CommandSyntaxException {
        String player = StringArgumentType.getString(context, "player"), id = requiredTag(context);
        modifyOfflineUser(context.getSource(), player, user -> {
            user.data().remove(PermissionNode.builder("kaituotags.tag." + id)
                    .withContext(DefaultContextKeys.SERVER_KEY, "global")
                    .build());
            // Retain compatibility with grants created before the explicit context.
            user.data().remove(PermissionNode.builder("kaituotags.tag." + id).build());
            user.data().remove(PermissionNode.builder("eternaltags.tag." + id).build());
            user.data().remove(PermissionNode.builder("deluxetags.tag." + id).build());
        }, name -> "Successfully revoked tag " + plain(id) + " from " + plain(name));
        return Command.SINGLE_SUCCESS;
    }

    private int assignSelf(CommandContext<S> context) throws CommandSyntaxException {
        S source = context.getSource();
        String id = requiredTag(context);
        Optional<String> player = playerName(source);
        if (player.isEmpty()) throw error("The assign command without an assignee can only be run by a player!");
        modifyOfflineUser(source, player.orElseThrow(), user -> {
            if (!isOperator(source) && !ownsTag(user, id)) {
                send(source, "<red>You don't have the tag " + plain(id));
                return;
            }
            assign(user, id);
        }, ignored -> "Successfully assigned tag " + plain(id) + " to you");
        return Command.SINGLE_SUCCESS;
    }

    private int assignPlayer(CommandContext<S> context) throws CommandSyntaxException {
        S source = context.getSource();
        String id = requiredTag(context), player = StringArgumentType.getString(context, "player");
        modifyOfflineUser(source, player, user -> {
            if (!isOperator(source) && !ownsTag(user, id)) {
                send(source, "<red>The specified player doesn't have the tag " + plain(id));
                return;
            }
            assign(user, id);
        }, name -> "Successfully assigned tag " + plain(id) + " to " + plain(name));
        return Command.SINGLE_SUCCESS;
    }

    private int unassignSelf(CommandContext<S> context) throws CommandSyntaxException {
        Optional<String> player = playerName(context.getSource());
        if (player.isEmpty()) throw error("The unassign command without an assignee can only be run by a player");
        modifyOfflineUser(context.getSource(), player.orElseThrow(), this::clearAssignedTag, _ -> "Successfully unassigned all your tags");
        return Command.SINGLE_SUCCESS;
    }

    private int unassignPlayer(CommandContext<S> context) {
        String player = StringArgumentType.getString(context, "player");
        modifyOfflineUser(context.getSource(), player, this::clearAssignedTag, name -> "Successfully unassigned all tags from " + plain(name));
        return Command.SINGLE_SUCCESS;
    }

    private String requiredTag(CommandContext<S> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        if (!config.tags.containsKey(id)) throw error("Tag " + id + " not found");
        return id;
    }

    private UserManager getUserManager() {
        return LuckPermsProvider.get().getUserManager();
    }

    private static boolean tagNodeFilter(PermissionNode node) {
        return node.getPermission().startsWith("kaituotags.tag") || node.getPermission().startsWith("eternaltags.tag") || node.getPermission().startsWith("deluxetags.tag");
    }

    private static String idFromTagNode(PermissionNode node) {
        String[] parts = node.getPermission().split("\\.");
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    private boolean ownsTag(User user, String id) {
        return user.getNodes(NodeType.PERMISSION).stream().anyMatch(node -> tagNodeFilter(node) && node.getPermission().endsWith("." + id));
    }

    private void assign(User user, String id) {
        clearAssignedTag(user);
        Tag tag = config.tags.get(id);
        String hover = tag.description == null ? tag.prefix : "<hover:show_text:\"" + tag.description + "\">" + tag.prefix + "</hover>";
        user.data().add(PrefixNode.builder(hover, 10).build());
    }

    private void clearAssignedTag(User user) {
        user.data().clear(node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
    }

    private Map<String, Tag> getUserObtainedTags(@NonNull User user) {
        Map<String, Tag> result = new LinkedHashMap<>();
        user.getNodes(NodeType.PERMISSION).stream().filter(TagCommands::tagNodeFilter).map(TagCommands::idFromTagNode).filter(Objects::nonNull).distinct().map(id -> new AbstractMap.SimpleEntry<>(id, config.tags.get(id))).filter(e -> e.getValue() != null).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private void modifyOfflineUser(S source, String name, Consumer<User> change, Function<String, String> success) {
        getUserManager().lookupUniqueId(name).thenCompose(id -> getUserManager().modifyUser(id, change)).whenCompleteAsync((_, failure) -> {
            if (failure != null) {
                logger.error("An exception occurred when modifying a LuckPerms user", failure);
                send(source, "<red>An exception occurred when getting User from LuckPerms");
            } else send(source, success.apply(name));
        }, mainThreadExecutor());
    }

    private String preview(String id, Tag tag) {
        String description = tag.description == null || tag.description.isBlank() ? "" : "<reset> (" + tag.description + "<reset>)";
        return plain(id) + "<reset>: " + tag.prefix + description;
    }
}
