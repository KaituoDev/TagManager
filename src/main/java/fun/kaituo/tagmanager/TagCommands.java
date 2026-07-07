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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class TagCommands {
    private static final LuckPerms LP = LuckPermsProvider.get();
    private final Logger logger;
    private final TagConfiguration config;
    private final NodeParser literalParser = TagParser.createSimplifiedTextFormat();

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

    private int run0Args(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("Usage:").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags list").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags reload").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags create <id> <format> [description]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags delete <id>").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags grant <player> <id>").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags revoke <player> [id]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags assign <id> [player]").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags unassign [player]").withColor(TextColor.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private int run1Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        switch (action) {
            case "list":
                if (player == null) {
                    source.sendSystemMessage(Component.literal(
                            "The assign command without an assignee can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                String playerName = player.getPlainTextName().toLowerCase();
                ArrayList<String> obtained = config.getPlayerObtained().get(playerName);
                HashMap<String, Tag> allTags = config.getTags();
                List<Map.Entry<String, Tag>> obtainedTags = allTags.entrySet().stream().filter(e -> obtained.contains(e.getKey())).toList();
                if (obtainedTags.isEmpty()) {
                    source.sendSystemMessage(Component.literal("You have no obtained_ tags"));
                }
                source.sendSystemMessage(Component.literal("All tags obtained_:"));
                for (Map.Entry<String, Tag> entry : obtainedTags) {
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
                if (player == null) {
                    source.sendSystemMessage(Component.literal(
                            "The assign command without an assignee can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                UserManager userManager = LP.getUserManager();
                User user = userManager.getUser(player.getUUID());
                if (user == null) {
                    source.sendSystemMessage(
                            Component.literal("An exception occurred when getting User from LuckPerms.")
                                    .withColor(TextColor.RED));
                    break;
                }
                user.data().clear(node -> node.getType() == NodeType.PERMISSION && ((PermissionNode) node).getPermission().startsWith("kaituotags.tag"));
                userManager.saveUser(user);
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
        UserManager userManager = LP.getUserManager();
        switch (action) {
            case "delete":
                Tag removed = config.getTags().remove(payload1);
                if (removed == null) {
                    source.sendSystemMessage(Component.literal("No tag with id " + payload1 + " exists").withColor(TextColor.RED));
                }
                source.sendSystemMessage(Component.literal("Successfully removed tag " + payload1));
                break;
            case "revoke":
                ArrayList<String> obtained = config.getPlayerObtained().computeIfAbsent(payload1.toLowerCase(), k -> new ArrayList<>());
                obtained.clear();
                source.sendSystemMessage(Component.literal("Successfully revoked all tags granted to " + payload1));
                break;
            case "assign":
                ServerPlayer player = source.getPlayer();
                if (player == null) {
                    source.sendSystemMessage(Component.literal(
                            "The assign command without an assignee can only be run by a player!").withColor(TextColor.RED));
                    break;
                }
                if (!config.getTags().containsKey(payload1)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload1 + " not found").withColor(TextColor.RED));
                    break;
                }
                User user = userManager.getUser(player.getUUID());
                if (user == null) {
                    source.sendSystemMessage(
                            Component.literal("An exception occurred when getting User from LuckPerms.")
                                    .withColor(TextColor.RED));
                    break;
                }
                ArrayList<String> obtained_ = config.getPlayerObtained().get(player.getPlainTextName().toLowerCase());
                if (!obtained_.contains(payload1)) {
                    source.sendSystemMessage(Component.literal("You don't have the tag " + payload1).withColor(TextColor.RED));
                    break;
                }
                PermissionNode tagNode = PermissionNode.builder("kaituotags.tag." + payload1).value(true).build();
                user.data().add(tagNode);
                userManager.saveUser(user);
                source.sendSystemMessage(Component.literal("Successfully assigned tag " + payload1 + " to you"));
                break;
            case "unassign":
                CompletableFuture<User> userFuture =
                        getUUIDFromName(payload1).thenComposeAsync(userManager::loadUser);
                userFuture.whenCompleteAsync((user_, exception) -> {
                    if (exception != null) {
                        logger.error("An exception occurred when getting User from LuckPerms", exception);
                        source.sendSystemMessage(
                                Component.literal("An exception occurred when getting User from LuckPerms.")
                                        .withColor(TextColor.RED));
                        return;
                    }
                    user_.data().clear(node -> node.getType() == NodeType.PERMISSION && ((PermissionNode) node).getPermission().startsWith("kaituotags.tag"));
                    userManager.saveUser(user_);
                    source.sendSystemMessage(Component.literal("Cleared currently assigned tag of player " + payload1));
                });
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
                config.getTags().put(payload1, new Tag(payload2, null));
                source.sendSystemMessage(literalParser.parseNode(
                        "Successfully created tag (id " + payload1 + "<reset>) " + payload2).toComponent());
                break;
            case "grant":
                if (!config.getTags().containsKey(payload2)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload2 + " not found").withColor(TextColor.RED));
                    break;
                }
                ArrayList<String> obtained = config.getPlayerObtained().computeIfAbsent(payload1.toLowerCase(), k -> new ArrayList<>());
                obtained.add(payload2);
                source.sendSystemMessage(Component.literal("Successfully granted tag " + payload2 + " to " + payload1));
                break;
            case "revoke":
                if (!config.getTags().containsKey(payload2)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload2 + " not found").withColor(TextColor.RED));
                    break;
                }
                ArrayList<String> obtained_ = config.getPlayerObtained().computeIfAbsent(payload1.toLowerCase(), k -> new ArrayList<>());
                obtained_.remove(payload2);
                source.sendSystemMessage(Component.literal("Successfully revoked tag " + payload2 + " from " + payload1));
                break;
            case "assign":
                if (!config.getTags().containsKey(payload1)) {
                    source.sendSystemMessage(Component.literal("Tag " + payload1 + " not found").withColor(TextColor.RED));
                    break;
                }
                UserManager userManager = LP.getUserManager();
                CompletableFuture<User> userFuture =
                        getUUIDFromName(payload2).thenComposeAsync(userManager::loadUser);
                userFuture.whenCompleteAsync((user, exception) -> {
                    if (exception != null) {
                        logger.error("An exception occurred when getting User from LuckPerms", exception);
                        source.sendSystemMessage(
                                Component.literal("An exception occurred when getting User from LuckPerms.")
                                        .withColor(TextColor.RED));
                        return;
                    }
                    ArrayList<String> obtained__ = config.getPlayerObtained().get(payload2);
                    if (!obtained__.contains(payload1)) {
                        source.sendSystemMessage(Component.literal("The player " + payload2 + " doesn't have the tag " + payload1).withColor(TextColor.RED));
                        return;
                    }
                    PermissionNode tagNode = PermissionNode.builder("kaituotags.tag." + payload1).value(true).build();
                    user.data().add(tagNode);
                    userManager.saveUser(user);
                    source.sendSystemMessage(Component.literal("Successfully assigned tag " + payload1 + " to " + payload2));
                });
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
        config.getTags().put(payload1, new Tag(payload2, null));
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
