/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common.agg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * Base class for {@link AndPredicate} and {@link OrPredicate}.
 *
 * @see org.eclipse.daanse.rolap.common.agg.ListColumnPredicate
 *
 * @author jhyde
 */
public abstract class ListPredicate implements StarPredicate {
    protected final List<StarPredicate> children =
        new ArrayList<>();

    /**
     * Hash map of children predicates, keyed off of the hash code of each
     * child.  Each entry in the map is a list of predicates matching that
     * hash code.
     */
    private HashMap<Integer, List<StarPredicate>> childrenHashMap;

    /**
     * Pre-computed hash code for this list column predicate
     */
    private int hashValue;

    protected final List<RolapStar.Column> columns;

    private BitKey columnBitKey = null;

    protected ListPredicate(List<StarPredicate> predicateList) {
        childrenHashMap = null;
        hashValue = 0;
        // Ensure that columns are sorted by bit-key, for determinacy.
        final SortedSet<RolapStar.Column> columnSet =
            new TreeSet<>(RolapStar.Column.COMPARATOR);
        for (StarPredicate predicate : predicateList) {
            children.add(predicate);
            columnSet.addAll(predicate.getConstrainedColumnList());
        }
        columns = new ArrayList<>(columnSet);
    }

    @Override
	public List<RolapStar.Column> getConstrainedColumnList() {
        return columns;
    }

    @Override
	public BitKey getConstrainedColumnBitKey() {
        if (columnBitKey == null) {
            for (StarPredicate predicate : children) {
                if (columnBitKey == null) {
                    columnBitKey =
                        predicate.getConstrainedColumnBitKey().copy();
                } else {
                    columnBitKey =
                        columnBitKey.or(predicate.getConstrainedColumnBitKey());
                }
            }
        }
        return columnBitKey;
    }

    public List<StarPredicate> getChildren() {
        return children;
    }

    @Override
	public int hashCode() {
        // Don't use the default list hashcode because we want a hash code
        // that's not order dependent
        if (hashValue == 0) {
            hashValue = 37;
            for (StarPredicate child : children) {
                int childHashCode = child.hashCode();
                if (childHashCode != 0) {
                    hashValue *= childHashCode;
                }
            }
            hashValue ^= children.size();
        }
        return hashValue;
    }

    @Override
	public boolean equalConstraint(StarPredicate that) {
        boolean isEqual =
            that instanceof ListPredicate
            && getConstrainedColumnBitKey().equals(
                that.getConstrainedColumnBitKey());

        if (isEqual) {
            ListPredicate thatPred = (ListPredicate) that;
            if (!getOp().equals(thatPred.getOp())
                || getChildren().size() != thatPred.getChildren().size())
            {
                isEqual = false;
            }

            if (isEqual) {
                // Create a hash map of the children predicates, if not
                // already done
                if (childrenHashMap == null) {
                    childrenHashMap =
                        new HashMap<>();
                    for (StarPredicate thisChild : getChildren()) {
                        Integer key = Integer.valueOf(thisChild.hashCode());
                        List<StarPredicate> predList = childrenHashMap.get(key);
                        if (predList == null) {
                            predList = new ArrayList<>();
                        }
                        predList.add(thisChild);
                        childrenHashMap.put(key, predList);
                    }
                }

                // Loop through thatPred's children predicates.  There needs
                // to be a matching entry in the hash map for each child
                // predicate.
                for (StarPredicate thatChild : thatPred.getChildren()) {
                    List<StarPredicate> predList =
                        childrenHashMap.get(thatChild.hashCode());
                    if (predList == null) {
                        isEqual = false;
                        break;
                    }
                    boolean foundMatch = false;
                    for (StarPredicate pred : predList) {
                        if (thatChild.equalConstraint(pred)) {
                            foundMatch = true;
                            break;
                        }
                    }
                    if (!foundMatch) {
                        isEqual = false;
                        break;
                    }
                }
            }
        }

        return isEqual;
    }

    @Override
	public StarPredicate minus(StarPredicate predicate) {
        throw Util.needToImplement(this);
    }

    @Override
	public void toSql(SqlQuery sqlQuery, StringBuilder buf) {
        if (children.size() == 1) {
            children.get(0).toSql(sqlQuery, buf);
        } else {
            int k = 0;
            buf.append("(");
            for (StarPredicate child : children) {
                if (k++ > 0) {
                    buf.append(" ").append(getOp()).append(" ");
                }
                child.toSql(sqlQuery, buf);
            }
            buf.append(")");
        }
    }

    protected abstract String getOp();

    @Override
	public void describe(StringBuilder buf) {
        buf.append(getOp()).append("(");
        int k = 0;
        for (StarPredicate child : children) {
            if (k++ > 0) {
                buf.append(", ");
            }
            buf.append(child);
        }
        buf.append(')');
    }


    @Override
	public String toString() {
        final StringBuilder buf = new StringBuilder();
        describe(buf);
        return buf.toString();
    }
}
