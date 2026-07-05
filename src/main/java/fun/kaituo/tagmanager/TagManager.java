package fun.kaituo.tagmanager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TagManager implements ModInitializer {
    public static final String MOD_ID = "tag-manager";
    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tag-manager.json");
    private static final Type tagListType = new TypeToken<ArrayList<Tag>>() {
    }.getType();
    public static final ArrayList<Tag> tags = new ArrayList<>();

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        Gson gson = new Gson();
        ArrayList<Tag> loadedTags = load(gson);
        tags.addAll(loadedTags);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
//            dispatcher.register(Commands.literal("tags").executes(context -> {
//                context.getSource().sendSuccess(() -> Component.literal("Called /tags."), false);
//                return 1;
//            }));
        });
    }

    public static ArrayList<Tag> load(Gson gson) {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return gson.fromJson(reader, tagListType);
            } catch (IOException e) {
                System.err.println("Failed to load configuration file!");
            }
        } else {
            save(gson, new ArrayList<>()); // Create the default file if it doesn't exist
        }
        return new ArrayList<>();
    }

    public static void save(Gson gson, ArrayList<Tag> tags) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(tags, writer);
        } catch (IOException e) {
            System.err.println("Failed to save configuration file!");
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
