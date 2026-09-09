package fun.kaituo.tagmanager.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fun.kaituo.tagmanager.Tag;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Configuration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Executor WRITE_EXECUTOR = command -> Thread.ofPlatform().name("tag-manager-config-write-", 0).start(command);
    private final File configFile;
    private ModConfig modConfig;
    private final Logger logger;

    public Configuration(File configFile, Logger logger) {
        this.configFile = configFile;
        this.logger = logger;
        this.modConfig = new ModConfig();
    }

    private ModConfig internalLoad() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                logger.error("Failed to load configuration file!");
            }
        }
        return new ModConfig();
    }

    public void load() {
        if (configFile.exists()) {
            modConfig = internalLoad();
        } else {
            saveAsync();
        }
    }

    public void reload() {
        modConfig = internalLoad();
    }

    public void save() {
        save(modConfig);
    }

    /** Writes a snapshot without holding up the server shutdown thread. */
    public CompletableFuture<Void> saveAsync() {
        ModConfig snapshot = new ModConfig();
        snapshot.network = modConfig.network;
        return CompletableFuture.runAsync(() -> save(snapshot), WRITE_EXECUTOR);
    }

    private void save(ModConfig config) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            logger.error("Failed to save configuration file!");
        }
    }

    public ModConfig getConfig() {
        return modConfig;
    }

    public static class ModConfig {
        public boolean network = false;
    }
}
