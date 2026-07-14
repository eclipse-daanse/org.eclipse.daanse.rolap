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
 * The routing layer between the engine's read seams and the {@code sqlbuild} mappers: one router
 * per query kind turns the (level shape, constraint contribution) pair into a routing decision —
 * an authoritative builder call, the recorder fallback where one still exists, or the defensive
 * throw. Routing is DATA here (a sealed {@code Route}), so the ladders are unit-testable without
 * a database.
 * <p>
 * Bundle-private like {@code ..sqlbuild} itself.
 */
package org.eclipse.daanse.rolap.common.sqlbuild.route;
