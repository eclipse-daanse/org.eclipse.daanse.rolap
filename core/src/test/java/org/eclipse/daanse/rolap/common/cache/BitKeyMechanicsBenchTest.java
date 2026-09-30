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
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.daanse.olap.key.BitKey;
import org.junit.jupiter.api.Test;

/**
 * Measures the open representation questions of the BitKey round against
 * cell-path-shaped workloads. Contenders: STATUS_QUO (the three-variant
 * BitKey), LONGARRAY (one final class over long[]), HYBRID (two inline
 * longs plus a long[] escape) and BITSET (java.util.BitSet as reference;
 * its subset test needs clone+andNot+isEmpty).
 *
 * Workloads: W1 fresh-key HashMap lookup (working-store profile, the
 * hottest contact), W2 isSuperSetOf scan over resident keys (poset /
 * batch-merge profile), W3 per-row build-and-lookup at arity 8
 * (grouping-set row profile), W4 nextSetBit loop vs forEachSetBit walk,
 * W5 mutable build vs builder-then-freeze (immutability follow-up),
 * W6 SegmentIdentity construction over a mutable key (the pre-freeze
 * clone cost) vs an already-frozen key (the shipped state).
 *
 * Verdict rules (full mode, medians): V1 — a prototype may replace the
 * variants only if it stays within +5% cpu/op of STATUS_QUO on W1-W3 at
 * every width; on a tie LONGARRAY wins over HYBRID (less code). V2 — if
 * both prototypes lose more than 10% on W1, the variants stay. V3 — the
 * builder pattern is green-lit for a follow-up round if W5 shows at most
 * one extra small allocation per op and no cpu regression.
 *
 * Modes via {@code -Ddaanse.cachebench.mode}: smoke (default, gate-free)
 * or full. Report: target/bitkey-mechanics-bench-report.md.
 */
class BitKeyMechanicsBenchTest {

    private static final boolean FULL = "full".equals(System.getProperty("daanse.cachebench.mode", "smoke"));
    private static final int ROUNDS = FULL ? 15 : 2;
    private static final int WARMUP_ROUNDS = FULL ? 2 : 1;
    private static final int OPS = FULL ? 1_000_000 : 50_000;
    private static final int SCAN_OPS = FULL ? 100_000 : 5_000;
    private static final int DIMENSIONALITIES = 32;
    private static final int RESIDENT_KEYS = 50;
    private static final int BITS_PER_KEY = 8;
    private static final int[] WIDTHS = {40, 100, 300};

    private static final AtomicLong BLACKHOLE = new AtomicLong();

    private enum Contender {
        STATUS_QUO, LONGARRAY, HYBRID, BITSET
    }

    private record Cell(long cpuPerOp, long allocPerOp) {
    }

    // ------------------------------------------------------------ prototypes

    /** One final class over long[] — no variants, no interface. */
    static final class WordsKey {
        private final long[] words;

        WordsKey(int width) {
            words = new long[(width >> 6) + 1];
        }

        void set(int pos) {
            words[pos >> 6] |= 1L << pos;
        }

        boolean get(int pos) {
            int chunk = pos >> 6;
            return chunk < words.length && (words[chunk] & (1L << pos)) != 0;
        }

        boolean isSuperSetOf(WordsKey other) {
            long[] a = words;
            long[] b = other.words;
            int shared = Math.min(a.length, b.length);
            for (int i = 0; i < shared; i++) {
                if ((a[i] | b[i]) != a[i]) {
                    return false;
                }
            }
            for (int i = shared; i < b.length; i++) {
                if (b[i] != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            long h = 1234;
            for (int i = words.length; --i >= 0;) {
                h ^= words[i] * (i + 1);
            }
            return (int) ((h >> 32) ^ h);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof WordsKey that)) {
                return false;
            }
            long[] a = words;
            long[] b = that.words;
            int shared = Math.min(a.length, b.length);
            for (int i = 0; i < shared; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            long[] longer = a.length > b.length ? a : b;
            for (int i = shared; i < longer.length; i++) {
                if (longer[i] != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Two inline longs plus a long[] escape for widths beyond 128. */
    static final class HybridKey {
        private long bits0;
        private long bits1;
        private final long[] rest;

        HybridKey(int width) {
            rest = width > 128 ? new long[(width >> 6) - 1] : null;
        }

        private HybridKey(long bits0, long bits1, long[] rest) {
            this.bits0 = bits0;
            this.bits1 = bits1;
            this.rest = rest;
        }

        void set(int pos) {
            if (pos < 64) {
                bits0 |= 1L << pos;
            } else if (pos < 128) {
                bits1 |= 1L << pos;
            } else {
                rest[(pos >> 6) - 2] |= 1L << pos;
            }
        }

        boolean get(int pos) {
            if (pos < 64) {
                return (bits0 & (1L << pos)) != 0;
            } else if (pos < 128) {
                return (bits1 & (1L << pos)) != 0;
            }
            int chunk = (pos >> 6) - 2;
            return rest != null && chunk < rest.length && (rest[chunk] & (1L << pos)) != 0;
        }

        boolean isSuperSetOf(HybridKey other) {
            if ((bits0 | other.bits0) != bits0 || (bits1 | other.bits1) != bits1) {
                return false;
            }
            long[] b = other.rest;
            if (b == null) {
                return true;
            }
            long[] a = rest;
            int shared = a == null ? 0 : Math.min(a.length, b.length);
            for (int i = 0; i < shared; i++) {
                if ((a[i] | b[i]) != a[i]) {
                    return false;
                }
            }
            for (int i = shared; i < b.length; i++) {
                if (b[i] != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            long h = 1234 ^ bits0 ^ (bits1 * 2);
            if (rest != null) {
                for (int i = rest.length; --i >= 0;) {
                    h ^= rest[i] * (i + 3);
                }
            }
            return (int) ((h >> 32) ^ h);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HybridKey that)) {
                return false;
            }
            if (bits0 != that.bits0 || bits1 != that.bits1) {
                return false;
            }
            long[] a = rest;
            long[] b = that.rest;
            int aLen = a == null ? 0 : a.length;
            int bLen = b == null ? 0 : b.length;
            int shared = Math.min(aLen, bLen);
            for (int i = 0; i < shared; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            long[] longer = aLen > bLen ? a : b;
            for (int i = shared; i < Math.max(aLen, bLen); i++) {
                if (longer[i] != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Builder-then-freeze shape for W5: the frozen key hashes eagerly. */
    static final class FrozenKey {
        private final long bits0;
        private final long bits1;
        private final int hash;

        FrozenKey(long bits0, long bits1) {
            this.bits0 = bits0;
            this.bits1 = bits1;
            long h = 1234 ^ bits0 ^ (bits1 * 2);
            this.hash = (int) ((h >> 32) ^ h);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FrozenKey that && bits0 == that.bits0 && bits1 == that.bits1;
        }
    }

    static final class FrozenBuilder {
        private long bits0;
        private long bits1;

        void set(int pos) {
            if (pos < 64) {
                bits0 |= 1L << pos;
            } else {
                bits1 |= 1L << pos;
            }
        }

        FrozenKey build() {
            return new FrozenKey(bits0, bits1);
        }
    }

    // ------------------------------------------------------------ key kits

    private static Object build(Contender contender, int width, int[] pattern) {
        switch (contender) {
        case STATUS_QUO: {
            BitKey key = BitKey.Factory.makeBitKey(width);
            for (int bit : pattern) {
                key.set(bit);
            }
            return key;
        }
        case LONGARRAY: {
            WordsKey key = new WordsKey(width);
            for (int bit : pattern) {
                key.set(bit);
            }
            return key;
        }
        case HYBRID: {
            HybridKey key = new HybridKey(width);
            for (int bit : pattern) {
                key.set(bit);
            }
            return key;
        }
        default: {
            BitSet key = new BitSet(width);
            for (int bit : pattern) {
                key.set(bit);
            }
            return key;
        }
        }
    }

    private static boolean isSuperSetOf(Contender contender, Object a, Object b) {
        switch (contender) {
        case STATUS_QUO:
            return ((BitKey) a).isSuperSetOf((BitKey) b);
        case LONGARRAY:
            return ((WordsKey) a).isSuperSetOf((WordsKey) b);
        case HYBRID:
            return ((HybridKey) a).isSuperSetOf((HybridKey) b);
        default: {
            // the honest BitSet emulation: no word access, no containsAll
            BitSet clone = (BitSet) ((BitSet) b).clone();
            clone.andNot((BitSet) a);
            return clone.isEmpty();
        }
        }
    }

    /** The distinct bit patterns of the run; index selects the dimensionality. */
    private static int[][] patterns(int width, int bitsPerKey) {
        Random random = new Random(4242 + width);
        int[][] patterns = new int[DIMENSIONALITIES][bitsPerKey];
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            for (int b = 0; b < bitsPerKey; b++) {
                patterns[d][b] = random.nextInt(width);
            }
        }
        return patterns;
    }

    // ------------------------------------------------------------ workloads

    /** W1: fresh key per op, resident HashMap lookup (working store). */
    private Cell runMapKey(Contender contender, int width) {
        int[][] patterns = patterns(width, BITS_PER_KEY);
        Map<Object, Object> resident = new HashMap<>();
        for (int i = 0; i < RESIDENT_KEYS; i++) {
            resident.put(build(contender, width, patterns[i % DIMENSIONALITIES]), Integer.valueOf(i));
        }
        return measure(OPS, () -> {
            Random random = new Random(42);
            long acc = 0;
            for (int op = 0; op < OPS; op++) {
                Object key = build(contender, width, patterns[random.nextInt(DIMENSIONALITIES)]);
                acc ^= System.identityHashCode(resident.get(key));
            }
            return acc;
        });
    }

    /** W2: subset scan over resident keys (poset / batch merge). */
    private Cell runSupersetScan(Contender contender, int width) {
        int[][] patterns = patterns(width, BITS_PER_KEY);
        List<Object> resident = new ArrayList<>();
        for (int i = 0; i < RESIDENT_KEYS; i++) {
            resident.add(build(contender, width, patterns[i % DIMENSIONALITIES]));
        }
        Object[] probes = new Object[DIMENSIONALITIES];
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            probes[d] = build(contender, width, patterns[d]);
        }
        return measure((long) SCAN_OPS * RESIDENT_KEYS, () -> {
            Random random = new Random(43);
            long acc = 0;
            for (int op = 0; op < SCAN_OPS; op++) {
                Object probe = probes[random.nextInt(DIMENSIONALITIES)];
                for (Object candidate : resident) {
                    if (isSuperSetOf(contender, candidate, probe)) {
                        acc++;
                    }
                }
            }
            return acc;
        });
    }

    /** W3: per-row key build and map hit at arity 8 (grouping sets). */
    private Cell runPerRowBuild(Contender contender) {
        int[][] patterns = patterns(8, 4);
        Map<Object, Object> cohorts = new HashMap<>();
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            cohorts.put(build(contender, 8, patterns[d]), Integer.valueOf(d));
        }
        return measure(OPS, () -> {
            Random random = new Random(44);
            long acc = 0;
            for (int op = 0; op < OPS; op++) {
                Object key = build(contender, 8, patterns[random.nextInt(DIMENSIONALITIES)]);
                acc ^= System.identityHashCode(cohorts.get(key));
            }
            return acc;
        });
    }

    /** W4: walking the set bits — nextSetBit loop vs forEachSetBit. */
    private Cell runWalk(boolean forEach, int width) {
        int[][] patterns = patterns(width, BITS_PER_KEY);
        BitKey[] keys = new BitKey[DIMENSIONALITIES];
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            keys[d] = (BitKey) build(Contender.STATUS_QUO, width, patterns[d]);
        }
        return measure(OPS, () -> {
            Random random = new Random(45);
            long acc = 0;
            AtomicLong sink = new AtomicLong();
            for (int op = 0; op < OPS; op++) {
                BitKey key = keys[random.nextInt(DIMENSIONALITIES)];
                if (forEach) {
                    key.forEachSetBit(sink::addAndGet);
                } else {
                    for (int pos = key.nextSetBit(0); pos >= 0; pos = key.nextSetBit(pos + 1)) {
                        sink.addAndGet(pos);
                    }
                }
            }
            return acc + sink.get();
        });
    }

    /** W5: mutable build vs builder-then-freeze (CellRequest profile). */
    private Cell runBuilderShape(boolean builder) {
        int[][] patterns = patterns(100, BITS_PER_KEY);
        Map<Object, Object> resident = new HashMap<>();
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            FrozenBuilder seed = new FrozenBuilder();
            for (int bit : patterns[d]) {
                seed.set(bit);
            }
            resident.put(seed.build(), Integer.valueOf(d));
        }
        return measure(OPS, () -> {
            Random random = new Random(46);
            long acc = 0;
            for (int op = 0; op < OPS; op++) {
                int[] pattern = patterns[random.nextInt(DIMENSIONALITIES)];
                Object key;
                if (builder) {
                    FrozenBuilder b = new FrozenBuilder();
                    for (int bit : pattern) {
                        b.set(bit);
                    }
                    key = b.build();
                } else {
                    HybridKey k = new HybridKey(100);
                    for (int bit : pattern) {
                        k.set(bit);
                    }
                    key = k;
                }
                acc ^= System.identityHashCode(resident.get(key));
            }
            return acc;
        });
    }

    /**
     * W6: what the freeze() round removed — SegmentIdentity construction
     * per rollup-ancestor lookup. Variant 0 rebuilds the pre-freeze cost
     * (identity over a MUTABLE key: the constructor must clone via
     * freeze()); variant 1 is the shipped state (key already frozen:
     * freeze() returns the same instance).
     */
    private Cell runIdentityConstruction(boolean frozenInput, int width) {
        int[][] patterns = patterns(width, BITS_PER_KEY);
        org.eclipse.daanse.olap.util.ByteString checksum =
                new org.eclipse.daanse.olap.util.ByteString("c".getBytes());
        Object[] keys = new Object[DIMENSIONALITIES];
        for (int d = 0; d < DIMENSIONALITIES; d++) {
            BitKey key = (BitKey) build(Contender.STATUS_QUO, width, patterns[d]);
            keys[d] = frozenInput ? key.freeze() : key;
        }
        java.util.List<org.eclipse.daanse.olap.spi.SegmentPredicate> noPredicates =
                java.util.List.of();
        return measure(OPS, () -> {
            Random random = new Random(47);
            long acc = 0;
            for (int op = 0; op < OPS; op++) {
                BitKey key = (BitKey) keys[random.nextInt(DIMENSIONALITIES)];
                acc ^= System.identityHashCode(
                        new org.eclipse.daanse.olap.spi.SegmentIdentity(
                                "S", checksum, "C", "FACT", "m", noPredicates, key));
            }
            return acc;
        });
    }

    // ------------------------------------------------------------ harness

    private interface Body {
        long run();
    }

    private Cell measure(long ops, Body body) {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long cpu0 = bean.getCurrentThreadCpuTime();
        long alloc0 = bean.getCurrentThreadAllocatedBytes();
        BLACKHOLE.addAndGet(body.run());
        long cpu = bean.getCurrentThreadCpuTime() - cpu0;
        long alloc = bean.getCurrentThreadAllocatedBytes() - alloc0;
        return new Cell(cpu / ops, alloc / ops);
    }

    private static Cell median(List<Cell> cells) {
        List<Long> cpu = cells.stream().map(Cell::cpuPerOp).sorted().toList();
        List<Long> alloc = cells.stream().map(Cell::allocPerOp).sorted().toList();
        int mid = cells.size() / 2;
        return new Cell(cpu.get(mid), alloc.get(mid));
    }

    private interface Runner {
        Cell run(int variantOrdinal);
    }

    /** Rotates variants each round against drift; returns per-variant medians. */
    private Cell[] series(int variantCount, Runner runner) {
        List<List<Cell>> collected = new ArrayList<>();
        for (int v = 0; v < variantCount; v++) {
            collected.add(new ArrayList<>());
        }
        for (int round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
            for (int i = 0; i < variantCount; i++) {
                int v = (i + round) % variantCount;
                Cell cell = runner.run(v);
                if (round >= WARMUP_ROUNDS) {
                    collected.get(v).add(cell);
                }
            }
        }
        Cell[] medians = new Cell[variantCount];
        for (int v = 0; v < variantCount; v++) {
            medians[v] = median(collected.get(v));
        }
        return medians;
    }

    @Test
    void benchMechanics() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("# BitKey-Mechanics-Bench (" + (FULL ? "full" : "smoke") + ", rounds=" + ROUNDS + ")");
        report.add("");
        Contender[] contenders = Contender.values();

        report.add("## W1 Map-Key (fresh key + HashMap.get, cpu ns/op | alloc B/op)");
        report.add("");
        report.add("| Breite | " + header(contenders) + " |");
        report.add("|---|---|---|---|---|");
        for (int width : WIDTHS) {
            final int w = width;
            Cell[] medians = series(contenders.length, v -> runMapKey(contenders[v], w));
            report.add("| " + width + " | " + cells(medians) + " |");
        }

        report.add("");
        report.add("## W2 isSuperSetOf-Scan (cpu ns/Vergleich | alloc B/Vergleich)");
        report.add("");
        report.add("| Breite | " + header(contenders) + " |");
        report.add("|---|---|---|---|---|");
        for (int width : WIDTHS) {
            final int w = width;
            Cell[] medians = series(contenders.length, v -> runSupersetScan(contenders[v], w));
            report.add("| " + width + " | " + cells(medians) + " |");
        }

        report.add("");
        report.add("## W3 Per-Row-Build arity 8 (cpu ns/op | alloc B/op)");
        report.add("");
        report.add("| " + header(contenders) + " |");
        report.add("|---|---|---|---|");
        Cell[] w3 = series(contenders.length, v -> runPerRowBuild(contenders[v]));
        report.add("| " + cells(w3) + " |");

        report.add("");
        report.add("## W4 Walk (STATUS_QUO: nextSetBit vs forEachSetBit)");
        report.add("");
        report.add("| Breite | nextSetBit | forEachSetBit |");
        report.add("|---|---|---|");
        for (int width : WIDTHS) {
            final int w = width;
            Cell[] medians = series(2, v -> runWalk(v == 1, w));
            report.add("| " + width + " | " + cell(medians[0]) + " | " + cell(medians[1]) + " |");
        }

        report.add("");
        report.add("## W5 Aufbau (mutable vs builder+freeze, Breite 100)");
        report.add("");
        report.add("| mutable | builder+freeze |");
        report.add("|---|---|");
        Cell[] w5 = series(2, v -> runBuilderShape(v == 1));
        report.add("| " + cell(w5[0]) + " | " + cell(w5[1]) + " |");

        report.add("");
        report.add("## W6 SegmentIdentity-Bau (mutabler Key = Vor-freeze-Kosten vs. gefrorener Key)");
        report.add("");
        report.add("| Breite | mutabler Key (klont) | gefrorener Key (no-op) |");
        report.add("|---|---|---|");
        for (int width : WIDTHS) {
            final int w = width;
            Cell[] medians = series(2, v -> runIdentityConstruction(v == 1, w));
            report.add("| " + width + " | " + cell(medians[0]) + " | " + cell(medians[1]) + " |");
        }

        Path out = Path.of("target", "bitkey-mechanics-bench-report.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report);
        System.out.println(String.join(System.lineSeparator(), report));
        assertTrue(BLACKHOLE.get() != Long.MIN_VALUE);
    }

    private static String header(Contender[] contenders) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contenders.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(contenders[i]);
        }
        return sb.toString();
    }

    private static String cells(Cell[] medians) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < medians.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(cell(medians[i]));
        }
        return sb.toString();
    }

    private static String cell(Cell cell) {
        return cell.cpuPerOp() + " ns, " + cell.allocPerOp() + " B";
    }
}
