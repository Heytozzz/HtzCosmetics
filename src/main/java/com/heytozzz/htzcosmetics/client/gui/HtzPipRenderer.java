package com.heytozzz.htzcosmetics.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.RenderType;
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

import java.util.function.BiConsumer;

/**
 * Base for our 3D-in-menu renderers. Hides how geometry reaches the GPU in each Minecraft version:
 * drawn straight into a buffer (up to 26.1) or submitted and drawn later (26.2+). Subclasses only describe what
 * to draw through {@link Drawer}; the {@code body} may run later, so it must do its own posing (e.g. animation).
 */
public abstract class HtzPipRenderer<T extends PictureInPictureRenderState> extends PictureInPictureRenderer<T> {

    /** Draws {@code body} with {@code type} at the current {@code pose}. */
    @FunctionalInterface
    public interface Drawer {
        void draw(PoseStack pose, RenderType type, BiConsumer<PoseStack, VertexConsumer> body);
    }

    //? if >=26.2 {
    /*protected HtzPipRenderer() {
        super();
    }

    @Override
    protected void renderToTexture(T state, PoseStack pose, SubmitNodeCollector collector) {
        setupLighting(lighting());
        draw(state, pose, (p, type, body) -> collector.submitCustomGeometry(p, type, (entry, vertices) -> {
            PoseStack drawPose = new PoseStack();
            drawPose.last().set(entry);
            body.accept(drawPose, vertices);
        }));
    }
    *///?} else {
    protected HtzPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    protected void renderToTexture(T state, PoseStack pose) {
        setupLighting(lighting());
        draw(state, pose, (p, type, body) -> body.accept(p, bufferSource.getBuffer(type)));
        bufferSource.endBatch();
    }
    //?}

    /** Which lighting the 3D scene uses. */
    protected abstract Lighting.Entry lighting();

    /** Describes the scene; called once per frame for each visible state. */
    protected abstract void draw(T state, PoseStack pose, Drawer drawer);

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F; // origin in the middle of the area instead of at the bottom
    }

    private static void setupLighting(Lighting.Entry entry) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gameRenderer.lighting().setupFor(entry);
        *///?} else {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(entry);
        //?}
    }
}
