package com.workshopcraft.railworks.trolley;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A carriage's parts and where they go, read from data/railworks/carriage. A slot takes one part; a phase is
 * a set of slots, open once the phases it needs are complete. The build state is a string of one char a slot.
 */
public final class CarriageLayout {
    public static final String NONE = "none", BLOW = "blow", RIVETS = "rivets", BLOCK = "BLOCK";

    public record Part(String id, Identifier item, String fix, int rivets) {
        public Item asItem() {
            return BuiltInRegistries.ITEM.getValue(item);
        }

        /** Hammer work to fix it: a blow, a rivet each, or none. */
        public int work() {
            return fix.equals(RIVETS) ? rivets : fix.equals(BLOW) ? 1 : 0;
        }
    }

    /** A textured box: zone, centre, size, then turns in degrees about x and z. */
    public record Box(String zone, float cx, float cy, float cz, float sx, float sy, float sz, float rx, float rz) {}

    /** A door leaf: 'y' turns about the pivot by swing radians (100 degrees out when absent), 's' slides along by swing. */
    public record Leaf(char axis, Vec3 pivot, float swing, List<Box> solid, List<Box> glass, List<Box> bolt) {}

    /**
     * One way a part can sit in a slot. lit boxes glow when a fixture says it is lit, blocks are slot ids that
     * must stay empty while it is fitted, and a block model draws that state in place of boxes.
     */
    public record Model(String name, Part part, List<Box> solid, List<Box> glass, List<Box> lit, List<float[]> rivets, List<float[]> seats,
                        @Nullable Leaf leaf, List<String> blocks, java.util.function.@Nullable Supplier<net.minecraft.world.level.block.state.BlockState> block, AABB bounds) {}

    public record Phase(String id, List<String> needs, boolean required) {}

    public record Slot(int i, String id, String phase, String group, int index, boolean required, boolean chain, List<String> needs,
                       int side, int end, int door, @Nullable Vec3 at, AABB box, List<Model> models, float @Nullable [] halfBay,
                       @Nullable String after, List<String> afterModels) {
        public @Nullable Model model(Item item) {
            for (Model m : models) if (m.part.asItem() == item) return m;
            return null;
        }
    }

    public static final Map<String, Part> PARTS = new LinkedHashMap<>();
    public static final List<Phase> PHASES = new ArrayList<>();
    public static final List<Slot> SLOTS = new ArrayList<>();

    /** Space kept clear for a later part, such as the power rail under each cornice. Nothing fits there yet. */
    public record Reserved(String id, String kind, int side, AABB box) {}

    public static final List<Reserved> RESERVED = new ArrayList<>();

    static {
        JsonObject parts = read("parts.json");
        for (Map.Entry<String, JsonElement> e : parts.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            PARTS.put(e.getKey(), new Part(e.getKey(), Identifier.parse(o.get("item").getAsString()), o.get("fix").getAsString(), o.has("rivets") ? o.get("rivets").getAsInt() : 0));
        }
        JsonObject layout = read("open_third.json");
        for (JsonElement e : layout.getAsJsonArray("phases")) {
            JsonObject o = e.getAsJsonObject();
            PHASES.add(new Phase(o.get("id").getAsString(), strings(o.getAsJsonArray("needs")), o.get("required").getAsBoolean()));
        }
        for (JsonElement e : layout.getAsJsonArray("slots")) {
            JsonObject o = e.getAsJsonObject();
            List<Model> ms = new ArrayList<>();
            for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("models").entrySet()) {
                JsonObject mo = m.getValue().getAsJsonObject();
                Part p = PARTS.get(m.getKey());
                if (p == null) throw new IllegalStateException("carriage part " + m.getKey() + " is not in parts.json");
                Leaf leaf = null;
                if (mo.has("leaf")) {
                    JsonObject lo = mo.getAsJsonObject("leaf");
                    leaf = new Leaf(lo.get("axis").getAsString().charAt(0), vec(lo.getAsJsonArray("pivot")), lo.has("swing") ? lo.get("swing").getAsFloat() : Float.NaN,
                            boxes(lo, "solid"), boxes(lo, "glass"), boxes(lo, "bolt"));
                }
                List<Box> solid = boxes(mo, "solid"), glass = boxes(mo, "glass"), lit = boxes(mo, "lit");
                ms.add(new Model(m.getKey(), p, solid, glass, lit, floats(mo, "rivets"), floats(mo, "seats"), leaf,
                        mo.has("blocks") ? strings(mo.getAsJsonArray("blocks")) : List.of(), null, bounds(solid, glass, lit)));
            }
            JsonArray b = o.getAsJsonArray("box");
            AABB box = new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble());
            SLOTS.add(new Slot(SLOTS.size(), o.get("id").getAsString(), o.get("phase").getAsString(), o.get("group").getAsString(), o.get("index").getAsInt(),
                    o.get("required").getAsBoolean(), o.get("chain").getAsBoolean(), o.has("needs") ? strings(o.getAsJsonArray("needs")) : List.of(),
                    o.has("side") ? o.get("side").getAsInt() : 0, o.has("end") ? o.get("end").getAsInt() : 0, o.has("door") ? o.get("door").getAsInt() : -1,
                    o.has("at") ? vec(o.getAsJsonArray("at")) : null, box, ms,
                    o.has("half_bay") ? new float[] {f(o.getAsJsonArray("half_bay"), 0), f(o.getAsJsonArray("half_bay"), 1)} : null,
                    o.has("after") ? o.get("after").getAsString() : null, o.has("after_models") ? strings(o.getAsJsonArray("after_models")) : List.of()));
        }
        for (JsonElement e : layout.getAsJsonArray("reserved")) {
            JsonObject o = e.getAsJsonObject();
            JsonArray b = o.getAsJsonArray("box");
            RESERVED.add(new Reserved(o.get("id").getAsString(), o.get("kind").getAsString(), o.get("side").getAsInt(),
                    new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble())));
        }
    }

    /** Half-bay space for API fixtures, in pixels: 16 along, 16 up, 10 deep from the aisle face to the wall. */
    public static final float BAY_X = 16, BAY_Y = 16, BAY_Z = 10, PX = 1 / 16f, WALL = 2.1f, FLOOR = 1.3f;

    /** A box given in half-bay pixels (z 0 is the aisle face, z BAY_Z the wall) in carriage terms for slot s. */
    public static Box bayBox(Slot s, String zone, float x0, float y0, float z0, float x1, float y1, float z1) {
        float mid = (s.halfBay[0] + s.halfBay[1]) / 2, k = s.side;
        float a0 = mid + k * (x0 - BAY_X / 2) * PX, a1 = mid + k * (x1 - BAY_X / 2) * PX;
        float w0 = k * (WALL - (BAY_Z - z0) * PX), w1 = k * (WALL - (BAY_Z - z1) * PX);
        return new Box(zone, (a0 + a1) / 2, FLOOR + (y0 + y1) / 2 * PX, (w0 + w1) / 2, Math.abs(a1 - a0), (y1 - y0) * PX, Math.abs(w1 - w0), 0, 0);
    }

    /**
     * Adds a fixture part from the API to every half bay of the given slot groups. Models go after the
     * built in ones, sorted by part id so client and server agree.
     */
    public static void addFixture(String partId, Identifier item, List<String> groups, java.util.function.Function<Slot, Model> make) {
        if (PARTS.containsKey(partId)) throw new IllegalStateException("carriage part " + partId + " is already registered");
        PARTS.put(partId, new Part(partId, item, BLOW, 0));
        for (int n = 0; n < SLOTS.size(); n++) {
            Slot s = SLOTS.get(n);
            if (!groups.contains(s.group) || s.halfBay == null) continue;
            Model m = make.apply(s);
            int at = s.models.size();
            while (at > 0 && s.models.get(at - 1).part.id.contains(":") && s.models.get(at - 1).part.id.compareTo(partId) > 0) at--;
            s.models.add(at, m);
            SLOTS.set(n, new Slot(s.i, s.id, s.phase, s.group, s.index, s.required, s.chain, s.needs, s.side, s.end, s.door, s.at, s.box.minmax(m.bounds), s.models, s.halfBay, s.after, s.afterModels));
        }
    }

    public static AABB bounds(List<Box> solid, List<Box> glass, List<Box> lit) {
        double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (List<Box> l : List.of(solid, glass, lit)) {
            for (Box x : l) {
                boolean turned = x.rx != 0 || x.rz != 0;
                double r = turned ? Math.sqrt(x.sx * x.sx + x.sy * x.sy + x.sz * x.sz) / 2 : 0;
                double[] h = {turned ? r : x.sx / 2, turned ? r : x.sy / 2, turned ? r : x.sz / 2}, c = {x.cx, x.cy, x.cz};
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], c[k] - h[k]);
                    b[k + 3] = Math.max(b[k + 3], c[k] + h[k]);
                }
            }
        }
        return b[0] > b[3] ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public static int index(String slotId) {
        for (Slot s : SLOTS) if (s.id.equals(slotId)) return s.i;
        return -1;
    }

    /** The model index for a part id in slot i, or -1 when that slot has no such part. */
    public static int modelIndex(int i, String partId) {
        List<Model> ms = SLOTS.get(i).models;
        for (int k = 0; k < ms.size(); k++) if (ms.get(k).part.id.equals(partId)) return k;
        return -1;
    }

    private CarriageLayout() {}

    private static JsonObject read(String name) {
        try (InputStream in = CarriageLayout.class.getResourceAsStream("/data/railworks/carriage/" + name)) {
            if (in == null) throw new IllegalStateException("missing carriage data " + name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read carriage data " + name, e);
        }
    }

    private static List<String> strings(JsonArray a) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : a) l.add(e.getAsString());
        return l;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<Box> boxes(JsonObject o, String key) {
        List<Box> l = new ArrayList<>();
        if (!o.has(key)) return l;
        for (JsonElement e : o.getAsJsonArray(key)) {
            JsonArray a = e.getAsJsonArray();
            l.add(new Box(a.get(0).getAsString(), f(a, 1), f(a, 2), f(a, 3), f(a, 4), f(a, 5), f(a, 6), f(a, 7), f(a, 9)));
        }
        return l;
    }

    private static List<float[]> floats(JsonObject o, String key) {
        List<float[]> l = new ArrayList<>();
        if (!o.has(key)) return l;
        for (JsonElement e : o.getAsJsonArray(key)) {
            JsonArray a = e.getAsJsonArray();
            float[] v = new float[a.size()];
            for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
            l.add(v);
        }
        return l;
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    // ---------------------------------------------------------------- build state

    public static String empty() {
        return ".".repeat(SLOTS.size());
    }

    public static boolean fitted(String st, int i) {
        return i < st.length() && st.charAt(i) != '.';
    }

    public static int model(String st, int i) {
        return (st.charAt(i) - 'a') / 10;
    }

    /** Hammer work done on the part in slot i: rivets set, or 1 for the blow. */
    public static int work(String st, int i) {
        return (st.charAt(i) - 'a') % 10;
    }

    public static char code(int model, int work) {
        return (char) ('a' + model * 10 + work);
    }

    public static @Nullable Model modelAt(String st, int i) {
        if (!fitted(st, i)) return null;
        List<Model> ms = SLOTS.get(i).models;
        return ms.get(Math.min(model(st, i), ms.size() - 1));
    }

    /** Fitted and all its hammer work done. */
    public static boolean secure(String st, int i) {
        Model m = modelAt(st, i);
        return m != null && work(st, i) >= m.part.work();
    }

    public static @Nullable Phase phase(String id) {
        for (Phase p : PHASES) if (p.id.equals(id)) return p;
        return null;
    }

    public static boolean complete(String st, String phase) {
        for (Slot s : SLOTS) if (s.phase.equals(phase) && s.required && !secure(st, s.i)) return false;
        return true;
    }

    private static boolean open(String st, List<String> needs) {
        for (String n : needs) if (!complete(st, n)) return false;
        return true;
    }

    /** Whether slot i could take a part now. */
    public static boolean canFit(String st, int i) {
        Slot s = SLOTS.get(i);
        if (fitted(st, i)) return false;
        Phase p = phase(s.phase);
        if (p == null || !open(st, p.needs) || !open(st, s.needs)) return false;
        if (s.after != null) {
            int j = index(s.after);
            if (j < 0 || !secure(st, j)) return false;
            if (!s.afterModels.isEmpty() && !s.afterModels.contains(SLOTS.get(j).models.get(model(st, j)).name)) return false;
        }
        if (!s.chain) return true;
        int n = 0;
        for (Slot o : SLOTS) if (o.group.equals(s.group)) n++;
        if (s.index == 0 || s.index == n - 1) return true;
        for (Slot o : SLOTS) if (o.group.equals(s.group) && Math.abs(o.index - s.index) == 1 && fitted(st, o.i)) return true;
        return false;
    }

    /** A fitted slot that must come off before slot i, such as the bellows on a gangway frame. */
    public static int dependent(String st, int i) {
        String id = SLOTS.get(i).id;
        for (Slot o : SLOTS) if (id.equals(o.after) && fitted(st, o.i)) return o.i;
        return -1;
    }

    /** Can be fitted or taken off a finished carriage: not structure, so gangways, end panels and doors, partitions and the corridor. */
    public static boolean lateFit(Slot s) {
        return !s.required() || s.group().equals("end_panel");
    }

    /** Taken off a finished carriage by a sneak hammer blow. */
    public static boolean detachable(Slot s) {
        String g = s.group();
        return g.startsWith("gangway_") || g.startsWith("lv_") || switch (g) {
            case "partition", "corridor", "end_panel", "jumper", "dynamo", "lamp" -> true;
            default -> false;
        };
    }

    /** Slot i holds a part that is a door: a hinged or sliding leaf. */
    public static boolean isDoor(String st, int i) {
        Slot s = SLOTS.get(i);
        if (s.door < 0) return false;
        Model m = modelAt(st, i);
        return m != null && m.leaf != null && (m.leaf.axis == 'y' || m.leaf.axis == 's');
    }

    /** Slot i is kept empty by something fitted elsewhere. */
    public static boolean blocked(String st, int i) {
        String id = SLOTS.get(i).id;
        for (Slot o : SLOTS) {
            Model m = modelAt(st, o.i);
            if (m != null && m.blocks.contains(id)) return true;
        }
        return false;
    }

    /** Whether slot i could take this model now, with nothing it blocks fitted and nothing blocking it. */
    public static boolean canFit(String st, int i, @Nullable Model m) {
        if (m == null || !canFit(st, i) || blocked(st, i)) return false;
        for (String b : m.blocks) {
            int j = index(b);
            if (j >= 0 && fitted(st, j)) return false;
        }
        return true;
    }

    /** Everything a finished carriage needs is on and fixed. */
    public static boolean finished(String st) {
        for (Phase p : PHASES) if (p.required && !complete(st, p.id)) return false;
        return true;
    }

    /** What is still missing for the hammer to finish it: parts by item, then rivets and blows owed. */
    public static List<ItemStack> missing(String st) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Slot s : SLOTS) {
            Phase p = phase(s.phase);
            if (!s.required || p == null || !p.required || fitted(st, s.i)) continue;
            need.merge(s.models.getFirst().part.asItem(), 1, Integer::sum);
        }
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : need.entrySet()) out.add(new ItemStack(e.getKey(), e.getValue()));
        return out;
    }

    public static int rivetsOwed(String st) {
        int n = 0;
        for (Slot s : SLOTS) {
            Model m = modelAt(st, s.i);
            if (m != null && m.part.fix.equals(RIVETS)) n += m.part.rivets - work(st, s.i);
        }
        return n;
    }

    public static int blowsOwed(String st) {
        int n = 0;
        for (Slot s : SLOTS) {
            Model m = modelAt(st, s.i);
            if (m != null && m.part.fix.equals(BLOW) && work(st, s.i) < 1) n++;
        }
        return n;
    }

    /** The slots a held item could go into right now. */
    public static List<Slot> fitsFor(String st, Item item) {
        List<Slot> l = new ArrayList<>();
        for (Slot s : SLOTS) if (canFit(st, s.i, s.model(item))) l.add(s);
        return l;
    }

    public static boolean isPart(Item item) {
        for (Part p : PARTS.values()) if (p.asItem() == item) return true;
        return false;
    }
}
