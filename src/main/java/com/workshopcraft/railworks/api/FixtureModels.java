package com.workshopcraft.railworks.api;

import com.workshopcraft.railworks.trolley.CarriageLayout;
import java.util.ArrayList;
import java.util.List;

/** Turns an API model into carriage boxes for one half bay. */
final class FixtureModels {
    private FixtureModels() {}

    static CarriageLayout.Model model(String part, FixtureType type, CarriageLayout.Slot s) {
        List<CarriageLayout.Box> solid = new ArrayList<>(), lit = new ArrayList<>();
        CarriageLayout.Part p = CarriageLayout.PARTS.get(part);
        if (type.model() instanceof FixtureModel.Boxes b) {
            for (FixtureModel.Box x : b.boxes()) solid.add(CarriageLayout.bayBox(s, x.zone(), x.x0(), x.y0(), x.z0(), x.x1(), x.y1(), x.z1()));
            for (FixtureModel.Box x : b.lit()) lit.add(CarriageLayout.bayBox(s, x.zone(), x.x0(), x.y0(), x.z0(), x.x1(), x.y1(), x.z1()));
        } else if (type.model() instanceof FixtureModel.Block bl) {
            solid.add(CarriageLayout.bayBox(s, CarriageLayout.BLOCK, 3, 0, 0, 13, 10, 10));
            return new CarriageLayout.Model(part, p, solid, List.of(), lit, List.of(), List.of(), null, blocks(s), bl.state(),
                    CarriageLayout.bounds(solid, List.of(), lit));
        }
        return new CarriageLayout.Model(part, p, solid, List.of(), lit, List.of(), List.of(), null, blocks(s), null, CarriageLayout.bounds(solid, List.of(), lit));
    }

    // a fixture in a half bay keeps the table on that side clear, as the built in ones do
    private static List<String> blocks(CarriageLayout.Slot s) {
        for (CarriageLayout.Model m : s.models()) if (m.name().equals("stove")) return m.blocks();
        return List.of();
    }
}
