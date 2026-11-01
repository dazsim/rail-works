package com.workshopcraft.railworks.track;

import com.workshopcraft.railworks.block.GroundFrameBlock;
import com.workshopcraft.railworks.block.StakeShapes;
import com.workshopcraft.railworks.block.TrackNodeBlock;
import com.workshopcraft.railworks.registry.ModContent;
import java.util.BitSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What the hammer, theodolite, ties, rail bars and buffer stops do to the track graph. */
public final class TrackActions {
    private static final Map<UUID, BlockPos> SELECTED = new HashMap<>();
    private static final Map<UUID, Long> SELECTED_AT = new HashMap<>();
    private static final long SELECT_TIMEOUT = 1200;
    private static final double TURN = Math.toRadians(2);

    private static final long WARN_EVERY = 40, WARN_FOR = 1200;
    private static final double WARN_REACH = 10;

    private record Warning(ServerLevel level, long seg, int index, Vec3 at, Component text, long until) {}

    private static final Map<UUID, Warning> WARNINGS = new java.util.concurrent.ConcurrentHashMap<>();

    private TrackActions() {}

    /** Re-shows a ground check failure while the player stays near the failed spot with ties in hand. */
    public static void tickWarnings(ServerLevel level) {
        long now = level.getGameTime();
        if (now % WARN_EVERY != 0 || WARNINGS.isEmpty()) return;
        TrackGraph g = TrackGraph.get(level);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            Warning w = WARNINGS.get(p.getUUID());
            if (w == null || w.level() != level) continue;
            TrackGraph.Segment s = g.segment(w.seg());
            boolean keep = now < w.until() && s != null && !s.live && w.index() < s.n
                    && s.unsupported.get(w.index()) && !s.ties.get(w.index())
                    && p.getMainHandItem().is(ModContent.TIE)
                    && p.position().distanceTo(w.at()) <= WARN_REACH;
            if (keep) p.sendOverlayMessage(w.text());
            else WARNINGS.remove(p.getUUID());
        }
    }

    public static double headingFromYaw(float yaw) {
        double deg = yaw + 90;
        double snap = Math.round(deg / 45) * 45.0;
        if (Math.abs(deg - snap) <= 3) deg = snap;
        return Math.toRadians(deg);
    }

    private static String f1(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    public static MutableComponent gradeText(double g) {
        if (Math.abs(g) < 1e-4) return Component.translatable("railworks.track.level");
        return Component.translatable("railworks.track.grade", Math.round(1 / Math.abs(g)));
    }

    public static MutableComponent radiusText(double r) {
        if (Double.isInfinite(r) || r > 1e5) return Component.translatable("railworks.track.straight");
        return Component.literal("R" + Math.round(r));
    }

    /** Length, grade and radius, white when fine, amber when steep, red when the survey fails. */
    public static MutableComponent describe(TrackPath p) {
        MutableComponent c = Component.translatable("railworks.track.readout", f1(p.length), gradeText(p.grade()), radiusText(p.minRadius()));
        if (p.tooTight()) c.append(Component.translatable("railworks.track.too_tight"));
        if (p.tooSteep()) c.append(Component.translatable("railworks.track.too_steep"));
        else if (p.steep()) c.append(Component.translatable("railworks.track.steep"));
        return c.withStyle(p.fails() ? ChatFormatting.RED : p.steep() ? ChatFormatting.GOLD : ChatFormatting.WHITE);
    }

    private static MutableComponent progress(TrackGraph g, TrackGraph.Segment s, Player player) {
        MutableComponent c = describe(s.path);
        if (s.locked) c.append(Component.translatable("railworks.track.locked_tag").withStyle(ChatFormatting.AQUA));
        if (s.live) c.append(Component.translatable("railworks.track.live").withStyle(ChatFormatting.GREEN));
        if (s.live && (openEnd(g.node(s.a)) || openEnd(g.node(s.b))))
            c.append(Component.translatable("railworks.track.continue_hint").withStyle(ChatFormatting.GRAY));
        else c.append(Component.translatable("railworks.track.progress", s.ties.cardinality(), s.n, s.rails.cardinality(), s.n));
        if (!s.live) {
            Vec3 m = nearestMissing(s, player.position());
            if (m != null) {
                double dx = m.x - player.getX(), dz = m.z - player.getZ();
                int d = (int) Math.round(Math.hypot(dx, dz));
                boolean tie = s.ties.cardinality() < s.n && !s.ties.isEmpty();
                c.append(Component.translatable(tie ? "railworks.track.missing_tie" : "railworks.track.missing_rail", d,
                        Component.translatable("railworks.compass." + compass(dx, dz))).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (!s.live && !s.blocked().isEmpty())
            c.append(Component.translatable("railworks.track.blocked", s.blocked().size()).withStyle(ChatFormatting.RED));
        if (!s.live && s.unsupportedFree() > 0) {
            BitSet b = (BitSet) s.unsupported.clone();
            b.andNot(s.ties);
            Vec3 p = spot(new TrackGraph.Spot(s, b.nextSetBit(0), 0));
            c.append(Component.translatable("railworks.track.over_air", s.unsupportedFree(),
                    (int) Math.floor(p.x), (int) Math.floor(p.y - 0.3), (int) Math.floor(p.z)).withStyle(ChatFormatting.RED));
        }
        return c;
    }

    /**
     * The nearest gap in a partly laid section: a missing tie if ties are partly down, else a missing rail if rails
     * are partly down. Null when the section is untouched, finished or only one kind is wholly missing.
     */
    static @Nullable Vec3 nearestMissing(TrackGraph.Segment s, Vec3 from) {
        BitSet have;
        if (!s.ties.isEmpty() && s.ties.cardinality() < s.n) have = s.ties;
        else if (!s.rails.isEmpty() && s.rails.cardinality() < s.n) have = s.rails;
        else return null;
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (int i = have.nextClearBit(0); i < s.n; i = have.nextClearBit(i + 1)) {
            Vec3 p = spot(new TrackGraph.Spot(s, i, 0));
            double d = p.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** One of eight compass points for a horizontal offset, as a lang key suffix. */
    static String compass(double dx, double dz) {
        double b = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(b / 45.0), 8);
        return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[i];
    }

    /** Set RAILWORKS_ECHO in the server's environment to copy hammer readouts to the log, for testing. */
    private static final boolean ECHO = System.getenv("RAILWORKS_ECHO") != null;

    private static void say(Player p, Component c) {
        if (ECHO) org.slf4j.LoggerFactory.getLogger("railworks").info("say: {}", c.getString());
        WARNINGS.remove(p.getUUID());
        p.sendOverlayMessage(c);
    }

    private static void give(Player p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    public static InteractionResult hammerNode(ServerLevel level, Player player, BlockPos pos, BlockState state, BlockHitResult hit) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Node n = g.nodeFor(level, pos);
        boolean sneak = player.isShiftKeyDown();
        if (!state.getValue(TrackNodeBlock.STAKE)) {
            // a hit at the toe throws the points, as a hit beside it does
            if (sneak || (n != null && g.pointsPort(n) >= 0)) return hammerGround(level, player, hit.getLocation());
            select(level, player, g, n);
            return InteractionResult.SUCCESS;
        }
        if (!sneak) {
            select(level, player, g, n);
            return InteractionResult.SUCCESS;
        }
        if (g.anyLaid(n)) {
            say(player, Component.translatable("railworks.track.take_up_first").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        Direction face = hit.getDirection();
        Vec3 at = hit.getLocation();
        double hy = at.y - pos.getY();
        boolean ok;
        MutableComponent what;
        if (face == Direction.UP || (face != Direction.DOWN && hy >= StakeShapes.TOP / 2)) {
            int d = face == Direction.UP ? -1 : 1;
            if (n.lift + d < TrackGraph.MIN_LIFT || n.lift + d > TrackGraph.MAX_LIFT) {
                say(player, Component.translatable(d < 0 ? "railworks.track.lowest" : "railworks.track.highest").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            ok = g.adjust(level, n, d, 0);
            what = Component.translatable("railworks.track.height", (n.lift >= 0 ? "+" : "") + n.lift + "/8");
        } else {
            if (g.lockedAt(n)) {
                say(player, Component.translatable("railworks.track.locked_turn").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            boolean right = hitsRight(player, pos, at);
            ok = g.adjust(level, n, 0, right ? TURN : -TURN);
            double bearing = Math.toDegrees(Math.atan2(Math.cos(n.heading), -Math.sin(n.heading)));
            what = Component.translatable(right ? "railworks.track.turned_right" : "railworks.track.turned_left", f1((bearing + 360) % 360));
        }
        if (!ok) {
            say(player, Component.translatable("railworks.track.no_fit").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.5F);
        if (!n.segs.isEmpty()) {
            TrackGraph.Segment s = g.segment(n.segs.get(0));
            if (s != null) what.append(Component.literal(": ")).append(describe(s.path));
        }
        say(player, what);
        return InteractionResult.SUCCESS;
    }

    /**
     * Which half of the stake the player hit, as they see it: true for the right half. Taken from the hit point's offset
     * from the stake's centre line across the player's line of sight, so it does not depend on which face of the outline
     * was hit. A right half hit turns the stake clockwise seen from above (to the right), a left half hit anticlockwise.
     */
    static boolean hitsRight(Player player, BlockPos pos, Vec3 at) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        double dx = cx - player.getX(), dz = cz - player.getZ();
        double rx = at.x - cx, rz = at.z - cz;
        return dx * rz - dz * rx >= 0;
    }

    /** A hidden node with one section and no buffer: track can be continued from here. */
    public static boolean openEnd(TrackGraph.@Nullable Node n) {
        return n != null && !n.stake && !n.buffer && n.segs.size() == 1;
    }

    /** Turn a track end back into a stake so the hammer can link a new section from it. */
    public static void restake(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Node n = g.nodeFor(level, pos);
        n.stake = true;
        level.setBlock(pos, state.setValue(TrackNodeBlock.STAKE, true), 3);
        g.changed(level, n);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, 1);
        say(player, Component.translatable("railworks.track.end_staked"));
    }

    /** A stake placed right by an open track end goes on the end instead, unless the player sneaks. */
    public static boolean joinNearEnd(ServerLevel level, Player player, BlockPos placed) {
        if (player.isShiftKeyDown()) return false;
        TrackGraph g = TrackGraph.get(level);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    m.setWithOffset(placed, dx, dy, dz);
                    TrackGraph.Node n = g.nodeAt(m);
                    if (!openEnd(n)) continue;
                    BlockState st = level.getBlockState(n.pos);
                    if (!st.is(ModContent.TRACK_NODE)) continue;
                    level.removeBlock(placed, false);
                    restake(level, player, n.pos, st);
                    return true;
                }
        return false;
    }

    private static void select(ServerLevel level, Player player, TrackGraph g, TrackGraph.Node n) {
        BlockPos sel = SELECTED.get(player.getUUID());
        long now = level.getGameTime();
        if (sel != null && now - SELECTED_AT.getOrDefault(player.getUUID(), 0L) > SELECT_TIMEOUT) sel = null;
        SELECTED_AT.put(player.getUUID(), now);
        TrackGraph.Node a = sel == null ? null : g.nodeAt(sel);
        if (a != null && !a.stake) a = null;
        level.playSound(null, n.pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.0F);
        if (a == null) {
            SELECTED.put(player.getUUID(), n.pos);
            say(player, Component.translatable("railworks.track.selected").append(sections(g, n)));
            return;
        }
        if (a == n) {
            SELECTED.remove(player.getUUID());
            say(player, Component.translatable("railworks.track.cleared").append(sections(g, n)));
            return;
        }
        TrackGraph.LinkResult r = g.link(level, a, n);
        if (r == TrackGraph.LinkResult.EXISTS) {
            SELECTED.put(player.getUUID(), n.pos);
            say(player, Component.translatable("railworks.track.reselected").append(sections(g, n)));
            return;
        }
        if (r != TrackGraph.LinkResult.OK) {
            say(player, Component.translatable("railworks.track.link." + r.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.RED));
            return;
        }
        SELECTED.put(player.getUUID(), n.pos);
        for (long id : n.segs) {
            TrackGraph.Segment s = g.segment(id);
            if (s != null && s.other(n.id) == a.id) {
                say(player, Component.translatable("railworks.track.linked").append(progress(g, s, player)));
                break;
            }
        }
    }

    public static InteractionResult hammerGround(ServerLevel level, Player player, Vec3 at) {
        TrackGraph g = TrackGraph.get(level);
        boolean sneak = player.isShiftKeyDown();
        if (ECHO) org.slf4j.LoggerFactory.getLogger("railworks").info("hammer ground at {} sneak {}", at, sneak);
        if (!sneak) {
            TrackGraph.Node toe = nearestNode(g, at, 3.5, true, false);
            if (toe == null) toe = nearestNode(g, player.position(), HAMMER_REACH, true, false);
            if (toe != null) {
                throwPoints(level, g, toe);
                player.getCooldowns().addCooldown(player.getMainHandItem(), 8);
                MutableComponent msg = Component.translatable("railworks.track.points", toe.route == 0 ? "A" : "B");
                TrackGraph.Spot near = g.nearestSegment(at.x, at.y, at.z, 4);
                if (near != null && !near.seg().live) {
                    g.refresh(level, near.seg());
                    g.changed(level, g.node(near.seg().a));
                    g.changed(level, g.node(near.seg().b));
                    msg.append(". ").append(progress(g, near.seg(), player));
                }
                say(player, msg);
                return InteractionResult.SUCCESS;
            }
            TrackGraph.Spot s = g.nearestSegment(at.x, at.y, at.z, 4);
            if (s == null) {
                say(player, whyNoPoints(g, player.position(), HAMMER_REACH, false));
                return InteractionResult.PASS;
            }
            g.refresh(level, s.seg());
            g.changed(level, g.node(s.seg().a));
            g.changed(level, g.node(s.seg().b));
            say(player, progress(g, s.seg(), player));
            return InteractionResult.SUCCESS;
        }
        if (com.workshopcraft.railworks.trolley.RailVehicle.hammerNear(level, player, at)) return InteractionResult.SUCCESS;
        TrackGraph.Node buf = nearestNode(g, at, 3, false, true);
        if (buf != null) {
            buf.buffer = false;
            g.changed(level, buf);
            give(player, new ItemStack(ModContent.BUFFER_STOP));
            level.playSound(null, buf.pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 1.6F);
            return InteractionResult.SUCCESS;
        }
        boolean rail = true;
        TrackGraph.Spot s = g.nearestLaid(at.x, at.y, at.z, 3, true);
        if (s == null) {
            rail = false;
            s = g.nearestLaid(at.x, at.y, at.z, 3, false);
        }
        if (s == null) {
            TrackGraph.Spot line = g.nearestSegment(at.x, at.y, at.z, 3);
            if (line == null) {
                say(player, Component.translatable("railworks.track.nothing_to_take"));
                return InteractionResult.PASS;
            }
            TrackGraph.LockResult r = g.toggleLock(level, line.seg());
            boolean good = r == TrackGraph.LockResult.LOCKED || r == TrackGraph.LockResult.UNLOCKED;
            level.playSound(null, at.x, at.y, at.z, good ? SoundEvents.CHAIN_PLACE : SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, good ? 1.4F : 0.8F);
            MutableComponent msg = Component.translatable("railworks.track.lock." + r.name().toLowerCase(java.util.Locale.ROOT));
            if (good) say(player, msg.withStyle(ChatFormatting.AQUA).append(". ").append(progress(g, line.seg(), player)));
            else say(player, msg.withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        (rail ? s.seg().rails : s.seg().ties).clear(s.index());
        int back = 1;
        if (rail && s.seg().extra.get(s.index())) {
            s.seg().extra.clear(s.index());
            back++;
        }
        g.laid(level, s.seg());
        give(player, new ItemStack(rail ? ModContent.RAIL_BAR : ModContent.TIE, back));
        Vec3 p = spot(s);
        level.playSound(null, p.x, p.y, p.z, rail ? SoundEvents.CHAIN_BREAK : SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 1.1F);
        say(player, progress(g, s.seg(), player));
        return InteractionResult.SUCCESS;
    }

    private static Vec3 spot(TrackGraph.Spot s) {
        double t = s.seg().tieS(s.index());
        double[] p = s.seg().path.pose(t);
        return new Vec3(p[0], s.seg().baseY + s.seg().path.height(t) + 0.3, p[1]);
    }

    private static TrackGraph.@Nullable Node nearestNode(TrackGraph g, Vec3 at, double reach, boolean points, boolean buffer) {
        TrackGraph.Node best = null;
        double bd = reach;
        for (TrackGraph.Node n : g.nodes()) {
            if (points && g.pointsPort(n) < 0) continue;
            if (buffer && !n.buffer) continue;
            double d = Math.hypot(n.x() - at.x, n.z() - at.z);
            if (d <= bd && Math.abs(n.y() - at.y) < 4) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    /** How far from a set of points' toe the player can stand and still throw them with the hammer. */
    public static final double HAMMER_REACH = 6;

    /** The sections at a node and how far each is laid, for the hammer readout. */
    static MutableComponent sections(TrackGraph g, TrackGraph.Node n) {
        MutableComponent c = Component.literal(". ");
        if (n.segs.isEmpty()) return c.append(Component.translatable("railworks.track.sec_none"));
        c.append(Component.translatable("railworks.track.sec_count", n.segs.size()));
        boolean first = true;
        for (long id : n.segs) {
            TrackGraph.Segment s = g.segment(id);
            if (s == null) continue;
            c.append(first ? " " : ", ");
            first = false;
            TrackGraph.Node o = g.node(s.other(n.id));
            String to = o == null ? "?" : bearing(Math.atan2(o.z() - n.z(), o.x() - n.x()));
            int len = (int) Math.round(s.path.length);
            if (s.live) c.append(Component.translatable("railworks.track.sec_live", len, to).withStyle(ChatFormatting.GREEN));
            else if (s.empty()) c.append(Component.translatable("railworks.track.sec_empty", len, to).withStyle(ChatFormatting.GOLD));
            else c.append(Component.translatable("railworks.track.sec_part", len, to, s.ties.cardinality(), s.n, s.rails.cardinality(), s.n));
        }
        return c;
    }

    /** Compass bearing of a heading, 0 north and 90 east, as a whole number of degrees. */
    static String bearing(double h) {
        double b = Math.toDegrees(Math.atan2(Math.cos(h), -Math.sin(h)));
        return String.valueOf((int) Math.round((b % 360 + 360) % 360));
    }

    /** Explain why there are no points to work near a spot, so the frame and the hammer always say something. */
    public static MutableComponent whyNoPoints(TrackGraph g, Vec3 at, double reach, boolean frame) {
        TrackGraph.Node join = null, side = null, far = null;
        double jd = reach, sd = reach, fd = 64;
        for (TrackGraph.Node n : g.nodes()) {
            if (Math.abs(n.y() - at.y) >= 4 || n.segs.isEmpty()) continue;
            double d = Math.hypot(n.x() - at.x, n.z() - at.z);
            if (g.pointsPort(n) >= 0) {
                if (d < fd) {
                    fd = d;
                    far = n;
                }
                continue;
            }
            if (d > reach) continue;
            if (n.segs.size() >= 2 && d < jd && bends(g, n)) {
                jd = d;
                join = n;
            }
            if (d < sd && besideAnother(g, n)) {
                sd = d;
                side = n;
            }
        }
        if (join != null)
            return Component.translatable("railworks.track.not_points", join.pos.getX(), join.pos.getY(), join.pos.getZ()).withStyle(ChatFormatting.RED);
        if (side != null)
            return Component.translatable("railworks.track.two_stakes", side.pos.getX(), side.pos.getY(), side.pos.getZ()).withStyle(ChatFormatting.RED);
        if (far != null)
            return Component.translatable(frame ? "railworks.track.frame_far" : "railworks.track.points_far", (int) Math.round(fd),
                    far.pos.getX(), far.pos.getY(), far.pos.getZ(), (int) reach).withStyle(ChatFormatting.RED);
        return Component.translatable(frame ? "railworks.track.no_points" : "railworks.track.nothing_here").withStyle(ChatFormatting.RED);
    }

    /** Two sections leave the node on opposite sides but not in a straight line, so it looks like a split. */
    private static boolean bends(TrackGraph g, TrackGraph.Node n) {
        java.util.List<Double> out = new java.util.ArrayList<>();
        for (long id : n.segs) {
            TrackGraph.Segment s = g.segment(id);
            if (s != null) out.add(TrackGraph.outward(s, n.id));
        }
        for (int i = 0; i < out.size(); i++)
            for (int j = i + 1; j < out.size(); j++)
                if (Math.cos(out.get(i) - out.get(j)) > Math.cos(Math.toRadians(170))) return true;
        return false;
    }

    /** Another node with track sits right beside this one without a section between them. */
    private static boolean besideAnother(TrackGraph g, TrackGraph.Node n) {
        for (TrackGraph.Node m : g.nodes()) {
            if (m == n || m.segs.isEmpty() || Math.abs(m.y() - n.y()) >= 2) continue;
            if (Math.hypot(m.x() - n.x(), m.z() - n.z()) > 3) continue;
            boolean linked = false;
            for (long id : n.segs) {
                TrackGraph.Segment s = g.segment(id);
                if (s != null && s.other(n.id) == m.id) linked = true;
            }
            if (!linked) return true;
        }
        return false;
    }

    public static void throwPoints(ServerLevel level, TrackGraph g, TrackGraph.Node toe) {
        g.throwPoints(level, toe);
        level.playSound(null, toe.pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, 1.4F);
        updateFrames(level, toe);
    }

    public static void updateFrames(ServerLevel level, TrackGraph.Node toe) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = GroundFrameBlock.REACH;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -4; dy <= 4; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    m.setWithOffset(toe.pos, dx, dy, dz);
                    BlockState s = level.getBlockState(m);
                    if (s.is(ModContent.GROUND_FRAME) && s.getValue(GroundFrameBlock.REVERSED) != (toe.route == 1))
                        level.setBlock(m, s.setValue(GroundFrameBlock.REVERSED, toe.route == 1), 3);
                }
    }

    public static TrackGraph.@Nullable Node nearestPoints(ServerLevel level, BlockPos pos) {
        return nearestNode(TrackGraph.get(level), Vec3.atCenterOf(pos), GroundFrameBlock.REACH, true, false);
    }

    private static Vec3 reachPoint(Player player) {
        Vec3 look = player.getLookAngle();
        double h = Math.hypot(look.x, look.z);
        if (h < 1e-4) return player.position();
        return player.position().add(look.x / h * 1.5, 0, look.z / h * 1.5);
    }

    /** Lay ties or rail bars on the nearest ghosts in front of the player. */
    public static InteractionResult lay(ServerLevel level, Player player, net.minecraft.world.InteractionHand hand, boolean rails) {
        ItemStack stack = player.getItemInHand(hand);
        TrackGraph g = TrackGraph.get(level);
        Vec3 at = reachPoint(player);
        int laid = 0, dropped = 0;
        TrackGraph.Segment last = null;
        while (laid < 2 && !stack.isEmpty()) {
            TrackGraph.Spot s = g.nearestFree(at.x, at.y, at.z, 3.0, rails);
            if (s == null) break;
            BlockPos in = rails ? null : g.buried(level, s.seg(), s.index());
            if (in != null) {
                g.refresh(level, s.seg());
                g.changed(level, g.node(s.seg().a));
                g.changed(level, g.node(s.seg().b));
                say(player, Component.translatable("railworks.track.buried", level.getBlockState(in).getBlock().getName(), in.getX(), in.getY(), in.getZ())
                        .withStyle(ChatFormatting.RED));
                return laid > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (!rails && !g.supported(level, s.seg(), s.index())) {
                s.seg().unsupported.set(s.index());
                g.changed(level, g.node(s.seg().a));
                g.changed(level, g.node(s.seg().b));
                Vec3 p = spot(s);
                Component warn = Component.translatable("railworks.track.unsupported", (int) Math.floor(p.x), (int) Math.floor(p.y - 0.3), (int) Math.floor(p.z))
                        .withStyle(ChatFormatting.RED);
                say(player, warn);
                WARNINGS.put(player.getUUID(), new Warning(level, s.seg().id, s.index(), p, warn, level.getGameTime() + WARN_FOR));
                return laid > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            int extra = rails ? g.extraBars(s.seg(), s.index()) : 0;
            if (extra > 0 && stack.getCount() < 1 + extra && !player.hasInfiniteMaterials()) {
                say(player, Component.translatable("railworks.track.crossing_bars", 1 + extra).withStyle(ChatFormatting.RED));
                return laid > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            s.seg().unsupported.clear(s.index());
            (rails ? s.seg().rails : s.seg().ties).set(s.index());
            if (extra > 0) s.seg().extra.set(s.index());
            stack.consume(1 + extra, player);
            laid++;
            last = s.seg();
            Vec3 p = spot(s);
            level.playSound(null, p.x, p.y, p.z, rails ? SoundEvents.CHAIN_PLACE : SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F,
                    0.9F + level.getRandom().nextFloat() * 0.2F);
            g.laid(level, s.seg());
            if (s.seg().live) {
                for (TrackGraph.Node n : g.stakesToReturn(s.seg())) {
                    dropped += g.dropEmpty(level, n);
                    n.stake = false;
                    BlockState st = level.getBlockState(n.pos);
                    if (st.is(ModContent.TRACK_NODE)) level.setBlock(n.pos, st.setValue(TrackNodeBlock.STAKE, false), 3);
                    g.changed(level, n);
                    give(player, new ItemStack(ModContent.SURVEY_STAKE));
                }
                level.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.4F);
            }
        }
        if (laid > 0) {
            player.swing(hand, true);
            MutableComponent msg = progress(g, last, player);
            if (dropped > 0) msg.append(Component.translatable("railworks.track.dropped_strays", dropped).withStyle(ChatFormatting.GOLD));
            say(player, msg);
            return InteractionResult.SUCCESS;
        }
        TrackGraph.Spot near = g.nearestSegment(at.x, at.y, at.z, 4);
        if (near != null) {
            if (near.seg().path.fails()) say(player, Component.translatable("railworks.track.fails").withStyle(ChatFormatting.RED));
            else if (rails && near.seg().ties.cardinality() < near.seg().n) say(player, Component.translatable("railworks.track.ties_first"));
            else say(player, progress(g, near.seg(), player));
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult fitBuffer(ServerLevel level, Player player, ItemStack stack) {
        TrackGraph g = TrackGraph.get(level);
        Vec3 at = player.pick(5, 1, false).getLocation();
        TrackGraph.Node best = null;
        double bd = 4;
        for (TrackGraph.Node n : g.nodes()) {
            if (n.buffer || n.segs.size() != 1) continue;
            double d = Math.hypot(n.x() - at.x, n.z() - at.z);
            if (d <= bd && Math.abs(n.y() - at.y) < 4) {
                bd = d;
                best = n;
            }
        }
        if (best == null) {
            say(player, Component.translatable("railworks.track.buffer_where"));
            return InteractionResult.PASS;
        }
        best.buffer = true;
        g.changed(level, best);
        stack.consume(1, player);
        level.playSound(null, best.pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.2F);
        say(player, Component.translatable("railworks.track.buffer_fitted"));
        return InteractionResult.SUCCESS;
    }

    /** The theodolite: sight a stake and read the line to it from the stake beside you. */
    public static void sight(ServerLevel level, Player player) {
        HitResult hr = player.pick(128, 1, false);
        if (!(hr instanceof BlockHitResult bh) || !level.getBlockState(bh.getBlockPos()).is(ModContent.TRACK_NODE)) {
            say(player, Component.translatable("railworks.track.sight_stake").withStyle(ChatFormatting.GRAY));
            return;
        }
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Node t = g.nodeFor(level, bh.getBlockPos());
        TrackGraph.Node near = null;
        double bd = 12;
        for (TrackGraph.Node n : g.nodes()) {
            if (n == t) continue;
            double d = player.position().distanceTo(new Vec3(n.x(), n.y(), n.z()));
            if (d < bd) {
                bd = d;
                near = n;
            }
        }
        if (near != null) {
            for (long id : near.segs) {
                TrackGraph.Segment s = g.segment(id);
                if (s != null && s.other(near.id) == t.id) {
                    say(player, Component.translatable("railworks.track.sighted").append(describe(s.path)));
                    return;
                }
            }
            double chord = Math.hypot(t.x() - near.x(), t.z() - near.z()), rise = t.y() - near.y();
            double grade = chord < 1e-3 ? 0 : rise / chord;
            boolean bad = chord > TrackPath.MAX_SPAN || Math.abs(grade) > TrackPath.MAX_GRADE;
            MutableComponent c = Component.translatable("railworks.track.unlinked_reading", f1(chord), f1(rise), gradeText(grade));
            if (chord > TrackPath.MAX_SPAN) c.append(", ").append(Component.translatable("railworks.track.link.too_far"));
            say(player, c.withStyle(bad ? ChatFormatting.RED : Math.abs(grade) > TrackPath.STEEP_GRADE ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            return;
        }
        if (!t.segs.isEmpty()) {
            TrackGraph.Segment s = g.segment(t.segs.get(0));
            if (s != null) {
                say(player, Component.translatable("railworks.track.sighted").append(describe(s.path)));
                return;
            }
        }
        say(player, Component.translatable("railworks.track.stand_by_stake").withStyle(ChatFormatting.GRAY));
    }

    public static boolean isTrackItem(Item item) {
        return item == ModContent.TIE || item == ModContent.RAIL_BAR;
    }
}
