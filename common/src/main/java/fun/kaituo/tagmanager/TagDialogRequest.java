package fun.kaituo.tagmanager;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;

/** A platform-independent request to render a tag UI for a player. */
public record TagDialogRequest(@NonNull Type type, @NonNull UUID playerId, @NonNull Map<String, Tag> tags) {
    public TagDialogRequest {
        tags = Map.copyOf(tags);
    }

    public enum Type {
        TAG_SELECTOR,
        ALL_TAGS
    }
}
