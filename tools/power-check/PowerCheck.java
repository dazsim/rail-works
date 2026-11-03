import com.workshopcraft.railworks.trolley.TrainPower;
import java.util.List;

/** Topology checks for TrainPower with stub stores. javac -d /tmp/pc TrainPower.java PowerCheck.java && java -cp /tmp/pc PowerCheck */
public class PowerCheck {
    static final class Store implements TrainPower.Node {
        long have, cap;

        Store(long have, long cap) { this.have = have; this.cap = cap; }

        public long stored() { return have; }

        public long space() { return cap - have; }

        public long put(long a, boolean sim) { long n = Math.min(a, cap - have); if (!sim) have += n; return n; }

        public long take(long a, boolean sim) { long n = Math.min(a, have); if (!sim) have -= n; return n; }
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
        is("state stays NONE", t.state().ordinal(), TrainPower.State.NONE.ordinal());
        is("simulated draw", t.draw(2, 1200, true), 1200);
        is("simulate leaves stores", t.stored(), 5300);
        is("draw over link cap is all or nothing", t.draw(2, 1400, false), 0);
        is("draw own then train order", t.draw(2, 1300, false), 1300);
        is("leader gave 1000", s0.have, 4000);
        is("own store emptied first", s2.have, 0);
        is("link spent this tick", t.draw(1, 1, false), 0);
        t.tick();
        is("cap resets each tick", t.draw(1, 900, false), 900);
        is("offer fills own first", t.offer(1, 500, false), 500);
        is("own got it", s1.have, 500);
        TrainPower[] p = t.part(0);
        is("part keeps front", p[0].stored(), s0.have);
        is("part keeps rest", p[1].stored(), s1.have + s2.have);
        is("single carriage no cap", new TrainPower(List.of(new Store(5000, 5000))).draw(0, 5000, false), 5000);
        is("empty train", new TrainPower(List.of()).draw(0, 1, false), 0);
        System.out.println(fails == 0 ? "all passed" : fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
