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
*   Stefan Bischof (bipolis.org) - initial
*/
/**
 * The dialect-free SQL-building layer for the ROLAP engine: it translates ROLAP structures
 * (stars, relations, levels, constraints) into the generic {@code org.eclipse.daanse.sql.statement}
 * model, which a {@code DialectSqlRenderer} then spells for a concrete dialect. These are the
 * expressive, purpose-specific builders that sit <em>behind</em> the engine's existing seams
 * ({@code MemberReader}, {@code TupleReader}, {@code QuerySpec}).
 * <p>
 * Cohesive translators:
 * <ul>
 *   <li>{@code JoinPlanner} — star columns, aggregate names, and {@code RolapStar.Condition} joins;</li>
 *   <li>{@code RelationFromMapper} — a hierarchy's {@code RelationalSource} tree → {@code FromClause};</li>
 *   <li>{@code MemberSqlMapper} — member-enumeration SELECTs for a level;</li>
 *   <li>{@code StarPredicateTranslator} — {@code StarPredicate} constraints → builder {@code Predicate}.</li>
 * </ul>
 * <p>
 * The shared SQL-text helpers ({@code SqlTextNormalizer}) and the structural statement comparator
 * ({@code StatementEquivalence}) live in {@code org.eclipse.daanse.sql.statement.compare}.
 */
//TODO: RM EXPORT
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("0.0.1")
package org.eclipse.daanse.rolap.common.sqlbuild;
