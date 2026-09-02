/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (c) 2015-2017 Hitachi Vantara..  All rights reserved.
*/
package org.eclipse.daanse.rolap.agg;

import static org.eclipse.daanse.rolap.agg.AggregationOnInvalidRoleTest.executeAnalyzerQuery;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(value = AggregationOnInvalidRoleTestInstance.class, dbScope = DbScope.PER_CLASS)
class AggregationOnInvalidRoleWhenNotIgnoringTest {

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void test_ThrowsException_WhenNonIgnoringInvalidMembers(Context<?> context) {
        try {
            executeAnalyzerQuery(context.getConnection(new ConnectionProps(List.of("Test"))));
        } catch (Exception e) {
            // that's ok, junit's assertion errors are derived from Error,
            // hence they will not be caught here
            return;
        }
        fail("Schema should not load when restriction is invalid");
    }

}
