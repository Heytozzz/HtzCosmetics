package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.ClientState;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Wardrobe screen: categories on the left, search / rarity / sort on top, cosmetic cards with live 3D thumbnails in
 * the middle, and a rotatable player preview on the right with an "Equipped" window.
 *
 * <p>Everything except the search box is drawn and hit-tested here (see {@link #regions}), so the version-specific
 * input methods of Minecraft stay in a handful of small overrides.
 */
public final class CosmeticsScreen extends Screen {
    // Palette (ARGB)
    static final int BG = 0xE0101217;
    static final int PANEL = 0xFF1C1F27;
    static final int PANEL_EDGE = 0xFF2B2F3A;
    static final int CARD = 0xFF222632;
    static final int CARD_HOVER = 0xFF2A2F3D;
    static final int THUMB_BG = 0xFF181B22;
    static final int TEXT = 0xFFE6E8EE;
    static final int TEXT_DIM = 0xFF9AA0AD;
    static final int TEXT_FAINT = 0xFF5F6573;
    static final int ACCENT = 0xFFF2C94C;
    static final int WORN = 0xFF5FD068;
    static final int CHIP = 0xFF2A2E39;
    static final int CHIP_ON = 0xFF3A3F4D;

    private enum Sort { NAME_ASC, NAME_DESC, RARITY_DESC, RARITY_ASC }

    private enum PreviewMode { FREE, FRONT, BACK, SPIN }

    /** A clickable rectangle; {@code key} names it for the automated in-game test (may be null). */
    private record Region(int x, int y, int w, int h, Runnable action, String key) {
        Region(int x, int y, int w, int h, Runnable action) {
            this(x, y, w, h, action, null);
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private final List<Region> regions = new ArrayList<>();
    private Category category; // null = all
    private Rarity rarity;     // null = all
    private Sort sort = Sort.NAME_ASC;
    private String query = "";
    private int page;
    private EditBox search;
    private boolean equippedWindow;
    private String highlightId;
    private long highlightUntil;
    private PreviewMode previewMode = PreviewMode.FRONT;
    private float yaw;
    // Preview without a world: the account's skin on a plain player model.
    private java.util.function.Supplier<net.minecraft.client.resources.PlayerSkin> skin;
    private net.minecraft.client.model.PlayerModel wideModel;
    private net.minecraft.client.model.PlayerModel slimModel;
    private boolean draggingPreview;
    private long lastFrameNanos;

    // Layout (computed in init)
    private int topY, bodyY, bodyBottom, leftX, leftW, gridX, gridW, previewX, previewW;
    private int cols, rows, cardW, cardH;

    public CosmeticsScreen(Screen parent) {
        super(Component.translatable("htzcosmetics.screen.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int m = 6;
        topY = m;
        bodyY = topY + 24;
        bodyBottom = height - m;
        leftX = m;
        leftW = 84;
        previewW = Math.max(104, Math.min(170, width / 4));
        previewX = width - m - previewW;
        gridX = leftX + leftW + 4;
        gridW = previewX - 4 - gridX;

        int gap = 4;
        int minCard = 64;
        cols = Math.max(1, (gridW - gap) / (minCard + gap));
        cardW = (gridW - gap - cols * gap) / cols;
        cardH = cardW + 22;
        rows = Math.max(1, (bodyBottom - bodyY - gap - 18) / (cardH + gap));

        if (minecraft.player == null) {
            skin = minecraft.getSkinManager().lookupInsecure(minecraft.getGameProfile());
            var models = minecraft.getEntityModels();
            wideModel = new net.minecraft.client.model.PlayerModel(
                models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            slimModel = new net.minecraft.client.model.PlayerModel(
                models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
        }

        search = new EditBox(font, gridX + 76, topY + 3, 92, 16, Component.translatable("htzcosmetics.screen.search"));
        search.setHint(Component.translatable("htzcosmetics.screen.search"));
        search.setValue(query);
        search.setResponder(text -> {
            query = text.toLowerCase(Locale.ROOT).trim();
            page = 0;
        });
        addRenderableWidget(search);
    }

    private String lang() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
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

    private int perPage() {
        return cols * rows;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : Math.min(0.1F, (now - lastFrameNanos) / 1e9F);
        lastFrameNanos = now;
        if (previewMode == PreviewMode.SPIN) yaw = (yaw + dt * 50) % 360;

        regions.clear();
        g.fill(0, 0, width, height, BG);
        boolean modal = equippedWindow;
        int mx = modal ? -1 : mouseX;
        int my = modal ? -1 : mouseY;

        drawTopBar(g, mx, my);
        drawCategories(g, mx, my);
        drawGrid(g, mx, my);
        drawPreview(g, mx, my);
        search.render(g, mouseX, mouseY, partialTick);

        if (modal) {
            regions.clear(); // only the window is clickable while it is open
            g.nextStratum();
            drawEquippedWindow(g, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Our own background is drawn in render(); skip the default blur so the 3D previews stay sharp.
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_EDGE);
    }

    private int chip(GuiGraphics g, int x, int y, Component label, int textColor, boolean on, int mx, int my,
                     Runnable action) {
        return chip(g, x, y, label, textColor, on, mx, my, action, null);
    }

    private int chip(GuiGraphics g, int x, int y, Component label, int textColor, boolean on, int mx, int my,
                     Runnable action, String key) {
        int w = font.width(label) + 10;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
        g.fill(x, y, x + w, y + 14, on ? CHIP_ON : (hover ? CARD_HOVER : CHIP));
        if (on) g.fill(x, y + 13, x + w, y + 14, textColor);
        g.drawString(font, label, x + 5, y + 3, textColor, false);
        regions.add(new Region(x, y, w, 14, action, key));
        return w;
    }

    private void drawTopBar(GuiGraphics g, int mx, int my) {
        panel(g, leftX, topY, width - 2 * leftX, 22);

        // Back arrow (top-left): returns to the menu we came from.
        int bx = leftX + 3;
        int by = topY + 3;
        boolean backHover = mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
        g.fill(bx, by, bx + 16, by + 16, backHover ? CHIP_ON : CHIP);
        g.renderOutline(bx, by, 16, 16, PANEL_EDGE);
        drawLeftArrow(g, bx + 4, by + 4, backHover ? TEXT : TEXT_DIM);
        if (backHover) g.setTooltipForNextFrame(font, Component.translatable("htzcosmetics.screen.back_tooltip"), mx, my);
        regions.add(new Region(bx, by, 16, 16, this::onClose, "back"));

        g.drawString(font, title, leftX + 26, topY + 7, TEXT, false);

        int x = gridX + 172;
        int right = width - leftX - 6;
        int sortW = font.width(sortLabel()) + 10;
        // Rarity filter: chips when they fit, otherwise a single cycling chip.
        int chipsW = font.width(Component.translatable("htzcosmetics.rarity.all")) + 10;
        for (Rarity r : Rarity.values()) chipsW += font.width(rarityName(r)) + 13;
        if (x + chipsW + 6 + sortW <= right) {
            x += chip(g, x, topY + 4, Component.translatable("htzcosmetics.rarity.all"), TEXT, rarity == null, mx, my,
                () -> setRarity(null)) + 3;
            for (Rarity r : Rarity.values()) {
                x += chip(g, x, topY + 4, rarityName(r), rarityColor(r), rarity == r, mx, my, () -> setRarity(r)) + 3;
            }
        } else {
            Component label = rarity == null ? Component.translatable("htzcosmetics.rarity.all") : rarityName(rarity);
            chip(g, x, topY + 4, Component.translatable("htzcosmetics.screen.rarity", label),
                rarity == null ? TEXT : rarityColor(rarity), rarity != null, mx, my, () -> setRarity(
                    rarity == null ? Rarity.values()[0]
                        : rarity.ordinal() + 1 < Rarity.values().length ? Rarity.values()[rarity.ordinal() + 1] : null));
        }
        chip(g, right - sortW, topY + 4, sortLabel(), TEXT_DIM, false, mx, my, () -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
        });
    }

    /** An 8x8 pixel-art arrow pointing left (crisp at every GUI scale, no font glyph needed). */
    private static void drawLeftArrow(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + 3 - i, x + i + 1, y + 5 + i, color); // head
        }
        g.fill(x + 3, y + 3, x + 8, y + 5, color);                // shaft
    }

    private void setRarity(Rarity r) {
        rarity = r;
        page = 0;
    }

    private void drawCategories(GuiGraphics g, int mx, int my) {
        panel(g, leftX, bodyY, leftW, bodyBottom - bodyY);
        int y = bodyY + 4;
        y = categoryRow(g, null, Component.translatable("htzcosmetics.category.all"), y, mx, my);
        for (Category c : Category.values()) y = categoryRow(g, c, categoryName(c), y, mx, my);

        int oy = bodyBottom - 20;
        boolean hover = mx >= leftX && mx < leftX + leftW && my >= oy && my < oy + 16;
        if (hover) g.fill(leftX + 1, oy, leftX + leftW - 1, oy + 16, CARD_HOVER);
        g.drawString(font, Component.translatable("htzcosmetics.screen.options"), leftX + 8, oy + 4, TEXT_DIM, false);
        regions.add(new Region(leftX, oy, leftW, 16, () -> minecraft.setScreen(new CosmeticsOptionsScreen(this))));
    }

    private int categoryRow(GuiGraphics g, Category c, Component name, int y, int mx, int my) {
        boolean on = category == c;
        boolean hover = mx >= leftX && mx < leftX + leftW && my >= y && my < y + 16;
        if (on) {
            g.fill(leftX + 1, y, leftX + leftW - 1, y + 16, CHIP_ON);
            g.fill(leftX + 1, y, leftX + 3, y + 16, ACCENT);
        } else if (hover) {
            g.fill(leftX + 1, y, leftX + leftW - 1, y + 16, CARD_HOVER);
        }
        g.drawString(font, name, leftX + 8, y + 4, on ? TEXT : TEXT_DIM, false);
        int count = 0;
        for (CosmeticDefinition d : CosmeticLibrary.get().available()) if (c == null || d.category() == c) count++;
        String n = Integer.toString(count);
        g.drawString(font, n, leftX + leftW - 6 - font.width(n), y + 4, TEXT_FAINT, false);
        regions.add(new Region(leftX, y, leftW, 16, () -> {
            category = c;
            page = 0;
        }));
        return y + 17;
    }

    private void drawGrid(GuiGraphics g, int mx, int my) {
        int gridH = bodyBottom - bodyY;
        panel(g, gridX, bodyY, gridW, gridH);
        List<CosmeticDefinition> items = filtered();
        int pages = Math.max(1, (items.size() + perPage() - 1) / perPage());
        page = Math.max(0, Math.min(page, pages - 1));

        if (items.isEmpty()) {
            Component msg = CosmeticLibrary.get().available().isEmpty()
                ? Component.translatable("htzcosmetics.screen.loading") : Component.translatable("htzcosmetics.screen.empty");
            g.drawCenteredString(font, msg, gridX + gridW / 2, bodyY + gridH / 2 - 4, TEXT_DIM);
        }

        List<ThumbnailsRenderState.Thumb> thumbs = new ArrayList<>();
        float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
        int gap = 4;
        int start = page * perPage();
        for (int i = start; i < Math.min(items.size(), start + perPage()); i++) {
            int slot = i - start;
            int x = gridX + gap + (slot % cols) * (cardW + gap);
            int y = bodyY + gap + (slot / cols) * (cardH + gap);
            CosmeticDefinition def = items.get(i);
            drawCard(g, def, x, y, mx, my, thumbs, seconds);
        }
        if (!thumbs.isEmpty()) {
            g.guiRenderState.submitPicturesInPictureState(ThumbnailsRenderState.of(thumbs, seconds, gridX + 1, bodyY + 1,
                gridX + gridW - 1, bodyBottom - 1, g.scissorStack.peek()));
        }

        // Pager
        int py = bodyBottom - 16;
        String label = (page + 1) + " / " + pages;
        int lw = font.width(label);
        int cx = gridX + gridW / 2;
        g.drawString(font, label, cx - lw / 2, py + 3, TEXT_DIM, false);
        if (page > 0) chip(g, cx - lw / 2 - 20, py, Component.literal("<"), TEXT, false, mx, my, () -> page--);
        if (page < pages - 1) chip(g, cx + lw / 2 + 6, py, Component.literal(">"), TEXT, false, mx, my, () -> page++);
    }

    private void drawCard(GuiGraphics g, CosmeticDefinition def, int x, int y, int mx, int my,
                          List<ThumbnailsRenderState.Thumb> thumbs, float seconds) {
        boolean worn = OwnCosmetics.isEquipped(def.id());
        boolean hover = mx >= x && mx < x + cardW && my >= y && my < y + cardH;
        int rc = rarityColor(def.rarity());
        g.fill(x, y, x + cardW, y + cardH, hover ? CARD_HOVER : CARD);
        g.fill(x + 1, y + 1, x + cardW - 1, y + cardW, THUMB_BG);
        g.fill(x, y + cardW, x + cardW, y + cardW + 1, rc);
        if (worn) g.renderOutline(x, y, cardW, cardH, WORN);
        if (def.id().equals(highlightId) && System.currentTimeMillis() < highlightUntil
            && (System.currentTimeMillis() / 200) % 2 == 0) {
            g.renderOutline(x - 1, y - 1, cardW + 2, cardH + 2, ACCENT);
        }

        BakedCosmetic baked = CosmeticLibrary.get().baked(def.id());
        if (baked != null) {
            float spin = hover ? (seconds * 90) % 360 : 30 + (float) Math.sin(seconds * 0.8) * 20;
            thumbs.add(new ThumbnailsRenderState.Thumb(baked, x + cardW / 2, y + cardW / 2, cardW - 6, spin));
        }

        String name = font.plainSubstrByWidth(def.displayName(lang()), cardW - 8);
        g.drawString(font, name, x + 4, y + cardW + 3, TEXT, false);
        g.drawString(font, font.plainSubstrByWidth(rarityName(def.rarity()).getString(), cardW - 8), x + 4,
            y + cardW + 12, rc, false);
        if (baked != null && !baked.animations().isEmpty()) {
            // Small "animated" badge in the top-left corner of the thumbnail.
            g.fill(x + 2, y + 2, x + 12, y + 12, 0xFF3A3F4D);
            g.drawString(font, "▶", x + 4, y + 3, ACCENT, false);
            if (mx >= x + 2 && mx < x + 12 && my >= y + 2 && my < y + 12) {
                g.setTooltipForNextFrame(font, Component.translatable("htzcosmetics.screen.animated"), mx, my);
            }
        }
        if (worn) {
            Component tag = Component.translatable("htzcosmetics.screen.worn");
            int tw = font.width(tag) + 6;
            g.fill(x + cardW - tw - 2, y + 2, x + cardW - 2, y + 12, 0xFF2F6B38);
            g.drawString(font, tag, x + cardW - tw + 1, y + 3, 0xFFD6F5DA, false);
        }
        regions.add(new Region(x, y, cardW, cardH, () -> {
            if (OwnCosmetics.isEquipped(def.id())) OwnCosmetics.unequip(def.id());
            else OwnCosmetics.equip(def);
        }, "card:" + def.id()));
    }

    private void drawPreview(GuiGraphics g, int mx, int my) {
        int h = bodyBottom - bodyY;
        panel(g, previewX, bodyY, previewW, h);
        int areaTop = bodyY + 14;
        int areaBottom = bodyBottom - 64;
        LivingEntity player = minecraft != null ? minecraft.player : null;
        if (player != null) {
            renderPlayer(g, previewX + 2, areaTop, previewX + previewW - 2, areaBottom, player);
            g.drawString(font, Component.translatable("htzcosmetics.screen.drag_hint"), previewX + 5, bodyY + 4, TEXT_FAINT, false);
        } else if (skin != null) {
            // No world yet: draw the account's skin and worn cosmetics on a plain player model.
            var playerSkin = skin.get();
            var model = playerSkin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? slimModel : wideModel;
            List<BakedCosmetic> worn = new ArrayList<>();
            java.util.EnumSet<Category> used = java.util.EnumSet.noneOf(Category.class);
            for (String id : EquippedCosmetics.get(ClientState.self())) {
                BakedCosmetic b = CosmeticLibrary.get().baked(id);
                if (b != null && used.add(b.definition().category())) worn.add(b);
            }
            float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
            g.guiRenderState.submitPicturesInPictureState(PlayerPreviewRenderState.of(model, playerSkin.texture(), worn,
                yaw, seconds, previewX + 2, areaTop, previewX + previewW - 2, areaBottom, g.scissorStack.peek()));
            g.drawString(font, Component.translatable("htzcosmetics.screen.drag_hint"), previewX + 5, bodyY + 4, TEXT_FAINT, false);
        }

        // Front / Back / Spin
        Component[] labels = {Component.translatable("htzcosmetics.screen.front"),
            Component.translatable("htzcosmetics.screen.back"), Component.translatable("htzcosmetics.screen.spin")};
        PreviewMode[] modes = {PreviewMode.FRONT, PreviewMode.BACK, PreviewMode.SPIN};
        int total = -3;
        for (Component l : labels) total += font.width(l) + 13;
        int x = previewX + (previewW - total) / 2;
        for (int i = 0; i < 3; i++) {
            PreviewMode mode = modes[i];
            x += chip(g, x, areaBottom + 4, labels[i], TEXT, previewMode == mode, mx, my, () -> {
                previewMode = mode;
                if (mode == PreviewMode.FRONT) yaw = 0;
                if (mode == PreviewMode.BACK) yaw = 180;
            }, "preview:" + mode.name().toLowerCase(Locale.ROOT)) + 3;
        }

        // Equipped button
        int by = bodyBottom - 40;
        int count = EquippedCosmetics.get(ClientState.self()).size();
        Component label = Component.translatable("htzcosmetics.screen.equipped_button", count);
        boolean hover = mx >= previewX + 4 && mx < previewX + previewW - 4 && my >= by && my < by + 18;
        g.fill(previewX + 4, by, previewX + previewW - 4, by + 18, hover ? CHIP_ON : CHIP);
        g.renderOutline(previewX + 4, by, previewW - 8, 18, PANEL_EDGE);
        g.drawCenteredString(font, label, previewX + previewW / 2, by + 5, TEXT);
        regions.add(new Region(previewX + 4, by, previewW - 8, 18, () -> equippedWindow = true, "equipped_button"));

        g.drawString(font, font.plainSubstrByWidth(statusText().getString(), previewW - 10), previewX + 5,
            bodyBottom - 14, statusColor(), false);
    }

    private void renderPlayer(GuiGraphics g, int x0, int y0, int x1, int y1, LivingEntity entity) {
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        float facing = 180.0F + yaw;
        entity.yBodyRot = facing;
        entity.setYRot(facing);
        entity.setXRot(0);
        entity.yHeadRot = facing;
        entity.yHeadRotO = facing;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf();
        int scale = Math.max(20, Math.min((y1 - y0) / 2 - 4, (x1 - x0) / 2));
        InventoryScreen.renderEntityInInventory(g, x0, y0, x1, y1, scale,
            new Vector3f(0, entity.getBbHeight() / 2.0F + 0.0625F, 0), pose, camera, entity);
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
    }

    private void drawEquippedWindow(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0xB0000000);
        List<String> ids = EquippedCosmetics.get(ClientState.self());
        int w = Math.min(260, width - 40);
        int rowH = 26;
        int h = 34 + Math.max(1, ids.size()) * rowH + 6;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + 20, CHIP);
        g.drawString(font, Component.translatable("htzcosmetics.screen.equipped_title"), x + 8, y + 6, TEXT, false);
        int cx = x + w - 16;
        boolean closeHover = mx >= cx && mx < cx + 12 && my >= y + 4 && my < y + 16;
        g.drawString(font, "×", cx + 3, y + 6, closeHover ? TEXT : TEXT_DIM, false);
        regions.add(new Region(cx, y + 2, 14, 16, () -> equippedWindow = false));

        int ry = y + 26;
        if (ids.isEmpty()) {
            g.drawString(font, Component.translatable("htzcosmetics.screen.equipped_empty"), x + 10, ry + 6, TEXT_DIM, false);
        }
        for (String id : ids) {
            CosmeticDefinition def = CosmeticLibrary.get().definition(id);
            String name = def != null ? def.displayName(lang()) : id;
            int color = def != null ? rarityColor(def.rarity()) : TEXT_DIM;
            g.fill(x + 6, ry, x + w - 6, ry + rowH - 2, CARD);
            g.fill(x + 6, ry, x + 8, ry + rowH - 2, color);
            g.drawString(font, font.plainSubstrByWidth(name, w - 140), x + 12, ry + 3, color, false);
            if (def != null) g.drawString(font, categoryName(def.category()), x + 12, ry + 13, TEXT_FAINT, false);

            Component remove = Component.translatable("htzcosmetics.screen.remove");
            int rw = font.width(remove) + 10;
            int bx = x + w - 10 - rw;
            chip(g, bx, ry + 5, remove, 0xFFF08A8A, false, mx, my, () -> OwnCosmetics.unequip(id), "window:remove:" + id);
            if (def != null) {
                Component go = Component.translatable("htzcosmetics.screen.go");
                int gw = font.width(go) + 10;
                chip(g, bx - 4 - gw, ry + 5, go, TEXT, false, mx, my, () -> goTo(def), "window:go:" + id);
            }
            ry += rowH;
        }
    }

    /** Opens the "Equipped" window (also used by the automated in-game test). */
    public void openEquippedWindow() {
        equippedWindow = true;
    }

    // --- Read-only hooks for the automated in-game test ---

    /** Center (GUI coordinates) of the clickable area named {@code key} in the last frame, or null. */
    public int[] regionCenter(String key) {
        for (Region r : regions) {
            if (key.equals(r.key())) return new int[]{r.x() + r.w() / 2, r.y() + r.h() / 2};
        }
        return null;
    }

    public boolean isEquippedWindowOpen() {
        return equippedWindow;
    }

    public Category selectedCategory() {
        return category;
    }

    public float previewYaw() {
        return yaw;
    }

    /** Center of the player preview (GUI coordinates). */
    public int[] previewCenter() {
        return new int[]{previewX + previewW / 2, (bodyY + bodyBottom - 64) / 2};
    }

    /** Closes the window and shows the cosmetic's card (category, filters and page), briefly highlighted. */
    private void goTo(CosmeticDefinition def) {
        equippedWindow = false;
        category = def.category();
        rarity = null;
        query = "";
        search.setValue("");
        List<CosmeticDefinition> items = filtered();
        int index = Math.max(0, items.indexOf(def));
        page = index / perPage();
        highlightId = def.id();
        highlightUntil = System.currentTimeMillis() + 1600;
    }

    // ------------------------------------------------------------------ input (version-specific signatures)

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleClick(mouseX, mouseY)) return true;
        if (equippedWindow) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (handleDrag(mouseX, mouseY, dx)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!equippedWindow && mouseX >= gridX && mouseX < gridX + gridW) {
            page = Math.max(0, page - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && equippedWindow) { // Escape closes the window first
            equippedWindow = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ input (version-independent)

    private boolean handleClick(double mx, double my) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(mx, my)) {
                r.action().run();
                return true;
            }
        }
        if (equippedWindow) {
            equippedWindow = false; // click outside the window closes it
            return true;
        }
        if (mx >= previewX && mx < previewX + previewW && my >= bodyY && my < bodyBottom - 64) {
            draggingPreview = true;
            return true;
        }
        return false;
    }

    private boolean handleDrag(double mx, double my, double dx) {
        if (!draggingPreview || equippedWindow) return false;
        previewMode = PreviewMode.FREE;
        yaw = (float) ((yaw - dx * 2.0) % 360);
        return true;
    }

    // ------------------------------------------------------------------ text helpers

    private Component sortLabel() {
        return Component.translatable("htzcosmetics.sort." + sort.name().toLowerCase(Locale.ROOT));
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

    private int statusColor() {
        return switch (OwnCosmetics.status()) {
            case SAVED -> WORN;
            case ERROR -> 0xFFF08A8A;
            default -> TEXT_DIM;
        };
    }

    static Component categoryName(Category c) {
        return Component.translatable("htzcosmetics.category." + c.name().toLowerCase(Locale.ROOT));
    }

    static Component rarityName(Rarity r) {
        return Component.translatable("htzcosmetics.rarity." + r.name().toLowerCase(Locale.ROOT));
    }

    static int rarityColor(Rarity r) {
        return switch (r) {
            case COMMON -> 0xFFD8DCE3;
            case UNCOMMON -> 0xFF7FE07F;
            case RARE -> 0xFF6FE3F2;
            case EPIC -> 0xFFE58BF0;
            case LEGENDARY -> 0xFFF2C94C;
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
