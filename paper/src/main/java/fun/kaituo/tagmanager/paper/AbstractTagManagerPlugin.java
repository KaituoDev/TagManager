package fun.kaituo.tagmanager.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import fun.kaituo.tagmanager.TagsMap;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;

/** Shared lifecycle for the native-dialog and legacy-inventory Paper builds. */
public abstract class AbstractTagManagerPlugin extends JavaPlugin {
    private Configuration configuration;
    private TagsMap tags;

    protected abstract TagDialogRenderer createRenderer();

    protected final boolean networkMode() {
        return configuration.get().network;
    }

    @Override
    public void onEnable() {
        configuration = new Configuration(new File(getDataFolder(), "config.json"), getSLF4JLogger());
        configuration.load();

        TagDialogRenderer dialogs = createRenderer();
        if (configuration.get().network) {
            NetworkMessages messages = new NetworkMessages(dialogs, getSLF4JLogger(), this);
            getServer().getMessenger().registerIncomingPluginChannel(this, TagDialogRequestCodec.CHANNEL, messages);
            getSLF4JLogger().info("Network mode enabled; Velocity owns commands and tag data");
            return;
        }

        tags = new TagsMap(new File(getDataFolder(), "tags.json"), new HashMap<String, Tag>(), getSLF4JLogger());
        tags.load();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            new PaperTagCommands(tags, getSLF4JLogger(), dialogs, this).register(dispatcher);
            @SuppressWarnings("unchecked")
            LiteralCommandNode<CommandSourceStack> command =
                    (LiteralCommandNode<CommandSourceStack>) dispatcher.getRoot().getChild("tags");
            event.registrar().register(command);
        });
    }

    @Override
    public void onDisable() {
        // Shutdown persistence is deliberately synchronous so server exit cannot outrun writes.
        if (tags != null) tags.save();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }
}
