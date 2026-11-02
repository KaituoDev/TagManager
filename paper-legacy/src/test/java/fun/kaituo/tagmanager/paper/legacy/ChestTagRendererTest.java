package fun.kaituo.tagmanager.paper.legacy;

import fun.kaituo.tagmanager.Tag;
import fun.kaituo.tagmanager.TagDialogRequest;
import fun.kaituo.tagmanager.TagSelectionRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChestTagRendererTest {
    private final UUID playerId = UUID.randomUUID();
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<TagSelectionRequest> selections = new ArrayList<>();
    private MockedStatic<ItemStack> items;
    private InventoryView view;
    private ChestTagRenderer renderer;

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(playerId)).thenReturn(player);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(server.getScheduler()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenAnswer(call -> view);
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(call.getArgument(0));
            return view;
        });
        doAnswer(call -> {
            view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(mock(Inventory.class));
            return null;
        }).when(player).closeInventory();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        when(server.createInventory(any(InventoryHolder.class), eq(54), any(Component.class))).thenAnswer(call -> {
            Inventory inventory = mock(Inventory.class);
            Map<Integer, ItemStack> contents = new HashMap<>();
            when(inventory.getHolder()).thenReturn(call.getArgument(0));
            when(inventory.getSize()).thenReturn(54);
            doAnswer(write -> contents.put(write.getArgument(0), write.getArgument(1)))
                    .when(inventory).setItem(anyInt(), any(ItemStack.class));
            when(inventory.getItem(anyInt())).thenAnswer(read -> contents.get(read.getArgument(0)));
            return inventory;
        });
        items = mockStatic(ItemStack.class);
        items.when(() -> ItemStack.of(any(Material.class))).thenAnswer(call -> {
            ItemStack item = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getType()).thenReturn(call.getArgument(0));
            when(item.getItemMeta()).thenReturn(meta);
            doAnswer(write -> {
                when(meta.itemName()).thenReturn(write.getArgument(0));
                return null;
            }).when(meta).itemName(any(Component.class));
            doAnswer(write -> {
                when(meta.lore()).thenReturn(write.getArgument(0));
                return null;
            }).when(meta).lore(anyList());
            return item;
        });
        renderer = new ChestTagRenderer(plugin, (viewer, selection) -> selections.add(selection));
    }

    @AfterEach
    void tearDown() { items.close(); }

    @Test
    void rendersNamedNameTagsWithFormattedDescriptionLore() {
        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of("vip", new Tag("<gold>[VIP]</gold>", "<green>A premium tag</green>")));

        assertEquals(54, top().getSize());
        ItemStack item = top().getItem(0);
        assertEquals(Material.NAME_TAG, item.getType());
        assertEquals("[VIP]", text(item.getItemMeta().itemName()));
        assertEquals(NamedTextColor.GOLD, item.getItemMeta().itemName().color());
        assertEquals(List.of("vip", "A premium tag", "Click to assign"),
                item.getItemMeta().lore().stream().map(ChestTagRendererTest::text).toList());
    }

    @Test
    void assignsAndClearsOnlyAfterTheClickTransaction() {
        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of("vip", new Tag("VIP", null)));
        InventoryClickEvent click = click(0, ClickType.LEFT);
        assertTrue(selections.isEmpty());
        verify(click).setCancelled(true);
        flushTasks();
        assertEquals(List.of(new TagSelectionRequest("vip")), selections);
        verify(player).closeInventory();

        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of());
        click(47, ClickType.RIGHT);
        flushTasks();
        assertEquals(new TagSelectionRequest(null), selections.getLast());
    }

    @Test
    void paginatesWithoutLosingTags() {
        Map<String, Tag> tags = new HashMap<>();
        for (int i = 0; i < 46; i++) tags.put("tag%02d".formatted(i), new Tag("Tag " + i, null));
        show(TagDialogRequest.Type.TAG_SELECTOR, tags);
        assertEquals("Tag 44", text(top().getItem(44).getItemMeta().itemName()));
        assertEquals("Page 1 / 2", text(top().getItem(49).getItemMeta().itemName()));
        click(53, ClickType.LEFT);
        flushTasks();
        assertEquals("Tag 45", text(top().getItem(0).getItemMeta().itemName()));
        assertNull(top().getItem(1));
        assertEquals("Page 2 / 2", text(top().getItem(49).getItemMeta().itemName()));
        click(45, ClickType.LEFT);
        flushTasks();
        click(0, ClickType.LEFT);
        flushTasks();
        assertEquals(List.of(new TagSelectionRequest("tag00")), selections);
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "SWAP_OFFHAND", "DOUBLE_CLICK", "DROP", "CONTROL_DROP", "MIDDLE"})
    void blocksItemTransfersAndUnintendedActions(ClickType type) {
        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of("vip", new Tag("VIP", null)));
        verify(click(0, type)).setCancelled(true);
        verify(click(54, type)).setCancelled(true);
        flushTasks();
        assertTrue(selections.isEmpty());
    }

    @Test
    void blocksDragsAndIgnoresBottomInventoryAndOutsideClicks() {
        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of());
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        renderer.onDrag(drag);
        verify(drag).setCancelled(true);
        verify(click(54, ClickType.LEFT)).setCancelled(true);
        verify(click(-999, ClickType.LEFT)).setCancelled(true);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void listAllIsReadOnlyAndClosesOnDisable() {
        show(TagDialogRequest.Type.ALL_TAGS, Map.of("unowned", new Tag("Unowned", null)));
        click(0, ClickType.LEFT);
        click(47, ClickType.LEFT);
        assertTrue(tasks.isEmpty());
        assertTrue(selections.isEmpty());
        assertNull(top().getItem(47));
        renderer.close();
        verify(player).closeInventory();
    }

    @Test
    void ignoresQueuedClicksWhenThePlayerClosesOrChangesTheMenu() {
        show(TagDialogRequest.Type.TAG_SELECTOR, Map.of("vip", new Tag("VIP", null)));
        click(0, ClickType.LEFT);
        player.closeInventory();
        flushTasks();
        assertTrue(selections.isEmpty());
    }

    @Test
    void leavesUnrelatedInventoriesUntouched() {
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        verify(click(0, ClickType.LEFT), never()).setCancelled(anyBoolean());
    }

    private void show(TagDialogRequest.Type type, Map<String, Tag> tags) {
        renderer.show(new TagDialogRequest(type, playerId, tags));
    }

    private Inventory top() { return view.getTopInventory(); }

    private InventoryClickEvent click(int slot, ClickType type) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(type);
        when(event.getRawSlot()).thenReturn(slot);
        renderer.onClick(event);
        return event;
    }

    private void flushTasks() {
        List<Runnable> pending = List.copyOf(tasks);
        tasks.clear();
        pending.forEach(Runnable::run);
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
