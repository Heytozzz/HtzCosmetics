package com.heytozzz.htzcosmetics.gametest;

import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Automated in-game check: loads the signed local site, equips every cosmetic on the player and saves
 * screenshots from several angles (build/run/clientGameTest/screenshots). Run: gradlew :1.21.8-fabric:runClientGameTest
 */
public final class CosmeticsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");

            context.waitFor(mc -> !CosmeticLibrary.get().available().isEmpty(), 400);
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
