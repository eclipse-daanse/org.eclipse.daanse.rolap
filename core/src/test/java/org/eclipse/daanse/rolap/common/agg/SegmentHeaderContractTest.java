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
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.spi.SegmentPredicate;
import org.eclipse.daanse.olap.spi.SegmentRegion;
import org.eclipse.daanse.olap.util.ArraySortedSet;
import org.eclipse.daanse.olap.util.ByteString;
import org.junit.jupiter.api.Test;

class SegmentHeaderContractTest {

    private static final ByteString CHECKSUM = new ByteString("checksum-a".getBytes());

    private static SegmentColumn wildcard(String expression) {
        return new SegmentColumn(expression, 10, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static SegmentColumn valued(String expression, String... values) {
        return new SegmentColumn(expression, 10, (SortedSet) new ArraySortedSet<>(values));
    }

    private static SegmentHeader header(List<SegmentColumn> columns, List<SegmentColumn> regionColumns) {
        List<SegmentRegion> regions = regionColumns.stream()
                .map(c -> new SegmentRegion(List.of(c))).toList();
        return new SegmentHeader("Schema", CHECKSUM, "Cube", "Measure", columns, List.of(), "FACT",
                BitKey.Factory.makeBitKey(4), regions);
    }

    @Test
    void wildcardColumnsOverDifferentExpressionsDiffer() {
        assertThat(wildcard("a")).isNotEqualTo(wildcard("b"));
        assertThat(wildcard("a")).isEqualTo(wildcard("a"));
        assertThat(wildcard("a").hashCode()).isEqualTo(wildcard("a").hashCode());
    }

    @Test
    void twoColumnFlushBoxExcludesOnlyTheCombination() {
        SegmentHeader segment = header(
                List.of(valued("year", "2024", "2025"), valued("region", "East", "West")), List.of());
        SegmentColumn[] box = { valued("year", "2025"), valued("region", "West") };

        assertThat(segment.canConstrain(box)).isTrue();
        SegmentHeader constrained = segment.constrain(box);

        assertThat(constrained.isCellExcluded(Map.of("year", "2025", "region", "West"))).isTrue();
        assertThat(constrained.isCellExcluded(Map.of("year", "2025", "region", "East"))).isFalse();
        assertThat(constrained.isCellExcluded(Map.of("year", "2024", "region", "West"))).isFalse();
    }

    @Test
    void boxCoveringEveryTouchedAxisEntirelyCannotConstrain() {
        SegmentHeader segment = header(
                List.of(valued("year", "2025"), valued("region", "West")), List.of());
        SegmentColumn[] box = { valued("year", "2025"), valued("region", "West") };

        assertThat(segment.canConstrain(box)).isFalse();
    }

    @Test
    void headerEqualityIgnoresColumnAndRegionOrder() {
        SegmentColumn a = valued("a", "1");
        SegmentColumn b = valued("b", "2");
        SegmentHeader h1 = header(List.of(a, b), List.of(wildcard("x"), wildcard("y")));
        SegmentHeader h2 = header(List.of(b, a), List.of(wildcard("y"), wildcard("x")));

        assertThat(h1).isEqualTo(h2);
        assertThat(h1.hashCode()).isEqualTo(h2.hashCode());
        assertThat(h1.getUniqueID()).isEqualTo(h2.getUniqueID());
    }

    /**
     * Two regions over the SAME column with different values (the normal
     * shape after two flushes on one axis) must not make the uniqueID
     * depend on flush order - two nodes flushing in opposite order would
     * store the same segment under two different keys.
     */
    @Test
    void headerUniqueIdIgnoresRegionOrderOnTheSameColumn() {
        SegmentColumn year1997 = valued("year", "1997");
        SegmentColumn year1998 = valued("year", "1998");
        SegmentHeader h1 = header(List.of(wildcard("year")), List.of(year1997, year1998));
        SegmentHeader h2 = header(List.of(wildcard("year")), List.of(year1998, year1997));

        assertThat(h1.getUniqueID()).isEqualTo(h2.getUniqueID());
        assertThat(h1).isEqualTo(h2);
    }

    /**
     * The region order must not collapse on separator collisions: a comma
     * INSIDE a value ({"x,y"}) must not tie with two values ({"x","y"}) -
     * a tie fell back to insertion (= flush) order and the digest diverged
     * between nodes flushing in opposite order.
     */
    @Test
    void regionOrderSurvivesSeparatorCollisions() {
        SegmentColumn commaValue = valued("year", "x,y");
        SegmentColumn twoValues = valued("year", "x", "y");
        SegmentHeader h1 = header(List.of(wildcard("year")), List.of(commaValue, twoValues));
        SegmentHeader h2 = header(List.of(wildcard("year")), List.of(twoValues, commaValue));

        assertThat(h1.getUniqueID()).isEqualTo(h2.getUniqueID());
    }

    /**
     * Predicate digestion is structural: Values("c", ["a,b"]) and
     * Values("c", ["a","b"]) must produce DIFFERENT uniqueIDs - the flat
     * canonical string made them collide, and a digest collision here is
     * a mis-read, not just a miss.
     */
    @Test
    void predicateDigestionIsSeparatorSafe() {
        SegmentHeader one = headerWithPredicate(new SegmentPredicate.Values("c", List.of("a,b")));
        SegmentHeader two = headerWithPredicate(new SegmentPredicate.Values("c", List.of("a", "b")));

        assertThat(one.getUniqueID()).isNotEqualTo(two.getUniqueID());
    }

    private static SegmentHeader headerWithPredicate(SegmentPredicate predicate) {
        return new SegmentHeader("Schema", CHECKSUM, "Cube", "Measure",
                List.of(wildcard("c")), List.of(predicate), "FACT",
                BitKey.Factory.makeBitKey(4), List.of());
    }

    /**
     * Constraining is idempotent: re-flushing the SAME region must return
     * the identical header - previously every repeat appended a duplicate
     * excluded region, minting a fresh uniqueID (and a store rename plus
     * a cluster publication) on every cron run, forever.
     */
    @Test
    void repeatedConstrainWithTheSameBoxIsIdempotent() {
        SegmentHeader segment = header(
                List.of(valued("year", "2024", "2025"), valued("region", "East", "West")), List.of());
        SegmentColumn[] box = { valued("year", "2025"), valued("region", "West") };

        SegmentHeader once = segment.constrain(box);
        assertThat(once.canConstrain(box)).isTrue();
        SegmentHeader twice = once.constrain(box);

        assertThat(twice).isSameAs(once);
        assertThat(twice.getUniqueID()).isEqualTo(once.getUniqueID());

        // a smaller box inside the excluded region is also subsumed
        SegmentColumn[] inner = { valued("year", "2025"), valued("region", "West") };
        assertThat(once.constrain(inner)).isSameAs(once);

        // a DIFFERENT box still constrains
        SegmentColumn[] other = { valued("year", "2024"), valued("region", "East") };
        SegmentHeader different = once.constrain(other);
        assertThat(different).isNotEqualTo(once);
    }

    @Test
    void headerEqualityIsContentSensitive() {
        SegmentHeader h1 = header(List.of(valued("a", "1")), List.of());
        SegmentHeader h2 = header(List.of(valued("a", "2")), List.of());
        SegmentHeader differentChecksum = new SegmentHeader("Schema", new ByteString("checksum-b".getBytes()), "Cube",
                "Measure", List.of(valued("a", "1")), List.of(), "FACT", BitKey.Factory.makeBitKey(4), List.of());

        assertThat(h1).isNotEqualTo(h2);
        assertThat(h1).isNotEqualTo(differentChecksum);
    }

    @Test
    void serializedFormExcludesLazyFields() throws Exception {
        SegmentHeader plain = header(List.of(valued("a", "1")), List.of());
        int sizeBeforeMaterialization = serialize(plain).length;

        // materialize both lazy fields
        plain.getUniqueID();
        plain.getDescription();
        byte[] materialized = serialize(plain);

        assertThat(materialized.length).isEqualTo(sizeBeforeMaterialization);

        SegmentHeader roundTripped = deserialize(materialized);
        assertThat(roundTripped).isEqualTo(plain);
        assertThat(roundTripped.getUniqueID()).isEqualTo(plain.getUniqueID());
    }

    @Test
    void constructorCopiesLists() {
        List<SegmentColumn> columns = new ArrayList<>(List.of(valued("a", "1")));
        SegmentHeader h = header(columns, List.of());
        columns.add(valued("b", "2"));

        assertThat(h.getConstrainedColumns()).hasSize(1);
        assertThatThrownBy(() -> h.getConstrainedColumns().add(wildcard("c")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SegmentHeader header) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(header);
        }
        return baos.toByteArray();
    }

    private static SegmentHeader deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (SegmentHeader) ois.readObject();
        }
    }
}
