package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Equip screen: categories on the left, search + rarity filter + sorting on top, the cosmetics list in the middle
 * and a live preview of the player on the right. Clicking a cosmetic equips or removes it.
 */
public final class CosmeticsScreen extends Screen {
    private enum Sort {
        NAME_ASC, NAME_DESC, RARITY_DESC, RARITY_ASC
    }

    private static final int LEFT_WIDTH = 86;
    private static final int PREVIEW_WIDTH = 110;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private Category category; // null = all
    private Rarity rarity;     // null = all
    private Sort sort = Sort.NAME_ASC;
    private String query = "";
    private int page;
    private final List<AbstractWidget> listWidgets = new ArrayList<>();
    private int rowsPerPage = 1;

    public CosmeticsScreen(Screen parent) {
        super(Component.translatable("htzcosmetics.screen.title"));
        this.parent = parent;
    }

    private String lang() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    private int listLeft() {
        return LEFT_WIDTH + 14;
    }

    private int listRight() {
        return width - PREVIEW_WIDTH - 10;
    }

    @Override
    protected void init() {
        // Categories (left)
        int y = 40;
        addRenderableWidget(categoryButton(null, Component.translatable("htzcosmetics.category.all"), y));
        for (Category c : Category.values()) {
            y += 22;
            addRenderableWidget(categoryButton(c, categoryName(c), y));
        }
        addRenderableWidget(Button.builder(Component.translatable("htzcosmetics.screen.options"),
                b -> minecraft.setScreen(new CosmeticsOptionsScreen(this)))
            .bounds(8, y + 30, LEFT_WIDTH, 20).build());

        // Filters (top)
        int x = listLeft();
        EditBox search = new EditBox(font, x, 18, 110, 18, Component.translatable("htzcosmetics.screen.search"));
        search.setHint(Component.translatable("htzcosmetics.screen.search"));
        search.setValue(query);
        search.setResponder(text -> {
            query = text.toLowerCase(Locale.ROOT).trim();
            page = 0;
            rebuildList();
        });
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(rarityLabel(), b -> {
                rarity = rarity == null ? Rarity.values()[0]
                    : (rarity.ordinal() + 1 < Rarity.values().length ? Rarity.values()[rarity.ordinal() + 1] : null);
                page = 0;
                b.setMessage(rarityLabel());
                rebuildList();
            }).bounds(x + 114, 17, 100, 20).build());
        addRenderableWidget(Button.builder(sortLabel(), b -> {
                sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                b.setMessage(sortLabel());
                rebuildList();
            }).bounds(x + 218, 17, 84, 20).build());

        // Bottom
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width - PREVIEW_WIDTH - 5, height - 26, PREVIEW_WIDTH, 20).build());

        rowsPerPage = Math.max(1, (height - 46 - 48) / ROW_HEIGHT); // leaves room for the status line + paging
        rebuildList();
    }

    private Button categoryButton(Category c, Component name, int y) {
        Component label = c == category ? Component.literal("> ").append(name).withStyle(ChatFormatting.YELLOW) : name;
        return Button.builder(label, b -> {
            category = c;
            page = 0;
            rebuildWidgets();
        }).bounds(8, y, LEFT_WIDTH, 20).build();
    }

    private List<CosmeticDefinition> filtered() {
        String lang = lang();
        List<CosmeticDefinition> out = new ArrayList<>();
        for (CosmeticDefinition def : CosmeticLibrary.get().available()) {
            if (category != null && def.category() != category) continue;
            if (rarity != null && def.rarity() != rarity) continue;
            if (!query.isEmpty() && !def.displayName(lang).toLowerCase(Locale.ROOT).contains(query)
                && !def.id().contains(query)) continue;
            out.add(def);
        }
        Comparator<CosmeticDefinition> byName = Comparator.comparing(d -> d.displayName(lang).toLowerCase(Locale.ROOT));
        switch (sort) {
            case NAME_ASC -> out.sort(byName);
            case NAME_DESC -> out.sort(byName.reversed());
            case RARITY_DESC -> out.sort(Comparator.comparing(CosmeticDefinition::rarity).reversed().thenComparing(byName));
            case RARITY_ASC -> out.sort(Comparator.comparing(CosmeticDefinition::rarity).thenComparing(byName));
        }
        return out;
    }

    private void rebuildList() {
        for (AbstractWidget w : listWidgets) removeWidget(w);
        listWidgets.clear();

        List<CosmeticDefinition> items = filtered();
        int pages = Math.max(1, (items.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.min(page, pages - 1);
        int left = listLeft();
        int w = listRight() - left;
        int y = 46;
        for (int i = page * rowsPerPage; i < Math.min(items.size(), (page + 1) * rowsPerPage); i++) {
            CosmeticDefinition def = items.get(i);
            Button b = Button.builder(entryLabel(def), btn -> {
                if (OwnCosmetics.isEquipped(def.id())) OwnCosmetics.unequip(def.id());
                else OwnCosmetics.equip(def);
                rebuildList();
            }).bounds(left, y, w, 20).build();
            b.setTooltip(Tooltip.create(rarityName(def.rarity()).copy().append(" - ").append(categoryName(def.category()))));
            listWidgets.add(addRenderableWidget(b));
            y += ROW_HEIGHT;
        }
        Button prev = Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildList();
        }).bounds(left, height - 26, 20, 20).build();
        prev.active = page > 0;
        Button next = Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildList();
        }).bounds(left + 24, height - 26, 20, 20).build();
        next.active = page < pages - 1;
        listWidgets.add(addRenderableWidget(prev));
        listWidgets.add(addRenderableWidget(next));
    }

    private Component entryLabel(CosmeticDefinition def) {
        // Names are plain text validated by the manifest parser (no formatting codes).
        Component name = Component.literal(def.displayName(lang())).withStyle(rarityColor(def.rarity()));
        return OwnCosmetics.isEquipped(def.id())
            ? Component.literal("✔ ").withStyle(ChatFormatting.GREEN).append(name)
            : name;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 5, 0xFFFFFFFF);

        List<CosmeticDefinition> items = filtered();
        if (items.isEmpty()) {
            Component empty = CosmeticLibrary.get().available().isEmpty()
                ? Component.translatable("htzcosmetics.screen.loading")
                : Component.translatable("htzcosmetics.screen.empty");
            g.drawString(font, empty, listLeft(), 52, 0xFFAAAAAA);
        }
        int pages = Math.max(1, (items.size() + rowsPerPage - 1) / rowsPerPage);
        g.drawString(font, (page + 1) + "/" + pages, listLeft() + 50, height - 20, 0xFFFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(statusText().getString(), listRight() - listLeft()),
            listLeft(), height - 40, 0xFFAAAAAA);

        // Live player preview (right)
        int x1 = width - PREVIEW_WIDTH - 5;
        int x2 = width - 5;
        g.fill(x1, 40, x2, height - 32, 0x66000000);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, 40, x2, height - 32, 45, 0.0625F,
                mouseX, mouseY, minecraft.player);
        } else {
            g.drawCenteredString(font, Component.translatable("htzcosmetics.screen.no_preview"), (x1 + x2) / 2,
                height / 2, 0xFFAAAAAA);
        }
    }

    private Component statusText() {
        return switch (OwnCosmetics.status()) {
            case IDLE -> Component.empty();
            case SAVING -> Component.translatable("htzcosmetics.status.saving");
            case SAVED -> Component.translatable("htzcosmetics.status.saved");
            case LOCAL_ONLY -> Component.translatable("htzcosmetics.status.local_only");
            case ERROR -> Component.translatable("htzcosmetics.status.error");
        };
    }

    private Component rarityLabel() {
        return Component.translatable("htzcosmetics.screen.rarity",
            rarity == null ? Component.translatable("htzcosmetics.rarity.all") : rarityName(rarity));
    }

    private Component sortLabel() {
        return Component.translatable("htzcosmetics.sort." + sort.name().toLowerCase(Locale.ROOT));
    }

    static Component categoryName(Category c) {
        return Component.translatable("htzcosmetics.category." + c.name().toLowerCase(Locale.ROOT));
    }

    static Component rarityName(Rarity r) {
        return Component.translatable("htzcosmetics.rarity." + r.name().toLowerCase(Locale.ROOT)).withStyle(rarityColor(r));
    }

    static ChatFormatting rarityColor(Rarity r) {
        return switch (r) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
        };
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
