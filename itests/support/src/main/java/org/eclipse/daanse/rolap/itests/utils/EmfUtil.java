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

    public static EcoreUtil.Copier copier(CatalogImpl catalog) {
        Set<EObject> allRefs = collectAllReachableRecursive(catalog);
        EcoreUtil.Copier copier = new EcoreUtil.Copier(true, false);

        List<EObject> l = new ArrayList<EObject>(allRefs);
        for(int i = (l.size()-1); i>=0; i--) {
            copier.copy(l.get(i));
        }
        copier.copyReferences();

        return copier;
    }

    public static CatalogImpl copy(CatalogImpl catalog) {
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
