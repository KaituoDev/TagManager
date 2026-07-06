package fun.kaituo.tagmanager;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class TagManager implements ModInitializer {
    public static final String MOD_ID = "tag-manager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final HashMap<String, String> tags = new HashMap<>();
    public static final TagConfiguration config = new TagConfiguration(tags, LOGGER);
    public static final TagCommands commands = new TagCommands(tags, config);

    @Override
    public void onInitialize() {
        config.load();
        commands.register();
    }



    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
