package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.client.ClientState;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.client.PlayerUuidAccess;
import com.heytozzz.htzcosmetics.core.config.ClientConfig;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Player render layer that draws equipped cosmetics attached to the posed body parts.
 * Runs every frame: only map lookups and drawing, never I/O or parsing.
 *
 * <p>Since 1.21.9 layers SUBMIT their geometry and Minecraft draws it later. Baked cosmetic parts are shared by all
 * players, so the animation pose is applied inside the deferred draw callback, right before each cosmetic is drawn.
 */
public final class CosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    public CosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Draws (or submits) one cosmetic whose anchor transform is already on {@code pose}. */
    @FunctionalInterface
    private interface Drawer {
        void draw(BakedCosmetic cosmetic, PoseStack pose, int overlay, float seconds, AnimationClip.Trigger trigger);
    }

    //? if >=1.21.9 {
    /*@Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight, PlayerRenderState state,
                       float yRot, float xRot) {
        forEachCosmetic(pose, state, (c, p, overlay, seconds, trigger) ->
            collector.submitCustomGeometry(p, CosmeticRenderUtil.renderType(c), (entry, vertices) -> {
                PoseStack drawPose = new PoseStack();
                drawPose.last().set(entry);
                CosmeticAnimator.poseAt(c, seconds, trigger);
                c.root().render(drawPose, vertices, packedLight, overlay);
            }));
    }
    *///?} else {
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerRenderState state,
                       float yRot, float xRot) {
        forEachCosmetic(pose, state, (c, p, overlay, seconds, trigger) -> {
            CosmeticAnimator.poseAt(c, seconds, trigger);
            c.root().render(p, buffers.getBuffer(CosmeticRenderUtil.renderType(c)), packedLight, overlay);
        });
    }
    //?}

    private void forEachCosmetic(PoseStack pose, PlayerRenderState state, Drawer drawer) {
        if (state.isInvisible || state.isSpectator) return;
        UUID uuid = ((PlayerUuidAccess) state).htzcosmetics$getUuid();
        if (uuid == null || !visibleByOptions(uuid)) return;
        List<String> ids = EquippedCosmetics.get(uuid);
        if (ids.isEmpty()) return;

        PlayerModel model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        float seconds = state.ageInTicks / 20.0F;
        AnimationClip.Trigger trigger = CosmeticAnimator.trigger(state);
        EnumSet<Category> drawn = EnumSet.noneOf(Category.class);
        for (String id : ids) {
            BakedCosmetic cosmetic = CosmeticLibrary.get().baked(id);
            if (cosmetic == null) continue; // not loaded yet, or rejected by validation
            if (!drawn.add(cosmetic.definition().category())) continue; // one per category
            ModelPart anchor = CosmeticRenderUtil.anchorPart(model, cosmetic.definition().anchor());
            if (!anchor.visible) continue;

            pose.pushPose();
            AnchorTransform.apply(pose, model.root(), anchor);
            drawer.draw(cosmetic, pose, overlay, seconds, trigger);
            pose.popPose();
        }
    }

    /** Player options: hide others, hide own, and own cosmetics in first person (for first-person body mods). */
    private static boolean visibleByOptions(UUID uuid) {
        ClientConfig config = ClientState.config();
        if (!uuid.equals(ClientState.self())) return config.showOthers;
        if (!config.showOwn) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean inGameFirstPerson = mc.options.getCameraType().isFirstPerson() && mc.screen == null;
        return !inGameFirstPerson || config.showOwnFirstPerson;
    }
}
