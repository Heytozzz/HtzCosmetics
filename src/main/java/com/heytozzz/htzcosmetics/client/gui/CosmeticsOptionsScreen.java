package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.ClientState;
import com.heytozzz.htzcosmetics.core.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Player options: what to show and whether to send/receive the change signal. Saved immediately. */
public final class CosmeticsOptionsScreen extends Screen {
    private final Screen parent;
    private int nextY;

    public CosmeticsOptionsScreen(Screen parent) {
        super(Component.translatable("htzcosmetics.options.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nextY = 40;
        toggle("show_others", c -> c.showOthers, (c, v) -> c.showOthers = v);
        toggle("show_own", c -> c.showOwn, (c, v) -> c.showOwn = v);
        toggle("show_own_first_person", c -> c.showOwnFirstPerson, (c, v) -> c.showOwnFirstPerson = v);
        toggle("send_signal", c -> c.sendChangeSignal, (c, v) -> c.sendChangeSignal = v);
        toggle("receive_signal", c -> c.receiveChangeSignal, (c, v) -> c.receiveChangeSignal = v);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 100, nextY + 12, 200, 20).build());
    }

    private void toggle(String key, Function<ClientConfig, Boolean> getter, BiConsumer<ClientConfig, Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(getter.apply(ClientState.config()))
            .create(width / 2 - 155, nextY, 310, 20, Component.translatable("htzcosmetics.options." + key),
                (b, value) -> {
                    setter.accept(ClientState.config(), value);
                    ClientState.saveConfig();
                });
        button.setTooltip(Tooltip.create(Component.translatable("htzcosmetics.options." + key + ".tooltip")));
        addRenderableWidget(button);
        nextY += 24;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
