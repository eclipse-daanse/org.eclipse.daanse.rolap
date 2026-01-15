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
package org.eclipse.daanse.rolap.common.connection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.core.internal.BasicContext;

public class ExternalRolapConnection extends AbstractRolapConnection {

    public ExternalRolapConnection(BasicContext context, ConnectionProps props) {
        super(context, null, props);
        List<String> roleNameList =connectionProps.roles();
        Role roleInner = null;
        if ( roleNameList.isEmpty() ) {
            if (context.getCatalogMapping().getDefaultAccessRole() == null && !catalog.roleNames().isEmpty()) {
               throw new RuntimeException("User doesn't have any roles assigned and no default role is configured");
            }
        } else {
          List<Role> roleList = new ArrayList<>();
          for ( String roleName : roleNameList ) {
              Role role1 = catalog.lookupRole( roleName );

              if ( role1 == null ) {
                throw Util.newError(
                  new StringBuilder("Role '").append(roleName).append("' not found").toString() );
              }
              roleList.add( role1 );
            }
            // If they specify 'Role=;', the list of names will be
            // empty, and the effect will be as if they did specify Role at all.
            roleInner = switch (roleList.size()) {
                case 0 -> null;
                case 1 -> roleList.getFirst();
                default -> RoleImpl.union(roleList);
            };
        }

        if ( roleInner == null ) {
          roleInner = catalog.getDefaultRole();
        }
        setRole( roleInner );
    }
}
