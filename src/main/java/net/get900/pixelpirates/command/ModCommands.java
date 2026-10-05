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

    /** /pptest chronicle [on|off]: reveal every page of the Weathered Chronicle (and hand one over if missing). */
    private static int chronicleToggle(ServerPlayerEntity p, Boolean want) {
        boolean on = want != null ? want : !net.get900.pixelpirates.world.Chronicle.revealAll(p);
        net.get900.pixelpirates.world.Chronicle.setRevealAll(p, on);
        if (on && !p.getInventory().contains(new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.WEATHERED_CHRONICLE)))
            p.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.WEATHERED_CHRONICLE));
        p.sendMessage(Text.literal("[~] Chronicle: " + (on ? "every page revealed" : "only earned pages")).formatted(Formatting.YELLOW), false);
        return 1;
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
        if (net.get900.pixelpirates.world.dungeon.LayoutStructures.exists(type)) {                 // editable: remember it for /ppstruct
            net.get900.pixelpirates.world.dungeon.StructureEditState.get(world.getServer()).add(type, world, origin, rot);
            source.sendFeedback(() -> Text.literal("    Edit it by hand, then /ppstruct save to keep your changes in the " + built + " layout."), false);
        }
        return 1;
    }

    /** /ppstruct - hand-editing layout structures (see LayoutStructures + StructureEditState). */
    private static net.get900.pixelpirates.world.dungeon.StructureEditState.Instance structNear(ServerCommandSource source, String id) {
        var s = net.get900.pixelpirates.world.dungeon.StructureEditState.get(source.getServer());
        var i = s.nearest(source.getWorld(), BlockPos.ofFloored(source.getPosition()), id, 64);
        if (i == null) source.sendError(Text.literal("No editable copy" + (id == null ? "" : " of " + id) + " within 64 blocks - build one with /ppdungeon <id> first."));
        return i;
    }

    private static int structSave(ServerCommandSource source, String id) {
        var i = structNear(source, id);
        if (i == null) return 0;
        var s = net.get900.pixelpirates.world.dungeon.StructureEditState.get(source.getServer());
        try {
            var r = net.get900.pixelpirates.world.dungeon.LayoutStructures.save(source.getWorld(), i.id(), i.origin(), i.rot(), s.beforeIn(i));
            s.forgetEdits(i);
            if (r.files().isEmpty()) { source.sendFeedback(() -> Text.literal("[~] " + i.id() + ": nothing changed - the layout already matches."), false); return 1; }
            source.sendFeedback(() -> Text.literal("[~] Saved " + i.id() + ": " + r.changed() + " blocks changed, " + r.added() + " added"
                    + (r.lootAdded() > 0 ? ", " + r.lootAdded() + " new loot container(s)" : "") + (r.lootLost() > 0 ? ", " + r.lootLost() + " loot container(s) removed" : "")
                    + (r.withData() > 0 ? ", " + r.withData() + " with saved text/data" : "") + ". Every new " + i.id() + " uses it."), true);
            for (var f : r.files()) source.sendFeedback(() -> Text.literal("    -> " + f), false);
            return 1;
        } catch (Exception e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.error("[Layout] save {} failed", i.id(), e);
            source.sendError(Text.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /ppdungeon <id>  — build any dungeon in Dungeons.ALL right here (testing; features have no
            // /locate). Origin = the block under you; the worldgen site checks are skipped. Short aliases
            // grotto/shrine/galleon still work.
            // /ppstruct save [id] | restamp | forget | revert <id> | list - keep hand edits to a /ppdungeon copy in its layout
            dispatcher.register(CommandManager.literal("ppstruct")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("save")
                            .executes(ctx -> structSave(ctx.getSource(), null))
                            .then(CommandManager.argument("id", StringArgumentType.word())
                                    .executes(ctx -> structSave(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                    .then(CommandManager.literal("restamp").executes(ctx -> {
                        var i = structNear(ctx.getSource(), null);
                        if (i == null) return 0;
                        ServerWorld w = ctx.getSource().getWorld();
                        net.get900.pixelpirates.world.dungeon.LayoutStructures.build(new net.get900.pixelpirates.world.dungeon.DungeonBuilder(w, i.origin(), i.rot(), w.random), i.id(), false);
                        net.get900.pixelpirates.world.dungeon.StructureEditState.get(ctx.getSource().getServer()).forgetEdits(i);
                        ctx.getSource().sendFeedback(() -> Text.literal("[~] Re-stamped " + i.id() + " from its layout (no mobs). Unsaved hand edits there are gone."), true);
                        return 1;
                    }))
                    .then(CommandManager.literal("forget").executes(ctx -> {
                        var i = structNear(ctx.getSource(), null);
                        if (i == null) return 0;
                        net.get900.pixelpirates.world.dungeon.StructureEditState.get(ctx.getSource().getServer()).remove(i);
                        ctx.getSource().sendFeedback(() -> Text.literal("[~] No longer tracking the " + i.id() + " copy at " + i.origin().toShortString() + "."), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("revert").then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        try {
                            boolean had = net.get900.pixelpirates.world.dungeon.LayoutStructures.revert(id);
                            ctx.getSource().sendFeedback(() -> Text.literal(had ? "[~] Removed this game's saved copy of " + id + " - the layout shipped with the mod applies again (in the dev workspace, undo the src/main/resources copy with git)."
                                    : "[~] " + id + " has no saved copy in this game folder."), true);
                            return 1;
                        } catch (Exception e) { ctx.getSource().sendError(Text.literal("Revert failed: " + e.getMessage())); return 0; }
                    })))
                    // /ppstruct export <id> - turn a code-built dungeon into a layout file (then switch its Dungeons.ALL builder)
                    .then(CommandManager.literal("export").then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        var t = net.get900.pixelpirates.world.dungeon.Dungeons.byId(id);
                        if (t == null) { ctx.getSource().sendError(Text.literal("Unknown dungeon: " + id)); return 0; }
                        if (net.get900.pixelpirates.world.dungeon.LayoutStructures.exists(id)) { ctx.getSource().sendError(Text.literal(id + " is already a layout - use /ppstruct save.")); return 0; }
                        try {
                            String name = Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
                            var r = net.get900.pixelpirates.world.dungeon.LayoutExport.export(t, 0, name, "Exported from the old Java builder: y=0 = the site's ground surface, entrance toward -Z");
                            ctx.getSource().sendFeedback(() -> Text.literal("[~] Exported " + id + ": " + r.cells() + " cells, " + r.loot() + " loot, " + r.spawners() + " spawners, "
                                    + r.mobs() + " mobs" + (r.clipped() > 0 ? ", " + r.clipped() + " writes beyond +-" + net.get900.pixelpirates.world.dungeon.DungeonBuilder.MAX_REACH + " dropped" : "")), true);
                            for (var f : r.files()) ctx.getSource().sendFeedback(() -> Text.literal("    -> " + f), false);
                            return 1;
                        } catch (Exception e) {
                            net.get900.pixelpirates.PixelPirates.LOGGER.error("[Layout] export {} failed", id, e);
                            ctx.getSource().sendError(Text.literal("Export failed: " + e));
                            return 0;
                        }
                    })))
                    .then(CommandManager.literal("list").executes(ctx -> {
                        var all = net.get900.pixelpirates.world.dungeon.StructureEditState.get(ctx.getSource().getServer()).instances();
                        StringBuilder ids = new StringBuilder();
                        for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL)
                            if (net.get900.pixelpirates.world.dungeon.LayoutStructures.exists(t.id())) ids.append(ids.length() == 0 ? "" : ", ").append(t.id());
                        ctx.getSource().sendFeedback(() -> Text.literal("[~] Editable structures: " + ids), false);
                        if (all.isEmpty()) ctx.getSource().sendFeedback(() -> Text.literal("    No copies built yet - /ppdungeon <id> [rotation 0-3]."), false);
                        for (var i : all) ctx.getSource().sendFeedback(() -> Text.literal("    " + i.id() + " at " + i.origin().toShortString() + " (" + i.dim() + ", rotation " + i.rot().ordinal() + ")"), false);
                        return 1;
                    })));

            dispatcher.register(CommandManager.literal("ppdungeon")
                    .requires(source -> source.hasPermissionLevel(2))
                    // /ppdungeon locate <id>  — nearest predicted site (same grid prediction worldgen uses)
                    // /ppdungeon scan <id> <radius in chunks>  — op diagnostic: why a type finds (no) sites
                    .then(CommandManager.literal("scan").then(CommandManager.argument("id", StringArgumentType.word())
                            .then(CommandManager.argument("r", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 400)).executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                var t = net.get900.pixelpirates.world.dungeon.Dungeons.byId(StringArgumentType.getString(ctx, "id"));
                                if (t == null) { source.sendError(Text.literal("Unknown dungeon")); return 0; }
                                ServerWorld world = source.getWorld();
                                var gen = world.getChunkManager().getChunkGenerator();
                                var pctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(world.getSeed(), gen,
                                        world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
                                BlockPos from = BlockPos.ofFloored(source.getPosition());
                                String s = net.get900.pixelpirates.world.dungeon.DungeonPlacement.siteScan(t, pctx, from.getX() >> 4, from.getZ() >> 4,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "r"));
                                source.sendFeedback(() -> Text.literal("[~] " + s), false);
                                return 1;
                            }))))
                    // /ppdungeon coastscan <radius in chunks>  — op diagnostic for COAST placement tuning
                    .then(CommandManager.literal("coastscan").then(CommandManager.argument("r", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 400))
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                ServerWorld world = source.getWorld();
                                var gen = world.getChunkManager().getChunkGenerator();
                                var pctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(world.getSeed(), gen,
                                        world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
                                BlockPos from = BlockPos.ofFloored(source.getPosition());
                                String s = net.get900.pixelpirates.world.dungeon.DungeonPlacement.coastScan(pctx, from.getX() >> 4, from.getZ() >> 4,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "r"));
                                source.sendFeedback(() -> Text.literal("[~] " + s), false);
                                return 1;
                            })))
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
                        net.get900.pixelpirates.world.Chronicle.setRevealAll(p, true);
                        p.sendMessage(Text.literal("[~] Test kit: god mode ON, clear sight ON, Boss Slayer given, every Chronicle page revealed").formatted(Formatting.GREEN), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("chronicle")
                            .executes(ctx -> chronicleToggle(ctx.getSource().getPlayerOrThrow(), null))
                            .then(CommandManager.literal("on").executes(ctx -> chronicleToggle(ctx.getSource().getPlayerOrThrow(), true)))
                            .then(CommandManager.literal("off").executes(ctx -> chronicleToggle(ctx.getSource().getPlayerOrThrow(), false))))
                    .then(CommandManager.literal("status").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        p.sendMessage(Text.literal("[~] god: " + (net.get900.pixelpirates.world.TestModes.god(p) ? "ON" : "off")
                                + "   clearsight: " + (net.get900.pixelpirates.world.TestModes.clearSight(p) ? "ON" : "off")
                                + "   chronicle: " + (net.get900.pixelpirates.world.Chronicle.revealAll(p) ? "ALL" : "earned")).formatted(Formatting.YELLOW), false);
                        return 1;
                    })));

            // /ppleviathan - THE LEVIATHAN HUNT (world/leviathan). `awaken` is the Rift Seal's confirmation (any player, checked
            // there); the rest are testing: status | locate | build <site> [ruined] | do <op> [arg]
            //   ops: wake, summon, horn, bell <i>, stage <0-7>, flee, arrive, ruin <1|2>, restore <1|2>, tide <n>, drain, bane, kill, reset, act <ability>
            // /pphelmet - hide/show the helmet you are wearing (same as the H key)
            dispatcher.register(CommandManager.literal("pphelmet").executes(ctx -> net.get900.pixelpirates.item.HelmetToggle.toggle(ctx.getSource().getPlayerOrThrow())));

            // /pparmortest - boss sets: measured damage per loadout beside the nearest boss (item/BossArmor#test)
            dispatcher.register(CommandManager.literal("pparmortest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                for (String line : net.get900.pixelpirates.item.BossArmor.test(ctx.getSource().getWorld(), ctx.getSource().getPosition()))
                    ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                return 1;
            }));

            // /ppskills level <n> | learn <key> | reset  (op) - test the pirate skill tree (world/PirateLevelManager)
            dispatcher.register(CommandManager.literal("ppskills").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("level").then(CommandManager.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> {
                                var p = ctx.getSource().getPlayerOrThrow();
                                var comp = (net.get900.pixelpirates.util.PlayerProgressionComponent) p;
                                int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n");
                                int spent = 0;
                                for (int i = 0; i < net.get900.pixelpirates.world.PirateLevelingSystem.ALL_SKILLS.size(); i++) spent += comp.pp_getSkillLevel(i);
                                comp.pp_setPirateLevel(n);
                                comp.pp_setPirateXp(0);
                                comp.pp_setSkillPoints(Math.max(0, net.get900.pixelpirates.world.PirateLevelManager.pointsEarned(n) - spent));
                                net.get900.pixelpirates.world.PirateLevelManager.applyAttributeModifiers(p);
                                net.get900.pixelpirates.world.PirateLevelManager.syncToClient(p);
                                ctx.getSource().sendFeedback(() -> Text.literal("Pirate level " + n + ", " + comp.pp_getSkillPoints() + " points free"), false);
                                return 1;
                            })))
                    .then(CommandManager.literal("learn").then(CommandManager.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> net.get900.pixelpirates.world.PirateLevelManager.spendSkillPoint(ctx.getSource().getPlayerOrThrow(),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key")) ? 1 : 0)))
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        var p = ctx.getSource().getPlayerOrThrow();
                        var comp = (net.get900.pixelpirates.util.PlayerProgressionComponent) p;
                        int spent = 0;
                        for (int i = 0; i < net.get900.pixelpirates.world.PirateLevelingSystem.ALL_SKILLS.size(); i++) { spent += comp.pp_getSkillLevel(i); comp.pp_setSkillLevel(i, 0); }
                        comp.pp_setSkillPoints(comp.pp_getSkillPoints() + spent);
                        net.get900.pixelpirates.world.PirateLevelManager.applyAttributeModifiers(p);
                        net.get900.pixelpirates.world.PirateLevelManager.syncToClient(p);
                        return 1;
                    })));

            // /pptabs - log every creative tab's contents (item/ModCreativeTabs)
            dispatcher.register(CommandManager.literal("pptabs").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                for (String line : net.get900.pixelpirates.item.ModCreativeTabs.dump()) {
                    net.get900.pixelpirates.PixelPirates.LOGGER.info("[tabs] " + line);
                    ctx.getSource().sendFeedback(() -> Text.literal(line.substring(0, Math.min(200, line.length())) + "..."), false);
                }
                return 1;
            }));

            // /ppisland list | restamp <building|number|all> | tp <building|number> - the spawn island overhaul tools: re-stamp a
            // building from PortCityLayout into this world after editing it (labels = PortCityLayout.buildings(), the same
            // names/numbers as tools/previews/spawn/wavebreak_map.png)
            dispatcher.register(CommandManager.literal("ppisland").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("list").executes(ctx -> {
                    int i = 0;
                    for (var e : net.get900.pixelpirates.world.gen.PortCityLayout.buildings().entrySet()) {
                        int[] b = e.getValue();
                        int n = ++i;
                        ctx.getSource().sendFeedback(() -> Text.literal(n + ". " + e.getKey() + "  (x " + b[0] + ".." + b[2] + ", z " + b[1] + ".." + b[3] + ")"), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("restamp").then(CommandManager.argument("building", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(islandNames(), sb))
                    .executes(ctx -> {
                        var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                        if (w == null) return 0;
                        String q = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "building");
                        int[] b = q.equalsIgnoreCase("all") ? new int[]{-200, -200, 200, 200} : islandBox(q);
                        if (b == null) { ctx.getSource().sendError(Text.literal("No building '" + q + "' - see /ppisland list")); return 0; }
                        int n = net.get900.pixelpirates.world.gen.SpawnIslandFeature.restamp(w, b[0] - 3, b[1] - 3, b[2] + 3, b[3] + 3);
                        ctx.getSource().sendFeedback(() -> Text.literal("Restamped " + q + ": " + n + " blocks changed"), true);
                        return 1;
                    })))
                // capture | edits | discard - hand edits made in game, saved over the plan (world/gen/IslandEdits)
                .then(CommandManager.literal("capture").then(CommandManager.argument("building", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(islandNames(), sb))
                    .executes(ctx -> islandEdit(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "building"), "capture"))))
                .then(CommandManager.literal("edits").then(CommandManager.argument("building", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(islandNames(), sb))
                    .executes(ctx -> islandEdit(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "building"), "edits"))))
                .then(CommandManager.literal("discard").then(CommandManager.argument("building", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(islandNames(), sb))
                    .executes(ctx -> islandEdit(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "building"), "discard"))))
                .then(CommandManager.literal("tp").then(CommandManager.argument("building", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(islandNames(), sb))
                    .executes(ctx -> {
                        var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        int[] b = islandBox(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "building"));
                        if (w == null || b == null) { ctx.getSource().sendError(Text.literal("No such building - see /ppisland list")); return 0; }
                        double x = (b[0] + b[2]) / 2.0 + 0.5, z = b[3] + 8.5;     // stand south of it, looking north at it
                        int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
                        p.teleport(w, x, y + 1, z, 180f, 15f);
                        return 1;
                    }))));

            // /ppmarket rebuild|seat - re-stamp the Wavebreak Bazaar (PortCityLayout.marketSquare) into this world and seat
            // every keeper behind his booth (homestead/trade/PortTraders)
            dispatcher.register(CommandManager.literal("ppmarket").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("rebuild").executes(ctx -> {
                    var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                    if (w == null) return 0;
                    int n = net.get900.pixelpirates.world.gen.SpawnIslandFeature.restamp(w, -48, 8, 48, 49);
                    int s = net.get900.pixelpirates.homestead.trade.PortTraders.seatAll(w);
                    ctx.getSource().sendFeedback(() -> Text.literal("Market rebuilt: " + n + " blocks, " + s + " keepers spawned, the rest moved to their booths"), true);
                    return 1;
                }))
                .then(CommandManager.literal("seat").executes(ctx -> {
                    var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                    if (w == null) return 0;
                    int s = net.get900.pixelpirates.homestead.trade.PortTraders.seatAll(w);
                    ctx.getSource().sendFeedback(() -> Text.literal("Keepers seated (" + s + " spawned)"), true);
                    return 1;
                })));

            // /ppharbour status | expire - the harbour dues watch (homestead/harbour/HarbourDues)
            dispatcher.register(CommandManager.literal("ppharbour").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("status").executes(ctx -> {
                    var lines = net.get900.pixelpirates.homestead.harbour.HarbourDues.report(ctx.getSource().getServer());
                    if (lines.isEmpty()) ctx.getSource().sendFeedback(() -> Text.literal("[Harbour] no player ships on the books"), false);
                    for (String l : lines) ctx.getSource().sendFeedback(() -> Text.literal("[Harbour] " + l), false);
                    return 1;
                }))
                .then(CommandManager.literal("expire").executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrThrow();
                    Long ship = net.get900.pixelpirates.world.ShipRegistryState.get(ctx.getSource().getServer().getOverworld()).getOwnedShip(p.getUuid());
                    if (ship == null) return 0;
                    net.get900.pixelpirates.homestead.harbour.HarbourDues.get(ctx.getSource().getServer()).debugExpire(ship);
                    ctx.getSource().sendFeedback(() -> Text.literal("[Harbour] your permit is gone and the grace used up - the watch will chain the helm on its next pass if you lie in the harbour"), false);
                    return 1;
                })));

            // /pptavern liars <pos> <regulars> - a bots-only Liar's Dice game at pos (places a table there if needed);
            // /pptavern status <pos> - the table's state (server soak test for the tavern games)
            dispatcher.register(CommandManager.literal("pptavern").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("liars").then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos())
                    .then(CommandManager.argument("regulars", com.mojang.brigadier.arguments.IntegerArgumentType.integer(2, 6)).executes(ctx -> {
                        var w = ctx.getSource().getWorld();
                        var pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                        if (!(w.getBlockEntity(pos) instanceof net.get900.pixelpirates.homestead.tavern.LiarsDiceBlockEntity))
                            w.setBlockState(pos, net.get900.pixelpirates.homestead.HomesteadBlocks.LIARS_DICE_TABLE.getDefaultState(), 3);
                        if (!(w.getBlockEntity(pos) instanceof net.get900.pixelpirates.homestead.tavern.LiarsDiceBlockEntity be)) return 0;
                        be.debugBots(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "regulars"));
                        ctx.getSource().sendFeedback(() -> Text.literal("[Tavern] " + be.debugStatus()), false);
                        return 1;
                    }))))
                .then(CommandManager.literal("status").then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos()).executes(ctx -> {
                    var be = ctx.getSource().getWorld().getBlockEntity(net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos"));
                    String line = be instanceof net.get900.pixelpirates.homestead.tavern.LiarsDiceBlockEntity l ? l.debugStatus() : "no Liar's Dice table there";
                    ctx.getSource().sendFeedback(() -> Text.literal("[Tavern] " + line), false);
                    return 1;
                }))));

            // /ppweapontest - every relic weapon's attacks from a fake player on a row of zombies (item/RelicWeapons#selfTest)
            dispatcher.register(CommandManager.literal("ppweapontest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                var src = ctx.getSource();
                net.get900.pixelpirates.item.RelicWeapons.selfTest(src.getWorld(), src.getPosition(),
                        line -> src.sendFeedback(() -> Text.literal(line), false));
                return 1;
            }));

            // /ppedittest - a fake player changes three spots on the island, then /ppisland capture all (world/gen/IslandEditSelfTest)
            dispatcher.register(CommandManager.literal("ppedittest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                var src = ctx.getSource();
                var w = src.getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                try { net.get900.pixelpirates.world.gen.IslandEditSelfTest.run(w, line -> src.sendFeedback(() -> Text.literal(line), false)); }
                catch (java.io.IOException e) { src.sendError(Text.literal(e.getMessage())); }
                return 1;
            }));

            // /ppaviary - turn the parrot keeper's aviary stock over now (townhouse #25): 4 new birds drawn by rarity (homestead/parrot/Aviary)
            dispatcher.register(CommandManager.literal("ppaviary").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                var got = w == null ? java.util.List.<net.get900.pixelpirates.homestead.parrot.ParrotTypes.PType>of() : net.get900.pixelpirates.homestead.parrot.Aviary.restock(w);
                StringBuilder sb = new StringBuilder("Aviary restocked:");
                for (var b : got) sb.append(" ").append(b.name()).append(" (").append(b.tier().label).append(")").append(",");
                String msg = got.isEmpty() ? "Aviary restocked: nothing (port world not loaded)" : sb.substring(0, sb.length() - 1);
                ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
                return 1;
            }));

            // /ppcattery - turn the ship's-cat keeper's stock over now (townhouse #36): 4 new cats drawn by rarity (homestead/cat/Cattery)
            // /pplivery check = every livery's block ids exist; /pplivery apply <id|original> = repaint your own ship, free (testing)
            dispatcher.register(CommandManager.literal("pplivery").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("check").executes(ctx -> {
                        int bad = 0;
                        for (var l : net.get900.pixelpirates.world.livery.Livery.ALL) {
                            for (String id : new String[]{l.hull().planks(), l.hull().stairs(), l.hull().slab(), l.hull().fence(), l.deck().planks(), l.deck().slab(),
                                    l.trim(), l.band(), l.band2(), l.gilt(), l.sail(), l.sail2(), l.emblem(), l.light()}) {
                                if (id == null) continue;
                                if (net.minecraft.registry.Registries.BLOCK.get(new net.minecraft.util.Identifier(id)) == net.minecraft.block.Blocks.AIR) {
                                    bad++;
                                    String msg = "[livery] " + l.id() + ": unknown block " + id;
                                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                                }
                            }
                        }
                        int b = bad;
                        ctx.getSource().sendFeedback(() -> Text.literal("[livery] " + net.get900.pixelpirates.world.livery.Livery.ALL.size() + " liveries, " + b + " unknown block id(s)"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("apply").then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        Long ship = ShipRegistryState.get(p.getServer().getOverworld()).getOwnedShip(p.getUuid());
                        if (ship == null) { ctx.getSource().sendError(Text.literal("You own no ship.")); return 0; }
                        String id = StringArgumentType.getString(ctx, "id");
                        var vs = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(p.getServerWorld()).getLoadedShips().getById(ship);
                        if (vs == null) { ctx.getSource().sendError(Text.literal("Your ship is not loaded.")); return 0; }
                        var l = id.equals("original") ? null : net.get900.pixelpirates.world.livery.Livery.byId(id);
                        if (l == null && !id.equals("original")) { ctx.getSource().sendError(Text.literal("Unknown livery " + id)); return 0; }
                        int n = net.get900.pixelpirates.world.livery.Liveries.repaint(p.getServerWorld(), vs,
                                net.get900.pixelpirates.world.livery.LiveryState.get(p.getServer()), l);
                        ctx.getSource().sendFeedback(() -> Text.literal("[livery] " + id + ": " + n + " blocks repainted"), false);
                        return 1;
                    })))
                    .then(CommandManager.literal("spawn").then(CommandManager.argument("blueprint", StringArgumentType.word()).executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        String name = StringArgumentType.getString(ctx, "blueprint");
                        var look = p.getRotationVec(1.0f);
                        BlockPos origin = BlockPos.ofFloored(p.getX() + look.x * 30, 75, p.getZ() + look.z * 30);
                        try {
                            var ship = net.get900.pixelpirates.world.ShipSpawner.spawn(p.getServerWorld(), ShipSchematic.load(name), origin);
                            var reg = ShipRegistryState.get(p.getServer().getOverworld());
                            reg.clearOwnership(p.getUuid());
                            reg.saveMastCount(ship.getId(), net.get900.pixelpirates.world.ShipSteeringManager.MAST_COUNTS.getOrDefault(ship.getId(), 1));
                            reg.setOwnership(p.getUuid(), ship.getId());
                            ctx.getSource().sendFeedback(() -> Text.literal("[livery] spawned your " + name + " (" + ship.getId() + ")"), false);
                            return 1;
                        } catch (Exception e) {
                            ctx.getSource().sendError(Text.literal("Spawn failed: " + e.getMessage()));
                            return 0;
                        }
                    })))
                    .then(CommandManager.literal("near").then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerWorld w = src.getWorld();
                        org.valkyrienskies.core.api.ships.ServerShip best = null; double bd = 200 * 200;
                        for (var s : org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(w).getLoadedShips()) {
                            var q = s.getTransform().getPositionInWorld();
                            double d = src.getPosition().squaredDistanceTo(q.x(), q.y(), q.z());
                            if (d < bd) { bd = d; best = s; }
                        }
                        if (best == null) { src.sendError(Text.literal("No ship within 200 blocks.")); return 0; }
                        String id = StringArgumentType.getString(ctx, "id");
                        var l = id.equals("original") ? null : net.get900.pixelpirates.world.livery.Livery.byId(id);
                        if (l == null && !id.equals("original")) { src.sendError(Text.literal("Unknown livery " + id)); return 0; }
                        int n = net.get900.pixelpirates.world.livery.Liveries.repaint(w, best, net.get900.pixelpirates.world.livery.LiveryState.get(w.getServer()), l);
                        src.sendFeedback(() -> Text.literal("[livery] " + id + ": " + n + " blocks repainted"), false);
                        return 1;
                    }))));

            // /ppchess <pos> demo: the computer plays itself on that board (testing - homestead/chess)
            dispatcher.register(CommandManager.literal("ppchess").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos())
                            .then(CommandManager.literal("demo").executes(ctx -> {
                                BlockPos pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                boolean ok = net.get900.pixelpirates.homestead.chess.Chess.demo(ctx.getSource().getWorld(), pos);
                                ctx.getSource().sendFeedback(() -> Text.literal(ok ? "The computer plays itself." : "No chess board there."), false);
                                return ok ? 1 : 0;
                            }))
                            .then(CommandManager.literal("open").executes(ctx -> {
                                BlockPos pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                if (!(ctx.getSource().getWorld().getBlockEntity(pos) instanceof net.get900.pixelpirates.homestead.chess.ChessBoardEntity be)) return 0;
                                net.get900.pixelpirates.homestead.chess.Chess.open(ctx.getSource().getPlayerOrThrow(), be);
                                return 1;
                            }))));

            // /pptelescope <pos>: look through the telescope there (testing - homestead/nav/Telescopes)
            dispatcher.register(CommandManager.literal("pptelescope").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos()).executes(ctx -> {
                        BlockPos pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        if (!(p.getServerWorld().getBlockState(pos).getBlock() instanceof net.get900.pixelpirates.homestead.furniture.TelescopeBlock)) {
                            ctx.getSource().sendError(Text.literal("No telescope there.")); return 0;
                        }
                        net.get900.pixelpirates.homestead.nav.Telescopes.start(p, pos);
                        return 1;
                    })));

            // /ppswing <pos>: sit on the swing there (testing - homestead/swing)
            dispatcher.register(CommandManager.literal("ppswing").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos()).executes(ctx -> {
                        BlockPos pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        var st = p.getServerWorld().getBlockState(pos);
                        if (st.getBlock() instanceof net.get900.pixelpirates.homestead.swing.SwingBlock && st.get(net.get900.pixelpirates.homestead.TallFurniture.HALF)
                                == net.minecraft.block.enums.DoubleBlockHalf.UPPER) pos = pos.down();
                        boolean ok = net.get900.pixelpirates.homestead.swing.SwingSeatEntity.sit(p.getServerWorld(), pos, p);
                        ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Swinging." : "No free swing there."), false);
                        return ok ? 1 : 0;
                    })));

            // /ppbeard <style|none> [colour] | /ppbeard grow <days> (testing facial hair - homestead/beard)
            dispatcher.register(CommandManager.literal("ppbeard").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("grow").then(CommandManager.argument("days", IntegerArgumentType.integer(1, 60)).executes(ctx -> {
                        String m = net.get900.pixelpirates.homestead.beard.Beards.debug(ctx.getSource().getPlayerOrThrow(), "", null, IntegerArgumentType.getInteger(ctx, "days"));
                        ctx.getSource().sendFeedback(() -> Text.literal(m), false); return 1; })))
                    .then(CommandManager.argument("style", StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(java.util.stream.Stream.concat(java.util.stream.Stream.of("none"),
                                    net.get900.pixelpirates.homestead.beard.Beards.STYLES.stream().map(s -> s.id())), b))
                            .executes(ctx -> {
                                String m = net.get900.pixelpirates.homestead.beard.Beards.debug(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "style"), null, 0);
                                ctx.getSource().sendFeedback(() -> Text.literal(m), false); return 1; })
                            .then(CommandManager.argument("colour", StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(net.get900.pixelpirates.homestead.beard.Beards.COLOURS, b))
                                    .executes(ctx -> {
                                        String m = net.get900.pixelpirates.homestead.beard.Beards.debug(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "style"),
                                                StringArgumentType.getString(ctx, "colour"), 0);
                                        ctx.getSource().sendFeedback(() -> Text.literal(m), false); return 1; }))));

            dispatcher.register(CommandManager.literal("ppcattery").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                var w = ctx.getSource().getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
                var got = w == null ? java.util.List.<net.get900.pixelpirates.homestead.cat.CatCoats.Coat>of() : net.get900.pixelpirates.homestead.cat.Cattery.restock(w);
                StringBuilder sb = new StringBuilder("Cattery restocked:");
                for (var c : got) sb.append(" ").append(c.name()).append(" (").append(c.tier().label).append("),");
                String msg = got.isEmpty() ? "Cattery restocked: nothing (port world not loaded)" : sb.substring(0, sb.length() - 1);
                ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
                return 1;
            }));

            // /ppcat <coat> - a tame cat in that coat at your feet (op; ship's cats test, homestead/cat/CatCoats)
            dispatcher.register(CommandManager.literal("ppcat").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("coat", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> { for (var t : net.get900.pixelpirates.homestead.cat.CatCoats.ALL) b.suggest(t.id()); return b.buildFuture(); })
                            .executes(ctx -> {
                                var t = net.get900.pixelpirates.homestead.cat.CatCoats.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "coat"));
                                var pl = ctx.getSource().getPlayerOrThrow();
                                if (t == null) { ctx.getSource().sendError(Text.literal("No such cat coat")); return 0; }
                                var c = net.minecraft.entity.EntityType.CAT.create(pl.getServerWorld());
                                if (c == null) return 0;
                                c.refreshPositionAndAngles(pl.getX(), pl.getY(), pl.getZ(), pl.getYaw(), 0f);
                                net.get900.pixelpirates.homestead.cat.CatCoats.apply(c, t);
                                c.setOwner(pl);
                                c.setPersistent();
                                pl.getServerWorld().spawnEntity(c);
                                ctx.getSource().sendFeedback(() -> Text.literal("[~] A " + t.name() + " (" + t.tier().label + ") - your ship's cat"), false);
                                return 1;
                            })));

            // /ppparrot <type> - a tame parrot of that type at your feet (op; parrot types phase 1 test, homestead/parrot/ParrotTypes)
            dispatcher.register(CommandManager.literal("ppparrot").requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> { for (var t : net.get900.pixelpirates.homestead.parrot.ParrotTypes.ALL) b.suggest(t.id()); return b.buildFuture(); })
                            .executes(ctx -> {
                                var t = net.get900.pixelpirates.homestead.parrot.ParrotTypes.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "type"));
                                var pl = ctx.getSource().getPlayerOrThrow();
                                if (t == null) { ctx.getSource().sendError(Text.literal("No such parrot type")); return 0; }
                                var p = net.minecraft.entity.EntityType.PARROT.create(pl.getServerWorld());
                                if (p == null) return 0;
                                p.refreshPositionAndAngles(pl.getX(), pl.getY(), pl.getZ(), pl.getYaw(), 0f);
                                net.get900.pixelpirates.homestead.parrot.ParrotTypes.apply(p, t);
                                p.setOwner(pl);
                                p.setPersistent();
                                pl.getServerWorld().spawnEntity(p);
                                ctx.getSource().sendFeedback(() -> Text.literal("Here's a " + t.name() + " (" + t.tier().label + ")"), false);
                                return 1;
                            })));

            // /ppforgetest - a fake player runs every forging pattern, a mending and each forged weapon's ability (homestead/forge/ForgeSelfTest)
            dispatcher.register(CommandManager.literal("ppforgetest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                var src = ctx.getSource();
                net.get900.pixelpirates.homestead.forge.ForgeSelfTest.run(src.getWorld(), net.minecraft.util.math.BlockPos.ofFloored(src.getPosition()),
                        line -> src.sendFeedback(() -> Text.literal(line), false));
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

            // ── /ppship place <blueprint> - stamp a blueprint UNASSEMBLED beside you (to look at, edit, re-save) ──
            // Placed exactly as stored: helm at the origin, bow toward +Z (south), no rotation - so sneak-clicking its
            // helm with a named Ship Blueprint saves it back the same way round.
            // /ppwar - the balance of power in the war at sea (world/faction/SeaWar)
            dispatcher.register(CommandManager.literal("ppwar").executes(ctx -> {
                String r = net.get900.pixelpirates.world.faction.SeaWar.report(ctx.getSource().getServer());
                ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                return 1;
            }));

            dispatcher.register(CommandManager.literal("ppship")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("place")
                            .then(CommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                            net.get900.pixelpirates.world.ShipSchematic.listNames(), b))
                                    .executes(ctx -> {
                                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                        net.get900.pixelpirates.world.ShipSchematic sc;
                                        try { sc = net.get900.pixelpirates.world.ShipSchematic.load(name); }
                                        catch (Exception e) { ctx.getSource().sendError(Text.literal(e.getMessage())); return 0; }
                                        net.minecraft.util.math.BlockPos helm = p.getBlockPos().add(12, 0, 0);
                                        for (var en : sc.getEntries())
                                            p.getServerWorld().setBlockState(helm.add(en.relPos()),
                                                    net.get900.pixelpirates.world.ShipSchematic.restoreState(en.stateNbt()), 3);
                                        net.get900.pixelpirates.world.ShipCapture.recordPlace(p.getServerWorld(), name, helm, sc);
                                        ctx.getSource().sendFeedback(() -> Text.literal("[~] Placed " + name + " with its helm at "
                                                + helm.toShortString() + " (bow facing south / +Z). Edit it, then /ppship capture " + name
                                                + " to save it (or sneak-click the helm to assemble).").formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("drain").executes(ctx -> {
                        String r = net.get900.pixelpirates.world.ShipCapture.drain(ctx.getSource().getPlayerOrThrow());
                        ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                            net.get900.pixelpirates.world.ShipSchematic.listNames(), b))
                                    .executes(ctx -> {
                                        String msg = net.get900.pixelpirates.world.ShipCapture.remove(ctx.getSource().getPlayerOrThrow(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name"));
                                        ctx.getSource().sendFeedback(() -> Text.literal("[~] " + msg).formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
                    // /ppship capture <name>: save the placed (unassembled) ship back as a blueprint - see world/ShipCapture
                    .then(CommandManager.literal("capture")
                            .then(CommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                            net.get900.pixelpirates.world.ShipSchematic.listNames(), b))
                                    .executes(ctx -> {
                                        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                        try {
                                            String msg = net.get900.pixelpirates.world.ShipCapture.capture(ctx.getSource().getPlayerOrThrow(), name);
                                            ctx.getSource().sendFeedback(() -> Text.literal("[~] " + msg).formatted(Formatting.GREEN), false);
                                            return 1;
                                        } catch (java.io.IOException e) {
                                            ctx.getSource().sendError(Text.literal("Capture failed: " + e.getMessage()));
                                            return 0;
                                        }
                                    }))));

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

            // ── /ppship upgrade <key> <level> - set a Shipwright refit on your ship (testing) ──
            dispatcher.register(CommandManager.literal("ppship")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("upgrade")
                            .then(CommandManager.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(
                                            net.get900.pixelpirates.world.ShipUpgrades.ALL.stream().map(net.get900.pixelpirates.world.ShipUpgrades.Def::key), b))
                                    .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 10))
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                                long shipId = getOwnedShipId(player);
                                                if (shipId < 0) { ctx.getSource().sendError(Text.literal("You don't own a ship.")); return 0; }
                                                String key = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key");
                                                var def = net.get900.pixelpirates.world.ShipUpgrades.find(key);
                                                if (def == null) { ctx.getSource().sendError(Text.literal("Unknown refit " + key)); return 0; }
                                                int level = Math.min(def.maxLevel(), IntegerArgumentType.getInteger(ctx, "level"));
                                                ShipRegistryState.get(player.getServer().getOverworld()).setUpgradeLevel(shipId, key, level);
                                                ctx.getSource().sendFeedback(() -> Text.literal("§a" + def.displayName() + " set to " + level + "."), false);
                                                return 1;
                                            })))));

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

    /** /ppisland capture|edits|discard <building | grounds | all>: save, list or drop the hand edits (world/gen/IslandEdits). */
    private static int islandEdit(net.minecraft.server.command.ServerCommandSource src, String q, String what) {
        var w = src.getServer().getWorld(net.get900.pixelpirates.homestead.trade.PortTraders.DIM);
        String G = net.get900.pixelpirates.world.gen.IslandEdits.GROUNDS;
        boolean all = q.trim().equalsIgnoreCase("all");
        String name = all ? "all" : q.trim().equalsIgnoreCase("grounds") || q.trim().equalsIgnoreCase("streets") || q.trim().equalsIgnoreCase(G) ? G : islandName(q);
        if (w == null || name == null) { src.sendError(Text.literal("No building '" + q + "' - see /ppisland list (or use grounds / all)")); return 0; }
        try {
            switch (what) {
                case "capture" -> {
                    var r = all ? net.get900.pixelpirates.world.gen.IslandEdits.captureAll(w) : net.get900.pixelpirates.world.gen.IslandEdits.capture(w, name);
                    src.sendFeedback(() -> Text.literal("Captured " + (all ? "your changes (" + r.spots() + " touched spots checked)" : name) + ": "
                            + r.edits() + " hand edit(s) saved" + (all ? " across " + r.sections() + " section(s)" : "")
                            + (r.withData() > 0 ? " (" + r.withData() + " with sign/banner/contents data)" : "")
                            + ". They now survive restamps and new worlds."), true);
                }
                case "discard" -> {
                    int n = all ? net.get900.pixelpirates.world.gen.IslandEdits.discardAll(w) : net.get900.pixelpirates.world.gen.IslandEdits.discard(w, name);
                    src.sendFeedback(() -> Text.literal("Dropped " + n + " hand edit(s) from " + (all ? "the whole island" : name)
                            + " - /ppisland restamp " + q.trim() + " puts the plan back."), true);
                }
                default -> {
                    var edits = net.get900.pixelpirates.world.gen.PortCityLayout.edits();
                    if (all) {
                        int total = 0;
                        for (var e : edits.entrySet()) {
                            total += e.getValue().size();
                            src.sendFeedback(() -> Text.literal("  " + e.getKey() + ": " + e.getValue().size()).formatted(net.minecraft.util.Formatting.GRAY), false);
                        }
                        int t = total;
                        src.sendFeedback(() -> Text.literal(t + " hand edit(s) saved in " + edits.size() + " section(s)"), false);
                        return 1;
                    }
                    var list = edits.getOrDefault(name, java.util.List.of());
                    src.sendFeedback(() -> Text.literal(name + ": " + list.size() + " hand edit(s) saved"), false);
                    for (int i = 0; i < Math.min(10, list.size()); i++) {
                        var e = list.get(i);
                        src.sendFeedback(() -> Text.literal("  " + e.x() + " " + e.y() + " " + e.z() + "  " + e.state()).formatted(net.minecraft.util.Formatting.GRAY), false);
                    }
                    if (list.size() > 10) src.sendFeedback(() -> Text.literal("  ...").formatted(net.minecraft.util.Formatting.GRAY), false);
                }
            }
        } catch (java.io.IOException e) {
            src.sendError(Text.literal("Could not write the edits file: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    /** The full label name for a building query (name, prefix or map number). */
    private static String islandName(String q) {
        int[] b = islandBox(q);
        if (b == null) return null;
        for (var e : net.get900.pixelpirates.world.gen.PortCityLayout.buildings().entrySet()) if (e.getValue() == b) return e.getKey();
        return null;
    }

    private static java.util.List<String> islandNames() {
        java.util.List<String> out = new java.util.ArrayList<>(net.get900.pixelpirates.world.gen.PortCityLayout.buildings().keySet());
        out.add("all");
        out.add("grounds");
        return out;
    }

    /** A building's box by name (case-insensitive, prefix ok) or by its map number. */
    private static int[] islandBox(String q) {
        var all = net.get900.pixelpirates.world.gen.PortCityLayout.buildings();
        try {
            int n = Integer.parseInt(q.trim());
            int i = 0;
            for (int[] b : all.values()) if (++i == n) return b;
            return null;
        } catch (NumberFormatException ignored) {}
        for (var e : all.entrySet()) if (e.getKey().equalsIgnoreCase(q.trim())) return e.getValue();
        for (var e : all.entrySet()) if (e.getKey().toLowerCase().startsWith(q.trim().toLowerCase())) return e.getValue();
        return null;
    }
}
