/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.olap.fun;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.tuple.TupleCursor;
import org.eclipse.daanse.olap.api.calc.tuple.TupleIterable;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.function.FunctionParameter;
import org.eclipse.daanse.olap.api.query.Validator;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.api.type.TupleType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.def.crossjoin.CrossJoinFunDef;
import org.eclipse.daanse.olap.function.def.crossjoin.CrossJoinIterCalc;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


/**
 * <code>CrossJoint</code> tests the collation order of positive and negative
 * infinity, and {@link Double#NaN}.
 *
 * @author <a>Richard M. Emberson</a>
 * @since Jan 14, 2007
 */

@RolapContextTest(FoodmartTestInstance.class)
public class CrossJoinTest {

  private static final String SELECT_GENDER_MEMBERS =
    "select Gender.members on 0 from sales";

  private static final String SALES_CUBE = "Sales";

  private ExecutionImpl excMock = mock( ExecutionImpl.class );

  static List<List<Member>> m3 = Arrays.asList(
    Arrays.<Member>asList( new TestMember( "k" ), new TestMember( "l" ) ),
    Arrays.<Member>asList( new TestMember( "m" ), new TestMember( "n" ) ) );

  static List<List<Member>> m4 = Arrays.asList(
    Arrays.<Member>asList( new TestMember( "U" ), new TestMember( "V" ) ),
    Arrays.<Member>asList( new TestMember( "W" ), new TestMember( "X" ) ),
    Arrays.<Member>asList( new TestMember( "Y" ), new TestMember( "Z" ) ) );

  static final Comparator<List<Member>> memberComparator =
    new Comparator<>() {
      @Override
	public int compare( List<Member> ma1, List<Member> ma2 ) {
        for ( int i = 0; i < ma1.size(); i++ ) {
          int c = ma1.get( i ).compareTo( ma2.get( i ) );
          if ( c < 0 ) {
            return c;
          } else if ( c > 0 ) {
            return c;
          }
        }
        return 0;
      }
    };

  private CrossJoinFunDef crossJoinFunDef;

  @BeforeEach
  protected void beforeEach() throws Exception {
    crossJoinFunDef = new CrossJoinFunDef( new NullFunDef().getFunctionMetaData() );
  }

  @AfterEach
  protected void afterEach() throws Exception {
  }

  private Cube cubeByName(Connection connection, String cubeName) {
    CatalogReader reader = connection.getCatalogReader().withLocus();
    List<Cube> cubes = reader.getCubes();
    Cube resultCube = null;
    for (Cube cube : cubes) {
      if (cubeName.equals(cube.getName())) {
        resultCube = cube;
        break;
      }
    }
    return resultCube;
  }

  private TupleList productMembersPotScrubbersPotsAndPans(
          CatalogReader salesCubeCatalogReader)
  {
      return new UnaryTupleList(Arrays.asList(
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pot Scrubbers", "Cormorant"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pot Scrubbers", "Denny"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pot Scrubbers", "Red Wing"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pots and Pans", "Cormorant"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pots and Pans", "Denny"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pots and Pans", "High Quality"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pots and Pans", "Red Wing"),
              true),
          salesCubeCatalogReader.getMemberByUniqueName(
              IdImpl.toList(
                  "Product", "All Products", "Non-Consumable", "Household",
                  "Kitchen Products", "Pots and Pans", "Sunset"),
              true)));
  }

  ////////////////////////////////////////////////////////////////////////
  // Iterable
  ////////////////////////////////////////////////////////////////////////



  // The test to verify that cancellation/timeout is checked
  // in CrossJoinFunDef$CrossJoinIterCalc$1$1.forward()
	@Test
	@RolapConfig(key = ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL, value = "1", type = Integer.class)
  void testCrossJoinIterCalc_IterationCancellationOnForward(Connection con) {
    // Get product members as TupleList
    RolapCube salesCube =
      (RolapCube) cubeByName( con, SALES_CUBE );
    CatalogReader salesCubeCatalogReader =
      salesCube.getCatalogReader( con.getRole() )
        .withLocus();
    TupleList productMembers =
    		productMembersPotScrubbersPotsAndPans( salesCubeCatalogReader );
    // Get genders members as TupleList
    Result genders = executeQuery(con, SELECT_GENDER_MEMBERS );
    TupleList genderMembers = getGenderMembers( genders );

    // Test execution to track cancellation/timeout calls
    ExecutionImpl execution =
      spy( new ExecutionImpl( genders.getQuery().getStatement(), Optional.empty() ) );
    execution.asContext().setExecution(execution);
    // check no execution of checkCancelOrTimeout has been yet
    verify( execution, times( 0 ) ).checkCancelOrTimeout();
    Integer crossJoinIterCalc =
      crossJoinIterCalcIterate( productMembers, genderMembers, execution );

    // checkCancelOrTimeout should be called once for the left tuple
    //from CrossJoinIterCalc$1$1.forward() since phase
    // interval is 1
    verify( execution, times( productMembers.size() ) ).checkCancelOrTimeout();
    assertEquals(
      productMembers.size() * genderMembers.size(),
      crossJoinIterCalc.intValue() );
  }

  private static TupleList getGenderMembers( Result genders ) {
    TupleList genderMembers = new UnaryTupleList();
    for ( Position pos : genders.getAxes()[ 0 ].getPositions() ) {
      genderMembers.add( pos );
    }
    return genderMembers;
  }

  private Integer crossJoinIterCalcIterate(
    final TupleList list1, final TupleList list2,
    final ExecutionImpl execution ) {
    ExecutionMetadata metadata = ExecutionMetadata.of("CrossJoinTest", "CrossJoinTest", null, 0);
    return ExecutionContext.where(
      execution.asContext().createChild(metadata, Optional.empty()),
      () -> {
        TupleIterable iterable =
          new CrossJoinIterCalc(
            getResolvedFunCall(), null, crossJoinFunDef.getCtag() ).makeIterable( list1, list2 );
        TupleCursor tupleCursor = iterable.tupleCursor();
        // total count of all iterations
        int counter = 0;
        while ( tupleCursor.forward() ) {
          counter++;
        }
        return Integer.valueOf( counter );
      } );
  }


	@Test
void testResultLimitWithinCrossjoin_1(Context<?> foodMartContext) {
	}


	@Disabled // RESULT_LIMIT=1000 now trips during catalog load, not just the query under test
	@Test
	@RolapConfig(key = ConfigConstants.RESULT_LIMIT, value = "1000", type = Integer.class)
  void testResultLimitWithinCrossjoin(Context<?> foodMartContext) {
   Connection connection= foodMartContext.getConnectionWithDefaultRole();
   // After the connection: building the catalog reads members too, and a limit
   // this low would already trip there.
    assertThatAxis(connection, "Sales",
      "Hierarchize(Crossjoin(Union({[Gender].CurrentMember}, [Gender].Children), "
        + "Union({[Product].CurrentMember}, [Product].[Brand Name].Members)))")
      .throwsMessage( "result (1,539) exceeded limit (1,000)" );
  }


  protected ResolvedFunCallImpl getResolvedFunCall() {
    FunctionDefinition funDef = new TestFunDef();
    Expression[] args = new Expression[ 0 ];
    Type returnType =
      new SetType(
        new TupleType(
          new Type[] {
            new MemberType( null, null, null, null ),
            new MemberType( null, null, null, null ) } ) );
    return new ResolvedFunCallImpl( funDef, args, returnType );
  }

  ////////////////////////////////////////////////////////////////////////
  // Helper classes
  ////////////////////////////////////////////////////////////////////////
  public static class TestFunDef implements FunctionDefinition {
    TestFunDef() {
    }


    @Override
	public Expression createCall( Validator validator, Expression[] args ) {
      throw new UnsupportedOperationException();
    }

    @Override
	public String getSignature() {
      throw new UnsupportedOperationException();
    }

    @Override
	public void unparse( Expression[] args, PrintWriter pw ) {
      throw new UnsupportedOperationException();
    }

    @Override
	public Calc compileCall( ResolvedFunCall call, ExpressionCompiler compiler ) {
      throw new UnsupportedOperationException();
    }

	@Override
	public FunctionMetaData getFunctionMetaData() {
		return new FunctionMetaData() {

			@Override
			public OperationAtom operationAtom() {

				return new FunctionOperationAtom("SomeName");
			}

			    @Override
				public String description() {
			      throw new UnsupportedOperationException();
			    }

			    @Override
				public DataType returnCategory() {
			      throw new UnsupportedOperationException();
			    }

			    @Override
				public DataType[] parameterDataTypes() {
			      throw new UnsupportedOperationException();
			    }


				@Override
				public FunctionParameterR[] parameters() {
			      throw new UnsupportedOperationException();
				}

		};
	}
  }

  public static class NullFunDef implements FunctionDefinition {
    public NullFunDef() {
    }



    @Override
	public Expression createCall( Validator validator, Expression[] args ) {
      return null;
    }

    @Override
	public String getSignature() {
      return "";
    }

    @Override
	public void unparse( Expression[] args, PrintWriter pw ) {
      //
    }

    @Override
	public Calc<?> compileCall( ResolvedFunCall call, ExpressionCompiler compiler ) {
      return null;
    }

	@Override
	public FunctionMetaData getFunctionMetaData() {
        return new FunctionMetaData() {

            @Override
            public OperationAtom operationAtom() {
                return new FunctionOperationAtom("");
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public DataType returnCategory() {
                  return DataType.UNKNOWN;
            }

                @Override
                public DataType[] parameterDataTypes() {
                    return new DataType[ 0 ];
                }

                @Override
                public FunctionParameter[] parameters() {
                    return new FunctionParameter[0];
                }
       };
	}
  }
}
