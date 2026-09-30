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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Interner;

/**
 * Contender benchmark for the canonical member key map: the hand-built
 * {@link SoftValueCache} against three Caffeine constructions, measured over
 * rotated series with CPU-time metrics (wall time drifts under foreign
 * load). Report: target/cache-bench-report.md.
 *
 * <p>
 * Modes via {@code -Ddaanse.cachebench.mode}: {@code smoke} (default, runs in
 * mvn test, small and gate-free on numbers) and {@code full} (long run;
 * recommended flags {@code -DargLine="-Xmx1g -XX:SoftRefLRUPolicyMSPerMB=0"}
 * so soft references yield deterministically).
 */
class CacheContenderBenchTest {

    private static final boolean FULL = "full".equals(System.getProperty("daanse.cachebench.mode", "smoke"));
    private static final int ROUNDS = FULL ? 15 : 3;
    private static final int WARMUP_ROUNDS = 2;
    private static final int KEYSPACE = FULL ? 100_000 : 10_000;
    private static final int[] THREAD_COUNTS = FULL ? new int[] {1, 4, 8} : new int[] {1};
    private static final int OPS_PER_THREAD = FULL ? 400_000 : 50_000;

    enum Variant {
        SOFT_VALUE, CAFFEINE_SYNC, CAFFEINE_ASYNC, CAFFEINE_INTERNER
    }

    /** value-equals key holding its parent value strongly, like MemberKeyR */
    static final class BenchKey {
        final BenchValue parent;
        final int ordinal;

        BenchKey(BenchValue parent, int ordinal) {
            this.parent = parent;
            this.ordinal = ordinal;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BenchKey that && ordinal == that.ordinal && parent == that.parent;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(parent) + ordinal;
        }
    }

    /** like RolapMember: payload, no back-reference to its own key */
    static final class BenchValue {
        final long[] payload = new long[8];
        // only the interner variant anchors the canonical key here; without
        // the anchor its entry would die as soon as the weak key does
        volatile BenchKey canonicalAnchor;

        BenchValue(int seed) {
            Arrays.fill(payload, seed);
        }
    }

    static SimpleCache<BenchKey, BenchValue> create(Variant variant) {
        return switch (variant) {
        case SOFT_VALUE -> new SoftValueCache<>();
        case CAFFEINE_SYNC -> new CaffeineSimpleCache<>(true, false);
        case CAFFEINE_ASYNC -> new CaffeineSimpleCache<>(false, false);
        case CAFFEINE_INTERNER -> new InternerSimpleCache();
        };
    }

    /** Caffeine softValues with strong equals keys, behind SimpleCache. */
    static class CaffeineSimpleCache<K, V> implements SimpleCache<K, V> {
        final Cache<K, V> cache;
        final ConcurrentMap<K, V> map;

        CaffeineSimpleCache(boolean sameThreadMaintenance, boolean weakKeys) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder().softValues();
            if (weakKeys) {
                builder.weakKeys();
            }
            if (sameThreadMaintenance) {
                builder.executor(Runnable::run);
            }
            this.cache = builder.build();
            this.map = cache.asMap();
        }

        @Override
        public V put(K key, V value) {
            Objects.requireNonNull(value);
            return map.put(key, value);
        }

        @Override
        public V putIfAbsent(K key, V value) {
            Objects.requireNonNull(value);
            return map.putIfAbsent(key, value);
        }

        @Override
        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
            Objects.requireNonNull(value);
            // compute, not Map.merge: existing==null stores value without remapping
            return map.compute(key, (k, existing) ->
                existing == null ? value : remapping.apply(existing, value));
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            return map.replace(key, oldValue, newValue);
        }

        @Override
        public V get(K key) {
            return map.get(key);
        }

        @Override
        public V remove(K key) {
            return map.remove(key);
        }

        @Override
        public void clear() {
            cache.invalidateAll();
        }

        @Override
        public int size() {
            cache.cleanUp();
            return map.size();
        }

        @Override
        public void execute(Task<K, V> task) {
            final Iterator<Map.Entry<K, V>> raw = map.entrySet().iterator();
            task.execute(new Iterator<Map.Entry<K, V>>() {
                private Map.Entry<K, V> lastReturned;

                @Override
                public boolean hasNext() {
                    return raw.hasNext();
                }

                @Override
                public Map.Entry<K, V> next() {
                    lastReturned = raw.next();
                    return lastReturned;
                }

                @Override
                public void remove() {
                    if (lastReturned == null) {
                        throw new IllegalStateException();
                    }
                    map.remove(lastReturned.getKey(), lastReturned.getValue());
                    lastReturned = null;
                }
            });
        }
    }

    /**
     * The Caffeine-endorsed pattern for equals-based weak keys: an Interner
     * canonicalizes the key, a weakKeys+softValues cache is addressed only
     * through the canonical instance, and the VALUE anchors that instance so
     * the entry lives exactly as long as the value.
     */
    static class InternerSimpleCache extends CaffeineSimpleCache<BenchKey, BenchValue> {
        final Interner<BenchKey> interner = Interner.newWeakInterner();

        InternerSimpleCache() {
            super(true, true);
        }

        @Override
        public BenchValue put(BenchKey key, BenchValue value) {
            BenchKey canonical = interner.intern(key);
            value.canonicalAnchor = canonical;
            return map.put(canonical, value);
        }

        @Override
        public BenchValue putIfAbsent(BenchKey key, BenchValue value) {
            BenchKey canonical = interner.intern(key);
            value.canonicalAnchor = canonical;
            return map.putIfAbsent(canonical, value);
        }

        @Override
        public BenchValue merge(BenchKey key, BenchValue value,
                BiFunction<? super BenchValue, ? super BenchValue, ? extends BenchValue> remapping) {
            BenchKey canonical = interner.intern(key);
            value.canonicalAnchor = canonical;
            return map.compute(canonical, (k, existing) ->
                existing == null ? value : remapping.apply(existing, value));
        }

        @Override
        public BenchValue get(BenchKey key) {
            // no peek on Interner: a miss interns a weak-only key husk
            return map.get(interner.intern(key));
        }

        @Override
        public BenchValue remove(BenchKey key) {
            return map.remove(interner.intern(key));
        }
    }

    // ------------------------------------------------------------------
    // P0 — contract parity across all variants
    // ------------------------------------------------------------------

    @Test
    void contractParity() {
        for (Variant variant : Variant.values()) {
            SimpleCache<BenchKey, BenchValue> cache = create(variant);
            BenchValue parent = new BenchValue(0);
            BenchKey key = new BenchKey(parent, 1);
            BenchValue value = new BenchValue(1);

            assertThat(cache.get(key)).as("%s: empty get", variant).isNull();
            assertThat(cache.putIfAbsent(key, value)).as("%s: first putIfAbsent", variant).isNull();
            // fresh equal key must hit
            assertThat(cache.get(new BenchKey(parent, 1))).as("%s: equals-based hit", variant).isSameAs(value);
            BenchValue other = new BenchValue(2);
            assertThat(cache.putIfAbsent(new BenchKey(parent, 1), other))
                .as("%s: second putIfAbsent returns existing", variant).isSameAs(value);
            assertThat(cache.size()).as("%s: size", variant).isEqualTo(1);
            assertThat(cache.remove(key)).as("%s: remove returns value", variant).isSameAs(value);
            assertThat(cache.get(key)).as("%s: removed", variant).isNull();

            cache.put(key, value);
            cache.merge(key, other, (a, b) -> a);
            assertThat(cache.get(key)).as("%s: merge keeps existing", variant).isSameAs(value);

            cache.execute(iterator -> {
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            });
            assertThat(cache.size()).as("%s: execute-remove empties", variant).isZero();

            cache.put(key, value);
            cache.clear();
            assertThat(cache.get(key)).as("%s: cleared", variant).isNull();
        }
    }

    // ------------------------------------------------------------------
    // T1/T2 — throughput cells over rotated series
    // ------------------------------------------------------------------

    private record CellResult(long opsPerSec, long threadCpuNsPerOp, long processCpuNsPerOp, long gcMillis) {
    }

    private static final AtomicLong BLACKHOLE = new AtomicLong();

    @Test
    void benchThroughputAndRetention() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("# Cache-Bench-Report (CacheContenderBenchTest)");
        report.add("");
        report.add("mode=" + (FULL ? "full" : "smoke") + " rounds=" + ROUNDS + " keyspace=" + KEYSPACE
                + " opsPerThread=" + OPS_PER_THREAD + " maxMemory=" + Runtime.getRuntime().maxMemory() / (1024 * 1024)
                + "MB cores=" + Runtime.getRuntime().availableProcessors());
        report.add("");

        for (boolean mixed : new boolean[] {false, true}) {
            if (mixed && !FULL) {
                continue;
            }
            for (int threads : THREAD_COUNTS) {
                report.add("## " + (mixed ? "T2 mix 80/20" : "T1 get-hit") + " threads=" + threads);
                report.add("");
                report.add("| Variante | ops/s med | CPU-ns/op Thread med | CPU-ns/op Prozess med | GC-ms med |");
                report.add("|---|---|---|---|---|");
                Map<Variant, List<CellResult>> series = runRotatedSeries(mixed, threads);
                for (Variant variant : Variant.values()) {
                    List<CellResult> results = series.get(variant);
                    report.add("| " + variant
                            + " | " + median(results, CellResult::opsPerSec)
                            + " | " + median(results, CellResult::threadCpuNsPerOp)
                            + " | " + median(results, CellResult::processCpuNsPerOp)
                            + " | " + median(results, CellResult::gcMillis) + " |");
                }
                report.add("");
            }
        }

        if (FULL) {
            retentionAndPurge(report);
        }

        report.add("blackhole=" + BLACKHOLE.get());
        Path out = Path.of("target", "cache-bench-report.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report);
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private Map<Variant, List<CellResult>> runRotatedSeries(boolean mixed, int threads) throws Exception {
        Map<Variant, List<CellResult>> series = new java.util.EnumMap<>(Variant.class);
        for (Variant variant : Variant.values()) {
            series.put(variant, new ArrayList<>());
        }
        Variant[] variants = Variant.values();
        for (int round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
            boolean measured = round >= WARMUP_ROUNDS;
            for (int i = 0; i < variants.length; i++) {
                // rotate the start variant each round to neutralize drift
                Variant variant = variants[(round + i) % variants.length];
                CellResult result = runThroughputCell(variant, mixed, threads);
                if (measured) {
                    series.get(variant).add(result);
                }
            }
        }
        return series;
    }

    private CellResult runThroughputCell(Variant variant, boolean mixed, int threads) throws Exception {
        SimpleCache<BenchKey, BenchValue> cache = create(variant);
        // pinned model: values stay strongly reachable, GC never interferes
        BenchValue[] parents = new BenchValue[64];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = new BenchValue(i);
        }
        BenchValue[] pinned = new BenchValue[KEYSPACE];
        for (int i = 0; i < KEYSPACE; i++) {
            pinned[i] = new BenchValue(i);
            cache.putIfAbsent(new BenchKey(parents[i % parents.length], i), pinned[i]);
        }

        long gcBefore = totalGcMillis();
        long processCpuBefore = processCpuNs();
        AtomicLong threadCpu = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        long wall0 = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            futures.add(pool.submit(() -> {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long cpu0 = bean.getCurrentThreadCpuTime();
                long acc = 0;
                java.util.Random random = new java.util.Random(42 + seed);
                for (int op = 0; op < OPS_PER_THREAD; op++) {
                    int i = random.nextInt(mixed ? KEYSPACE * 2 : KEYSPACE);
                    BenchKey key = new BenchKey(parents[i % parents.length], i);
                    if (mixed && i >= KEYSPACE && (op % 5 == 0)) {
                        BenchValue existing = cache.putIfAbsent(key, new BenchValue(i));
                        acc ^= System.identityHashCode(existing);
                    } else {
                        BenchValue value = cache.get(key);
                        acc ^= System.identityHashCode(value);
                    }
                }
                BLACKHOLE.addAndGet(acc);
                threadCpu.addAndGet(bean.getCurrentThreadCpuTime() - cpu0);
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(10, TimeUnit.MINUTES);
        }
        long wallNs = System.nanoTime() - wall0;
        pool.shutdown();
        // charge async maintenance (CAFFEINE_ASYNC) to this cell
        ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
        long processCpuNs = processCpuNs() - processCpuBefore;
        long gcMillis = totalGcMillis() - gcBefore;

        long totalOps = (long) threads * OPS_PER_THREAD;
        return new CellResult(
            totalOps * 1_000_000_000L / Math.max(1, wallNs),
            threadCpu.get() / totalOps,
            Math.max(0, processCpuNs) / totalOps,
            gcMillis);
    }

    // ------------------------------------------------------------------
    // R1/R2 — retention under soft pressure, purge promptness
    // ------------------------------------------------------------------

    private void retentionAndPurge(List<String> report) {
        report.add("## R1/R2 Retention und Purge (Ketten Tiefe 5, 10% Blätter gepinnt)");
        report.add("");
        report.add("| Variante | size vor | size nach Druck | pinned-Hit-% | Purge-Messpunkte bis stabil |");
        report.add("|---|---|---|---|---|");
        int chains = 20_000;
        for (Variant variant : Variant.values()) {
            SimpleCache<BenchKey, BenchValue> cache = create(variant);
            List<BenchValue> pinnedLeaves = new ArrayList<>();
            List<BenchKey> pinnedLeafKeys = new ArrayList<>();
            for (int c = 0; c < chains; c++) {
                BenchValue parent = null;
                BenchKey key = null;
                BenchValue value = null;
                for (int depth = 0; depth < 5; depth++) {
                    key = new BenchKey(parent, c * 5 + depth);
                    value = new BenchValue(c);
                    cache.putIfAbsent(key, value);
                    parent = value;
                }
                if (c % 10 == 0) {
                    pinnedLeaves.add(value);
                    pinnedLeafKeys.add(key);
                }
            }
            int sizeBefore = cache.size();
            applySoftPressure();
            int sizeAfter = cache.size();

            int hits = 0;
            for (BenchKey key : pinnedLeafKeys) {
                if (cache.get(new BenchKey(key.parent, key.ordinal)) != null) {
                    hits++;
                }
            }
            // guarantee invariant: a strongly reachable value must answer
            assertThat(hits).as("%s: pinned leaves answer after pressure", variant)
                .isEqualTo(pinnedLeafKeys.size());

            int stableAfter = 0;
            int previous = sizeAfter;
            for (int probe = 1; probe <= 10; probe++) {
                System.gc();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                int now = cache.size();
                if (now != previous) {
                    stableAfter = probe;
                }
                previous = now;
            }
            report.add("| " + variant + " | " + sizeBefore + " | " + sizeAfter + " | "
                    + (100 * hits / pinnedLeafKeys.size()) + " | " + stableAfter + " |");
            BLACKHOLE.addAndGet(pinnedLeaves.size());
        }
        report.add("");
    }

    /** Allocates ballast until a canary soft reference yields, then frees it. */
    private void applySoftPressure() {
        SoftReference<Object> canary = new SoftReference<>(new Object());
        List<byte[]> ballast = new ArrayList<>();
        long cap = (long) (Runtime.getRuntime().maxMemory() * 0.7) / (1024 * 1024);
        try {
            while (canary.get() != null && ballast.size() < cap) {
                ballast.add(new byte[1024 * 1024]);
            }
        } catch (OutOfMemoryError e) {
            // the pressure point itself
        }
        ballast.clear();
        System.gc();
    }

    // ------------------------------------------------------------------

    private static long median(List<CellResult> results, java.util.function.ToLongFunction<CellResult> metric) {
        long[] values = results.stream().mapToLong(metric).sorted().toArray();
        return values.length == 0 ? 0 : values[values.length / 2];
    }

    private static long processCpuNs() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuTime();
        }
        return 0;
    }

    private static long totalGcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionTime)
                .sum();
    }

    // ------------------------------------------------------------------
    // H1/H2 — bounded contenders: hit rate + CPU under Zipf and scan mix
    // ------------------------------------------------------------------

    private static final long BOUNDED_CAP = FULL ? 10_000 : 1_000;
    private static final int BOUNDED_KEYSPACE = FULL ? 100_000 : 10_000;

    enum BoundedVariant {
        CAFFEINE_BOUNDED, OWN_BOUNDED
    }

    private static SimpleCache<Integer, int[]> createBounded(BoundedVariant variant) {
        return switch (variant) {
        case CAFFEINE_BOUNDED -> BoundedCache.weighted(BOUNDED_CAP, (k, v) -> v.length);
        case OWN_BOUNDED -> OwnBoundedCache.weighted(BOUNDED_CAP, (k, v) -> v.length);
        };
    }

    /** stable per-key weight 1..64 */
    private static int keyWeight(int index) {
        long z = index + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return 1 + (int) ((z ^ (z >>> 31)) & 63);
    }

    /** Zipf(s=1.0) CDF over the bounded keyspace, for binary-search sampling. */
    private static final double[] ZIPF_CDF = buildZipfCdf();

    private static double[] buildZipfCdf() {
        double[] cdf = new double[BOUNDED_KEYSPACE];
        double sum = 0;
        for (int i = 0; i < cdf.length; i++) {
            sum += 1.0 / (i + 1);
            cdf[i] = sum;
        }
        for (int i = 0; i < cdf.length; i++) {
            cdf[i] /= sum;
        }
        return cdf;
    }

    private static int zipfSample(java.util.Random random) {
        int idx = Arrays.binarySearch(ZIPF_CDF, random.nextDouble());
        return idx >= 0 ? idx : -idx - 1;
    }

    private record BoundedCellResult(long hitPermille, long threadCpuNsPerOp, long processCpuNsPerOp,
            long gcMillis) {
    }

    @Test
    void benchBoundedContenders() throws Exception {
        List<String> report = new ArrayList<>();
        report.add("# Bounded-Contender-Report (CacheContenderBenchTest)");
        report.add("");
        report.add("mode=" + (FULL ? "full" : "smoke") + " rounds=" + ROUNDS + " cap=" + BOUNDED_CAP
                + " keyspace=" + BOUNDED_KEYSPACE + " opsPerThread=" + OPS_PER_THREAD);
        report.add("");
        for (boolean scanMix : new boolean[] {false, true}) {
            for (int threads : THREAD_COUNTS) {
                report.add("## " + (scanMix ? "H2 zipf+scan 90/10" : "H1 zipf") + " threads=" + threads);
                report.add("");
                report.add("| Variante | Hit-Rate% med | CPU-ns/op Thread med | CPU-ns/op Prozess med | GC-ms med |");
                report.add("|---|---|---|---|---|");
                Map<BoundedVariant, List<BoundedCellResult>> series =
                        new java.util.EnumMap<>(BoundedVariant.class);
                for (BoundedVariant variant : BoundedVariant.values()) {
                    series.put(variant, new ArrayList<>());
                }
                BoundedVariant[] variants = BoundedVariant.values();
                for (int round = 0; round < WARMUP_ROUNDS + ROUNDS; round++) {
                    boolean measured = round >= WARMUP_ROUNDS;
                    for (int i = 0; i < variants.length; i++) {
                        BoundedVariant variant = variants[(round + i) % variants.length];
                        BoundedCellResult result = runBoundedCell(variant, scanMix, threads);
                        if (measured) {
                            series.get(variant).add(result);
                        }
                    }
                }
                for (BoundedVariant variant : BoundedVariant.values()) {
                    List<BoundedCellResult> results = series.get(variant);
                    report.add("| " + variant
                            + " | " + (medianBounded(results, BoundedCellResult::hitPermille) / 10.0)
                            + " | " + medianBounded(results, BoundedCellResult::threadCpuNsPerOp)
                            + " | " + medianBounded(results, BoundedCellResult::processCpuNsPerOp)
                            + " | " + medianBounded(results, BoundedCellResult::gcMillis) + " |");
                }
                report.add("");
            }
        }
        report.add("blackhole=" + BLACKHOLE.get());
        Path out = Path.of("target", "cache-bench-bounded-report.md");
        Files.createDirectories(out.getParent());
        Files.write(out, report);
        System.out.println(String.join(System.lineSeparator(), report));
    }

    private BoundedCellResult runBoundedCell(BoundedVariant variant, boolean scanMix, int threads)
            throws Exception {
        SimpleCache<Integer, int[]> cache = createBounded(variant);
        long gcBefore = totalGcMillis();
        long processCpuBefore = processCpuNs();
        AtomicLong threadCpu = new AtomicLong();
        AtomicLong hits = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            futures.add(pool.submit(() -> {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long cpu0 = bean.getCurrentThreadCpuTime();
                long localHits = 0;
                long acc = 0;
                int scanCursor = seed * (BOUNDED_KEYSPACE / Math.max(1, 8));
                java.util.Random random = new java.util.Random(42 + seed);
                for (int op = 0; op < OPS_PER_THREAD; op++) {
                    int index = (scanMix && op % 10 == 0)
                        ? (scanCursor++ % BOUNDED_KEYSPACE)
                        : zipfSample(random);
                    Integer key = index;
                    int[] value = cache.get(key);
                    if (value == null) {
                        cache.putIfAbsent(key, new int[keyWeight(index)]);
                    } else {
                        localHits++;
                        acc ^= value.length;
                    }
                }
                hits.addAndGet(localHits);
                BLACKHOLE.addAndGet(acc);
                threadCpu.addAndGet(bean.getCurrentThreadCpuTime() - cpu0);
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(10, TimeUnit.MINUTES);
        }
        pool.shutdown();
        ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
        long processCpuNs = processCpuNs() - processCpuBefore;
        long gcMillis = totalGcMillis() - gcBefore;
        long totalOps = (long) threads * OPS_PER_THREAD;

        // weight-account drift detector for the hand-built contender
        if (cache instanceof OwnBoundedCache<Integer, int[]> own) {
            assertThat(own.weightUsed())
                .as("weight account within cap plus in-flight tolerance")
                .isLessThanOrEqualTo(BOUNDED_CAP + 8L * 64);
        }
        return new BoundedCellResult(
            hits.get() * 1000 / totalOps,
            threadCpu.get() / totalOps,
            Math.max(0, processCpuNs) / totalOps,
            gcMillis);
    }

    private static long medianBounded(List<BoundedCellResult> results,
            java.util.function.ToLongFunction<BoundedCellResult> metric) {
        long[] values = results.stream().mapToLong(metric).sorted().toArray();
        return values.length == 0 ? 0 : values[values.length / 2];
    }
}
