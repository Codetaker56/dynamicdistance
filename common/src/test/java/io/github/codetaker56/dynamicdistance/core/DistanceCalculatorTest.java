package io.github.codetaker56.dynamicdistance.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceCalculatorTest {

    private static DistanceConfig cfg(int minV, int maxV, int minS, int maxS, int offset) {
        DistanceConfig c = new DistanceConfig();
        c.minimumViewDistance = minV;
        c.maximumViewDistance = maxV;
        c.minimumSimulationDistance = minS;
        c.maximumSimulationDistance = maxS;
        c.simulationDistanceOffset = offset;
        c.validate();
        return c;
    }

    // ---- view distance ----

    @Test
    void viewMatchesRequestWhenInRange() {
        DistanceConfig c = cfg(2, 32, 2, 32, 0);
        // server max 16, player asks 12 -> 12
        assertEquals(12, DistanceCalculator.effectiveView(12, 16, DistanceCalculator.NO_OVERRIDE, c));
        // player asks 6 -> 6
        assertEquals(6, DistanceCalculator.effectiveView(6, 16, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void viewCappedByServerMax() {
        DistanceConfig c = cfg(2, 32, 2, 32, 0);
        // player asks 32, server max 16 -> 16 (example C in the spec)
        assertEquals(16, DistanceCalculator.effectiveView(32, 16, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void viewCappedByConfigMax() {
        DistanceConfig c = cfg(2, 10, 2, 32, 0);
        // config max 10 tighter than server max 16
        assertEquals(10, DistanceCalculator.effectiveView(16, 16, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void viewFlooredByConfigMin() {
        DistanceConfig c = cfg(8, 32, 2, 32, 0);
        // player asks 4 but minimum is 8 -> 8
        assertEquals(8, DistanceCalculator.effectiveView(4, 16, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void serverMaxBelowConfigMinStillNeverExceedsServer() {
        DistanceConfig c = cfg(10, 32, 2, 32, 0);
        // config wants a floor of 10 but the server only allows 6 -> 6 (can't send more than the server)
        assertEquals(6, DistanceCalculator.effectiveView(12, 6, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void viewOverrideWins() {
        DistanceConfig c = cfg(2, 32, 2, 32, 0);
        // override 9 ignores the client's request of 32
        assertEquals(9, DistanceCalculator.effectiveView(32, 16, 9, c));
        // override still capped by server max
        assertEquals(16, DistanceCalculator.effectiveView(4, 16, 20, c));
    }

    // ---- simulation distance ----

    @Test
    void simDefaultsToMinOfViewAndMax() {
        DistanceConfig c = cfg(2, 32, 2, 12, 0);
        // view 8, sim max 12 -> 8
        assertEquals(8, DistanceCalculator.effectiveSimulation(8, DistanceCalculator.NO_OVERRIDE, c));
        // view 16, sim max 12 -> 12
        assertEquals(12, DistanceCalculator.effectiveSimulation(16, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void simRespectsOffset() {
        DistanceConfig c = cfg(2, 32, 2, 32, -2);
        // view 10, offset -2 -> 8
        assertEquals(8, DistanceCalculator.effectiveSimulation(10, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void simFlooredByMin() {
        DistanceConfig c = cfg(2, 32, 6, 32, 0);
        // view 3 -> floored to sim min 6
        assertEquals(6, DistanceCalculator.effectiveSimulation(3, DistanceCalculator.NO_OVERRIDE, c));
    }

    @Test
    void simOverrideWinsButClamped() {
        DistanceConfig c = cfg(2, 32, 2, 12, 0);
        assertEquals(10, DistanceCalculator.effectiveSimulation(4, 10, c));
        // override above sim max clamps to max
        assertEquals(12, DistanceCalculator.effectiveSimulation(4, 20, c));
    }
}
