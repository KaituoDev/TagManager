package fun.kaituo.tagmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public final class TagConfiguration {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tag-manager.json");
    private final Logger logger;
    private final Config config = new Config();

    public TagConfiguration(Logger logger) {
        this.logger = logger;
    }

    private Config internalLoad() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                logger.error("Failed to load configuration file!");
            }
        }
        return new Config();
    }

    public void load() {
        if (CONFIG_FILE.exists()) {
            Config newConfig = internalLoad();
            config.tags.putAll(newConfig.tags);
            config.obtained.forEach((k, v) -> v.addAll(newConfig.obtained.get(k)));
        } else {
            save();
        }
    }

    public void reload() {
        Config newConfig = internalLoad();
        if (!newConfig.tags.isEmpty()) {
            config.tags.clear();
            config.tags.putAll(newConfig.tags);
        }
        if (!newConfig.obtained.isEmpty()) {
            config.obtained.clear();
            config.obtained.putAll(newConfig.obtained);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            logger.error("Failed to save configuration file!");
        }
    }

    public HashMap<String, Tag> getTags() {
        return config.tags;
    }

    public HashMap<String, ArrayList<String>> getPlayerObtained() {
        return config.obtained;
    }

    public static class Config {
        HashMap<String, Tag> tags;
        @SerializedName("player_obtained")
        HashMap<String, ArrayList<String>> obtained;

        public Config() {
            this.tags = new HashMap<>();
            this.obtained = new HashMap<>();
        }
    }
}
