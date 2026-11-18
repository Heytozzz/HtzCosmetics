package com.heytozzz.htzcosmetics.fabric;

//? if fabric {
import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.client.ClientBootstrap;
import com.heytozzz.htzcosmetics.client.CosmeticLibrary;
import com.heytozzz.htzcosmetics.client.OwnCosmetics;
import com.heytozzz.htzcosmetics.client.RemoteSync;
import com.heytozzz.htzcosmetics.client.gui.CosmeticsScreen;
import com.heytozzz.htzcosmetics.client.gui.GuiCompat;
import com.heytozzz.htzcosmetics.client.gui.PlayerPreviewRenderer;
import com.heytozzz.htzcosmetics.client.gui.ThumbnailsRenderer;
import com.heytozzz.htzcosmetics.client.gui.WardrobeButton;
import com.heytozzz.htzcosmetics.client.render.CosmeticLayer;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Fabric client entrypoint. Only Fabric-specific glue lives in this package;
 * the real logic is in {@code client} and {@code core} so other loaders can reuse it.
 */
public final class HtzCosmeticsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HtzCosmetics.LOGGER.info("HtzCosmetics {} loaded on Minecraft {}", HtzCosmetics.VERSION, HtzCosmetics.MINECRAFT);

        // Adds our render layer to both player renderers (wide and slim arms).
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer playerRenderer) helper.register(new CosmeticLayer(playerRenderer));
        });

        // Wardrobe button on the title and pause menus.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            var buttons = Screens.getButtons(screen);
            var button = WardrobeButton.forScreen(screen, buttons);
            if (button != null) buttons.add(button);
        });

        // 3D cosmetic thumbnails in the wardrobe menu.
        //? if >=26.2 {
        /*SpecialGuiElementRegistry.register(ctx -> new ThumbnailsRenderer());
        SpecialGuiElementRegistry.register(ctx -> new PlayerPreviewRenderer());
        *///?} else if >=26.1 {
        /*SpecialGuiElementRegistry.register(ctx -> new ThumbnailsRenderer(ctx.bufferSource()));
        SpecialGuiElementRegistry.register(ctx -> new PlayerPreviewRenderer(ctx.bufferSource()));
        *///?} else {
        SpecialGuiElementRegistry.register(ctx -> new ThumbnailsRenderer(ctx.vertexConsumers()));
        SpecialGuiElementRegistry.register(ctx -> new PlayerPreviewRenderer(ctx.vertexConsumers()));
        //?}

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ClientBootstrap.init());
        KeyMapping openKey = KeyBindingHelper.registerKeyBinding(GuiCompat.keyMapping("key.htzcosmetics.open", InputConstants.KEY_J));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RemoteSync.tick(client);
            while (openKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new CosmeticsScreen(null));
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal(HtzCosmetics.MOD_ID)
                .executes(HtzCosmeticsFabricClient::status)
                .then(ClientCommandManager.literal("list").executes(HtzCosmeticsFabricClient::list))
                .then(ClientCommandManager.literal("menu").executes(ctx -> {
                    // Deferred: the chat screen closes right after the command runs.
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new CosmeticsScreen(null)));
                    return 1;
                }))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    ClientBootstrap.loadCosmetics(true);
                    ctx.getSource().sendFeedback(Component.translatable("htzcosmetics.command.reloading"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("equip")
                    .then(ClientCommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (CosmeticDefinition def : CosmeticLibrary.get().available()) builder.suggest(def.id());
                            return builder.buildFuture();
                        })
                        .executes(HtzCosmeticsFabricClient::equip)))
                .then(ClientCommandManager.literal("unequip")
                    .then(ClientCommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("all");
                            for (CosmeticDefinition def : CosmeticLibrary.get().available()) {
                                if (OwnCosmetics.isEquipped(def.id())) builder.suggest(def.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(HtzCosmeticsFabricClient::unequip)))));
    }

    private static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.translatable("htzcosmetics.command.status",
            HtzCosmetics.VERSION, CosmeticLibrary.get().available().size()));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        var available = CosmeticLibrary.get().available();
        if (available.isEmpty()) {
            ctx.getSource().sendFeedback(Component.translatable("htzcosmetics.command.none"));
            return 0;
        }
        for (CosmeticDefinition def : available) {
            // Names are plain text checked by the manifest parser; Component.literal never interprets formatting.
            ctx.getSource().sendFeedback(Component.literal("- " + def.id() + " : " + def.displayName(language())
                + " [" + def.category() + ", " + def.rarity() + "]"));
        }
        return available.size();
    }

    private static int equip(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        CosmeticDefinition def = CosmeticLibrary.get().definition(id);
        if (def == null) {
            ctx.getSource().sendError(Component.translatable("htzcosmetics.command.unknown", id));
            return 0;
        }
        OwnCosmetics.equip(def);
        ctx.getSource().sendFeedback(Component.translatable("htzcosmetics.command.equipped",
            Component.literal(def.displayName(language()))));
        return 1;
    }

    private static int unequip(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        if (id.equals("all")) {
            OwnCosmetics.unequipAll();
            ctx.getSource().sendFeedback(Component.translatable("htzcosmetics.command.unequipped_all"));
            return 1;
        }
        boolean removed = OwnCosmetics.unequip(id);
        ctx.getSource().sendFeedback(Component.translatable(removed
            ? "htzcosmetics.command.unequipped" : "htzcosmetics.command.not_worn", id));
        return removed ? 1 : 0;
    }
}
//?}
