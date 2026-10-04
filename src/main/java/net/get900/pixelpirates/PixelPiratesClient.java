package net.get900.pixelpirates;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.get900.pixelpirates.entity.client.GlowingMobRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.client.PirateLevelHud;
import net.get900.pixelpirates.client.PirateLevelingClient;
import net.get900.pixelpirates.client.RadarState;
import net.get900.pixelpirates.client.ShipMusicPlayer;
import net.get900.pixelpirates.client.ShipRadarHud;
import net.get900.pixelpirates.client.screen.BlueprintSelectScreen;
import net.get900.pixelpirates.client.screen.MapMerchantScreen;
import net.get900.pixelpirates.client.screen.PirateSkillScreen;
import net.get900.pixelpirates.client.screen.ShipwrightScreen;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.client.CutlassItemRenderer;
import net.get900.pixelpirates.world.PirateLevelingSystem;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.client.CannonBallEntityRenderer;
import net.get900.pixelpirates.entity.client.CaptainEntityRenderer;
import net.get900.pixelpirates.entity.client.ChestCrabEntityRenderer;
import net.get900.pixelpirates.entity.client.CastawayEntityRenderer;
import net.get900.pixelpirates.entity.client.LavaCrabEntityRenderer;
import net.get900.pixelpirates.entity.client.CursedMonkeyEntityRenderer;
import net.get900.pixelpirates.entity.client.FloatingBarrelEntityRenderer;
import net.get900.pixelpirates.entity.client.MapMerchantEntityRenderer;
import net.get900.pixelpirates.entity.client.PirateCrewEntityRenderer;
import net.get900.pixelpirates.entity.client.RaftEntityRenderer;
import net.get900.pixelpirates.entity.client.SloopEntityRenderer;
import net.get900.pixelpirates.world.ZoneEffectsClient;
import net.minecraft.text.Text;
import net.minecraft.client.render.RenderLayer;
import net.get900.pixelpirates.entity.client.SharkEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

public class PixelPiratesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        net.get900.pixelpirates.homestead.client.HomesteadClient.init();
        net.get900.pixelpirates.client.TreasureFx.register();                 // the treasure block's fireworks
        // GeckoLib 3D cutlass renderer — must register before any item rendering occurs
        CutlassItemRenderer cutlassRenderer = new CutlassItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.CUTLASS, cutlassRenderer::render);
        net.get900.pixelpirates.item.client.GallowbrandItemRenderer gallowbrandRenderer = new net.get900.pixelpirates.item.client.GallowbrandItemRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.GALLOWBRAND, gallowbrandRenderer::render);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(ModBlocks.GALLOWBRAND_STONE_ENTITY,
                ctx -> new net.get900.pixelpirates.block.client.GallowbrandStoneRenderer());

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.DRIFTWOOD_BLOCK, RenderLayer.getCutout());
        // Leaves need cutout-mipped so their transparent texels render as holes, not black
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SHOREWOOD_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.PALM_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TIDEWOOD_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.EMBER_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WISP_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.VOIDBLOOM_LEAVES, RenderLayer.getCutoutMipped());
        // Sprite-based models (cross plants, the helm's spoked wheel) need cutout for their gaps
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.SHOREWOOD_SAPLING,
                ModBlocks.POTTED_SHOREWOOD_SAPLING, ModBlocks.BANANA_BLOCK, ModBlocks.SHIP_HELM);
        EntityRendererRegistry.register(ModEntities.SHARK, SharkEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHEST_CRAB, ChestCrabEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LAVA_CRAB, LavaCrabEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CASTAWAY, CastawayEntityRenderer::new);
        // Generated mobs - scale pairs with the hitbox in ModEntities
        EntityRendererRegistry.register(ModEntities.SIREN, GlowingMobRenderer.of("siren", 0.75f, 0.5f));
        EntityRendererRegistry.register(ModEntities.CORAL_JELLY, GlowingMobRenderer.of("coral_jelly", 0.8f, 0.3f));
        EntityRendererRegistry.register(ModEntities.MAGMA_BRUTE, GlowingMobRenderer.of("magma_brute", 1.25f, 1.2f));
        EntityRendererRegistry.register(ModEntities.MIMIC, GlowingMobRenderer.mimic());
        EntityRendererRegistry.register(ModEntities.ABYSSAL_ANGLER, GlowingMobRenderer.of("abyssal_angler", 1.0f, 0.8f));
        // the data-driven roster: renderer scale/shadow come from each MobSpec (paired with its hitbox)
        net.get900.pixelpirates.entity.mob.ModMobs.TYPES.forEach((id, type) -> {
            var spec = net.get900.pixelpirates.entity.mob.MobSpecs.get(id);
            @SuppressWarnings("unchecked")
            var t = (net.minecraft.entity.EntityType<net.get900.pixelpirates.entity.mob.ModMob>) type;
            EntityRendererRegistry.register(t, switch (id) {
                case "rival_eye" -> GlowingMobRenderer.<net.get900.pixelpirates.entity.mob.ModMob>rivalEye(spec.renderScale());
                case "heart_phantasm", "drowned_keeper" -> GlowingMobRenderer.<net.get900.pixelpirates.entity.mob.ModMob>ghost(id, spec.renderScale());
                case "leviathan" -> net.get900.pixelpirates.entity.client.LeviathanRenderers.head(spec.renderScale());
                default -> GlowingMobRenderer.<net.get900.pixelpirates.entity.mob.ModMob>of(id, spec.renderScale(), spec.shadow());
            });
        });
        EntityRendererRegistry.register(ModEntities.MOB_PROJECTILE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHIP_CAPTAIN, CaptainEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PIRATE_CREW, PirateCrewEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CURSED_MONKEY, CursedMonkeyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MAP_MERCHANT, MapMerchantEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.RAFT, RaftEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SLOOP, SloopEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DYNAMITE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DEPTH_CHARGE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.TIDE_ARROW, net.get900.pixelpirates.entity.client.RelicProjectileRenderers.TideArrow::new);
        EntityRendererRegistry.register(ModEntities.SPECTRAL_SHOT, ctx -> new FlyingItemEntityRenderer<>(ctx, 1.4f, true));
        EntityRendererRegistry.register(ModEntities.THROWN_RELIC, net.get900.pixelpirates.entity.client.RelicProjectileRenderers.Thrown::new);
        EntityRendererRegistry.register(ModEntities.THROWN_GALLOWBRAND, net.get900.pixelpirates.entity.client.ThrownGallowbrandRenderer::new);
        EntityRendererRegistry.register(ModEntities.HARPOON, ctx -> new net.get900.pixelpirates.entity.client.HarpoonGeoRenderer<>(ctx, 1.0f));
        EntityRendererRegistry.register(ModEntities.KRAKEN_HARPOON, ctx -> new net.get900.pixelpirates.entity.client.HarpoonGeoRenderer<>(ctx, 1.6f));
        // the Leviathan's body (same render scale as its head), its wake, the hunt's props; and the eclipse
        EntityRendererRegistry.register(ModEntities.LEVIATHAN_SEGMENT, net.get900.pixelpirates.entity.client.LeviathanRenderers.segment(3.0f));
        EntityRendererRegistry.register(ModEntities.LEVIATHAN_WAKE, net.get900.pixelpirates.entity.client.LeviathanRenderers.wake(3.0f));
        EntityRendererRegistry.register(ModEntities.POWDER_BARGE, net.get900.pixelpirates.entity.client.LeviathanRenderers.barge());
        EntityRendererRegistry.register(ModEntities.DEBRIS, net.get900.pixelpirates.entity.client.LeviathanRenderers.debris());
        EntityRendererRegistry.register(ModEntities.BANE_BOLT, ctx -> new net.get900.pixelpirates.entity.client.HarpoonGeoRenderer<>(ctx, 5.0f));
        net.get900.pixelpirates.client.LeviathanClient.register();
        EntityRendererRegistry.register(ModEntities.CHUM, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.INK_BOMB, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.REVENANT_PART, net.get900.pixelpirates.entity.client.RevenantPartRenderer::new);
        EntityRendererRegistry.register(ModEntities.THROWN_KNIFE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CANNON_BALL, CannonBallEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.POWDER_KEG, net.get900.pixelpirates.entity.client.PowderKegEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FORT_CANNON, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.GHOST_CANNON);
        // the Dutchman is half there: ghostwood, sails and ghost masts are drawn see-through
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), ModBlocks.SPECTRAL_SAIL, ModBlocks.GHOSTWOOD_LOG,
                ModBlocks.GHOSTWOOD_PLANKS, ModBlocks.GHOST_MAST);
        EntityRendererRegistry.register(ModEntities.FLOATING_BARREL, FloatingBarrelEntityRenderer::new);

        ZoneEffectsClient.register();

        // N key — toggle ship music (play random track / stop current track)
        KeyBinding musicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pixelpirates.ship_music",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_N,
            "category.pixelpirates"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(PirateLevelingClient::tickSwim);       // Deep Diver
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (musicKey.wasPressed()) ShipMusicPlayer.toggle();
        });

        // 3D ARMOR: every PixelArmorItem gets its GeckoLib renderer from here (never on a dedicated server)
        net.get900.pixelpirates.item.custom.PixelArmorItem.CLIENT_PROVIDER = net.get900.pixelpirates.entity.client.PixelArmorRenderer::provider;

        // H key - hide / show the helmet you are wearing (item/HelmetToggle; it still protects you)
        KeyBinding helmetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pixelpirates.toggle_helmet",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_H,
            "category.pixelpirates"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (helmetKey.wasPressed())
                if (client.player != null) ClientPlayNetworking.send(net.get900.pixelpirates.item.HelmetToggle.PACKET, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
        });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (net.get900.pixelpirates.item.HelmetToggle.hidden(stack))
                lines.add(net.minecraft.text.Text.literal("Hidden while worn (press H to show)").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        });

        // Pirate Journal HUD (top-left: rank, level, XP bar, skill points)
        HudRenderCallback.EVENT.register(PirateLevelHud::render);

        // Radar HUD
        HudRenderCallback.EVENT.register(ShipRadarHud::render);
        // /pptest clearsight flag from the server; cleared on disconnect so it never leaks into another world
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_TEST_FLAGS, (client, handler, buf, responseSender) -> {
            boolean clear = buf.readBoolean();
            client.execute(() -> net.get900.pixelpirates.world.ZoneEffectsClient.clearSight = clear);
        });
        // The Weathered Chronicle: the server sends the reader's page state, the book opens on arrival
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_CHRONICLE, (client, handler, buf, responseSender) -> {
            var data = net.get900.pixelpirates.client.screen.ChronicleScreen.read(buf);
            client.execute(() -> client.setScreen(new net.get900.pixelpirates.client.screen.ChronicleScreen(data)));
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> net.get900.pixelpirates.world.ZoneEffectsClient.clearSight = false);
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.RADAR_UPDATE, (client, handler, buf, responseSender) -> {
            boolean hasRadar = buf.readBoolean();
            boolean hasShip  = buf.readBoolean();
            double sx = buf.readDouble();
            double sz = buf.readDouble();
            int aiCount = buf.readInt();
            java.util.List<double[]> ais = new java.util.ArrayList<>(aiCount);
            for (int i = 0; i < aiCount; i++) ais.add(new double[]{buf.readDouble(), buf.readDouble()});
            client.execute(() -> RadarState.update(hasRadar, hasShip, sx, sz, ais));
        });

        // Map Merchant shop screen
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_MAP_MERCHANT, (client, handler, buf, responseSender) -> {
            boolean hasRadar = buf.readBoolean();
            int coins = buf.readInt();
            int claimed = buf.readInt();
            client.execute(() -> client.setScreen(new MapMerchantScreen(hasRadar, coins, claimed)));
        });

        // Ship music: server validates ship presence and sends back the track index to play
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SHIP_MUSIC_PLAY, (client, handler, buf, responseSender) -> {
            int trackIndex = buf.readInt();
            client.execute(() -> {
                if (trackIndex < 0) {
                    if (client.player != null)
                        client.player.sendMessage(Text.literal("§7You must be on a ship to play music."), true);
                } else {
                    ShipMusicPlayer.playTrack(trackIndex);
                }
            });
        });

        // J key — open Pirate Journal / skill screen directly (no server round-trip needed)
        KeyBinding journalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pixelpirates.open_journal",
            InputUtil.Type.KEYSYM,
            InputUtil.GLFW_KEY_J,
            "category.pixelpirates"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (journalKey.wasPressed() && client.currentScreen == null)
                client.setScreen(new PirateSkillScreen());
        });

        // S2C: server tells client to open the skill screen (via journal item right-click)
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_OPEN_SKILL_SCREEN,
            (client, handler, buf, responseSender) ->
                client.execute(() -> client.setScreen(new PirateSkillScreen()))
        );

        // S2C: server syncs level / XP / skill data to the client
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.S2C_LEVEL_SYNC,
            (client, handler, buf, responseSender) -> {
                int   level  = buf.readInt();
                int   xp     = buf.readInt();
                int   points = buf.readInt();
                int   count  = PirateLevelingSystem.ALL_SKILLS.size();
                int[] skills = new int[count];
                for (int i = 0; i < count; i++) skills[i] = buf.readInt();
                int bosses = buf.isReadable() ? buf.readInt() : 0;
                client.execute(() -> { PirateLevelingClient.bossesBeaten = bosses; PirateLevelingClient.update(level, xp, points, skills); });
            }
        );

        // Open the blueprint selection screen when the server sends the list
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_BLUEPRINT_MENU, (client, handler, buf, responseSender) -> {
            var names = buf.readCollection(java.util.ArrayList::new, b -> b.readString(64));
            client.execute(() -> client.setScreen(new BlueprintSelectScreen(names)));
        });

        // Open the shipwright screen when the server sends menu data
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_SHIPWRIGHT,
            (client, handler, buf, responseSender) -> {
                var blueprints = buf.readCollection(java.util.ArrayList::new, b -> b.readString(64));
                long shipId    = buf.readLong();
                int  hp        = buf.readInt();
                int  maxHp     = buf.readInt();
                int  mastCount = buf.readInt();
                int[] levels   = new int[net.get900.pixelpirates.world.ShipUpgrades.ALL.size()];
                for (int i = 0; i < levels.length; i++) levels[i] = buf.readInt();
                int livery = buf.readVarInt();
                int[] liveries = new int[net.get900.pixelpirates.world.livery.Livery.ALL.size()];
                for (int i = 0; i < liveries.length; i++) liveries[i] = buf.readByte();
                client.execute(() -> client.setScreen(
                    new ShipwrightScreen(blueprints, shipId, hp, maxHp, mastCount, levels, livery, liveries)));
            }
        );

        // Send WASD + sprint inputs to the server every tick while riding a helm seat.
        // This bypasses VS2's PacketPlayerDriving chain which silently fails in e4mc/LAN
        // multiplayer when getLoadedShipManagingPos returns null on the client.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (!(client.player.getVehicle() instanceof ShipMountingEntity)) return;

            float fwd  = 0f;
            float turn = 0f;
            if (client.options.forwardKey.isPressed()) fwd  += 1f;
            if (client.options.backKey.isPressed())    fwd  -= 1f;
            if (client.options.leftKey.isPressed())    turn += 1f;
            if (client.options.rightKey.isPressed())   turn -= 1f;
            boolean sprint = client.options.sprintKey.isPressed();

            var buf = PacketByteBufs.create();
            buf.writeFloat(fwd);
            buf.writeFloat(turn);
            buf.writeBoolean(sprint);
            ClientPlayNetworking.send(ModNetworking.HELM_STEER, buf);
        });

        PixelPirates.LOGGER.info("Pixel Pirates client initializing...");
    }
}