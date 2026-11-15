package com.cardgame.parity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/**
 * Test double for {@link RandomGenerator} that returns a fixed script of values.
 *
 * <p>Each scripted step is a pair {@code (expectedBound, value)}. A call to {@link #nextInt(int)} with a
 * different bound fails the test, so the scripts also pin the exact <em>draw order</em> required by the
 * game-engine contract (see "Contract for backend: game engine" in docs/migration-plan.md).
 * Any other random method fails on purpose: the engine must only call {@code nextInt(int bound)}.
 */
final class ScriptedRandom implements RandomGenerator {

    private final Deque<int[]> script = new ArrayDeque<>();

    private ScriptedRandom() {
    }

    /** @param boundValuePairs flat list: bound1, value1, bound2, value2, ... */
    static ScriptedRandom of(int... boundValuePairs) {
        if (boundValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("pairs of (bound, value) expected");
        }
        ScriptedRandom random = new ScriptedRandom();
        for (int i = 0; i < boundValuePairs.length; i += 2) {
            random.script.add(new int[] {boundValuePairs[i], boundValuePairs[i + 1]});
        }
        return random;
    }

    /** Appends more steps (used when a test needs several deals). */
    ScriptedRandom then(int... boundValuePairs) {
        ScriptedRandom more = of(boundValuePairs);
        script.addAll(more.script);
        return this;
    }

    boolean isExhausted() {
        return script.isEmpty();
    }

    int remainingSteps() {
        return script.size();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int[] step = script.poll();
        if (step == null) {
            throw new AssertionError("ScriptedRandom exhausted: unexpected nextInt(" + bound + ")");
        }
        if (step[0] != bound) {
            throw new AssertionError("expected nextInt(" + step[0] + ") but engine called nextInt(" + bound + ")");
        }
        return step[1];
    }

    @Override
    public long nextLong() {
        throw new AssertionError("game-engine contract: only nextInt(int bound) may be used");
    }
}
