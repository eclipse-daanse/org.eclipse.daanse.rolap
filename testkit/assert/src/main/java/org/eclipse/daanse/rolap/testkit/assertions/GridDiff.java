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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Line-level side-by-side diff of two multi-line strings, rendered as plain text for
 * embedding in an assertion failure message.
 *
 * <p>
 * Lines are aligned with a classic LCS-based diff, then runs of matching lines longer
 * than {@link #CONTEXT} on each side are collapsed to a placeholder, so the rendered
 * block stays focused on the mismatching rows even for large MDX grids.
 */
final class GridDiff {

    private static final int COLUMN_WIDTH = 80;
    private static final int CONTEXT = 2;

    /** dp table is (n+1)*(m+1) ints; beyond this, skip alignment and compare index-for-index. */
    private static final long MAX_ALIGN_CELLS = 4_000_000L;

    private GridDiff() {
    }

    /** Renders {@code expected} vs. {@code actual} as a side-by-side block; empty string if they're equal. */
    static String render(String expected, String actual) {
        if (Objects.equals(expected, actual)) {
            return "";
        }
        String[] left = splitLines(expected);
        String[] right = splitLines(actual);

        List<Row> rows = (long) left.length * (long) right.length <= MAX_ALIGN_CELLS
                ? alignedRows(left, right)
                : zippedRows(left, right);
        List<Row> collapsed = collapseEqualRuns(rows);

        int width = Math.max(1, Math.min(COLUMN_WIDTH, widest(collapsed)));
        int leftNoWidth = Integer.toString(left.length).length();
        int rightNoWidth = Integer.toString(right.length).length();

        StringBuilder sb = new StringBuilder();
        for (Row row : collapsed) {
            if (row.collapse()) {
                sb.append("  ... (").append(row.collapsedCount()).append(" matching lines omitted) ...")
                        .append(System.lineSeparator());
                continue;
            }
            String marker = row.equal() ? "|" : "≠";
            sb.append(String.format(
                    "%" + leftNoWidth + "s %-" + width + "s %s %-" + width + "s %" + rightNoWidth + "s%n",
                    row.leftNo() == 0 ? "" : String.valueOf(row.leftNo()),
                    truncate(row.left(), width),
                    marker,
                    truncate(row.right(), width),
                    row.rightNo() == 0 ? "" : String.valueOf(row.rightNo())));
        }
        return sb.toString();
    }

    private static String[] splitLines(String s) {
        return s.split("\r\n|\r|\n", -1);
    }

    /** Aligns lines with an LCS backtrack, so unchanged lines line up even around inserted/removed rows. */
    private static List<Row> alignedRows(String[] left, String[] right) {
        int n = left.length;
        int m = right.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = left[i].equals(right[j]) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<Row> rows = new ArrayList<>();
        List<String> pendingDeletes = new ArrayList<>();
        List<Integer> pendingDeleteNos = new ArrayList<>();
        List<String> pendingInserts = new ArrayList<>();
        List<Integer> pendingInsertNos = new ArrayList<>();

        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && left[i].equals(right[j])) {
                flushChangeRun(rows, pendingDeletes, pendingDeleteNos, pendingInserts, pendingInsertNos);
                rows.add(Row.of(i + 1, left[i], j + 1, right[j], true));
                i++;
                j++;
            } else if (i < n && (j >= m || dp[i + 1][j] >= dp[i][j + 1])) {
                pendingDeletes.add(left[i]);
                pendingDeleteNos.add(i + 1);
                i++;
            } else {
                pendingInserts.add(right[j]);
                pendingInsertNos.add(j + 1);
                j++;
            }
        }
        flushChangeRun(rows, pendingDeletes, pendingDeleteNos, pendingInserts, pendingInsertNos);
        return rows;
    }

    /** Pairs up a run of deleted/inserted lines into change rows; leftover rows show a blank counterpart. */
    private static void flushChangeRun(List<Row> rows, List<String> deletes, List<Integer> deleteNos,
            List<String> inserts, List<Integer> insertNos) {
        int count = Math.max(deletes.size(), inserts.size());
        for (int k = 0; k < count; k++) {
            int leftNo = k < deletes.size() ? deleteNos.get(k) : 0;
            String left = k < deletes.size() ? deletes.get(k) : null;
            int rightNo = k < inserts.size() ? insertNos.get(k) : 0;
            String right = k < inserts.size() ? inserts.get(k) : null;
            rows.add(Row.of(leftNo, left, rightNo, right, false));
        }
        deletes.clear();
        deleteNos.clear();
        inserts.clear();
        insertNos.clear();
    }

    /** Fallback for inputs too large to align: pairs lines purely by index. */
    private static List<Row> zippedRows(String[] left, String[] right) {
        List<Row> rows = new ArrayList<>();
        int n = Math.max(left.length, right.length);
        for (int i = 0; i < n; i++) {
            String l = i < left.length ? left[i] : null;
            String r = i < right.length ? right[i] : null;
            rows.add(Row.of(l == null ? 0 : i + 1, l, r == null ? 0 : i + 1, r, Objects.equals(l, r)));
        }
        return rows;
    }

    private static List<Row> collapseEqualRuns(List<Row> rows) {
        List<Row> result = new ArrayList<>();
        int i = 0;
        while (i < rows.size()) {
            if (!rows.get(i).equal()) {
                result.add(rows.get(i));
                i++;
                continue;
            }
            int start = i;
            while (i < rows.size() && rows.get(i).equal()) {
                i++;
            }
            int runLength = i - start;
            if (runLength <= 2 * CONTEXT + 1) {
                result.addAll(rows.subList(start, i));
            } else {
                result.addAll(rows.subList(start, start + CONTEXT));
                result.add(Row.collapse(runLength - 2 * CONTEXT));
                result.addAll(rows.subList(i - CONTEXT, i));
            }
        }
        return result;
    }

    private static int widest(List<Row> rows) {
        int max = 0;
        for (Row row : rows) {
            if (row.collapse()) {
                continue;
            }
            if (row.left() != null) {
                max = Math.max(max, row.left().length());
            }
            if (row.right() != null) {
                max = Math.max(max, row.right().length());
            }
        }
        return max;
    }

    private static String truncate(String s, int width) {
        if (s == null) {
            return "";
        }
        if (s.length() <= width) {
            return s;
        }
        if (width <= 1) {
            return s.substring(0, width);
        }
        return s.substring(0, width - 1) + "…";
    }

    private record Row(boolean collapse, int collapsedCount, int leftNo, String left, int rightNo, String right,
            boolean equal) {

        static Row of(int leftNo, String left, int rightNo, String right, boolean equal) {
            return new Row(false, 0, leftNo, left, rightNo, right, equal);
        }

        static Row collapse(int count) {
            return new Row(true, count, 0, null, 0, null, true);
        }
    }
}
