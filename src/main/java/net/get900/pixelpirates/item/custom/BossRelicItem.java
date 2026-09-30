package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.get900.pixelpirates.world.dungeon.DungeonPlacement;
import net.get900.pixelpirates.world.dungeon.Dungeons;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Boss relics - awarded by each chain boss (BossProgression.CHAIN), each one built to counter the
 * NEXT boss or the region it lives in. Passives work from anywhere in the inventory; right-click
 * points to the nearest lair of the boss the relic was made for (same grid prediction as
 * /ppdungeon locate).
 *
 * Passives are applied here (inventoryTick) or, when they change combat maths, by hooks that check
 * {@link BossProgression#has}: Abilities.pull (TIDE_SIGIL), Abilities.grab (RAZOR_TOOTH),
 * ModMob.damage (TIDE_PEARL / RAZOR_TOOTH bonus damage), ModBoss (SHACKLE), ZoneHazardManager
 * (TIDE_PEARL = heat amulet, ANCHOR = sanity amulet).
 */
public class BossRelicItem extends Item implements net.minecraft.item.Equipment {
    public enum Kind {
        /** Rackham -> Sea Serpent: fight underwater. */
        DIVING_CHARM,
        /** Sea Serpent -> Molten Warlord: scalding-water immunity, +50% damage to water-hating mobs. */
        TIDE_PEARL,
        /** Molten Warlord -> Ghost Captain: no Darkness/Blindness, undead within 24 blocks glow. */
        LANTERN,
        /** Ghost Captain -> Abyssal King: no madness, Conduit Power underwater, no Mining Fatigue. */
        ANCHOR,
        /** Abyssal King -> Bloodfin: immune to pulls/whirlpools, no Slowness, Dolphin's Grace. */
        TIDE_SIGIL,
        /** Bloodfin -> Kraken: grabs can't hold you, +50% damage to rooted mobs (tentacles, Kraken). */
        RAZOR_TOOTH,
        /** Kraken -> Chained Revenant: Wither immunity. */
        INK_HEART,
        /** Chained Revenant -> Abyssal Heart: your hits stop bosses healing for 8 s; no Poison. */
        SHACKLE,
        /** Abyssal Heart -> Leviathan: opens the rift; no Weakness, Resistance I in water. Points at the Leviathan during the hunt. */
        HEARTSTONE,
        /** The Leviathan: the sea is yours - water breathing, Dolphin's Grace, Conduit Power and night vision underwater. */
        CROWN
    }

    private static final int LOCATE_COOLDOWN = 100;
    private final Kind kind;

    public BossRelicItem(Kind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    /** The Crown of the Drowned goes on your head (its powers work from any slot); the other relics are held. */
    @Override
    public net.minecraft.entity.EquipmentSlot getSlotType() { return kind == Kind.CROWN ? net.minecraft.entity.EquipmentSlot.HEAD : net.minecraft.entity.EquipmentSlot.MAINHAND; }

    @Override
    public net.minecraft.sound.SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_GOLD; }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity p) || p.age % 10 != 0) return;
        boolean wet = p.isSubmergedInWater();
        if (BossProgression.relicsSilenced(p)) {          // the Abyssal Heart drowns them out: only the charm's breath remains
            if (kind == Kind.DIVING_CHARM && p.isTouchingWater()) give(p, StatusEffects.WATER_BREATHING, 0);
            return;
        }
        switch (kind) {
            case DIVING_CHARM -> {
                if (p.isTouchingWater()) {
                    give(p, StatusEffects.WATER_BREATHING, 0);
                    give(p, StatusEffects.DOLPHINS_GRACE, 0);
                }
            }
            case TIDE_PEARL -> { /* passive lives in ZoneHazardManager + ModMob.damage */ }
            case LANTERN -> {
                cleanse(p, StatusEffects.DARKNESS, StatusEffects.BLINDNESS);
                if (p.age % 20 == 0) {
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(24),
                            e -> e.isAlive() && (e.getGroup() == EntityGroup.UNDEAD || e instanceof ModMob m && m.spec().phase == 4))) {
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
                    }
                }
            }
            case ANCHOR -> {
                cleanse(p, StatusEffects.MINING_FATIGUE);
                if (wet) give(p, StatusEffects.CONDUIT_POWER, 0);
            }
            case TIDE_SIGIL -> {
                cleanse(p, StatusEffects.SLOWNESS);
                if (p.isTouchingWater()) give(p, StatusEffects.DOLPHINS_GRACE, 0);
            }
            case RAZOR_TOOTH -> { /* passive lives in Abilities.grab + ModMob.damage */ }
            case INK_HEART -> cleanse(p, StatusEffects.WITHER);
            case SHACKLE -> cleanse(p, StatusEffects.POISON);
            case HEARTSTONE -> {
                cleanse(p, StatusEffects.WEAKNESS);
                if (wet) give(p, StatusEffects.RESISTANCE, 0);
            }
            case CROWN -> {
                if (p.isTouchingWater()) { give(p, StatusEffects.WATER_BREATHING, 0); give(p, StatusEffects.DOLPHINS_GRACE, 0); }
                if (wet) { give(p, StatusEffects.CONDUIT_POWER, 0); give(p, StatusEffects.NIGHT_VISION, 0); }
            }
        }
    }

    /** Short ambient effect, refreshed every half second while the condition holds. */
    private static void give(PlayerEntity p, StatusEffect e, int amp) {
        StatusEffectInstance cur = p.getStatusEffect(e);
        if (cur == null || cur.getDuration() < 30) p.addStatusEffect(new StatusEffectInstance(e, 60, amp, true, false, true));
    }

    private static void cleanse(PlayerEntity p, StatusEffect... effects) {
        for (StatusEffect e : effects) if (p.hasStatusEffect(e)) p.removeStatusEffect(e);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (kind == Kind.CROWN) return equipAndSwap(this, world, user, hand);          // the Crown is worn
        if (!(world instanceof ServerWorld sw)) return TypedActionResult.success(stack, true);
        if (kind == Kind.HEARTSTONE) {                                      // THE HUNT: it pulls toward the Leviathan itself
            var ls = net.get900.pixelpirates.world.leviathan.LeviathanState.get(sw);
            if (ls.stage != net.get900.pixelpirates.world.leviathan.LeviathanState.SLEEPING) {
                user.getItemCooldownManager().set(this, LOCATE_COOLDOWN);
                net.minecraft.util.math.Vec3d at = net.get900.pixelpirates.world.leviathan.LeviathanHunt.whereIsIt(sw);
                if (at == null) { user.sendMessage(Text.literal("The Heartstone is quiet. It is over.").formatted(Formatting.GRAY), true); return TypedActionResult.success(stack); }
                int dx = (int) (at.x - user.getX()), dz = (int) (at.z - user.getZ());
                int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
                user.sendMessage(Text.literal("The Heartstone pulls " + compass(dx, dz) + " - toward the Leviathan (" + dist + " blocks, "
                        + net.get900.pixelpirates.world.leviathan.LeviathanState.STAGE_NAMES[ls.stage] + ")").formatted(Formatting.DARK_AQUA), true);
                world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                return TypedActionResult.success(stack);
            }
        }
        BossProgression.Step target = BossProgression.targetOf(this);
        var type = target == null ? null : Dungeons.byId(target.lair());
        user.getItemCooldownManager().set(this, LOCATE_COOLDOWN);
        if (type == null) return TypedActionResult.pass(stack);
        var gen = sw.getChunkManager().getChunkGenerator();
        var ctx = new DungeonPlacement.Context(sw.getSeed(), gen, sw.getChunkManager().getNoiseConfig(), sw, gen.getSeaLevel());
        BlockPos from = user.getBlockPos();
        BlockPos hit = DungeonPlacement.locate(type, ctx, from, 12);
        if (hit == null) {
            user.sendMessage(Text.literal("The relic is cold here - " + target.name() + " does not dwell in these waters.")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }
        int dx = hit.getX() - from.getX(), dz = hit.getZ() - from.getZ();
        int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
        user.sendMessage(Text.literal("[~] " + Character.toUpperCase(target.name().charAt(0)) + target.name().substring(1)
                + " waits " + dist + " blocks " + compass(dx, dz) + " (" + hit.getX() + ", " + hit.getZ() + ")")
                .formatted(Formatting.AQUA), false);
        world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.7f);
        return TypedActionResult.success(stack);
    }

    private static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) return "beneath you";
        String[] dirs = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double a = Math.toDegrees(Math.atan2(dz, dx));            // 0 = +X (east), 90 = +Z (south)
        return dirs[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String key = "tooltip.pixelpirates." + kind.name().toLowerCase();
        tooltip.add(Text.translatable(key).formatted(Formatting.AQUA));
        BossProgression.Step target = BossProgression.targetOf(this);
        if (target != null) tooltip.add(Text.literal("Right-click: find " + target.name()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(key + ".lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
