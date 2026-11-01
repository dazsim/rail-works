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
    public static final String NONE = "none", BLOW = "blow", RIVETS = "rivets";

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

    public record Leaf(char axis, Vec3 pivot, List<Box> solid, List<Box> glass) {}

    public record Model(String name, Part part, List<Box> solid, List<Box> glass, List<float[]> rivets, List<float[]> seats, @Nullable Leaf leaf) {}

    public record Phase(String id, List<String> needs, boolean required) {}

    public record Slot(int i, String id, String phase, String group, int index, boolean required, boolean chain, List<String> needs,
                       int side, int end, int door, @Nullable Vec3 at, AABB box, List<Model> models) {
        public @Nullable Model model(Item item) {
            for (Model m : models) if (m.part.asItem() == item) return m;
            return null;
        }
    }

    public static final Map<String, Part> PARTS = new LinkedHashMap<>();
    public static final List<Phase> PHASES = new ArrayList<>();
    public static final List<Slot> SLOTS = new ArrayList<>();

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
                    leaf = new Leaf(lo.get("axis").getAsString().charAt(0), vec(lo.getAsJsonArray("pivot")), boxes(lo, "solid"), boxes(lo, "glass"));
                }
                ms.add(new Model(m.getKey(), p, boxes(mo, "solid"), boxes(mo, "glass"), floats(mo, "rivets"), floats(mo, "seats"), leaf));
            }
            JsonArray b = o.getAsJsonArray("box");
            AABB box = new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble());
            SLOTS.add(new Slot(SLOTS.size(), o.get("id").getAsString(), o.get("phase").getAsString(), o.get("group").getAsString(), o.get("index").getAsInt(),
                    o.get("required").getAsBoolean(), o.get("chain").getAsBoolean(), o.has("needs") ? strings(o.getAsJsonArray("needs")) : List.of(),
                    o.has("side") ? o.get("side").getAsInt() : 0, o.has("end") ? o.get("end").getAsInt() : 0, o.has("door") ? o.get("door").getAsInt() : -1,
                    o.has("at") ? vec(o.getAsJsonArray("at")) : null, box, ms));
        }
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
        if (!s.chain) return true;
        int n = 0;
        for (Slot o : SLOTS) if (o.group.equals(s.group)) n++;
        if (s.index == 0 || s.index == n - 1) return true;
        for (Slot o : SLOTS) if (o.group.equals(s.group) && Math.abs(o.index - s.index) == 1 && fitted(st, o.i)) return true;
        return false;
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
        for (Slot s : SLOTS) if (s.model(item) != null && canFit(st, s.i)) l.add(s);
        return l;
    }

    public static boolean isPart(Item item) {
        for (Part p : PARTS.values()) if (p.asItem() == item) return true;
        return false;
    }
}
