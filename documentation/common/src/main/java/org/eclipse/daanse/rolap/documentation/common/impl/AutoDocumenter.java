/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.documentation.common.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.documentation.api.ContextDocumentationProvider;
import org.eclipse.daanse.rolap.documentation.common.api.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;

@Designate(ocd = AutoDocumenterConfig.class, factory = true)
@Component(immediate = true, configurationPid = Constants.AUTO_DOCUMENTER_PID)
public class AutoDocumenter {

    private ExecutorService newVirtualThreadPerTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private Path outputBasePath;

    private ContextDocumentationProvider documenter;

    @Activate
    public AutoDocumenter(AutoDocumenterConfig config, @Reference ContextDocumentationProvider docProvider)
            throws IOException {
        this.documenter = docProvider;
        outputBasePath = Paths.get(config.outputBasePath());
        Files.createDirectories(outputBasePath);
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindContext(Context<?> context) throws Exception {

        Path path = outputBasePath.resolve(context.getName());
        Files.createDirectories(path);

        newVirtualThreadPerTaskExecutor.execute(() -> {

            try {
                documenter.createDocumentation(context, path);
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    public void unbindContext(Context<?> context, Map<String, Object> props) throws Exception {
        // here that we do not recreate class when a context leave
    }

}
