package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.art.Art;
import net.get900.pixelpirates.homestead.art.EaselBlockEntity;
import net.get900.pixelpirates.homestead.furniture.SeatBlock;
import net.get900.pixelpirates.homestead.furniture.SeatEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A TOWNSPERSON (homestead/town): one named character from Townsfolk, living by the clock. Each part of the day TownLife
 * hands them a PLAN (where to stand, a seat, a bed, an easel or a chess board, and what they're doing there); they walk
 * there - or, when nobody is around to see, simply arrive - and get on with it: work, a drink, dice, chess, painting,
 * prayer, a stroll, bed. Right-click to talk (TownTalk: dialogue, their shop, their service). They can't be hurt.
 * Finished paintings go home with them and are hung on their own walls.
 */
public class TownsfolkEntity extends MerchantEntity implements GeoEntity {
    /** What they're doing - picks the animation, and which props the renderer shows. */
    public enum Act { IDLE, WORK, SIT, DRINK, GAMBLE, CHESS_SIT, CHESS_STAND, PAINT, PRAY, SLEEP, ORGAN, PREACH, SWING, FIDDLE, DANCE, FISH, DARTS }

    private static final TrackedData<String> FOLK = DataTracker.registerData(TownsfolkEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> ACT = DataTracker.registerData(TownsfolkEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Singing in the choir (the hymn is playing and they're one of the singers). */
    private static final TrackedData<Boolean> SING = DataTracker.registerData(TownsfolkEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // the plan (rebuilt by TownLife, never saved)
    @Nullable TownLife.Plan plan;
    @Nullable Townsfolk.Phase planPhase;
    boolean arrived;
    private double bestDist = Double.MAX_VALUE;
    private int noProgress, stuckRounds, nextWander, nextFlourish = 200, chatCool;
    // talking to a player
    @Nullable private PlayerEntity talkingTo;
    private int talkTimer;
    int line;                                                       // the next dialogue line (TownTalk)
    // painting
    @Nullable NbtCompound wip;                                      // the painting under way: {Size, Title, Author, Pixels}
    int wipRow;
    final List<NbtCompound> pending = new ArrayList<>();            // finished, waiting to be hung at home
    final List<UUID> hung = new ArrayList<>();
    // chess
    int chessOverAt = -1;
    // round 3: the day's event, the pet, greeting friends, the children's games
    @Nullable TownEvents.Mode planMode;
    @Nullable UUID petUuid;
    int petMissing, waveCool, kidCool;

    public TownsfolkEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        if (this.getNavigation() instanceof MobNavigation nav) { nav.setCanPathThroughDoors(true); nav.setCanEnterOpenDoors(true); }
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FOLK, "");
        this.dataTracker.startTracking(ACT, 0);
        this.dataTracker.startTracking(SING, false);
    }

    public String folkId() { return this.dataTracker.get(FOLK); }

    @Nullable public Townsfolk.Folk folk() { return Townsfolk.get(folkId()); }

    public void setFolk(Townsfolk.Folk f) {
        this.dataTracker.set(FOLK, f.id());
        this.setCustomName(Text.literal(f.name()));
    }

    public Act act() { return Act.values()[Math.floorMod(this.dataTracker.get(ACT), Act.values().length)]; }

    void setAct(Act a) { this.dataTracker.set(ACT, a.ordinal()); }

    void setSinging(boolean b) { if (this.dataTracker.get(SING) != b) this.dataTracker.set(SING, b); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LongDoorInteractGoal(this, true));
        this.goalSelector.add(2, new LookAtCustomerGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6f));
    }

    // ------------------------------------------------------------------ talking
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.isAlive() || this.hasCustomer()) return super.interactMob(player, hand);
        if (!this.getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            if (this.isSleeping()) {
                sp.sendMessage(Text.literal(name() + " is fast asleep. Zzz...").formatted(Formatting.GRAY), true);
                return ActionResult.CONSUME;
            }
            talkingTo = player;
            talkTimer = 200;
            this.getNavigation().stop();
            this.triggerAnim("action", "talk");
            TownTalk.open(sp, this, null);
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    String name() { Townsfolk.Folk f = folk(); return f == null ? "Someone" : f.name(); }

    /** Open the vanilla trade screen with their stock (Haggler discount like the port traders). */
    void openShop(ServerPlayerEntity player) {
        if (this.getOffers().isEmpty() || this.hasCustomer()) return;
        for (TradeOffer t : this.getOffers()) {
            t.clearSpecialPrice();
            if (t.getOriginalFirstBuyItem().isOf(ModItems.COIN)) {
                int base = t.getOriginalFirstBuyItem().getCount();
                t.increaseSpecialPrice(net.get900.pixelpirates.world.SkillEffects.haggle(player, base) - base);
                Townsfolk.Folk f = folk();
                double off = f == null ? 0 : TownMemory.discount(TownMemory.level(player, f.id()));      // friends pay less
                if (off > 0) t.increaseSpecialPrice(-Math.max(1, (int) Math.round(base * off)));
            }
        }
        talkingTo = player;
        talkTimer = 200;
        this.setCustomer(player);
        this.sendOffers(player, Text.literal(name()), 1);
    }

    void keepTalking(PlayerEntity p) { talkingTo = p; talkTimer = Math.max(talkTimer, 200); }

    @Override
    protected void fillRecipes() {
        Townsfolk.Folk f = folk();
        if (f != null) f.shop().accept(this.getOffers());
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        if (this.getCustomer() instanceof ServerPlayerEntity sp && folk() != null && this.random.nextInt(2) == 0) TownMemory.befriend(sp, folk(), 1);
        if (offer.shouldRewardPlayerExperience())
            this.getWorld().spawnEntity(new ExperienceOrbEntity(this.getWorld(), getX(), getY() + 0.5, getZ(), 1 + this.random.nextInt(2)));
    }

    // ------------------------------------------------------------------ the day
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld w)) return;
        Townsfolk.Folk f = folk();
        if (f == null || (this.age % 100 == 0 && !TownLife.owns(w, this))) { leaveSpot(); this.discard(); return; }
        if (this.age % 2400 == 0) for (TradeOffer t : this.getOffers()) t.resetUses();
        if (this.age % 100 == 0 && !TownEvents.present(w, f)) { leaveSpot(); this.discard(); return; }   // Marco: market days only
        TownPets.tick(w, this);
        if (talkTimer > 0) {
            talkTimer--;
            if (hasCustomer()) talkTimer = Math.max(talkTimer, 20);
            if (!this.hasVehicle() && !this.isSleeping()) {
                this.getNavigation().stop();
                if (talkingTo != null) this.getLookControl().lookAt(talkingTo, 30f, 30f);
            }
            if (talkingTo != null && talkingTo.squaredDistanceTo(this) > 10 * 10) talkTimer = 0;
            return;
        }
        if ((this.age + this.getId()) % 10 != 0) return;
        Townsfolk.Phase phase = Townsfolk.phase(f, w.getTimeOfDay());
        if (phase != Townsfolk.Phase.SLEEP && Townsfolk.attends(f) && TownLife.serviceElapsed(w) >= 0) phase = Townsfolk.Phase.SERVICE;
        TownEvents.Mode mode = TownLife.challenged(this) ? null : TownEvents.mode(w, f, phase);
        if (plan == null || phase != planPhase || mode != planMode || !TownLife.stillValid(w, this, plan)) {
            TownLife.release(w, this);
            leaveSpot();
            plan = TownLife.plan(w, this, f, phase, mode);
            planPhase = phase;
            planMode = mode;
            this.dataTracker.set(SING, false);
            arrived = false;
            bestDist = Double.MAX_VALUE;
            noProgress = stuckRounds = 0;
            setAct(Act.IDLE);
        }
        if (!arrived) travel(w);
        else perform(w, f, phase);
    }

    private void travel(ServerWorld w) {
        BlockPos to = plan.stand;
        Vec3d at = Vec3d.ofBottomCenter(to);
        double dx = getX() - at.x, dz = getZ() - at.z, dy = getY() - at.y, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.3 && Math.abs(dy) < 2) { arrive(w); return; }
        boolean watched = TownLife.watched(w, getPos(), 40) || TownLife.watched(w, at, 40);
        if (!watched && w.isChunkLoaded(to)) { teleport(at); arrive(w); return; }
        // walk: long trips go a stretch at a time (the path search reaches ~60 blocks)
        if (this.getNavigation().isIdle() || this.age % 100 < 10) {
            Vec3d goal = at;
            if (d > 40) {
                Vec3d dir = at.subtract(getPos()).multiply(1, 0, 1).normalize();
                BlockPos mid = TownLife.standNear(w, BlockPos.ofFloored(getPos().add(dir.multiply(32))), 5);
                goal = Vec3d.ofBottomCenter(mid);
            }
            this.getNavigation().startMovingTo(goal.x, goal.y, goal.z, 0.6);
        }
        double full = getPos().distanceTo(at);
        if (full < bestDist - 0.3) { bestDist = full; noProgress = 0; }
        else if (++noProgress > 20) {                                          // 200 ticks with no headway
            noProgress = 0;
            stuckRounds++;
            boolean close = TownLife.watched(w, getPos(), stuckRounds > 3 ? 8 : 16);
            if (!close && w.isChunkLoaded(to)) { teleport(at); arrive(w); }
            else this.getNavigation().stop();
        }
    }

    private void teleport(Vec3d at) {
        this.getNavigation().stop();
        this.refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
    }

    private void arrive(ServerWorld w) {
        arrived = true;
        this.getNavigation().stop();
        switch (plan.act) {
            case SLEEP -> {
                hangPaintings(w);
                BlockState bs = plan.bed == null ? null : w.getBlockState(plan.bed);
                if (bs != null && bs.getBlock() instanceof BedBlock && !bs.get(BedBlock.OCCUPIED)) this.sleep(plan.bed);
            }
            case SWING -> {
                if (plan.seat == null || !net.get900.pixelpirates.homestead.swing.SwingSeatEntity.sit(w, plan.seat, this)) { plan = null; return; }
            }
            case SIT, DRINK, CHESS_SIT, PRAY, GAMBLE -> {
                if (plan.act == Act.GAMBLE && plan.seat == null) break;
                if (plan.seat == null || !(w.getBlockState(plan.seat).getBlock() instanceof SeatBlock sb)
                        || !SeatEntity.sit(w, plan.seat, sb.seatHeight(), this)) { plan = null; return; }
            }
            default -> {
                if (plan.phase == Townsfolk.Phase.HOME) hangPaintings(w);
            }
        }
        setAct(plan.act);
        face();
    }

    private void face() {
        if (plan == null || plan.look == null || this.isSleeping()) return;
        double dx = plan.look.x - getX(), dz = plan.look.z - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90f;
        this.setYaw(yaw); this.setHeadYaw(yaw); this.setBodyYaw(yaw);
        if (this.getVehicle() != null) this.getVehicle().setYaw(yaw);
    }

    private void perform(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase phase) {
        Act a = act();
        if ((a == Act.SIT || a == Act.DRINK || a == Act.CHESS_SIT || a == Act.PRAY || a == Act.SWING || (a == Act.GAMBLE && plan.seat != null)) && !this.hasVehicle()) { plan = null; return; }
        if (a == Act.SLEEP && !this.isSleeping() && plan.bed != null) { plan = null; return; }
        if (this.getNavigation().isIdle() && !this.hasVehicle() && !this.isSleeping()) face();
        switch (a) {
            case PAINT -> TownLife.paintStep(w, this);
            case CHESS_SIT, CHESS_STAND -> chess(w);
            case ORGAN -> TownLife.organ(w, this, phase);
            case PREACH -> TownLife.preach(w, this);
            case SWING -> { if (this.random.nextInt(40) == 0) { plan = null; return; } }          // hop off now and then
            case FIDDLE -> TownLife.fiddle(w, this);
            case FISH -> TownLife.fishStep(w, this);
            case PRAY -> TownLife.choir(w, this);
            default -> { }
        }
        if (plan != null && (a == Act.WORK || a == Act.IDLE || a == Act.DANCE)) {
            TownLife.greetFriends(w, this);
            if (Townsfolk.scale(folkId()) < 1) TownLife.kidStep(w, this);
        }
        if (plan != null && plan.route != null) { rounds(w); return; }
        if (plan == null) return;                                              // the game ended / the canvas was taken: re-plan next time
        // wander round the spot (shop floors, the fields, a stroll)
        if (plan.wander > 0 && --nextWander <= 0 && !this.hasVehicle()) {
            nextWander = 12 + this.random.nextInt(24);
            BlockPos c = plan.wanderCentre != null ? plan.wanderCentre : plan.stand;
            if (plan.strollArea != null) c = TownLife.strollPoint(w, plan.strollArea, this.random);
            else c = TownLife.standNear(w, c.add(this.random.nextInt(plan.wander * 2 + 1) - plan.wander, 0, this.random.nextInt(plan.wander * 2 + 1) - plan.wander), 3);
            this.getNavigation().startMovingTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.45);
        }
        if ((a == Act.WORK || a == Act.IDLE) && this.getNavigation().isIdle() && --nextFlourish <= 0) {
            nextFlourish = 30 + this.random.nextInt(40);
            if (!chatWithNeighbour(w)) {
                this.triggerAnim("action", "flourish");
                if (f.id().equals("agnes") && phase == Townsfolk.Phase.WORK) TownLife.feedGulls(w, this.getPos());   // crumbs for the gulls
            }
        }
    }

    int routeIdx, routeWait;

    /** Walking a round: the crier stops to cry the news, the lamplighter to light up, the sergeant to look about. */
    private void rounds(ServerWorld w) {
        if (!this.getNavigation().isIdle()) return;
        if (routeWait-- > 0) return;
        routeWait = 6 + this.random.nextInt(6);
        Townsfolk.Folk f = folk();
        if (f != null && f.style() == Townsfolk.WorkStyle.CRIER) TownLife.cry(w, this);
        else this.triggerAnim("action", "flourish");
        if (f != null && f.id().equals("ginny")) TownLife.lightLamps(w, this.getBlockPos());
        routeIdx = (routeIdx + 1) % plan.route.length;
        BlockPos to = TownLife.groundAt(w, plan.route[routeIdx][0], plan.route[routeIdx][1]);
        if (!TownLife.watched(w, getPos(), 40) && !TownLife.watched(w, Vec3d.ofBottomCenter(to), 40) && w.isChunkLoaded(to))
            this.refreshPositionAndAngles(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, getYaw(), 0);
        else this.getNavigation().startMovingTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.5);
    }

    @Override
    public net.minecraft.entity.EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
        return super.getDimensions(pose).scaled(Townsfolk.scale(folkId()));
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (FOLK.equals(data)) this.calculateDimensions();
    }

    /** Two townsfolk at a loose end near each other stop for a natter. */
    private boolean chatWithNeighbour(ServerWorld w) {
        if (chatCool-- > 0) return false;
        List<TownsfolkEntity> near = w.getEntitiesByClass(TownsfolkEntity.class, this.getBoundingBox().expand(3.5), e -> e != this && !e.isSleeping() && !e.hasVehicle());
        if (near.isEmpty()) return false;
        TownsfolkEntity o = near.get(0);
        this.getLookControl().lookAt(o, 30f, 30f); o.getLookControl().lookAt(this, 30f, 30f);
        this.triggerAnim("action", "talk");
        o.triggerAnim("action", this.random.nextInt(3) == 0 ? "laugh" : "talk");
        chatCool = 4; o.chatCool = 4;
        return true;
    }

    private void chess(ServerWorld w) {
        if (plan.board == null) return;
        if (plan.challenge) { if (!Chess_playing(w)) plan = null; return; }          // a player's challenge: TownLife.watchChallenges
        if (!Chess_playing(w)) { plan = null; return; }
        String r = net.get900.pixelpirates.homestead.chess.Chess.result(w, plan.board);
        if (r.isEmpty()) { chessOverAt = -1; return; }
        if (chessOverAt < 0) {
            chessOverAt = this.age;
            int win = net.get900.pixelpirates.homestead.chess.Chess.winner(w, plan.board);
            if (plan.side == 0) TownLife.chessResult(w, plan, win);
            this.triggerAnim("action", win == plan.side ? "cheer" : win < 0 ? "talk" : "flourish");
        } else if (this.age - chessOverAt > 200 && plan.side == 0) TownLife.rematch(w, plan);
    }

    private boolean Chess_playing(ServerWorld w) {
        return TownLife.waitingAt(plan.board, folkId()) || net.get900.pixelpirates.homestead.chess.Chess.npcPlaying(w, plan.board, name());
    }

    void leaveSpot() {
        if (this.hasVehicle()) this.stopRiding();
        if (this.isSleeping()) this.wakeUp();
    }

    // ------------------------------------------------------------------ paintings
    private void hangPaintings(ServerWorld w) {
        while (!pending.isEmpty()) {
            if (!TownLife.hang(w, this, pending.get(0))) break;
            pending.remove(0);
        }
    }

    // ------------------------------------------------------------------ odds and ends
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.getAttacker() instanceof PlayerEntity p && this.age % 10 == 0)
            p.sendMessage(Text.literal(name() + " glares at you. \"Mind your manners, this is a respectable port.\"").formatted(Formatting.GRAY), true);
        return false;
    }

    @Override
    protected Text getDefaultName() { return Text.literal(name()); }

    @Override
    public boolean isLeveledMerchant() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    public boolean isPushable() { return !this.hasVehicle() && !this.isSleeping(); }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) { return false; }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    public SoundEvent getYesSound() { return SoundEvents.ENTITY_WANDERING_TRADER_YES; }

    @Override
    protected SoundEvent getTradingSound(boolean sold) { return sold ? SoundEvents.ENTITY_WANDERING_TRADER_YES : SoundEvents.ENTITY_WANDERING_TRADER_NO; }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }

    @Override
    public void remove(RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld w && reason.shouldDestroy()) TownLife.release(w, this);
        super.remove(reason);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Folk", folkId());
        if (wip != null) { nbt.put("Wip", wip.copy()); nbt.putInt("WipRow", wipRow); }
        NbtList l = new NbtList();
        for (NbtCompound c : pending) l.add(c.copy());
        nbt.put("Pending", l);
        NbtList h = new NbtList();
        for (UUID u : hung) h.add(NbtHelper.fromUuid(u));
        nbt.put("Hung", h);
        if (petUuid != null) nbt.putUuid("Pet", petUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        Townsfolk.Folk f = Townsfolk.get(nbt.getString("Folk"));
        if (f != null) setFolk(f);
        wip = nbt.contains("Wip") ? nbt.getCompound("Wip") : null;
        wipRow = nbt.getInt("WipRow");
        pending.clear();
        for (NbtElement e : nbt.getList("Pending", NbtElement.COMPOUND_TYPE)) pending.add((NbtCompound) e);
        hung.clear();
        for (NbtElement e : nbt.getList("Hung", NbtElement.INT_ARRAY_TYPE)) hung.add(NbtHelper.toUuid(e));
        if (nbt.containsUuid("Pet")) petUuid = nbt.getUuid("Pet");
        this.offers = null;                                              // always restock from the current list
    }

    // ------------------------------------------------------------------ GeckoLib
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), MOVE = RawAnimation.begin().thenLoop("move"),
            SIT = RawAnimation.begin().thenLoop("sit"), SIT_DRINK = RawAnimation.begin().thenLoop("sit_drink"),
            SIT_CHESS = RawAnimation.begin().thenLoop("sit_chess"), SIT_PRAY = RawAnimation.begin().thenLoop("sit_pray"),
            STAND_CHESS = RawAnimation.begin().thenLoop("stand_chess"), GAMBLE = RawAnimation.begin().thenLoop("gamble"),
            PAINT = RawAnimation.begin().thenLoop("paint"), ORGAN = RawAnimation.begin().thenLoop("organ"),
            PREACH = RawAnimation.begin().thenLoop("preach"), FIDDLE = RawAnimation.begin().thenLoop("flourish"),
            DANCE = RawAnimation.begin().thenLoop("dance"), FISH = RawAnimation.begin().thenLoop("fish"), SIT_SING = RawAnimation.begin().thenLoop("sit_sing"), SIT_GAMBLE = RawAnimation.begin().thenLoop("sit_gamble"), SLEEP = RawAnimation.begin().thenLoop("sleep");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, st -> {
            if (this.isSleeping()) return st.setAndContinue(SLEEP);
            if (this.hasVehicle()) return st.setAndContinue(switch (act()) {
                case DRINK -> SIT_DRINK; case CHESS_SIT -> SIT_CHESS; case PRAY -> this.dataTracker.get(SING) ? SIT_SING : SIT_PRAY;
                case GAMBLE -> SIT_GAMBLE; default -> SIT; });
            if (st.isMoving()) return st.setAndContinue(MOVE);
            return st.setAndContinue(switch (act()) { case GAMBLE -> GAMBLE; case PAINT -> PAINT; case CHESS_STAND -> STAND_CHESS;
                case ORGAN -> ORGAN; case PREACH -> PREACH; case FIDDLE -> FIDDLE; case DANCE -> DANCE; case FISH -> FISH; default -> IDLE; });
        }));
        controllers.add(new AnimationController<>(this, "action", 3, st -> PlayState.STOP)
                .triggerableAnim("talk", RawAnimation.begin().thenPlay("talk"))
                .triggerableAnim("laugh", RawAnimation.begin().thenPlay("cheer"))
                .triggerableAnim("cheer", RawAnimation.begin().thenPlay("cheer"))
                .triggerableAnim("wave", RawAnimation.begin().thenPlay("wave"))
                .triggerableAnim("flourish", RawAnimation.begin().thenPlay("flourish"))
                .triggerableAnim("throw", RawAnimation.begin().thenPlay("throw")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Pay coins: true when the player had them (taken from the inventory). */
    static boolean pay(ServerPlayerEntity p, int coins) {
        if (p.isCreative()) return true;
        if (p.getInventory().count(ModItems.COIN) < coins) return false;
        int left = coins;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(ModItems.COIN)) continue;
            int take = Math.min(left, s.getCount());
            s.decrement(take);
            left -= take;
        }
        return true;
    }

    static EaselBlockEntity easel(ServerWorld w, BlockPos p) { return w.getBlockEntity(p) instanceof EaselBlockEntity e ? e : null; }

    static Art.Size sizeOf(NbtCompound art) { return Art.Size.of(art.getByte("Size")); }
}
