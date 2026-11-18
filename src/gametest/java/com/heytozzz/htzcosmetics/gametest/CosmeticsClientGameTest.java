package com.heytozzz.htzcosmetics.gametest;

import com.heytozzz.htzcosmetics.client.ChangeSignal;
import com.heytozzz.htzcosmetics.client.ClientBootstrap;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.client.gui.CosmeticsOptionsScreen;
import com.heytozzz.htzcosmetics.client.gui.CosmeticsScreen;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.signal.ChangeSignalPolicy;
import com.heytozzz.htzcosmetics.mixin.PlayerAccessor;
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
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
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
            byte before = context.computeOnClient(mc -> mc.player.getEntityData().get(PlayerAccessor.htzcosmetics$skinPartsData()));
            context.runOnClient(mc -> ChangeSignal.send());
            context.waitTicks(20);
            byte after = context.computeOnClient(mc -> mc.player.getEntityData().get(PlayerAccessor.htzcosmetics$skinPartsData()));
            System.out.println("[htz-gametest] skin parts byte before=" + (before & 0xFF) + " after=" + (after & 0xFF));
            if (((before ^ after) & ChangeSignalPolicy.SIGNAL_BIT) == 0) throw new AssertionError("signal bit was not relayed");
            if ((before & 0x7F) != (after & 0x7F)) throw new AssertionError("visible skin parts changed");

            context.runOnClient(mc -> {
                mc.options.hideGui = true;
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
                mc.options.hideGui = false;
            });
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitTicks(5);
            context.takeScreenshot("htz_inventory");

            context.setScreen(() -> new CosmeticsScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("htz_equip_screen");

            context.setScreen(() -> new CosmeticsOptionsScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("htz_options_screen");
            context.setScreen(() -> null);
        }
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
