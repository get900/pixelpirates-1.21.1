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
        // PARROT TYPES phase 2: the glow layer of the Ghost Parrot, Ember Macaw and Kraken's Pet
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof net.minecraft.client.render.entity.ParrotEntityRenderer r) helper.register(new ParrotGlowFeature(r));
            if (renderer instanceof net.minecraft.client.render.entity.CatEntityRenderer r) helper.register(new CatGlowFeature(r));     // ship's cats
            if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer r) helper.register(new TattooFeature(r)); // tattoos
            if (renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer r) helper.register(new BeardFeature(r));  // facial hair
        });
        TattooFeature.registerClient();
        // the townsfolk (homestead/town): their models, the dialogue card
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.TOWNSFOLK, TownsfolkRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.get900.pixelpirates.homestead.town.TownTalk.OPEN, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            String name = buf.readString(), title = buf.readString(), say = buf.readString();
            String friend = buf.readString();
            java.util.List<String[]> opts = new java.util.ArrayList<>();
            for (int i = buf.readVarInt(); i > 0; i--) opts.add(new String[]{buf.readString(), buf.readString()});
            client.execute(() -> client.setScreen(new net.get900.pixelpirates.client.screen.TownsfolkScreen(id, name, title, say, opts).friendship(friend)));
        });
        // the harbour gulls; the festival fireworks (harmless client-side bursts - homestead/town/TownEvents)
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.SEAGULL,
                ctx -> new software.bernie.geckolib.renderer.GeoEntityRenderer<>(ctx, new net.get900.pixelpirates.entity.client.NamedGeoModel<>("seagull")));
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HomesteadBlocks.STREET_LAMP, HomesteadBlocks.DARTBOARD);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.DART, DartRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.get900.pixelpirates.homestead.town.TownEvents.FX, (client, handler, buf, sender) -> {
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            int seed = buf.readVarInt();
            client.execute(() -> net.get900.pixelpirates.client.TownFx.burst(client, x, y, z, seed));
        });
        BeardFeature.registerClient();
        TelescopeView.register();
        // chess: the screen (open / refresh), the pieces on the boards
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.get900.pixelpirates.homestead.chess.Chess.STATE, (client, handler, buf, sender) -> {
            var pos = buf.readBlockPos();
            var n = buf.readNbt();
            client.execute(() -> {
                if (n == null) return;
                if (client.currentScreen instanceof net.get900.pixelpirates.client.screen.ChessScreen s && s.pos.equals(pos)) s.update(n);
                else if (n.getBoolean("Open")) client.setScreen(new net.get900.pixelpirates.client.screen.ChessScreen(pos, n));
            });
        });
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.CHESS, ChessRenderer::new);                                              // the telescope block (homestead/nav/Telescopes)
        // paintings: the easel's canvas screen, the canvas on the easel, paintings on walls (homestead/art)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(net.get900.pixelpirates.homestead.art.Art.OPEN, (client, handler, buf, sender) -> {
            var pos = buf.readBlockPos();
            int size = buf.readByte();
            String title = buf.readString(32);
            int[] px = buf.readIntArray(32 * 32);
            boolean canvas = buf.readBoolean();
            client.execute(() -> client.setScreen(new net.get900.pixelpirates.client.screen.PaintScreen(pos, size, title, px, canvas)));
        });
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.EASEL, EaselRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.CUSTOM_PAINTING, CustomPaintingRenderer::new);
        // swings: the seat swinging with its rider
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.SWING, SwingRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(net.get900.pixelpirates.homestead.HomesteadEntities.SWING_SEAT,
                net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx -> ctx.addModels(SwingRenderer.SEAT_MODEL));
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HomesteadBlocks.SWING, HomesteadBlocks.HANGING_SWING, HomesteadBlocks.EASEL);
        // the Parrot Roost: the server sends the player's unlocked parrot types (homestead/parrot/ParrotCollection)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.get900.pixelpirates.homestead.parrot.ParrotCollection.ROOST_OPEN, (client, handler, buf, sender) -> {
                    java.util.Set<String> own = new java.util.HashSet<>();
                    for (int i = buf.readVarInt(); i > 0; i--) own.add(buf.readString(64));
                    client.execute(() -> client.setScreen(new net.get900.pixelpirates.client.screen.ParrotRoostScreen(own)));
                });
        // Strongbox reel (the server already rolled the prize)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.get900.pixelpirates.homestead.hoard.Strongboxes.SPIN, (client, handler, buf, sender) -> {
                    net.minecraft.nbt.NbtCompound nbt = buf.readNbt();
                    client.execute(() -> { if (nbt != null) client.setScreen(new net.get900.pixelpirates.client.screen.CaseScreen(nbt)); });
                });
        // Bounty Board menu: open it, or refresh it after a hand-in
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.get900.pixelpirates.homestead.bounty.Bounties.OPEN_BOARD, (client, handler, buf, sender) -> {
                    net.minecraft.nbt.NbtCompound nbt = buf.readNbt();
                    client.execute(() -> {
                        if (nbt == null) return;
                        if (client.currentScreen instanceof net.get900.pixelpirates.client.screen.BountyScreen bs) bs.update(nbt);
                        else client.setScreen(new net.get900.pixelpirates.client.screen.BountyScreen(nbt));
                    });
                });
        // Tavern games: open the table's screen, or refresh the one already open
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.get900.pixelpirates.homestead.tavern.TavernGames.STATE, (client, handler, buf, sender) -> {
                    net.minecraft.nbt.NbtCompound nbt = buf.readNbt();
                    client.execute(() -> {
                        if (nbt == null) return;
                        net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.fromLong(nbt.getLong("Pos"));
                        if (client.currentScreen instanceof net.get900.pixelpirates.client.screen.TavernGameScreen s && s.pos.equals(pos)) s.update(nbt);
                        else if (nbt.getBoolean("Open")) client.setScreen("liars".equals(nbt.getString("Game"))
                                ? new net.get900.pixelpirates.client.screen.LiarsDiceScreen(nbt) : new net.get900.pixelpirates.client.screen.CrownAnchorScreen(nbt));
                    });
                });
        net.get900.pixelpirates.homestead.chapel.ChapelBlocks.OrganConsole.CLIENT_OPEN =
                p -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.get900.pixelpirates.client.screen.OrganScreen(p));
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), HomesteadBlocks.SHIP_IN_BOTTLE_SLOOP, HomesteadBlocks.SHIP_IN_BOTTLE_BRIG,
                HomesteadBlocks.SHIP_IN_BOTTLE_GALLEON, HomesteadBlocks.SHIP_IN_BOTTLE_GHOST);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HomesteadBlocks.CANDELABRA, HomesteadBlocks.VOTIVE_RACK, HomesteadBlocks.CRYSTAL_CHANDELIER, HomesteadBlocks.GARDEN_URN, HomesteadBlocks.BELL_ROPE,
                HomesteadBlocks.ALTAR_CROSS, HomesteadBlocks.VOTIVE_SHIP, HomesteadBlocks.ORGAN_PIPES, HomesteadBlocks.BAPTISMAL_FONT);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), HomesteadBlocks.TAVERN_SIGN, HomesteadBlocks.CHANDLERY_SIGN, HomesteadBlocks.BAKERY_SIGN, HomesteadBlocks.HARBOUR_SIGN, HomesteadBlocks.WAREHOUSE_SIGN, HomesteadBlocks.DISTILLERY_SIGN, HomesteadBlocks.FISH_SIGN, HomesteadBlocks.INN_SIGN, HomesteadBlocks.PERCH_BRANCH, HomesteadBlocks.PARROT_ROOST, HomesteadBlocks.DOCK_SIGN, HomesteadBlocks.WATCH_SIGN, HomesteadBlocks.WEAPON_RACK, HomesteadBlocks.SMITHY_SIGN, HomesteadBlocks.PARK_SIGN, HomesteadBlocks.TELESCOPE, HomesteadBlocks.FORGE_ANVIL, HomesteadBlocks.FORGE_HEARTH, HomesteadBlocks.SPIRIT_BOTTLES, HomesteadBlocks.PINEAPPLE_CROP, HomesteadBlocks.LIME_CROP, HomesteadBlocks.CHILI_CROP,
                HomesteadBlocks.PALM_DOOR, HomesteadBlocks.PALM_TRAPDOOR, HomesteadBlocks.WOVEN_PALM_SCREEN, HomesteadBlocks.ROPE_LADDER,
                HomesteadBlocks.TIKI_TORCH, HomesteadBlocks.HANGING_NET, HomesteadBlocks.HANGING_ROPE, HomesteadBlocks.FISH_TRAP, HomesteadBlocks.LOBSTER_POT);
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(net.get900.pixelpirates.homestead.HomesteadItems.SALVAGE_HOOK,
                new net.minecraft.util.Identifier("cast"), (stack, world, entity, seed) -> {
                    if (!(entity instanceof net.minecraft.entity.player.PlayerEntity p)) return 0f;
                    boolean main = p.getMainHandStack() == stack, off = p.getOffHandStack() == stack && !(p.getMainHandStack().getItem() instanceof net.minecraft.item.FishingRodItem);
                    return (main || off) && p.fishHook != null ? 1f : 0f;
                });
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.DISPLAY, DisplayRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.FORGE_ANVIL, ForgeAnvilRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.MOB_TROPHY, MobTrophyRenderer::new);
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
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(net.get900.pixelpirates.homestead.HomesteadBlockEntities.ROULETTE_TABLE,
                ctx -> new RouletteRenderer());
        GunRenderer pistol = new GunRenderer("flintlock_pistol"), blunderbuss = new GunRenderer("blunderbuss");
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(net.get900.pixelpirates.homestead.HomesteadItems.FLINTLOCK_PISTOL, pistol::render);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(net.get900.pixelpirates.homestead.HomesteadItems.BLUNDERBUSS, blunderbuss::render);
    }
}
