package com.svenruppert.flow.views.module02;

/**
 * Reference implementation of {@link Similarity}.
 *
 * <p>Computes cosine similarity in one pass: the two dot products
 * ({@code a.b}, {@code a.a}, {@code b.b}) are accumulated together, then
 * divided. Clamped to {@code [-1, 1]} at the end to keep floating-point
 * drift from pushing the result out of the documented interval.
 */
public final class DefaultSimilarity implements Similarity {

    @Override
    public double cosine(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("cosine: vectors must not be null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "cosine: vectors must have the same length, got " + a.length
                            + " and " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double ai = a[i];
            double bi = b[i];
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        if (normA == 0.0 || normB == 0.0) {
            // Zero-norm vector: documented to return 0.0 rather than NaN.
            return 0.0;
        }
        double cos = dot / Math.sqrt(normA * normB);
        // Clamp floating-point drift back into the documented interval.
        if (cos > 1.0) return 1.0;
        if (cos < -1.0) return -1.0;
        return cos;
    }
}
