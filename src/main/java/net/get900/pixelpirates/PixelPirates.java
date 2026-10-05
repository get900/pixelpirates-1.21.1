package net.get900.pixelpirates;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.sound.ModSounds;
import net.get900.pixelpirates.enchantment.ModEnchantments;
import net.get900.pixelpirates.command.ModCommands;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.get900.pixelpirates.entity.custom.ChestCrabEntity;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.get900.pixelpirates.entity.custom.LavaCrabEntity;
import net.get900.pixelpirates.entity.custom.CursedMonkeyEntity;
import net.get900.pixelpirates.entity.custom.FloatingBarrelEntity;
import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.minecraft.util.math.Box;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.AdminTestState;
import net.get900.pixelpirates.world.TestModes;
import net.get900.pixelpirates.world.AiShipConfig;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.PirateLevelManager;
import net.get900.pixelpirates.world.PirateLevelingSystem;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSpawner;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ZoneAdvancementManager;
import net.get900.pixelpirates.world.ZoneEffectsServer;
import net.get900.pixelpirates.world.ZoneHazardManager;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.biome.PixelPiratesBiomeSource;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.get900.pixelpirates.world.gen.ModEntitySpawns;
import net.get900.pixelpirates.world.gen.ModFeatures;
import net.get900.pixelpirates.world.gen.ModWorldGeneration;
import net.get900.pixelpirates.world.gen.density.ZoneTerrainFunction;
import net.get900.pixelpirates.world.tree.ModTreeDecorator;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PixelPirates implements ModInitializer {
	public static final String MOD_ID = "pixelpirates";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// the spawn island's hand edits saved in game (/ppisland capture) - read with the layout, written by world/gen/IslandEdits
		net.get900.pixelpirates.world.gen.PortCityLayout.LOCAL_EDITS = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("pixelpirates/island_edits.txt");
		net.get900.pixelpirates.world.gen.IslandEditTracker.init();
		net.get900.pixelpirates.world.dungeon.StructureEditState.init();   // /ppstruct: hand edits to /ppdungeon copies of layout structures   // remembers what players change on the island (/ppisland capture all)
		ModItems.registerModItems();
		net.get900.pixelpirates.item.ModCreativeTabs.register();   // self-sorting creative tabs (item/ModCreativeTabs)
		ModBlocks.registerModBlocks();
		net.get900.pixelpirates.world.dungeon.AbyssPuzzleNodes.register();   // Phase 5 puzzle stones (abyss_puzzle_node + its block entity)
		net.get900.pixelpirates.homestead.Homestead.init();   // base building, farming, galley, trade... (homestead/)
		net.get900.pixelpirates.item.food.PirateFoods.register();   // the galley: 40 dishes (item/food/PirateFoods)
		net.get900.pixelpirates.util.ModLootTableModifiers.register();

		ModEnchantments.register();
		ModFeatures.register();
		ModCommands.register();
		ModWorldGeneration.generateModWorldGen();
		ModTreeDecorator.init();

		net.get900.pixelpirates.util.ModBlockBehaviours.register();

		ModEntities.registerModEntities();
		FabricDefaultAttributeRegistry.register(ModEntities.SHARK, SharkEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.REVENANT_PART, net.get900.pixelpirates.entity.mob.RevenantPartEntity.attributes());
		FabricDefaultAttributeRegistry.register(ModEntities.LEVIATHAN_SEGMENT, net.minecraft.entity.mob.PathAwareEntity.createMobAttributes()
				.add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH, 1000).add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1));
		FabricDefaultAttributeRegistry.register(ModEntities.MAP_MERCHANT, MapMerchantEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.CURSED_MONKEY, CursedMonkeyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.SHIP_CAPTAIN, CaptainEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.PIRATE_CREW, PirateCrewEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.CHEST_CRAB, ChestCrabEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.LAVA_CRAB, LavaCrabEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.CASTAWAY, CastawayEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.SIREN, net.get900.pixelpirates.entity.custom.SirenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.CORAL_JELLY, net.get900.pixelpirates.entity.custom.CoralJellyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.MAGMA_BRUTE, net.get900.pixelpirates.entity.custom.MagmaBruteEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.MIMIC, net.get900.pixelpirates.entity.custom.MimicEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.ABYSSAL_ANGLER, net.get900.pixelpirates.entity.custom.AbyssalAnglerEntity.createAttributes());
		net.get900.pixelpirates.entity.mob.ModMobs.register();   // the data-driven roster (MobSpecs)
		net.get900.pixelpirates.item.ModSpawnEggs.register();

		// Register custom biome source type so the dimension JSON can decode it
		Registry.register(Registries.BIOME_SOURCE, id("pixel_pirates_biome_source"), PixelPiratesBiomeSource.CODEC_INSTANCE);

		// Register custom density function type for zone-based terrain heights
		Registry.register(Registries.DENSITY_FUNCTION_TYPE, id("zone_terrain"), ZoneTerrainFunction.MAP_CODEC.codec());

		// Give creative players a Dimension Key on first join (if they don't already have one)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			if (!player.getAbilities().creativeMode) return;
			boolean hasKey = false;
			for (int i = 0; i < player.getInventory().size(); i++) {
				if (player.getInventory().getStack(i).isOf(ModItems.DIMENSION_KEY)) {
					hasKey = true;
					break;
				}
			}
			if (!hasKey) {
				player.getInventory().insertStack(new ItemStack(ModItems.DIMENSION_KEY));
			}
		});

		// Grant 6 starter coins the first time a player enters the PP world
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			if (player.getServerWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) {
				grantStarterCoinsIfNeeded(player);
				net.get900.pixelpirates.world.Chronicle.giveStarter(player);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (destination.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) {
				grantStarterCoinsIfNeeded(player);
				net.get900.pixelpirates.world.Chronicle.giveStarter(player);
			}
		});

		// Restore persisted ship registrations (MAST_COUNTS + AI_SHIPS) after world load.
		// Must run after VS2 has loaded ship data — SERVER_STARTED fires once all worlds are ready.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			net.get900.pixelpirates.world.ShipSimDistance.apply();          // ships stay simulated out to 320 blocks
			ServerWorld ppWorld = server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
			if (ppWorld == null) {
				LOGGER.warn("[ShipRegistry] PP world not found at SERVER_STARTED — ship registrations NOT restored.");
				return;
			}
			ShipRegistryState.get(server.getOverworld()).restoreToMaps(ppWorld);
			ShipSchematic.installBundled();          // copy the mod's blueprints into config/ if missing
			AiShipConfig.generateDefaults();

			// The Map Merchant is seated in his market booth by homestead/trade/PortTraders (with the port traders).
		});

		// AI computes SHIP_INPUTS first so SteeringManager sees fresh values this same tick.
		ServerTickEvents.END_SERVER_TICK.register(AiShipController::tick);

		// Ship helm steering — applies physics forces using SHIP_INPUTS set above (or by HELM_STEER packet).
		ServerTickEvents.END_SERVER_TICK.register(ShipSteeringManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.world.KeelBreaker::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.homestead.harbour.HarbourDues::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.item.custom.SirenConchItem::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.item.RelicWeapons::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.world.HelmDismountGuard::tick);

		// Natural AI ship spawning — rate and cap are configurable via /ppai setrate and /ppai setcap.
		// Multiple factions can spawn; defaults: every 2400 ticks, cap = max(8, players*2).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() < AdminTestState.nextSpawnTick) return;
			AdminTestState.nextSpawnTick = server.getTicks() + AdminTestState.effectiveSpawnRate();

			List<String> blueprints = ShipSchematic.listNames();
			if (blueprints.isEmpty()) {
				LOGGER.debug("[AI] Natural spawn skipped — no blueprints found in config/pixelpirates/ships/");
				return;
			}

			ServerWorld ppWorld = server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
			if (ppWorld == null) return;

			int cap = AdminTestState.effectiveCap(server.getPlayerManager().getCurrentPlayerCount());
			if (AiShipController.AI_SHIPS.size() >= cap) return;

			List<ServerPlayerEntity> ppPlayers = server.getPlayerManager().getPlayerList().stream()
					.filter(p -> p.getServerWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD))
					.collect(Collectors.toList());
			if (ppPlayers.isEmpty()) return;

			// One spawn per trigger — two same-tick spawns can race VS2's AABB index and
			// cause IllegalStateException (Collectors.toMap duplicate key) in onSetBlock.
			int toSpawn = Math.min(1, cap - AiShipController.AI_SHIPS.size());
			for (int attempt = 0; attempt < toSpawn; attempt++) {
				ServerPlayerEntity chosen = ppPlayers.get((int)(Math.random() * ppPlayers.size()));
				Vec3d pPos = chosen.getPos();

				int physicalZone = PlayerProgressionManager.getZoneAt(pPos);
				Faction faction  = pickFactionForZone(physicalZone);
				String blueprint = pickAiBlueprintForZone(physicalZone, faction, blueprints);
				if (blueprint == null) {
					LOGGER.debug("[AI] Natural spawn skipped — no suitable blueprint for zone {} faction {}",
						physicalZone, faction.id);
					continue;
				}

				double angle  = Math.random() * 2 * Math.PI;
				double dist   = 150 + Math.random() * 80;
				// Y=90: well above assembled ships whose hulls reach up to ~Y=74 at WATER_Y=62.
				// The 16-block vertical gap prevents VS2 from seeing virtual ship blocks at the
				// new spawn position during the setBlockState placement loop.
				BlockPos origin = new BlockPos(
					(int)(pPos.x + Math.cos(angle) * dist),
					90,
					(int)(pPos.z + Math.sin(angle) * dist)
				);

				BlockPos waterCheck = new BlockPos(origin.getX(), ppWorld.getSeaLevel() - 1, origin.getZ());
				if (!ppWorld.getBlockState(waterCheck).getFluidState().isIn(FluidTags.WATER)) {
					LOGGER.debug("[AI] Natural spawn skipped — not over water at ({}, {})", origin.getX(), origin.getZ());
					continue;
				}

				// Reject if any existing VS2 ship is within 250 blocks XZ of the spawn origin.
				// Expanded to 250 blocks: the AABB check uses VS2's physics-thread AABB index which
				// can lag behind actual ship positions. ShipSpawner adds a second per-block
				// getBlockState scan (VS2's own mixin, zero race window) as the final guard.
				{
					double ox = origin.getX(), oz = origin.getZ();
					AABBd clearBox = new AABBd(ox - 250, 0, oz - 250, ox + 250, 256, oz + 250);
					if (ValkyrienSkies.getShipsIntersecting(ppWorld, clearBox).iterator().hasNext()) {
						LOGGER.debug("[AI] Natural spawn skipped — existing ship within 250 blocks of ({}, {})", (int)ox, (int)oz);
						continue;
					}
				}

				try {
					ShipSchematic schematic = ShipSchematic.load(blueprint);
					org.valkyrienskies.core.api.ships.ServerShip ship = ShipSpawner.spawn(ppWorld, schematic, origin);

					Matrix4d worldToShip = new Matrix4d(ship.getTransform().getShipToWorld()).invert();
					List<Vector3d> cannons = new java.util.ArrayList<>();
					for (ShipSchematic.Entry e : schematic.getEntries()) {
						net.minecraft.block.BlockState bs = ShipSchematic.restoreState(e.stateNbt());
						if (bs != null && bs.isOf(ModBlocks.SHIP_CANNON)) {
							BlockPos cp = origin.add(e.relPos());
							Vector3d wpos = new Vector3d(cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5);
							cannons.add(worldToShip.transformPosition(wpos, new Vector3d()));
						}
					}
					AiShipController.registerAiShip(ship.getId(), ppWorld, cannons, blueprint, faction);
					LOGGER.info("[AI] Natural spawn: blueprint='{}' faction='{}' (zone {}) near '{}' at {} ({} cannons)",
						blueprint, faction.id, physicalZone, chosen.getName().getString(), origin, cannons.size());
				} catch (Exception e) {
					LOGGER.warn("[AI] Natural spawn failed for blueprint '{}': {}", blueprint, e.getMessage());
				}
			}
		});

		// Floating barrel spawning — every 1200 ticks (1 minute), 20% chance per player
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 1200 != 0) return;
			ServerWorld ppWorld = server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
			if (ppWorld == null) return;
			List<ServerPlayerEntity> ppPlayers = ppWorld.getPlayers();
			if (ppPlayers.isEmpty()) return;

			int seaLevel = ppWorld.getSeaLevel();
			for (ServerPlayerEntity player : ppPlayers) {
				if (ppWorld.getRandom().nextFloat() > 0.20f) continue;
				double angle = ppWorld.getRandom().nextDouble() * Math.PI * 2;
				double dist  = 15 + ppWorld.getRandom().nextDouble() * 15; // 15–30 blocks ahead
				double bx    = player.getX() + Math.cos(angle) * dist;
				double bz    = player.getZ() + Math.sin(angle) * dist;
				BlockPos checkPos = BlockPos.ofFloored(bx, seaLevel - 1, bz);
				if (!ppWorld.getFluidState(checkPos).isIn(FluidTags.WATER)) continue;
				FloatingBarrelEntity barrel = new FloatingBarrelEntity(ModEntities.FLOATING_BARREL, ppWorld);
				barrel.setPosition(bx, seaLevel, bz);
				ppWorld.spawnEntity(barrel);
			}
		});

		// Radar HUD update — broadcast ship positions to all clients once per second
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) return;
			ModNetworking.broadcastRadarUpdates(server);
		});

		// Zone boundary enforcement — check every 20 ticks (1 second)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) return;
			server.getPlayerManager().getPlayerList().forEach(PlayerProgressionManager::enforce);
		});

		// /pptest god + clearsight upkeep (every tick)
		ServerTickEvents.END_SERVER_TICK.register(TestModes::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.world.GhostShipEncounter::tick);
		// SEALED BOSS HOARDS: a hoard won't open (or break) until you're ready for its boss; a second try wakes it (BossHoards)
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
			return net.get900.pixelpirates.entity.mob.BossHoards.onUse(sp, (ServerWorld) world, hit.getBlockPos());
		});
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
				world.isClient || be == null || net.get900.pixelpirates.entity.mob.BossHoards.mayBreak(player, be));
		// a thrown rope line reels back in as rope (item/custom/RopeItem)
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
				world.isClient || net.get900.pixelpirates.item.custom.RopeItem.reel(world, player, pos, state));
		// Ringing a bell that stands on a Phantom Buoy (Dutchman's Rest) summons the Flying Dutchman
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND) return net.minecraft.util.ActionResult.PASS;
			var pos = hit.getBlockPos();
			if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.BELL)
					&& world.getBlockState(pos.down()).isOf(ModBlocks.PHANTOM_BUOY)
					&& player instanceof net.minecraft.server.network.ServerPlayerEntity sp)
				net.get900.pixelpirates.world.GhostShipEncounter.summon((ServerWorld) world, pos, sp);
			return net.minecraft.util.ActionResult.PASS;
		});
		// THE LEVIATHAN HUNT: the world's one Leviathan, its flights, the ports it destroys (world/leviathan)
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.world.leviathan.LeviathanHunt::tick);
		ServerTickEvents.END_SERVER_TICK.register(net.get900.pixelpirates.item.BossArmor::tick);        // boss set bonuses
		net.get900.pixelpirates.item.HelmetToggle.registerServer();                                     // H: hide helmet
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(net.get900.pixelpirates.world.leviathan.LeviathanPorts::onChunkLoad);
		// Left-clicking a Tide Bell hammers it (the Leviathan's Last Tide)
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
			if (!world.getBlockState(pos).isOf(ModBlocks.TIDE_BELL)) return net.minecraft.util.ActionResult.PASS;
			if (!world.isClient) net.get900.pixelpirates.world.leviathan.LeviathanHunt.onBlockUsed((ServerWorld) world, pos,
					net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.BELL, player, true);
			return net.minecraft.util.ActionResult.SUCCESS;
		});
		// Left-clicking a Galvanic Pylon strikes it (Abyssal Heart pacemaker); right-click is GalvanicPylonBlock.onUse
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
			if (!world.getBlockState(pos).isOf(ModBlocks.GALVANIC_PYLON)) return net.minecraft.util.ActionResult.PASS;
			if (!world.isClient) net.get900.pixelpirates.block.custom.GalvanicPylonBlock.strike((ServerWorld) world, pos, player);
			return net.minecraft.util.ActionResult.SUCCESS;            // never mined, not even in creative
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(TestModes::allowDeath);
		// A broken player-added ship block frees its build slot (world/ShipBuilding)
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerWorld sw) net.get900.pixelpirates.world.ShipBuilding.broken(sw, pos);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> TestModes.sync(handler.getPlayer()));

		// Zone hazards (hot water Zone 3, madness Zone 5) — check every 40 ticks (2 seconds)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 40 != 0) return;
			server.getPlayerManager().getPlayerList().forEach(ZoneHazardManager::enforce);
		});

		// Zone advancement checks — grant progression advancements every second
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) return;
			server.getPlayerManager().getPlayerList().forEach(ZoneAdvancementManager::check);
		});

		// Zone visual effects (falling fireballs Zone 3, lightning Zone 4) — checked every tick,
		// each effect has its own internal cadence with per-player jitter
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			server.getPlayerManager().getPlayerList().forEach(p -> ZoneEffectsServer.enforce(p, tick));
		});

		// Carry zone unlocks, boss progress, level/skills and reputation over to the respawned player
		// (they live in PlayerProgressionMixin fields, which vanilla's copyFrom knows nothing about)
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			net.minecraft.nbt.NbtCompound carry = new net.minecraft.nbt.NbtCompound();
			((net.get900.pixelpirates.util.PlayerProgressionComponent) oldPlayer).pp_writeProgression(carry);
			((net.get900.pixelpirates.util.PlayerProgressionComponent) newPlayer).pp_readProgression(carry);
		});

		// Respawn players who die in the PP dimension back to the spawn island
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive) return; // not a death — e.g. returning from the End
			if (!oldPlayer.getServerWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;
			ServerWorld ppWorld = newPlayer.getServer().getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
			if (ppWorld == null) return;
			// Force-load the spawn chunk so getTopY returns a real terrain height
			ppWorld.getChunk(0, 0);
			int spawnY = ppWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
			// Clamp to safe minimum above water (62) in case the chunk has no terrain yet
			if (spawnY < 64) spawnY = 65;
			newPlayer.teleport(ppWorld, 0.5, spawnY, 0.5, Set.of(), 0f, 0f);
		});

		// Grant "Shark Bait" advancement when a shark kills a player
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killedEntity) -> {
			if (!(killer instanceof SharkEntity)) return;
			if (!(killedEntity instanceof ServerPlayerEntity player)) return;
			AdvancementHelper.grant(player, "shark_bait");
		});

		// First shark kill -> "Monster of the Deep". (Zones are opened by the boss chain only - the old placeholder
		// "3 sharks unlock the next zone" let players skip the Ghost Captain and was removed 2026-09-30.)
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killedEntity) -> {
			if (killedEntity instanceof SharkEntity && killer instanceof ServerPlayerEntity player)
				AdvancementHelper.grant(player, "monster_of_the_deep");
		});

		// ── Pirate leveling ──────────────────────────────────────────────────────

		// Apply attribute modifiers and sync level data on player join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			PirateLevelManager.applyAttributeModifiers(player);
			server.execute(() -> PirateLevelManager.syncToClient(player));
		});

		// Award XP from kills and trigger on-kill skill effects
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killedEntity) -> {
			if (!(killer instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;

			int xp = net.get900.pixelpirates.world.PirateXp.forKill(killedEntity);   // roster mobs by sea, bosses via onBossKilled

			if (xp > 0) PirateLevelManager.awardXp(player, xp, false);

			net.get900.pixelpirates.world.SkillEffects.onKill(player, killedEntity);   // Monster Hunter

			// Bloodlust: heal on a melee kill (the killing blow came from the player's own hand)
			int bloodlustLvl = PirateLevelManager.getSkillLevel(player, "bloodlust");
			var lastHit = killedEntity.getRecentDamageSource();
			if (bloodlustLvl > 0 && lastHit != null && lastHit.getSource() == player) player.heal(bloodlustLvl);

			// Silver Tongue: chance for a bonus doubloon
			int silverLvl = PirateLevelManager.getSkillLevel(player, "silver_tongue");
			if (silverLvl > 0 && world.getRandom().nextFloat() < silverLvl * 0.05f) {
				player.getInventory().offerOrDrop(new ItemStack(ModItems.COIN));
			}
		});

		// Damage events: every "survive / cancel this hit" skill (world/SkillEffects)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)
				|| net.get900.pixelpirates.world.SkillEffects.allowDamage(player, source, amount));

		// Per-tick: cooldowns + second wind
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			server.getPlayerManager().getPlayerList().forEach(PirateLevelManager::tickPlayer);
		});

		// ── end pirate leveling ───────────────────────────────────────────────

		ModSounds.register();
		ModNetworking.registerServer();
		LOGGER.info("Pixel Pirates is initializing...");

		FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.DRIFTWOOD_BLOCK, 5, 5);
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	private static void grantStarterCoinsIfNeeded(ServerPlayerEntity player) {
		PlayerProgressionComponent comp = (PlayerProgressionComponent) player;
		if (comp.pp_hasStarterCoins()) return;
		comp.pp_setStarterCoins(true);
		player.getInventory().insertStack(new ItemStack(ModItems.COIN, 30));      // a sloop costs 25 doubloons (ShipTiers)
		player.getInventory().insertStack(new ItemStack(ModItems.PIRATE_JOURNAL));
		// (the Weathered Chronicle has its own once-per-player flag - see Chronicle.giveStarter)
		player.sendMessage(net.minecraft.text.Text.literal(
			"§6You've been given 6 Coins — commission a ship at the Shipwright!"), false);
		player.sendMessage(net.minecraft.text.Text.literal(
			"§7A §6Pirate Journal §7has been added to your inventory. Press §bJ§7 to open it."), false);
	}

	/**
	 * Picks a faction to spawn for the given zone using weighted random selection.
	 * Zone 0 returns null (no spawn in starter area).
	 */
	private static Faction pickFactionForZone(int zone) {
		double r = Math.random();
		return switch (zone) {
			case 1  -> r < 0.65 ? Faction.PIRATES : (r < 0.85 ? Faction.MERCHANTS : Faction.NAVY);
			case 2  -> r < 0.50 ? Faction.PIRATES : (r < 0.75 ? Faction.MERCHANTS : Faction.NAVY);
			case 3  -> r < 0.45 ? Faction.PIRATES : (r < 0.65 ? Faction.NAVY      : Faction.UNDEAD);
			default -> r < 0.35 ? Faction.PIRATES : (r < 0.55 ? Faction.NAVY      : Faction.UNDEAD);
		};
	}

	/**
	 * Returns a blueprint name for the given zone and faction (2026-10-04 fleet): a ship of that faction whose zone range
	 * (AiShipConfig.zoneRange - each faction sails a small -> flagship ladder) covers the zone; failing that the faction ship
	 * whose range lies nearest; failing that any blueprint (keeps things working with a bare config folder).
	 */
	private static String pickAiBlueprintForZone(int zone, Faction faction, List<String> available) {
		if (zone <= 0) return null;
		java.util.List<String> inZone = new java.util.ArrayList<>();
		String nearest = null; int nearestGap = Integer.MAX_VALUE;
		for (String b : available) {
			AiShipConfig cfg = AiShipConfig.load(b);
			if (Faction.fromId(cfg.faction) != faction) continue;
			int[] r = cfg.zoneRange(b);
			if (zone >= r[0] && zone <= r[1]) inZone.add(b);
			int gap = zone < r[0] ? r[0] - zone : zone - r[1];
			if (gap < nearestGap) { nearestGap = gap; nearest = b; }
		}
		if (!inZone.isEmpty()) return inZone.get((int) (Math.random() * inZone.size()));
		if (nearest != null) return nearest;
		return available.isEmpty() ? null : available.get((int) (Math.random() * available.size()));
	}

}
