package com.workshopcraft.railworks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Server settings from config/railworks.properties, read once at start; both loaders hand in their config folder. */
public final class RailWorksConfig {
    /** Every finished engine runs at line speed with no driver, fuel or water: for testing a long train. */
    public static boolean endlessTrain = false;
    /** Outlines the block that stops a train and says why, for finding what is in the way. */
    public static boolean debugObstacles = false;

    private RailWorksConfig() {}

    public static void load(Path dir) {
        Path f = dir.resolve("railworks.properties");
        Properties p = new Properties();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f)) {
                p.load(r);
            } catch (IOException e) {
                System.err.println("[railworks] could not read " + f + ": " + e);
            }
        }
        endlessTrain = Boolean.parseBoolean(p.getProperty("endlessTrain", "false").trim());
        debugObstacles = Boolean.parseBoolean(p.getProperty("debugObstacles", "false").trim());
        if (!p.containsKey("endlessTrain") || !p.containsKey("debugObstacles")) {
            p.setProperty("endlessTrain", Boolean.toString(endlessTrain));
            p.setProperty("debugObstacles", Boolean.toString(debugObstacles));
            try {
                Files.createDirectories(dir);
                try (Writer w = Files.newBufferedWriter(f)) {
                    p.store(w, "RailWorks. endlessTrain=true: every finished engine runs at full line speed, no driver, fuel or water needed. debugObstacles=true: outline whatever stops a train and say why");
                }
            } catch (IOException e) {
                System.err.println("[railworks] could not write " + f + ": " + e);
            }
        }
    }
}
