/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2019 Hitachi Vantara..  All rights reserved.
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


package org.eclipse.daanse.rolap.util;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.daanse.olap.common.Util;

/**
 * Partially-ordered set.
 *
 * When you create a partially-ordered set ('poset' for short) you must
 * provide an {@link Ordering} that determines the order relation. The
 * ordering must be:
 *
 *
 *     reflexive: e.lte(e) returns true;
 *     anti-symmetric: if e.lte(f) returns true,
 *     then f.lte(e) returns false only if e = f;
 *     transitive: if e.lte(f) returns true and
 *     f.lte(g) returns true, then e.lte(g) must return true.
 *
 *
 * Note that not all pairs of elements are related. If is OK if e.lte(f)
 * returns false and f.lte(e) returns false also.
 *
 * In addition to the usual set methods, there are methods to determine the
 * immediate parents and children of an element in the set, and method to find
 * all elements which have no parents or no children (i.e. "root" and "leaf"
 * elements).
 *
 * A lattice is a special kind of poset where there is a unique top and
 * bottom element. You can use a PartiallyOrderedSet for a lattice also. It may
 * be helpful to add the top and bottom elements to the poset on
 * construction.
 *
 * @param <E> Element type
 *
 * @author Julian Hyde
 */
public class PartiallyOrderedSet<E> extends AbstractSet<E>
{
    private final Map<E, Node<E>> map;
    private final Ordering<E> ordering;

    /**
     * Synthetic node to hold all nodes that have no parents. It does not appear
     * in the set.
     */
    private final Node<E> topNode;
    private final Node<E> bottomNode;

    private static final boolean DEBUG = false;

    /**
     * Creates a partially-ordered set.
     *
     * @param ordering Ordering relation
     */
    public PartiallyOrderedSet(Ordering<E> ordering) {
        this(ordering, new HashMap<E, Node<E>>());
    }

    /**
     * Creates a partially-ordered set, and populates it with a given
     * collection.
     *
     * @param ordering Ordering relation
     * @param collection Initial contents of partially-ordered set
     */
    public PartiallyOrderedSet(Ordering<E> ordering, Collection<E> collection) {
        this(ordering, new HashMap<E, Node<E>>(collection.size() * 3 / 2));
        addAll(collection);
    }

    /**
     * Internal constructor.
     *
     * @param ordering Ordering relation
     * @param map Map from values to nodes
     */
    private PartiallyOrderedSet(Ordering<E> ordering, Map<E, Node<E>> map) {
        this.ordering = ordering;
        this.map = map;
        this.topNode = new TopBottomNode<>(true);
        this.bottomNode = new TopBottomNode<>(false);
        this.topNode.childList.add(bottomNode);
        this.bottomNode.parentList.add(topNode);
    }

    @Override
    public Iterator<E> iterator() {
        final Iterator<E> iterator = map.keySet().iterator();
        return new Iterator<>() {
            E previous;

            @Override
			public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
			public E next() {
                return previous = iterator.next();
            }

            @Override
			public void remove() {
                if (!PartiallyOrderedSet.this.remove(previous)) {
                    // Object was not present.
                    // Maybe they have never called 'next'?
                    // Maybe they called 'remove' twice?
                    // Either way, something is screwy.
                    throw new IllegalStateException();
                }
            }
        };
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean contains(Object o) {
        //noinspection SuspiciousMethodCalls
        return map.containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
        @SuppressWarnings("SuspiciousMethodCalls")
        final Node<E> node = map.remove(o);
        if (node == null) {
            return false;
        }
        for (int i = 0; i < node.parentList.size(); i++) {
            Node<E> parent = node.parentList.get(i);
            for (Node<E> child : node.childList) {
                if (parent.e == null && child.e == null) {
                    parent.childList.remove(node);
                    continue;
                }
                replace(parent.childList, node, child);
            }
        }
        for (int i = 0; i < node.childList.size(); i++) {
            Node<E> child = node.childList.get(i);
            for (Node<E> parent : node.parentList) {
                if (child.e == null && parent.e == null) {
                    child.parentList.remove(node);
                    continue;
                }
                replace(child.parentList, node, parent);
            }
        }
        return true;
    }

    /**
     * Adds an element to this lattice.
     */
    @Override
    public boolean add(E e) {
        if (e == null) {
            return false;
        }
        assert !DEBUG || isValid(true);
        Node<E> node = map.get(e);
        if (node != null) {
            // already present
            return false;
        }
        Set<Node<E>> parents = findParents(e);
        Set<Node<E>> children = findChildren(e);

        node = new Node<>(e);

        for (Node<E> parent : parents) {
            node.parentList.add(parent);
            int n = 0;
            for (int i = 0; i < parent.childList.size(); i++) {
                Node<E> child = parent.childList.get(i);
                if (child.e == null || ordering.lessThan(child.e, e)) {
                    if (parent.childList.contains(node)) {
                        parent.childList.remove(i);
                        --i;
                    } else {
                        parent.childList.set(i, node);
                    }
                    replace(child.parentList, parent, node);
                    if (!node.childList.contains(child)) {
                        node.childList.add(child);
                    }
                    ++n;
                }
            }
            if (n == 0) {
                parent.childList.add(node);
            }
        }

        // Nodes reachable from parents.
        final Set<Node<E>> childSet = new HashSet<>(node.childList);
        for (Node<E> child : children) {
            if (!isDescendantOfAny(child, childSet)) {
                node.childList.add(child);
                if (!child.parentList.contains(node)) {
                    child.parentList.add(node);
                }
            }
        }

        map.put(node.e, node);
        assert !DEBUG || isValid(true);
        return true;
    }

    /**
     * Returns whether node's value is a descendant of any of the values in
     * nodeSet.
     *
     * @param node Node
     * @param nodeSet Suspected ancestors
     * @return Whether node is a descendant of any of the nodes
     */
    private boolean isDescendantOfAny(
        Node<E> node,
        Set<Node<E>> nodeSet)
    {
        final ArrayDeque<Node<E>> deque = new ArrayDeque<>();
        final Set<Node<E>> seen = new HashSet<>();
        deque.add(node);
        while (!deque.isEmpty()) {
            final Node<E> node1 = deque.pop();
            if (nodeSet.contains(node1)) {
                return true;
            }
            for (Node<E> parent : node1.parentList) {
                if (seen.add(parent)) {
                    deque.add(parent);
                }
            }
        }
        return false;
    }

    private Set<Node<E>> findChildren(E e) {
        ArrayDeque<Node<E>> descendants = new ArrayDeque<>();
        descendants.add(bottomNode);
        return findParentsChildren(e, descendants, false);
    }

    private Set<Node<E>> findParents(E e) {
        ArrayDeque<Node<E>> ancestors = new ArrayDeque<>();
        ancestors.add(topNode);
        return findParentsChildren(e, ancestors, true);
    }

    private Set<Node<E>> findParentsChildren(
        E e,
        ArrayDeque<Node<E>> ancestors,
        boolean up)
    {
        final Set<Node<E>> parents = new HashSet<>();
        while (!ancestors.isEmpty()) {
            final Node<E> ancestor = ancestors.pop();
            assert ancestor.e == null
                || (up
                    ? !ordering.lessThan(ancestor.e, e)
                    : !ordering.lessThan(e, ancestor.e));
            assert ancestor.e != e; // e not in tree yet
            // Example: e = "a", ancestor = "abc".
            // If there is at least one child c of ancestor such that e <= c
            // (e.g. "ab", "ac") examine those children. If there are no
            // such children, ancestor becomes a parent
            int found = 0;
            for (Node<E> child
                : (up ? ancestor.childList : ancestor.parentList))
            {
                if (child.e == null) {
                    continue; // child is the bottom node
                }
                if (up
                    ? ordering.lessThan(e, child.e)
                    : ordering.lessThan(child.e, e))
                {
                    ancestors.add(child);
                    ++found;
                } else if (up
                    ? !ordering.lessThan(child.e, e)
                    : !ordering.lessThan(e, child.e))
                {
                    // e is not less than child (and therefore some descendant
                    // of child might be less than e). Recurse into its
                    // children.
                    ancestors.add(child);
                }
            }
            if (found == 0) {
                // None of the descendants of the node are greater than e.
                // Therefore node is a parent, provided that e is definitely
                // less than node
                if (ancestor.e == null
                    || (up
                        ? ordering.lessThan(e, ancestor.e)
                        : ordering.lessThan(ancestor.e, e)))
                {
                    parents.add(ancestor);
                }
            }
        }
        return parents;
    }

    private <T> void replace(List<T> list, T remove, T add) {
        if (list.contains(add)) {
            list.remove(remove);
        } else {
            final int index = list.indexOf(remove);
            if (index >= 0) {
                list.set(index, add);
            } else {
                list.add(add);
            }
        }
    }

    /**
     * Checks internal consistency of this lattice.
     *
     * @param fail Whether to throw an assertion error
     * @return Whether valid
     */
    @SuppressWarnings({"ConstantConditions"})
    public boolean isValid(boolean fail) {
        // Top has no parents.
        // Bottom has no children.
        // Each node except top has at least one parent.
        // Each node except bottom has at least one child.
        // Every node's children list it as a parent.
        // Every node's parents list it as a child.
        for (Node<E> node : map.values()) {
            if ((node == topNode)
                != (node.parentList.isEmpty()))
            {
                assert !fail
                    : new StringBuilder("only top node should have no parents ").append(node)
                      .append(", parents ").append(node.parentList).toString();
                return false;
            }
            if ((node == bottomNode)
                != (node.childList.isEmpty()))
            {
                assert !fail
                    : new StringBuilder("only bottom node should have no children ").append(node)
                      .append(", children ").append(node.childList).toString();
                return false;
            }
            for (int i = 0; i < node.childList.size(); i++) {
                Node<E> child = node.childList.get(i);
                if (!child.parentList.contains(node)) {
                    assert !fail
                        : new StringBuilder("child ").append(child).append(" of ").append(node)
                        .append(" does not know its parent").toString();
                    return false;
                }
                if (child.e != null && !ordering.lessThan(child.e, node.e)) {
                    assert !fail
                        : new StringBuilder("child ").append(child.e).append(" not less than parent ")
                        .append(node.e).toString();
                    return false;
                }
                for (int i2 = 0; i2 < node.childList.size(); i2++) {
                    Node<E> child2 = node.childList.get(i2);
                    if (child == child2
                        && i != i2)
                    {
                        assert !fail
                            : new StringBuilder("duplicate child ").append(child)
                            .append(" of parent ").append(node).toString();
                        return false;
                    }
                    if (child.e != null
                        && child2.e != null
                        && child != child2
                        && ordering.lessThan(child.e, child2.e))
                    {
                        assert !fail
                            : new StringBuilder("relation between children ").append(child.e)
                              .append(" and ").append(child2.e).append(" of node ").append(node.e).toString();
                        return false;
                    }
                }
            }
            for (Node<E> parent : node.parentList) {
                if (!parent.childList.contains(node)) {
                    assert !fail;
                    return false;
                }
            }
        }
        final Map<Node, Integer> distanceToRoot =
            new HashMap<>();
        distanceRecurse(distanceToRoot, topNode, 0);
        for (Node<E> node : map.values()) {
            if (!distanceToRoot.containsKey(node)) {
                assert !fail : new StringBuilder("node ").append(node).append(" is not reachable").toString();
                return false;
            }
        }

        // For each pair of elements, ensure that elements are related if and
        // only if they are in the ancestors or descendants lists.
        Map<Node<E>, Set<E>> nodeAncestors = new HashMap<>();
        Map<Node<E>, Set<E>> nodeDescendants = new HashMap<>();
        for (Node<E> node : map.values()) {
            nodeAncestors.put(
                node,
                new HashSet<>(getAncestors(node.e)));
            nodeDescendants.put(
                node,
                new HashSet<>(getDescendants(node.e)));
        }
        for (Node<E> node1 : map.values()) {
            for (Node<E> node2 : map.values()) {
                final boolean lt12 = ordering.lessThan(node1.e, node2.e);
                final boolean lt21 = ordering.lessThan(node2.e, node1.e);
                if (node1 == node2) {
                    if (!(lt12 && lt21)) {
                        assert !fail
                            : "self should be less than self: " + node1;
                    }
                }
                if (lt12 && lt21) {
                    if (!(node1 == node2)) {
                        assert !fail
                            : new StringBuilder("node ").append(node1.e).append(" and node ").append(node2.e)
                              .append(" are less than each other but are not the same")
                              .append(" value").toString();
                        return false;
                    }
                }
                if (lt12 && !lt21) {
                    if (!nodeAncestors.get(node1).contains(node2.e)) {
                        assert !fail
                            : new StringBuilder().append(node1.e).append(" is less than ").append(node2.e)
                            .append(" but ")
                            .append(node2.e).append(" is not in the ancestor set of ")
                            .append(node1.e).toString();
                        return false;
                    }
                    if (!nodeDescendants.get(node2).contains(node1.e)) {
                        assert !fail
                            : new StringBuilder().append(node1.e).append(" is less than ").append(node2.e)
                            .append(" but ")
                            .append(node1.e).append(" is not in the descendant set of ")
                              .append(node2.e).toString();
                        return false;
                    }
                }
                if (lt21 && !lt12) {
                    if (!nodeAncestors.get(node2).contains(node1.e)) {
                        assert !fail
                            : new StringBuilder().append(node2.e).append(" is less than ").append(node1.e)
                            .append(" but ")
                            .append(node1.e).append(" is not in the ancestor set of ")
                            .append(node2.e).toString();
                        return false;
                    }
                    if (!nodeDescendants.get(node1).contains(node2.e)) {
                        assert !fail
                            : new StringBuilder().append(node2.e).append(" is less than ").append(node1.e)
                            .append(" but ")
                            .append(node2.e).append(" is not in the descendant set of ")
                            .append(node1.e).toString();
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void distanceRecurse(
        Map<Node, Integer> distanceToRoot,
        Node<E> node,
        int distance)
    {
        final Integer best = distanceToRoot.get(node);
        if (best == null || distance < best) {
            distanceToRoot.put(node, distance);
        }
        if (best != null) {
            return;
        }
        for (Node<E> child : node.childList) {
            distanceRecurse(
                distanceToRoot,
                child,
                distance + 1);
        }
    }

    public void out(StringBuilder buf) {
        buf.append("PartiallyOrderedSet size: ");
        buf.append(size());
        buf.append(" elements: {");
        buf.append(Util.NL);

        // breadth-first search, to iterate over every element once, printing
        // those nearest the top element first
        final HashSet<E> seen = new HashSet<>();
        final ArrayDeque<E> unseen = new ArrayDeque<>();
        unseen.addAll(getNonChildren());
        while (!unseen.isEmpty()) {
            E e = unseen.pop();
            buf.append("  ");
            buf.append(e);
            buf.append(" parents: ");
            final List<E> parents = getParents(e);
            buf.append(parents);
            buf.append(" children: ");
            final List<E> children = getChildren(e);
            buf.append(children);
            buf.append(Util.NL);

            for (E child : children) {
                if (seen.add(child)) {
                    unseen.add(child);
                }
            }
        }
        buf.append("}");
    }

    /**
     * Returns the values in this partially-ordered set that are less-than
     * a given value and there are no intervening values.
     *
     * If the value is not in this set, returns the empty list.
     *
     * @see #getDescendants
     *
     * @param e Value
     * @return List of values in this set that are directly less than the given
     *   value
     */
    public List<E> getChildren(E e) {
        final Node<E> node = map.get(e);
        if (node == null) {
            return null;
        } else if (node.childList.get(0).e == null) {
            // child list contains bottom element, so officially there are no
            // children
            return Collections.emptyList();
        } else {
            return new StripList<>(node.childList);
        }
    }

    /**
     * Returns the values in this partially-ordered set that are greater-than
     * a given value and there are no intervening values.
     *
     * If the value is not in this set, returns the empty list.
     *
     * @see #getAncestors
     *
     * @param e Value
     * @return List of values in this set that are directly greater than the
     *   given value
     */
    public List<E> getParents(E e) {
        final Node<E> node = map.get(e);
        if (node == null) {
            return null;
        } else if (node.parentList.get(0).e == null) {
            // parent list contains top element, so officially there are no
            // parents
            return Collections.emptyList();
        } else {
            return new StripList<>(node.parentList);
        }
    }

    public List<E> getNonChildren() {
        if (topNode.childList.size() == 1
            && topNode.childList.get(0).e == null)
        {
            return Collections.emptyList();
        }
        return new StripList<>(topNode.childList);
    }

    public List<E> getNonParents() {
        if (bottomNode.parentList.size() == 1
            && bottomNode.parentList.get(0).e == null)
        {
            return Collections.emptyList();
        }
        return new StripList<>(bottomNode.parentList);
    }

    @Override
    public void clear() {
        map.clear();
        assert topNode.parentList.isEmpty();
        topNode.childList.clear();
        topNode.childList.add(bottomNode);
        assert bottomNode.childList.isEmpty();
        bottomNode.parentList.clear();
        bottomNode.parentList.add(topNode);
    }

    /**
     * Returns a list of values in the set that are less-than a given value.
     * The list is in topological order but order is otherwise
     * non-deterministic.
     *
     * @param e Value
     * @return Values less than given value
     */
    public List<E> getDescendants(E e) {
        return descendants(e, true);
    }

    /**
     * Returns a list of values in the set that are less-than a given value.
     * The list is in topological order but order is otherwise
     * non-deterministic.
     *
     * @param e Value
     * @return Values less than given value
     */
    public List<E> getAncestors(E e) {
        return descendants(e, false);
    }

    private List<E> descendants(E e, boolean up) {
        Node<E> node = map.get(e);
        final Collection<Node<E>> c;
        if (node == null) {
            c = up ? findChildren(e) : findParents(e);
        } else {
            c = up ? node.childList : node.parentList;
        }
        if (c.size() == 1 && c.iterator().next().e == null) {
            return Collections.emptyList();
        }
        ArrayDeque<Node<E>> deque = new ArrayDeque<>(c);

        final Set<Node<E>> seen = new HashSet<>();
        final List<E> list = new ArrayList<>();
        while (!deque.isEmpty()) {
            Node<E> node1 = deque.pop();
            if (node1.e == null) {
                // Node is top or bottom.
                continue;
            }
            list.add(node1.e);
            for (Node<E> child : up ? node1.childList : node1.parentList) {
                if (seen.add(child)) {
                    deque.add(child);
                }
            }
        }
        return list;
    }

    /**
     * Holds a value, its parent nodes, and child nodes.
     *
     * We deliberately do not override {@link #hashCode} or
     * {@link #equals(Object)}. A canonizing map ensures that within a
     * given PartiallyOrderedSet, two nodes are identical if and only if they
     * contain the same value.
     *
     * @param <E> Element type
     */
    private static class Node<E> {
        final List<Node<E>> parentList = new ArrayList<>();
        final List<Node<E>> childList = new ArrayList<>();
        final E e;

        public Node(E e) {
            this.e = e;
        }

        @Override
        public String toString() {
            return e.toString();
        }
    }

    /**
     * Subclass of Node for top/bottom nodes. Improves readability when
     * debugging.
     *
     * @param <E> Element type
     */
    private static class TopBottomNode<E> extends Node<E> {
        private final String description;

        public TopBottomNode(boolean top) {
            super(null);
            this.description = top ? "top" : "bottom";
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Ordering relation.
     *
     * To obey the constraints of the partially-ordered set, the function
     * must be consistent with the reflexive, anti-symmetric, and transitive
     * properties required by a partially ordered set.
     *
     * For instance, if {@code ordering(foo, foo)} returned false for any
     * not-null value of foo, it would violate the reflexive property.
     *
     * If an ordering violates any of these required properties, the behavior
     * of a {@link PartiallyOrderedSet} is unspecified. (But mayhem is
     * likely.)
     *
     * @param <E> Element type
     */
    public interface Ordering<E> {
        /**
         * Returns whether element e1 is &le; e2 according to
         * the relation that defines a partially-ordered set.
         *
         * @param e1 Element 1
         * @param e2 Element 2
         * @return Whether element 1 is &le; element 2
         */
        boolean lessThan(E e1, E e2);
    }

    private static class StripList<E> extends AbstractList<E> {
        private final List<Node<E>> list;

        public StripList(List<Node<E>> list) {
            this.list = list;
        }

        @Override
        public E get(int index) {
            return list.get(index).e;
        }

        @Override
        public int size() {
            return list.size();
        }
    }

    /**
     * Cut-down version of java.util.ArrayDeque, which is not available until
     * JDK 1.6.
     *
     * @param <E> Element type
     */
    private static class ArrayDeque<E> {
        private E[] es; // length must be multiple of 2
        private int first;
        private int last;

        public ArrayDeque() {
            this(16);
        }

        public ArrayDeque(Collection<E> nodes) {
            this(nextPowerOf2(nodes.size()));
            addAll(nodes);
        }

        private ArrayDeque(int capacity) {
            first = last = 0;
            //noinspection unchecked
            es = (E[]) new Object[capacity];
        }

        private static int nextPowerOf2(int v) {
            // Algorithm from
            // http://graphics.stanford.edu/~seander/bithacks.html
            v--;
            v |= v >> 1;
            v |= v >> 2;
            v |= v >> 4;
            v |= v >> 8;
            v |= v >> 16;
            v++;
            return v;
        }

        @SuppressWarnings({"SuspiciousSystemArraycopy", "unchecked"})
        private void expand() {
            Object[] olds = es;
            es = (E[]) new Object[es.length * 2];
            System.arraycopy(olds, 0, es, 0, olds.length);
            if (last <= first) {
                final int x = last & (olds.length - 1);
                System.arraycopy(olds, 0, es, olds.length, x);
                last += olds.length;
            }
        }

        public void add(E e) {
            es[last] = e;
            last = (last + 1) & (es.length - 1);
            if (last == first) {
                expand();
            }
        }

        public boolean isEmpty() {
            return last == first;
        }


        public E pop() {
            if (last == first) {
                throw new NoSuchElementException();
            }
            E e = es[first];
            first = (first + 1) & (es.length - 1);
            return e;
        }


        public void addAll(Collection<E> list) {
            for (E e : list) {
                add(e);
            }
        }
    }
}
