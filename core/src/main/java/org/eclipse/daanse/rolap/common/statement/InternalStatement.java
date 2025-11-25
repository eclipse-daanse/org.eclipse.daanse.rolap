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
package org.eclipse.daanse.rolap.common.statement;

import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.connection.Connection;

/**
 * Implementation of {@link Statement} for use when you don't have an
 * olap4j connection.
 */
public class InternalStatement extends org.eclipse.daanse.olap.impl.StatementImpl {
  private boolean closed = false;

    /**
     * Creates a StatementImpl.
     *
     * @param connection
     */
    public InternalStatement(Connection connection) {
        super(connection);
    }

    @Override
	public void close() {
    if ( !closed ) {
      closed = true;
      context.removeStatement( this );
    }
  }

  @Override
	public Connection getDaanseConnection() {
    return this.getConnection();
  }
}
