package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.ClientState;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import com.heytozzz.htzcosmetics.client.render.CosmeticRenderUtil;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    /** Id of the cosmetic whose colour window is open, or null. */
    private String colorFor;
    // Colour wheel state (hue 0-1, saturation 0-1, brightness 0-1) and which part is being dragged.
    private float wheelHue;
    private float wheelSat;
    private float wheelVal = 1.0F;
    private boolean draggingWheel;
    private boolean draggingValue;
    private int wheelX;
    private int wheelY;
    private int valueX;
    private static final int WHEEL_R = 30;
    private String highlightId;
    private long highlightUntil;
    private PreviewMode previewMode = PreviewMode.FRONT;
    private float yaw;
    /** Forward/backward tilt of the preview in degrees (vertical drag). */
    private float pitch;
    private static final float MAX_PITCH = 80.0F;
    // Preview without a world: the account's skin on a plain player model.
    private java.util.function.Supplier<GuiCompat.SkinInfo> skin;
    private net.minecraft.client.model.PlayerModel wideModel;
    private net.minecraft.client.model.PlayerModel slimModel;
    private boolean draggingPreview;
    private boolean panningPreview;
    private static final float MAX_ZOOM = 6.0F;
    private float zoom = 1.0F;
    private int categoryStep = 17;
    private float panX;
    private float panY;
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

        {
            // The preview always draws the account's skin with the worn cosmetics on a plain player model
            // (works the same in every Minecraft version, with or without a world).
            skin = GuiCompat.accountSkin(minecraft);
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
        boolean modal = modalOpen();
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
            if (colorFor != null) drawColorWindow(g, mouseX, mouseY);
            else drawEquippedWindow(g, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Our own background is drawn in render(); skip the default blur so the 3D previews stay sharp.
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        GuiCompat.outline(g, x, y, w, h, PANEL_EDGE);
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
        GuiCompat.outline(g, bx, by, 16, 16, PANEL_EDGE);
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
        // Rows shrink a little on small screens so every category fits above "Options".
        int rows = Category.values().length + 1;
        categoryStep = Math.max(11, Math.min(17, (bodyBottom - 24 - y) / rows));
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
        int h = categoryStep - 1;
        int textY = y + (h - 8) / 2;
        boolean hover = mx >= leftX && mx < leftX + leftW && my >= y && my < y + h;
        if (on) {
            g.fill(leftX + 1, y, leftX + leftW - 1, y + h, CHIP_ON);
            g.fill(leftX + 1, y, leftX + 3, y + h, ACCENT);
        } else if (hover) {
            g.fill(leftX + 1, y, leftX + leftW - 1, y + h, CARD_HOVER);
        }
        g.drawString(font, name, leftX + 8, textY, on ? TEXT : TEXT_DIM, false);
        int count = 0;
        for (CosmeticDefinition d : CosmeticLibrary.get().available()) if (c == null || d.category() == c) count++;
        String n = Integer.toString(count);
        g.drawString(font, n, leftX + leftW - 6 - font.width(n), textY, TEXT_FAINT, false);
        regions.add(new Region(leftX, y, leftW, h, () -> {
            category = c;
            page = 0;
        }));
        return y + categoryStep;
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
        if (worn) GuiCompat.outline(g, x, y, cardW, cardH, WORN);
        if (def.id().equals(highlightId) && System.currentTimeMillis() < highlightUntil
            && (System.currentTimeMillis() / 200) % 2 == 0) {
            GuiCompat.outline(g, x - 1, y - 1, cardW + 2, cardH + 2, ACCENT);
        }

        BakedCosmetic baked = CosmeticLibrary.get().baked(def.id());
        if (baked != null) {
            float spin = hover ? (seconds * 90) % 360 : 30 + (float) Math.sin(seconds * 0.8) * 20;
            thumbs.add(new ThumbnailsRenderState.Thumb(baked, worn ? OwnCosmetics.color(def.id()) : null,
                x + cardW / 2, y + cardW / 2, cardW - 6, spin));
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
        if (worn && def.tint() != null) {
            // Colour square in the thumbnail's bottom-right corner: opens the colour window (added last: on top).
            int sx = x + cardW - 13;
            int sy = y + cardW - 13;
            boolean sHover = mx >= sx && mx < sx + 11 && my >= sy && my < sy + 11;
            g.fill(sx, sy, sx + 11, sy + 11, sHover ? TEXT : 0xFF0E1015);
            g.fill(sx + 1, sy + 1, sx + 10, sy + 10, 0xFF000000 | def.tint().effective(OwnCosmetics.color(def.id())));
            if (sHover) g.setTooltipForNextFrame(font, Component.translatable("htzcosmetics.color.change"), mx, my);
            regions.add(new Region(sx, sy, 11, 11, () -> openColorWindow(def.id()), "color:" + def.id()));
        }
    }

    private void drawPreview(GuiGraphics g, int mx, int my) {
        int h = bodyBottom - bodyY;
        panel(g, previewX, bodyY, previewW, h);
        int areaTop = bodyY + 14;
        int areaBottom = bodyBottom - 64;
        if (skin != null) {
            var playerSkin = skin.get();
            var model = playerSkin.slim() ? slimModel : wideModel;
            List<CosmeticRenderUtil.Worn> worn = new ArrayList<>();
            java.util.EnumSet<Category> used = java.util.EnumSet.noneOf(Category.class);
            for (String id : EquippedCosmetics.get(ClientState.self())) {
                BakedCosmetic b = CosmeticLibrary.get().baked(id);
                if (b != null && used.add(b.definition().category())) {
                    worn.add(new CosmeticRenderUtil.Worn(b, OwnCosmetics.color(id)));
                }
            }
            float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
            g.guiRenderState.submitPicturesInPictureState(PlayerPreviewRenderState.of(model, playerSkin.texture(), worn,
                yaw, pitch, seconds, zoom, panX, panY, previewX + 2, areaTop, previewX + previewW - 2, areaBottom,
                g.scissorStack.peek()));
            drawPreviewControls(g, mx, my);
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
                pitch = 0;
            }, "preview:" + mode.name().toLowerCase(Locale.ROOT)) + 3;
        }

        // Equipped button
        int by = bodyBottom - 40;
        int count = EquippedCosmetics.get(ClientState.self()).size();
        Component label = Component.translatable("htzcosmetics.screen.equipped_button", count);
        boolean hover = mx >= previewX + 4 && mx < previewX + previewW - 4 && my >= by && my < by + 18;
        g.fill(previewX + 4, by, previewX + previewW - 4, by + 18, hover ? CHIP_ON : CHIP);
        GuiCompat.outline(g, previewX + 4, by, previewW - 8, 18, PANEL_EDGE);
        g.drawCenteredString(font, label, previewX + previewW / 2, by + 5, TEXT);
        regions.add(new Region(previewX + 4, by, previewW - 8, 18, () -> equippedWindow = true, "equipped_button"));

        g.drawString(font, font.plainSubstrByWidth(statusText().getString(), previewW - 10), previewX + 5,
            bodyBottom - 14, statusColor(), false);
    }

    /** Top row of the preview: a "?" with the mouse controls, and a reset button once zoomed or moved. */
    private void drawPreviewControls(GuiGraphics g, int mx, int my) {
        int hx = previewX + 4;
        int hy = bodyY + 3;
        boolean helpHover = mx >= hx && mx < hx + 12 && my >= hy && my < hy + 11;
        g.fill(hx, hy, hx + 12, hy + 11, helpHover ? CHIP_ON : CHIP);
        g.drawString(font, "?", hx + 4, hy + 2, helpHover ? TEXT : TEXT_DIM, false);
        if (helpHover) {
            g.setComponentTooltipForNextFrame(font, List.of(
                Component.translatable("htzcosmetics.screen.help_rotate"),
                Component.translatable("htzcosmetics.screen.help_move"),
                Component.translatable("htzcosmetics.screen.help_zoom")), mx, my);
        }
        if (zoom != 1.0F || panX != 0 || panY != 0 || pitch != 0) {
            Component reset = Component.translatable("htzcosmetics.screen.reset_view");
            int rw = font.width(reset) + 10;
            chip(g, previewX + previewW - 4 - rw, bodyY + 2, reset, TEXT_DIM, false, mx, my, this::resetView, "preview:reset");
        }
    }

    private void resetView() {
        pitch = 0;
        zoom = 1.0F;
        panX = 0;
        panY = 0;
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
                if (def.tint() != null) {
                    int sx = bx - 4 - gw - 18;
                    boolean sHover = mx >= sx && mx < sx + 14 && my >= ry + 5 && my < ry + 19;
                    g.fill(sx, ry + 5, sx + 14, ry + 19, sHover ? TEXT : 0xFF0E1015);
                    g.fill(sx + 1, ry + 6, sx + 13, ry + 18, 0xFF000000 | def.tint().effective(OwnCosmetics.color(id)));
                    regions.add(new Region(sx, ry + 5, 14, 14, () -> openColorWindow(id), "window:color:" + id));
                }
            }
            ry += rowH;
        }
    }

    /** Opens the colour window for a worn cosmetic whose colour players may change. */
    public void openColorWindow(String id) {
        CosmeticDefinition def = CosmeticLibrary.get().definition(id);
        if (def == null || def.tint() == null || !OwnCosmetics.isEquipped(id)) return;
        equippedWindow = false;
        colorFor = id;
        float[] hsv = toHsv(def.tint().effective(OwnCosmetics.color(id)));
        wheelHue = hsv[0];
        wheelSat = hsv[1];
        wheelVal = hsv[2];
    }

    public String colorWindowFor() {
        return colorFor;
    }

    /** Centre of the colour wheel in the last frame (GUI coordinates), for the automated in-game test. */
    public int[] wheelCenter() {
        return new int[]{wheelX, wheelY};
    }

    private void drawColorWindow(GuiGraphics g, int mx, int my) {
        CosmeticDefinition def = CosmeticLibrary.get().definition(colorFor);
        if (def == null || def.tint() == null || !OwnCosmetics.isEquipped(colorFor)) {
            colorFor = null;
            return;
        }
        var tint = def.tint();
        Integer chosen = OwnCosmetics.color(colorFor);
        int current = tint.effective(chosen);
        g.fill(0, 0, width, height, 0xB0000000);

        int sw = 16; // swatch size
        int perRow = 8;
        int swatches = 1 + tint.palette().length;
        int rows = (swatches + perRow - 1) / perRow;
        int w = Math.min(260, width - 40);
        int h = 30 + rows * (sw + 4) + 8 + (tint.free() ? WHEEL_R * 2 + 26 : 0) + 6;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + 20, CHIP);
        g.drawString(font, font.plainSubstrByWidth(
            Component.translatable("htzcosmetics.color.title", def.displayName(lang())).getString(), w - 30), x + 8, y + 6, TEXT, false);
        int cx = x + w - 16;
        boolean closeHover = mx >= cx && mx < cx + 12 && my >= y + 4 && my < y + 16;
        g.drawString(font, "×", cx + 3, y + 6, closeHover ? TEXT : TEXT_DIM, false);
        regions.add(new Region(cx, y + 2, 14, 16, () -> colorFor = null, "color:close"));

        // Swatches: the default colour first (marked), then the palette.
        int sx0 = x + 10;
        int sy = y + 28;
        for (int i = 0; i < swatches; i++) {
            int rgb = i == 0 ? tint.defaultColor() : tint.palette()[i - 1];
            int px = sx0 + (i % perRow) * (sw + 4);
            int py = sy + (i / perRow) * (sw + 4);
            boolean selected = i == 0 ? chosen == null : chosen != null && chosen == rgb;
            boolean hover = mx >= px && mx < px + sw && my >= py && my < py + sw;
            g.fill(px - 1, py - 1, px + sw + 1, py + sw + 1, selected ? ACCENT : hover ? TEXT : 0xFF0E1015);
            g.fill(px + 1, py + 1, px + sw - 1, py + sw - 1, 0xFF000000 | rgb);
            if (i == 0) g.drawString(font, "★", px + 4, py + 4, contrast(rgb), false);
            if (hover) {
                g.setTooltipForNextFrame(font, i == 0 ? Component.translatable("htzcosmetics.color.default")
                    : Component.literal(String.format("#%06X", rgb)), mx, my);
            }
            Integer value = i == 0 ? null : rgb;
            regions.add(new Region(px, py, sw, sw, () -> {
                OwnCosmetics.setColor(colorFor, value);
                float[] hsv = toHsv(value == null ? tint.defaultColor() : value);
                wheelHue = hsv[0];
                wheelSat = hsv[1];
                wheelVal = hsv[2];
            }, "color:swatch:" + i));
        }

        if (tint.free()) {
            int top = sy + rows * (sw + 4) + 6;
            g.drawString(font, Component.translatable("htzcosmetics.color.custom"), x + 10, top, TEXT_DIM, false);
            wheelX = x + 10 + WHEEL_R;
            wheelY = top + 12 + WHEEL_R;
            // The wheel: hue around, saturation outwards, at the current brightness (2x2 pixel cells).
            for (int dy = -WHEEL_R; dy < WHEEL_R; dy += 2) {
                for (int dx = -WHEEL_R; dx < WHEEL_R; dx += 2) {
                    float r = (float) Math.sqrt((dx + 1) * (dx + 1) + (dy + 1) * (dy + 1)) / WHEEL_R;
                    if (r > 1) continue;
                    float hue = (float) ((Math.atan2(dy + 1, dx + 1) / (2 * Math.PI) + 1) % 1);
                    g.fill(wheelX + dx, wheelY + dy, wheelX + dx + 2, wheelY + dy + 2, 0xFF000000 | hsv(hue, r, wheelVal));
                }
            }
            // Marker for the current hue/saturation.
            int mxk = wheelX + Math.round((float) Math.cos(wheelHue * 2 * Math.PI) * wheelSat * WHEEL_R);
            int myk = wheelY + Math.round((float) Math.sin(wheelHue * 2 * Math.PI) * wheelSat * WHEEL_R);
            GuiCompat.outline(g, mxk - 2, myk - 2, 5, 5, wheelVal > 0.5F ? 0xFF000000 : 0xFFFFFFFF);
            // Brightness bar.
            valueX = wheelX + WHEEL_R + 12;
            int top2 = wheelY - WHEEL_R;
            for (int i = 0; i < WHEEL_R * 2; i += 2) {
                float v = 1 - i / (float) (WHEEL_R * 2);
                g.fill(valueX, top2 + i, valueX + 10, top2 + i + 2, 0xFF000000 | hsv(wheelHue, wheelSat, v));
            }
            int vy = top2 + Math.round((1 - wheelVal) * (WHEEL_R * 2 - 1));
            g.fill(valueX - 2, vy, valueX + 12, vy + 1, 0xFFFFFFFF);
            // Current colour.
            int px = valueX + 22;
            g.fill(px, top2, px + 30, top2 + 30, 0xFF0E1015);
            g.fill(px + 1, top2 + 1, px + 29, top2 + 29, 0xFF000000 | current);
            g.drawString(font, String.format("#%06X", current), px, top2 + 36, TEXT_DIM, false);
        }
    }

    /**
     * Click or drag on the colour wheel or the brightness bar.
     *
     * @param start true for the initial click (starts a drag), false while dragging
     */
    private boolean pickColorAt(double mx, double my, boolean start) {
        CosmeticDefinition def = CosmeticLibrary.get().definition(colorFor);
        if (def == null || def.tint() == null || !def.tint().free()) return false;
        double dx = mx - wheelX;
        double dy = my - wheelY;
        boolean onWheel = dx * dx + dy * dy <= (WHEEL_R + 1) * (WHEEL_R + 1);
        boolean onValue = mx >= valueX - 2 && mx < valueX + 12 && my >= wheelY - WHEEL_R - 2 && my < wheelY + WHEEL_R + 2;
        if (start) {
            draggingWheel = onWheel;
            draggingValue = !onWheel && onValue;
        }
        if (draggingWheel) {
            wheelHue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            wheelSat = (float) Math.min(1, Math.sqrt(dx * dx + dy * dy) / WHEEL_R);
        } else if (draggingValue) {
            wheelVal = (float) Math.max(0, Math.min(1, 1 - (my - (wheelY - WHEEL_R)) / (WHEEL_R * 2)));
        } else {
            return false;
        }
        OwnCosmetics.setColor(colorFor, hsv(wheelHue, wheelSat, wheelVal));
        return true;
    }

    private static int hsv(float h, float s, float v) {
        float r;
        float g;
        float b;
        int i = (int) Math.floor(h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6);
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static float[] toHsv(int rgb) {
        float r = (rgb >> 16 & 255) / 255F;
        float g = (rgb >> 8 & 255) / 255F;
        float b = (rgb & 255) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h = (h / 6 + 1) % 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Black or white, whichever reads better on {@code rgb}. */
    private static int contrast(int rgb) {
        int lum = ((rgb >> 16 & 255) * 3 + (rgb >> 8 & 255) * 6 + (rgb & 255)) / 10;
        return lum > 140 ? 0xFF000000 : 0xFFFFFFFF;
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

    public float previewPitch() {
        return pitch;
    }

    public float previewZoom() {
        return zoom;
    }

    public float[] previewPan() {
        return new float[]{panX, panY};
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

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (onMouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (handleDrag(event.x(), event.y(), dx, dy)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        onMouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (onKeyPressed(event.key())) return true;
        return super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (onMouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (handleDrag(mouseX, mouseY, dx, dy)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        onMouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handleZoom(mouseX, mouseY, scrollY)) return true;
        if (!modalOpen() && mouseX >= gridX && mouseX < gridX + gridW) {
            page = Math.max(0, page - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ input (version-independent)

    /** @return true if consumed (also true for any click while the Equipped window is open) */
    private boolean onMouseClicked(double mx, double my, int button) {
        // Named constants: mouse button numbers differ between versions (26.3 uses SDL numbering).
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && handleClick(mx, my)) return true;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && handleRightClick(mx, my)) return true;
        return modalOpen();
    }

    private void onMouseReleased() {
        draggingPreview = false;
        panningPreview = false;
        draggingWheel = false;
        draggingValue = false;
    }

    private boolean modalOpen() {
        return equippedWindow || colorFor != null;
    }

    private boolean onKeyPressed(int keyCode) {
        if (keyCode == 256 && colorFor != null) { // Escape closes the window first
            colorFor = null;
            return true;
        }
        if (keyCode == 256 && equippedWindow) {
            equippedWindow = false;
            return true;
        }
        return false;
    }

    private boolean handleClick(double mx, double my) {
        if (colorFor != null && pickColorAt(mx, my, true)) return true;
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (r.contains(mx, my)) {
                r.action().run();
                return true;
            }
        }
        if (modalOpen()) {
            equippedWindow = false; // click outside the window closes it
            colorFor = null;
            return true;
        }
        if (mx >= previewX && mx < previewX + previewW && my >= bodyY && my < bodyBottom - 64) {
            draggingPreview = true;
            return true;
        }
        return false;
    }

    private boolean inPreviewArea(double mx, double my) {
        return mx >= previewX && mx < previewX + previewW && my >= bodyY && my < bodyBottom - 64;
    }

    private boolean handleRightClick(double mx, double my) {
        if (modalOpen() || !inPreviewArea(mx, my)) return false;
        panningPreview = true;
        return true;
    }

    private boolean handleDrag(double mx, double my, double dx, double dy) {
        if (colorFor != null) return (draggingWheel || draggingValue) && pickColorAt(mx, my, false);
        if (modalOpen()) return false;
        if (panningPreview) {
            float limitX = previewW * zoom / 2;
            float limitY = (bodyBottom - bodyY) * zoom / 2;
            panX = (float) Math.max(-limitX, Math.min(limitX, panX + dx));
            panY = (float) Math.max(-limitY, Math.min(limitY, panY + dy));
            return true;
        }
        if (!draggingPreview) return false;
        previewMode = PreviewMode.FREE;
        yaw = (float) ((yaw - dx * 2.0) % 360);
        pitch = (float) Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch - dy * 2.0));
        return true;
    }

    /** Mouse wheel over the preview zooms in and out (1x to 6x). */
    private boolean handleZoom(double mx, double my, double amount) {
        if (modalOpen() || !inPreviewArea(mx, my) || amount == 0) return false;
        float newZoom = (float) Math.max(1.0, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, amount)));
        // Keep the point under the cursor where it is: pan' = m - (m - pan) * zoom' / zoom,
        // with m the cursor relative to the middle of the preview area.
        float cx = previewX + previewW / 2.0F;
        float cy = (bodyY + 14 + bodyBottom - 64) / 2.0F;
        float ratio = newZoom / zoom;
        float mxRel = (float) mx - cx;
        float myRel = (float) my - cy;
        panX = mxRel - (mxRel - panX) * ratio;
        panY = myRel - (myRel - panY) * ratio;
        zoom = newZoom;
        float limitX = previewW * zoom / 2;
        float limitY = (bodyBottom - bodyY) * zoom / 2;
        panX = Math.max(-limitX, Math.min(limitX, panX));
        panY = Math.max(-limitY, Math.min(limitY, panY));
        if (zoom == 1.0F) {
            panX = 0;
            panY = 0;
        }
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
