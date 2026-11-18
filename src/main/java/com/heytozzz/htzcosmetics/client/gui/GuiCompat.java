package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.9 {
/*import net.minecraft.world.entity.player.PlayerModelType;
*///?} else {
import net.minecraft.client.resources.PlayerSkin;
//?}

import java.util.function.Supplier;

/** Small differences between Minecraft versions in GUI-related APIs, kept in one place. */
public final class GuiCompat {
    private GuiCompat() {
    }

    /** The account's skin texture and arm type. */
    public record SkinInfo(ResourceLocation texture, boolean slim) {
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        //? if >=1.21.9 && <1.21.11 {
        /*g.submitOutline(x, y, w, h, color);
        *///?} else {
        g.renderOutline(x, y, w, h, color);
        //?}
    }

    /** Skin of the logged-in account; the default skin until the real one has downloaded. */
    public static Supplier<SkinInfo> accountSkin(Minecraft mc) {
        //? if >=1.21.9 {
        /*var lookup = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
        return () -> {
            var skin = lookup.get();
            return new SkinInfo(skin.body().texturePath(), skin.model() == PlayerModelType.SLIM);
        };
        *///?} else {
        var lookup = mc.getSkinManager().lookupInsecure(mc.getGameProfile());
        return () -> {
            var skin = lookup.get();
            return new SkinInfo(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
        };
        //?}
    }

    /** Creates the "open wardrobe" key mapping in our own controls category. */
    public static KeyMapping keyMapping(String name, int defaultKey) {
        //? if >=1.21.9 {
        /*KeyMapping.Category category = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "main"));
        return new KeyMapping(name, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, defaultKey, category);
        *///?} else {
        return new KeyMapping(name, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, defaultKey,
            "key.categories." + HtzCosmetics.MOD_ID);
        //?}
    }
}
