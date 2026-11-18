// stonecutter.gradle.kts — Stonecutter's control file.
// The line below says which version is "active" (the one your IDE shows and runs).
stonecutter active "1.21.8-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Lets stonecutter.properties.toml have sections per version and per loader.
    properties {
        tags(version, loader)
    }

    // Enables `//? if fabric {` ... `//?}` comments in Java code.
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    // `/*$ mod_version*/ "x"` in Java gets replaced by the real version string.
    swaps["mod_version"] = "\"${properties["mod.version"]}\";"
    swaps["minecraft"] = "\"${current.version}\";"

    replacements {
        // 1.21.9 renamed the player renderer and its render state ("avatars" also cover mannequins).
        string(current.parsed >= "1.21.9") {
            replace("PlayerRenderState", "AvatarRenderState")
        }
        string(current.parsed >= "1.21.9") {
            replace("PlayerRenderer", "AvatarRenderer")
        }
        // 1.21.11 moved the player model and render types into new packages.
        string(current.parsed >= "1.21.11") {
            replace("net.minecraft.client.model.PlayerModel", "net.minecraft.client.model.player.PlayerModel")
        }
        string(current.parsed >= "1.21.11") {
            replace("net.minecraft.client.renderer.RenderType;", "net.minecraft.client.renderer.rendertype.RenderType;")
        }
        // (The RenderType factory methods moved to RenderTypes: handled in CosmeticRenderUtil.)
        // 26.1: the GUI "render" pass became an "extract" pass (GuiGraphics -> GuiGraphicsExtractor and renames).
        string(current.parsed >= "26.1") { replace("public void render(", "public void extractRenderState(") }
        string(current.parsed >= "26.1") { replace("public void renderBackground(", "public void extractBackground(") }
        // Fabric API 26.x adopted Mojang names.
        string(current.parsed >= "26.1") { replace("ClientCommandManager", "ClientCommands") }
        string(current.parsed >= "26.1") { replace("keybinding.v1.KeyBindingHelper", "keymapping.v1.KeyMappingHelper") }
        string(current.parsed >= "26.1") { replace("KeyBindingHelper.registerKeyBinding", "KeyMappingHelper.registerKeyMapping") }
        string(current.parsed >= "26.1") { replace("LivingEntityFeatureRendererRegistrationCallback", "LivingEntityRenderLayerRegistrationCallback") }
        string(current.parsed >= "26.1") { replace("SpecialGuiElementRegistry", "PictureInPictureRendererRegistry") }
        string(current.parsed >= "26.1") { replace("Screens.getButtons(", "Screens.getWidgets(") }
        string(current.parsed >= "26.1") { replace("super.render(g,", "super.extractRenderState(g,") }
        string(current.parsed >= "26.1") { replace("search.render(g,", "search.extractRenderState(g,") }
        string(current.parsed >= "26.1") { replace("g.drawString(", "g.text(") }
        string(current.parsed >= "26.1") { replace("g.drawCenteredString(", "g.centeredText(") }
        string(current.parsed >= "26.1") { replace("submitPicturesInPictureState(", "addPicturesInPictureState(") }
        string(current.parsed >= "26.1") {
            replace("net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState",
                "net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState")
        }
        string(current.parsed >= "26.1") { replace("import net.minecraft.client.renderer.LightTexture;", "import net.minecraft.util.LightCoordsUtil;") }
        string(current.parsed >= "26.1") { replace("LightTexture.FULL_BRIGHT", "LightCoordsUtil.FULL_BRIGHT") }
        string(current.parsed >= "26.1") { replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphicsExtractor;") }
        string(current.parsed >= "26.1") { replace("GuiGraphics g,", "GuiGraphicsExtractor g,") }
        // 26.2: screens are managed by Minecraft.gui; 26.3: PoseStack.mulPose(Quaternion) became rotate(...).
        string(current.parsed >= "26.2") { replace("minecraft.setScreen(", "minecraft.gui.setScreen(") }
        string(current.parsed >= "26.2") { replace("Minecraft.getInstance().setScreen(", "Minecraft.getInstance().gui.setScreen(") }
        string(current.parsed >= "26.2") { replace("client.setScreen(", "client.gui.setScreen(") }
        string(current.parsed >= "26.2") { replace("mc.setScreen(", "mc.gui.setScreen(") }
        string(current.parsed >= "26.2") { replace("client.screen ", "client.gui.screen() ") }
        string(current.parsed >= "26.2") { replace("mc.screen ", "mc.gui.screen() ") }
        string(current.parsed >= "26.2") { replace("mc.screen)", "mc.gui.screen())") }
        string(current.parsed >= "26.2") { replace("mc.screen.", "mc.gui.screen().") }
        string(current.parsed >= "26.3") { replace("pose.mulPose(Axis.", "pose.rotate(Axis.") }
        string(current.parsed >= "26.2") { replace("mc.screen;", "mc.gui.screen();") }
        string(current.parsed >= "26.2") { replace("mc.getOverlay()", "mc.gui.overlay()") }
        // Mojang renamed ResourceLocation -> Identifier in 1.21.11.
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
