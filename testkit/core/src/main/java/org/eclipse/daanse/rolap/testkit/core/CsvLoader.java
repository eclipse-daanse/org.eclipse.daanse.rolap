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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.core;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.api.DatabaseService;
import org.eclipse.daanse.jdbc.db.dialect.api.DialectFactory;
import org.eclipse.daanse.jdbc.db.impl.DatabaseServiceImpl;
import org.eclipse.daanse.jdbc.db.importer.csv.impl.CsvDataImporter;
import org.eclipse.daanse.jdbc.db.importer.csv.impl.CsvDataImporterConfig;

/**
 * Loads classpath-packaged CSV resources into a {@link DataSource} by driving
 * an instance of {@link CsvDataImporter} the same way OSGi DS would:
 * instantiate, call setters, call {@code @Activate}, then call its existing
 * public listener methods.
 *
 * <p>
 * URLs are first staged into a transient temp directory so the importer can use
 * its existing {@code Path}-based loading machinery unchanged.
 */
public final class CsvLoader {

    private CsvLoader() {
    }

    public static void load(DataSource dataSource, DialectFactory dialectFactory, Map<String, URL> csvResources)
            throws SQLException, IOException {
        if (csvResources == null || csvResources.isEmpty()) {
            return;
        }
        Path tempDir = Files.createTempDirectory("daanse-csv-import-");
        try {
            List<Path> ordered = new ArrayList<>();
            for (Map.Entry<String, URL> e : csvResources.entrySet()) {
                Path target = tempDir.resolve(e.getKey() + ".csv");
                try (InputStream in = e.getValue().openStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                ordered.add(target);
            }
            CsvDataImporter importer = new CsvDataImporter();
            importer.setDataSource(dataSource);
            importer.setDatabaseService(new DatabaseServiceImpl());
            importer.setDialectFactory(dialectFactory);
            importer.activate(defaultConfig());
            importer.handleBasePath(tempDir);
            importer.handleInitialPaths(ordered);
        } finally {
            deleteTree(tempDir);
        }
    }

    private static CsvDataImporterConfig defaultConfig() {
        return new CsvDataImporterConfig() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CsvDataImporterConfig.class;
            }

            @Override
            public String nullValue() {
                return "NULL";
            }

            @Override
            public char quoteCharacter() {
                return '"';
            }

            @Override
            public char fieldSeparator() {
                return ',';
            }

            @Override
            public String encoding() {
                return "UTF-8";
            }

            @Override
            public boolean skipEmptyLines() {
                return true;
            }

            @Override
            public char commentCharacter() {
                return '#';
            }

            @Override
            public boolean ignoreDifferentFieldCount() {
                return true;
            }

            @Override
            public boolean clearTableBeforeLoad() {
                return true;
            }

            @Override
            public int batchSize() {
                return 1000;
            }
        };
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
