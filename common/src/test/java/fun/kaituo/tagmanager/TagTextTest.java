package fun.kaituo.tagmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TagTextTest {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().hexColors().build();

    @Test
    void usesNamedBalancedColorAndDecorationTags() {
        assertEquals("<red>Red</red>", TagText.convertLegacyCodes("§cRed"));
        assertEquals("<bold>Bold</bold>", TagText.convertLegacyCodes("§lBold"));
        assertEquals("<dark_blue>Blue</dark_blue>", TagText.convertLegacyCodes("§1Blue"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "§0zero§1one§2two§3three§4four§5five§6six§7seven§8eight§9nine§aten§beleven§ctwelve§dthirteen§efourteen§ffifteen",
            "§kObfuscated §lBold §mStrike §nUnderline §oItalic §rPlain",
            "§lBold§cRed§rPlain",
            "§aGreen§lBold green§9Blue",
            "§#12AbEFHex§rPlain",
            "§x§1§2§A§b§E§fHex§rPlain"
    })
    void preservesLegacyColorDecorationAndResetSemantics(String source) {
        Component expected = LEGACY.deserialize(source);
        String migrated = TagText.convertLegacyCodes(source);
        Component actual = MiniMessage.miniMessage().deserialize(migrated);

        assertEquals(LEGACY.serialize(expected), LEGACY.serialize(actual));
        assertFalse(migrated.contains("§"));
        assertEquals(migrated, TagText.convertLegacyCodes(migrated));
    }

    @Test
    void handlesUppercaseCodes() {
        assertEquals(TagText.convertLegacyCodes("§cRed§lBold§rPlain"),
                TagText.convertLegacyCodes("§CRed§LBold§RPlain"));
    }

    @Test
    void leavesModernMarkupAndLiteralSectionSignsAlone() {
        String modern = "<gradient:red:blue>Modern</gradient> &cLiteral §zUnknown §";
        assertSame(modern, TagText.convertLegacyCodes(modern));
    }

    @Test
    void supportsMixedLegacyAndModernMarkup() {
        Component parsed = TagText.miniMessage().deserialize("§aLegacy <bold>modern</bold>");
        assertEquals("§aLegacy §lmodern", LEGACY.serialize(parsed));
    }

    @Test
    void convertsLegacyFormattingInsideHoverText() {
        Component parsed = TagText.miniMessage().deserialize("<hover:show_text:'§cHover'>Text</hover>");
        assertNotNull(parsed.hoverEvent());
        assertEquals("§cHover", LEGACY.serialize((Component) parsed.hoverEvent().value()));
    }

    @Test
    void convertedResetsDoNotRemoveAnAssignedHover() {
        Tag tag = new Tag("§cRed§rPlain", "§eDescription with \"quotes\"");
        Component prefix = TagText.miniMessage().deserialize(tag.prefix)
                .hoverEvent(HoverEvent.showText(TagText.miniMessage().deserialize(tag.description)));
        Component restored = TagText.miniMessage().deserialize(TagText.miniMessage().serialize(prefix));

        assertNotNull(restored.hoverEvent());
        assertEquals("Description with \"quotes\"", PlainTextComponentSerializer.plainText()
                .serialize((Component) restored.hoverEvent().value()));
        assertEquals("RedPlain", PlainTextComponentSerializer.plainText().serialize(restored));
    }
}
