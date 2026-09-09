package fun.kaituo.tagmanager.fabric;

import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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

    private record DialogPayload(byte[] bytes) implements CustomPacketPayload {
        private static final Type<DialogPayload> TYPE = CustomPacketPayload.createType(TagDialogRequestCodec.CHANNEL);
        private static final StreamCodec<RegistryFriendlyByteBuf, DialogPayload> STREAM_CODEC =
                CustomPacketPayload.codec(DialogPayload::write, DialogPayload::read);

        private static DialogPayload read(RegistryFriendlyByteBuf buffer) {
            return new DialogPayload(buffer.readByteArray(1_048_576));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeByteArray(bytes);
        }

        @Override public @NonNull Type<DialogPayload> type() { return TYPE; }
    }

}
