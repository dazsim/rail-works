import com.workshopcraft.railworks.trolley.TrainPower;
import java.util.List;

/** API v2 network rules on TrainPower with stub stores. javac -d /tmp/pc ../../src/main/java/com/workshopcraft/railworks/trolley/TrainPower.java PowerCheck.java && java -cp /tmp/pc PowerCheck */
public class PowerCheck {
    static final class Store implements TrainPower.Node {
        long have, cap;

        Store(long have, long cap) { this.have = have; this.cap = cap; }

        public long stored() { return have; }

        public long space() { return cap - have; }

        public long put(long a, boolean sim) { long n = Math.min(a, cap - have); if (!sim) have += n; return n; }

        public long take(long a, boolean sim) { long n = Math.min(a, have); if (!sim) have -= n; return n; }

        boolean refusedFlag;

        public boolean refused() { return refusedFlag; }

        public void refuse() { refusedFlag = true; }
    }

    static int fails;

    static void is(String what, long got, long want) {
        System.out.println((got == want ? "ok   " : "FAIL ") + what + ": " + got + (got == want ? "" : " want " + want));
        if (got != want) fails++;
    }

    public static void main(String[] a) {
        Store s0 = new Store(5000, 10000), s1 = new Store(0, 10000), s2 = new Store(300, 10000);
        TrainPower t = new TrainPower(List.of(s0, s1, s2));
        is("joined stored", t.stored(), 5300);
        is("joined space", t.space(), 24700);
        is("state OK over 10%", t.state().ordinal(), TrainPower.State.OK.ordinal());
        is("state LOW under 10%", new TrainPower(List.of(new Store(999, 10000))).state().ordinal(), TrainPower.State.LOW.ordinal());
        is("no carriages is NONE", new TrainPower(List.of()).state().ordinal(), TrainPower.State.NONE.ordinal());
        is("full enough is OK", new TrainPower(List.of(new Store(1000, 10000))).state().ordinal(), TrainPower.State.OK.ordinal());
        is("simulated draw", t.draw(2, 1000, true), 1000);
        is("simulate leaves stores", t.stored(), 5300);
        is("draw over network cap is all or nothing", t.draw(2, 1001, false), 0);
        is("draw own then train order", t.draw(2, 1000, false), 1000);
        is("leader gave 700", s0.have, 4300);
        is("own store emptied first", s2.have, 0);
        is("link over what is left refused", t.draw(1, 301, false), 0);
        is("link has 300 left", t.draw(1, 300, false), 300);
        t.tick();
        is("cap resets each tick", t.draw(1, 900, false), 900);
        is("offer fills own first", t.offer(1, 500, false), 500);
        is("own got it", s1.have, 500);
        TrainPower[] p = t.part(0);
        is("part keeps front", p[0].stored(), s0.have);
        is("part keeps rest", p[1].stored(), s1.have + s2.have);
        Store one = new Store(9000, 10000);
        TrainPower single = new TrainPower(List.of(one));
        is("network cap 1000 a tick", single.draw(0, 1000, false), 1000);
        is("network cap spent", single.draw(0, 1, true), 0);
        is("simulated refusal leaves state", single.state().ordinal(), TrainPower.State.OK.ordinal());
        is("refused draw", single.draw(0, 1, false), 0);
        is("refused draw makes LOW", single.state().ordinal(), TrainPower.State.LOW.ordinal());
        is("node keeps the refusal", one.refusedFlag ? 1 : 0, 1);
        one.refusedFlag = false;
        single.tick();
        is("LOW clears next tick", single.state().ordinal(), TrainPower.State.OK.ordinal());
        is("offer cap 1000 a tick", single.offer(0, 5000, false), 1000);
        is("offer cap spent", single.offer(0, 5, false), 0);
        is("offers do not count against draws", single.draw(0, 600, false), 600);
        single.tick();
        is("offer after tick", single.offer(0, 900, false), 900);
        is("draw over cap at once is refused", single.draw(0, 1001, false), 0);
        is("in counted", single.in(0), 900);
        is("offer takes only what fits", new TrainPower(List.of(new Store(9950, 10000))).offer(0, 100, false), 50);
        Store a1 = new Store(0, 10000), a2 = new Store(0, 10000), a3 = new Store(10000, 10000);
        TrainPower three = new TrainPower(List.of(a1, a2, a3));
        is("draw across two links", three.draw(0, 800, false), 800);
        is("middle link shared", three.draw(1, 300, false), 0);
        is("middle link has 200 left", three.draw(1, 200, false), 200);
        three.tick();
        is("offer spreads in train order", three.offer(2, 1000, false), 1000);
        is("own filled first", a3.have, 10000);
        is("in cap shared by the tick", three.offer(2, 1000, true), 0);
        three.tick();
        is("then leader before middle", three.offer(2, 700, false), 700);
        is("leader got it", a1.have, 700);
        Store b0 = new Store(10000, 110000), b1 = new Store(0, 10000);
        TrainPower buf = new TrainPower(List.of(b0, b1));
        is("buffer counts in capacity", buf.stored() + buf.space(), 120000);
        is("10% of buffer is LOW", buf.state().ordinal(), TrainPower.State.LOW.ordinal());
        TrainPower[] cut = buf.part(0);
        is("uncoupled draw fails", cut[1].draw(0, 10, false), 0);
        is("uncoupled store kept", cut[0].stored(), 10000);
        is("failing draw goes LOW", cut[1].state().ordinal(), TrainPower.State.LOW.ordinal());
        is("empty train", new TrainPower(List.of()).draw(0, 1, false), 0);
        System.out.println(fails == 0 ? "all passed" : fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
