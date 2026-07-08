package fun.kaituo.tagmanager;


import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagParser;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
            dispatcher.register(Commands.literal("tags").executes(this::run0Args)
                    .then(Commands.argument("action", StringArgumentType.word()).executes(this::run1Args)
                            .then(Commands.argument("payload1", StringArgumentType.word()).executes(this::run2Args)
                                    .then(Commands.argument("payload2", StringArgumentType.word()).executes(this::run3Args)
                                            .then(Commands.argument("payload3", StringArgumentType.word()).executes(this::run4Args)
                                            )))));
        });
    }

    private UserManager getUserManager() {
        return LuckPermsProvider.get().getUserManager();
    }

    private boolean checkPermission(User user, String permission) {
        return user.resolveInheritedNodes(QueryOptions.defaultContextualOptions()).contains(PermissionNode.builder(permission).value(true).build());
    }

    private int run0Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("Usage:").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags list").withColor(TextColor.AQUA));
        if (source.isPlayer() &&
                checkPermission(
                        Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                        "kaituotags.command.listall")) {
            source.sendSystemMessage(Component.literal("/tags listall").withColor(TextColor.AQUA));
        }
        source.sendSystemMessage(Component.literal("/tags reload").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags create <id> <format> [description]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags delete <id>").withColor(TextColor.AQUA));
        if (source.isPlayer() &&
                checkPermission(
                        Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                        "kaituotags.command.grant")) {
            source.sendSystemMessage(Component.literal("/tags grant <player> <id>").withColor(TextColor.AQUA));
        }
        if (source.isPlayer() &&
                checkPermission(
                        Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                        "kaituotags.command.grant")) {
            source.sendSystemMessage(Component.literal("/tags revoke <player> [id]").withColor(TextColor.AQUA));
        }
        source.sendSystemMessage(Component.literal("/tags assign <id> [player]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags unassign [player]").withColor(TextColor.AQUA));
        return Command.SINGLE_SUCCESS;
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

    private void modifyOfflineUser(CommandSourceStack source, String name, Consumer<User> modifyingFunction, Component successMessage) {
        CompletableFuture<Void> modifyFuture =
                getUUIDFromName(name).thenComposeAsync(uuid -> getUserManager().modifyUser(uuid, modifyingFunction));
        modifyFuture.whenCompleteAsync((_, exception) -> {
            if (exception != null) {
                logger.error("An exception occurred when getting User from LuckPerms", exception);
                source.sendSystemMessage(
                        Component.literal("An exception occurred when getting User from LuckPerms.")
                                .withColor(TextColor.RED));
                return;
            }
            source.sendSystemMessage(successMessage);
        });
    }

    private int run1Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        User user = null;
        if (player != null) {
            user = getUserManager().getUser(player.getUUID());
        }
        switch (action) {
            case "list":
                if (user == null) {
                    source.sendSystemMessage(Component.literal(
                            "The list command can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                Map<String, Tag> obtainedTags = getUserObtainedTags(user);
                if (obtainedTags.isEmpty()) {
                    source.sendSystemMessage(Component.literal("You have no obtained tags"));
                    break;
                }
                source.sendSystemMessage(Component.literal("All tags obtained:"));
                for (Map.Entry<String, Tag> entry : obtainedTags.entrySet()) {
                    source.sendSystemMessage(
                            literalParser.parseNode(
                                    entry.getKey() + "<reset>: " +
                                            entry.getValue().prefix +
                                            "<reset> (" + entry.getValue().description + "<reset>) ").toComponent());
                }
                break;
            case "listall":
                if (source.isPlayer() &&
                        !checkPermission(
                                Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                                "kaituotags.command.listall")) {
                    source.sendSystemMessage(Component.literal("You don't have the permission to run this command!").withColor(TextColor.RED));
                    break;
                }
                source.sendSystemMessage(Component.literal("All tags registered:"));
                for (Map.Entry<String, Tag> entry : config.tags.entrySet()) {
                    source.sendSystemMessage(
                            literalParser.parseNode(
                                    entry.getKey() + "<reset>: " +
                                            entry.getValue().prefix +
                                            "<reset> (" + entry.getValue().description + "<reset>) ").toComponent());
                }
                break;
            case "reload":
                config.reload();
                source.sendSystemMessage(Component.literal("Tags reloaded!").withColor(TextColor.YELLOW));
                break;
            case "unassign":
                if (user == null) {
                    source.sendSystemMessage(Component.literal(
                            "The unassign command without an assignee can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                user.data().clear(node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
                getUserManager().saveUser(user);
                source.sendSystemMessage(Component.literal("Cleared your currently assigned tag"));
                break;
            default:
                throw new SimpleCommandExceptionType(() -> "Invalid action: " + action).create();
        }
        return Command.SINGLE_SUCCESS;
    }

    private int run2Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        String payload1 = StringArgumentType.getString(context, "payload1");
        CommandSourceStack source = context.getSource();
        switch (action) {
            case "delete":
                Tag removed = config.tags.remove(payload1);
                if (removed == null) {
                    source.sendSystemMessage(Component.literal("No tag with id " + payload1 + " exists").withColor(TextColor.RED));
                    break;
                }
                source.sendSystemMessage(Component.literal("Successfully removed tag " + payload1));
                break;
            case "revoke":
                if (source.isPlayer() &&
                        !checkPermission(
                                Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                                "kaituotags.command.revoke")) {
                    source.sendSystemMessage(Component.literal("You don't have the permission to run this command!").withColor(TextColor.RED));
                    break;
                }
                modifyOfflineUser(source, payload1,
                        (offlineUser -> offlineUser.data().clear(
                                node -> node.getType() == NodeType.PERMISSION && tagNodeFilter((PermissionNode) node))),
                        Component.literal("Successfully revoked all tags granted to " + payload1));
                break;
            case "assign":
                ServerPlayer player = source.getPlayer();
                if (player == null) {
                    source.sendSystemMessage(Component.literal(
                            "The assign command without an assignee can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                if (!config.tags.containsKey(payload1)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload1 + " not found").withColor(TextColor.RED));
                    break;
                }
                modifyOfflineUser(source, player.getPlainTextName(), user -> {
                    if (user.getNodes(NodeType.PERMISSION).stream().noneMatch(
                            node ->
                                    node.getPermission().equals("kaituotags.tag." + payload1) ||
                                            node.getPermission().equals("eternaltags.tag." + payload1) ||
                                            node.getPermission().equals("deluxetags.tag." + payload1))) {
                        source.sendSystemMessage(Component.literal("You don't have the tag " + payload1).withColor(TextColor.RED));
                        return;
                    }
                    user.data().clear(
                            node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
                    Tag tag = config.tags.get(payload1);
                    String sb = "<hover:\"" +
                            tag.description +
                            "\">" +
                            tag.prefix +
                            "</hover>";
                    PrefixNode prefixNode = PrefixNode.builder(sb, 10).build();
                    user.data().add(prefixNode);
                }, Component.literal("Successfully assigned tag " + payload1 + " to you"));
                break;
            case "unassign":
                modifyOfflineUser(source, payload1,
                        (offlineUser -> offlineUser.data().clear(
                                node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10)),
                        Component.literal("Successfully unassigned all tags from " + payload1));
                break;
            default:
                throw new SimpleCommandExceptionType(() -> "Invalid action: " + action).create();
        }
        return Command.SINGLE_SUCCESS;
    }

    private int run3Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        String payload1 = StringArgumentType.getString(context, "payload1");
        String payload2 = StringArgumentType.getString(context, "payload2");
        CommandSourceStack source = context.getSource();
        switch (action) {
            case "create":
                config.tags.put(payload1, new Tag(payload2, null));
                source.sendSystemMessage(literalParser.parseNode(
                        "Successfully created tag (id " + payload1 + "<reset>) " + payload2).toComponent());
                break;
            case "grant":
                if (source.isPlayer() &&
                        !checkPermission(
                                Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                                "kaituotags.command.grant")) {
                    source.sendSystemMessage(Component.literal("You don't have the permission to run this command!").withColor(TextColor.RED));
                    break;
                }
                if (!config.tags.containsKey(payload2)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload2 + " not found").withColor(TextColor.RED));
                    break;
                }
                modifyOfflineUser(source, payload1, offlineUser -> {
                            PermissionNode permissionNode = PermissionNode.builder("kaituotags.tag." + payload2).build();
                            offlineUser.data().add(permissionNode);
                        },
                        Component.literal("Successfully granted tag " + payload2 + " to " + payload1));
                break;
            case "revoke":
                if (source.isPlayer() &&
                        !checkPermission(
                                Objects.requireNonNull(getUserManager().getUser(source.getPlayerOrException().getUUID())),
                                "kaituotags.command.revoke")) {
                    source.sendSystemMessage(Component.literal("You don't have the permission to run this command!").withColor(TextColor.RED));
                    break;
                }
                if (!config.tags.containsKey(payload2)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload2 + " not found").withColor(TextColor.RED));
                    break;
                }
                modifyOfflineUser(source, payload1, user -> {
                            user.data().remove(PermissionNode.builder("kaituotags.tag." + payload2).build());
                            user.data().remove(PermissionNode.builder("eternaltags.tag." + payload2).build());
                            user.data().remove(PermissionNode.builder("delxuetags.tag." + payload2).build());
                        },
                        Component.literal("Successfully revoked tag " + payload2 + " from " + payload1));
                break;
            case "assign":
                if (!config.tags.containsKey(payload1)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload1 + " not found").withColor(TextColor.RED));
                    break;
                }
                modifyOfflineUser(source, payload2, user -> {
                    if (user.getNodes(NodeType.PERMISSION).stream().noneMatch(
                            node ->
                                    node.getPermission().equals("kaituotags.tag." + payload1) ||
                                            node.getPermission().equals("eternaltags.tag." + payload1) ||
                                            node.getPermission().equals("deluxetags.tag." + payload1))) {
                        source.sendSystemMessage(Component.literal("The player " + payload2 + " doesn't have the tag " + payload1).withColor(TextColor.RED));
                        return;
                    }
                    user.data().clear(
                            node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
                    Tag tag = config.tags.get(payload1);
                    String sb = "<hover:\"" +
                            tag.description +
                            "\">" +
                            tag.prefix +
                            "</hover>";
                    PrefixNode prefixNode = PrefixNode.builder(sb, 10).build();
                    user.data().add(prefixNode);
                }, Component.literal("Successfully assigned tag " + payload1 + " to " + payload2));
                break;
            default:
                throw new SimpleCommandExceptionType(() -> "Invalid action: " + action).create();
        }
        return Command.SINGLE_SUCCESS;
    }

    private int run4Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        String payload1 = StringArgumentType.getString(context, "payload1");
        String payload2 = StringArgumentType.getString(context, "payload2");
        String payload3 = StringArgumentType.getString(context, "payload3");
        CommandSourceStack source = context.getSource();
        if (!action.equals("create")) {
            throw new SimpleCommandExceptionType(() -> "Invalid action: " + action).create();
        }
        config.tags.put(payload1, new Tag(payload2, null));
        source.sendSystemMessage(literalParser.parseNode(
                "Successfully created tag (id " + payload1 + "<reset>) " + payload2 + "<reset> with description " + payload3
        ).toComponent());
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<UUID> getUUIDFromName(String name) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        // Status 204 means the player does not exist
                        if (response.statusCode() == 204) return null;

                        MojangProfile profile = new Gson().fromJson(response.body(), MojangProfile.class);
                        return parseUUID(profile.id);
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static UUID parseUUID(String uuidString) {
        return UUID.fromString(uuidString.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    static class MojangProfile {
        public String id;   // The UUID (un-dashed)
        public String name; // The exact username
    }
}
