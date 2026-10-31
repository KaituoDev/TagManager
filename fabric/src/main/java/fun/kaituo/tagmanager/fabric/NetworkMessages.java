package fun.kaituo.tagmanager.fabric;

import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public final class NetworkMessages {
    private NetworkMessages() { }

    /** Registers the Velocity-to-Fabric dialog forwarding endpoint. */
    public static void register(TagDialogRenderer renderer) {
        PayloadTypeRegistry.serverboundPlay().register(DialogPayload.TYPE, DialogPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DialogPayload.TYPE, (payload, context) -> {
            TagDialogRequest request = TagDialogRequestCodec.decode(payload.bytes());
            // A proxy may only open a dialog for the player whose connection carried it.
            if (!request.playerId().equals(context.player().getUUID())) return;
            context.server().execute(() -> renderer.show(request));
        });
    }

    record DialogPayload(byte[] bytes) implements CustomPacketPayload {
        private static final Type<DialogPayload> TYPE = new Type<>(Identifier.parse(TagDialogRequestCodec.CHANNEL));
        static final StreamCodec<RegistryFriendlyByteBuf, DialogPayload> STREAM_CODEC =
                CustomPacketPayload.codec(DialogPayload::write, DialogPayload::read);

        private static DialogPayload read(RegistryFriendlyByteBuf buffer) {
            // Velocity sends raw JSON; plugin messages have no byte-array length prefix.
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new DialogPayload(bytes);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBytes(bytes);
        }

        @Override public @NonNull Type<DialogPayload> type() { return TYPE; }
    }

}
