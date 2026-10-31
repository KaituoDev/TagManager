package fun.kaituo.tagmanager.fabric;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagsMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;

public class TagManager implements ModInitializer {
    public static final String MOD_ID = BuildConstants.ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final HashMap<String, Tag> tags = new HashMap<>();
    private volatile net.minecraft.server.MinecraftServer server;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("TagManager");
        File configFile = new File(configDir.toFile(), "config.json");
        Configuration config = new Configuration(configFile, LOGGER);
        config.load();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.server = server);
        StandaloneTagDialogRenderer dialogRenderer = new StandaloneTagDialogRenderer(() -> server);
        if (!config.getConfig().network) {
            File tagsFile = new File(configDir.toFile(), "tags.json");
            TagsMap tagsMap = new TagsMap(tagsFile, tags, LOGGER);
            tagsMap.load();
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    new FabricTagCommands(tagsMap, LOGGER, dialogRenderer, () -> server).register(dispatcher));
            ServerLifecycleEvents.SERVER_STOPPING.register(_ -> {
                LOGGER.info("Server stopping... saving tags.");
                tagsMap.save();
            });
        } else {
            // In network mode Velocity owns commands/data and forwards only render requests.
            NetworkMessages.register(dialogRenderer);
        }
    }

    @SuppressWarnings("unused")
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
