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
    /** Each kind has its own model (tools/mobs/traders.py -> geo/trader_<kind>) and its own booth in the market. */
    public enum Kind { QUARTERMASTER, FISHMONGER, BARKEEP, CURIO_DEALER, GUNSMITH, CHANDLER, COOK }

    private static final TrackedData<Integer> KIND = DataTracker.registerData(PortTraderEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), MOVE = RawAnimation.begin().thenLoop("move");
    private int flourishIn = 300;
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
        this.goalSelector.add(4, new WanderAroundGoal(this, 0.4, 600));      // rarely - they mind their booths (see mobTick)
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    /** Client only: when this player last right-clicked a port trader. A MerchantScreen opened within 3 s wears the port skin. */
    public static long clientOpenedAt;

    public static boolean skinRecent() { return System.currentTimeMillis() - clientOpenedAt < 3000; }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.isAlive() || this.hasCustomer() || this.isBaby()) return super.interactMob(player, hand);
        if (this.getWorld().isClient) clientOpenedAt = System.currentTimeMillis();   // -> the port counter skin (MerchantScreenSkinMixin)
        if (!this.getWorld().isClient) {
            if (this.getOffers().isEmpty()) return ActionResult.CONSUME;
            // Haggler: a per-player discount on everything the trader SELLS (paid in coins)
            for (TradeOffer t : this.getOffers()) {
                t.clearSpecialPrice();
                if (t.getOriginalFirstBuyItem().isOf(ModItems.PIRATE_COIN)) {
                    int base = t.getOriginalFirstBuyItem().getCount();
                    t.increaseSpecialPrice(net.get900.pixelpirates.world.SkillEffects.haggle(player, base) - base);
                }
            }
            this.setCustomer(player);
            this.triggerAnim("action", "talk");
            this.sendOffers(player, this.getDisplayName(), 1);
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    // ------------------------------------------------------------------ trades
    private static TradeOffer sell(ItemConvertible what, int count, int coins, int uses) {
        return new TradeOffer(new ItemStack(ModItems.COIN, coins), new ItemStack(what, count), uses, 2, 0.05f);
    }

    private static TradeOffer buy(Item what, int price) {
        int qty = Math.max(1, Math.min(16, 32 / Math.max(1, price)));
        // 6 uses a day: the port buys a fair haul, not an endless one (24 uses refilled every 2 minutes was a money tap)
        return new TradeOffer(new ItemStack(what, qty), new ItemStack(ModItems.COIN, Math.min(64, qty * price)), 6, 2, 0.05f);
    }

    @Override
    protected void fillRecipes() {
        TradeOfferList o = this.getOffers();
        switch (kind()) {
            case QUARTERMASTER -> {                                  // farming, fishing and camp goods
                o.add(sell(HomesteadItems.PINEAPPLE_CROWN, 2, 4, 16));
                o.add(sell(HomesteadItems.LIME_SEEDS, 4, 3, 16));
                o.add(sell(HomesteadItems.CHILI_SEEDS, 4, 3, 16));
                o.add(sell(ModItems.HARDTACK, 4, 2, 16));
                o.add(sell(HomesteadBlocks.FISH_TRAP, 1, 10, 6));
                o.add(sell(HomesteadItems.SALVAGE_HOOK, 1, 18, 4));
                o.add(sell(net.get900.pixelpirates.block.ModBlocks.HAMMOCK, 1, 12, 4));
                o.add(sell(Items.LANTERN, 2, 4, 12));
                o.add(sell(Items.PAPER, 6, 2, 16));
                o.add(sell(Items.COAL, 4, 2, 16));                       // no ores in our seas (2026-10-01): the port supplies the metal
                o.add(sell(Items.STRING, 4, 3, 16));                     // ...and string - no spiders out here
                o.add(sell(ModItems.PIRATE_JOURNAL, 1, 6, 4));
            }
            case GUNSMITH -> {                                       // firearms, shot and powder
                o.add(sell(HomesteadItems.PAPER_CARTRIDGE, 6, 6, 16));
                o.add(sell(HomesteadItems.SCATTERSHOT, 4, 8, 16));
                o.add(sell(Items.GUNPOWDER, 4, 5, 16));
                o.add(sell(ModItems.DYNAMITE, 2, 12, 8));
                o.add(sell(HomesteadItems.CHAIN_SHOT, 2, 10, 8));
                o.add(sell(HomesteadItems.GRAPE_SHOT, 2, 10, 8));
                o.add(sell(ModItems.DEPTH_CHARGE, 2, 14, 6));
                o.add(sell(HomesteadItems.FLINTLOCK_PISTOL, 1, 48, 2));
                o.add(sell(HomesteadItems.BLUNDERBUSS, 1, 64, 2));
                o.add(buy(ModItems.VOLCANIC_EMBER, 4));
            }
            case CHANDLER -> {                                       // everything a ship needs
                o.add(sell(ModItems.ROPE, 4, 3, 16));
                o.add(sell(Items.IRON_INGOT, 2, 5, 12));
                o.add(sell(Items.COPPER_INGOT, 3, 4, 12));
                o.add(sell(ModItems.SAIL, 1, 8, 8));
                o.add(sell(ModItems.MAST, 1, 12, 6));
                o.add(sell(ModItems.CANNON_BALL, 8, 6, 16));
                o.add(sell(ModItems.SHIP_REPAIR_KIT, 1, 20, 6));
                o.add(sell(ModItems.HARPOON, 2, 8, 12));
                o.add(sell(ModItems.SHIP_BLUEPRINT, 1, 15, 4));
                o.add(sell(HomesteadItems.GRAPPLING_HOOK, 1, 30, 2));
                o.add(buy(ModItems.TATTERED_CLOTH, 2));
                o.add(buy(ModItems.DRIFTWOOD, 1));
            }
            case COOK -> {                                           // galley dishes; buys island produce + the catch
                o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("shark_jerky"), 3, 5, 16));
                o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("fish_and_chips"), 2, 8, 12));
                o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("banana_pudding"), 1, 6, 12));
                o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("scurvy_tonic"), 1, 6, 12));
                o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("salmagundi"), 1, 12, 6));
                o.add(sell(ModItems.COCONUT_WATER, 2, 4, 16));
                o.add(sell(ModItems.SEA_BANDAGE, 2, 6, 12));
                o.add(buy(ModItems.RAW_SHARK_MEAT, 3));
                o.add(buy(HomesteadItems.PINEAPPLE, 2));
            }
            case FISHMONGER -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.FISHMONGER) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(ModItems.CHUM, 4, 3, 16));
                o.add(sell(HomesteadBlocks.LOBSTER_POT, 1, 10, 6));
            }
            case BARKEEP -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.BARKEEP) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(HomesteadItems.PINEAPPLE_GROG, 1, 6, 12));
                o.add(sell(HomesteadItems.ALE, 1, 2, 16));
                o.add(sell(HomesteadItems.HONEY_MEAD, 1, 4, 12));
                o.add(sell(HomesteadItems.SPICED_WINE, 1, 5, 12));
                o.add(sell(HomesteadItems.BILGE_WHISKEY, 1, 6, 8));
                o.add(sell(HomesteadBlocks.TANKARD, 2, 2, 16));
                o.add(sell(ModItems.COCONUT_GROG, 1, 5, 12));
                o.add(sell(HomesteadItems.MOLASSES, 2, 3, 16));
                o.add(sell(Items.SUGAR_CANE, 8, 2, 16));
            }
            case CURIO_DEALER -> {
                for (var e : Prices.BUY.entrySet()) if (e.getValue().buyer() == Prices.Buyer.CURIO_DEALER) o.add(buy(e.getKey(), e.getValue().coins()));
                o.add(sell(ModItems.TREASURE_MAP_COMMON, 1, 12, 3));
                o.add(sell(Items.COMPASS, 1, 8, 6));
                o.add(sell(Items.GOLD_INGOT, 1, 6, 8));
                o.add(sell(ModItems.DEPTH_CHARGE, 2, 10, 8));
                o.add(sell(Items.COCOA_BEANS, 6, 3, 16));
            }
        }
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        // Fence: bonus coins when the trader BUYS from you
        if (offer.getSellItem().isOf(ModItems.COIN) && this.getCustomer() != null) {
            PlayerEntity p = this.getCustomer();
            int paid = offer.getSellItem().getCount(), extra = net.get900.pixelpirates.world.SkillEffects.fence(p, paid) - paid;
            if (extra > 0) p.getInventory().offerOrDrop(new ItemStack(ModItems.COIN, extra));
        }
        if (offer.shouldRewardPlayerExperience())
            this.getWorld().spawnEntity(new ExperienceOrbEntity(this.getWorld(), getX(), getY() + 0.5, getZ(), 2 + this.random.nextInt(3)));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (--restock <= 0) {
            restock = 24000;                                              // once a Minecraft day (was every 2 minutes)
            for (TradeOffer t : this.getOffers()) t.resetUses();
        }
        // mind the booth: back behind the counter whenever he strays more than a couple of blocks
        if (home != null && this.age % 20 == 0 && this.getBlockPos().getSquaredDistance(home) > 4 && !hasCustomer())
            this.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.5);
        if (!hasCustomer() && --flourishIn <= 0) {                        // his own little idle gesture
            flourishIn = 300 + this.random.nextInt(300);
            this.triggerAnim("action", "flourish");
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.getAttacker() instanceof PlayerEntity p && this.age % 10 == 0)
            p.sendMessage(Text.literal("The " + kindName() + " scowls. \"Coin talks, sailor. Steel walks.\"").formatted(Formatting.GRAY), true);
        return false;
    }

    public String kindName() {
        return switch (kind()) {
            case QUARTERMASTER -> "Quartermaster"; case FISHMONGER -> "Fishmonger"; case BARKEEP -> "Barkeep";
            case CURIO_DEALER -> "Curio Dealer"; case GUNSMITH -> "Gunsmith"; case CHANDLER -> "Ship Chandler"; case COOK -> "Galley Cook";
        };
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
        nbt.putInt("OffersVersion", OFFERS_VERSION);
    }

    /** Bump when the stock lists change: saved traders with an older version restock from fillRecipes on load.
     *  2 = doubloons replace pirate coins, metal for sale, no banana/coconut buying (2026-10-01). */
    private static final int OFFERS_VERSION = 4;          // 3 = mob trophies at the Fishmonger

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setKind(Kind.values()[Math.floorMod(nbt.getInt("Kind"), Kind.values().length)]);
        if (nbt.contains("Home")) home = NbtHelper.toBlockPos(nbt.getCompound("Home"));
        if (nbt.getInt("OffersVersion") < OFFERS_VERSION) this.offers = null;     // getOffers() refills from fillRecipes
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, st -> st.setAndContinue(st.isMoving() ? MOVE : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 3, st -> software.bernie.geckolib.core.object.PlayState.STOP)
                .triggerableAnim("talk", RawAnimation.begin().thenPlay("talk"))
                .triggerableAnim("flourish", RawAnimation.begin().thenPlay("flourish")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
