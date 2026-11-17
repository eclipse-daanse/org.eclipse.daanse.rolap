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
package org.eclipse.daanse.rolap.common.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Content identity of a context: mapping hash plus technical database identity
 * (JDBC URL, effective user, catalog, schema - never the password), from
 * VM-independent strings only. Embedded databases (URL without a {@code //host}
 * authority) hold node-local data and additionally carry a per-JVM id.
 */
public final class ContentIdentity {

    // v2: every part carries a marker, so a missing part no longer equals the text "null"
    private static final byte[] SALT = "daanse-id-v2\0".getBytes(StandardCharsets.UTF_8);

    private static final String JVM_INSTANCE_ID = UUID.randomUUID().toString();

    private ContentIdentity() {
    }

    public static byte[] sha256(byte[] mappingSha256, String jdbcUrl, String dbUser, String dbCatalog,
            String dbSchema) {
        return sha256(mappingSha256, jdbcUrl, dbUser, dbCatalog, dbSchema,
                isNodeLocal(jdbcUrl) ? JVM_INSTANCE_ID : "");
    }

    /** A URL without a {@code //host} authority addresses node-local data. */
    static boolean isNodeLocal(String jdbcUrl) {
        // a file or in-memory database lives on this node only, also when written with an empty host
        // (jdbc:h2:file:///data/db, jdbc:sqlite:///x)
        return jdbcUrl == null || !jdbcUrl.contains("//") || jdbcUrl.contains(":///") || jdbcUrl.contains(":file:")
                || jdbcUrl.contains(":mem:");
    }

    static byte[] sha256(byte[] mappingSha256, String jdbcUrl, String dbUser, String dbCatalog, String dbSchema,
            String nodeScope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(SALT);
            digest.update(mappingSha256);
            for (String part : new String[] { jdbcUrl, dbUser, dbCatalog, dbSchema, nodeScope }) {
                // a marker before every part: a missing part and the text "null" are two identities
                if (part == null) {
                    digest.update((byte) 1);
                } else {
                    digest.update((byte) 2);
                    digest.update(part.getBytes(StandardCharsets.UTF_8));
                }
                digest.update((byte) 0);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
