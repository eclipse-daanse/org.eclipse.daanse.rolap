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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.olap.function;

import static org.eclipse.daanse.olap.common.Util.assertTrue;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.calc.base.profile.SimpleCalculationProfileWriter;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;

public class TestResources {
    public static final String[] AllHiers = {
            "[Measures]",
            "[Store].[Store]",
            "[Store Size in SQFT].[Store Size in SQFT]",
            "[Store Type].[Store Type]",
            "[Time].[Time]",
            "[Time].[Weekly]",
            "[Product].[Product]",
            "[Promotion Media].[Promotion Media]",
            "[Promotions].[Promotions]",
            "[Customers].[Customers]",
            "[Education Level].[Education Level]",
            "[Gender].[Gender]",
            "[Marital Status].[Marital Status]",
            "[Yearly Income].[Yearly Income]"
    };

    public static final String year1997 = "[Time].[Time].[1997]";
    public static final String months =
            "[Time].[Time].[1997].[Q1].[1]\n"
              + "[Time].[Time].[1997].[Q1].[2]\n"
              + "[Time].[Time].[1997].[Q1].[3]\n"
              + "[Time].[Time].[1997].[Q2].[4]\n"
              + "[Time].[Time].[1997].[Q2].[5]\n"
              + "[Time].[Time].[1997].[Q2].[6]\n"
              + "[Time].[Time].[1997].[Q3].[7]\n"
              + "[Time].[Time].[1997].[Q3].[8]\n"
              + "[Time].[Time].[1997].[Q3].[9]\n"
              + "[Time].[Time].[1997].[Q4].[10]\n"
              + "[Time].[Time].[1997].[Q4].[11]\n"
              + "[Time].[Time].[1997].[Q4].[12]";

    public static final String hierarchized1997 =
            year1997
              + "\n"
              + "[Time].[Time].[1997].[Q1]\n"
              + "[Time].[Time].[1997].[Q1].[1]\n"
              + "[Time].[Time].[1997].[Q1].[2]\n"
              + "[Time].[Time].[1997].[Q1].[3]\n"
              + "[Time].[Time].[1997].[Q2]\n"
              + "[Time].[Time].[1997].[Q2].[4]\n"
              + "[Time].[Time].[1997].[Q2].[5]\n"
              + "[Time].[Time].[1997].[Q2].[6]\n"
              + "[Time].[Time].[1997].[Q3]\n"
              + "[Time].[Time].[1997].[Q3].[7]\n"
              + "[Time].[Time].[1997].[Q3].[8]\n"
              + "[Time].[Time].[1997].[Q3].[9]\n"
              + "[Time].[Time].[1997].[Q4]\n"
              + "[Time].[Time].[1997].[Q4].[10]\n"
              + "[Time].[Time].[1997].[Q4].[11]\n"
              + "[Time].[Time].[1997].[Q4].[12]";

    public static final String quarters =
            "[Time].[Time].[1997].[Q1]\n"
              + "[Time].[Time].[1997].[Q2]\n"
              + "[Time].[Time].[1997].[Q3]\n"
              + "[Time].[Time].[1997].[Q4]";

    public static final String NullNumericExpr =
            " ([Measures].[Unit Sales],"
              + "   [Customers].[All Customers].[USA].[CA].[Bellflower], "
              + "   [Product].[All Products].[Drink].[Alcoholic Beverages]."
              + "[Beer and Wine].[Beer].[Good].[Good Imported Beer])";

    public static void checkNullOp(Connection connection, final String op ) {
        assertThatExpr(connection, "Sales", " 0 " + op + " " + NullNumericExpr).isFalse();
        assertThatExpr(connection, "Sales", NullNumericExpr + " " + op + " 0").isFalse();
        assertThatExpr(connection, "Sales",
            NullNumericExpr + " " + op + " " + NullNumericExpr).isFalse();
    }

    /**
     * All dimension hierarchies except those given, as individual hierarchy names - what
     * {@link FunDependencies}'s {@code dependsOn(String...)} takes.
     */
    public static String[] hiersExcept( String... hiers ) {
      for ( String hier : hiers ) {
        assert contains( AllHiers, hier ) : "unknown hierarchy " + hier;
      }
      return java.util.Arrays.stream( AllHiers )
        .filter( hier -> !contains( hiers, hier ) )
        .toArray( String[]::new );
    }

    /**
     * Compiles a set expression, and asserts that the program looks as expected.
     */
    public static void assertAxisCompilesTo(Connection connection,
      String expr,
      String expectedCalc ) {
      Query query = connection.parseQuery("SELECT {" + expr + "} ON COLUMNS FROM Sales");
      Calc calc = query.compileExpression(query.getAxes()[0].getSet(), false, null);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      new SimpleCalculationProfileWriter(pw).write(calc.getCalculationProfile());
      pw.flush();
      final String actualCalc = sw.toString();
      final int expDeps =
        connection.getContext().getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class);
      if ( expDeps > 0 ) {
        // Don't bother checking the compiled output if we are also
        // testing dependencies. The compiled code will have extra
        // 'DependencyTestingCalc' instances embedded in it.
        return;
      }
      assertEquals(stubAnonymousClasses(expectedCalc), stubAnonymousClasses(actualCalc));
    }

    private static boolean contains( String[] a, String s ) {
        for ( String anA : a ) {
          if ( anA.equals( s ) ) {
            return true;
          }
        }
        return false;
      }

    /**
     * Replaces anonymous class names (/\$\d+/) with a stub "$-anonymous-class-" in constructions
     * "class&nbsp;mondrian.rest.package.name.ClassName$InnerClassNames". <br/> e.g. <br/>
     * <code>stubAnonymousClasses("class mondrian.fun.Fun$21$1")</code>
     * results
     * <code>
     * "class mondrian.fun.Fun$-anonymous-class-$-anonymous-class-"
     * </code>.
     * <br/> Within a Strings comparison <br/> applying this to both compared <code>String</code>s makes the comparison
     * independent on anonymous class names.
     * </br>
     */
    private static String stubAnonymousClasses( String str ) {
      if ( !str.contains( "$" ) ) {
        return str;
      }
      final String regex =
          "(class mondrian(?:\\.\\w+)*(?:\\$(?:\\w+|-anonymous-class-))*?)(?:\\$\\d+)\\b";
      final String replacement = "$1\\$-anonymous-class-";
      Pattern p = Pattern.compile( regex );
      String str1 = p.matcher( str ).replaceAll( replacement );
      while ( !str.equals( str1 ) ) {
        str = str1;
        str1 = p.matcher( str ).replaceAll( replacement );
      }
      return str1;
    }

    public static void checkDataResults(
            Double[][] expected,
            Result result,
            final double tolerance ) {
            int[] coords = new int[ 2 ];

            for ( int row = 0; row < expected.length; row++ ) {
              coords[ 1 ] = row;
              for ( int col = 0; col < expected[ 0 ].length; col++ ) {
                coords[ 0 ] = col;

                Cell cell = result.getCell( coords );
                final Double expectedValue = expected[ row ][ col ];
                if ( expectedValue == null ) {
                  assertTrue(cell.isNull(),  "Expected null value");
                } else if ( cell.isNull() ) {
                  fail(
                    "Cell at (" + row + ", " + col
                      + ") was null, but was expecting "
                      + expectedValue );
                } else {
                  assertEquals(
                    expectedValue,
                    ( (Number) cell.getValue() ).doubleValue(),
                    tolerance, "Incorrect value returned at (" + row + ", " + col + ")" );
                }
              }
            }
          }

}
