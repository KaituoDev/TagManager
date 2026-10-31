package fun.kaituo.tagmanager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TagCommandsTest {
    private final UUID playerId = UUID.randomUUID();
    private final List<TagDialogRequest> requests = new ArrayList<>();
    private final List<Component> messages = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();
    private CompletableFuture<User> loadedUser;
    private Field providerInstance;
    private Object previousProvider;
    private Logger logger;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        loadedUser = new CompletableFuture<>();
        UserManager users = stub(UserManager.class, (_, method, _) -> {
            if (method.getName().equals("loadUser")) return loadedUser;
            throw new AssertionError(method);
        });
        GroupManager groups = stub(GroupManager.class, (_, method, _) -> {
            throw new AssertionError(method);
        });
        LuckPerms api = stub(LuckPerms.class, (_, method, _) -> switch (method.getName()) {
            case "getUserManager" -> users;
            case "getGroupManager" -> groups;
            default -> throw new AssertionError(method);
        });
        providerInstance = LuckPermsProvider.class.getDeclaredField("instance");
        providerInstance.setAccessible(true);
        previousProvider = providerInstance.get(null);
        providerInstance.set(null, api);
        logger = stub(Logger.class, (_, method, args) -> {
            if (method.getName().equals("error")) {
                failures.add((Throwable) args[args.length - 1]);
                return null;
            }
            throw new AssertionError(method);
        });
    }

    @AfterEach
    void restoreProvider() throws IllegalAccessException {
        providerInstance.set(null, previousProvider);
    }

    @Test
    void opensSelectorWhenUserFinishesLoading() throws Exception {
        runTags(requests::add);
        assertTrue(requests.isEmpty());

        loadedUser.complete(emptyUser());

        assertEquals(1, requests.size());
        assertEquals(TagDialogRequest.Type.TAG_SELECTOR, requests.getFirst().type());
        assertEquals(playerId, requests.getFirst().playerId());
        assertTrue(failures.isEmpty());
        assertTrue(messages.isEmpty());
    }

    @Test
    void reportsMissingRendererDependency() throws Exception {
        NoClassDefFoundError missingBridge = new NoClassDefFoundError("MinecraftServerAudiences");
        runTags(_ -> { throw missingBridge; });

        loadedUser.complete(emptyUser());

        assertFailureReported(missingBridge);
    }

    @Test
    void reportsUserLoadFailure() throws Exception {
        runTags(requests::add);
        RuntimeException loadFailure = new RuntimeException("LuckPerms storage unavailable");

        loadedUser.completeExceptionally(loadFailure);

        assertTrue(requests.isEmpty());
        assertFailureReported(loadFailure);
    }

    @Test
    void listAllIncludesTagsThePlayerDoesNotOwn() throws Exception {
        HashMap<String, Tag> registered = new HashMap<>();
        registered.put("unowned", new Tag("<gold>Unowned</gold>", null));
        TagsMap tags = new TagsMap(new File("unused-tags.json"), registered, logger);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        new TestCommands(tags, requests::add).register(dispatcher);

        assertEquals(1, dispatcher.execute("tags listall", new Object()));

        assertEquals(1, requests.size());
        assertEquals(TagDialogRequest.Type.ALL_TAGS, requests.getFirst().type());
        assertEquals(registered, requests.getFirst().tags());
        assertFalse(loadedUser.isDone());
        assertTrue(failures.isEmpty());
    }

    private void assertFailureReported(Throwable expected) {
        assertEquals(1, failures.size());
        Throwable cause = failures.getFirst();
        while (cause.getCause() != null) cause = cause.getCause();
        assertSame(expected, cause);
        assertEquals(1, messages.size());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(messages.getFirst())
                .contains("Failed to open tag selector"));
    }

    private void runTags(TagDialogRenderer renderer) throws Exception {
        TagsMap tags = new TagsMap(new File("unused-tags.json"), new HashMap<>(), logger);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        new TestCommands(tags, renderer).register(dispatcher);
        assertEquals(1, dispatcher.execute("tags", new Object()));
    }

    private static User emptyUser() {
        return stub(User.class, (_, method, _) -> {
            if (method.getName().equals("getNodes")) return List.of();
            throw new AssertionError(method);
        });
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private class TestCommands extends TagCommands<Object> {
        TestCommands(TagsMap tags, TagDialogRenderer renderer) {
            super(tags, TagCommandsTest.this.logger, renderer);
        }

        protected Predicate<Object> checkPermission(String permission) { return _ -> true; }
        protected Audience audience(Object source) {
            return new Audience() {
                @Override public void sendMessage(Component message) { messages.add(message); }
            };
        }
        protected Optional<UUID> playerId(Object source) { return Optional.of(playerId); }
        protected Optional<String> playerName(Object source) { return Optional.of("TestPlayer"); }
        protected boolean isOperator(Object source) { return false; }
        protected Collection<String> playerNames(Object source) { return List.of(); }
        protected Message commandTooltip(Component component) { return () -> ""; }
        protected Executor mainThreadExecutor() { return Runnable::run; }
    }
}
