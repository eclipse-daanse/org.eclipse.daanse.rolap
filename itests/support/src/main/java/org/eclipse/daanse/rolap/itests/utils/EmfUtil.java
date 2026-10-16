/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * History:
 *  This files came from the mondrian project. Some of the Flies
 *  (mostly the Tests) did not have License Header.
 *  But the Project is EPL Header. 2002-2022 Hitachi Vantara.
 *
 * Contributors:
 *   Hitachi Vantara.
 *   SmartCity Jena - initial  Java 8, Junit5
 */
package org.eclipse.daanse.rolap.itests.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

public class EmfUtil {

    /**
     * Copies of a catalog are made one at a time. The itests run classes and methods in parallel, and many of them
     * copy the one shared FoodMart catalog: walking it with {@code eGet(ref, true)} fills and resolves lists of the
     * source, so a second copy running at the same time met a list in change ({@code ConcurrentModificationException}
     * in {@code copyReferences}, chain 300; the EMF cross-reference null of chain 178 is the same family).
     */
    private static final Object COPYING = new Object();

    public static EcoreUtil.Copier copier(CatalogImpl catalog) {
        synchronized (COPYING) {
            return copierAlone(catalog);
        }
    }

    private static EcoreUtil.Copier copierAlone(CatalogImpl catalog) {
        Set<EObject> allRefs = collectAllReachableRecursive(catalog);
        EcoreUtil.Copier copier = new EcoreUtil.Copier(true, false);

        List<EObject> l = new ArrayList<EObject>(allRefs);
        for(int i = (l.size()-1); i>=0; i--) {
            copier.copy(l.get(i));
        }
        copier.copyReferences();

        return copier;
    }

    /**
     * A copy of one EMF object and what it contains, made one at a time like the catalog copies: for a test that adds
     * to a part of the shared FoodMart catalog (a database schema), which it must never change in place.
     */
    public static <T extends EObject> T copyOf(T object) {
        synchronized (COPYING) {
            return EcoreUtil.copy(object);
        }
    }

    public static CatalogImpl copy(CatalogImpl catalog) {
        synchronized (COPYING) {
            return copyAlone(catalog);
        }
    }

    private static CatalogImpl copyAlone(CatalogImpl catalog) {
        Set<EObject> allRefs = collectAllReachableRecursive(catalog);
        EcoreUtil.Copier copier = new EcoreUtil.Copier(true, false);

        List<EObject> l = new ArrayList<EObject>(allRefs);
        for(int i = (l.size()-1); i>=0; i--) {
            copier.copy(l.get(i));
        }
        copier.copyReferences();

        return (CatalogImpl) copier.get(catalog);
    }

    private static Set<EObject> collectAllReachableRecursive(EObject start) {
        LinkedHashSet<EObject> visited = new LinkedHashSet<>();
        dfs(start, visited);
        return visited;
    }

    private static void dfs(EObject cur, Set<EObject> visited) {

        if (visited.contains(cur)) {
            return;
        } else {
            visited.add(cur);
        }

        for (EReference ref : cur.eClass().getEAllReferences()) {
            if (ref.isContainment()) {
                //continue;
            }
            Object val;
            try {
                val = cur.eGet(ref, true);
            } catch (Exception ex) {
                continue;
            }
            if (val == null) {
                continue;
            }

            if ("importer".equals(ref.getName())) {
                // skip back-references to consuming catalogs
                continue;
            }
            if (ref.isMany()) {
                for (Object o : new java.util.ArrayList<>((List<?>) val)) {
                    if (o instanceof EObject) {
                        dfs((EObject) o, visited);

                    }
                }
            } else if (val instanceof EObject) {
                dfs((EObject) val, visited);
            }
        }

    }
}
