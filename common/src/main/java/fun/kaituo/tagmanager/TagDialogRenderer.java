package fun.kaituo.tagmanager;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;

/**
 * Renders the tag UI for one platform.
 *
 * <p>The command module deliberately does not describe a Minecraft dialog. A
 * platform may use a native dialog, an inventory UI, a proxy message, or any
 * other UI mechanism. Implementations must treat the supplied map as a
 * snapshot and must not mutate it.</p>
 */
public interface TagDialogRenderer {
    /** Renders one immutable request. This is the method proxy transports should forward. */
    void show(@NonNull TagDialogRequest request);

    /** Shows the UI used by {@code /tags} to choose or clear an owned tag. */
    default void showTagSelector(@NonNull UUID playerId, @NonNull Map<String, Tag> ownedTags) {
        show(new TagDialogRequest(TagDialogRequest.Type.TAG_SELECTOR, playerId, ownedTags));
    }

    /** Shows the UI used by {@code /tags listall}. */
    default void showAllTags(@NonNull UUID playerId, @NonNull Map<String, Tag> tags) {
        show(new TagDialogRequest(TagDialogRequest.Type.ALL_TAGS, playerId, tags));
    }
}
