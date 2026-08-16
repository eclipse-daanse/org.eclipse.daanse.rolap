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
*   Stefan Bischof (bipolis.org) - initial
*/
package org.eclipse.daanse.rolap.cwm.supplier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.cwm.spi.CwmSchemaSupplier;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * Liefert die CWM-relationalen Schemas eines Katalogs aus dem
 * Katalog-Mapping der verfuegbaren {@link RolapContext}e
 * ({@code getCatalogMapping().getDbschemas()}) — genau die Objekte, aus
 * denen der {@code RolapCatalog} seine Datenbank-Metadaten baut.
 */
@Component(service = CwmSchemaSupplier.class)
public class RolapCwmSchemaSupplier implements CwmSchemaSupplier {

    private final List<Context<?>> contexts = new CopyOnWriteArrayList<>();

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    void bindContext(Context<?> context) {
        contexts.add(context);
    }

    void unbindContext(Context<?> context) {
        contexts.remove(context);
    }

    @Override
    public List<Schema> schemasFor(String catalogName) {
        return contexts.stream()
                .filter(RolapContext.class::isInstance)
                .map(RolapContext.class::cast)
                .filter(context -> context.getCatalogMapping() != null
                        && catalogName.equals(context.getCatalogMapping().getName()))
                .findFirst()
                .<List<Schema>>map(context -> List.copyOf(context.getCatalogMapping().getDbschemas()))
                .orElse(List.of());
    }
}
