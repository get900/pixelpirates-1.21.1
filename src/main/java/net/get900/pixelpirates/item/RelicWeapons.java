package net.get900.pixelpirates.item;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.item.custom.BossRelicItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * RELIC WEAPONS (2026-09-30, design: D:\Minecraft Modding\Relic Weapons - Design and Art Brief.txt): each boss relic is
 * forged into that boss's weapon (item/relic). A relic weapon anywhere in your inventory gives its relic's passive
 * ({@link BossProgression#relicActive} counts it as the relic), shift + right-click points to the next lair like the
 * relic, and it has its own ability. Shared runtime here: the relic mapping, a tick scheduler for multi-tick abilities,
 * BLEED (Bloodfin's Maw) and FRENZY windows, and a few reusable effects (lanes of fire, a wave, pull/push).
 */
public final class RelicWeapons {
    private RelicWeapons() {}

    // ------------------------------------------------------------------ relic <-> weapon
    private static final Map<Item, Item> RELIC_TO_WEAPON = new HashMap<>();
    private static final Map<Item, Supplier<Item>> WEAPON_TO_RELIC = new HashMap<>();

    public static void link(Item weapon, Supplier<Item> relic) {
        WEAPON_TO_RELIC.put(weapon, relic);
    }

    /** The weapon forged from this relic, or null. */
    public static Item weaponOf(Item relic) {
        if (RELIC_TO_WEAPON.isEmpty()) WEAPON_TO_RELIC.forEach((w, r) -> RELIC_TO_WEAPON.put(r.get(), w));
        return RELIC_TO_WEAPON.get(relic);
    }

    public static BossRelicItem relicOf(Item weapon) {
        Supplier<Item> r = WEAPON_TO_RELIC.get(weapon);
        return r != null && r.get() instanceof BossRelicItem b ? b : null;
    }

    /** inventoryTick of every relic weapon: its relic's passive, twice a second, from any slot. */
    public static void passive(Item weapon, World world, Entity entity) {
        if (world.isClient || !(entity instanceof PlayerEntity p) || p.age % 10 != 0) return;
        BossRelicItem relic = relicOf(weapon);
        if (relic == null || BossProgression.has(p, relic)) return;                // the relic itself already does it
        BossRelicItem.passive(relic.kind(), p, world);
    }

    /** Shift + right-click: point to the next lair (true = handled, the caller returns). */
    public static boolean locate(Item weapon, World world, PlayerEntity p) {
        if (!p.isSneaking()) return false;
        if (world instanceof ServerWorld sw) {
            BossRelicItem relic = relicOf(weapon);
            if (relic != null) BossRelicItem.locate(relic, sw, p);
        }
        p.getItemCooldownManager().set(weapon, 40);
        return true;
    }

    // ------------------------------------------------------------------ scheduler (multi-tick abilities)
    private record Task(ServerWorld world, int[] t, int length, Consumer<Integer> step) {}
    private static final List<Task> TASKS = new ArrayList<>();

    /** Run `step(t)` for t = 0 .. length-1, one per server tick. */
    public static void run(ServerWorld world, int length, Consumer<Integer> step) {
        synchronized (TASKS) { TASKS.add(new Task(world, new int[]{0}, length, step)); }
    }

    public static void later(ServerWorld world, int delay, Runnable r) {
        run(world, delay + 1, t -> { if (t == delay) r.run(); });
    }

    // ------------------------------------------------------------------ bleed + frenzy
    private record Bleed(LivingEntity target, LivingEntity source, int[] left) {}
    private static final List<Bleed> BLEEDS = new ArrayList<>();
    private static final Map<UUID, Long> FRENZY = new HashMap<>();

    public static void bleed(LivingEntity target, LivingEntity source, int ticks) {
        synchronized (BLEEDS) {
            for (Bleed b : BLEEDS) if (b.target() == target) { b.left()[0] = Math.max(b.left()[0], ticks); return; }
            BLEEDS.add(new Bleed(target, source, new int[]{ticks}));
        }
    }

    public static boolean bleeding(LivingEntity e) {
        synchronized (BLEEDS) { for (Bleed b : BLEEDS) if (b.target() == e) return true; }
        return false;
    }

    public static void frenzy(PlayerEntity p, int ticks) { FRENZY.put(p.getUuid(), p.getWorld().getTime() + ticks); }

    public static boolean inFrenzy(PlayerEntity p) {
        Long t = FRENZY.get(p.getUuid());
        return t != null && p.getWorld().getTime() < t;
    }

    public static void tick(MinecraftServer server) {
        synchronized (TASKS) {
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task k = it.next();
                try { k.step().accept(k.t()[0]); } catch (Exception e) { net.get900.pixelpirates.PixelPirates.LOGGER.warn("[RelicWeapons] ability step failed", e); it.remove(); continue; }
                if (++k.t()[0] >= k.length()) it.remove();
            }
        }
        synchronized (BLEEDS) {
            BLEEDS.removeIf(b -> {
                if (!b.target().isAlive() || --b.left()[0] <= 0) return true;
                if (b.left()[0] % 20 == 0) {
                    b.target().timeUntilRegen = 0;
                    b.target().damage(b.target().getDamageSources().magic(), 1.5f);
                    if (b.target().getWorld() instanceof ServerWorld sw)
                        sw.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.7f, 0.05f, 0.05f), 1.2f),
                                b.target().getX(), b.target().getBodyY(0.6), b.target().getZ(), 6, 0.3, 0.3, 0.3, 0);
                }
                return false;
            });
        }
    }

    // ------------------------------------------------------------------ shared effects
    /** Every living thing (not the user, not their pets/other players in creative) within `r` of `at`. */
    public static List<LivingEntity> around(ServerWorld w, Vec3d at, double r, LivingEntity except) {
        return w.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(r), e -> e.isAlive() && e != except
                && !(e instanceof PlayerEntity pp && (pp.isCreative() || pp.isSpectator())) && e.getPos().distanceTo(at) <= r);
    }

    public static void hit(LivingEntity target, LivingEntity by, float dmg) {
        target.timeUntilRegen = 0;
        target.damage(by instanceof PlayerEntity p ? by.getDamageSources().playerAttack(p) : by.getDamageSources().mobAttack(by), dmg);
    }

    public static void knock(LivingEntity e, Vec3d dir, double h, double up) {
        Vec3d d = new Vec3d(dir.x, 0, dir.z);
        if (d.lengthSquared() < 1e-6) d = new Vec3d(0, 0, 1);
        d = d.normalize().multiply(h);
        e.setVelocity(d.x, up, d.z);
        e.velocityModified = true;
    }

    /** A lane of effect racing forward from `from` along `dir` for `len` blocks over `ticks`, hitting each target once. */
    public static void lane(ServerWorld w, LivingEntity user, Vec3d from, Vec3d dir, double len, double width, int ticks,
                            ParticleEffect fx, Consumer<LivingEntity> onHit) {
        Vec3d d = new Vec3d(dir.x, 0, dir.z).normalize();
        java.util.Set<UUID> done = new java.util.HashSet<>();
        run(w, ticks, t -> {
            double a = len * t / ticks, b = len * (t + 1) / ticks;
            for (double s = a; s < b; s += 0.5) {
                Vec3d q = from.add(d.multiply(s));
                w.spawnParticles(fx, q.x, q.y + 0.2, q.z, 3, width * 0.3, 0.1, width * 0.3, 0.02);
                for (LivingEntity e : around(w, q, width, user))
                    if (done.add(e.getUuid())) onHit.accept(e);
            }
        });
    }

    public static void sound(World w, Entity at, net.minecraft.sound.SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundCategory.PLAYERS, vol, pitch);
    }

    public static void say(PlayerEntity p, String msg) { p.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), true); }

    /** A cooldown kept on the stack (so the special doesn't share the primary's cooldown bar). */
    public static boolean ready(ItemStack stack, World w, String key, int ticks) {
        long now = w.getTime();
        long at = stack.getOrCreateNbt().getLong(key);
        if (now < at) return false;
        stack.getNbt().putLong(key, now + ticks);
        return true;
    }

    public static long left(ItemStack stack, World w, String key) {
        return stack.hasNbt() ? Math.max(0, stack.getNbt().getLong(key) - w.getTime()) : 0;
    }

    /** Take `n` of `ammo` from anywhere in the inventory (creative: free). False (and nothing taken) if there isn't enough. */
    public static boolean takeAmmo(PlayerEntity p, Item ammo, int n) {
        if (p.getAbilities().creativeMode) return true;
        if (p.getInventory().count(ammo) < n) return false;
        for (int i = 0; i < p.getInventory().size() && n > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(ammo)) continue;
            int k = Math.min(n, s.getCount());
            s.decrement(k);
            n -= k;
        }
        return true;
    }

    /** The shared tail of every relic weapon's tooltip: its relic's passive and the locate hint. */
    public static void tooltip(Item weapon, List<Text> tooltip, String... lines) {
        for (String l : lines) tooltip.add(Text.literal(l).formatted(Formatting.GRAY));
        BossRelicItem relic = relicOf(weapon);
        if (relic != null) {
            tooltip.add(Text.literal("Forged from " + relic.getName().getString() + " - carries its blessing:").formatted(Formatting.DARK_AQUA));
            tooltip.add(Text.translatable("tooltip.pixelpirates." + relic.kind().name().toLowerCase()).formatted(Formatting.AQUA));
        }
        tooltip.add(Text.literal("Shift + right-click: find the next lair").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    public static float clamp01(float v) { return MathHelper.clamp(v, 0, 1); }

    // ------------------------------------------------------------------ /ppweapontest: every weapon's attacks on a dummy
    /**
     * A fake player uses every relic weapon against its own zombie (a row along +x from `at`, zombies 4 blocks ahead):
     * tap, hold-special, melee hit + right-click ability. HP lost is reported 3 s later; the relic passive check too.
     */
    public static void selfTest(ServerWorld w, Vec3d at, Consumer<String> out) {
        var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        fp.getInventory().clear();
        fp.getInventory().selectedSlot = 8;                                          // ammo fills 0.., the weapon sits in 8
        fp.getInventory().insertStack(new ItemStack(net.minecraft.item.Items.GUNPOWDER, 64));
        fp.getInventory().insertStack(new ItemStack(ModItems.CANNON_BALL, 16));
        fp.getInventory().insertStack(new ItemStack(ModItems.HARPOON, 16));
        fp.getInventory().insertStack(new ItemStack(net.minecraft.item.Items.ARROW, 64));
        List<Runnable> reports = new ArrayList<>();
        int i = 0;
        for (Item weapon : ModItems.RELIC_WEAPONS) {
            Vec3d spot = at.add(i++ * 14, 0, 0);
            var z = net.minecraft.entity.EntityType.ZOMBIE.create(w);
            if (z == null) continue;
            z.refreshPositionAndAngles(spot.x, spot.y, spot.z + 4, 180, 0);
            z.setAiDisabled(true);
            z.setPersistent();
            z.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
            z.setHealth(200);
            w.spawnEntity(z);
            fp.refreshPositionAndAngles(spot.x, spot.y, spot.z, 0, 0);
            ItemStack stack = new ItemStack(weapon);
            fp.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
            fp.getItemCooldownManager().remove(weapon);
            String name = weapon.getName().getString();
            StringBuilder log = new StringBuilder();
            try {
                BossRelicItem relic = relicOf(weapon);
                log.append("passive=").append(relic != null && BossProgression.relicActive(fp, relic)).append(' ');
                if (weapon instanceof net.minecraft.item.SwordItem || weapon instanceof net.get900.pixelpirates.item.relic.SunkenTridentItem) {
                    z.timeUntilRegen = 0;
                    weapon.postHit(stack, z, fp);
                    log.append("postHit ");
                }
                if (weapon instanceof net.get900.pixelpirates.item.relic.RelicMeleeItem) {
                    if (weapon == ModItems.BLOODFIN_MAW) { weapon.use(w, fp, net.minecraft.util.Hand.MAIN_HAND); weapon.postHit(stack, z, fp); weapon.postHit(stack, z, fp); }
                    else weapon.use(w, fp, net.minecraft.util.Hand.MAIN_HAND);
                    log.append("ability ");
                } else {
                    int max = weapon.getMaxUseTime(stack);
                    weapon.onStoppedUsing(stack, w, fp, max - 3);                     // tap
                    fp.getItemCooldownManager().remove(weapon);
                    weapon.onStoppedUsing(stack, w, fp, max - 45);                    // hold (special / full draw volley / throw)
                    log.append("tap+hold ");
                }
            } catch (Exception e) {
                log.append("EXCEPTION ").append(e);
                net.get900.pixelpirates.PixelPirates.LOGGER.warn("[RelicWeapons] self-test " + name, e);
            }
            reports.add(() -> out.accept(String.format("%-28s %s| zombie hp %.1f / 200", name, log, z.getHealth())));
        }
        later(w, 60, () -> { reports.forEach(Runnable::run); fp.getInventory().clear(); });
    }
}
