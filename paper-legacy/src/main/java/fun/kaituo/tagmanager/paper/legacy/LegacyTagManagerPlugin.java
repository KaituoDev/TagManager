package fun.kaituo.tagmanager.paper.legacy;

import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagSelectionRequest;
import fun.kaituo.tagmanager.TagSelectionRequestCodec;
import fun.kaituo.tagmanager.paper.AbstractTagManagerPlugin;
import org.bukkit.entity.Player;

public final class LegacyTagManagerPlugin extends AbstractTagManagerPlugin {
    private ChestTagRenderer menus;

    @Override
    protected TagDialogRenderer createRenderer() {
        menus = new ChestTagRenderer(this, this::select);
        getServer().getPluginManager().registerEvents(menus, this);
        if (networkMode()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, TagSelectionRequestCodec.CHANNEL);
        }
        return menus;
    }

    private void select(Player player, TagSelectionRequest selection) {
        if (networkMode()) {
            player.sendPluginMessage(this, TagSelectionRequestCodec.CHANNEL, TagSelectionRequestCodec.encode(selection));
        } else {
            player.performCommand(selection.command());
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.close();
        super.onDisable();
    }
}
