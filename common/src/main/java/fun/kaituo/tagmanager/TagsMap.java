package fun.kaituo.tagmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TagsMap {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Executor READ_EXECUTOR = command -> Thread.ofVirtual().name("tag-manager-read-", 0).start(command);
    private static final Executor WRITE_EXECUTOR = command -> Thread.ofPlatform().name("tag-manager-write-", 0).start(command);
    private final File configFile;
    private final Logger logger;
    public final HashMap<String, Tag> tags;

    public TagsMap(File configFile, HashMap<String, Tag> tagsStorage, Logger logger) {
        this.configFile = configFile;
        this.tags = tagsStorage;
        this.logger = logger;
    }

    private HashMap<String, Tag> internalLoad() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                HashMap<String, Tag> loaded = GSON.fromJson(reader, new TypeToken<HashMap<String, Tag>>(){}.getType());
                return loaded == null ? new HashMap<>() : loaded;
            } catch (IOException e) {
                logger.error("Failed to load configuration file!");
            }
        }
        return new HashMap<>();
    }

    public void load() {
        if (configFile.exists()) {
            tags.putAll(internalLoad());
        } else {
            saveAsync();
        }
    }

    public void reload() {
        HashMap<String, Tag> newTags = internalLoad();
        replace(newTags);
    }

    /** Reads the configuration without mutating the shared tag map. */
    public CompletableFuture<Map<String, Tag>> reloadAsync() {
        return CompletableFuture.supplyAsync(this::internalLoad, READ_EXECUTOR).thenApply(Map::copyOf);
    }

    /** Must be invoked by the platform's main-thread executor. */
    public void replace(Map<String, Tag> newTags) {
        tags.clear();
        tags.putAll(newTags);
    }

    public void save() {
        save(tags);
    }

    /** Snapshots tags before writing so the server thread never waits for disk I/O. */
    public CompletableFuture<Void> saveAsync() {
        Map<String, Tag> snapshot = Map.copyOf(tags);
        return CompletableFuture.runAsync(() -> save(snapshot), WRITE_EXECUTOR);
    }

    private void save(Map<String, Tag> tags) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(tags, writer);
        } catch (IOException e) {
            logger.error("Failed to save configuration file!");
        }
    }
}
