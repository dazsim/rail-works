package com.workshopcraft.railworks.api;

import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What a fixture is: its id, the part item that fits it, the slot groups it fits into, how it looks, how many
 * item slots it keeps, a factory for its behaviour, and any power it will use or make.
 *
 * @param slots slot groups it fits; {@link #HALF_BAY} is the only one open to fixtures for now
 */
public record FixtureType(Identifier id, Identifier item, List<String> slots, FixtureModel model, int inventorySize,
                          Function<FixtureContext, Fixture> factory, @Nullable FixturePower power) {
    /** The half bays between the windows, where benches, lockers and stoves go. */
    public static final String HALF_BAY = "bench";

    public FixtureType {
        slots = List.copyOf(slots);
        if (inventorySize < 0 || inventorySize > 54) throw new IllegalArgumentException("inventorySize must be 0 to 54");
    }

    public static Builder builder(Identifier id, Identifier item) {
        return new Builder(id, item);
    }

    public static final class Builder {
        private final Identifier id, item;
        private List<String> slots = List.of(HALF_BAY);
        private FixtureModel model = FixtureModel.boxes().box("STEEL", 1, 0, 0, 15, 14, 10).build();
        private int inventorySize;
        private Function<FixtureContext, Fixture> factory = c -> new Fixture() {};
        private @Nullable FixturePower power;

        Builder(Identifier id, Identifier item) {
            this.id = id;
            this.item = item;
        }

        public Builder slots(String... groups) {
            this.slots = List.of(groups);
            return this;
        }

        public Builder model(FixtureModel model) {
            this.model = model;
            return this;
        }

        public Builder inventorySize(int size) {
            this.inventorySize = size;
            return this;
        }

        public Builder factory(Function<FixtureContext, Fixture> factory) {
            this.factory = factory;
            return this;
        }

        public Builder power(@Nullable FixturePower power) {
            this.power = power;
            return this;
        }

        public FixtureType build() {
            return new FixtureType(id, item, slots, model, inventorySize, factory, power);
        }
    }
}
