package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Small square "wardrobe" button (vanilla button background + our icon) added to the title and pause menus.
 * It is placed next to a vanilla button it can recognise, so it follows layouts changed by other mods; if that
 * button is not there (e.g. the reduced pause menu), nothing is added.
 */
public final class WardrobeButton {
    public static final String MESSAGE_KEY = "htzcosmetics.button.wardrobe";
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "wardrobe");
    private static final int SIZE = 20;
    private static final int GAP = 4;

    private WardrobeButton() {
    }

    /**
     * @param buttons the screen's current widgets (read-only use)
     * @return the button to add, or null if this screen gets none
     */
    public static SpriteIconButton forScreen(Screen screen, List<? extends AbstractWidget> buttons) {
        int x;
        int y;
        if (screen instanceof TitleScreen) {
            AbstractWidget language = find(buttons, "options.language");
            if (language == null) return null;
            x = language.getX() - SIZE - GAP; // left of the language button
            y = language.getY();
        } else if (screen instanceof PauseScreen) {
            AbstractWidget back = find(buttons, "menu.returnToGame");
            if (back == null) return null;
            x = back.getX() + back.getWidth() + GAP; // right of "Back to Game"
            y = back.getY();
        } else {
            return null;
        }
        Component label = Component.translatable(MESSAGE_KEY);
        SpriteIconButton button = SpriteIconButton.builder(label,
                b -> Minecraft.getInstance().setScreen(new CosmeticsScreen(screen)), true)
            .sprite(ICON, 16, 16)
            .size(SIZE, SIZE)
            .build();
        button.setPosition(x, y);
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    private static AbstractWidget find(List<? extends AbstractWidget> buttons, String key) {
        for (AbstractWidget w : buttons) {
            if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) return w;
        }
        return null;
    }
}
