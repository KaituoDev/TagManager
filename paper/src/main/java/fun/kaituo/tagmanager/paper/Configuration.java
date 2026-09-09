package fun.kaituo.tagmanager.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

final class Configuration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger logger;
    private ModConfig config = new ModConfig();

    Configuration(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void load() {
        if (!file.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            config = loaded == null ? new ModConfig() : loaded;
        } catch (IOException | RuntimeException exception) {
            logger.error("Failed to load configuration file", exception);
        }
    }

    void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.error("Failed to create plugin data directory {}", parent);
            return;
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(config, writer);
        } catch (IOException exception) {
            logger.error("Failed to save configuration file", exception);
        }
    }

    ModConfig get() {
        return config;
    }

    static final class ModConfig {
        boolean network = false;
    }
}
