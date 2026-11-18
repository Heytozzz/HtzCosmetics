package com.heytozzz.htzcosmetics.gametest;

import com.heytozzz.htzcosmetics.client.ChangeSignal;
import com.heytozzz.htzcosmetics.client.ClientBootstrap;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.client.gui.CosmeticsOptionsScreen;
import com.heytozzz.htzcosmetics.client.gui.CosmeticsScreen;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.signal.ChangeSignalPolicy;
import com.heytozzz.htzcosmetics.mixin.SkinPartsAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automated in-game check (run: gradlew :1.21.8-fabric:runClientGameTest):
 * <ol>
 *   <li>downloads the signed site from GitHub Pages (production path, with disk cache);</li>
 *   <li>asks the real backend about a test player (row 00000000000040008000000000000001 in D1);</li>
 *   <li>equips every cosmetic through the normal player flow (save is "local only" on this offline dev account);</li>
 *   <li>checks that the server relays the change-signal bit;</li>
 *   <li>saves screenshots (versions/1.21.8-fabric/build/run/clientGameTest/screenshots).</li>
 * </ol>
 */
public final class CosmeticsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        enableCompatPacks(context);
        context.waitFor(mc -> mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen, 600);
        context.waitTicks(60); // let the title screen finish fading in
        context.takeScreenshot("htz_title_screen");
        context.waitFor(mc -> CosmeticLibrary.get().definition("top_hat") != null, 600);
        context.runOnClient(mc -> OwnCosmetics.equip(CosmeticLibrary.get().definition("top_hat")));
        clickWardrobeButton(context, "title");
        context.runOnClient(mc -> ((CosmeticsScreen) mc.screen).regionCenter("back")); // keep screen
        context.takeScreenshot("htz_title_preview_front");
        click(context, "preview:back");
        context.takeScreenshot("htz_title_preview_back");
        click(context, "preview:front");
        zoomAndMove(context, "title");
        click(context, "back");
        check(context, mc -> mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen,
            "back arrow returns to the title screen");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            //? if >=26.3 {
            /*world.getConnection().waitForChunksRender();
            *///?} else if >=26.1 {
            /*world.getClientLevel().waitForChunksRender();
            *///?} else {
            world.getClientWorld().waitForChunksRender();
            //?}
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");

            context.waitFor(mc -> !CosmeticLibrary.get().available().isEmpty(), 600);

            UUID probe = UUID.fromString("00000000-0000-4000-8000-000000000001");
            try {
                Map<UUID, List<String>> remote = ClientBootstrap.backend().fetchEquipped(List.of(probe));
                System.out.println("[htz-gametest] backend says " + probe + " wears " + remote.get(probe));
                if (!List.of("top_hat").equals(remote.get(probe))) throw new AssertionError("unexpected backend answer: " + remote);
            } catch (Exception e) {
                throw new AssertionError("backend request failed", e);
            }

            // Normal player flow: equip -> debounced save. A dev account has no Mojang certificate: LOCAL_ONLY.
            context.runOnClient(mc -> {
                for (CosmeticDefinition def : CosmeticLibrary.get().available()) OwnCosmetics.equip(def);
            });
            context.waitFor(mc -> OwnCosmetics.status() != OwnCosmetics.Status.SAVING, 600);
            OwnCosmetics.Status status = context.computeOnClient(mc -> OwnCosmetics.status());
            System.out.println("[htz-gametest] save status: " + status);
            if (status != OwnCosmetics.Status.LOCAL_ONLY) throw new AssertionError("expected LOCAL_ONLY, got " + status);

            // Change signal: the bit we send must come back through the server's synced player data.
            byte before = context.computeOnClient(mc -> mc.player.getEntityData().get(SkinPartsAccessor.htzcosmetics$skinPartsData()));
            context.runOnClient(mc -> ChangeSignal.send());
            context.waitTicks(20);
            byte after = context.computeOnClient(mc -> mc.player.getEntityData().get(SkinPartsAccessor.htzcosmetics$skinPartsData()));
            System.out.println("[htz-gametest] skin parts byte before=" + (before & 0xFF) + " after=" + (after & 0xFF));
            if (((before ^ after) & ChangeSignalPolicy.SIGNAL_BIT) == 0) throw new AssertionError("signal bit was not relayed");
            if ((before & 0x7F) != (after & 0x7F)) throw new AssertionError("visible skin parts changed");

            context.runOnClient(mc -> {
                hideGui(mc, true);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0);
                mc.player.setXRot(0);
            });
            context.waitTicks(20);
            context.takeScreenshot("htz_front");

            // Motion: walking, sneaking and looking down (animation mods move the head in these poses).
            context.getInput().holdKey(o -> o.keyUp);
            context.waitTicks(8);
            context.takeScreenshot("htz_walk");
            context.getInput().releaseKey(o -> o.keyUp);
            context.getInput().holdKey(o -> o.keyShift);
            context.waitTicks(10);
            context.takeScreenshot("htz_sneak");
            context.getInput().releaseKey(o -> o.keyShift);
            context.runOnClient(mc -> mc.player.setXRot(40));
            context.waitTicks(10);
            context.takeScreenshot("htz_look_down");

            // Animated cosmetic (if published): two shots 1 s apart must differ (spin + float).
            boolean hasHalo = context.computeOnClient(mc -> CosmeticLibrary.get().definition("angel_halo") != null);
            if (hasHalo) {
                context.runOnClient(mc -> {
                    OwnCosmetics.equip(CosmeticLibrary.get().definition("angel_halo"));
                    mc.player.setXRot(15);
                });
                context.waitTicks(5);
                context.takeScreenshot("htz_halo_a");
                context.waitTicks(15);
                context.takeScreenshot("htz_halo_b");
                context.runOnClient(mc -> OwnCosmetics.equip(CosmeticLibrary.get().definition("top_hat")));
            }

            context.runOnClient(mc -> {
                mc.player.setXRot(10);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(5);
            context.takeScreenshot("htz_back");

            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                hideGui(mc, false);
            });
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("htz_inventory");

            context.setScreen(() -> new CosmeticsScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("htz_equip_screen");
            // Real mouse clicks on the wardrobe (what a player does).
            click(context, "equipped_button");
            check(context, mc -> ((CosmeticsScreen) mc.screen).isEquippedWindowOpen(), "Equipped button opens the window");
            context.takeScreenshot("htz_equipped_window");
            click(context, "window:remove:top_hat");
            check(context, mc -> !OwnCosmetics.isEquipped("top_hat"), "Remove takes the cosmetic off");
            clickAt(context, 3, 3);
            check(context, mc -> !((CosmeticsScreen) mc.screen).isEquippedWindowOpen(), "click outside closes the window");
            if (hasHalo) {
                click(context, "card:angel_halo");
                check(context, mc -> OwnCosmetics.isEquipped("angel_halo"), "clicking a card equips it");
                click(context, "equipped_button");
                click(context, "window:go:angel_halo");
                check(context, mc -> {
                    CosmeticsScreen s = (CosmeticsScreen) mc.screen;
                    return !s.isEquippedWindowOpen() && s.selectedCategory() == com.heytozzz.htzcosmetics.core.manifest.Category.HEAD;
                }, "Go closes the window and jumps to the cosmetic's category");
                context.takeScreenshot("htz_after_go");
            }
            // Drag the preview to rotate the player.
            int[] center = context.computeOnClient(mc -> ((CosmeticsScreen) mc.screen).previewCenter());
            double guiScale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
            context.getInput().setCursorPos(center[0] * guiScale, center[1] * guiScale);
            context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTick();
            context.getInput().moveCursor(60 * guiScale, 0);
            context.waitTick();
            context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            check(context, mc -> ((CosmeticsScreen) mc.screen).previewYaw() != 0, "dragging rotates the preview");
            context.takeScreenshot("htz_preview_dragged");
            zoomAndMove(context, "world");

            // Big screen: 1920x1080 at GUI scale 3 (rarity filter shows as chips).
            context.runOnClient(mc -> mc.options.guiScale().set(3));
            context.getInput().resizeWindow(1920, 1080); // the resize applies the new GUI scale
            context.waitTicks(5);
            context.takeScreenshot("htz_equip_screen_1080p");

            context.setScreen(() -> null);
            context.runOnClient(mc -> mc.pauseGame(false));
            context.waitTicks(3);
            context.takeScreenshot("htz_pause_screen");
            clickWardrobeButton(context, "pause");

            context.setScreen(() -> new CosmeticsOptionsScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("htz_options_screen");
            context.setScreen(() -> null);
        }
    }

    /** Hides the HUD for cleaner screenshots (not available in every version; then it is skipped). */
    private static void hideGui(net.minecraft.client.Minecraft mc, boolean hide) {
        //? if <26.2 {
        mc.options.hideGui = hide;
        //?}
    }

    /** Finds our wardrobe button on the current screen, clicks it, and checks the wardrobe opened. */
    private static void clickWardrobeButton(ClientGameTestContext context, String where) {
        int[] p = context.computeOnClient(mc -> {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals(com.heytozzz.htzcosmetics.client.gui.WardrobeButton.MESSAGE_KEY)) {
                    return new int[]{w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
                }
            }
            return null;
        });
        if (p == null) throw new AssertionError("no wardrobe button on the " + where + " screen");
        clickAt(context, p[0], p[1]);
        check(context, mc -> mc.screen instanceof CosmeticsScreen, "wardrobe button on the " + where + " screen opens the wardrobe");
        context.takeScreenshot("htz_wardrobe_from_" + where);
    }

    /** Mouse wheel over the preview, then a right-button drag; checks zoom and pan changed, then resets. */
    private static void zoomAndMove(ClientGameTestContext context, String where) {
        int[] center = context.computeOnClient(mc -> ((CosmeticsScreen) mc.screen).previewCenter());
        double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.getInput().scroll(4);
        context.waitTicks(2);
        check(context, mc -> ((CosmeticsScreen) mc.screen).previewZoom() > 1.5F, "wheel zooms the preview (" + where + ")");
        context.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTick();
        context.getInput().moveCursor(30 * scale, 40 * scale); // right and down
        context.waitTick();
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitTicks(2);
        check(context, mc -> ((CosmeticsScreen) mc.screen).previewPan()[1] > 10, "right drag moves the preview (" + where + ")");
        context.takeScreenshot("htz_preview_zoom_" + where);
        click(context, "preview:reset");
        check(context, mc -> ((CosmeticsScreen) mc.screen).previewZoom() == 1.0F, "reset restores the view (" + where + ")");
    }

    private static void click(ClientGameTestContext context, String key) {
        int[] p = context.computeOnClient(mc -> ((CosmeticsScreen) mc.screen).regionCenter(key));
        if (p == null) throw new AssertionError("nothing clickable named " + key);
        clickAt(context, p[0], p[1]);
    }

    private static void clickAt(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiX * scale + scale / 2, guiY * scale + scale / 2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void check(ClientGameTestContext context,
                              java.util.function.Predicate<net.minecraft.client.Minecraft> condition, String what) {
        if (!context.computeOnClient(condition::test)) throw new AssertionError("failed: " + what);
        System.out.println("[htz-gametest] ok: " + what);
    }

    /**
     * Compatibility run (gradlew :1.21.8-fabric:runClientGameTest -PhtzCompat): enables the resource packs found in
     * devmods/resourcepacks (e.g. Fresh Animations) before the world is created.
     */
    private static void enableCompatPacks(ClientGameTestContext context) {
        String dir = System.getProperty("htzcosmetics.test.packs");
        if (dir == null || dir.isBlank()) return;
        context.runOnClient(mc -> {
            List<String> ids = new java.util.ArrayList<>();
            try (var files = java.nio.file.Files.list(java.nio.file.Path.of(dir))) {
                for (java.nio.file.Path zip : files.filter(f -> f.toString().endsWith(".zip")).sorted().toList()) {
                    java.nio.file.Files.copy(zip, mc.getResourcePackDirectory().resolve(zip.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    ids.add("file/" + zip.getFileName());
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            var repo = mc.getResourcePackRepository();
            repo.reload();
            List<String> selected = new java.util.ArrayList<>(repo.getSelectedIds());
            // Base pack first, then the extension (later = higher priority).
            ids.sort(java.util.Comparator.comparing((String id) -> id.contains("+")));
            selected.addAll(ids);
            repo.setSelected(selected);
            System.out.println("[htz-gametest] enabling packs " + ids);
            mc.options.updateResourcePacks(repo);
        });
        context.waitTicks(5);
        context.waitFor(mc -> mc.getOverlay() == null, 2400);
    }
}
