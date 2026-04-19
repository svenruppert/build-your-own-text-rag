package com.svenruppert.flow.views.module02;

/**
 * A pairwise similarity metric over dense {@code float[]} vectors.
 *
 * <p>Implementations return a value in the closed interval {@code [-1, 1]};
 * higher means more similar. The metric is symmetric
 * ({@code cosine(a, b) == cosine(b, a)}).
 */
public interface Similarity {

    /**
     * Cosine similarity of two vectors.
     *
     * <p>The result lies in {@code [-1, 1]}: {@code 1.0} for identical
     * direction, {@code 0.0} for orthogonal, {@code -1.0} for opposite
     * direction.
     *
     * <p>If either input has zero norm (the all-zero vector), the result
     * is {@code 0.0} rather than {@code NaN} -- callers never have to
     * special-case {@link Double#isNaN(double)}.
     *
     * @param a first vector, non-{@code null}
     * @param b second vector, non-{@code null}, same length as {@code a}
     * @return cosine similarity, in {@code [-1, 1]}
     * @throws IllegalArgumentException if either vector is {@code null}
     *         or if the two vectors do not have the same length
     */
    double cosine(float[] a, float[] b);
}
