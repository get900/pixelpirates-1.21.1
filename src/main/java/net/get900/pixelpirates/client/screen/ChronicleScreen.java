package net.get900.pixelpirates.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * THE WEATHERED CHRONICLE (2026-10-01): an open leather book with parchment tabs down its right edge.
 *
 * The words are DATA: assets/pixelpirates/chronicle/tabs.json lists the tabs, and each tab's <id>.json is a flat list of
 * elements that this screen flows onto pages (two to a spread): title, h, p, quote, img, rule, space, row, torn, page
 * ("next"|"spread"), boss (a full portrait page) and dyn (hunt | fish | captain | treasure | standing - filled from the
 * reader's own state). Any element may carry "if": "met:N" (boss N's pages are revealed = boss N-1 is dead), "slain:N", "!" negations, joined with "&";
 * "slain:N", or either with a leading "!". Edit the JSON and press F3+T to re-read it.
 *
 * Art: tools/gen_chronicle_art.py -> textures/gui/chronicle/book.png (the B_* rectangles below match its atlas, in
 * texels at 2 per GUI pixel) and textures/gui/chronicle/art/*.png. Server side: world/Chronicle.
 */
@Environment(EnvType.CLIENT)
public class ChronicleScreen extends Screen {
    // ------------------------------------------------------------------ reader state (from the S2C_CHRONICLE packet)
    public record Fish(Item item, boolean caught) {}
    public record Site(String name, String x, String z) {}
    public record Rep(String id, String name, int value, String label) {}
    public record Data(int step, boolean all, String name, int level, List<Fish> fish, List<Site> sites,
                       int bounties, String hideout, List<Rep> reps) {}

    public static Data read(PacketByteBuf buf) {
        int step = buf.readVarInt();
        boolean all = buf.readBoolean();
        String name = buf.readString();
        int level = buf.readVarInt();
        List<Fish> fish = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) fish.add(new Fish(Registries.ITEM.get(new Identifier(buf.readString())), buf.readBoolean()));
        List<Site> sites = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) sites.add(new Site(buf.readString(), buf.readString(), buf.readString()));
        int bounties = buf.readVarInt();
        String hideout = buf.readString();
        List<Rep> reps = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) reps.add(new Rep(buf.readString(), buf.readString(), buf.readVarInt(), buf.readString()));
        return new Data(step, all, name, level, fish, sites, bounties, hideout, reps);
    }

    // ------------------------------------------------------------------ art
    private static final Identifier BOOK = new Identifier(PixelPirates.MOD_ID, "textures/gui/chronicle/book.png");
    private static final int TW = 1024, TH = 512;
    private static final int W = 352, H = 220;                       // the spread, GUI pixels
    private static final int TAB_W = 64, TAB_H = 18, TAB_GAP = 23, TAB_OUT = 54;
    // atlas rectangles in texels (u, v, w, h) - see gen_chronicle_art.py
    private static final int[] B_TAB = {720, 0, 108, 36}, B_TAB_SEL = {720, 38, 108, 36};
    private static final int[] B_NEXT = {720, 80, 36, 24}, B_PREV = {760, 80, 36, 24};
    private static final int[] B_X = {704, 140, 128, 128}, B_SKULL = {840, 140, 72, 72}, B_SEAL = {920, 140, 56, 56};
    private static final int[] B_RULE = {704, 280, 280, 16}, B_TORN = {704, 300, 300, 120};

    // page content boxes (GUI, relative to the book)
    private static final int[] PAGE_X = {24, 190};
    private static final int PAGE_W = 138, TOP = 16, CONTENT_H = 178;

    private static final int INK = 0xFF3B2A1A, INK_DIM = 0xFF7A6040, RED = 0xFF8E1A14, GOLD = 0xFF8A6410, FADED = 0xFFA89070;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static int lastTab = 0;
    private static final Map<String, Integer> lastSpread = new HashMap<>();
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();

    private final Data data;
    private final List<String[]> tabs = new ArrayList<>();          // {id, label}
    private int tab, spread;
    private List<Page> pages = new ArrayList<>();
    private final Map<String, Integer> anchors = new HashMap<>();
    private int bx, by;

    public ChronicleScreen(Data data) {
        super(Text.literal("The Weathered Chronicle"));
        this.data = data;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        bx = (width - (W + TAB_OUT)) / 2;
        by = (height - H) / 2;
        tabs.clear();
        JsonElement t = json("tabs");
        if (t != null) for (JsonElement e : t.getAsJsonObject().getAsJsonArray("tabs")) {
            JsonObject o = e.getAsJsonObject();
            tabs.add(new String[]{o.get("id").getAsString(), o.get("label").getAsString()});
        }
        if (tabs.isEmpty()) tabs.add(new String[]{"missing", "?"});
        tab = Math.min(lastTab, tabs.size() - 1);
        layout();
        spread = Math.min(lastSpread.getOrDefault(tabs.get(tab)[0], 0), maxSpread());
    }

    // ------------------------------------------------------------------ content
    private static JsonElement json(String name) {
        Identifier id = new Identifier(PixelPirates.MOD_ID, "chronicle/" + name + ".json");
        var res = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().getInputStream()) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            PixelPirates.LOGGER.warn("[Chronicle] bad {}: {}", id, e.toString());
            return null;
        }
    }

    private static Identifier art(String name) { return new Identifier(PixelPirates.MOD_ID, "textures/gui/chronicle/art/" + name + ".png"); }

    private static final java.util.Set<Identifier> SMOOTHED = new java.util.HashSet<>();

    /** Draw an art texture scaled to w x h, smoothed (linear filtering) - they are paintings, not pixel art. */
    private static void drawArt(DrawContext ctx, Identifier id, int[] sz, int x, int y, int w, int h) {
        if (SMOOTHED.add(id)) MinecraftClient.getInstance().getTextureManager().getTexture(id).setFilter(true, false);
        blend();
        ctx.drawTexture(id, x, y, w, h, 0, 0, sz[0], sz[1], sz[0], sz[1]);
    }

    /** Pixel size of an art texture (read once from the PNG). */
    private static int[] size(Identifier id) {
        return SIZES.computeIfAbsent(id, k -> {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(k);
            if (res.isEmpty()) return new int[]{0, 0};
            try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (Exception e) {
                return new int[]{0, 0};
            }
        });
    }

    private boolean met(int n) { return data.all() || data.step() >= n - 1; }

    private boolean slain(int n) { return data.all() || data.step() >= n; }

    private boolean reallySlain(int n) { return data.step() >= n; }

    /** "if": one or more conditions joined by '&' - met:N / slain:N, each optionally negated with '!'. */
    private boolean cond(JsonObject o) {
        if (!o.has("if")) return true;
        for (String c : o.get("if").getAsString().split("&")) if (!cond(c)) return false;
        return true;
    }

    private boolean cond(String c) {
        c = c.trim();
        boolean neg = c.startsWith("!");
        if (neg) c = c.substring(1);
        String[] a = c.split(":");
        boolean v;
        try {
            int n = Integer.parseInt(a[1].trim());
            v = switch (a[0].trim()) {
                case "met" -> met(n);
                case "slain" -> slain(n);
                default -> true;
            };
        } catch (Exception e) {
            v = true;
        }
        return neg != v;
    }

    // ------------------------------------------------------------------ layout: elements -> pages of ops
    private interface Op {
        int h();

        void draw(DrawContext ctx, int x, int y, int mx, int my);

        /** Click at (mx,my) with the op drawn at (x,y); true if handled. */
        default boolean click(int x, int y, double mx, double my) { return false; }
    }

    private static final class Page {
        final List<Op> ops = new ArrayList<>();
        final List<Integer> ys = new ArrayList<>();
    }

    private Page cur;
    private int y, keepWithNext;

    private void newPage() {
        cur = new Page();
        pages.add(cur);
        y = 0;
    }

    private void breakPage() { if (!cur.ops.isEmpty()) newPage(); }

    /** Start the next element at the top of a LEFT page. */
    private void breakSpread() {
        breakPage();
        if ((pages.size() - 1) % 2 == 1) newPage();
    }

    private void put(Op op, int gapAfter) {
        if (y + op.h() > CONTENT_H && !cur.ops.isEmpty()) newPage();
        cur.ops.add(op);
        cur.ys.add(y);
        y += op.h() + gapAfter;
    }

    private void layout() {
        pages = new ArrayList<>();
        anchors.clear();
        newPage();
        JsonElement e = json(tabs.get(tab)[0]);
        if (e == null) {
            para(Text.literal("This chapter is missing from the book."), INK_DIM, false);
            return;
        }
        List<JsonObject> shown = new ArrayList<>();
        for (JsonElement el : e.getAsJsonObject().getAsJsonArray("elements"))
            if (cond(el.getAsJsonObject())) shown.add(el.getAsJsonObject());
        for (int i = 0; i < shown.size(); i++) {
            JsonObject next = i + 1 < shown.size() ? shown.get(i + 1) : null;
            // a heading keeps its first lines with it: all of a following quote, else three lines
            keepWithNext = next == null ? 0 : next.has("quote")
                    ? textRenderer.wrapLines(Text.literal(next.get("quote").getAsString()), PAGE_W).size() * 9 : 27;
            element(shown.get(i));
        }
        while (pages.size() > 1 && pages.get(pages.size() - 1).ops.isEmpty()) pages.remove(pages.size() - 1);
    }

    private static String s(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }

    private void element(JsonObject o) {
        if (o.has("title")) title(o.get("title").getAsString());
        else if (o.has("h")) heading(o.get("h").getAsString(), s(o, "icon", null));
        else if (o.has("p")) para(Text.literal(o.get("p").getAsString()), INK, false);
        else if (o.has("quote")) para(Text.literal(o.get("quote").getAsString()).formatted(Formatting.ITALIC), INK_DIM, true);
        else if (o.has("img")) image(o.get("img").getAsString(), o.has("w") ? o.get("w").getAsInt() : PAGE_W, s(o, "caption", null));
        else if (o.has("rule")) put(new RuleOp(), 4);
        else if (o.has("space")) y += o.get("space").getAsInt();
        else if (o.has("page")) { if ("spread".equals(s(o, "page", "next"))) breakSpread(); else breakPage(); }
        else if (o.has("row")) row(o.get("row").getAsString(), s(o, "icon", null), INK);
        else if (o.has("torn")) put(new TornOp(o.get("torn").getAsString()), 6);
        else if (o.has("boss")) boss(o);
        else if (o.has("dyn")) dyn(o.get("dyn").getAsString());
    }

    private void title(String t) {
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(t).formatted(Formatting.BOLD), (int) (PAGE_W / 1.4f));
        for (int i = 0; i < lines.size(); i++) put(new TextOp(lines.get(i), RED, true, 1.4f, 13), i == lines.size() - 1 ? 3 : 0);
        put(new RuleOp(), 5);
    }

    private void heading(String t, String icon) {
        if (y > 0) y += 3;
        ItemStack st = icon == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(new Identifier(icon)));
        int indent = st.isEmpty() ? 0 : 14;
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(t).formatted(Formatting.BOLD), PAGE_W - indent);
        if (y + lines.size() * 10 + 3 + keepWithNext > CONTENT_H) newPage();   // never strand a heading at the page foot
        for (int i = 0; i < lines.size(); i++) put(new TextOp(lines.get(i), RED, false, 1f, 10, i == 0 ? st : ItemStack.EMPTY, indent), 0);
        y += 2;
    }

    private void para(Text t, int col, boolean centred) {
        List<OrderedText> lines = textRenderer.wrapLines(t, PAGE_W);
        int fit = (CONTENT_H - y) / 9;
        // keep short paragraphs and every quote whole, and never leave a single line behind on either page
        if (fit < lines.size() && !cur.ops.isEmpty() && (lines.size() <= 3 || centred || fit <= 1)) { newPage(); fit = Integer.MAX_VALUE; }
        else if (fit < lines.size() && lines.size() - fit == 1) fit--;
        else if (fit >= lines.size()) fit = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            if (i == fit && !cur.ops.isEmpty()) newPage();
            put(new TextOp(lines.get(i), col, centred, 1f, 9), 0);
        }
        y += 4;
    }

    private void row(String t, String icon, int col) {
        ItemStack st = icon == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(new Identifier(icon)));
        put(new RowOp(st, Text.literal(t), col, false), 1);
    }

    private void image(String name, int w, String caption) {
        Identifier id = art(name);
        int[] sz = size(id);
        if (sz[0] == 0) return;
        w = Math.min(w, PAGE_W);
        int h = Math.round(w * sz[1] / (float) sz[0]);
        put(new ImageOp(id, sz, w, h), caption == null ? 5 : 1);
        if (caption != null) put(new TextOp(Text.literal(caption).formatted(Formatting.ITALIC).asOrderedText(), INK_DIM, true, 0.75f, 8), 5);
    }

    private void boss(JsonObject o) {
        breakPage();
        int n = o.get("boss").getAsInt();
        anchors.put("boss:" + n, pages.size() - 1);
        put(new BossOp(n, s(o, "name", "?"), s(o, "epithet", ""), s(o, "lair", ""), s(o, "sea", ""),
                s(o, "portrait", "boss_" + n), s(o, "hint", "")), 0);
        newPage();
    }

    private void dyn(String kind) {
        switch (kind) {
            case "hunt" -> {
                JsonElement h = json("hunt");
                if (h == null) return;
                for (JsonElement el : h.getAsJsonObject().getAsJsonArray("elements")) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("boss")) put(new HuntRowOp(o.get("boss").getAsInt(), s(o, "name", "?")), 1);
                }
            }
            case "fish" -> {
                int caught = (int) data.fish().stream().filter(Fish::caught).count();
                para(Text.literal("Caught " + caught + " of " + data.fish().size()).formatted(Formatting.BOLD), GOLD, false);
                for (Fish f : data.fish())
                    put(new RowOp(new ItemStack(f.item()), f.caught() ? f.item().getName() : Text.literal("? ? ?"), f.caught() ? INK : FADED, !f.caught()), 1);
            }
            case "captain" -> {
                row("Captain " + data.name(), "pixelpirates:pirate_coin", INK);
                row("Pirate level " + data.level(), "pixelpirates:pirate_journal", INK);
                row("Bosses slain: " + Math.min(data.step(), 10) + " of 10", "minecraft:iron_sword", INK);
                row("Treasure charted: " + data.sites().size(), "pixelpirates:compass_of_desire", INK);
                row("Bounties paid: " + data.bounties(), "pixelpirates:bounty_board", INK);
                row("Hideout: " + (data.hideout().isEmpty() ? "none claimed" : data.hideout()), "pixelpirates:jolly_roger", INK);
            }
            case "treasure" -> {
                if (data.sites().isEmpty()) para(Text.literal("Nothing charted yet. Let the Compass of Desire lead you to treasure."), INK_DIM, false);
                for (Site s : data.sites()) {
                    String n = s.name().replace('_', ' ');
                    row(Character.toUpperCase(n.charAt(0)) + n.substring(1) + "  (" + s.x() + ", " + s.z() + ")", "minecraft:map", INK);
                }
            }
            case "standing" -> {
                for (Rep r : data.reps()) put(new RepOp(r), 3);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ ops
    private final class TextOp implements Op {
        final OrderedText line;
        final int col, h, indent;
        final boolean centred;
        final float scale;
        final ItemStack icon;

        TextOp(OrderedText line, int col, boolean centred, float scale, int h) { this(line, col, centred, scale, h, ItemStack.EMPTY, 0); }

        TextOp(OrderedText line, int col, boolean centred, float scale, int h, ItemStack icon, int indent) {
            this.line = line; this.col = col; this.centred = centred; this.scale = scale; this.h = h; this.icon = icon; this.indent = indent;
        }

        public int h() { return h; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            if (!icon.isEmpty()) smallItem(ctx, icon, x, y - 2);
            ctx.getMatrices().push();
            float w = textRenderer.getWidth(line) * scale;
            ctx.getMatrices().translate(centred ? x + (PAGE_W - w) / 2f : x + indent, y, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawText(textRenderer, line, 0, 0, col, false);
            ctx.getMatrices().pop();
        }
    }

    private final class RuleOp implements Op {
        public int h() { return 8; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) { part(ctx, B_RULE, x - 1, y, 140, 8); }
    }

    private final class ImageOp implements Op {
        final Identifier id;
        final int[] sz;
        final int w, h;

        ImageOp(Identifier id, int[] sz, int w, int h) { this.id = id; this.sz = sz; this.w = w; this.h = h; }

        public int h() { return h; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            drawArt(ctx, id, sz, x + (PAGE_W - w) / 2, y, w, h);
        }
    }

    private final class RowOp implements Op {
        final ItemStack icon;
        final List<OrderedText> lines;
        final int col;
        final boolean faded;

        RowOp(ItemStack icon, Text text, int col, boolean faded) {
            this.icon = icon; this.col = col; this.faded = faded;
            this.lines = textRenderer.wrapLines(text, PAGE_W - (icon.isEmpty() ? 0 : 16));
        }

        public int h() { return Math.max(12, lines.size() * 9 + 2); }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            int tx = x;
            if (!icon.isEmpty()) {
                smallItem(ctx, icon, x, y);
                if (faded) ctx.fill(x - 1, y - 1, x + 13, y + 13, 0xD8D9C39A);  // a faded sketch until you catch it
                tx += 16;
            }
            int ly = y + (lines.size() == 1 ? 2 : 0);
            for (OrderedText l : lines) { ctx.drawText(textRenderer, l, tx, ly, col, false); ly += 9; }
        }
    }

    private final class TornOp implements Op {
        final List<OrderedText> lines;

        TornOp(String text) { lines = textRenderer.wrapLines(Text.literal(text).formatted(Formatting.ITALIC), PAGE_W - 24); }

        public int h() { return lines.size() * 9 + 30; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            part(ctx, B_TORN, x - 4, y + 6, PAGE_W + 8, h() - 6);
            part(ctx, B_SEAL, x + PAGE_W / 2 - 9, y, 18, 18);
            int ly = y + 22;
            for (OrderedText l : lines) {
                ctx.drawText(textRenderer, l, x + (PAGE_W - textRenderer.getWidth(l)) / 2, ly, INK_DIM, false);
                ly += 9;
            }
        }
    }

    private final class HuntRowOp implements Op {
        final int n;
        final String name;

        HuntRowOp(int n, String name) { this.n = n; this.name = name; }

        public int h() { return 11; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            boolean known = met(n), dead = reallySlain(n), quarry = n == data.step() + 1, hot = known && in(mx, my, x, y - 1, PAGE_W, 11);
            if (hot) ctx.fill(x - 2, y - 1, x + PAGE_W + 2, y + 10, 0x307A1A10);
            ctx.drawText(textRenderer, Text.literal(ROMAN[n] + "."), x, y + 1, known ? RED : FADED, false);
            Text label = Text.literal(known ? name : "? ? ?");
            if (quarry) label = Text.literal(name).formatted(Formatting.BOLD);
            ctx.drawText(textRenderer, label, x + 22, y + 1, quarry ? GOLD : known ? INK : FADED, false);
            if (dead) part(ctx, B_SKULL, x + PAGE_W - 10, y, 10, 10);
            else if (quarry) ctx.drawText(textRenderer, Text.literal("<"), x + PAGE_W - 7, y + 1, GOLD, false);
            else if (!known) part(ctx, B_SEAL, x + PAGE_W - 10, y, 10, 10);
        }

        public boolean click(int x, int y, double mx, double my) {
            if (!met(n) || !in(mx, my, x, y - 2, PAGE_W, 13)) return false;
            Integer p = anchors.get("boss:" + n);
            if (p != null) { spread = p / 2; pageTurn(); }
            return true;
        }
    }

    private final class RepOp implements Op {
        final Rep r;

        RepOp(Rep r) { this.r = r; }

        public int h() { return 20; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            String flag = switch (r.id()) {
                case "pirates" -> "flag_corsairs";
                case "merchants" -> "flag_merchants";
                case "navy" -> "flag_armada";
                default -> "flag_drowned";
            };
            Identifier id = art(flag);
            int[] sz = size(id);
            if (sz[0] > 0) drawArt(ctx, id, sz, x, y, 28, 17);
            ctx.drawText(textRenderer, Text.literal(r.name()).formatted(Formatting.BOLD), x + 32, y, INK, false);
            ctx.drawText(textRenderer, Text.literal(r.value() + "  " + r.label()), x + 32, y + 9, r.value() < 0 ? RED : INK_DIM, false);
        }
    }

    /** A boss's portrait page: the ink sketch, struck out with a red X once slain; a sealed silhouette until revealed. */
    private final class BossOp implements Op {
        final int n;
        final String name, epithet, lair, sea, hint;
        final Identifier portrait;

        BossOp(int n, String name, String epithet, String lair, String sea, String portrait, String hint) {
            this.n = n; this.name = name; this.epithet = epithet; this.lair = lair; this.sea = sea; this.hint = hint;
            this.portrait = art(portrait);
        }

        public int h() { return CONTENT_H; }

        public void draw(DrawContext ctx, int x, int y, int mx, int my) {
            boolean known = met(n), dead = reallySlain(n);
            int cx = x + PAGE_W / 2;
            centred(ctx, Text.literal(ROMAN[n]).formatted(Formatting.BOLD), cx, y, known ? RED : FADED, 1f);
            centred(ctx, Text.literal(known ? name : "? ? ?").formatted(Formatting.BOLD), cx, y + 10, known ? INK : FADED, 1.25f);
            if (known && !epithet.isEmpty()) centred(ctx, Text.literal(epithet).formatted(Formatting.ITALIC), cx, y + 24, INK_DIM, 0.85f);
            int[] sz = size(portrait);
            int pw = 88, ph = sz[0] > 0 ? Math.min(112, Math.round(pw * sz[1] / (float) sz[0])) : 88;
            int px = cx - pw / 2, py = y + 34;
            if (sz[0] > 0) {
                if (!known) RenderSystem.setShaderColor(0.2f, 0.13f, 0.08f, 0.9f);        // an inked-over shadow
                drawArt(ctx, portrait, sz, px, py, pw, ph);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            if (!known) part(ctx, B_SEAL, cx - 14, py + ph / 2 - 14, 28, 28);
            if (dead) {
                part(ctx, B_X, cx - 42, py + ph / 2 - 42, 84, 84);
                part(ctx, B_SKULL, px + pw - 20, py + ph - 22, 22, 22);
            }
            int ty = py + ph + 4;
            if (!known) {
                // name the boss before it only once the reader knows that one too
                String h = hint.isEmpty() || !met(n - 1) ? "These pages are still blank." : hint;
                for (OrderedText l : textRenderer.wrapLines(Text.literal(h).formatted(Formatting.ITALIC), PAGE_W)) {
                    ctx.drawText(textRenderer, l, cx - textRenderer.getWidth(l) / 2, ty, INK_DIM, false);
                    ty += 9;
                }
                return;
            }
            if (!lair.isEmpty()) { small(ctx, "Lair: " + lair, cx, ty, INK, 1); ty += 8; }
            if (!sea.isEmpty()) { small(ctx, "Waters: " + sea, cx, ty, INK, 1); ty += 8; }
            ty += 2;
            if (dead) centred(ctx, Text.literal("SLAIN").formatted(Formatting.BOLD), cx, ty, RED, 1f);
            else if (n == data.step() + 1) centred(ctx, Text.literal("Your quarry").formatted(Formatting.BOLD), cx, ty, GOLD, 1f);
            else centred(ctx, Text.literal("Hunt the others first").formatted(Formatting.ITALIC), cx, ty, INK_DIM, 0.85f);
        }
    }

    // ------------------------------------------------------------------ drawing helpers
    private static void blend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** Draw an atlas rectangle (texels) at GUI size w x h. */
    private static void part(DrawContext ctx, int[] r, int x, int y, int w, int h) {
        blend();
        ctx.drawTexture(BOOK, x, y, w, h, r[0], r[1], r[2], r[3], TW, TH);
    }

    private void smallItem(DrawContext ctx, ItemStack st, int x, int y) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawItem(st, 0, 0);
        ctx.getMatrices().pop();
    }

    private void centred(DrawContext ctx, Text t, int cx, int y, int col, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - textRenderer.getWidth(t) * scale / 2f, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(textRenderer, t, 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    /** 0.75-scale text; align 0 = left, 1 = centred on x, 2 = right-aligned to x. */
    private void small(DrawContext ctx, String s, int x, int y, int col, int align) {
        float w = textRenderer.getWidth(s) * 0.75f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(align == 1 ? x - w / 2f : align == 2 ? x - w : x, y, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawText(textRenderer, s, 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    // ------------------------------------------------------------------ render
    private int maxSpread() { return Math.max(0, (pages.size() - 1) / 2); }

    private int tabX(int i) { return bx + W - 12 + (i == tab ? 6 : 0); }

    private int tabY(int i) { return by + 14 + i * TAB_GAP; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        for (int i = 0; i < tabs.size(); i++) if (i != tab) drawTab(ctx, i, mx, my);
        blend();
        ctx.drawTexture(BOOK, bx, by, W, H, 0, 0, W * 2, H * 2, TW, TH);
        drawTab(ctx, tab, mx, my);

        for (int side = 0; side < 2; side++) {
            int pi = spread * 2 + side;
            if (pi >= pages.size()) break;
            Page p = pages.get(pi);
            int x = bx + PAGE_X[side];
            if (p.ops.isEmpty()) blankPage(ctx, x);
            for (int i = 0; i < p.ops.size(); i++) p.ops.get(i).draw(ctx, x, by + TOP + p.ys.get(i), mx, my);
            small(ctx, "- " + (pi + 1) + " -", x + PAGE_W / 2, by + H - 17, INK_DIM, 1);
        }
        if (spread > 0) {
            boolean h = in(mx, my, bx + 14, by + H - 22, 18, 12);
            part(ctx, h ? new int[]{B_PREV[0], B_PREV[1] + 26, B_PREV[2], B_PREV[3]} : B_PREV, bx + 14, by + H - 22, 18, 12);
        }
        if (spread < maxSpread()) {
            boolean h = in(mx, my, bx + W - 32, by + H - 22, 18, 12);
            part(ctx, h ? new int[]{B_NEXT[0], B_NEXT[1] + 26, B_NEXT[2], B_NEXT[3]} : B_NEXT, bx + W - 32, by + H - 22, 18, 12);
        }
        super.render(ctx, mx, my, delta);
    }

    /** A page left empty (the chapter ran on to a new spread): a faded compass rose, like a margin doodle. */
    private void blankPage(DrawContext ctx, int x) {
        Identifier id = art("compass");
        int[] sz = size(id);
        if (sz[0] == 0) return;
        blend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.28f);
        ctx.drawTexture(id, x + PAGE_W / 2 - 40, by + TOP + 50, 80, 80, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void drawTab(DrawContext ctx, int i, int mx, int my) {
        int x = tabX(i), y = tabY(i);
        boolean sel = i == tab, hover = !sel && in(mx, my, x + 12, y, TAB_W - 12, TAB_H);
        if (hover) RenderSystem.setShaderColor(1.08f, 1.05f, 1f, 1f);
        part(ctx, sel ? B_TAB_SEL : B_TAB, x, y, TAB_W, TAB_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        String label = tabs.get(i)[1];
        float sc = Math.min(1f, 42f / Math.max(1, textRenderer.getWidth(label)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 17, y + TAB_H / 2f - 4 * sc, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.drawText(textRenderer, label, 0, 0, sel ? RED : INK, false);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ input
    private void pageTurn() {
        lastSpread.put(tabs.get(tab)[0], spread);
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f));
    }

    private void turn(int d) {
        int s = Math.max(0, Math.min(maxSpread(), spread + d));
        if (s != spread) { spread = s; pageTurn(); }
    }

    private void selectTab(int i) {
        if (i == tab || i < 0 || i >= tabs.size()) return;
        tab = lastTab = i;
        layout();
        spread = Math.min(lastSpread.getOrDefault(tabs.get(tab)[0], 0), maxSpread());
        pageTurn();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (int i = 0; i < tabs.size(); i++) if (in(mx, my, tabX(i) + 12, tabY(i), TAB_W - 12, TAB_H)) { selectTab(i); return true; }
            if (spread > 0 && in(mx, my, bx + 14, by + H - 22, 18, 12)) { turn(-1); return true; }
            if (spread < maxSpread() && in(mx, my, bx + W - 32, by + H - 22, 18, 12)) { turn(1); return true; }
            for (int side = 0; side < 2; side++) {
                int pi = spread * 2 + side;
                if (pi >= pages.size()) break;
                Page p = pages.get(pi);
                for (int i = 0; i < p.ops.size(); i++)
                    if (p.ops.get(i).click(bx + PAGE_X[side], by + TOP + p.ys.get(i), mx, my)) return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        turn(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_KP_6 || key == GLFW.GLFW_KEY_PAGE_DOWN) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_KP_4 || key == GLFW.GLFW_KEY_PAGE_UP) { turn(-1); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + tabs.size()) { selectTab(key - GLFW.GLFW_KEY_1); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
