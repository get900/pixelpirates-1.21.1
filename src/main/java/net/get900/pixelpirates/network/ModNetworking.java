package net.get900.pixelpirates.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.sound.ModSounds;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.PirateLevelManager;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSpawner;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.get900.pixelpirates.world.ShipUpgrades;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class ModNetworking {

    // S2C — server sends blueprint list, client opens the selection screen
    public static final Identifier OPEN_BLUEPRINT_MENU = PixelPirates.id("open_blueprint_menu");
    // C2S — client sends the name of the blueprint the player selected
    public static final Identifier SELECT_BLUEPRINT    = PixelPirates.id("select_blueprint");
    // C2S — client sends helm steering inputs every tick while riding a ShipMountingEntity.
    // Bypasses VS2's unreliable SeatedControllingPlayer / PacketPlayerDriving chain.
    public static final Identifier HELM_STEER          = PixelPirates.id("helm_steer");

    // S2C — server sends shipwright menu data to client
    public static final Identifier OPEN_SHIPWRIGHT    = PixelPirates.id("open_shipwright");
    // C2S — client requests to commission (spawn) a blueprint
    public static final Identifier SHIPWRIGHT_SPAWN   = PixelPirates.id("shipwright_spawn");
    // C2S — client requests to upgrade a ship
    public static final Identifier SHIPWRIGHT_UPGRADE = PixelPirates.id("shipwright_upgrade");

    // C2S — client pressed N; server validates player is on a ship, then responds
    public static final Identifier SHIP_MUSIC_REQUEST  = PixelPirates.id("ship_music_request");
    // S2C — server sends track index to play (or -1 if not on a ship)
    public static final Identifier SHIP_MUSIC_PLAY     = PixelPirates.id("ship_music_play");
    // C2S — client clicked Scuttle in the Shipwright screen
    public static final Identifier SHIPWRIGHT_SCUTTLE  = PixelPirates.id("shipwright_scuttle");
    // S2C — ship positions for the client radar HUD (sent every second)
    public static final Identifier RADAR_UPDATE        = PixelPirates.id("radar_update");
    // S2C — open the Map Merchant shop screen
    public static final Identifier OPEN_MAP_MERCHANT   = PixelPirates.id("open_map_merchant");
    // C2S — player clicks Buy on an item in the Map Merchant screen
    public static final Identifier MAP_MERCHANT_BUY    = PixelPirates.id("map_merchant_buy");

    // ── Pirate leveling ───────────────────────────────────────────────────────
    // S2C — sync level, xp, skill points, and all 25 skill levels to the client
    public static final Identifier S2C_LEVEL_SYNC      = PixelPirates.id("level_sync");
    /** S2C: /pptest flags the client must honour (boolean clearSight). */
    public static final Identifier S2C_TEST_FLAGS      = PixelPirates.id("test_flags");
    // S2C — tell the client to open the Pirate Journal / skill screen
    public static final Identifier S2C_OPEN_SKILL_SCREEN = PixelPirates.id("open_skill_screen");
    // C2S — client requests to spend one skill point on the given skill key
    public static final Identifier C2S_SKILL_SPEND     = PixelPirates.id("skill_spend");

    public static final int SHIP_COMMISSION_COST = 3; // Coins

    private static final Random MUSIC_RNG = new Random();

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_BLUEPRINT,
            (server, player, handler, buf, responseSender) -> {
                String shipName = buf.readString(64);
                server.execute(() -> spawnFromMenu(player, shipName));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(HELM_STEER,
            (server, player, handler, buf, responseSender) -> {
                float   fwd    = buf.readFloat();
                float   turn   = buf.readFloat();
                boolean sprint = buf.readBoolean();
                server.execute(() -> {
                    if (!(player.getVehicle() instanceof ShipMountingEntity seat)) return;
                    Ship ship = ValkyrienSkies.getShipManagingBlock(
                        player.getServerWorld(),
                        seat.getBlockX(), seat.getBlockY(), seat.getBlockZ()
                    );
                    if (ship == null) return;
                    long shipId = ship.getId();
                    ShipSteeringManager.SHIP_INPUTS.put(shipId, new float[]{fwd, turn, sprint ? 1f : 0f});
                    ShipSteeringManager.HELM_STEER_FRESHNESS.put(shipId, 5);
                    AdvancementHelper.grant(player, "captain_now");
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(SHIPWRIGHT_SPAWN,
            (server, player, handler, buf, responseSender) -> {
                String name = buf.readString(64);
                server.execute(() -> handleShipwrightSpawn(player, name));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(SHIPWRIGHT_UPGRADE,
            (server, player, handler, buf, responseSender) -> {
                long shipId = buf.readLong();
                String key  = buf.readString(32);
                server.execute(() -> handleShipwrightUpgrade(player, shipId, key));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(SHIP_MUSIC_REQUEST,
            (server, player, handler, buf, responseSender) -> {
                server.execute(() -> handleShipMusicRequest(player));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(MAP_MERCHANT_BUY,
            (server, player, handler, buf, responseSender) -> {
                int index = buf.readInt();
                server.execute(() -> handleMapMerchantBuy(player, index));
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(SHIPWRIGHT_SCUTTLE,
            (server, player, handler, buf, responseSender) -> {
                server.execute(() -> {
                    boolean scuttled = scuttlePlayerShip(player);
                    if (scuttled) {
                        player.sendMessage(Text.literal("§7Your ship has been scuttled."), true);
                    } else {
                        player.sendMessage(Text.literal("§cYou don't own a ship to scuttle."), true);
                    }
                });
            }
        );

        // C2S: spend one skill point on the given skill
        ServerPlayNetworking.registerGlobalReceiver(C2S_SKILL_SPEND,
            (server, player, handler, buf, responseSender) -> {
                String key = buf.readString(64);
                server.execute(() -> PirateLevelManager.spendSkillPoint(player, key));
            }
        );
    }

    // Called server-side when a player right-clicks a blank blueprint item.
    // Sends the blueprint list to the client to open the selection screen.
    public static void sendBlueprintMenu(ServerPlayerEntity player) {
        List<String> names = ShipSchematic.listNames();
        if (names.isEmpty()) {
            player.sendMessage(Text.literal(
                "§eNo blueprints saved yet. Build a ship, assemble it, then sneak + right-click the helm with this item."), true);
            return;
        }
        var buf = PacketByteBufs.create();
        buf.writeCollection(names, (b, s) -> b.writeString(s, 64));
        ServerPlayNetworking.send(player, OPEN_BLUEPRINT_MENU, buf);
    }

    private static void spawnFromMenu(ServerPlayerEntity player, String shipName) {
        ServerWorld world = player.getServerWorld();
        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());

        Long ownedShipId = registry.getOwnedShip(player.getUuid());
        if (ownedShipId != null) {
            if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                player.sendMessage(Text.literal(
                    "§cYou already own a ship. Use /scuttle to destroy it first."), true);
                return;
            }
            registry.clearOwnership(player.getUuid());
        }

        Vec3d look = player.getRotationVec(1.0f);
        BlockPos spawnOrigin = BlockPos.ofFloored(
            player.getX() + look.x * 20,
            75,
            player.getZ() + look.z * 20
        );
        try {
            ShipSchematic schematic = ShipSchematic.load(shipName);
            var ship = ShipSpawner.spawn(world, schematic, spawnOrigin);
            registry.saveMastCount(ship.getId(), ShipSteeringManager.MAST_COUNTS.getOrDefault(ship.getId(), 1));
            registry.setOwnership(player.getUuid(), ship.getId());
            player.sendMessage(Text.literal("§a+ §f" + shipName + " §7spawned (ID " + ship.getId() + ")"), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cSpawn failed: " + e.getMessage()), true);
        }
    }

    public static void sendShipwrightMenu(ServerPlayerEntity player, List<String> blueprints,
            long shipId, int hp, int maxHp, int mastCount, Map<String, Integer> upgrades) {
        var buf = PacketByteBufs.create();
        buf.writeCollection(blueprints, (b, s) -> b.writeString(s, 64));
        buf.writeLong(shipId);
        buf.writeInt(hp);
        buf.writeInt(maxHp);
        buf.writeInt(mastCount);
        for (ShipUpgrades.Def def : ShipUpgrades.ALL) {
            buf.writeInt(upgrades.getOrDefault(def.key(), 0));
        }
        ServerPlayNetworking.send(player, OPEN_SHIPWRIGHT, buf);
    }

    private static void handleShipwrightSpawn(ServerPlayerEntity player, String name) {
        ServerWorld world = player.getServerWorld();
        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());

        // 1-ship limit applies to all players, including creative
        Long ownedShipId = registry.getOwnedShip(player.getUuid());
        if (ownedShipId != null) {
            if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                player.sendMessage(Text.literal(
                    "§cYou already own a ship. Use /scuttle to destroy it first."), true);
                return;
            }
            // Ship no longer exists — clear stale ownership and allow proceed
            registry.clearOwnership(player.getUuid());
        }

        // Zone tier check — blocks server-side even if client somehow sends a locked ship name
        int requiredZone = ShipTiers.getRequiredZone(name);
        int playerZone   = PlayerProgressionManager.getUnlockedZone(player);
        if (playerZone < requiredZone) {
            player.sendMessage(Text.literal(
                "§cThe " + name + " requires Zone " + requiredZone + " access. Keep exploring!"), true);
            return;
        }

        if (!player.isCreative()) {
            if (player.getInventory().count(ModItems.COIN) < SHIP_COMMISSION_COST) {
                player.sendMessage(Text.literal(
                    "§cYou need " + SHIP_COMMISSION_COST + "x Coin to commission a ship."), true);
                return;
            }
            int remaining = SHIP_COMMISSION_COST;
            for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                var s = player.getInventory().getStack(i);
                if (!s.isOf(ModItems.COIN)) continue;
                int take = Math.min(s.getCount(), remaining);
                s.decrement(take);
                remaining -= take;
            }
        }

        Vec3d look = player.getRotationVec(1.0f);
        BlockPos origin = BlockPos.ofFloored(
            player.getX() + look.x * 20, 75, player.getZ() + look.z * 20);
        try {
            ShipSchematic schematic = ShipSchematic.load(name);
            ServerShip ship = ShipSpawner.spawn(world, schematic, origin);
            int masts = ShipSteeringManager.MAST_COUNTS.getOrDefault(ship.getId(), 1);
            registry.saveMastCount(ship.getId(), masts);
            registry.setOwnership(player.getUuid(), ship.getId());
            player.sendMessage(Text.literal("§a+ §f" + name + " §7commissioned!"), true);
            AdvancementHelper.grant(player, "money_well_spent");
            // First ship unlocks Zone 1 — lets the player sail the open seas
            PlayerProgressionManager.unlockZone(player, 1);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cSpawn failed: " + e.getMessage()), true);
        }
    }

    private static void handleShipwrightUpgrade(ServerPlayerEntity player, long shipId, String upgradeKey) {
        if (!ShipSteeringManager.MAST_COUNTS.containsKey(shipId)) {
            player.sendMessage(Text.literal("§cNo registered ship with that ID."), true);
            return;
        }
        if (AiShipController.AI_SHIPS.containsKey(shipId)) {
            player.sendMessage(Text.literal("§cCannot upgrade an AI ship."), true);
            return;
        }

        ShipUpgrades.Def def = ShipUpgrades.find(upgradeKey);
        if (def == null) {
            player.sendMessage(Text.literal("§cUnknown upgrade type."), true);
            return;
        }

        ServerWorld world = player.getServerWorld();
        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());
        int currentLevel = registry.getUpgradeLevel(shipId, upgradeKey);

        if (currentLevel >= def.maxLevel()) {
            player.sendMessage(Text.literal("§c" + def.displayName() + " is already at max level."), true);
            return;
        }
        if (!player.isCreative() && !ShipUpgrades.canAfford(player, def, currentLevel)) {
            player.sendMessage(Text.literal(
                "§cNeed: " + ShipUpgrades.costString(def) + " for " + def.displayName() + "."), true);
            return;
        }

        if (!player.isCreative()) ShipUpgrades.consume(player, def);

        int newLevel = currentLevel + 1;
        registry.setUpgradeLevel(shipId, upgradeKey, newLevel);

        // Apply upgrade effects immediately
        switch (upgradeKey) {
            case "speed" -> {
                ShipSteeringManager.MAST_COUNTS.merge(shipId, 1, Integer::sum);
                registry.saveMastCount(shipId, ShipSteeringManager.MAST_COUNTS.get(shipId));
            }
            // hull: effective max HP increases automatically via getEffectiveMaxHp()
            // cannon: stored only, no immediate effect
            // armor: applied in ShipHealthState.damage() via getUpgradeLevel()
        }

        player.sendMessage(Text.literal(
            "§a+ " + def.displayName() + " upgraded to level " + newLevel + "/" + def.maxLevel()), false);
    }

    public static void sendMapMerchantMenu(ServerPlayerEntity player) {
        var buf = PacketByteBufs.create();
        buf.writeBoolean(PlayerProgressionManager.hasRadar(player));
        buf.writeInt(player.getInventory().count(ModItems.PIRATE_COIN));
        ServerPlayNetworking.send(player, OPEN_MAP_MERCHANT, buf);
    }

    /** Prices (in pirate coins) matching index sent by MAP_MERCHANT_BUY. */
    private static final int[] SHOP_COSTS = {15, 5, 15, 30, 10, 0, 0, 0, 0};

    private static void handleMapMerchantBuy(ServerPlayerEntity player, int index) {
        if (index < 0 || index >= SHOP_COSTS.length) return;

        // Radar is one-time only
        if (index == 0 && PlayerProgressionManager.hasRadar(player)) {
            player.sendMessage(Text.literal("§7You already have the Ship Radar."), true);
            return;
        }

        int cost = SHOP_COSTS[index];
        if (!player.isCreative() && player.getInventory().count(ModItems.PIRATE_COIN) < cost) {
            player.sendMessage(Text.literal("§cNot enough doubloons. You need " + cost + "."), true);
            return;
        }

        if (!player.isCreative()) {
            int remaining = cost;
            for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                var s = player.getInventory().getStack(i);
                if (!s.isOf(ModItems.PIRATE_COIN)) continue;
                int take = Math.min(s.getCount(), remaining);
                s.decrement(take);
                remaining -= take;
            }
        }

        switch (index) {
            case 0 -> {
                PlayerProgressionManager.unlockRadar(player);
                player.sendMessage(Text.literal("§aShip Radar unlocked! It will appear on your HUD."), false);
                AdvancementHelper.grant(player, "eyes_on_the_horizon");
            }
            case 1 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.TREASURE_MAP_COMMON));
            case 2 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.TREASURE_MAP_RARE));
            case 3 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.TREASURE_MAP_LEGENDARY));
            case 4 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.BOUNTY_MAP));
            case 5 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.CANNON_BALL, 64));
            case 6 -> player.giveItemStack(new net.minecraft.item.ItemStack(
                net.get900.pixelpirates.block.ModBlocks.SHIPWRIGHT_TABLE));
            case 7 -> player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.CUTLASS));
            case 8 -> {
                player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.BANANA, 8));
                player.giveItemStack(new net.minecraft.item.ItemStack(ModItems.GROG, 3));
            }
        }
    }

    /** Sends a radar snapshot to every online player. Called on the server tick. */
    public static void broadcastRadarUpdates(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
            if (sw == null) continue;
            for (ServerPlayerEntity player : world.getPlayers()) {
                sendRadarUpdate(player, sw);
            }
        }
    }

    private static void sendRadarUpdate(ServerPlayerEntity player, VsiServerShipWorld sw) {
        ShipRegistryState registry = ShipRegistryState.get(player.getServer().getOverworld());
        var buf = PacketByteBufs.create();

        // Whether this player has purchased the radar upgrade
        buf.writeBoolean(PlayerProgressionManager.hasRadar(player));

        // Own ship position
        Long ownedId = registry.getOwnedShip(player.getUuid());
        ServerShip playerShip = (ownedId != null) ? sw.getLoadedShips().getById(ownedId) : null;
        if (playerShip != null) {
            org.joml.Vector3dc pos = playerShip.getTransform().getPositionInWorld();
            buf.writeBoolean(true);
            buf.writeDouble(pos.x());
            buf.writeDouble(pos.z());
        } else {
            buf.writeBoolean(false);
            buf.writeDouble(0); // placeholders so packet length is predictable
            buf.writeDouble(0);
        }

        // Nearby AI ship positions (within 500 blocks)
        double px = player.getX(), pz = player.getZ();
        java.util.List<double[]> nearby = new java.util.ArrayList<>();
        for (long shipId : AiShipController.AI_SHIPS.keySet()) {
            ServerShip ship = sw.getLoadedShips().getById(shipId);
            if (ship == null) continue;
            org.joml.Vector3dc pos = ship.getTransform().getPositionInWorld();
            double dx = pos.x() - px, dz = pos.z() - pz;
            if (dx * dx + dz * dz <= 200.0 * 200.0) {
                nearby.add(new double[]{pos.x(), pos.z()});
            }
        }
        buf.writeInt(nearby.size());
        for (double[] s : nearby) {
            buf.writeDouble(s[0]);
            buf.writeDouble(s[1]);
        }

        ServerPlayNetworking.send(player, RADAR_UPDATE, buf);
    }

    /** Destroys the player's owned ship and clears all maps. Returns true if a ship was scuttled. */
    public static boolean scuttlePlayerShip(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());

        Long shipId = registry.getOwnedShip(player.getUuid());
        if (shipId == null || !ShipSteeringManager.MAST_COUNTS.containsKey(shipId)) {
            if (shipId != null) registry.clearOwnership(player.getUuid());
            return false;
        }

        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
        if (sw != null) {
            ServerShip ship = sw.getLoadedShips().getById(shipId);
            if (ship != null) sw.deleteShip(ship);
        }

        ShipSteeringManager.MAST_COUNTS.remove(shipId);
        ShipSteeringManager.WATERLINE_OFFSETS.remove(shipId);
        ShipSteeringManager.SHIP_INPUTS.remove(shipId);
        ShipSteeringManager.HELM_STEER_FRESHNESS.remove(shipId);
        ShipHealthState.SINKING_SHIPS.remove(shipId);
        registry.removeShip(shipId);
        return true;
    }

    private static void handleShipMusicRequest(ServerPlayerEntity player) {
        // getShipManagingBlock() expects ship-space block coords (inside VS2 ship chunks),
        // not world-space player feet coords — so block-pos checks always return null.
        // Instead: scan all registered player ships and proximity-check against their
        // world-space center (25 blocks horizontal, 10 blocks vertical).
        ServerWorld world = player.getServerWorld();
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
        if (sw != null) {
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            for (long shipId : ShipSteeringManager.MAST_COUNTS.keySet()) {
                if (AiShipController.AI_SHIPS.containsKey(shipId)) continue;
                ServerShip ship = sw.getLoadedShips().getById(shipId);
                if (ship == null) continue;
                Vector3dc sp = ship.getTransform().getPositionInWorld();
                double dx = px - sp.x(), dz = pz - sp.z(), dy = py - sp.y();
                if (dx * dx + dz * dz < 625 && Math.abs(dy) < 10) {
                    int trackIndex = MUSIC_RNG.nextInt(ModSounds.SHIP_TRACKS.size());
                    var buf = PacketByteBufs.create();
                    buf.writeInt(trackIndex);
                    ServerPlayNetworking.send(player, SHIP_MUSIC_PLAY, buf);
                    return;
                }
            }
        }
        var buf = PacketByteBufs.create();
        buf.writeInt(-1);
        ServerPlayNetworking.send(player, SHIP_MUSIC_PLAY, buf);
    }
}
