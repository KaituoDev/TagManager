package fun.kaituo.tagmanager.fabric;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagText;
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Minecraft 26.2 native-dialog implementation used by standalone Fabric servers.
 */
final class StandaloneTagDialogRenderer implements TagDialogRenderer {
    private final Supplier<MinecraftServer> server;

    StandaloneTagDialogRenderer(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    @Override
    public void show(TagDialogRequest request) {
        ServerPlayer player = server.get().getPlayerList().getPlayer(request.playerId());
        if (player != null) {
            Dialog dialog = switch (request.type()) {
                case TAG_SELECTOR -> selector(request.tags());
                case ALL_TAGS -> allTags(request.tags());
            };
            player.openDialog(Holder.direct(dialog));
        }
    }

    private Dialog allTags(Map<String, Tag> tags) {
        List<PlainMessage> body = new ArrayList<>();
        tags.forEach((id, tag) -> body.add(new PlainMessage(preview(id, tag), PlainMessage.DEFAULT_WIDTH)));
        CommonDialogData data = new CommonDialogData(Component.literal("All tags registered"), Optional.empty(), true, true, DialogAction.CLOSE, List.copyOf(body), List.of());
        return new NoticeDialog(data, new ActionButton(new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH), Optional.empty()));
    }

    private Dialog selector(Map<String, Tag> tags) {
        CommonDialogData data = new CommonDialogData(Component.literal("Choose a tag to assign"), Optional.empty(), true, true, DialogAction.CLOSE, List.of(), List.of());
        List<ActionButton> buttons = new ArrayList<>();
        tags.forEach((id, tag) -> {
            CommonButtonData button = new CommonButtonData(toNativeComponent(tag.prefix), Optional.ofNullable(tag.description).map(this::toNativeComponent), CommonButtonData.DEFAULT_WIDTH);
            buttons.add(new ActionButton(button, Optional.of(new StaticAction(new ClickEvent.RunCommand("tags assign " + id)))));
        });
        buttons.add(new ActionButton(new CommonButtonData(Component.literal("Clear your tag"), CommonButtonData.DEFAULT_WIDTH), Optional.of(new StaticAction(new ClickEvent.RunCommand("tags unassign")))));
        ActionButton exit = new ActionButton(new CommonButtonData(Component.translatable("gui.back"), CommonButtonData.DEFAULT_WIDTH), Optional.empty());
        return new MultiActionDialog(data, buttons, Optional.of(exit), 1);
    }

    private Component toNativeComponent(String miniMessageFormat) {
        net.kyori.adventure.text.Component advComponent = TagText.miniMessage().deserialize(miniMessageFormat);
        return MinecraftServerAudiences.of(server.get()).nonWrappingSerializer().serialize(advComponent);
    }

    private Component preview(String id, Tag tag) {
        String format = id + "<reset>: " + tag.prefix + (tag.description == null ? "" : "<reset> (" + tag.description + "<reset>)");
        return toNativeComponent(format);
    }
}
