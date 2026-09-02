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
package org.eclipse.daanse.rolap.testkit.assertions;


import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Enumeration of common database types.
 *
 * <p>Branching on this enumeration allows you to write code which behaves
 * differently for different databases. However, since the capabilities of
 * a database can change between versions, it is recommended that
 * conditional code is in terms of capabilities methods in
 * {@link Dialect}.
 *
 * <p>Because there are so many differences between various versions and
 * ports of DB2, we represent them as 3 separate products. If you want to
 * treat them all as one product, note that the {@link #getFamily()} method
 * for {@link #DB2_AS400} and {@link #DB2_OLD_AS400} returns {@link #DB2}.
 */

public enum DatabaseProduct {
    ACCESS,
    UNKNOWN,
    CLICKHOUSE,
    DERBY,
    DB2_OLD_AS400,
    DB2_AS400,
    DB2,
    FIREBIRD,
    GREENPLUM,
    H2,
    HIVE,
    HSQLDB,
    IMPALA,
    INFORMIX,
    INFOBRIGHT,
    INGRES,
    INTERBASE,
    LUCIDDB,
    MSSQL,
    MONETDB,
    NETEZZA,
    NEOVIEW,
    NUODB,
    ORACLE,
    POSTGRES,
    REDSHIFT,
    MYSQL,
    SQLSTREAM,
    SYBASE,
    TERADATA,
    VERTICA,
    VECTORWISE,
    MARIADB,
    PDI,
    GOOGLEBIGQUERY,
    SNOWFLAKE,
    SQLITE;

    /**
     * Return the root of the family of products this database product
     * belongs to.
     *
     * <p>For {@link #DB2_AS400} and {@link #DB2_OLD_AS400} returns
     * {@link #DB2}; for all other database products, returns the same
     * product.
     *
     * @return root of family of database products
     */
    public DatabaseProduct getFamily() {
        switch (this) {
            case DB2_OLD_AS400:
            case DB2_AS400:
                return DB2;
            default:
                return this;
        }
    }

    public static DatabaseProduct getDatabaseProduct(String name) {
        for (DatabaseProduct databaseProduct : values()) {
            if (databaseProduct.name().equalsIgnoreCase(name)) {
                return databaseProduct;
            }
        }
        return UNKNOWN;
    }
}
