package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.*;
import java.util.function.Consumer;

/**
 * THE TOWNSFOLK OF WAVEBREAK PORT (2026-10-04, the user: "we want a lot of unique looking NPCs because we want this town to
 * look lively. NPCs go to the tavern to have beer and gamble, play chess against each other, make their own paintings which
 * they hang in their own houses - a real active town").
 *
 * Every named character: who they are, where they sleep (a bed in their own house), where they work and when, what they do
 * of an evening (HOBBIES - TownLife sends them to the tavern, the chess boards, the easels, the chapel or for a stroll), what
 * they sell (a vanilla trade list) or the special service they run (the barber's book, the tattoo chair, the aviary's birds,
 * the cattery, a fortune, a pardon), and what they say. Their model is geo/folk_<id> (tools/mobs/townsfolk.py).
 * Coordinates are the island's (PortCityLayout); TownLife snaps each to the nearest cell a body can stand in, so hand edits
 * round a spot don't strand anyone.
 */
public final class Townsfolk {
    private Townsfolk() {}

    public enum Hobby { DRINK, GAMBLE, CHESS, PAINT, PRAY, STROLL, PLAY }

    /** A special service on the dialogue screen (besides Trade). */
    public enum Service { NONE, BIRDS, BARBER, TATTOO, CATS, FORTUNE, PARDON, HEAL, GALLERY }

    /** How they spend the working day: stand at the spot, sit and drink at it, paint at an easel there, or wander round it. */
    public enum WorkStyle { STAND, DRINK, PAINT, WANDER, CHESS, ORGAN, ROUNDS, CRIER, INN_SIT, PLAY, FISH, MARKET }

    /** The parts of their day. */
    public enum Phase { WORK, LEISURE, HOME, SLEEP, SERVICE }

    public record Folk(String id, String name, String title, int[] bed, int[] work, WorkStyle style, int workFrom, int workTo,
                       int sleepFrom, int sleepTo, Set<Hobby> hobbies, Service service, Consumer<TradeOfferList> shop, String[] lines) {
        public String display() { return name + ", " + title; }
    }

    public static final Map<String, Folk> ALL = new LinkedHashMap<>();

    public static Folk get(String id) { return ALL.get(id); }

    /** Day ticks: 0 = 6 am, 6000 noon, 12000 6 pm, 18000 midnight. */
    public static Phase phase(Folk f, long timeOfDay) {
        int t = (int) Math.floorMod(timeOfDay, 24000L);
        if (in(t, f.sleepFrom, f.sleepTo)) return Phase.SLEEP;
        if (in(t, f.sleepFrom - 1500, f.sleepFrom)) return Phase.HOME;
        if (in(t, f.workFrom - 1000, f.workTo)) return Phase.WORK;                    // the walk to work counts
        return Phase.LEISURE;
    }

    private static boolean in(int t, int from, int to) {
        from = Math.floorMod(from, 24000); to = Math.floorMod(to, 24000);
        return from <= to ? t >= from && t < to : t >= from || t < to;
    }

    // ------------------------------------------------------------------ trade helpers
    static TradeOffer sell(ItemConvertible what, int count, int coins, int uses) {
        return new TradeOffer(new ItemStack(ModItems.COIN, coins), new ItemStack(what, count), uses, 2, 0.05f);
    }

    static TradeOffer buy(Item what, int count, int coins) {
        return new TradeOffer(new ItemStack(what, count), new ItemStack(ModItems.COIN, coins), 8, 2, 0.05f);
    }

    private static final Consumer<TradeOfferList> NO_SHOP = o -> { };

    // ------------------------------------------------------------------ the roster
    private static final int DAY_FROM = 1000, DAY_TO = 11000, BED = 16500, RISE = 23500;

    private static void add(String id, String name, String title, int[] bed, int[] work, WorkStyle style, Set<Hobby> hobbies, Service service,
                            Consumer<TradeOfferList> shop, String... lines) {
        ALL.put(id, new Folk(id, name, title, bed, work, style, DAY_FROM, DAY_TO, BED, RISE, hobbies, service, shop, lines));
    }

    private static void add(String id, String name, String title, int[] bed, int[] work, WorkStyle style, int wf, int wt, int sf, int st,
                            Set<Hobby> hobbies, Service service, Consumer<TradeOfferList> shop, String... lines) {
        ALL.put(id, new Folk(id, name, title, bed, work, style, wf, wt, sf, st, hobbies, service, shop, lines));
    }

    private static int[] p(int x, int y, int z) { return new int[]{x, y, z}; }

    /** No house of their own: a room at the inn (TownLife.innBed hands out the beds). */
    private static final int[] INN = null;

    /** The children are drawn smaller (TownsfolkRenderer) and have smaller hitboxes (TownsfolkEntity.getDimensions). */
    public static float scale(String id) { return switch (id) { case "pip" -> 0.66f; case "molly" -> 0.62f; default -> 1f; }; }

    /** The rounds of those who walk them (x, z - the ground height is found in the world). */
    static final Map<String, int[][]> ROUTES = Map.of(
            "tobias", new int[][]{{2, 3}, {30, 10}, {62, 10}, {30, 86}, {-20, 86}, {-40, 10}, {-70, -10}, {-26, -44}},
            "ginny", new int[][]{{-8, 8}, {20, 10}, {45, 10}, {-30, 10}, {-70, -12}, {-70, 8}, {0, 60}, {-26, -44}, {40, -45}},
            "brask", new int[][]{{0, -60}, {0, -90}, {0, -118}, {3, -121}, {-40, -122}, {0, -100}},
            // the night watch: the streets, the quay, the plaza, the park, the chapel square, the north road
            "hale", new int[][]{{2, 3}, {-40, 10}, {-70, -10}, {-26, -44}, {0, -60}, {40, -45}, {62, 10}, {30, 86}},
            "finch", new int[][]{{-20, 86}, {30, 86}, {62, 10}, {2, 3}, {-40, 10}, {-70, 8}, {-90, 40}, {-60, 60}});

    /** Where the lodgers sleep when it isn't the inn: the soldiers in the Guardhouse barracks; Lazlo sleeps by day in a
     *  market keeper's inn bed (they're out at their booths then). */
    static final Map<String, String> BED_POOL = Map.of("ashby", "guardhouse", "dobbs", "guardhouse", "brask", "guardhouse",
            "lazlo", "share:trader_quartermaster");

    /** What each likes as a gift (TownTalk "Give a gift"): a liked item is worth much more friendship. */
    static final Map<String, Set<net.minecraft.item.Item>> LIKES = new HashMap<>();
    /** A pet that follows them about: kind (cat / wolf / parrot) + name (TownPets). */
    static final Map<String, String[]> PETS = Map.of(
            "agatha", new String[]{"cat", "Marmalade"}, "pettigrew", new String[]{"cat", "Admiral"}, "silas", new String[]{"wolf", "Biscuit"},
            "elspeth", new String[]{"wolf", "Shep"}, "rufus", new String[]{"parrot", "Captain Squawk"}, "hob", new String[]{"wolf", "Barley"});

    private static void likes(String id, net.minecraft.item.Item... items) { LIKES.put(id, Set.of(items)); }

    /** Who goes to the chapel service: the priest, the organist and everyone who prays of an evening. */
    public static boolean attends(Folk f) { return f.hobbies().contains(Hobby.PRAY) || f.id().equals("anselm") || f.id().equals("harmonia"); }

    static {
        // ---- the feature shopkeepers
        add("polly", "Polly Marlowe", "the Parrot Keeper", p(-31, 73, -7), p(-23, 68, -2), WorkStyle.WANDER,
                EnumSet.of(Hobby.PAINT, Hobby.STROLL), Service.BIRDS, o -> {
                    o.add(sell(HomesteadBlocks.PERCH_BRANCH, 1, 8, 6));
                    o.add(sell(Items.WHEAT_SEEDS, 8, 1, 16));
                    o.add(sell(Items.MELON_SEEDS, 8, 2, 16));
                    o.add(sell(HomesteadItems.PARROT_CRATE, 1, 60, 1));
                },
                "Mind your fingers near the macaw, love. She bites for luck.",
                "Every morning I turn the cage over - new birds from all over the seas. The rare ones don't hang about long.",
                "A parrot on the shoulder is worth two in the rigging. That's what my old mum said, anyway.",
                "They pick up words, you know. The grey one learned every curse the harbourmaster owns.",
                "If you see a bird glowing in the dark, that's no trick of the rum. Some breeds just do.");
        add("snip", "Bartholomew Snip", "the Barber", p(-50, 76, -78), p(-48, 71, -73), WorkStyle.STAND,
                EnumSet.of(Hobby.GAMBLE, Hobby.STROLL), Service.BARBER, NO_SHOP,
                "Sit, sit! Let's see what the sea has done to that chin.",
                "A captain's beard says more than his flag. Yours says... we have work to do.",
                "Can't do a proper French moustache on stubble, friend. Let it grow a week and come back.",
                "I trimmed the Governor himself once. Hands steady as a millpond. Mine, not his.",
                "Hot towel, sharp razor, clean conscience. Two out of three, anyway.");
        add("inka", "Inka Rook", "the Tattooist", p(-95, 76, -30), p(-97, 71, -31), WorkStyle.STAND,
                EnumSet.of(Hobby.GAMBLE, Hobby.DRINK), Service.TATTOO, NO_SHOP,
                "Ink is forever. Well - until you pay me to take it off.",
                "Bring me proof you've beaten something big and I'll put it on your arm. The Kraken looks good on a shoulder.",
                "Hold still. No, stiller than that.",
                "Every mark on a sailor's skin is a story. Most of them are lies, but good ones.");
        add("agatha", "Agatha Purr", "the Cat Keeper", p(128, 71, -33), p(126, 71, -35), WorkStyle.WANDER,
                EnumSet.of(Hobby.PAINT, Hobby.PRAY), Service.CATS, o -> {
                    o.add(sell(Items.COD, 4, 2, 16));
                    o.add(sell(Items.SALMON, 3, 2, 16));
                    o.add(buy(Items.TROPICAL_FISH, 4, 3));
                },
                "Every ship needs a cat. The rats agree, which is why they hate it.",
                "That ginger one fell off a brig in a storm and swam ashore. Hasn't touched water since.",
                "Cats choose their sailors, dear. I just do the paperwork.",
                "Ooh, mind the tabby, she's expecting. Again.");
        add("isadora", "Isadora Vane", "the Marine Painter", p(105, 77, -71), p(103, 71, -76), WorkStyle.PAINT,
                EnumSet.of(Hobby.PAINT, Hobby.STROLL), Service.GALLERY, o -> {
                    o.add(sell(HomesteadItems.BLANK_CANVAS, 2, 4, 16));
                    o.add(sell(HomesteadBlocks.EASEL, 1, 12, 4));
                    for (Item d : new Item[]{Items.BLUE_DYE, Items.RED_DYE, Items.YELLOW_DYE, Items.WHITE_DYE, Items.BLACK_DYE, Items.GREEN_DYE})
                        o.add(sell(d, 4, 2, 12));
                },
                "The light on the water at dawn - you can't buy that. But you can buy a canvas from me.",
                "Every painting in this town I either painted or criticised.",
                "Try painting the sea at noon. It looks like a sheet of tin. Morning or evening, always.",
                "People hang their own work at home now, you know. I've started a movement!");
        add("gideon", "Gideon Tock", "the Toymaker", p(100, 76, -29), p(104, 71, -32), WorkStyle.STAND,
                EnumSet.of(Hobby.CHESS, Hobby.STROLL), Service.NONE, o -> {
                    o.add(sell(HomesteadBlocks.CHESS_TABLE, 1, 20, 3));
                    o.add(sell(HomesteadBlocks.SWING, 1, 10, 4));
                    o.add(sell(HomesteadBlocks.HANGING_SWING, 1, 10, 4));
                    o.add(sell(HomesteadBlocks.SHIP_IN_BOTTLE_SLOOP, 1, 15, 2));
                    o.add(sell(HomesteadBlocks.SHIP_IN_BOTTLE_BRIG, 1, 25, 2));
                    o.add(sell(Items.NOTE_BLOCK, 1, 6, 4));
                },
                "Chess, sir? Chess is a war where nobody drowns. My favourite kind.",
                "Wind it three times, not four. Four and the little soldier marches off the table.",
                "I carved the giant set on the green myself. Took me a winter and most of a forest.",
                "The Commodore cheats. Don't tell him I said so.");
        // ---- the town's notables
        add("aldous", "Aldous Thorne", "the Governor", p(-110, 89, -120), p(-97, 95, -127), WorkStyle.STAND,
                EnumSet.of(Hobby.CHESS, Hobby.PRAY), Service.PARDON, NO_SHOP,
                "Wavebreak flies the Armada's colours, and those cannons on my walls remember who's a friend.",
                "Order, trade and a full treasury. Pirates are welcome to spend their gold here - just not to take anyone else's.",
                "If you've crossed the Armada, there are... arrangements. A pardon is a matter of paperwork. And coin.",
                "The fort was built to keep the sea out. It keeps the town in, too.");
        add("quill", "Edwina Quill", "the Harbourmaster", p(-14, 71, 61), p(-15, 66, 63), WorkStyle.STAND,
                EnumSet.of(Hobby.CHESS, Hobby.DRINK), Service.NONE, o -> {
                    o.add(sell(Items.COMPASS, 1, 8, 6));
                    o.add(sell(Items.CLOCK, 1, 10, 4));
                    o.add(sell(HomesteadItems.CAPTAINS_SPYGLASS, 1, 30, 2));
                    o.add(sell(HomesteadBlocks.MOORING_POST, 2, 4, 8));
                },
                "Berths are paid by the day. Dues on the board, honesty in your heart, or a fine in your purse.",
                "High tide at noon, low by supper. The tide waits for no captain - believe me, I've asked.",
                "Moor straight, sailor. Some of these bowsprits come within a whisker of my window.",
                "Strange ships out past the third ring lately. Black sails, no flag. Keep your powder dry.");
        add("martha", "Martha Goodbarrel", "the Innkeeper", p(-74, 73, 41), p(-54, 68, 30), WorkStyle.WANDER,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL), Service.NONE, o -> {
                    o.add(sell(Items.BREAD, 4, 2, 16));
                    o.add(sell(Items.RABBIT_STEW, 1, 4, 12));
                    o.add(sell(Items.PUMPKIN_PIE, 2, 4, 12));
                    o.add(sell(net.get900.pixelpirates.block.ModBlocks.HAMMOCK, 1, 12, 4));
                    o.add(sell(Items.RED_BED, 1, 10, 4));
                },
                "Clean sheets, hot pies and no fighting upstairs. Downstairs is between you and the floor.",
                "You look half-starved, dear. Have a pie. No, have two.",
                "Room four's haunted. Only by Mister Barlow's snoring, but still.",
                "Ring the bell if you need me! Not at three in the morning, mind.");
        add("silas", "Silas Lamp", "the Lighthouse Keeper", p(117, 86, -105), p(116, 86, -102), WorkStyle.STAND,
                12500, 23000, 1500, 8500, EnumSet.of(Hobby.DRINK, Hobby.CHESS), Service.NONE, o -> {
                    o.add(sell(Items.LANTERN, 2, 4, 12));
                    o.add(sell(Items.CANDLE, 4, 2, 16));
                    o.add(sell(Items.GLOW_INK_SAC, 2, 4, 12));
                    o.add(sell(Items.SPYGLASS, 1, 12, 3));
                    o.add(buy(Items.GLOWSTONE_DUST, 8, 4));
                },
                "Forty years I've kept that light. Not one ship lost on the Wavebreak rocks. Well - one. He was drunk.",
                "I sleep by day. Someone has to watch the sea while you lot snore.",
                "On a clear night you can see the Kraken's wake from the gallery. Or a whale. Probably a whale.",
                "Oil, wick, glass, flame. Simple. The sea is the complicated bit.");
        add("anselm", "Father Anselm", "the Chapel Priest", p(-63, 73, -78), p(-27, 72, -76), WorkStyle.STAND,
                EnumSet.of(Hobby.PRAY, Hobby.CHESS), Service.NONE, o -> {
                    o.add(sell(Items.CANDLE, 4, 2, 16));
                    o.add(sell(HomesteadBlocks.VOTIVE_SHIP, 1, 8, 4));
                    o.add(sell(Items.GOLDEN_APPLE, 1, 40, 1));
                },
                "Bless you, sailor. The sea takes, and the chapel gives back a little.",
                "Light a candle for the ones still out there. It costs nothing - well, two doubloons, but the thought is free.",
                "I hear confessions daily. Pirates mostly. It takes a while.",
                "The bells ring for every ship that comes home. Ring them yourself sometime - the rope's by the door.");
        add("brannoc", "Brannoc Ironhand", "the Blacksmith", p(81, 76, -78), p(53, 71, -18), WorkStyle.STAND,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, o -> {
                    o.add(sell(Items.IRON_INGOT, 3, 6, 12));
                    o.add(sell(Items.CHAIN, 4, 4, 12));
                    o.add(sell(Items.SHEARS, 1, 4, 6));
                    o.add(sell(Items.ANVIL, 1, 30, 2));
                    o.add(sell(HomesteadItems.SMITHS_HAMMER, 1, 20, 2));
                    o.add(buy(Items.IRON_NUGGET, 18, 3));
                },
                "Hot iron and cold ale. That's a good day.",
                "The forge out back mends what you break. Bring me salvage and I'll show you.",
                "Another notch in that cutlass? Leave it with me, I'll take it out and put it back sharper.",
                "They call me Ironhand. Not because of the hand - because of the arm-wrestling.");
        // ---- the country folk of the North Downs
        add("hob", "Hob Furrow", "the Farmer", p(-60, 79, -146), p(-42, 74, -140), WorkStyle.WANDER,
                EnumSet.of(Hobby.DRINK), Service.NONE, o -> {
                    o.add(sell(Items.WHEAT_SEEDS, 8, 1, 16));
                    o.add(sell(Items.CARROT, 6, 2, 16));
                    o.add(sell(Items.POTATO, 6, 2, 16));
                    o.add(sell(Items.PUMPKIN, 2, 3, 12));
                    o.add(sell(Items.EGG, 6, 2, 12));
                    o.add(buy(Items.WHEAT, 16, 3));
                },
                "Up with the sun, down with the sun. Sea folk don't know what tired is.",
                "Rain's coming. My knee says so. My knee's never wrong, except about Tuesdays.",
                "Them fort soldiers eat more bread than a flock of gulls.",
                "Mind the hay bales. And the chickens. And the pig. Mind everything, really.");
        add("elspeth", "Elspeth Fleece", "the Shepherdess", p(-11, 76, -151), p(-24, 75, -150), WorkStyle.WANDER,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL, Hobby.PAINT), Service.NONE, o -> {
                    for (Item w : new Item[]{Items.WHITE_WOOL, Items.LIGHT_GRAY_WOOL, Items.BROWN_WOOL, Items.BLACK_WOOL})
                        o.add(sell(w, 4, 3, 12));
                    o.add(sell(Items.MUTTON, 4, 3, 12));
                    o.add(sell(Items.LEAD, 1, 4, 6));
                    o.add(buy(Items.WHEAT, 16, 3));
                },
                "Up here you can hear the whole island breathe. Down there it just shouts.",
                "Sails are only wool's cousins, you know. Canvas is a poor relation.",
                "One of my ewes swam out to a ship once. Came back a fortnight later with an earring.",
                "The wind on the downs is sweeter than the harbour's. Mostly it doesn't smell of fish.");
        add("cobb", "Cobb Appleby", "the Cider Maker", p(59, 81, -71), p(60, 76, -131), WorkStyle.WANDER,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, o -> {
                    o.add(sell(Items.APPLE, 6, 2, 16));
                    o.add(sell(HomesteadItems.ALE, 1, 2, 12));
                    o.add(sell(Items.HONEY_BOTTLE, 2, 4, 8));
                    o.add(buy(Items.SUGAR, 12, 3));
                },
                "Apples from the downs, barrels from the cooper, patience from nowhere at all.",
                "Taste that? That's three summers and a very cross bee.",
                "The landlord at the Grog Barrel waters my cider. I know, because I water it first.",
                "Pressing day is the best day. Everyone comes. Nobody remembers it.");
        add("wilma", "Wilma Grist", "the Miller", p(-133, 71, -17), p(-21, 81, -102), WorkStyle.WANDER,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL), Service.NONE, o -> {
                    o.add(sell(Items.BREAD, 6, 3, 16));
                    o.add(sell(Items.SUGAR, 6, 2, 16));
                    o.add(sell(Items.CAKE, 1, 8, 4));
                    o.add(sell(Items.COOKIE, 8, 3, 12));
                    o.add(buy(Items.WHEAT, 20, 4));
                },
                "When the sails turn, the town eats. When they don't, I go to the tavern.",
                "Flour gets everywhere. I sneeze white till Sunday.",
                "My mill is the tallest thing on the island after the lighthouse. Silas is very touchy about it.",
                "Grind it fine, bake it slow, sell it warm.");
        // ---- the lore characters
        add("zora", "Madame Zora", "the Fortune Teller", p(-112, 76, -25), p(-114, 76, -32), WorkStyle.STAND,
                EnumSet.of(Hobby.GAMBLE, Hobby.STROLL), Service.FORTUNE, o -> {
                    o.add(sell(ModItems.TREASURE_MAP_COMMON, 1, 14, 2));
                    o.add(sell(Items.AMETHYST_SHARD, 2, 4, 8));
                },
                "Cross my palm with gold and the sea will whisper to you. Cross it with copper and I'll whisper instead.",
                "I see... a long voyage. A dark stranger. A sandwich. The cards are odd today.",
                "The deep things are waking, sailor. The Leviathan dreams, and its dreams have teeth.",
                "Your future is uncertain. That's not a prophecy, that's just the sea.");
        add("ned", "Ned Barlow", "Captain Wren's First Mate", p(-127, 73, -5), p(-20, 66, 88), WorkStyle.WANDER,
                EnumSet.of(Hobby.DRINK, Hobby.CHESS), Service.NONE, NO_SHOP,
                "Sailed with Captain Wren on the Merry Wren, I did. Best ship that ever kissed a reef.",
                "She went down off the east beach, the Wren. I still hear her music box some nights.",
                "The captain wound that box every evening. Played the same tune till we all hated it. I'd give my other leg to hear it now.",
                "If you ever go poking round the old wreck, mind the ribs. They bite.",
                "The sea gives you three things: salt, scars and stories. I've plenty of all three.");
        add("rufus", "Rufus Brine", "a Retired Pirate", p(-119, 73, -1), p(55, 73, 30), WorkStyle.DRINK,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, NO_SHOP,
                "Pull up a stool and buy an old pirate a drink. Or don't, and hear the stories anyway.",
                "Lost the eye to a gull. Lost the hand to a shark. Lost the ship to a card game. Kept the hat.",
                "Sixty years at sea and not a single regret. Well. The card game.");
        add("finn", "Finn Gale", "a Fisherman", p(-55, 73, -5), p(12, 66, 132), WorkStyle.FISH,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, o -> {
                    o.add(sell(Items.FISHING_ROD, 1, 6, 4));
                    o.add(sell(ModItems.CHUM, 4, 3, 12));
                    o.add(buy(Items.COD, 8, 3));
                },
                "Fish bite at dawn and dusk. In between, I mostly bite my sandwich.",
                "Caught a moonfish once. Glowed so bright I couldn't sleep for a week.",
                "Sharks out past the piers. Don't go swimming with a bleeding thumb.");
        add("nell", "Nell Suds", "the Washerwoman", p(-46, 73, -4), p(-68, 68, 17), WorkStyle.WANDER,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL, Hobby.GAMBLE), Service.NONE, NO_SHOP,
                "Tar, rum, gunpowder and gravy. That's what's on every shirt in this port.",
                "I know every secret in Wavebreak. Laundry doesn't lie.",
                "The Governor's stockings have more darns than stocking. You didn't hear it from me.");
        add("jack", "Jack Tarr", "a Sailor Ashore", p(23, 73, -3), p(0, 66, 0), WorkStyle.WANDER,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, NO_SHOP,
                "Three months at sea and two days ashore. I intend to spend both of them in the Grog Barrel.",
                "My ship sails Tuesday. Or Wednesday. Depends when the captain wakes up.",
                "Ever been round the Horn? Nor have I. But I tell everyone I have.");
        add("pettigrew", "Commodore Pettigrew", "of the Armada (ret.)", p(41, 73, -5), p(-27, 79, -114), WorkStyle.CHESS,
                EnumSet.of(Hobby.CHESS, Hobby.DRINK), Service.NONE, NO_SHOP,
                "In my day we took a ship with a broadside and a brisk word. Now it's all paperwork.",
                "Chess, young man, is the art of naval warfare with the weather taken out.",
                "Gideon says I cheat at chess. I say I simply remember where the pieces were.",
                "Thirty-one years in the Armada, and I still salute the fort guns out of habit.");
        add("rosalind", "Rosalind Fairweather", "a Lady of the Town", p(56, 74, -6), p(-74, 71, -25), WorkStyle.WANDER,
                EnumSet.of(Hobby.PAINT, Hobby.PRAY, Hobby.STROLL), Service.NONE, NO_SHOP,
                "My late husband was a captain. He loved the sea more than me, and the sea loved him back - permanently.",
                "I paint a little. Seascapes, mostly. I like the sea best when it's on my wall.",
                "Lovely day for a stroll in the Gardens. The ducks are particularly insolent this year.");
        add("dan", "Fiddler Dan", "a Busker", p(114, 73, -2), p(4, 66, 4), WorkStyle.STAND,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, o -> {
                    o.add(sell(Items.MUSIC_DISC_CAT, 1, 24, 1));
                    o.add(sell(Items.NOTE_BLOCK, 1, 5, 4));
                    o.add(sell(Items.JUKEBOX, 1, 20, 1));
                },
                "Requests? I know three songs and two of them are Drunken Sailor.",
                "A coin in the hat keeps the tune sweet. No coin, and I play the bagpipe one.",
                "The tavern pays me in ale. The ale pays me in confidence.");
        add("ptolemy", "Ptolemy Inkwell", "the Astronomer", p(89, 76, -29), p(4, 92, -139), WorkStyle.STAND,
                EnumSet.of(Hobby.CHESS, Hobby.PAINT), Service.NONE, o -> {
                    o.add(sell(Items.MAP, 1, 4, 8));
                    o.add(sell(HomesteadBlocks.SEA_CHART, 1, 10, 3));
                    o.add(sell(HomesteadBlocks.TELESCOPE, 1, 30, 2));
                    o.add(sell(Items.SPYGLASS, 1, 12, 3));
                },
                "The stars don't move, sailor. Everything else does. That's why we navigate by them.",
                "From the Ridge Lookout you can see five islands on a clear day. I've named them all after my cats.",
                "I've charted every reef within a day's sail. Most of them by hitting them.");
        add("hal", "Big Hal Grogan", "Landlord of the Grog Barrel", p(73, 73, 43), p(54, 68, 19), WorkStyle.STAND,
                3000, 17000, 18500, 2000, EnumSet.of(Hobby.GAMBLE), Service.NONE, o -> {
                    o.add(sell(HomesteadItems.ALE, 1, 2, 16));
                    o.add(sell(HomesteadItems.PINEAPPLE_GROG, 1, 6, 12));
                    o.add(sell(HomesteadItems.HONEY_MEAD, 1, 4, 12));
                    o.add(sell(HomesteadItems.SPICED_WINE, 1, 5, 12));
                    o.add(sell(HomesteadItems.KRAKENS_KISS, 1, 12, 4));
                    o.add(sell(HomesteadBlocks.TANKARD, 2, 2, 16));
                },
                "Welcome to the Grog Barrel! First drink's full price, second drink's full price, third drink you won't care.",
                "No brawling, no cheating at dice, no singing after midnight. Well - no GOOD singing.",
                "The dice in the den are honest. Mostly. Ask Rufus about the time they weren't.",
                "If Ned starts on about the Merry Wren again, top him up. He sings better than he talks.");
        add("morwenna", "Morwenna Salt", "the Net-Mender", p(29, 73, -4), p(28, 66, 68), WorkStyle.WANDER,
                EnumSet.of(Hobby.GAMBLE, Hobby.STROLL, Hobby.PRAY), Service.NONE, o -> {
                    o.add(sell(Items.STRING, 6, 3, 12));
                    o.add(sell(HomesteadBlocks.HANGING_NET, 2, 4, 8));
                    o.add(buy(Items.STRING, 12, 2));
                },
                "Shh - lean in. Did you hear about the Governor's cook and the lighthouse keeper? No? Nor did I. Yet.",
                "Every hole in a net has a story. This one's a shark. This one's Finn.",
                "Gossip's like a net, dear. It's the holes that make it useful.");
        // ---- round 2 (2026-10-04): folk who just live here - lodgers at the inn and the innkeeper's children
        add("harmonia", "Harmonia Bellweather", "the Chapel Organist", INN, p(-20, 71, -78), WorkStyle.ORGAN, 3000, 9500, BED, RISE,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL), Service.NONE, NO_SHOP,
                "The organ has four hundred pipes and every one of them has an opinion.",
                "Service is every third morning, after the bell. I play the hymns; Father Anselm plays the congregation.",
                "I practise in the mornings. If you hear a wrong note, no you didn't.",
                "Amazing Grace was written by a sailor, you know. A slaver turned preacher. The sea changes people.");
        add("pip", "Pip Goodbarrel", "the Innkeeper's Son", INN, p(-75, 71, -25), WorkStyle.PLAY, 1000, 9000, 14500, 23500,
                EnumSet.of(Hobby.PLAY, Hobby.STROLL), Service.NONE, NO_SHOP,
                "I'm going to be a pirate captain when I'm big! A real one, with a hook and everything!",
                "Mum says I can't go on the ships till I'm ten. That's AGES.",
                "Bet I can swing higher than you!",
                "Old Rufus says he fought a Kraken. Molly says he's fibbing. I think he's fibbing a BIT.");
        add("molly", "Molly Goodbarrel", "the Innkeeper's Daughter", INN, p(-80, 71, -20), WorkStyle.PLAY, 1000, 9000, 14500, 23500,
                EnumSet.of(Hobby.PLAY, Hobby.PRAY), Service.NONE, NO_SHOP,
                "This is Captain Buttons. She's a doll AND a pirate.",
                "Pip says girls can't be captains. Pip is wrong about everything.",
                "Polly let me feed the parrots! The red one said a rude word.",
                "Do you have a ship? Is it big? Can I see it? Can I steer it?");
        add("tobias", "Tobias Penn", "the Town Crier", INN, p(2, 66, 3), WorkStyle.CRIER, 1000, 11000, BED, RISE,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, NO_SHOP,
                "Hear ye, hear ye! ...oh, it's just you. Hello.",
                "I've the loudest voice in Wavebreak. My wife says so. Loudly.",
                "News, news, news! Birds, sermons, chess scores - if it happens in this town, I shout it.");
        add("ginny", "Ginny Tallow", "the Lamplighter", INN, p(-8, 66, 8), WorkStyle.ROUNDS, 10500, 13500, 18000, 3000,
                EnumSet.of(Hobby.STROLL, Hobby.DRINK), Service.NONE, NO_SHOP,
                "Every lamp in Wavebreak, every dusk. Two hundred and eleven of them. I counted.",
                "The sea's darker than the sky at night. Somebody has to light the way home.",
                "Mind the soot. It doesn't come off. Ask my face.");
        add("brask", "Sergeant Brask", "of the Fort Garrison", INN, p(0, 66, -60), WorkStyle.ROUNDS, DAY_FROM, DAY_TO, BED, RISE,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE, Hobby.CHESS), Service.NONE, NO_SHOP,
                "Off duty, sailor. If you're going to break the law, wait till Tuesday.",
                "The Governor's guns are loaded day and night. Stay friendly with the Armada and you'll never hear them.",
                "Twenty years in red. The coat's the only thing that hasn't faded.");
        add("agnes", "Agnes Crumb", "who Feeds the Gulls", INN, p(-3, 66, -3), WorkStyle.WANDER,
                EnumSet.of(Hobby.PRAY, Hobby.STROLL), Service.NONE, NO_SHOP,
                "The gulls know me. That one's Horace. That one's also Horace. They're all Horace.",
                "Forty years I've fed them. Not one has ever said thank you.",
                "My husband went to sea and never came back. The gulls come back. Every morning.");
        add("seraphine", "Seraphine Vale", "a Traveller", INN, p(-62, 68, 28), WorkStyle.INN_SIT,
                EnumSet.of(Hobby.STROLL, Hobby.DRINK), Service.NONE, NO_SHOP,
                "I'm waiting for a ship. Not any ship. One that sank a long time ago.",
                "Have you heard of the Titan's Chest? No? Then you aren't looking hard enough.",
                "The Leviathan sleeps beneath the last ring of the sea. Some of us would rather it stayed asleep.",
                "This locket? It was my mother's. She sailed with the Merry Wren. Ask Ned Barlow - he knew her.");
        // ---- round 3 (2026-10-04, from the user's idea list)
        add("elias", "Dr Elias Marrow", "the Ship's Surgeon", p(-49, 76, -78), p(130, 68, -1), WorkStyle.STAND,      // lodges with Snip: the barber-surgeons
                EnumSet.of(Hobby.CHESS, Hobby.DRINK), Service.HEAL, o -> {
                    o.add(sell(ModItems.SEA_BANDAGE, 2, 5, 16));
                    o.add(sell(net.get900.pixelpirates.item.food.PirateFoods.item("scurvy_tonic"), 1, 5, 12));
                    o.add(sell(Items.GOLDEN_CARROT, 2, 6, 8));
                    o.add(sell(Items.MILK_BUCKET, 1, 4, 6));
                },
                "Hold still. This will hurt. Not me - you.",
                "I keep a collection of peg legs. Forty-one. Each one has a story, and a former owner.",
                "Scurvy, sword cuts, shark bites, too much rum. I treat the lot. The rum, mostly with more rum.",
                "Come back in one piece, captain. Or at least bring all the pieces.");
        add("lazlo", "Lazlo Quick", "a Smuggler", INN, p(-84, 66, 72), WorkStyle.STAND, 14000, 22500, 1000, 12000,
                EnumSet.noneOf(Hobby.class), Service.NONE, o -> {
                    o.add(sell(HomesteadItems.VINTAGE_RUM, 1, 14, 3));
                    o.add(sell(HomesteadItems.KRAKENS_KISS, 1, 9, 4));
                    o.add(sell(Items.GUNPOWDER, 6, 4, 8));
                    o.add(sell(ModItems.TREASURE_MAP_COMMON, 1, 10, 2));
                    o.add(sell(HomesteadItems.COMPASS_OF_DESIRE, 1, 90, 1));
                    o.add(sell(HomesteadItems.PARROT_CRATE, 1, 45, 1));
                    o.add(buy(ModItems.VOLCANIC_EMBER, 3, 10));
                },
                "Psst. You didn't see me. I wasn't here. Nice night for it, though.",
                "Everything I sell fell off a ship. Several ships. In the dark.",
                "The Governor's men walk past this alley twice a night. I know when. That's my whole job.",
                "Daylight's bad for business. And my complexion.");
        add("ashby", "Lieutenant Ashby", "of the Fort Garrison", INN, p(-96, 89, -100), WorkStyle.STAND,
                EnumSet.of(Hobby.CHESS, Hobby.PRAY), Service.NONE, NO_SHOP,
                "The fort stands, the guns are loaded and the Governor is in residence. All is in order.",
                "Mind the moat, captain. We fished three pirates and a goat out of it last week.",
                "Pardons are the Governor's business. Arrests are mine.");
        add("dobbs", "Private Dobbs", "of the Fort Garrison", INN, p(0, 72, -92), WorkStyle.STAND,
                EnumSet.of(Hobby.GAMBLE, Hobby.DRINK), Service.NONE, NO_SHOP,
                "Halt! Who goes... oh, sorry, sir. Madam. Captain. Carry on.",
                "Four hours on the gate and not a single pirate. Not that I'm complaining. I'm complaining a bit.",
                "My mum thinks I'm in the navy. Don't tell her it's mostly standing here.");
        add("hale", "Corporal Hale", "of the Night Watch", p(-110, 89, -117), p(2, 66, 3), WorkStyle.ROUNDS, 14000, 22500, 1000, 12000,
                EnumSet.of(Hobby.DRINK), Service.NONE, NO_SHOP,
                "Nine o'clock and all's well! Mostly. Somebody's drunk in the fountain again.",
                "I walk the town all night with a lantern so you lot can sleep. You're welcome.",
                "Something moves in the alley behind the warehouses some nights. Never catch it. Probably a cat.");
        add("finch", "Private Finch", "of the Night Watch", p(-110, 89, -114), p(-20, 66, 86), WorkStyle.ROUNDS, 14000, 22500, 1000, 12000,
                EnumSet.of(Hobby.DRINK, Hobby.PRAY), Service.NONE, NO_SHOP,
                "I drum the watch at the fort. Out here I just carry the lantern. It's quieter.",
                "The harbour at night is the best thing in the world. Black water, gold lights. Don't fall in.",
                "Corporal Hale snores on his feet. I've seen it.");
        add("tom", "Tom", "a Foundling at the Inn", INN, p(-70, 71, -30), WorkStyle.PLAY, 1000, 9000, 14500, 23500,
                EnumSet.of(Hobby.PLAY, Hobby.STROLL), Service.NONE, NO_SHOP,
                "Martha took us in when our ship went down. She makes the best pies in the world.",
                "Wanna see my kite? It's a dragon. Well. It's a square. With a dragon on.",
                "When I grow up I'm going to have a ship with twenty cannons. And a cat.");
        add("bella", "Bella", "a Foundling at the Inn", INN, p(-78, 71, -28), WorkStyle.PLAY, 1000, 9000, 14500, 23500,
                EnumSet.of(Hobby.PLAY, Hobby.PRAY), Service.NONE, NO_SHOP,
                "I catch crabs on the beach. I let them go again. Mostly.",
                "Tom says he's not scared of the Kraken. He is. He hides under the bed when Rufus tells stories.",
                "Have you been to the wreck? Ned says it sings at night. I want to hear it.");
        add("marco", "Marco Venn", "a Travelling Merchant", INN, p(6, 66, -4), WorkStyle.MARKET, 1000, 11000, BED, RISE,
                EnumSet.of(Hobby.DRINK, Hobby.GAMBLE), Service.NONE, o -> TownEvents.marcoStock(o),
                "Marco Venn, at your service! From every port between here and the edge of the map.",
                "One day only, captain! Tomorrow I sail, and who knows what I'll have next time?",
                "Rare goods, fair prices. Well - rare goods.");
    }

    static {
        likes("polly", net.minecraft.item.Items.MELON_SLICE, net.minecraft.item.Items.WHEAT_SEEDS, HomesteadItems.PINEAPPLE);
        likes("snip", net.minecraft.item.Items.SHEARS, net.minecraft.item.Items.HONEY_BOTTLE);
        likes("inka", net.minecraft.item.Items.INK_SAC, net.minecraft.item.Items.GLOW_INK_SAC);
        likes("agatha", net.minecraft.item.Items.COD, net.minecraft.item.Items.SALMON, net.minecraft.item.Items.STRING);
        likes("isadora", net.minecraft.item.Items.BLUE_DYE, net.minecraft.item.Items.LAPIS_LAZULI, HomesteadItems.BLANK_CANVAS);
        likes("gideon", net.minecraft.item.Items.CLOCK, net.minecraft.item.Items.REDSTONE, net.minecraft.item.Items.NOTE_BLOCK);
        likes("aldous", net.minecraft.item.Items.GOLD_INGOT, HomesteadItems.SPICED_WINE);
        likes("quill", net.minecraft.item.Items.COMPASS, net.minecraft.item.Items.MAP);
        likes("martha", net.minecraft.item.Items.SUGAR, net.minecraft.item.Items.EGG, net.minecraft.item.Items.APPLE);
        likes("silas", net.minecraft.item.Items.GLOWSTONE_DUST, net.minecraft.item.Items.CANDLE);
        likes("anselm", net.minecraft.item.Items.CANDLE, net.minecraft.item.Items.BOOK);
        likes("brannoc", net.minecraft.item.Items.IRON_INGOT, HomesteadItems.ALE);
        likes("hob", net.minecraft.item.Items.BONE_MEAL, net.minecraft.item.Items.WHEAT_SEEDS);
        likes("elspeth", net.minecraft.item.Items.WHEAT, net.minecraft.item.Items.WHITE_WOOL);
        likes("cobb", net.minecraft.item.Items.APPLE, net.minecraft.item.Items.SUGAR);
        likes("wilma", net.minecraft.item.Items.WHEAT, net.minecraft.item.Items.CAKE);
        likes("zora", net.minecraft.item.Items.AMETHYST_SHARD, net.minecraft.item.Items.ENDER_PEARL);
        likes("ned", HomesteadItems.AGED_RUM, net.minecraft.item.Items.COMPASS);
        likes("rufus", HomesteadItems.AGED_RUM, HomesteadItems.VINTAGE_RUM, HomesteadItems.BILGE_WHISKEY);
        likes("finn", net.minecraft.item.Items.FISHING_ROD, ModItems.CHUM);
        likes("nell", net.minecraft.item.Items.WHITE_DYE, net.minecraft.item.Items.HONEY_BOTTLE);
        likes("jack", HomesteadItems.ALE, HomesteadItems.PINEAPPLE_GROG);
        likes("pettigrew", net.minecraft.item.Items.GOLD_NUGGET, HomesteadItems.SPICED_WINE);
        likes("rosalind", net.minecraft.item.Items.POPPY, net.minecraft.item.Items.PINK_TULIP, net.minecraft.item.Items.LILY_OF_THE_VALLEY);
        likes("dan", net.minecraft.item.Items.NOTE_BLOCK, HomesteadItems.ALE);
        likes("ptolemy", net.minecraft.item.Items.SPYGLASS, net.minecraft.item.Items.PAPER, net.minecraft.item.Items.MAP);
        likes("hal", net.minecraft.item.Items.SUGAR_CANE, HomesteadItems.MOLASSES);
        likes("morwenna", net.minecraft.item.Items.STRING, net.minecraft.item.Items.COOKIE);
        likes("harmonia", net.minecraft.item.Items.NOTE_BLOCK, net.minecraft.item.Items.HONEY_BOTTLE);
        likes("pip", net.minecraft.item.Items.COOKIE, net.minecraft.item.Items.STICK, net.minecraft.item.Items.WOODEN_SWORD);
        likes("molly", net.minecraft.item.Items.COOKIE, net.minecraft.item.Items.POPPY, net.minecraft.item.Items.DANDELION);
        likes("tom", net.minecraft.item.Items.COOKIE, net.minecraft.item.Items.STRING, net.minecraft.item.Items.PAPER);
        likes("bella", net.minecraft.item.Items.COOKIE, net.minecraft.item.Items.GLASS_BOTTLE);
        likes("tobias", net.minecraft.item.Items.HONEY_BOTTLE, net.minecraft.item.Items.BELL);
        likes("ginny", net.minecraft.item.Items.COAL, net.minecraft.item.Items.TORCH);
        likes("brask", HomesteadItems.ALE, net.minecraft.item.Items.IRON_INGOT);
        likes("agnes", net.minecraft.item.Items.BREAD, net.minecraft.item.Items.WHEAT_SEEDS);
        likes("seraphine", net.minecraft.item.Items.AMETHYST_SHARD, ModItems.TREASURE_MAP_COMMON);
        likes("elias", net.minecraft.item.Items.BONE, ModItems.SEA_BANDAGE);
        likes("lazlo", HomesteadItems.VINTAGE_RUM, net.minecraft.item.Items.GUNPOWDER);
        likes("ashby", net.minecraft.item.Items.GOLD_INGOT, HomesteadItems.SPICED_WINE);
        likes("hale", HomesteadItems.ALE, net.minecraft.item.Items.COOKED_BEEF);
        likes("dobbs", net.minecraft.item.Items.COOKIE, net.minecraft.item.Items.BREAD);
        likes("finch", net.minecraft.item.Items.NOTE_BLOCK, net.minecraft.item.Items.BREAD);
        likes("marco", net.minecraft.item.Items.EMERALD, net.minecraft.item.Items.GOLD_INGOT);
    }

    /** Rufus's tales, by how far along the boss chain the listener is (BossProgression.progress). */
    static final String[] RUFUS_TALES = {
            "Rackham. Captain Rackham. Sits in his old sea fort, counting cannonballs. Bring him down and the sea opens up to ye.",
            "The Serpent in the trench - fifty yards of scale and spite. Keep moving; she can't bite what she can't catch.",
            "The Molten Warlord forges in the volcano's belly. Bring water, bring courage, and leave the wool at home.",
            "The Ghost Captain sails a ship that isn't there. Shoot the lanterns, not the man - he's already dead.",
            "The Abyssal King sits his throne under a mile of water. Pressure'll flatten ye before his trident does.",
            "The Bloodfin. Smell your blood in the next sea over, she can. Fight her on a deck, never in the water.",
            "The Kraken. I saw one arm once. Just the arm. Had a ship in it.",
            "The Chained Revenant broke his chains once. Don't give him a reason to break 'em again.",
            "The Abyssal Heart beats under everything. Ye can feel it in the deck at night. Kill it, and the sea goes quiet.",
            "The Leviathan. Nobody's come back to tell me about it. Ye'll be the first. Buy me a drink after.",
            "Ye've done it all, haven't ye? Every one. Then sit down - YOU tell ME a story for once."};

    /** Madame Zora's fortunes (a few of them point at your next boss). */
    static final String[] FORTUNES = {
            "The cards say: a rare bird comes to the aviary soon. Be there when the sun is young.",
            "I see gold under sand, where the palm leans east. Or possibly a crab. The crab is also gold.",
            "Beware the sailor who smiles with no teeth. He bites anyway.",
            "Your next storm will be kinder than your last. Your next tavern bill will not.",
            "A stranger in black sails watches your wake. Turn into the wind and he will lose you.",
            "The moon fish rises when the moon is full. Bring a long line.",
            "You will win at dice tonight. Or lose. The spirits are being difficult."};
}
