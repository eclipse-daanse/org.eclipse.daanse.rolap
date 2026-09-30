/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.cache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.daanse.olap.key.BitKey;
import org.junit.jupiter.api.Test;

/**
 * Measures the cell-path question behind BitKey interning: a fresh key per
 * request (status quo — wide keys carry a cached hash) against interned
 * canonical keys, over a resident HashMap lookup like the working store's
 * segmentRefs. Verdict rule (full mode, both wide widths): INTERN stays only
 * if cpu/op is within +5% single-threaded and +10% at 4 threads of FRESH,
 * AND it allocates at least 30% less per op. Modes via
 * {@code -Ddaanse.cachebench.mode}: smoke (default, gate-free) or full.
 * Report: target/bitkey-intern-bench-report.md.
 */
class BitKeyInternBenchTest {

    private static final boolean FULL = "full".equals(System.getProperty("daanse.cachebench.mode", "smoke"));
    private static final int ROUNDS = FULL ? 15 : 2;
    private static final int WARMUP_ROUNDS = FULL ? 2 : 1;
    private static final int OPS_PER_THREAD = FULL ? 1_000_000 : 100_000;
    private static final int DIMENSIONALITIES = 32;
    private static final int RESIDENT_KEYS = 64;
    private static final int BITS_PER_KEY = 8;
    private static final int[] WIDTHS = {64, 200, 320};
    private static final int[] THREAD_COUNTS = FULL ? new int[] {1, 4} : new int[] {1};

    private static final AtomicLong BLACKHOLE = new AtomicLong();

    private enum Variant {
        FRESH, INTERN
    }

    private record CellResult(long threadCpuPerOp, long processCpuPerOp, long allocPerOp) {
    }

    @Test
    void benchInterning() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("# BitKey-Intern-Bench (" + (FULL ? "full" : "smoke") + ", rounds=" + ROUNDS + ")");
        report.add("");
        report.add("| Breite | Threads | Variante | threadCpu ns/op | processCpu ns/op | alloc B/op |");
        report.add("|---|---|---|---|---|---|");
        for (int width : WIDTHS) {
            for (int threads : THREAD_COUNTS) {
                Map<Variant, List<CellResult>> series = new HashMap<>();
                for (int round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
                    // rotate variant order each round against drift
                    Variant[] order = round % 2 == 0
                            ? new Variant[] {Variant.FRESH, Variant.INTERN}
                            : new Variant[] {Variant.INTERN, Variant.FRESH};
                    for (Variant variant : order) {
                        CellResult result = runCell(variant, width, threads);
                        if (round >= WARMUP_ROUNDS) {
                            series.computeIfAbsent(variant, v -> new ArrayList<>()).add(result);
                        }
                    }
                }
                for (Variant variant : Variant.values()) {
                    CellResult median = median(series.get(variant));
                    report.add("| " + width + " | " + threads + " | " + variant + " | "
                            + median.threadCpuPerOp() + " | " + median.processCpuPerOp() + " | "
                            + median.allocPerOp() + " |");
                }
            }
        }
        Path out = Path.of("target", "bitkey-intern-bench-report.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report);
        System.out.println(String.join(System.lineSeparator(), report));
        assertTrue(BLACKHOLE.get() != Long.MIN_VALUE);
    }

    private static CellResult median(List<CellResult> results) {
        List<Long> thread = results.stream().map(CellResult::threadCpuPerOp).sorted().toList();
        List<Long> process = results.stream().map(CellResult::processCpuPerOp).sorted().toList();
        List<Long> alloc = results.stream().map(CellResult::allocPerOp).sorted().toList();
        int mid = results.size() / 2;
        return new CellResult(thread.get(mid), process.get(mid), alloc.get(mid));
    }

    /** The distinct bit patterns of the run; index selects the dimensionality. */
    private static int[][] patterns(int width) {
        java.util.Random random = new java.util.Random(4242 + width);
        int[][] patterns = new int[DIMENSIONALITIES][BITS_PER_KEY];
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            for (int b = 0; b < BITS_PER_KEY; b++) {
                patterns[d][b] = random.nextInt(width);
            }
        }
        return patterns;
    }

    private static BitKey build(int width, int[] pattern) {
        BitKey key = BitKey.Factory.makeBitKey(width);
        for (int bit : pattern) {
            key.set(bit);
        }
        return key;
    }

    private CellResult runCell(Variant variant, int width, int threads) throws Exception {
        int[][] patterns = patterns(width);
        // resident lookup map like the working store: value-equal keys built
        // independently of the per-op keys
        Map<BitKey, Object> resident = new HashMap<>();
        java.util.Random seed = new java.util.Random(7);
        for (int i = 0; i < RESIDENT_KEYS; i++) {
            BitKey key = build(width, patterns[i % DIMENSIONALITIES]);
            resident.put(key, Integer.valueOf(seed.nextInt()));
        }
        BitKeyInterner interner = new BitKeyInterner();

        long processCpuBefore = processCpuNs();
        AtomicLong threadCpu = new AtomicLong();
        AtomicLong allocated = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadSeed = t;
            futures.add(pool.submit(() -> {
                com.sun.management.ThreadMXBean bean =
                        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long cpu0 = bean.getCurrentThreadCpuTime();
                long alloc0 = bean.getCurrentThreadAllocatedBytes();
                long acc = 0;
                java.util.Random random = new java.util.Random(42 + threadSeed);
                for (int op = 0; op < OPS_PER_THREAD; op++) {
                    BitKey key = build(width, patterns[random.nextInt(DIMENSIONALITIES)]);
                    if (variant == Variant.INTERN) {
                        key = interner.intern(key);
                    }
                    acc ^= System.identityHashCode(resident.get(key));
                }
                BLACKHOLE.addAndGet(acc);
                allocated.addAndGet(bean.getCurrentThreadAllocatedBytes() - alloc0);
                threadCpu.addAndGet(bean.getCurrentThreadCpuTime() - cpu0);
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(10, TimeUnit.MINUTES);
        }
        pool.shutdown();
        long processCpu = Math.max(0, processCpuNs() - processCpuBefore);
        long totalOps = (long) threads * OPS_PER_THREAD;
        return new CellResult(
                threadCpu.get() / totalOps,
                processCpu / totalOps,
                allocated.get() / totalOps);
    }

    private static long processCpuNs() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getProcessCpuTime();
    }
}
