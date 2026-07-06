package fun.kaituo.tagmanager;


import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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
    private final HashMap<String, String> tags;
    private final TagConfiguration config;

    public TagCommands(HashMap<String, String> tags, TagConfiguration config, Logger logger) {
        this.tags = tags;
        this.config = config;
        this.logger = logger;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tags").executes(this::run0Args)
                    .then(Commands.argument("action", StringArgumentType.word()).executes(this::run1Args)
                            .then(Commands.argument("payload1", StringArgumentType.word()).executes(this::run2Args)
                                    .then(Commands.argument("payload2", StringArgumentType.word()).executes(this::run3Args)))));
        });
    }

    private int run0Args(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("Usage:").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags list").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags reload").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags create <id> <format>").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags delete <id>").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags assign <player> <id>").withColor(TextColor.AQUA));
        source.sendSystemMessage(Component.literal("/tags unassign <player>").withColor(TextColor.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private int run1Args(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String action = StringArgumentType.getString(context, "action");
        CommandSourceStack source = context.getSource();
        switch (action) {
            case "list":
                source.sendSystemMessage(Component.literal("All tags registered:"));
                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    source.sendSystemMessage(Component.literal(entry.getKey() + ": " + entry.getValue()));
                }
                break;
            case "reload":
                config.reload();
                source.sendSystemMessage(Component.literal("Tags reloaded!").withColor(TextColor.YELLOW));
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
                String removed = tags.remove(payload1);
                if (removed == null) {
                    source.sendSystemMessage(Component.literal("No tag with id " + payload1 + " exists").withColor(TextColor.RED));
                }
                break;
            case "unassign":
                UserManager userManager = LP.getUserManager();
                CompletableFuture<User> userFuture =
                        getUUIDFromName(payload1).thenComposeAsync(userManager::loadUser);
                userFuture.whenCompleteAsync((user, exception) -> {
                    if (exception != null) {
                        logger.error("An exception occurred when getting User from LuckPerms", exception);
                        source.sendSystemMessage(
                                Component.literal("An exception occurred when getting User from LuckPerms.")
                                        .withColor(TextColor.RED));
                        return;
                    }
                    user.data().clear(node -> node.getType() == NodeType.PREFIX && ((PrefixNode) node).getPriority() >= 10);
                    userManager.saveUser(user);
                    source.sendSystemMessage(Component.literal("Cleared all custom tags of player " + payload1));
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
        switch (action) {
            case "create":
                tags.put(payload1, payload2);
                break;
            case "assign":
                UserManager userManager = LP.getUserManager();
                CompletableFuture<User> userFuture =
                        getUUIDFromName(payload1).thenComposeAsync(userManager::loadUser);
                userFuture.whenCompleteAsync((user, exception) -> {
                    CommandSourceStack source = context.getSource();
                    if (exception != null) {
                        logger.error("An exception occurred when getting User from LuckPerms", exception);
                        source.sendSystemMessage(
                                Component.literal("An exception occurred when getting User from LuckPerms.")
                                        .withColor(TextColor.RED));
                        return;
                    }
                    PrefixNode prefixNode = PrefixNode.builder(tags.get(payload1), 10).build();
                    user.data().add(prefixNode);
                    userManager.saveUser(user);
                });
                break;
            default:
                throw new SimpleCommandExceptionType(() -> "Invalid action: " + action).create();
        }
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
