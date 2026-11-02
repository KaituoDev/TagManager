package fun.kaituo.tagmanager.paper.legacy;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRenderer;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagSelectionRequest;
import fun.kaituo.tagmanager.TagText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Six-row chest UI, with five rows of tags and one row of controls. */
final class ChestTagRenderer implements TagDialogRenderer, Listener {
    private static final int PAGE_SIZE = 45;
    private final JavaPlugin plugin;
    private final BiConsumer<Player, TagSelectionRequest> select;

    ChestTagRenderer(JavaPlugin plugin, BiConsumer<Player, TagSelectionRequest> select) {
        this.plugin = plugin;
        this.select = select;
    }

    @Override
    public void show(TagDialogRequest request) {
        Player player = plugin.getServer().getPlayer(request.playerId());
        if (player != null) open(player, request, 0);
    }

    private void open(Player player, TagDialogRequest request, int page) {
        List<Map.Entry<String, Tag>> tags = request.tags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        int pages = Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        boolean selector = request.type() == TagDialogRequest.Type.TAG_SELECTOR;
        TagMenu menu = new TagMenu(request, Component.text(selector ? "Choose a tag" : "All tags registered"));
        for (int slot = 0, index = page * PAGE_SIZE; slot < PAGE_SIZE && index < tags.size(); slot++, index++) {
            Map.Entry<String, Tag> entry = tags.get(index);
            Tag tag = entry.getValue();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(entry.getKey(), NamedTextColor.DARK_GRAY));
            if (tag.description != null) lore.add(TagText.miniMessage().deserialize(tag.description));
            if (selector) lore.add(Component.text("Click to assign", NamedTextColor.YELLOW));
            menu.inventory.setItem(slot, item(Material.NAME_TAG, TagText.miniMessage().deserialize(tag.prefix), lore));
            if (selector) menu.actions.put(slot, viewer -> choose(viewer, new TagSelectionRequest(entry.getKey())));
        }
        if (tags.isEmpty()) {
            menu.inventory.setItem(22, item(Material.NAME_TAG,
                    Component.text(selector ? "You have no obtained tags" : "No tags registered", NamedTextColor.GRAY), List.of()));
        }
        if (page > 0) {
            menu.button(45, Material.ARROW, "Previous page", viewer -> open(viewer, request, page - 1));
        }
        if (selector) {
            menu.button(47, Material.NAME_TAG, "Clear your tag", viewer -> choose(viewer, new TagSelectionRequest(null)));
        }
        menu.inventory.setItem(49, item(Material.PAPER,
                Component.text("Page " + (page + 1) + " / " + pages, NamedTextColor.GRAY), List.of()));
        menu.button(51, Material.BARRIER, "Close", Player::closeInventory);
        if (page + 1 < pages) {
            menu.button(53, Material.ARROW, "Next page", viewer -> open(viewer, request, page + 1));
        }
        player.openInventory(menu.inventory);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(name);
        meta.lore(lore.stream().map(line -> Component.empty()
                .decoration(TextDecoration.ITALIC, false).append(line)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void choose(Player player, TagSelectionRequest selection) {
        player.closeInventory();
        select.accept(player, selection);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TagMenu menu) || menu.renderer() != this) return;
        // Cancel the entire view, including shift clicks, hotbar swaps and collect-to-cursor.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !menu.request.playerId().equals(player.getUniqueId())
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        Consumer<Player> action = menu.actions.get(event.getRawSlot());
        if (action == null) return;
        // Bukkit requires inventory opens/closes to run after the click transaction.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == top) action.accept(player);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TagMenu menu && menu.renderer() == this) {
            event.setCancelled(true);
        }
    }

    void close() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof TagMenu menu && menu.renderer() == this) {
                player.closeInventory();
            }
        }
    }

    private final class TagMenu implements InventoryHolder {
        private final TagDialogRequest request;
        private final Inventory inventory;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        private TagMenu(TagDialogRequest request, Component title) {
            this.request = request;
            inventory = plugin.getServer().createInventory(this, 54, title);
        }

        private ChestTagRenderer renderer() { return ChestTagRenderer.this; }

        private void button(int slot, Material material, String name, Consumer<Player> action) {
            inventory.setItem(slot, item(material, Component.text(name, NamedTextColor.YELLOW), List.of()));
            actions.put(slot, action);
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
