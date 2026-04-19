package com.svenruppert.flow.views.module02;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSimilarityTest {

    private static final double EPSILON = 1.0e-9;

    private final Similarity similarity = new DefaultSimilarity();

    @Test
    @DisplayName("identical vectors return cosine 1.0")
    void identical_vectors_return_one() {
        float[] v = {1.0f, 2.0f, -3.0f, 0.5f};
        assertEquals(1.0, similarity.cosine(v, v.clone()), EPSILON);
    }

    @Test
    @DisplayName("orthogonal vectors return cosine 0.0")
    void orthogonal_vectors_return_zero() {
        float[] a = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] b = {0.0f, 1.0f, 0.0f, 0.0f};
        assertEquals(0.0, similarity.cosine(a, b), EPSILON);
    }

    @Test
    @DisplayName("opposite vectors return cosine -1.0")
    void opposite_vectors_return_minus_one() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        assertEquals(-1.0, similarity.cosine(a, b), EPSILON);
    }

    @Test
    @DisplayName("zero vector returns 0.0 rather than NaN")
    void zero_vector_returns_zero() {
        float[] zero = {0.0f, 0.0f, 0.0f};
        float[] nonZero = {1.0f, 2.0f, 3.0f};
        assertEquals(0.0, similarity.cosine(zero, nonZero), EPSILON);
        assertEquals(0.0, similarity.cosine(nonZero, zero), EPSILON);
        assertEquals(0.0, similarity.cosine(zero, zero), EPSILON);
    }

    @Test
    @DisplayName("different lengths throw IllegalArgumentException")
    void different_lengths_throw_illegal_argument() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(a, b));
    }

    @Test
    @DisplayName("null inputs throw IllegalArgumentException")
    void null_inputs_throw_illegal_argument() {
        float[] v = {1.0f, 2.0f};
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(null, v));
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(v, null));
        assertThrows(IllegalArgumentException.class, () -> similarity.cosine(null, null));
    }
}
