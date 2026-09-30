package net.get900.pixelpirates.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.FloatingBarrelEntity;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.AdminTestState;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSpawner;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModCommands {

    /** /pptest god|clearsight [on|off] */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> testToggle(String name, boolean god) {
        return CommandManager.literal(name)
                .executes(ctx -> setTestMode(ctx.getSource().getPlayerOrThrow(), god, null))
                .then(CommandManager.literal("on").executes(ctx -> setTestMode(ctx.getSource().getPlayerOrThrow(), god, true)))
                .then(CommandManager.literal("off").executes(ctx -> setTestMode(ctx.getSource().getPlayerOrThrow(), god, false)));
    }

    private static int setTestMode(ServerPlayerEntity p, boolean god, Boolean value) {
        boolean on = value != null ? value : !(god ? net.get900.pixelpirates.world.TestModes.god(p) : net.get900.pixelpirates.world.TestModes.clearSight(p));
        if (god) net.get900.pixelpirates.world.TestModes.setGod(p, on);
        else net.get900.pixelpirates.world.TestModes.setClearSight(p, on);
        p.sendMessage(Text.literal("[~] " + (god ? "God mode " : "Clear sight ") + (on ? "ON" : "off")
                + (god && on ? " - mobs still target and hit you, but you cannot die" : "")
                + (!god && on ? " - no zone fog, particles, hazards or darkness" : "")).formatted(on ? Formatting.GREEN : Formatting.GRAY), false);
        return 1;
    }

    private static int leviathanBuild(ServerCommandSource src, String site, boolean ruined) {
        BlockPos at = BlockPos.ofFloored(src.getPosition());
        int n = net.get900.pixelpirates.world.leviathan.LeviathanSites.buildAt(src.getWorld(), site, at, ruined);
        src.sendFeedback(() -> Text.literal("[~] Built " + site + (ruined ? " (ruined)" : "") + " around " + at.toShortString() + " (" + n + " chunks)"), true);
        return 1;
    }

    private static int leviathanDo(ServerCommandSource src, String op, String arg) {
        try {
            String out = net.get900.pixelpirates.world.leviathan.LeviathanHunt.debug(src.getWorld(), op, arg);
            src.sendFeedback(() -> Text.literal("[~] " + out), false);
            return 1;
        } catch (Exception e) {
            src.sendError(Text.literal("[~] " + op + " failed: " + e));
            return 0;
        }
    }

    /** /ppboss heart ... - the nearest Abyssal Heart (testing; AbyssalHeartEntity#debug). */
    private static int heartCmd(ServerCommandSource src, String op, int arg) {
        net.get900.pixelpirates.entity.mob.AbyssalHeartEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : src.getWorld().getEntitiesByClass(net.get900.pixelpirates.entity.mob.AbyssalHeartEntity.class,
                net.minecraft.util.math.Box.of(src.getPosition(), 400, 400, 400), net.minecraft.entity.Entity::isAlive)) {
            double d = e.squaredDistanceTo(src.getPosition());
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) { src.sendError(Text.literal("[~] No Abyssal Heart within 200 blocks.")); return 0; }
        String out = op.equals("status") ? best.debugStatus() : best.debug(op, arg);
        src.sendFeedback(() -> Text.literal("[~] " + out), false);
        return 1;
    }

    private static int revenantCmd(ServerCommandSource src, String op) {
        net.get900.pixelpirates.entity.mob.ChainedRevenantEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : src.getWorld().getEntitiesByClass(net.get900.pixelpirates.entity.mob.ChainedRevenantEntity.class,
                net.minecraft.util.math.Box.of(src.getPosition(), 400, 400, 400), net.minecraft.entity.Entity::isAlive)) {
            double d = e.squaredDistanceTo(src.getPosition());
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) { src.sendError(Text.literal("[~] No Chained Revenant within 200 blocks.")); return 0; }
        var r = best;
        switch (op) {
            case "wake" -> r.debugWake();
            case "sunder" -> r.debugSunder();
            case "limbs" -> r.debugBreakLimbs();
            case "quartering" -> r.debugAoe(0);
            case "floor" -> r.debugAoe(1);
            case "lights" -> r.debugLights(1);
            case "dark" -> r.debugLights(0);
            default -> { }
        }
        src.sendFeedback(() -> Text.literal("[~] " + r.debugStatus()), false);
        return 1;
    }

    private static int krakenCmd(ServerCommandSource src, String op) {
        net.get900.pixelpirates.entity.mob.KrakenEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : src.getWorld().getEntitiesByClass(net.get900.pixelpirates.entity.mob.KrakenEntity.class,
                net.minecraft.util.math.Box.of(src.getPosition(), 256, 256, 256), net.minecraft.entity.Entity::isAlive)) {
            double d = e.squaredDistanceTo(src.getPosition());
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) { src.sendError(Text.literal("[~] No Kraken within 128 blocks.")); return 0; }
        var k = best;
        if (op.equals("arms")) k.debugKillArms();
        if (op.equals("swallow")) k.debugSwallow();
        if (op.equals("harpoon")) k.debugHarpoon();
        if (op.equals("bulwark")) k.startBulwark();
        src.sendFeedback(() -> Text.literal("[~] " + k.debugStatus()), false);
        return 1;
    }

    private static int bloodfinCmd(ServerCommandSource src, String op) {
        net.get900.pixelpirates.entity.mob.BloodfinEntity best = null;
        double bd = Double.MAX_VALUE;
        for (var e : src.getWorld().getEntitiesByClass(net.get900.pixelpirates.entity.mob.BloodfinEntity.class,
                net.minecraft.util.math.Box.of(src.getPosition(), 256, 256, 256), net.minecraft.entity.Entity::isAlive)) {
            double d = e.squaredDistanceTo(src.getPosition());
            if (d < bd) { bd = d; best = e; }
        }
        if (best == null) { src.sendError(Text.literal("[~] No Bloodfin within 128 blocks.")); return 0; }
        var b = best;
        if (op.equals("pin")) b.debugPin();
        if (op.equals("tear")) b.debugTear();
        if (op.equals("devour")) b.debugDevour();
        src.sendFeedback(() -> Text.literal("[~] " + b.debugStatus()), false);
        return 1;
    }

    private static int courtCmd(ServerCommandSource src, int open) {
        var type = net.get900.pixelpirates.entity.mob.ModMobs.TYPES.get("abyssal_king");
        var kings = type == null ? java.util.List.<net.minecraft.entity.Entity>of() : src.getWorld().getEntitiesByType(type,
                net.minecraft.util.math.Box.of(src.getPosition(), 256, 256, 256), net.minecraft.entity.Entity::isAlive);
        net.get900.pixelpirates.entity.mob.AbyssalKingEntity king = null;
        double best = Double.MAX_VALUE;
        for (var e : kings)
            if (e instanceof net.get900.pixelpirates.entity.mob.AbyssalKingEntity k) {
                double d = k.squaredDistanceTo(src.getPosition()) - (k.hasCourt() ? 1e9 : 0);   // prefer kings with a court
                if (d < best) { best = d; king = k; }
            }
        if (king == null) { src.sendError(Text.literal("[~] No Abyssal King within 128 blocks.")); return 0; }
        var k = king;
        if (open >= 0) k.debugSetSluices(open);
        src.sendFeedback(() -> Text.literal("[~] " + k.debugStatus()), false);
        return 1;
    }

    private static int bossStatus(ServerPlayerEntity p) {
        var chain = net.get900.pixelpirates.entity.mob.BossProgression.CHAIN;
        int step = net.get900.pixelpirates.entity.mob.BossProgression.progress(p);
        p.sendMessage(Text.literal("[~] Boss chain: " + step + "/" + chain.size() + " beaten").formatted(Formatting.GOLD), false);
        for (int i = 0; i < chain.size(); i++) {
            var st = chain.get(i);
            Formatting f = i < step ? Formatting.GREEN : i == step ? Formatting.YELLOW : Formatting.DARK_GRAY;
            String mark = i < step ? "[x] " : i == step ? "[>] " : "[ ] ";
            p.sendMessage(Text.literal("  " + mark + (i + 1) + ". " + st.name() + "  (" + st.lair() + ")").formatted(f), false);
        }
        return 1;
    }

    private static int giveSlayer(ServerPlayerEntity p) {
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.BOSS_SLAYER);
        if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
        p.sendMessage(Text.literal("[~] Boss Slayer: hit to kill, right-click to kill what you aim at (64 blocks)").formatted(Formatting.GREEN), false);
        return 1;
    }

    /** /ppdungeon build: origin = the block under the source; rotation -1 = random. */
    private static int buildDungeon(ServerCommandSource source, String type, int rotation) {
        type = switch (type) { case "grotto" -> "smugglers_grotto"; case "shrine" -> "tidewater_shrine"; case "galleon" -> "sunken_galleon"; default -> type; };
        ServerWorld world = source.getWorld();
        BlockPos origin = BlockPos.ofFloored(source.getPosition()).down();
        var t = net.get900.pixelpirates.world.dungeon.Dungeons.byId(type);
        if (t == null) {
            source.sendError(Text.literal("Unknown dungeon: " + type));
            return 0;
        }
        if (type.equals(net.get900.pixelpirates.world.dungeon.GallowsGrotto.ID)) {                  // hundreds of blocks: its own builder
            int n = net.get900.pixelpirates.world.dungeon.GallowsGrotto.buildAll(world, origin);
            source.sendFeedback(() -> Text.literal("[~] Built the Gallows Grotto around " + origin.toShortString() + " (" + n + " chunks)"), true);
            return 1;
        }
        if (type.equals(net.get900.pixelpirates.world.dungeon.TitansChest.ID)) {                   // ~170 x 150 blocks: its own builder
            int n = net.get900.pixelpirates.world.dungeon.TitansChest.buildAll(world, origin);
            source.sendFeedback(() -> Text.literal("[~] Built the Titan's Chest around " + origin.toShortString() + " (" + n + " chunks)"), true);
            return 1;
        }
        var rot = rotation < 0 ? net.minecraft.util.BlockRotation.random(world.random) : net.minecraft.util.BlockRotation.values()[rotation];
        var b = new net.get900.pixelpirates.world.dungeon.DungeonBuilder(world, origin, rot, world.random);
        int depth = net.get900.pixelpirates.world.dungeon.DungeonFeature.waterDepth(world, origin);
        try {
            t.builder().build(b, depth);
        } catch (Exception e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.error("[Dungeon] /ppdungeon {} failed", type, e);
            source.sendError(Text.literal("Build failed: " + e));
            return 0;
        }
        String built = type;
        source.sendFeedback(() -> Text.literal("[~] Built " + built + " (phase " + t.phase() + ", depth " + depth + ") at " + origin.toShortString()), true);
        return 1;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /ppdungeon <id>  — build any dungeon in Dungeons.ALL right here (testing; features have no
            // /locate). Origin = the block under you; the worldgen site checks are skipped. Short aliases
            // grotto/shrine/galleon still work.
            dispatcher.register(CommandManager.literal("ppdungeon")
                    .requires(source -> source.hasPermissionLevel(2))
                    // /ppdungeon locate <id>  — nearest predicted site (same grid prediction worldgen uses)
                    .then(CommandManager.literal("locate")
                            .then(CommandManager.argument("id", StringArgumentType.word())
                                    .suggests((c, sb) -> { for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL) sb.suggest(t.id()); return sb.buildFuture(); })
                                    .executes(ctx -> {
                                        String id = StringArgumentType.getString(ctx, "id");
                                        var t = net.get900.pixelpirates.world.dungeon.Dungeons.byId(id);
                                        ServerCommandSource source = ctx.getSource();
                                        if (t == null) { source.sendError(Text.literal("Unknown dungeon: " + id)); return 0; }
                                        ServerWorld world = source.getWorld();
                                        var gen = world.getChunkManager().getChunkGenerator();
                                        var pctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(world.getSeed(), gen,
                                                world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
                                        BlockPos from = BlockPos.ofFloored(source.getPosition());
                                        BlockPos hit = net.get900.pixelpirates.world.dungeon.DungeonPlacement.locate(t, pctx, from, 12);
                                        if (hit == null) { source.sendError(Text.literal("No " + id + " within range (wrong dimension or zone?)")); return 0; }
                                        int dist = (int) Math.sqrt(hit.getSquaredDistance(from.getX(), 0, from.getZ()));
                                        source.sendFeedback(() -> Text.literal("[~] Nearest " + id + ": " + hit.getX() + " ~ " + hit.getZ() + " (" + dist + " blocks)"), false);
                                        return 1;
                                    })))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                            .suggests((c, sb) -> { for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL) sb.suggest(t.id()); return sb.buildFuture(); })
                            .executes(ctx -> buildDungeon(ctx.getSource(), StringArgumentType.getString(ctx, "type"), -1))
                            // optional fixed rotation 0-3 (0 = entrance faces north) - handy for screenshots/tests
                            .then(CommandManager.argument("rotation", IntegerArgumentType.integer(0, 3))
                                    .executes(ctx -> buildDungeon(ctx.getSource(), StringArgumentType.getString(ctx, "type"),
                                            IntegerArgumentType.getInteger(ctx, "rotation"))))
                    )
            );

            // /pptest god|clearsight [on|off]  /pptest slayer  /pptest all  /pptest status  — testing aids
            // (runtime only; see world/TestModes). Bare god/clearsight toggles.
            dispatcher.register(CommandManager.literal("pptest")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(testToggle("god", true))
                    .then(testToggle("clearsight", false))
                    .then(CommandManager.literal("slayer").executes(ctx -> giveSlayer(ctx.getSource().getPlayerOrThrow())))
                    .then(CommandManager.literal("all").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        net.get900.pixelpirates.world.TestModes.setGod(p, true);
                        net.get900.pixelpirates.world.TestModes.setClearSight(p, true);
                        giveSlayer(p);
                        p.sendMessage(Text.literal("[~] Test kit: god mode ON, clear sight ON, Boss Slayer given").formatted(Formatting.GREEN), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("status").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        p.sendMessage(Text.literal("[~] god: " + (net.get900.pixelpirates.world.TestModes.god(p) ? "ON" : "off")
                                + "   clearsight: " + (net.get900.pixelpirates.world.TestModes.clearSight(p) ? "ON" : "off")).formatted(Formatting.YELLOW), false);
                        return 1;
                    })));

            // /ppleviathan - THE LEVIATHAN HUNT (world/leviathan). `awaken` is the Rift Seal's confirmation (any player, checked
            // there); the rest are testing: status | locate | build <site> [ruined] | do <op> [arg]
            //   ops: wake, summon, horn, bell <i>, stage <0-7>, flee, arrive, ruin <1|2>, restore <1|2>, tide <n>, drain, bane, kill, reset, act <ability>
            // /pparmortest - boss sets: measured damage per loadout beside the nearest boss (item/BossArmor#test)
            dispatcher.register(CommandManager.literal("pparmortest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                for (String line : net.get900.pixelpirates.item.BossArmor.test(ctx.getSource().getWorld(), ctx.getSource().getPosition()))
                    ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                return 1;
            }));

            dispatcher.register(CommandManager.literal("ppleviathan")
                    .then(CommandManager.literal("awaken").executes(ctx -> net.get900.pixelpirates.world.leviathan.LeviathanHunt.awaken(ctx.getSource().getPlayerOrThrow())))
                    .then(CommandManager.literal("status").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                        String out = net.get900.pixelpirates.world.leviathan.LeviathanHunt.status(ctx.getSource().getWorld());
                        ctx.getSource().sendFeedback(() -> Text.literal("[~] " + out), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("build").requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("site", StringArgumentType.word())
                                    .suggests((c, sb) -> { for (String id : new String[]{"rift", "saltmarrow", "gullet", "brightwater", "spire"}) sb.suggest(id); return sb.buildFuture(); })
                                    .executes(ctx -> leviathanBuild(ctx.getSource(), StringArgumentType.getString(ctx, "site"), false))
                                    .then(CommandManager.literal("ruined").executes(ctx -> leviathanBuild(ctx.getSource(), StringArgumentType.getString(ctx, "site"), true)))))
                    .then(CommandManager.literal("do").requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("op", StringArgumentType.word())
                                    .executes(ctx -> leviathanDo(ctx.getSource(), StringArgumentType.getString(ctx, "op"), ""))
                                    .then(CommandManager.argument("arg", StringArgumentType.word())
                                            .executes(ctx -> leviathanDo(ctx.getSource(), StringArgumentType.getString(ctx, "op"), StringArgumentType.getString(ctx, "arg")))))));

            // /ppboss status  /ppboss set <0-10>  - boss chain progress (entity/mob/BossProgression).
            // set N = the first N chain bosses count as beaten (also unlocks their zones; no relics given).
            dispatcher.register(CommandManager.literal("ppboss")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("status").executes(ctx -> bossStatus(ctx.getSource().getPlayerOrThrow())))
                    // /ppboss dutchman       - raise the Flying Dutchman near you (as if you rang the Drowned Bell)
                    // /ppboss dutchman sink  - sink every active Dutchman (the captain breaks free and boards)
                    .then(CommandManager.literal("dutchman")
                            .executes(ctx -> {
                                ServerCommandSource src = ctx.getSource();
                                boolean ok = net.get900.pixelpirates.world.GhostShipEncounter.summon(src.getWorld(),
                                        BlockPos.ofFloored(src.getPosition()), src.getPlayer());
                                src.sendFeedback(() -> Text.literal(ok ? "[~] The Dutchman rises." : "[~] Could not raise the Dutchman."), true);
                                return ok ? 1 : 0;
                            })
                            .then(CommandManager.literal("sink").executes(ctx -> {
                                ServerWorld w = ctx.getSource().getWorld();
                                int n = 0;
                                for (long id : net.get900.pixelpirates.world.GhostShipEncounter.activeIds()) {
                                    net.get900.pixelpirates.world.ShipHealthState.get(w).setHealth(id, 0);
                                    net.get900.pixelpirates.world.ShipHealthState.SINKING_SHIPS.put(id, true);
                                    n++;
                                }
                                int sunk = n;
                                ctx.getSource().sendFeedback(() -> Text.literal("[~] Sinking " + sunk + " Dutchman."), true);
                                return n;
                            })))
                    // /ppboss revenant status|wake|sunder|limbs - the nearest Chained Revenant (testing)
                    .then(CommandManager.literal("revenant")
                            .then(CommandManager.literal("status").executes(ctx -> revenantCmd(ctx.getSource(), "status")))
                            .then(CommandManager.literal("wake").executes(ctx -> revenantCmd(ctx.getSource(), "wake")))
                            .then(CommandManager.literal("sunder").executes(ctx -> revenantCmd(ctx.getSource(), "sunder")))
                            .then(CommandManager.literal("limbs").executes(ctx -> revenantCmd(ctx.getSource(), "limbs")))
                            .then(CommandManager.literal("quartering").executes(ctx -> revenantCmd(ctx.getSource(), "quartering")))
                            .then(CommandManager.literal("floor").executes(ctx -> revenantCmd(ctx.getSource(), "floor")))
                            .then(CommandManager.literal("lights").executes(ctx -> revenantCmd(ctx.getSource(), "lights")))
                            .then(CommandManager.literal("dark").executes(ctx -> revenantCmd(ctx.getSource(), "dark"))))
                    // /ppboss heart status|arrest|clot|nodes|flatline|shock|triple|lash|hemorrhage|systole|embolism|memory <0-5>
                    .then(CommandManager.literal("heart")
                            .then(CommandManager.literal("status").executes(ctx -> heartCmd(ctx.getSource(), "status", 0)))
                            .then(CommandManager.literal("arrest").executes(ctx -> heartCmd(ctx.getSource(), "arrest", 0)))
                            .then(CommandManager.literal("clot").executes(ctx -> heartCmd(ctx.getSource(), "clot", 0)))
                            .then(CommandManager.literal("nodes").executes(ctx -> heartCmd(ctx.getSource(), "nodes", 0)))
                            .then(CommandManager.literal("flatline").executes(ctx -> heartCmd(ctx.getSource(), "flatline", 0)))
                            .then(CommandManager.literal("shock").executes(ctx -> heartCmd(ctx.getSource(), "shock", 0)))
                            .then(CommandManager.literal("triple").executes(ctx -> heartCmd(ctx.getSource(), "triple", 0)))
                            .then(CommandManager.literal("lash").executes(ctx -> heartCmd(ctx.getSource(), "lash", 0)))
                            .then(CommandManager.literal("hemorrhage").executes(ctx -> heartCmd(ctx.getSource(), "hemorrhage", 0)))
                            .then(CommandManager.literal("systole").executes(ctx -> heartCmd(ctx.getSource(), "systole", 0)))
                            .then(CommandManager.literal("embolism").executes(ctx -> heartCmd(ctx.getSource(), "embolism", 0)))
                            .then(CommandManager.literal("memory").then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5))
                                    .executes(ctx -> heartCmd(ctx.getSource(), "memory", IntegerArgumentType.getInteger(ctx, "n"))))))
                    // /ppboss kraken status|arms|swallow - the nearest Kraken (testing)
                    .then(CommandManager.literal("kraken")
                            .then(CommandManager.literal("status").executes(ctx -> krakenCmd(ctx.getSource(), "status")))
                            .then(CommandManager.literal("arms").executes(ctx -> krakenCmd(ctx.getSource(), "arms")))
                            .then(CommandManager.literal("swallow").executes(ctx -> krakenCmd(ctx.getSource(), "swallow")))
                            .then(CommandManager.literal("harpoon").executes(ctx -> krakenCmd(ctx.getSource(), "harpoon")))
                            .then(CommandManager.literal("bulwark").executes(ctx -> krakenCmd(ctx.getSource(), "bulwark"))))
                    // /ppboss bloodfin status|pin|tear - the nearest Bloodfin (testing)
                    .then(CommandManager.literal("bloodfin")
                            .then(CommandManager.literal("status").executes(ctx -> bloodfinCmd(ctx.getSource(), "status")))
                            .then(CommandManager.literal("pin").executes(ctx -> bloodfinCmd(ctx.getSource(), "pin")))
                            .then(CommandManager.literal("tear").executes(ctx -> bloodfinCmd(ctx.getSource(), "tear")))
                            .then(CommandManager.literal("devour").executes(ctx -> bloodfinCmd(ctx.getSource(), "devour"))))
                    // /ppboss court status|open <n>|close - the nearest Abyssal King's floodgates (testing)
                    .then(CommandManager.literal("court")
                            .then(CommandManager.literal("status").executes(ctx -> courtCmd(ctx.getSource(), -1)))
                            .then(CommandManager.literal("close").executes(ctx -> courtCmd(ctx.getSource(), 0)))
                            .then(CommandManager.literal("open").then(CommandManager.argument("n", IntegerArgumentType.integer(1, 4))
                                    .executes(ctx -> courtCmd(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"))))))
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("step", IntegerArgumentType.integer(0, net.get900.pixelpirates.entity.mob.BossProgression.CHAIN.size()))
                                    .executes(ctx -> {
                                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                        int step = IntegerArgumentType.getInteger(ctx, "step");
                                        var chain = net.get900.pixelpirates.entity.mob.BossProgression.CHAIN;
                                        net.get900.pixelpirates.entity.mob.BossProgression.setProgress(p, step);
                                        for (int i = 0; i < step; i++) {
                                            int zone = net.get900.pixelpirates.entity.mob.MobSpecs.get(chain.get(i).boss()).unlocksZone();
                                            if (zone > 0) PlayerProgressionManager.unlockZone(p, zone);
                                        }
                                        return bossStatus(p);
                                    }))));

            // /ppunlock <zone 1-5>  — grant the executing player access up to that zone
            dispatcher.register(CommandManager.literal("ppunlock")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("zone", IntegerArgumentType.integer(1, 5))
                            .executes(ctx -> {
                                int zone = IntegerArgumentType.getInteger(ctx, "zone");
                                ServerCommandSource source = ctx.getSource();
                                ServerPlayerEntity player = source.getPlayerOrThrow();
                                PlayerProgressionManager.unlockZone(player, zone);
                                source.sendFeedback(
                                        () -> Text.literal("§aUnlocked zones up to " + zone + " for " + player.getName().getString()),
                                        false
                                );
                                return 1;
                            })
                    )
            );

            // /ppai spawn <blueprint>  — spawn an AI-controlled ship from a saved blueprint
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("spawn")
                            .then(CommandManager.argument("blueprint", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "blueprint");
                                        ServerCommandSource source = ctx.getSource();
                                        ServerPlayerEntity player = source.getPlayerOrThrow();
                                        ServerWorld world = player.getServerWorld();

                                        // Spawn 40 blocks ahead at Y=75
                                        Vec3d look = player.getRotationVec(1.0f);
                                        BlockPos origin = BlockPos.ofFloored(
                                            player.getX() + look.x * 40,
                                            75,
                                            player.getZ() + look.z * 40
                                        );

                                        try {
                                            ShipSchematic schematic = ShipSchematic.load(name);
                                            ServerShip ship = ShipSpawner.spawn(world, schematic, origin);

                                            // Convert cannon world positions to VS2 ship-space
                                            // using the initial inverse transform (right after assembly).
                                            Matrix4d worldToShip = new Matrix4d(ship.getTransform().getShipToWorld()).invert();
                                            java.util.List<Vector3d> cannons = new java.util.ArrayList<>();
                                            for (ShipSchematic.Entry e : schematic.getEntries()) {
                                                net.minecraft.block.BlockState bs = ShipSchematic.restoreState(e.stateNbt());
                                                if (bs != null && bs.isOf(ModBlocks.SHIP_CANNON)) {
                                                    BlockPos wp = origin.add(e.relPos());
                                                    Vector3d wpos = new Vector3d(wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5);
                                                    cannons.add(worldToShip.transformPosition(wpos, new Vector3d()));
                                                }
                                            }
                                            AiShipController.registerAiShip(ship.getId(), world, cannons, name);
                                            source.sendFeedback(
                                                () -> Text.literal("§a+ AI ship §f" + name
                                                    + " §7spawned (ship ID " + ship.getId() + ") — state: PATROL"),
                                                false
                                            );
                                        } catch (Exception e) {
                                            source.sendError(Text.literal("§cFailed to spawn AI ship: " + e.getMessage()));
                                        }
                                        return 1;
                                    })
                            )
                    )
            );

            // /scuttle  — destroy the player's own ship and free the ownership slot
            dispatcher.register(CommandManager.literal("scuttle")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayerOrThrow();
                        boolean scuttled = ModNetworking.scuttlePlayerShip(player);
                        if (scuttled) {
                            source.sendFeedback(() -> Text.literal("§7Your ship has been scuttled."), false);
                            return 1;
                        } else {
                            source.sendError(Text.literal("You don't own a ship to scuttle."));
                            return 0;
                        }
                    })
            );

            // /ppstatus  — show current zone position and unlock level
            // /ppreputation  — show the executing player their faction standing
            dispatcher.register(CommandManager.literal("ppreputation")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        StringBuilder sb = new StringBuilder("§6=== Faction Standing ===");
                        for (Faction f : Faction.values()) {
                            int rep    = FactionManager.getReputation(player, f);
                            String standing = FactionManager.standingLabel(rep);
                            sb.append("\n").append(f.displayName)
                              .append("§7: ").append(standing).append(" §8(").append(rep).append(")");
                        }
                        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                        return 1;
                    })
            );

            // /ppreputation set <faction> <amount>  — admin override
            dispatcher.register(CommandManager.literal("ppreputation")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("set")
                            .then(CommandManager.argument("faction", StringArgumentType.word())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(Faction.REP_MIN, Faction.REP_MAX))
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                                String factionId = StringArgumentType.getString(ctx, "faction");
                                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                Faction faction = Faction.fromId(factionId);
                                                ((net.get900.pixelpirates.util.PlayerProgressionComponent) player)
                                                    .pp_setFactionRep(faction.ordinal(), amount);
                                                ctx.getSource().sendFeedback(
                                                    () -> Text.literal("§aSet " + faction.displayName + " §arep to " + amount),
                                                    false
                                                );
                                                return 1;
                                            })
                                    )
                            )
                    )
            );

            dispatcher.register(CommandManager.literal("ppstatus")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayerOrThrow();
                        int unlocked = PlayerProgressionManager.getUnlockedZone(player);
                        int current  = PlayerProgressionManager.getZoneAt(player.getPos());
                        source.sendFeedback(
                                () -> Text.literal(
                                        "§eZone status for §f" + player.getName().getString() +
                                        "§e:\n  Current zone: §f" + current +
                                        "§e\n  Max unlocked: §f" + unlocked),
                                false
                        );
                        return 1;
                    })
            );

            // ── /ppai list ────────────────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                ServerCommandSource src = ctx.getSource();
                                if (AiShipController.AI_SHIPS.isEmpty()) {
                                    src.sendFeedback(() -> Text.literal("§7No active AI ships."), false);
                                    return 1;
                                }
                                ServerWorld world = src.getServer().getWorlds().iterator().next();
                                src.sendFeedback(() -> Text.literal("§6AI Ships (" + AiShipController.AI_SHIPS.size() + "):"), false);
                                for (AiShipController.AiShipData data : AiShipController.AI_SHIPS.values()) {
                                    int hp = ShipHealthState.get(data.world).getHealth(data.shipId);
                                    VsiServerShipWorld vsw = VSGameUtilsKt.getShipObjectWorld(data.world);
                                    String pos = "?";
                                    if (vsw != null) {
                                        var s = vsw.getLoadedShips().getById(data.shipId);
                                        if (s != null) {
                                            var p = s.getTransform().getPositionInWorld();
                                            pos = "(" + (int)p.x() + ", " + (int)p.z() + ")";
                                        }
                                    }
                                    long id = data.shipId;
                                    String line = "§f  [" + id + "] §e" + data.faction.id
                                        + " §7| §b" + data.state.name()
                                        + " §7| HP §f" + hp
                                        + " §7| §f" + data.blueprintName
                                        + " §7| " + pos;
                                    src.sendFeedback(() -> Text.literal(line), false);
                                }
                                return 1;
                            })
                    )
            );

            // ── /ppai killall [faction] ───────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("killall")
                            .executes(ctx -> {
                                int count = killAllAiShips(ctx.getSource().getServer(), null);
                                ctx.getSource().sendFeedback(
                                    () -> Text.literal("§aDespawned §f" + count + " §aAI ships."), false);
                                return count;
                            })
                            .then(CommandManager.argument("faction", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String fId = StringArgumentType.getString(ctx, "faction");
                                        Faction f = Faction.fromId(fId);
                                        int count = killAllAiShips(ctx.getSource().getServer(), f);
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aDespawned §f" + count + " §a" + f.id + " ships."), false);
                                        return count;
                                    })
                            )
                    )
            );

            // ── /ppai setrate <ticks|reset> ───────────────────────────────────
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("setrate")
                            .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                        AdminTestState.spawnRateOverride = ticks;
                                        AdminTestState.nextSpawnTick = 0; // trigger immediately
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aSpawn rate set to §f" + ticks + " §aticks. Next spawn: immediate."),
                                            false);
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("reset")
                                    .executes(ctx -> {
                                        AdminTestState.spawnRateOverride = -1;
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aSpawn rate reset to default (2400 ticks)."), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppai setcap <n|reset> ────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("setcap")
                            .then(CommandManager.argument("cap", IntegerArgumentType.integer(0, 200))
                                    .executes(ctx -> {
                                        int cap = IntegerArgumentType.getInteger(ctx, "cap");
                                        AdminTestState.capOverride = cap;
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aAI ship cap set to §f" + cap + "§a."), false);
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("reset")
                                    .executes(ctx -> {
                                        AdminTestState.capOverride = -1;
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aAI ship cap reset to default (max(8, players x2))."), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppai info <shipId> ───────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppai")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("shipId", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                    .executes(ctx -> {
                                        long shipId = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "shipId");
                                        AiShipController.AiShipData data = AiShipController.AI_SHIPS.get(shipId);
                                        if (data == null) {
                                            ctx.getSource().sendError(Text.literal("No AI ship with ID " + shipId));
                                            return 0;
                                        }
                                        int hp = ShipHealthState.get(data.world).getHealth(shipId);
                                        StringBuilder sb = new StringBuilder("§6=== AI Ship " + shipId + " ===\n");
                                        sb.append("§7Blueprint: §f").append(data.blueprintName).append("\n");
                                        sb.append("§7Faction: §f").append(data.faction.displayName).append("\n");
                                        sb.append("§7State: §b").append(data.state).append("§7 (").append(data.stateTicks).append(" ticks)\n");
                                        sb.append("§7HP: §f").append(hp).append("\n");
                                        sb.append("§7Crew: §f").append(data.crewEntityIds.size()).append("\n");
                                        sb.append("§7Cannons: §f").append(data.cannonShipPositions.size()).append("\n");
                                        sb.append("§7Reload: §f").append(data.reloadTimer).append(" ticks\n");
                                        sb.append("§7Idle ticks: §f").append(data.idleTicks).append("/9600\n");
                                        sb.append("§7Speed mult: §f").append(data.config.speedMult).append("\n");
                                        sb.append("§7Detection: §f").append(data.config.detectionRange).append("m\n");
                                        sb.append("§7Accuracy spread: §f").append(data.config.accuracySpread);
                                        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppship hp [amount] ───────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppship")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("hp")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                long shipId = getOwnedShipId(player);
                                if (shipId < 0) { ctx.getSource().sendError(Text.literal("You don't own a ship.")); return 0; }
                                int hp = ShipHealthState.get(player.getServerWorld()).getHealth(shipId);
                                int maxHp = ShipRegistryState.get(player.getServer().getOverworld()).getEffectiveMaxHp(shipId);
                                ctx.getSource().sendFeedback(() -> Text.literal("§7Ship HP: §f" + hp + "/" + maxHp), false);
                                return 1;
                            })
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        long shipId = getOwnedShipId(player);
                                        if (shipId < 0) { ctx.getSource().sendError(Text.literal("You don't own a ship.")); return 0; }
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        ShipHealthState.get(player.getServerWorld()).setHealth(shipId, amount);
                                        ctx.getSource().sendFeedback(() -> Text.literal("§aShip HP set to §f" + amount + "§a."), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppship damage <amount> ────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppship")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("damage")
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        long shipId = getOwnedShipId(player);
                                        if (shipId < 0) { ctx.getSource().sendError(Text.literal("You don't own a ship.")); return 0; }
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        ServerWorld world = player.getServerWorld();
                                        int newHp = ShipHealthState.get(world).damage(world, shipId,
                                            net.minecraft.util.math.BlockPos.ofFloored(player.getPos()), amount);
                                        ctx.getSource().sendFeedback(() -> Text.literal("§cDealt §f" + amount + "§c damage to ship. HP: §f" + newHp), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppfaction hostile <faction> — toggle forced-hostile ───────────
            dispatcher.register(CommandManager.literal("ppfaction")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("hostile")
                            .then(CommandManager.argument("faction", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String fId = StringArgumentType.getString(ctx, "faction");
                                        Faction f = Faction.fromId(fId);
                                        boolean added = AdminTestState.forcedHostile.add(f);
                                        if (!added) AdminTestState.forcedHostile.remove(f);
                                        boolean nowHostile = !added;
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal(f.displayName + " §fis now §"
                                                + (nowHostile ? "cforced-HOSTILE" : "anot forced (uses rep)")),
                                            false);
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("list")
                                    .executes(ctx -> {
                                        String list = AdminTestState.forcedHostile.isEmpty()
                                            ? "§7(none)"
                                            : AdminTestState.forcedHostile.stream()
                                                .map(f -> f.displayName).reduce((a, b) -> a + ", " + b).orElse("");
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§6Forced-hostile factions: " + list), false);
                                        return 1;
                                    })
                            )
                    )
            );

            // ── /ppbarrel spawn ────────────────────────────────────────────────
            dispatcher.register(CommandManager.literal("ppbarrel")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("spawn")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                ServerWorld world = player.getServerWorld();
                                FloatingBarrelEntity barrel = new FloatingBarrelEntity(
                                    ModEntities.FLOATING_BARREL, world);
                                barrel.refreshPositionAndAngles(
                                    player.getX(), world.getSeaLevel(), player.getZ(), 0f, 0f);
                                world.spawnEntity(barrel);
                                ctx.getSource().sendFeedback(
                                    () -> Text.literal("§aFloating barrel spawned at sea level."), false);
                                return 1;
                            })
                    )
            );

            // ── /ppzone tp <zone> — teleport to approximate zone centre ─────────
            dispatcher.register(CommandManager.literal("ppzone")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("tp")
                            .then(CommandManager.argument("zone", IntegerArgumentType.integer(1, 5))
                                    .executes(ctx -> {
                                        int zone = IntegerArgumentType.getInteger(ctx, "zone");
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        // Zone radii (approximate centres): 1=500, 2=1750, 3=3500, 4=5750, 5=8000
                                        double[] zoneRadius = {0, 500, 1750, 3500, 5750, 8000};
                                        double r = zoneRadius[Math.min(zone, zoneRadius.length - 1)];
                                        int seaLevel = player.getServerWorld().getSeaLevel();
                                        player.teleport(r, seaLevel + 1, 0);
                                        PlayerProgressionManager.unlockZone(player, zone);
                                        ctx.getSource().sendFeedback(
                                            () -> Text.literal("§aTeleported to zone " + zone + " (~" + (int)r + ", " + seaLevel + ", 0). Zone unlocked."),
                                            false);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int killAllAiShips(net.minecraft.server.MinecraftServer server, Faction factionFilter) {
        List<Long> toKill = new ArrayList<>();
        for (AiShipController.AiShipData data : AiShipController.AI_SHIPS.values()) {
            if (factionFilter == null || data.faction == factionFilter) {
                toKill.add(data.shipId);
            }
        }
        for (long shipId : toKill) AiShipController.despawn(shipId, server);
        return toKill.size();
    }

    private static long getOwnedShipId(ServerPlayerEntity player) {
        Long shipId = ShipRegistryState.get(player.getServer().getOverworld())
                          .getOwnedShip(player.getUuid());
        return shipId != null ? shipId : -1L;
    }
}
