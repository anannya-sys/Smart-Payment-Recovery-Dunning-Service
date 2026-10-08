package com.subscription.recovery.gateway;

/**
 * Reproducible pseudo-randomness keyed by <i>what</i> is happening rather than by call order.
 *
 * <p>{@code uniform(seed, customerId, day, SALT)} always returns the same number for the same inputs. So a
 * customer's balance on a given day is the same no matter how many other customers were charged first, which
 * keeps results identical between runs and lets the smart and fixed policies face exactly the same "world".
 *
 * <p>Uses the SplitMix64 finaliser, a well-known fast, high-quality 64-bit mixing function.
 */
public final class DeterministicRandom {

    private DeterministicRandom() {
    }

    /** Uniform double in [0, 1) derived from the given keys. */
    public static double uniform(long... keys) {
        long h = 0x9E3779B97F4A7C15L;
        for (long k : keys) {
            h = mix(h ^ k);
        }
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
