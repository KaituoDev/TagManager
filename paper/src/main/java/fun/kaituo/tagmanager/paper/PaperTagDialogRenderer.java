package fun.kaituo.tagmanager.paper;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PaperTagDialogRenderer implements TagDialogRenderer {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final Server server;

    PaperTagDialogRenderer(Server server) {
        this.server = server;
    }

    @Override
    public void show(TagDialogRequest request) {
        Player player = server.getPlayer(request.playerId());
        if (player == null) return;

        Dialog dialog = switch (request.type()) {
            case TAG_SELECTOR -> selector(request.tags());
            case ALL_TAGS -> allTags(request.tags());
        };
        player.showDialog(dialog);
    }

    private Dialog allTags(Map<String, Tag> tags) {
        List<DialogBody> body = new ArrayList<>();
        tags.forEach((id, tag) -> body.add(DialogBody.plainMessage(preview(id, tag))));
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("All tags registered"))
                        .body(body)
                        .build())
                .type(DialogType.notice()));
    }

    private Dialog selector(Map<String, Tag> tags) {
        List<ActionButton> buttons = new ArrayList<>();
        tags.forEach((id, tag) -> buttons.add(ActionButton.create(
                parse(tag.prefix),
                tag.description == null ? null : parse(tag.description),
                150,
                DialogAction.staticAction(ClickEvent.runCommand("/tags assign " + id)))));
        buttons.add(ActionButton.create(
                Component.text("Clear your tag"),
                null,
                150,
                DialogAction.staticAction(ClickEvent.runCommand("/tags unassign"))));

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Choose a tag to assign")).build())
                .type(DialogType.multiAction(
                        buttons,
                        ActionButton.create(Component.translatable("gui.back"), null, 150, null),
                        1)));
    }

    private Component preview(String id, Tag tag) {
        return parse(MINI_MESSAGE.escapeTags(id) + "<reset>: " + tag.prefix
                + (tag.description == null ? "" : "<reset> (" + tag.description + "<reset>)"));
    }

    private Component parse(String value) {
        return MINI_MESSAGE.deserialize(value);
    }
}
