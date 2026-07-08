package fun.kaituo.tagmanager;


import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagParser;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TagCommands {
    private final Logger logger;
    private final TagConfiguration config;
    private final NodeParser literalParser = TagParser.SIMPLIFIED_TEXT_FORMAT;

    public TagCommands(TagConfiguration config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("tags").executes(this::ui)
                            .then(Commands.literal("help").executes(this::usage))
                            .then(Commands.literal("list").executes(this::list))
                            .then(Commands.literal("listall").requires(Permissions.require("kaituotags.command.listall")).executes(this::listAll))
                            .then(Commands.literal("reload").requires(Permissions.require("kaituotags.command.reload")).executes(this::reload))
                            .then(Commands.literal("create").requires(Permissions.require("kaituotags.command.create"))
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .then(Commands.argument("format", StringArgumentType.string()).executes(this::createWithFormat)
                                                    .then(Commands.argument("description", StringArgumentType.greedyString()).executes(this::createWithFormatDesc)))))
                            .then(Commands.literal("delete").requires(Permissions.require("kaituotags.command.create"))
                                    .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestAllTagIds).executes(this::delete)))
                            .then(Commands.literal("grant").requires(Permissions.require("kaituotags.command.grant"))
                                    .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestAllPlayerNames)
                                            .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestAllTagIds).executes(this::grant))))
                            .then(Commands.literal("revoke").requires(Permissions.require("kaituotags.command.revoke"))
                                    .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestAllPlayerNames).executes(this::revokeAll)
                                            .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestObtainedTagIds).executes(this::revokeOne))))
                            .then(Commands.literal("assign")
                                    .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestObtainedTagIds).executes(this::assignSelf)
                                            .then(Commands.argument("player", StringArgumentType.word()).requires(Permissions.require("kaituotags.command.assign"))
                                                    .suggests(this::suggestAllPlayerNames).executes(this::assignPlayer))))
                            .then(Commands.literal("unassign").executes(this::unassignSelf)
                                    .then(Commands.argument("player", StringArgumentType.word()).requires(Permissions.require("kaituotags.command.unassign"))
                                            .suggests(this::suggestAllPlayerNames).executes(this::unassignPlayer)))
            );
        });
    }

    private CompletableFuture<Suggestions> suggestAllTagIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(config.tags.entrySet(), builder, Map.Entry::getKey, entry -> literalParser.parseNode(entry.getValue().prefix).toComponent());
    }

    private CompletableFuture<Suggestions> suggestObtainedTagIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            String playerArg = StringArgumentType.getString(context, "player");
            return getProfileFromName(playerArg).thenComposeAsync(profile -> getUserManager().loadUser(parseUUID(profile.id)))
                    .thenComposeAsync(user -> {
                        Map<String, Tag> obtained = getUserObtainedTags(user);
                        return SharedSuggestionProvider.suggest(obtained.entrySet(), builder, Map.Entry::getKey, entry -> literalParser.parseNode(entry.getValue().prefix).toComponent());
                    });
        } catch (IllegalArgumentException _) {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                return suggestAllTagIds(context, builder);
            }
            if (player.getPermissionContext().permissionLevel().isEqualOrHigherThan(PermissionLevel.OWNERS)) {
                return suggestAllTagIds(context, builder);
            }
            User user = getUserManager().getUser(player.getUUID());
            if (user == null) {
                return suggestAllTagIds(context, builder);
            }
            Map<String, Tag> obtained = getUserObtainedTags(user);
            return SharedSuggestionProvider.suggest(obtained.entrySet(), builder, Map.Entry::getKey, entry -> literalParser.parseNode(entry.getValue().prefix).toComponent());
        }
    }

    private CompletableFuture<Suggestions> suggestAllPlayerNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder);
    }

    private int ui(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        User user = null;
        if (player != null) {
            user = getUserManager().getUser(player.getUUID());
        }
        if (user != null) {
            player.openDialog(Holder.direct(buildObtainedTagsDialog(getUserObtainedTags(user))));
            return Command.SINGLE_SUCCESS;
        } else {
            return usage(context);
        }
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("Usage:").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags list").withColor(TextColor.AQUA));
        if (Permissions.check(source, "kaituotags.command.listall")) {
            source.sendSystemMessage(Component.literal("/tags listall").withColor(TextColor.AQUA));
        }
        if (Permissions.check(source, "kaituotags.command.reload")) {
            source.sendSystemMessage(Component.literal("/tags reload").withColor(TextColor.AQUA));
        }
        if (Permissions.check(source, "kaituotags.command.create")) {
            source.sendSystemMessage(Component.literal("/tags create <id> <format> [description]").withColor(TextColor.AQUA));
        }
        if (Permissions.check(source, "kaituotags.command.delete")) {
            source.sendSystemMessage(Component.literal("/tags delete <id>").withColor(TextColor.AQUA));
        }
        if (Permissions.check(source, "kaituotags.command.grant")) {
            source.sendSystemMessage(Component.literal("/tags grant <player> <id>").withColor(TextColor.AQUA));
        }
        if (Permissions.check(source, "kaituotags.command.revoke")) {
            source.sendSystemMessage(Component.literal("/tags revoke <player> [id]").withColor(TextColor.AQUA));
        }
        source.sendSystemMessage(Component.literal("/tags assign <id> [player]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags unassign [player]").withColor(TextColor.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new SimpleCommandExceptionType(() -> "The list command can only be run by a player!").create();
        }
        if (player.getPermissionContext().permissionLevel().isEqualOrHigherThan(PermissionLevel.OWNERS)) {
            return listAll(context);
        }
        User user = getUserManager().getUser(player.getUUID());
        if (user == null) {
            throw new SimpleCommandExceptionType(() -> "The list command can only be run by a player!").create();
        }
        Map<String, Tag> obtainedTags = getUserObtainedTags(user);
        if (obtainedTags.isEmpty()) {
            source.sendSystemMessage(Component.literal("You have no obtained tags"));
            return 0;
        }
        source.sendSystemMessage(Component.literal("All tags obtained:"));
        for (Map.Entry<String, Tag> entry : obtainedTags.entrySet()) {
            source.sendSystemMessage(literalParser.parseNode(getTagPreview(entry.getKey(), entry.getValue())).toComponent());
        }
        return obtainedTags.size();
    }

    private int listAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (source.getPlayer() != null) {
            source.getPlayer().openDialog(Holder.direct(buildAllTagsDialog()));
        } else {
            source.sendSystemMessage(Component.literal("All tags registered:"));
            for (Map.Entry<String, Tag> entry : config.tags.entrySet()) {
                source.sendSystemMessage(literalParser.parseNode(getTagPreview(entry.getKey(), entry.getValue())).toComponent());
            }
        }
        return config.tags.size();
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        config.reload();
        source.sendSystemMessage(Component.literal("Tags reloaded (loaded " + config.tags.size() + " tags)").withColor(TextColor.YELLOW));
        return config.tags.size();
    }

    private int createWithFormat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String format = StringArgumentType.getString(context, "format");
        if (config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " already exists").create();
        }
        config.tags.put(id, new Tag(format, null));
        source.sendSystemMessage(
                Component.literal("Successfully created tag ")
                        .append(literalParser.parseNode(format + "<reset>").toComponent())
                        .append(Component.literal(" with id "))
                        .append(id));
        return Command.SINGLE_SUCCESS;
    }

    private int createWithFormatDesc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String format = StringArgumentType.getString(context, "format");
        String description = StringArgumentType.getString(context, "description");
        if (config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " already exists").create();
        }
        config.tags.put(id, new Tag(format, description));
        source.sendSystemMessage(
                Component.literal("Successfully created tag ")
                        .append(literalParser.parseNode(format + "<reset> (" + description + "<reset>)").toComponent())
                        .append(Component.literal(" with id "))
                        .append(id));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        Tag removed = config.tags.remove(id);
        if (removed == null) {
            throw new SimpleCommandExceptionType(() -> "No tag with id " + id + " exists").create();
        }
        source.sendSystemMessage(Component.literal("Successfully removed tag ")
                .append(literalParser.parseNode(removed.prefix).toComponent())
                .append(", id ")
                .append(id));
        return Command.SINGLE_SUCCESS;
    }

    private int grant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String player = StringArgumentType.getString(context, "player");
        String id = StringArgumentType.getString(context, "id");
        if (!config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " not found").create();
        }
        modifyOfflineUser(source, player, offlineUser -> {
                    PermissionNode permissionNode = PermissionNode.builder("kaituotags.tag." + id).build();
                    offlineUser.data().add(permissionNode);
                },
                exactName -> Component.literal("Successfully granted tag ").append(
                        literalParser.parseNode(config.tags.get(id).prefix).toComponent()
                ).append(Component.literal(" to ")).append(exactName));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String player = StringArgumentType.getString(context, "player");
        modifyOfflineUser(source, player,
                (offlineUser -> offlineUser.data().clear(
                        node -> node.getType() == NodeType.PERMISSION && tagNodeFilter((PermissionNode) node))),
                exactName -> Component.literal("Successfully revoked all tags granted to " + exactName));
        return Command.SINGLE_SUCCESS;
    }

    private int revokeOne(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String player = StringArgumentType.getString(context, "player");
        String id = StringArgumentType.getString(context, "id");
        if (!config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " not found").create();
        }
        modifyOfflineUser(source, player, user -> {
                    user.data().remove(PermissionNode.builder("kaituotags.tag." + id).build());
                    user.data().remove(PermissionNode.builder("eternaltags.tag." + id).build());
                    user.data().remove(PermissionNode.builder("delxuetags.tag." + id).build());
                },
                exactName -> Component.literal("Successfully revoked tag " + id + " from " + exactName));
        return Command.SINGLE_SUCCESS;
    }

    private int assignSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new SimpleCommandExceptionType(() -> "The assign command without an assignee can only be run by a player!").create();
        }
        String id = StringArgumentType.getString(context, "id");
        if (!config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " not found").create();
        }
        modifyOfflineUser(source, player.getPlainTextName(), user -> {
            if (!player.getPermissionContext().permissionLevel().isEqualOrHigherThan(PermissionLevel.OWNERS) &&
                    user.getNodes(NodeType.PERMISSION).stream().noneMatch(
                            node ->
                                    node.getPermission().equals("kaituotags.tag." + id) ||
                                            node.getPermission().equals("eternaltags.tag." + id) ||
                                            node.getPermission().equals("deluxetags.tag." + id))) {
                source.sendSystemMessage(Component.literal("You don't have the tag ").append(id).withColor(TextColor.RED));
                return;
            }
            user.data().clear(
                    node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
            Tag tag = config.tags.get(id);
            String hoverablePrefix = "<hover:\"" +
                    tag.description +
                    "\">" +
                    tag.prefix +
                    "</hover>";
            PrefixNode prefixNode = PrefixNode.builder(hoverablePrefix, 10).build();
            user.data().add(prefixNode);
        }, _ -> Component.literal("Successfully assigned tag " + id + " to you"));
        return Command.SINGLE_SUCCESS;
    }

    private int assignPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String player = StringArgumentType.getString(context, "player");
        if (!config.tags.containsKey(id)) {
            throw new SimpleCommandExceptionType(() -> "Tag " + id + " not found").create();
        }
        modifyOfflineUser(source, player, user -> {
            if (user.getNodes(NodeType.PERMISSION).stream().noneMatch(
                    node ->
                            node.getPermission().equals("kaituotags.tag." + id) ||
                                    node.getPermission().equals("eternaltags.tag." + id) ||
                                    node.getPermission().equals("deluxetags.tag." + id))) {
                source.sendSystemMessage(Component.literal("The specified player doesn't have the tag ").append(id).withColor(TextColor.RED));
                return;
            }
            user.data().clear(
                    node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
            Tag tag = config.tags.get(id);
            String sb = "<hover:\"" +
                    tag.description +
                    "\">" +
                    tag.prefix +
                    "</hover>";
            PrefixNode prefixNode = PrefixNode.builder(sb, 10).build();
            user.data().add(prefixNode);
        }, exactName -> Component.literal("Successfully assigned tag " + id + " to " + exactName));
        return Command.SINGLE_SUCCESS;
    }

    private int unassignSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            throw new SimpleCommandExceptionType(() -> "The unassign command without an assignee can only be run by a player").create();
        }
        modifyOfflineUser(source, player.getPlainTextName(),
                (offlineUser -> offlineUser.data().clear(
                        node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10)),
                _ -> Component.literal("Successfully unassigned all your tags"));
        return Command.SINGLE_SUCCESS;
    }

    private int unassignPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String player = StringArgumentType.getString(context, "player");
        modifyOfflineUser(source, player,
                (offlineUser -> offlineUser.data().clear(
                        node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10)),
                exactName -> Component.literal("Successfully unassigned all tags from " + exactName));
        return Command.SINGLE_SUCCESS;
    }

    private UserManager getUserManager() {
        return LuckPermsProvider.get().getUserManager();
    }

    private static boolean tagNodeFilter(PermissionNode node) {
        return node.getPermission().startsWith("kaituotags.tag") ||
                node.getPermission().startsWith("eternaltags.tag") ||
                node.getPermission().startsWith("deluxetags.tag");
    }

    private static String idFromTagNode(PermissionNode node) {
        String[] parts = node.getPermission().split("\\.");
        if (parts.length == 0) {
            return null;
        }
        return parts[parts.length - 1];
    }

    private Map.Entry<String, Tag> tagEntryFromId(String id) {
        if (config.tags.containsKey(id)) {
            return new AbstractMap.SimpleEntry<>(id, config.tags.get(id));
        }
        return null;
    }

    private Map<String, Tag> getUserObtainedTags(@NonNull User user) {
        return user.getNodes(NodeType.PERMISSION).stream()
                .filter(TagCommands::tagNodeFilter)
                .map(TagCommands::idFromTagNode)
                .filter(Objects::nonNull)
                .distinct()
                .map(this::tagEntryFromId)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void modifyOfflineUser(CommandSourceStack source, String name, Consumer<User> modifyingFunction, Function<String, Component> successMessage) {
        CompletableFuture<String> modifyFuture = getProfileFromName(name).thenComposeAsync(profile -> {
            getUserManager().modifyUser(parseUUID(profile.id), modifyingFunction);
            return CompletableFuture.completedFuture(profile.name);
        });
        modifyFuture.whenCompleteAsync((player, exception) -> {
            if (exception != null) {
                logger.error("An exception occurred when getting User from LuckPerms", exception);
                source.sendSystemMessage(Component.literal("An exception occurred when getting User from LuckPerms").withColor(TextColor.RED));
                return;
            }
            source.sendSystemMessage(successMessage.apply(player));
        });
    }

    private static CompletableFuture<MojangProfile> getProfileFromName(String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        // Status 204 means the player does not exist
                        if (response.statusCode() == 204) return null;

                        return new Gson().fromJson(response.body(), MojangProfile.class);
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static UUID parseUUID(String uuidString) {
        return UUID.fromString(uuidString.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    private @NonNull String getTagPreview(String id, Tag tag) {
        StringBuilder sb = new StringBuilder(id + "<reset>: " + tag.prefix);
        if (tag.description != null && !literalParser.parseNode(tag.description).toComponent().getString().isEmpty()) {
            sb.append("<reset> (").append(tag.description).append("<reset>) ");
        }
        return sb.toString();
    }

    private Dialog buildAllTagsDialog() {
        List<DialogBody> dialogBodyList = new ArrayList<>();
        config.tags.forEach((id, tag) -> dialogBodyList.add(new PlainMessage(
                literalParser.parseNode(getTagPreview(id, tag)).toComponent(), PlainMessage.DEFAULT_WIDTH)));
        CommonDialogData dialogData = new CommonDialogData(
                Component.literal("All tags registered"),
                Optional.empty(),
                true,
                true,
                DialogAction.CLOSE,
                dialogBodyList,
                List.of()
        );
        CommonButtonData buttonData = new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH);
        ActionButton actionButton = new ActionButton(buttonData, Optional.empty());
        return new NoticeDialog(dialogData, actionButton);
    }

    private Dialog buildObtainedTagsDialog(Map<String, Tag> tags) {
        CommonDialogData dialogData = new CommonDialogData(
                Component.literal("Choose a tag to assign"),
                Optional.empty(),
                true,
                true,
                DialogAction.CLOSE,
                List.of(),
                List.of()
        );
        List<ActionButton> actionButtons = new ArrayList<>();
        tags.forEach((id, tag) -> {
            Component tagButtonComponent = literalParser.parseNode(tag.prefix).toComponent();
            Component tagButtonHoverComponent = literalParser.parseNode(tag.description).toComponent();
            CommonButtonData tagButtonData = new CommonButtonData(tagButtonComponent, Optional.of(tagButtonHoverComponent), CommonButtonData.DEFAULT_WIDTH);
            Action tagButtonAction = new StaticAction(new ClickEvent.RunCommand("tags assign " + id));
            actionButtons.add(new ActionButton(tagButtonData, Optional.of(tagButtonAction)));
        });
        Component clearButtonComponent = Component.literal("Clear your tag");
        CommonButtonData clearButtonData = new CommonButtonData(clearButtonComponent, CommonButtonData.DEFAULT_WIDTH);
        Action clearButtonAction = new StaticAction(new ClickEvent.RunCommand("tags unassign"));
        actionButtons.add(new ActionButton(clearButtonData, Optional.of(clearButtonAction)));
        CommonButtonData exitButtonData = new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH);
        ActionButton exitButton = new ActionButton(exitButtonData, Optional.empty());
        return new MultiActionDialog(dialogData, actionButtons, Optional.of(exitButton), 1);
    }

    static class MojangProfile {
        String id;   // The UUID (un-dashed)
        String name; // The exact username
    }
}
