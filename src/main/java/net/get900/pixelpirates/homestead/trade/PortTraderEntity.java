package net.get900.pixelpirates.homestead.trade;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * PORT TRADERS (#15): four kinds of dockside merchant, trading in pirate coins through the vanilla trade screen.
 *  QUARTERMASTER sells seeds, ammunition, rope, traps and the salvage hook.
 *  FISHMONGER buys every fish (trophies too), lobster and shark meat; sells bait and traps.
 *  BARKEEP buys rum by its age and island produce; sells grog and molasses.
 *  CURIO DEALER buys monster parts and sea curios; sells treasure maps, compasses and depth charges.
 * Four set up in Wavebreak Port's market square; more can be hired to a hideout's Trading Post. Stock restocks every
 * two minutes. They can't be hurt by players. Model: port_trader (tools/mobs/traders.py), one texture per kind.
 */
public class PortTraderEntity extends MerchantEntity implements GeoEntity {
    public enum Kind { QUARTERMASTER, FISHMONGER, BARKEEP, CURIO_DEALER }

    private static final TrackedData<Integer> KIND = DataTracker.registerData(PortTraderEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), MOVE = RawAnimation.begin().thenLoop("move");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos home;
    private int restock = 2400;

    public PortTraderEntity(EntityType<? extends MerchantEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.45);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(KIND, 0);
    }

    public Kind kind() { return Kind.values()[Math.floorMod(this.dataTracker.get(KIND), Kind.values().length)]; }

    public void setKind(Kind k) { this.dataTracker.set(KIND, k.ordinal()); }

    public void setHome(BlockPos p) { this.home = p.toImmutable(); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new StopFollowingCustomerGoal(this));
        this.goalSelector.add(1, new LookAtCustomerGoal(this));
        this.goalSelector.add(4, new WanderAroundGoal(this, 0.5, 200));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.isAlive() || this.hasCustomer() || this.isBaby()) return super.interactMob(player, hand);
        if (!this.getWorld().isClient) {
            if (this.getOffers().isEmpty()) return ActionResult.CONSUME;
            this.setCustomer(player);
            this.sendOffers(player, this.getDisplayName(), 1);
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    // ------------------------------------------------------------------ trades
    private static TradeOffer sell(ItemConvertible what, int count, int coins, int uses) {
        return new TradeOffer(new ItemStack(ModItems.PIRATE_COIN, coins), new ItemStack(what, count), uses, 2, 0.05f);
    }

    private static TradeOffer buy(Item what, int price) {
        int qty = Math.max(1, Math.min(16, 32 / Math.max(1, price)));
        return new TradeOffer(new ItemStack(what, qty), new ItemStack(ModItems.PIRATE_COIN, Math.min(64, qty * price)), 24, 2, 0.05f);
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList o = this.getOffers();
        switch (kind()) {
            case QUARTERMASTER -> {
                o.add(sell(HomesteadItems.PINEAPPLE_CROWN, 2, 4, 16));
                o.add(sell(HomesteadItems.LIME_SEEDS, 4, 3, 16));
                o.add(sell(HomesteadItems.CHILI_SEEDS, 4, 3, 16));
                o.add(sell(ModItems.ROPE, 4, 3, 16));
                o.add(sell(HomesteadItems.PAPER_CARTRIDGE, 6, 6, 16));
                o.add(sell(HomesteadItems.SCATTERSHOT, 4, 8, 16));
                o.add(sell(ModItems.HARDTACK, 4, 2, 16));
                o.add(sell(HomesteadBlocks.FISH_TRAP, 1, 10, 6));
                o.add(sell(HomesteadItems.SALVAGE_HOOK, 1, 18, 4));
                o.add(sell(ModItems.SHIP_REPAIR_KIT, 1, 20, 6));
                o.add(sell(HomesteadItems.FLINTLOCK_PISTOL, 1, 48, 2));
            }
            case FISHMONGER -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.FISHMONGER) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(ModItems.CHUM, 4, 3, 16));
                o.add(sell(HomesteadBlocks.LOBSTER_POT, 1, 10, 6));
            }
            case BARKEEP -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.BARKEEP) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(HomesteadItems.PINEAPPLE_GROG, 1, 6, 12));
                o.add(sell(ModItems.COCONUT_GROG, 1, 5, 12));
                o.add(sell(HomesteadItems.MOLASSES, 2, 3, 16));
                o.add(sell(Items.SUGAR_CANE, 8, 2, 16));
            }
            case CURIO_DEALER -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.CURIO_DEALER) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(ModItems.TREASURE_MAP_COMMON, 1, 25, 3));
                o.add(sell(Items.COMPASS, 1, 8, 6));
                o.add(sell(ModItems.DEPTH_CHARGE, 2, 10, 8));
                o.add(sell(Items.COCOA_BEANS, 6, 3, 16));
            }
        }
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        if (offer.shouldRewardPlayerExperience())
            this.getWorld().spawnEntity(new ExperienceOrbEntity(this.getWorld(), getX(), getY() + 0.5, getZ(), 2 + this.random.nextInt(3)));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (--restock <= 0) {
            restock = 2400;
            for (TradeOffer t : this.getOffers()) t.resetUses();
        }
        if (home != null && this.age % 40 == 0 && this.getBlockPos().getSquaredDistance(home) > 64)
            this.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.getAttacker() instanceof PlayerEntity p && this.age % 10 == 0)
            p.sendMessage(Text.literal("The " + kindName() + " scowls. \"Coin talks, sailor. Steel walks.\"").formatted(Formatting.GRAY), true);
        return false;
    }

    public String kindName() {
        return switch (kind()) { case QUARTERMASTER -> "Quartermaster"; case FISHMONGER -> "Fishmonger"; case BARKEEP -> "Barkeep"; case CURIO_DEALER -> "Curio Dealer"; };
    }

    @Override
    protected Text getDefaultName() { return Text.literal(kindName()); }

    @Override
    public boolean isLeveledMerchant() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return hasCustomer() ? SoundEvents.ENTITY_VILLAGER_TRADE : SoundEvents.ENTITY_WANDERING_TRADER_AMBIENT; }

    @Override
    public SoundEvent getYesSound() { return SoundEvents.ENTITY_WANDERING_TRADER_YES; }

    @Override
    protected SoundEvent getTradingSound(boolean sold) { return sold ? SoundEvents.ENTITY_WANDERING_TRADER_YES : SoundEvents.ENTITY_WANDERING_TRADER_NO; }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Kind", kind().ordinal());
        if (home != null) nbt.put("Home", NbtHelper.fromBlockPos(home));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setKind(Kind.values()[Math.floorMod(nbt.getInt("Kind"), Kind.values().length)]);
        if (nbt.contains("Home")) home = NbtHelper.toBlockPos(nbt.getCompound("Home"));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, st -> st.setAndContinue(st.isMoving() ? MOVE : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
