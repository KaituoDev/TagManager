package fun.kaituo.tagmanager;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagParser;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public class TagPlaceholders {
    private final TagConfiguration config;
    private final NodeParser literalParser = TagParser.createSimplifiedTextFormat();
    private final LuckPerms LP = LuckPermsProvider.get();

    public TagPlaceholders(TagConfiguration config) {
        this.config = config;
    }

    public void registerPlaceholders() {
        Placeholders.registerCommon(
                Identifier.fromNamespaceAndPath("kaituotags", "tag"), this::parseTagPlaceholder
        );
    }

    public PlaceholderResult parseTagPlaceholder(PlaceholderContext context, String arg) {
        if (!context.hasPlayer()) return PlaceholderResult.invalid();
        Player player = context.player();
        assert player != null;
        UserManager userManager = LP.getUserManager();
        User user = userManager.getUser(player.getUUID());
        if (user == null) {
            return PlaceholderResult.invalid();
        }
        PermissionNode permNode = user.getNodes(NodeType.PERMISSION).stream().filter(node ->
                        node.getPermission().startsWith("kaituotags.tag") ||
                                node.getPermission().startsWith("eternaltags.tag") ||
                                node.getPermission().startsWith("deluxetags.tag"))
                .findFirst().orElse(PermissionNode.builder("kaituotags.tag.empty").build());
        String[] permParts = permNode.getPermission().split("\\.");
        String tagId = permParts[permParts.length - 1];
        if (tagId.equals("empty")) {
            return PlaceholderResult.value("");
        }
        if (!config.getTags().containsKey(tagId)) {
            return PlaceholderResult.value("");
        }
        Tag tag = config.getTags().get(tagId);
        return PlaceholderResult.value(
            literalParser.parseNode("<hover:" + tag.description + ">" + tag.prefix + "</hover>").toComponent()
        );
    }
}
