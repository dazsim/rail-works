package com.workshopcraft.railworks.api;

import com.workshopcraft.railworks.trolley.CarriageLayout;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The registry of carriage fixtures. */
public final class CarriageFixtures {
    /** Goes up when something here changes in a way callers must notice. */
    public static final int API_VERSION = 1;

    private static final Map<Identifier, FixtureType> TYPES = new LinkedHashMap<>();
    private static final Map<String, FixtureType> BY_PART = new LinkedHashMap<>();

    private CarriageFixtures() {}

    /**
     * Adds a fixture. Call once during mod setup; the id must be unique. The part item must be registered by
     * the caller under {@link FixtureType#item()}.
     */
    public static synchronized void register(FixtureType type) {
        if (TYPES.containsKey(type.id())) throw new IllegalStateException("carriage fixture " + type.id() + " is already registered");
        String part;
        if (type.model() instanceof FixtureModel.Layout l) {
            part = l.part();
        } else {
            part = type.id().toString();
            CarriageLayout.addFixture(part, type.item(), type.slots(), s -> FixtureModels.model(part, type, s));
        }
        TYPES.put(type.id(), type);
        BY_PART.put(part, type);
    }

    public static @Nullable FixtureType get(Identifier id) {
        return TYPES.get(id);
    }

    public static Collection<FixtureType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** The fixture behind a carriage part id, or null for a plain part such as a bench. */
    public static @Nullable FixtureType forPart(String part) {
        return BY_PART.get(part);
    }
}
