package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.forged.SoulreaverItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

/**
 * /ppforgetest (op): builds a hearth + bellows + anvil at your feet and has a fake player run every Forging pattern through
 * the real block code (lay the piece, add materials, strike with the hammer), then a mending, then each forged weapon's
 * ability against an AI-less zombie. One line per check: OK / FAIL.
 */
public final class ForgeSelfTest {
    private ForgeSelfTest() {}

    public static void run(ServerWorld w, BlockPos at, Consumer<String> out) {
        var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        fp.getInventory().clear();
        BlockPos hearth = at.add(0, 0, 2), bellows = at.add(1, 0, 2), anvil = at.add(0, 0, 4);
        w.setBlockState(hearth, HomesteadBlocks.FORGE_HEARTH.getDefaultState().with(ForgeHearthBlock.FUEL, 8));
        w.setBlockState(bellows, HomesteadBlocks.BELLOWS.getDefaultState());
        w.setBlockState(anvil, HomesteadBlocks.FORGE_ANVIL.getDefaultState());
        BlockState as = w.getBlockState(anvil);
        fp.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 5.5, 180, 30);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anvil), Direction.UP, anvil, false);
        int fails = 0;

        for (Forging.Pattern p : Forging.PATTERNS) {
            use(fp, w, as, anvil, hit, new ItemStack(p.base().get()));
            for (Forging.Mat m : p.mats()) use(fp, w, as, anvil, hit, new ItemStack(m.item().get(), m.count()), true);
            int strikes = 0;
            ForgeAnvilBlockEntity be = (ForgeAnvilBlockEntity) w.getBlockEntity(anvil);
            while (be.piece().isOf(p.base().get()) && strikes < 10) {
                fp.getItemCooldownManager().remove(HomesteadItems.SMITHS_HAMMER);
                use(fp, w, as, anvil, hit, new ItemStack(HomesteadItems.SMITHS_HAMMER));
                strikes++;
            }
            boolean ok = be.piece().isOf(p.result().get()) && be.mats().isEmpty() && strikes == Forging.FORGE_STRIKES_BELLOWS;
            if (!ok) fails++;
            out.accept((ok ? "OK   " : "FAIL ") + p.base().get().getName().getString() + " -> " + be.piece().getName().getString()
                    + " in " + strikes + " strikes, leftovers " + be.mats().size());
            use(fp, w, as, anvil, hit, ItemStack.EMPTY);                                 // take it back
        }
        // a wrong material forges nothing; the anvil gives everything back
        use(fp, w, as, anvil, hit, new ItemStack(ModItems.NAVAL_RAPIER));
        use(fp, w, as, anvil, hit, new ItemStack(Items.DIRT, 5), true);
        for (int i = 0; i < 8; i++) { fp.getItemCooldownManager().remove(HomesteadItems.SMITHS_HAMMER); use(fp, w, as, anvil, hit, new ItemStack(HomesteadItems.SMITHS_HAMMER)); }
        ForgeAnvilBlockEntity be = (ForgeAnvilBlockEntity) w.getBlockEntity(anvil);
        boolean wrongOk = be.piece().isOf(ModItems.NAVAL_RAPIER);
        if (!wrongOk) fails++;
        out.accept((wrongOk ? "OK   " : "FAIL ") + "wrong materials forge nothing");
        use(fp, w, as, anvil, hit, ItemStack.EMPTY);
        // mending: a half-broken cutlass + rope
        ItemStack worn = new ItemStack(ModItems.CUTLASS);
        worn.setDamage(worn.getMaxDamage() / 2);
        use(fp, w, as, anvil, hit, worn);
        use(fp, w, as, anvil, hit, new ItemStack(ModItems.ROPE, 4), true);
        for (int i = 0; i < Forging.MEND_STRIKES; i++) { fp.getItemCooldownManager().remove(HomesteadItems.SMITHS_HAMMER); use(fp, w, as, anvil, hit, new ItemStack(HomesteadItems.SMITHS_HAMMER)); }
        be = (ForgeAnvilBlockEntity) w.getBlockEntity(anvil);
        boolean mendOk = be.piece().isOf(ModItems.CUTLASS) && be.piece().getDamage() == 0 && Forging.count(be.mats(), ModItems.ROPE) == 2;
        if (!mendOk) fails++;
        out.accept((mendOk ? "OK   " : "FAIL ") + "mend: cutlass damage " + be.piece().getDamage() + ", rope left " + Forging.count(be.mats(), ModItems.ROPE));
        use(fp, w, as, anvil, hit, ItemStack.EMPTY);
        // a cold hearth: no progress
        w.setBlockState(hearth, w.getBlockState(hearth).with(ForgeHearthBlock.FUEL, 0));
        use(fp, w, as, anvil, hit, new ItemStack(ModItems.RUSTED_CUTLASS));
        use(fp, w, as, anvil, hit, new ItemStack(Items.IRON_INGOT, 2), true);
        fp.getItemCooldownManager().remove(HomesteadItems.SMITHS_HAMMER);
        use(fp, w, as, anvil, hit, new ItemStack(HomesteadItems.SMITHS_HAMMER));
        be = (ForgeAnvilBlockEntity) w.getBlockEntity(anvil);
        boolean coldOk = be.strikes == 0;
        if (!coldOk) fails++;
        out.accept((coldOk ? "OK   " : "FAIL ") + "a cold hearth stops the work");
        use(fp, w, as, anvil, hit, ItemStack.EMPTY);
        fp.getInventory().clear();

        // the abilities, against a 200 HP zombie in front
        ZombieEntity z = EntityType.ZOMBIE.create(w);
        z.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 7.5, 0, 0);
        z.setAiDisabled(true);
        z.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        w.spawnEntity(z);
        fp.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 5.5, 0, 0);
        fails += check(out, "crabclaw pinch", () -> {
            ItemStack s = new ItemStack(ModItems.CRABCLAW_SABRE);
            float h = z.getHealth();
            for (int i = 0; i < 3; i++) ModItems.CRABCLAW_SABRE.postHit(s, z, fp);
            return z.getHealth() < h && z.hasStatusEffect(StatusEffects.SLOWNESS);
        });
        fails += check(out, "pearlguard parry", () -> {
            fp.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PEARLGUARD_RAPIER));
            ModItems.PEARLGUARD_RAPIER.use(w, fp, Hand.MAIN_HAND);
            boolean blocked = !net.get900.pixelpirates.world.SkillEffects.allowDamage(fp, w.getDamageSources().mobAttack(z), 5f);
            boolean secondLands = net.get900.pixelpirates.world.SkillEffects.allowDamage(fp, w.getDamageSources().mobAttack(z), 5f);
            return blocked && secondLands && z.hasStatusEffect(StatusEffects.WEAKNESS);
        });
        fails += check(out, "pistol cutlass shot", () -> {
            fp.getInventory().insertStack(new ItemStack(HomesteadItems.PAPER_CARTRIDGE, 2));
            fp.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PISTOL_CUTLASS));
            int before = w.getEntitiesByType(net.get900.pixelpirates.homestead.HomesteadEntities.MUSKET_BALL, e -> true).size();
            ModItems.PISTOL_CUTLASS.use(w, fp, Hand.MAIN_HAND);
            int after = w.getEntitiesByType(net.get900.pixelpirates.homestead.HomesteadEntities.MUSKET_BALL, e -> true).size();
            return after == before + 1 && countOf(fp, HomesteadItems.PAPER_CARTRIDGE) == 1;
        });
        fails += check(out, "obsidian halberd cleave", () -> {
            fp.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.OBSIDIAN_HALBERD));
            float h = z.getHealth();
            z.refreshPositionAndAngles(fp.getX(), fp.getY(), fp.getZ() + 2, 0, 0);
            ModItems.OBSIDIAN_HALBERD.use(w, fp, Hand.MAIN_HAND);
            return z.getHealth() < h - 6f;                                     // 8 less the zombie's armour
        });
        fails += check(out, "soulreaver soul + release", () -> {
            ItemStack s = new ItemStack(ModItems.SOULREAVER);
            fp.setStackInHand(Hand.MAIN_HAND, s);
            SoulreaverItem.onKill(fp, z);
            boolean stored = s.getNbt() != null && s.getNbt().getInt("Souls") == 1;
            ModItems.SOULREAVER.use(w, fp, Hand.MAIN_HAND);
            return stored && s.getNbt().getInt("Souls") == 0 && fp.hasStatusEffect(StatusEffects.RESISTANCE);
        });
        fails += check(out, "inkfang blind + vanish", () -> {
            ItemStack s = new ItemStack(ModItems.INKFANG);
            fp.setStackInHand(Hand.MAIN_HAND, s);
            ModItems.INKFANG.postHit(s, z, fp);
            z.setTarget(fp);
            ModItems.INKFANG.use(w, fp, Hand.MAIN_HAND);
            return z.hasStatusEffect(StatusEffects.BLINDNESS) && fp.hasStatusEffect(StatusEffects.INVISIBILITY) && z.getTarget() == null;
        });
        z.discard();
        fp.getInventory().clear();
        fp.clearStatusEffects();
        out.accept(fails == 0 ? "ALL FORGE CHECKS PASSED" : fails + " FORGE CHECK(S) FAILED");
    }

    private static void use(net.minecraft.server.network.ServerPlayerEntity fp, ServerWorld w, BlockState as, BlockPos pos, BlockHitResult hit, ItemStack held) {
        use(fp, w, as, pos, hit, held, false);
    }

    private static void use(net.minecraft.server.network.ServerPlayerEntity fp, ServerWorld w, BlockState as, BlockPos pos, BlockHitResult hit, ItemStack held, boolean sneak) {
        fp.setSneaking(sneak);
        fp.setStackInHand(Hand.MAIN_HAND, held);
        as.onUse(w, fp, Hand.MAIN_HAND, hit);
        fp.setSneaking(false);
    }

    private static int countOf(net.minecraft.entity.player.PlayerEntity p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(item)) n += p.getInventory().getStack(i).getCount();
        return n;
    }

    private static int check(Consumer<String> out, String name, java.util.function.BooleanSupplier test) {
        boolean ok;
        try { ok = test.getAsBoolean(); } catch (Exception e) { ok = false; name += " (" + e + ")"; }
        out.accept((ok ? "OK   " : "FAIL ") + name);
        return ok ? 0 : 1;
    }
}
