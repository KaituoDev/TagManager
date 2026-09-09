package fun.kaituo.tagmanager;

import java.nio.charset.StandardCharsets;

/** Wire representation shared by proxy dialog forwarders and Fabric receivers. */
public final class TagDialogRequestCodec {
    /** The Velocity-to-backend plugin-message channel. */
    public static final String CHANNEL = "tag-manager:dialog";

    private TagDialogRequestCodec() { }

    public static byte[] encode(TagDialogRequest request) {
        return TagsMap.GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
    }

    public static TagDialogRequest decode(byte[] payload) {
        return TagsMap.GSON.fromJson(new String(payload, StandardCharsets.UTF_8), TagDialogRequest.class);
    }
}
