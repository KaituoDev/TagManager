package fun.kaituo.tagmanager.paper;

import fun.kaituo.tagmanager.TagDialogRenderer;

public final class TagManagerPlugin extends AbstractTagManagerPlugin {
    @Override
    protected TagDialogRenderer createRenderer() {
        return new PaperTagDialogRenderer(getServer());
    }
}
