/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.element.VisualTotalMemberImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;

/**
 * The relational {@link VisualTotalMemberImpl}: the total itself is provider-neutral, what a
 * relational catalog adds is that it is a {@link RolapMember}.
 * <p>
 * Everything a relational member is asked for comes from the member the total stands for, so the
 * total answers with that member's hierarchy, level, parent and all-ness. That is also what the
 * relational paths do with it: a predicate over a visual total is built from the member behind it,
 * never from the total.
 */
public class VisualTotalMember extends VisualTotalMemberImpl implements RolapMember {

    public VisualTotalMember(Member member, String name, String caption, Expression exp) {
        super(member, name, caption, exp);
    }

    private RolapMember wrapped() {
        return (RolapMember) getMember();
    }

    @Override
    public RolapMember getParentMember() {
        return wrapped().getParentMember();
    }

    @Override
    public RolapHierarchy getHierarchy() {
        return wrapped().getHierarchy();
    }

    @Override
    public RolapLevel getLevel() {
        return wrapped().getLevel();
    }

    @Override
    public boolean isAllMember() {
        return wrapped().isAllMember();
    }
}
