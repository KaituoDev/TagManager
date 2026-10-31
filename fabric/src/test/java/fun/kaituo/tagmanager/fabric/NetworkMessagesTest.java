package fun.kaituo.tagmanager.fabric;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagDialogRequestCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessagesTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @ParameterizedTest
    @EnumSource(TagDialogRequest.Type.class)
    void decodesRawProxyJsonWithoutALengthPrefix(TagDialogRequest.Type type) {
        Tag tag = new Tag("<gold>称号</gold>", "A description longer than the JSON's opening byte value: " + "x".repeat(200));
        TagDialogRequest request = new TagDialogRequest(type, PLAYER_ID, Map.of("vip", tag));
        byte[] proxyMessage = TagDialogRequestCodec.encode(request);
        RegistryFriendlyByteBuf buffer = buffer(proxyMessage);
        try {
            NetworkMessages.DialogPayload payload = NetworkMessages.DialogPayload.STREAM_CODEC.decode(buffer);

            assertArrayEquals(proxyMessage, payload.bytes());
            assertEquals(TagDialogRequestCodec.CHANNEL, payload.type().id().toString());
            assertFalse(buffer.isReadable(), "The codec must consume the entire plugin message");
            TagDialogRequest decoded = TagDialogRequestCodec.decode(payload.bytes());
            assertEquals(type, decoded.type());
            assertEquals(PLAYER_ID, decoded.playerId());
            assertEquals(tag.prefix, decoded.tags().get("vip").prefix);
            assertEquals(tag.description, decoded.tags().get("vip").description);
        } finally {
            buffer.release();
        }
    }

    @Test
    void decodesSelectorForAPlayerWithNoTags() {
        TagDialogRequest request = new TagDialogRequest(TagDialogRequest.Type.TAG_SELECTOR, PLAYER_ID, Map.of());
        RegistryFriendlyByteBuf buffer = buffer(TagDialogRequestCodec.encode(request));
        try {
            NetworkMessages.DialogPayload payload = NetworkMessages.DialogPayload.STREAM_CODEC.decode(buffer);

            assertEquals(request, TagDialogRequestCodec.decode(payload.bytes()));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void encodesTheSameRawJsonAsTheProxy() {
        TagDialogRequest request = new TagDialogRequest(TagDialogRequest.Type.ALL_TAGS, PLAYER_ID,
                Map.of("vip", new Tag("<gold>VIP</gold>", null)));
        byte[] proxyMessage = TagDialogRequestCodec.encode(request);
        RegistryFriendlyByteBuf buffer = buffer(new byte[0]);
        try {
            NetworkMessages.DialogPayload.STREAM_CODEC.encode(buffer, new NetworkMessages.DialogPayload(proxyMessage));

            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            assertArrayEquals(proxyMessage, encoded);
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer(byte[] bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer().writeBytes(bytes), RegistryAccess.EMPTY);
    }
}
