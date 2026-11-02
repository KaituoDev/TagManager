package fun.kaituo.tagmanager;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TagSelectionRequestTest {
    @Test
    void roundTripsAssignmentAndClearing() {
        for (TagSelectionRequest request : new TagSelectionRequest[] {
                new TagSelectionRequest("vip"), new TagSelectionRequest(null)}) {
            assertEquals(request, TagSelectionRequestCodec.decode(TagSelectionRequestCodec.encode(request)));
        }
        assertEquals("tags assign vip", new TagSelectionRequest("vip").command());
        assertEquals("tags unassign", new TagSelectionRequest(null).command());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "vip Alex", "/op", "vip\ntags grant", "<red>vip"})
    void rejectsValuesThatCouldChangeTheCommand(String id) {
        assertThrows(IllegalArgumentException.class, () -> new TagSelectionRequest(id));
        byte[] payload = ("{\"tagId\":" + TagsMap.GSON.toJson(id) + "}").getBytes(StandardCharsets.UTF_8);
        assertThrows(JsonParseException.class, () -> TagSelectionRequestCodec.decode(payload));
    }
}
