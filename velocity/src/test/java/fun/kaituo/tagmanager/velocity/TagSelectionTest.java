package fun.kaituo.tagmanager.velocity;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import fun.kaituo.tagmanager.TagSelectionRequest;
import fun.kaituo.tagmanager.TagSelectionRequestCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TagSelectionTest {
    @TempDir Path directory;
    private final List<String> executed = new ArrayList<>();
    private final UUID id = UUID.randomUUID();
    private Player player;
    private ServerConnection connection;
    private ServerConnection current;
    private TagManager plugin;

    @BeforeEach
    void setUp() throws Exception {
        player = stub(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getUsername" -> "TestPlayer";
            case "getCurrentServer" -> Optional.ofNullable(current);
            default -> throw new AssertionError(method);
        });
        connection = stub(ServerConnection.class, (proxy, method, args) -> {
            if (method.getName().equals("getPlayer")) return player;
            throw new AssertionError(method);
        });
        current = connection;
        CommandManager commands = stub(CommandManager.class, (proxy, method, args) -> {
            if (method.getName().equals("executeAsync")) {
                assertSame(player, args[0]);
                executed.add((String) args[1]);
                return CompletableFuture.completedFuture(true);
            }
            throw new AssertionError(method);
        });
        ProxyServer server = stub(ProxyServer.class, (proxy, method, args) -> {
            if (method.getName().equals("getCommandManager")) return commands;
            throw new AssertionError(method);
        });
        Files.writeString(directory.resolve("tags.json"), "{}");
        Logger logger = LoggerFactory.getLogger(TagSelectionTest.class);
        plugin = new TagManager(server, logger, directory);
    }

    @Test
    void dispatchesAssignAndClearAsTheConnectedPlayer() {
        for (String tag : new String[] {"vip", null}) {
            PluginMessageEvent event = event(TagSelectionRequestCodec.encode(new TagSelectionRequest(tag)));
            plugin.onPluginMessage(event);
            assertFalse(event.getResult().isAllowed());
        }
        assertEquals(List.of("tags assign vip", "tags unassign"), executed);
    }

    @Test
    void discardsClientOriginatedMessages() {
        PluginMessageEvent event = new PluginMessageEvent(player, connection,
                MinecraftChannelIdentifier.from(TagSelectionRequestCodec.CHANNEL),
                TagSelectionRequestCodec.encode(new TagSelectionRequest("vip")));
        plugin.onPluginMessage(event);
        assertFalse(event.getResult().isAllowed());
        assertTrue(executed.isEmpty());
    }

    @Test
    void discardsMessagesFromAPreviousBackend() {
        current = null;
        PluginMessageEvent event = event(TagSelectionRequestCodec.encode(new TagSelectionRequest("vip")));
        plugin.onPluginMessage(event);
        assertFalse(event.getResult().isAllowed());
        assertTrue(executed.isEmpty());
    }

    @Test
    void discardsMalformedAndInjectedCommands() {
        for (String payload : List.of("{", "null", "{\"tagId\":\"vip SomeoneElse\"}")) {
            PluginMessageEvent event = event(payload.getBytes(StandardCharsets.UTF_8));
            assertDoesNotThrow(() -> plugin.onPluginMessage(event));
            assertFalse(event.getResult().isAllowed());
        }
        assertTrue(executed.isEmpty());
    }

    private PluginMessageEvent event(byte[] payload) {
        return new PluginMessageEvent(connection, player,
                MinecraftChannelIdentifier.from(TagSelectionRequestCodec.CHANNEL), payload);
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
