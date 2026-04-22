package com.svenruppert.flow.views.module02;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Latency benchmark for the two {@link VectorStore} implementations in
 * module 2. Runs on reproducible pseudo-random vectors so the output is
 * comparable across machines and across workshop runs.
 *
 * <h2>What this benchmark is supposed to show</h2>
 * <ul>
 *   <li>{@link InMemoryVectorStore} performs an O(n) linear scan per
 *       query: latency grows linearly with corpus size.</li>
 *   <li>{@link EclipseStoreJVectorStore} uses an HNSW index: latency
 *       grows approximately with O(log n) -- sub-linear. The bigger the
 *       corpus, the larger the gap becomes.</li>
 * </ul>
 *
 * <p>The output is a compact table that can be pasted straight into
 * slides.
 *
 * <h2>Configuration (system properties)</h2>
 * <ul>
 *   <li>{@code -Dbenchmark.sizes=1000,10000,50000} -- corpus sizes</li>
 *   <li>{@code -Dbenchmark.large=true} -- additionally run 100 000</li>
 *   <li>{@code -Dbenchmark.queries=500} -- measured queries per size</li>
 *   <li>{@code -Dbenchmark.warmup=100} -- warmup queries (absorbs the
 *       first lazy index rebuild in the JVector store)</li>
 *   <li>{@code -Dbenchmark.topk=10} -- top-k for every query</li>
 * </ul>
 *
 * <p>Run from the project root:
 * <pre>mvn exec:java -Dexec.mainClass=com.svenruppert.flow.views.module02.JVectorStoreBenchmark</pre>
 */
public final class JVectorStoreBenchmark implements HasLogger {

    private static final int DEFAULT_DIMENSION = 768;     // common embedding size
    private static final long DEFAULT_SEED = 42L;

    private JVectorStoreBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        List<Integer> sizes = parseSizes(System.getProperty("benchmark.sizes", "1000,10000,50000"));
        if (Boolean.getBoolean("benchmark.large")) {
            sizes = new ArrayList<>(sizes);
            if (!sizes.contains(100_000)) sizes.add(100_000);
        }
        int queries = Integer.parseInt(System.getProperty("benchmark.queries", "500"));
        int warmup = Integer.parseInt(System.getProperty("benchmark.warmup", "100"));
        int topK = Integer.parseInt(System.getProperty("benchmark.topk", "10"));
        int dimension = Integer.parseInt(
                System.getProperty("benchmark.dimension", Integer.toString(DEFAULT_DIMENSION)));

        System.out.printf(Locale.ROOT,
                "Benchmark: dim=%d  sizes=%s  queries=%d  warmup=%d  topk=%d%n",
                dimension, sizes, queries, warmup, topK);

        printHeader();

        for (int size : sizes) {
            float[][] corpus = generateVectors(size, dimension, DEFAULT_SEED);
            float[][] queryVectors = generateVectors(
                    queries + warmup, dimension, DEFAULT_SEED + 1);

            runOne(new InMemoryVectorStore(new DefaultSimilarity()),
                    "InMemoryVectorStore",
                    size, corpus, queryVectors, warmup, queries, topK,
                    null);

            Path tempDir = Files.createTempDirectory("jvector-bench-");
            try (EclipseStoreJVectorStore store = new EclipseStoreJVectorStore(tempDir)) {
                runOne(store, "EclipseStoreJVectorStore",
                        size, corpus, queryVectors, warmup, queries, topK,
                        tempDir);
            } finally {
                deleteRecursively(tempDir);
            }
        }
    }

    // ---------- per-size runner -----------------------------------------

    private static void runOne(VectorStore store, String storeName,
                               int size,
                               float[][] corpus, float[][] queryVectors,
                               int warmup, int queries, int topK,
                               Path ownedTempDir) {
        long ingestStart = System.nanoTime();
        for (int i = 0; i < size; i++) {
            store.add("id-" + i, corpus[i], "payload-" + i);
        }
        long ingestNanos = System.nanoTime() - ingestStart;

        // Warmup: triggers the JVector lazy rebuild and lets the JIT
        // settle; throw away the measurements.
        for (int i = 0; i < warmup; i++) {
            store.search(queryVectors[i], topK);
        }

        long[] latencies = new long[queries];
        for (int i = 0; i < queries; i++) {
            long t0 = System.nanoTime();
            store.search(queryVectors[warmup + i], topK);
            latencies[i] = System.nanoTime() - t0;
        }

        // Close transient stores; persistent ones are owned by the caller.
        if (ownedTempDir == null) {
            try {
                store.close();
            } catch (Exception ignored) {
            }
        }

        printRow(size, storeName, ingestNanos, latencies);
    }

    // ---------- data generation -----------------------------------------

    private static float[][] generateVectors(int n, int dim, long seed) {
        Random random = new Random(seed);
        float[][] data = new float[n][dim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < dim; j++) {
                // Gaussian-ish distribution around zero, scaled so every
                // vector has a non-trivial norm but stays below 1.
                data[i][j] = (float) (random.nextGaussian() * 0.1);
            }
        }
        return data;
    }

    // ---------- reporting -----------------------------------------------

    private static void printHeader() {
        System.out.println();
        System.out.printf(Locale.ROOT,
                "%11s | %-25s | %10s | %11s | %8s | %8s | %8s%n",
                "Corpus size", "Store", "Ingest (s)", "Median (ms)",
                "P95 (ms)", "Min (ms)", "Max (ms)");
        System.out.println("-".repeat(11) + "-+-" + "-".repeat(25) + "-+-"
                + "-".repeat(10) + "-+-" + "-".repeat(11) + "-+-"
                + "-".repeat(8) + "-+-" + "-".repeat(8) + "-+-"
                + "-".repeat(8));
    }

    private static void printRow(int size, String store, long ingestNanos, long[] latenciesNs) {
        double ingestSec = ingestNanos / 1.0e9;
        long[] sorted = latenciesNs.clone();
        Arrays.sort(sorted);
        double medianMs = toMillis(percentile(sorted, 50));
        double p95Ms = toMillis(percentile(sorted, 95));
        double minMs = toMillis(sorted[0]);
        double maxMs = toMillis(sorted[sorted.length - 1]);

        System.out.printf(Locale.ROOT,
                "%,11d | %-25s | %10.2f | %11.2f | %8.2f | %8.2f | %8.2f%n",
                size, store, ingestSec, medianMs, p95Ms, minMs, maxMs);
    }

    /**
     * Simple nearest-rank percentile over a pre-sorted array. No
     * interpolation -- appropriate for latency distributions where
     * individual samples are already quantised by the OS scheduler.
     */
    private static long percentile(long[] sorted, int percentile) {
        if (sorted.length == 0) return 0L;
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        if (rank < 0) rank = 0;
        if (rank >= sorted.length) rank = sorted.length - 1;
        return sorted[rank];
    }

    private static double toMillis(long nanos) {
        return nanos / 1.0e6;
    }

    // ---------- helpers -------------------------------------------------

    private static List<Integer> parseSizes(String csv) {
        List<Integer> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(Integer.parseInt(trimmed));
        }
        out.sort(Comparator.naturalOrder());
        return List.copyOf(out);
    }

    private static void deleteRecursively(Path root) {
        if (root == null) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            System.err.println("Could not delete " + root + ": " + e.getMessage());
        }
    }
}
