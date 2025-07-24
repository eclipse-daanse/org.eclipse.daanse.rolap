/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.olap.api.Evaluator;

/**
 * This is a special subclass of {@link DelegatingRolapMember}.
 * It is needed because {@link Evaluator} doesn't support multi cardinality
 * default members. RolapHierarchy.LimitedRollupSubstitutingMemberReader
 * .substitute() looks for this class and substitutes the
 * FIXME: If/when we refactor evaluator to support
 * multi cardinality default members, we can remove this.
 */
public class MultiCardinalityDefaultMember extends DelegatingRolapMember {
	
    public  MultiCardinalityDefaultMember(RolapMember member) {
        super(member);
    }
}