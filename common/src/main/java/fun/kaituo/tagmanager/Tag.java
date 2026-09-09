package fun.kaituo.tagmanager;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Tag {
    @NonNull
    public String prefix;
    @Nullable
    public String description;

    public Tag(@NonNull String prefix, @Nullable String description) {
        this.prefix = prefix;
        this.description = description;
    }
}
