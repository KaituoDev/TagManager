package fun.kaituo.tagmanager.paper;

import com.google.gson.JsonParseException;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

final class NetworkMessages implements PluginMessageListener {
    private final TagDialogRenderer renderer;
    private final Logger logger;
    private final Plugin plugin;

    NetworkMessages(TagDialogRenderer renderer, Logger logger, Plugin plugin) {
        this.renderer = renderer;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] payload) {
        if (!TagDialogRequestCodec.CHANNEL.equals(channel)) return;
        try {
            TagDialogRequest request = TagDialogRequestCodec.decode(payload);
            // The proxy may only target the player whose connection carried the message.
            if (request == null || request.type() == null || request.playerId() == null || request.tags() == null
                    || !request.playerId().equals(player.getUniqueId())) return;
            Runnable render = () -> renderer.show(request);
            if (Bukkit.isPrimaryThread()) render.run();
            else Bukkit.getScheduler().runTask(plugin, render);
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            logger.warn("Discarding malformed dialog plugin message from {}", player.getName(), exception);
        }
    }
}
