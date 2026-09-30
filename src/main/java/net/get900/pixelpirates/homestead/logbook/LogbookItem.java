package net.get900.pixelpirates.homestead.logbook;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * THE CAPTAIN'S LOGBOOK (#20): a codex that writes itself. Opens in the vanilla book screen (LogbookClient) from
 * pages kept in the stack's NBT, which the server rewrites on use and every 5 s while carried:
 *   the captain's page (bosses beaten, hideout, treasure charted, bounties paid), THE HUNT (the boss chain - beaten /
 *   next / sealed), THE CATCH (every fish: caught or not, from the picked-up stat), CHARTED TREASURE (sites found with
 *   the Compass of Desire), and STANDING (reputation with each faction).
 */
public class LogbookItem extends Item {
    public LogbookItem(Settings s) { super(s); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack s = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(s);                 // LogbookClient opens the screen
        write((ServerPlayerEntity) user, s);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 1f);
        return TypedActionResult.success(s);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof ServerPlayerEntity p && world.getTime() % 100 == 11) write(p, stack);
    }

    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        if (player instanceof ServerPlayerEntity p) write(p, stack);                // so the very first open has pages
    }

    static String json(Text t) { return Text.Serializer.toJson(t); }

    public static void write(ServerPlayerEntity p, ItemStack stack) {
        List<Text> pages = new ArrayList<>();
        HomesteadState st = HomesteadState.get(p.getServer());
        int beaten = BossProgression.progress(p);
        Set<String> found = st.discoveries(p.getUuid());
        long charted = found.stream().filter(k -> k.startsWith("site:")).count();
        HomesteadState.Hideout h = st.hideout(p.getUuid());
        int bounties = st.bounty(p.getUuid()).getInt("Done");

        pages.add(Text.literal("").append(Text.literal("CAPTAIN'S LOG\n").formatted(Formatting.BOLD, Formatting.DARK_RED))
                .append(Text.literal(p.getName().getString() + "\n\n").formatted(Formatting.ITALIC))
                .append(line("Bosses beaten", beaten + " / " + BossProgression.CHAIN.size()))
                .append(line("Treasure charted", String.valueOf(charted)))
                .append(line("Bounties paid", String.valueOf(bounties)))
                .append(line("Hideout", h == null ? "none" : h.pos().getX() + ", " + h.pos().getZ() + " (tier " + h.tier() + ")"))
                .append(Text.literal("\nTurn the page, captain.").formatted(Formatting.DARK_GRAY)));

        MutableText hunt = Text.literal("THE HUNT\n\n").formatted(Formatting.BOLD, Formatting.DARK_RED);
        for (int i = 0; i < BossProgression.CHAIN.size(); i++) {
            BossProgression.Step s = BossProgression.CHAIN.get(i);
            boolean done = i < beaten, next = i == beaten;
            hunt.append(Text.literal(done ? "[X] " : next ? "[>] " : "[ ] ").formatted(done ? Formatting.DARK_GREEN : next ? Formatting.GOLD : Formatting.DARK_GRAY))
                    .append(Text.literal((done || next ? cap(s.name()) : "???") + "\n").formatted(done ? Formatting.BLACK : next ? Formatting.DARK_RED : Formatting.GRAY));
        }
        pages.add(hunt);

        List<Item> fish = new ArrayList<>(List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH));
        fish.addAll(HomesteadItems.FISH);
        int caught = 0;
        List<MutableText> rows = new ArrayList<>();
        for (Item f : fish) {
            boolean got = p.getStatHandler().getStat(Stats.PICKED_UP.getOrCreateStat(f)) > 0 || p.getStatHandler().getStat(Stats.USED.getOrCreateStat(f)) > 0;
            if (got) caught++;
            rows.add(Text.literal(got ? "* " : "- ").formatted(got ? Formatting.DARK_AQUA : Formatting.GRAY)
                    .append(got ? f.getName().copy().formatted(Formatting.BLACK) : Text.literal("uncaught").formatted(Formatting.GRAY)).append("\n"));
        }
        for (int from = 0; from < rows.size(); from += 10) {
            MutableText page = Text.literal("THE CATCH " + (from == 0 ? "(" + caught + "/" + fish.size() + ")" : "(cont.)") + "\n\n").formatted(Formatting.BOLD, Formatting.DARK_BLUE);
            for (int i = from; i < Math.min(rows.size(), from + 10); i++) page.append(rows.get(i));
            pages.add(page);
        }

        MutableText tr = Text.literal("CHARTED TREASURE\n\n").formatted(Formatting.BOLD, Formatting.GOLD);
        int n = 0;
        for (String k : found) {
            if (!k.startsWith("site:") || n >= 10) continue;
            String[] a = k.split(":");
            tr.append(Text.literal("* " + cap(a[1].replace('_', ' ')) + "\n   " + a[2] + ", " + a[3] + "\n").formatted(Formatting.BLACK));
            n++;
        }
        if (n == 0) tr.append(Text.literal("Nothing yet. Let the Compass of Desire lead you to treasure.").formatted(Formatting.DARK_GRAY));
        pages.add(tr);

        MutableText rep = Text.literal("STANDING\n\n").formatted(Formatting.BOLD, Formatting.DARK_GREEN);
        for (Faction f : Faction.values()) {
            int r = FactionManager.getReputation(p, f);
            rep.append(Text.literal(f.displayName.replaceAll("§.", "") + "\n").formatted(Formatting.BLACK))
                    .append(Text.literal("  " + r + " - " + FactionManager.standingLabel(r).replaceAll("§.", "") + "\n").formatted(Formatting.DARK_GRAY));
        }
        pages.add(rep);

        NbtList list = new NbtList();
        for (Text t : pages) list.add(NbtString.of(json(t)));
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.put("pages", list);
        nbt.putString("title", "Captain's Logbook");
        nbt.putString("author", p.getName().getString());
        nbt.putBoolean("resolved", true);
    }

    private static Text line(String k, String v) {
        return Text.literal(k + ": ").formatted(Formatting.DARK_GRAY).append(Text.literal(v + "\n").formatted(Formatting.BLACK));
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
