package com.heytozzz.htzcosmetics.gametest;

import com.heytozzz.htzcosmetics.client.ClientBootstrap;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automated in-game check: downloads the signed site from GitHub Pages (production path, with disk cache), asks the
 * real backend about a test player (row 00000000000040008000000000000001 in D1), equips every cosmetic and saves
 * screenshots (versions/1.21.8-fabric/build/run/clientGameTest/screenshots). Run: gradlew :1.21.8-fabric:runClientGameTest
 */
public final class CosmeticsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");

            // Downloads the signed manifest + assets from GitHub Pages (no dev folder in this run).
            context.waitFor(mc -> !CosmeticLibrary.get().available().isEmpty(), 600);

            // Real backend round trip with the production HTTPS client (test player prepared in the database).
            UUID probe = UUID.fromString(System.getProperty("htzcosmetics.test.probeUuid", "00000000-0000-4000-8000-000000000001"));
            try {
                Map<UUID, List<String>> remote = ClientBootstrap.backend().fetchEquipped(List.of(probe));
                System.out.println("[htz-gametest] backend says " + probe + " wears " + remote.get(probe));
                if (!List.of("top_hat").equals(remote.get(probe))) throw new AssertionError("unexpected backend answer: " + remote);
            } catch (Exception e) {
                throw new AssertionError("backend request failed", e);
            }
            context.runOnClient(mc -> {
                for (CosmeticDefinition def : CosmeticLibrary.get().available()) {
                    EquippedCosmetics.equip(mc.player.getUUID(), def);
                }
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0);
                mc.player.setXRot(0);
            });
            context.waitTicks(20);
            context.takeScreenshot("htz_front");

            context.runOnClient(mc -> mc.player.setXRot(35));
            context.waitTicks(5);
            context.takeScreenshot("htz_front_high");

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
            context.setScreen(() -> null);
        }
    }
}
