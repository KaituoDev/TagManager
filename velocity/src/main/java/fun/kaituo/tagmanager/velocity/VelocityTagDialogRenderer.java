package fun.kaituo.tagmanager.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagDialogRequestCodec;

/** Forwards dialog requests to the Fabric server the player is currently on. */
final class VelocityTagDialogRenderer implements TagDialogRenderer {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(TagDialogRequestCodec.CHANNEL);
    private final ProxyServer server;

    VelocityTagDialogRenderer(ProxyServer server) { this.server = server; }

    @Override public void show(TagDialogRequest request) {
        server.getPlayer(request.playerId())
                .flatMap(player -> player.getCurrentServer())
                .ifPresent(connection -> connection.sendPluginMessage(CHANNEL, TagDialogRequestCodec.encode(request)));
    }
}
