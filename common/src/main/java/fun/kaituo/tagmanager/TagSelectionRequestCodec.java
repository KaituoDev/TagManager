package fun.kaituo.tagmanager;

import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;

public final class TagSelectionRequestCodec {
    public static final String CHANNEL = "tag-manager:selection";

    private TagSelectionRequestCodec() { }

    public static byte[] encode(TagSelectionRequest request) {
        return TagsMap.GSON.toJson(request).getBytes(StandardCharsets.UTF_8);
    }

    public static TagSelectionRequest decode(byte[] payload) {
        try {
            return TagsMap.GSON.fromJson(new String(payload, StandardCharsets.UTF_8), TagSelectionRequest.class);
        } catch (RuntimeException exception) {
            // Gson wraps failures from record constructors in RuntimeException.
            throw new JsonParseException("Invalid tag selection", exception);
        }
    }
}
