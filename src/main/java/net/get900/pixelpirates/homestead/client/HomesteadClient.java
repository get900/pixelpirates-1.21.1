package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.minecraft.client.render.RenderLayer;

/** Client registrations for the homestead content (called from PixelPiratesClient). */
@Environment(EnvType.CLIENT)
public final class HomesteadClient {
    private HomesteadClient() {}

    public static void init() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HomesteadBlocks.PINEAPPLE_CROP, HomesteadBlocks.LIME_CROP, HomesteadBlocks.CHILI_CROP,
                HomesteadBlocks.PALM_DOOR, HomesteadBlocks.PALM_TRAPDOOR, HomesteadBlocks.WOVEN_PALM_SCREEN, HomesteadBlocks.ROPE_LADDER,
                HomesteadBlocks.TIKI_TORCH, HomesteadBlocks.HANGING_NET, HomesteadBlocks.HANGING_ROPE, HomesteadBlocks.FISH_TRAP, HomesteadBlocks.LOBSTER_POT);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(net.get900.pixelpirates.homestead.HomesteadItems.SALVAGE_HOOK,
                new net.minecraft.util.Identifier("cast"), (stack, world, entity, seed) -> {
                    if (!(entity instanceof net.minecraft.entity.player.PlayerEntity p)) return 0f;
                    boolean main = p.getMainHandStack() == stack, off = p.getOffHandStack() == stack && !(p.getMainHandStack().getItem() instanceof net.minecraft.item.FishingRodItem);
                    return (main || off) && p.fishHook != null ? 1f : 0f;
                });
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.DISPLAY, DisplayRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.SEAT,
                net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.MUSKET_BALL,
                net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.PORT_TRADER, PortTraderRenderer::new);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(net.get900.pixelpirates.homestead.HomesteadItems.COMPASS_OF_DESIRE,
                new net.minecraft.util.Identifier("angle"), new net.minecraft.client.item.CompassAnglePredicateProvider(
                        (world, stack, entity) -> net.get900.pixelpirates.homestead.nav.CompassOfDesireItem.target(stack)));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.JOLLY_ROGER,
                ctx -> new software.bernie.geckolib.renderer.GeoBlockRenderer<>(new net.get900.pixelpirates.entity.client.NamedGeoModel<net.get900.pixelpirates.homestead.hideout.JollyRogerBlockEntity>("jolly_roger")) {
                    @Override
                    public boolean rendersOutsideBoundingBox(net.get900.pixelpirates.homestead.hideout.JollyRogerBlockEntity be) { return true; }
                });
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.GRAPPLE_HOOK, GrappleHookRenderer::new);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            net.minecraft.item.ItemStack st = player.getStackInHand(hand);
            if (world.isClient && st.isOf(net.get900.pixelpirates.homestead.HomesteadItems.CAPTAINS_LOGBOOK) && st.hasNbt() && st.getNbt().contains("pages"))
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ingame.BookScreen(
                        new net.minecraft.client.gui.screen.ingame.BookScreen.WrittenBookContents(st)));
            return net.minecraft.util.TypedActionResult.pass(st);
        });
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.ROULETTE_TABLE,
                ctx -> new RouletteRenderer());
        GunRenderer pistol = new GunRenderer("flintlock_pistol"), blunderbuss = new GunRenderer("blunderbuss");
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(net.get900.pixelpirates.homestead.HomesteadItems.FLINTLOCK_PISTOL, pistol::render);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(net.get900.pixelpirates.homestead.HomesteadItems.BLUNDERBUSS, blunderbuss::render);
    }
}
