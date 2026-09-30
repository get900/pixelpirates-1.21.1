package net.get900.pixelpirates.entity.mob;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * THE LAST KEEPER - the ghost of the last of the ancients who sealed Thalassar's heart away. When the Abyssal Heart dies
 * the chest floor SPLITS (this entity carves the crack) and he rises out of it to tell you what you have done, then fades.
 */
public class DrownedKeeperEntity extends ModMob {
    static final String[] LINES = {
            "...so. You have done it. You have stilled the heart of Thalassar.",
            "For ten thousand years it beat down here, and every beat told the Leviathan: HE STILL LIVES. STAY DOWN.",
            "We cut it from the Tide Father and buried it in a dead god's chest, so the great worm would never stop being afraid.",
            "Listen. Do you hear that? Nothing. And far away, in the Rift, something has heard the nothing too.",
            "Every hull on every sea will pay for tonight. Take your stone, fool. Follow it. It is waiting for you - it has always been waiting."};
    static final int CRACK = 30, RISE = 70, LINE_EVERY = 90;
    private BlockPos crackFrom;
    private Vec3d crackDir;
    private int t;

    public DrownedKeeperEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent();
    }

    /** The floor splits from `from` (a floor block) outward along `dir`. */
    public void setCrack(BlockPos from, Vec3d dir) { crackFrom = from; crackDir = dir.normalize(); }

    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected List<String> extraAnims() { return List.of("talk", "fade"); }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        t++;
        this.setVelocity(Vec3d.ZERO);
        if (crackFrom != null && t <= CRACK) crack(sw, t);
        if (t > 10 && t <= RISE) this.setPosition(getX(), getY() + 0.12, getZ());               // rises out of the split
        if (t % 5 == 0) sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 2, 0.3, 0.8, 0.3, 0.01);
        int k = (t - RISE) / LINE_EVERY;
        if (t >= RISE && (t - RISE) % LINE_EVERY == 0 && k < LINES.length) {
            triggerAnim(ACTION, "talk");
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.NEUTRAL, 2.0f, 0.6f);
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 96 * 96))
                p.sendMessage(Text.literal("The Last Keeper: ").formatted(Formatting.DARK_AQUA, Formatting.BOLD)
                        .append(Text.literal("\"" + LINES[k] + "\"").formatted(Formatting.AQUA, Formatting.ITALIC)), false);
        }
        int end = RISE + LINE_EVERY * LINES.length;
        if (t == end) triggerAnim(ACTION, "fade");
        if (t >= end + 30) {
            sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 60, 0.5, 1, 0.5, 0.05);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_VEX_DEATH, SoundCategory.NEUTRAL, 2.0f, 0.4f);
            this.discard();
        }
    }

    /** One more block of the split racing across the floor (both ways from the heart's seat). */
    private void crack(ServerWorld sw, int step) {
        if (step == 1) sw.playSound(null, crackFrom, SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 4.0f, 0.5f);
        for (int side = -1; side <= 1; side += 2) {
            double wob = Math.sin(step * 0.7 + side) * 1.4;
            Vec3d q = Vec3d.ofCenter(crackFrom).add(crackDir.multiply(side * step)).add(-crackDir.z * wob, 0, crackDir.x * wob);
            BlockPos c = BlockPos.ofFloored(q);
            for (int dy = 0; dy >= -3; dy--) set(sw, c.up(dy), Blocks.WATER.getDefaultState());
            set(sw, BlockPos.ofFloored(q.add(-crackDir.z, 0, crackDir.x)), Blocks.CRYING_OBSIDIAN.getDefaultState());
            set(sw, BlockPos.ofFloored(q.add(crackDir.z, 0, -crackDir.x)), Blocks.CRYING_OBSIDIAN.getDefaultState());
            set(sw, c.down(4), net.get900.pixelpirates.block.ModBlocks.LUMINOUS_VEIN.getDefaultState());
            sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, q.x, q.y + 1, q.z, 12, 0.4, 0.2, 0.4, 0.2);
        }
        if (step % 6 == 0) sw.playSound(null, crackFrom, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 3.0f, 0.5f);
    }

    private static void set(ServerWorld sw, BlockPos p, BlockState s) {
        BlockState old = sw.getBlockState(p);
        if (old.isOf(Blocks.WATER) && !s.isOf(Blocks.WATER)) return;       // don't plug the water
        if (old.getHardness(sw, p) < 0 || old.isOf(Blocks.CHEST)) return;
        sw.setBlockState(p, s, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.damage(source, amount);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluids() { return false; }

    @Override
    protected SoundEvent sound(String kind) { return null; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("T", t);
        if (crackFrom != null) {
            nbt.put("CrackFrom", NbtHelper.fromBlockPos(crackFrom));
            nbt.putDouble("DX", crackDir.x); nbt.putDouble("DZ", crackDir.z);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        t = nbt.getInt("T");
        if (nbt.contains("CrackFrom")) {
            crackFrom = NbtHelper.toBlockPos(nbt.getCompound("CrackFrom"));
            crackDir = new Vec3d(nbt.getDouble("DX"), 0, nbt.getDouble("DZ"));
        }
    }
}
