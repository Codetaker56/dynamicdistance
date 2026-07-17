package io.github.codetaker56.dynamicdistance.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceConfigTest {

    @Test
    void defaultsAreValid() {
        DistanceConfig c = new DistanceConfig();
        assertTrue(c.validate().isEmpty(), "shipped defaults must need no correction");
    }

    @Test
    void minAboveMaxIsLowered() {
        DistanceConfig c = new DistanceConfig();
        c.minimumViewDistance = 20;
        c.maximumViewDistance = 8;
        List<String> notes = c.validate();
        assertFalse(notes.isEmpty());
        assertEquals(8, c.minimumViewDistance);
        assertEquals(8, c.maximumViewDistance);
    }

    @Test
    void outOfRangeValuesClamped() {
        DistanceConfig c = new DistanceConfig();
        c.minimumViewDistance = 0;    // below engine floor 2
        c.maximumViewDistance = 99;   // above engine cap 32
        c.validate();
        assertEquals(2, c.minimumViewDistance);
        assertEquals(32, c.maximumViewDistance);
    }

    @Test
    void negativeCooldownClamped() {
        DistanceConfig c = new DistanceConfig();
        c.updateCooldownTicks = -5;
        c.validate();
        assertEquals(0, c.updateCooldownTicks);
    }

    @Test
    void simMinAboveMaxIsLowered() {
        DistanceConfig c = new DistanceConfig();
        c.minimumSimulationDistance = 16;
        c.maximumSimulationDistance = 6;
        c.validate();
        assertEquals(6, c.minimumSimulationDistance);
        assertEquals(6, c.maximumSimulationDistance);
    }

    @Test
    void roundTripThroughProperties() throws Exception {
        DistanceConfig c = new DistanceConfig();
        c.enabled = false;
        c.maximumViewDistance = 20;
        c.minimumViewDistance = 5;
        c.simulationDistanceOffset = -1;
        c.debugLogging = true;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        c.save(out);

        DistanceConfig loaded = new DistanceConfig();
        loaded.load(new ByteArrayInputStream(out.toByteArray()));

        assertFalse(loaded.enabled);
        assertEquals(20, loaded.maximumViewDistance);
        assertEquals(5, loaded.minimumViewDistance);
        assertEquals(-1, loaded.simulationDistanceOffset);
        assertTrue(loaded.debugLogging);
    }

    @Test
    void garbageValuesFallBackToDefaults() throws Exception {
        String junk = "enabled=maybe\nmaximumViewDistance=lots\nminimumViewDistance=5\n";
        DistanceConfig c = new DistanceConfig();
        c.load(new ByteArrayInputStream(junk.getBytes(StandardCharsets.UTF_8)));
        // unparseable values keep their defaults; the good one is applied
        assertTrue(c.enabled);            // default
        assertEquals(32, c.maximumViewDistance); // default
        assertEquals(5, c.minimumViewDistance);  // parsed
    }
}
