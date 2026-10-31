package fun.kaituo.tagmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class TagsMapTest {
    @TempDir Path directory;

    @Test
    void migratesLegacyPrefixesAndDescriptionsOnLoadReloadAndSave() throws Exception {
        Path file = directory.resolve("tags.json");
        Files.writeString(file, """
                {"vip":{"prefix":"§cVIP","description":"§lDescription"},
                 "modern":{"prefix":"<green>Modern</green>"}}
                """);
        TagsMap storage = new TagsMap(file.toFile(), new HashMap<>(), null);

        storage.load();

        assertEquals("<red>VIP</red>", storage.tags.get("vip").prefix);
        assertEquals("<bold>Description</bold>", storage.tags.get("vip").description);
        assertEquals("<green>Modern</green>", storage.tags.get("modern").prefix);
        assertNull(storage.tags.get("modern").description);

        Files.writeString(file, """
                {"vip":{"prefix":"§9Reloaded"}}
                """);
        storage.replace(storage.reloadAsync().join());
        assertEquals("<blue>Reloaded</blue>", storage.tags.get("vip").prefix);

        storage.save();
        assertFalse(Files.readString(file).contains("§"));
        assertTrue(Files.readString(file).contains("<blue>Reloaded</blue>"));
        HashMap<String, Tag> saved = TagsMap.GSON.fromJson(Files.readString(file),
                new com.google.gson.reflect.TypeToken<HashMap<String, Tag>>() { }.getType());
        assertEquals("<blue>Reloaded</blue>", saved.get("vip").prefix);
    }

    @Test
    void migratesLegacyTagDataReceivedFromAProxy() {
        String payload = """
                {"type":"ALL_TAGS","playerId":"00000000-0000-0000-0000-000000000001",
                 "tags":{"vip":{"prefix":"§cVIP","description":"§eDescription"}}}
                """;

        TagDialogRequest request = TagDialogRequestCodec.decode(payload.getBytes(StandardCharsets.UTF_8));

        assertEquals("<red>VIP</red>", request.tags().get("vip").prefix);
        assertEquals("<yellow>Description</yellow>", request.tags().get("vip").description);
        assertFalse(new String(TagDialogRequestCodec.encode(request), StandardCharsets.UTF_8).contains("§"));
    }
}
