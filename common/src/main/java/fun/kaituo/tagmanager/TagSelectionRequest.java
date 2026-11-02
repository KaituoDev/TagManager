package fun.kaituo.tagmanager;

import org.jspecify.annotations.Nullable;

/** A chest selection; the transport connection supplies the player's identity. */
public record TagSelectionRequest(@Nullable String tagId) {
    public TagSelectionRequest {
        if (tagId != null && !tagId.matches("[A-Za-z0-9_+.-]+")) {
            throw new IllegalArgumentException("Invalid tag ID");
        }
    }

    public String command() {
        return tagId == null ? "tags unassign" : "tags assign " + tagId;
    }
}
