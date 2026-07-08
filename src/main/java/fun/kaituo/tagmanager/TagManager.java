package fun.kaituo.tagmanager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class TagManager implements ModInitializer {
    public static final String MOD_ID = "tag-manager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final HashMap<String, Tag> tags = new HashMap<>();

    @Override
    public void onInitialize() {
        TagConfiguration config = new TagConfiguration(tags, LOGGER);
        TagCommands commands = new TagCommands(config, LOGGER);
        config.load();
        commands.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> {
            LOGGER.info("Server stopping... saving mod config.");
            config.save();
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
