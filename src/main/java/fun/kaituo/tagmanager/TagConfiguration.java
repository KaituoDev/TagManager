package fun.kaituo.tagmanager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;

public final class TagConfiguration {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tag-manager.json");
    private static final Type tagListType = new TypeToken<HashMap<String, String>>() {}.getType();
    private final HashMap<String, String> tags;
    private final Logger logger;

    public TagConfiguration(HashMap<String, String> tags, Logger logger) {
        this.tags = tags;
        this.logger = logger;
    }

    private HashMap<String, String> internalLoad() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, tagListType);
            } catch (IOException e) {
                logger.error("Failed to load configuration file!");
            }
        }
        return new HashMap<>();
    }

    public void load() {
        if (CONFIG_FILE.exists()) {
            tags.putAll(internalLoad());
        } else {
            save();
        }
    }

    public void reload() {
        HashMap<String, String> newTags = internalLoad();
        if (!newTags.isEmpty()) {
            tags.clear();
            tags.putAll(newTags);
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(tags, writer);
        } catch (IOException e) {
            logger.error("Failed to save configuration file!");
        }
    }
}
