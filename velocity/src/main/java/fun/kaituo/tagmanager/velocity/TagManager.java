package fun.kaituo.tagmanager.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import fun.kaituo.tagmanager.TagSelectionRequest;
import fun.kaituo.tagmanager.TagSelectionRequestCodec;
import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagsMap;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;

@Plugin(id = BuildConstants.ID, name = "Tag Manager", version = BuildConstants.VERSION, authors = {"LeChocolatChaud"})
public class TagManager {
    private static final MinecraftChannelIdentifier SELECTION_CHANNEL = MinecraftChannelIdentifier.from(TagSelectionRequestCodec.CHANNEL);

    private final ProxyServer server;
    private final Logger logger;
    private final TagsMap config;
    private static final HashMap<String, Tag> tags = new HashMap<>();

    @Inject
    public TagManager(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;

        File configFile = new File(dataDirectory.toFile(), "tags.json");
        this.config = new TagsMap(configFile, tags, logger);
        config.load();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(MinecraftChannelIdentifier.from(TagDialogRequestCodec.CHANNEL));
        server.getChannelRegistrar().register(SELECTION_CHANNEL);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        new VelocityTagCommands(config, logger, new VelocityTagDialogRenderer(server), server).register(dispatcher);
        @SuppressWarnings("unchecked")
        LiteralCommandNode<CommandSource> tagsCommand = (LiteralCommandNode<CommandSource>) dispatcher.getRoot().getChild("tags");
        server.getCommandManager().register(new BrigadierCommand(tagsCommand));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!SELECTION_CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)
                || !(event.getTarget() instanceof Player player)
                || !connection.getPlayer().getUniqueId().equals(player.getUniqueId())
                || player.getCurrentServer().orElse(null) != connection) return;
        try {
            TagSelectionRequest selection = TagSelectionRequestCodec.decode(event.getData());
            if (selection == null) return;
            server.getCommandManager().executeAsync(player, selection.command()).exceptionally(failure -> {
                logger.error("Failed to apply tag selection for {}", player.getUsername(), failure);
                return false;
            });
        } catch (JsonParseException | IllegalArgumentException exception) {
            logger.warn("Discarding malformed tag selection from {}", player.getUsername(), exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Proxy stopping... saving tags.");
        config.save();
    }
}
