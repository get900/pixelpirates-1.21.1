package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OLD WICK, THE LAMPLIGHTER - the last soul in Gallows Landing, the run-down port on Hangman's Rock above the Gallows
 * Grotto. He keeps the soul lanterns burning "so he stays asleep", and warns anyone who will listen never to tread down
 * into the dwelling. Right-click to talk: each conversation moves on a line (warning, the story, then real help for the
 * fight). Harmless, unkillable by players, never strays far from his lamp post. Model tools/mobs/gallows.py.
 */
public class LamplighterEntity extends ModMob {
    private static final List<String> LINES = List.of(
            "Turn back, sailor. Whatever brought you to this rock - never tread down into the dwelling. Never.",
            "Down the old well there's a pit, and in the pit hangs a pirate lord. They drew and quartered him for what he did... and hung what was left in chains.",
            "The chains held him together. That was the joke of it. Now the chains are all that's left of him - and they're still moving.",
            "I keep the lanterns lit. Soul-fire is the only thing he ever feared. Or so I tell meself, so I can sleep.",
            "There's a blade under him, driven into the rock. The hangman's own. It's what holds him in the gallows. Whoever pulls it frees him - I've watched three fools try.",
            "When the chains burst, his arms and legs crawl up the walls and his head goes hopping about, laughing. You can't hurt the head. Don't bother.",
            "If you're set on it - and you've that look - bring a Tideshackle from the old coral temple. Strike the head with it and it'll be slung into a cage.",
            "And that blade... it flies true and it comes home to the hand that threw it. His limbs hate the touch of it.",
            "The way down is the well under the gatehouse, past the gallows. Mind the drop. And mind the lanterns - don't let 'em go out behind you.");
    private static final String AFTER = "You... came back up. Is it truly over? Then I can let the lanterns go out. Thank you, sailor. Go and sleep - I mean to.";

    private final Map<UUID, Integer> heard = new HashMap<>();
    private BlockPos home;
    private int talkCooldown;

    public LamplighterEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    @Override
    protected List<String> extraAnims() { return List.of("talk"); }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp) || talkCooldown > 0) return ActionResult.SUCCESS;
        talkCooldown = 20;
        this.getNavigation().stop();
        this.getLookControl().lookAt(sp, 30, 30);
        triggerAnim(ACTION, "talk");
        String line;
        int beaten = BossProgression.progress(sp), rev = BossProgression.indexOf("chained_revenant");
        if (rev >= 0 && beaten > rev) line = AFTER;
        else {
            int i = heard.getOrDefault(sp.getUuid(), 0);
            line = LINES.get(Math.min(i, LINES.size() - 1));
            heard.put(sp.getUuid(), i + 1 >= LINES.size() ? 1 : i + 1);             // loop the story, not the greeting
        }
        MutableText t = Text.literal("[Old Wick] ").formatted(Formatting.DARK_AQUA, Formatting.BOLD)
                .append(Text.literal(line).formatted(Formatting.GRAY, Formatting.ITALIC));
        sp.sendMessage(t, false);
        this.playSound(SoundEvents.ENTITY_WANDERING_TRADER_AMBIENT, 1.0f, 0.7f);
        return ActionResult.SUCCESS;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (talkCooldown > 0) talkCooldown--;
        if (home == null) home = this.getBlockPos();
        // an old man keeps to his lamp post: wander, but never more than ~9 blocks from home
        if (this.age % 20 == 0 && this.getBlockPos().getSquaredDistance(home) > 9 * 9)
            this.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.7);
        if (this.getBlockPos().getSquaredDistance(home) > 40 * 40) this.refreshPositionAndAngles(home, getYaw(), 0);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.getAttacker() instanceof ServerPlayerEntity sp && talkCooldown == 0) {
            talkCooldown = 30;
            sp.sendMessage(Text.literal("[Old Wick] ").formatted(Formatting.DARK_AQUA, Formatting.BOLD)
                    .append(Text.literal("Easy! Easy! I'm the only friend you've got on this rock.").formatted(Formatting.GRAY, Formatting.ITALIC)), false);
        }
        return false;                                                                   // not a target - for anything
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "ambient" -> SoundEvents.ENTITY_WANDERING_TRADER_AMBIENT;
            case "hurt" -> SoundEvents.ENTITY_WANDERING_TRADER_HURT;
            case "death" -> SoundEvents.ENTITY_WANDERING_TRADER_DEATH;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.7f; }

    @Override
    public int getMinAmbientSoundDelay() { return 400; }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (home != null) nbt.put("Home", NbtHelper.fromBlockPos(home));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) home = NbtHelper.toBlockPos(nbt.getCompound("Home"));
    }
}
