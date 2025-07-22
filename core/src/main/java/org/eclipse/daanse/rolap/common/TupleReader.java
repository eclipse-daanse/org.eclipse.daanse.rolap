/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common;

import java.sql.SQLException;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;

/**
 * Describes the public methods of {@link org.eclipse.daanse.rolap.common.SqlTupleReader}.
 *
 * @author av
 * @since Nov 21, 2005
 */
public interface TupleReader {
    /**
     * Factory to create new members for a
     * hierarchy from SQL result.
     *
     * @author av
     * @since Nov 11, 2005
     */
    public interface MemberBuilder {

        /**
         * Returns the MemberCache to look up members before
         * creating them.
         *
         * @return member cache
         */
        MemberCache getMemberCache();

        /**
         * Returns the object which acts as the member cache
         * synchronization lock.
         *
         * @return Object to lock
         */
        Object getMemberCacheLock();


        /**
         * Creates a new member (together with its properties).
         *
         * @param parentMember Parent member
         * @param childLevel Child level
         * @param value Member value
         * @param captionValue Caption
         * @param parentChild Whether a parent-child hierarchy
         * @param stmt SQL statement
         * @param key Member key
         * @param column Column ordinal (0-based)
         * @return new member
         * @throws java.sql.SQLException on error
         */
        RolapMember makeMember(
            RolapMember parentMember,
            RolapLevel childLevel,
            Object value,
            Object captionValue,
            boolean parentChild,
            SqlStatement stmt,
            Object key,
            int column)
            throws SQLException;

        /**
         * Returns the 'all' member of the hierarchy.
         *
         * @return The 'all' member
         */
        RolapMember allMember();
    }

    /**
     * Adds a hierarchy to retrieve members from.
     *
     * @param level level that the members correspond to
     * @param memberBuilder used to build new members for this level
     * @param srcMembers if set, array of enumerated members that make up
     *     this level
     */
    void addLevelMembers(
        RolapLevel level,
        MemberBuilder memberBuilder,
        List<RolapMember> srcMembers);

    /**
     * Performs the read.
     *
     * @param context context
     * @param partialResult List of rows from previous pass
     * @param newPartialResult Populated with a new list of rows
     *
     * @return a list of tuples
     */
    TupleList readTuples(
        Context context,
        TupleList partialResult,
        List<List<RolapMember>> newPartialResult);

    /**
     * Performs the read.
     *
     * @param context context
     * @param partialResult partially cached result that should be used
     * instead of executing sql query
     * @param newPartialResult if non-null, return the result of the read;
     * note that this is a subset of the full return list

     * @return a list of RolapMember
     */
    TupleList readMembers(
        Context context,
        TupleList partialResult,
        List<List<RolapMember>> newPartialResult);

    /**
     * Returns an object that uniquely identifies the Result that this
     * {@link TupleReader} would return. Clients may use this as a key for
     * caching the result.
     *
     * @return Cache key
     */
    Object getCacheKey();

    /**
     * Indicates that there was an empty argument somewhere in the tuple.
     */
    void incrementEmptySets();

}
