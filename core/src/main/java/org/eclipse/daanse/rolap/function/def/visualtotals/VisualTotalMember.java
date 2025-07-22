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
package org.eclipse.daanse.rolap.function.def.visualtotals;

import java.util.List;

import org.eclipse.daanse.mdx.model.api.expression.operation.BracesOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Validator;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.AbstractProperty;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.MemberExpressionImpl;
import org.eclipse.daanse.olap.query.component.UnresolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.RolapMemberBase;

/**
 * Calculated member for VisualTotals function.
 *
 * It corresponds to a real member, and most of its properties are
 * similar. The main differences are:
 * its name is derived from the VisualTotals pattern, e.g.
 *     "*Subtotal - Dairy" as opposed to "Dairy"
 * its value is a calculation computed by aggregating all of the
 *     members which occur following it in the list
 */
public class VisualTotalMember  extends RolapMemberBase {
    final Member member;
    private Expression exp;


    VisualTotalMember(
        Member member,
        String name,
        String caption,
        final Expression exp)
    {
        super(
            member.getParentMember(),
            member.getLevel(),
            Util.sqlNullValue, name, member.getMemberType() ==  MemberType.ALL ? MemberType.ALL : MemberType.FORMULA);
        this.member = member;
        this.caption = caption;
        this.exp = exp;
    }

    @Override
    public boolean equals(Object o) {
        // A visual total member must compare equal to the member it wraps
        // (for purposes of the MDX Intersect function, for instance).
        return o instanceof VisualTotalMember
            && this.member.equals(((VisualTotalMember) o).member)
            && this.exp.equals(((VisualTotalMember) o).exp)
            || o instanceof Member
            && this.member.equals(o);
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof VisualTotalMember) {
            // VisualTotals members are a special case. We have
            // to compare the delegate member.
            return this.getMember().compareTo(
                ((VisualTotalMember) o).getMember());
        } else {
            return super.compareTo(o);
        }
    }

    @Override
    public int hashCode() {
        return member.hashCode();
    }

    @Override
    public String getCaption() {
        return caption;
    }

    @Override
    protected boolean computeCalculated(final MemberType memberType) {
        return true;
    }

    @Override
    public int getSolveOrder() {
        // high solve order, so it is expanded after other calculations
        // REVIEW: 99...really?? I've seen many queries with higher SO.
        // I don't think we should be abusing arbitrary constants
        // like this.
        return 99;
    }

    @Override
    public Expression getExpression() {
        return exp;
    }

    public void setExpression(Expression exp) {
        this.exp = exp;
    }

    public void setExpression(
        Evaluator evaluator,
        List<Member> childMembers)
    {
        final Expression exp = makeExpr(childMembers);
        final Validator validator = evaluator.getQuery().createValidator();
        final Expression validatedExp = exp.accept(validator);
        setExpression(validatedExp);
    }

    private Expression makeExpr(final List childMemberList) {
        Expression[] memberExprs = new Expression[childMemberList.size()];
        for (int i = 0; i < childMemberList.size(); i++) {
            final Member childMember = (Member) childMemberList.get(i);
            memberExprs[i] = new MemberExpressionImpl(childMember);
        }
        return new UnresolvedFunCallImpl(
                 new FunctionOperationAtom(  "Aggregate"),
            new Expression[] {
                new UnresolvedFunCallImpl(
                    new BracesOperationAtom(),
                    memberExprs)
            });
    }

    @Override
    public int getOrdinal() {
        return member.getOrdinal();
    }

    @Override
    public Member getDataMember() {
        return member;
    }

    @Override
    public String getQualifiedName() {
        throw new UnsupportedOperationException();
    }

    public Member getMember() {
        return member;
    }

    @Override
    public Object getPropertyValue(String propertyName, boolean matchCase) {
        AbstractProperty property = StandardProperty.lookup(propertyName, matchCase);
        if (property == null) {
            return null;
        }

        if(property == StandardProperty.CHILDREN_CARDINALITY) {
            return member.getPropertyValue(propertyName, matchCase);}
        else {
        	return super.getPropertyValue(propertyName, matchCase);
        }
    }
}
