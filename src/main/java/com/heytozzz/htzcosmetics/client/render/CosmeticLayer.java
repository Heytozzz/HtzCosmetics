package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.EquippedCosmetics;
import com.heytozzz.htzcosmetics.client.PlayerUuidAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import java.util.Collection;
import java.util.UUID;

/**
 * Player render layer that draws equipped cosmetics attached to the posed body parts.
 * Runs every frame: only map lookups and drawing, never I/O or parsing.
 */
public final class CosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    public CosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerRenderState state,
                       float yRot, float xRot) {
        if (state.isInvisible || state.isSpectator) return;
        UUID uuid = ((PlayerUuidAccess) state).htzcosmetics$getUuid();
        if (uuid == null) return;
        Collection<String> ids = EquippedCosmetics.get(uuid);
        if (ids.isEmpty()) return;

        PlayerModel model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        for (String id : ids) {
            BakedCosmetic cosmetic = CosmeticLibrary.get().baked(id);
            if (cosmetic == null) continue; // not loaded yet, or rejected by validation
            ModelPart anchor = switch (cosmetic.definition().anchor()) {
                case HEAD -> model.head;
                case BODY -> model.body;
                case LEFT_ARM -> model.leftArm;
                case RIGHT_ARM -> model.rightArm;
                case LEFT_LEG -> model.leftLeg;
                case RIGHT_LEG -> model.rightLeg;
            };
            if (!anchor.visible) continue;

            pose.pushPose();
            anchor.translateAndRotate(pose);
            VertexConsumer vertices = buffers.getBuffer(cosmetic.translucent()
                ? RenderType.entityTranslucent(cosmetic.texture())
                : RenderType.entityCutoutNoCull(cosmetic.texture()));
            cosmetic.root().render(pose, vertices, packedLight, overlay);
            pose.popPose();
        }
    }
}
