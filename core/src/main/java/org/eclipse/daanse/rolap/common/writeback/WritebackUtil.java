/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.writeback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.IdentifierSegment;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.impl.IdentifierParser;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WritebackUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(WritebackUtil.class);

    public static void commit(RolapCube cube, Connection con, List<Map<String, Map.Entry<Datatype, Object>>> sessionValues, String userId) {
        int rowCount = sessionValues == null ? 0 : sessionValues.size();
        // INFO so operators can confirm the commit path is reached without
        // turning on DEBUG. Logs unconditionally — even when the cube has no
        // writeback table (helps diagnose "I clicked Publish but nothing
        // happened" cases).
        LOGGER.info("Writeback[commit] cube='{}' rows={} userId='{}' hasWritebackTable={}",
                cube.getName(), rowCount, userId, cube.getWritebackTable().isPresent());

        if (!cube.getWritebackTable().isPresent()) {
            return;
        }

        RolapWritebackTable writebackTable = cube.getWritebackTable().get();
        if (rowCount == 0) {
            LOGGER.info("Writeback[commit] cube='{}' writebackTable='{}' — no rows to write",
                    cube.getName(), writebackTable.getName());
            return;
        }

        DataSource dataSource = con.getDataSource();
        Dialect dialect = con.getContext().getDialect();
        LOGGER.info("Writeback[commit] cube='{}' writebackTable='{}' writing {} row(s)",
                cube.getName(), writebackTable.getName(), rowCount);
        BatchInsertEmitter.execute(cube, dataSource, dialect, writebackTable, sessionValues, userId);
    }

    public static List<Map<String, Map.Entry<DataTypeJdbc, Object>>> getAllocationValues(RolapCube rolapCube,
            String tupleString,
            Object value,
            AllocationPolicy allocationPolicy, Role role
        ) {
            List<Map<String, Map.Entry<DataTypeJdbc, Object>>> res = new ArrayList<>();
            //[D1.HierarchyWithHasAll].[Level11], [Measures].[Measure1]
                Optional<RolapWritebackTable> oWritebackTable = rolapCube.getWritebackTable();
                if (oWritebackTable.isPresent()) {
                    RolapWritebackTable writebackTable = oWritebackTable.get();
                    List<List<IdentifierSegment>> segmentList = IdentifierParser.parseIdentifierList(tupleString.replace("(", "").replace(")", ""));
                    if (segmentList.size() > 0) {
                            List<String> tuples = List.of();
                            String measure;
                            if (segmentList.getFirst().stream().anyMatch(id -> id.getName().equals("Measures"))) {
                                //measure only
                                measure = getUnicalNameFromIdentifierSegment(segmentList.get(0));
                            } else {
                                tuples = getTuples(segmentList.get(0));
                                measure = getUnicalNameFromIdentifierSegment(segmentList.get(1));
                            }
                            String measureName = measure;
                            Optional<Member> oMember =
                                rolapCube.getMeasures().stream().filter(m -> m.getUniqueName().equals(measureName)).findFirst();
                            if (oMember.isPresent() && oMember.get() instanceof RolapBaseCubeMeasure rolapBaseCubeMeasure) {
                                if (!tuples.isEmpty()) {
                                    String hierarchyName = tuples.get(1);
                                    Optional<Hierarchy> oRolapHierarchy =
                                        rolapCube.getHierarchies().stream()
                                            .filter(h -> h.getName().equals(hierarchyName)).findFirst();
                                    List<String> ls = new ArrayList<>();
                                    if (tuples.size() > 2) {
                                        for (int i = 2; i < tuples.size(); i++) {
                                            ls.add(tuples.get(i));
                                        }
                                    }
                                    if (oRolapHierarchy.isPresent()) {
                                    	Hierarchy hierarchy = oRolapHierarchy.get();
                                        List<? extends Level> levels = oRolapHierarchy.get().getLevels();
                                        Optional<Member> oRolapMember = getHierarchy(levels, ls, rolapCube, hierarchy, role);
                                        Set<Member> columnMembers = getLevelLeafMembers(levels, oRolapMember, rolapCube, hierarchy, role);
                                        List<List<IdentifierSegment>> filterList = new ArrayList<List<IdentifierSegment>>();
                                        if (segmentList.size() > 2) {
                                            for (int i=2; i< segmentList.size(); i++) {
                                               filterList.add(segmentList.get(i));
                                            }
                                        }
                                        List<Set<Member>> rowMembers = getRowMembers(writebackTable, hierarchy.getDimension(), rolapCube, filterList, role);
                                        Map<List<Member>, Object> data = getData(columnMembers, rowMembers, rolapBaseCubeMeasure.getUniqueName(), rolapCube);
                                        res.addAll(AllocationPolicyApplier.allocateData(data, measureName, (Double) value, allocationPolicy, writebackTable));
                                    }
                                } else {
                                    List<Hierarchy> hs = rolapCube.getHierarchies();
                                    if (hs != null && hs.stream().anyMatch(h -> h instanceof Hierarchy)) {
                                        List<List<IdentifierSegment>> filterList = new ArrayList<List<IdentifierSegment>>();
                                        if (segmentList.size() > 1) {
                                            //[Measures][AmmountPlan], [Year].[2025] -> [Year].[2025]
                                            for (int i=1; i< segmentList.size(); i++) {
                                               filterList.add(segmentList.get(i));
                                            }
                                        }
                                        if (!filterList.isEmpty()) {
                                            List<IdentifierSegment> fIdentifierSegment = filterList.getFirst();
                                            Optional<Hierarchy> oHierarchy = hs.stream()
                                                    .filter(h -> (!h.getDimension().isMeasures() && 
                                                        !h.getDimension().getName().equals(fIdentifierSegment.get(0).getName()) && 
                                                        !h.getName().equals(fIdentifierSegment.get(1).getName()))).findFirst();
                                            if (oHierarchy.isPresent()) {
                                                Hierarchy rolapCubeHierarchy = oHierarchy.get();
                                                List<? extends Level> levels = rolapCubeHierarchy.getLevels();
                                                if (levels != null && !levels.isEmpty()) {
                                                    Set<Member> columnMembers = getLevelLeafMembers(levels, Optional.empty(),
                                                        rolapCube, rolapCubeHierarchy, role);
                                                    List<Set<Member>> rowMembers = getRowMembers(writebackTable, rolapCubeHierarchy.getDimension(), rolapCube, filterList, role);
                                                    Map<List<Member>, Object> data = getData(columnMembers, rowMembers, rolapBaseCubeMeasure.getUniqueName(), rolapCube);
                                                    res.addAll(AllocationPolicyApplier.allocateData(data, measureName, (Double) value, allocationPolicy,
                                                        writebackTable));
                                                }
                                            } else {
                                                for (Hierarchy h : hs) {
                                                    if (h instanceof Hierarchy rolapCubeHierarchy) {
                                                        if (!rolapCubeHierarchy.getDimension().isMeasures()) {
                                                            List<? extends Level> levels = rolapCubeHierarchy.getLevels();
                                                            if (levels != null && !levels.isEmpty()) {
                                                                Set<Member> columnMembers = getLevelLeafMembers(levels, Optional.empty(),
                                                                    rolapCube, rolapCubeHierarchy, role);
                                                                List<Set<Member>> rowMembers = getRowMembers(writebackTable, rolapCubeHierarchy.getDimension(), rolapCube, List.of(), role);
                                                                Map<List<Member>, Object> data = getData(columnMembers, rowMembers, rolapBaseCubeMeasure.getUniqueName(), rolapCube);
                                                                res.addAll(AllocationPolicyApplier.allocateData(data, measureName, (Double) value, allocationPolicy,
                                                                    writebackTable));
                                                                break;
                                                            }
                                                        }
                                                    }
                                                 }
                                            }
                                            
                                        } else {
                                            for (Hierarchy h : hs) {
                                                if (h instanceof Hierarchy rolapCubeHierarchy) {
                                                    if (!rolapCubeHierarchy.getDimension().isMeasures()) {
                                                        List<? extends Level> levels = rolapCubeHierarchy.getLevels();
                                                        if (levels != null && !levels.isEmpty()) {
                                                            Set<Member> columnMembers = getLevelLeafMembers(levels, Optional.empty(),
                                                                rolapCube, rolapCubeHierarchy, role);
                                                            List<Set<Member>> rowMembers = getRowMembers(writebackTable, rolapCubeHierarchy.getDimension(), rolapCube, List.of(), role);
                                                            Map<List<Member>, Object> data = getData(columnMembers, rowMembers, rolapBaseCubeMeasure.getUniqueName(), rolapCube);
                                                            res.addAll(AllocationPolicyApplier.allocateData(data, measureName, (Double) value, allocationPolicy,
                                                                writebackTable));
                                                            break;
                                                        }
                                                    }
                                                }
                                             }
                                        }
                                    } else {
                                        // Hierarchies is absent
                                        Map<List<Member>, Object> data = getData(rolapBaseCubeMeasure, rolapCube);
                                        res.addAll(AllocationPolicyApplier.allocateData(data, measureName, (Double) value, allocationPolicy,
                                            writebackTable));
                                    }
                                }
                            }
                    }
                }
            return res;
        }

    private static List<Set<Member>> getRowMembers(RolapWritebackTable writebackTable, Dimension dimension, RolapCube rolapCube, List<List<IdentifierSegment>> filterList, Role role) {
    	List<Set<Member>> result = new ArrayList<Set<Member>>();
    	if (writebackTable.getColumns() != null) {
    		for (RolapWritebackColumn writebackColumn : writebackTable.getColumns()) {
    			if (writebackColumn instanceof RolapWritebackAttribute writebackAttribute) {
    				Dimension d = writebackAttribute.getDimension();
    				if (d != null && !d.equals(dimension)) {
        				Optional<List<IdentifierSegment>> oDimensionIdentifierSegment = findDimension(filterList, d.getName());
        				if (oDimensionIdentifierSegment.isPresent()) {
        					String filterLevelName = getUnicalNameFromIdentifierSegment(oDimensionIdentifierSegment.get());
        					Set<Member> columnMembers = getMembers(d, rolapCube, role);
        					Set<Member> filteredSet = columnMembers.stream().filter(m -> m.getUniqueName().startsWith(filterLevelName)).collect(Collectors.toSet());
        					if (!filteredSet.isEmpty()) {
        						result.add(filteredSet);
        					}
        				} else {
        					Set<Member> columnMembers = getMembers(d, rolapCube, role);
        					if (!columnMembers.isEmpty()) {
        						result.add(columnMembers);
        					}
        				}
    				}
    			}
    		}
    	}
		return result;
	}

	private static Set<Member> getMembers(Dimension d, RolapCube rolapCube, Role role) {
		Set<Member> result = new HashSet<Member>();
		List<? extends Hierarchy> hs = d.getHierarchies();
		if (hs != null) {
			List<? extends Hierarchy> rolapHierarchies = hs.stream().filter(h -> !h.getDimension().isMeasures()).toList();
			for (Hierarchy h : rolapHierarchies) {
                List<? extends Level> levels = h.getLevels();
                Set<Member> columnMembers = getLevelLeafMembers(levels, Optional.empty(), rolapCube, h, role);
                result.addAll(columnMembers);
			}
		}
		return result;
	}

	private static Optional<List<IdentifierSegment>> findDimension(List<List<IdentifierSegment>> filterList,
        String name) {
        return filterList.stream().filter(l -> (l.size() > 0 && name.equals(l.get(0).getName()))).findFirst();
	}

	private static Optional<Member> getHierarchy(List<? extends Level> levels, List<String> memberNames, Cube cube, Hierarchy hierarchy, Role role) {
        Optional<RolapMember> result = Optional.empty();
        if (levels.size() > memberNames.size()) {
            Level level = null;
            for (int i = 0; i < memberNames.size(); i++) {
                int index = i;
                if (i == 0) {
                    for (Level l : levels) {
                        List<RolapMember> members = ((RolapHierarchy)hierarchy).createMemberReader(role).getMembersInLevel((RolapLevel)l);
                        result = members.stream().filter(m -> m.getName().equals(memberNames.get(index))).findFirst();
                        if (result.isPresent()) {
                            level = l.getChildLevel();
                            break;
                        }
                    }
                } else {
                    if (result.isPresent() && level != null) {
                        Member mem = result.get();
                        List<RolapMember> members = ((RolapHierarchy)hierarchy).createMemberReader(role).getMembersInLevel((RolapLevel)level);
                        result =
                            members.stream().filter(m -> m.getName().equals(memberNames.get(index)) && m.getUniqueName().startsWith(mem.getUniqueName())).findFirst();
                        level = level.getChildLevel();
                    }
                }

            }
        }
        if (result.isPresent()) {
            return Optional.of((Member) result.get());
        }
        return Optional.empty();
    }


    private static String getUnicalNameFromIdentifierSegment(List<IdentifierSegment> identifierSegments) {
        return identifierSegments.stream().map(IdentifierSegment::getName).collect(Collectors.joining("].[", "[", "]"));
    }


    private static List<String> getTuples(List<IdentifierSegment> identifierSegments) {
    	return identifierSegments.stream().map(IdentifierSegment::getName).toList();
    }

    private static Set<Member> getLevelLeafMembers(List<? extends Level> levels, Optional<Member> oRolapMember, Cube rolapCube, Hierarchy hierarchy, Role role) {
        Set<Member> result = new HashSet<>();
        if (oRolapMember.isPresent()) {
            Level level = oRolapMember.get().getLevel();
            if (level.getChildLevel() != null) {
                result.addAll(getLevelLeafMembers(level.getChildLevel(), oRolapMember, rolapCube, hierarchy, role));
            } else {
                List<RolapMember> members = ((RolapHierarchy)hierarchy).createMemberReader(role).getMembersInLevel((RolapLevel)level);
                if (members != null) {
                    for (Member member : members) {
                        if (member.getUniqueName().startsWith(oRolapMember.get().getUniqueName())) {
                            result.add(member);
                        }
                    }
                }
            }
        } else {
            if (levels != null) {
                for (Level level : levels) {
                    result.addAll(getLevelLeafMembers(level, Optional.empty(), rolapCube, hierarchy, role));
                }
            }
        }
        return result;
    }

    private static Set<Member> getLevelLeafMembers(Level level, Optional<Member> oRolapMember, Cube rolapCube, Hierarchy hierarchy, Role role) {
        Set<Member> result = new HashSet<>();
        if (level.getChildLevel() != null) {
            result.addAll(getLevelLeafMembers(level.getChildLevel(), oRolapMember, rolapCube, hierarchy, role));
        } else {
            List<RolapMember> members = ((RolapHierarchy)hierarchy).createMemberReader(role).getMembersInLevel((RolapLevel)level);
            if (members != null) {
                for (Member member : members) {
                    if (oRolapMember.isPresent()) {
                        if (member.getUniqueName().startsWith(oRolapMember.get().getUniqueName())) {
                            result.add(member);
                        }
                    } else {
                        result.add(member);
                    }
                }
            }
        }
        return result;
    }

    private static Map<List<Member>, Object> getData(Member measure, Cube cube) {
        //example
        //SELECT
        //{
        //    ([Measures].[Measure1])
        //} ON 0
        //FROM C

        Map<List<Member>, Object> res = new HashMap<>();
        final StringBuilder buf = new StringBuilder();
        buf.append("select {");
        buf.append("(").append(measure.getUniqueName()).append(")");
        buf.append("} ON 0 FROM ").append(cube.getName());
        final String mdx = buf.toString();
        final Connection connection =
            cube.getCatalog().getInternalConnection();
        final Query query = connection.parseQuery(mdx);
        final Result result = connection.execute(query);
        res.put(List.of(measure), result.getCell(new int[]{0}).getValue());
        return res;
    }

    private static Map<List<Member>, Object> getData(Set<Member> members, String measureUniqueName, Cube cube) {
        //example
        //SELECT
        //{
        //    ([D1.HierarchyWithHasAll].[Level11].[Level11], [Measures].[Measure1]),
        //    ([D1.HierarchyWithHasAll].[Level11].[Level22], [Measures].[Measure1]),
        //    ([D1.HierarchyWithHasAll].[Level22].[Level11], [Measures].[Measure1]),
        //    ([D1.HierarchyWithHasAll].[Level22].[Level22], [Measures].[Measure1]),
        //    ([D1.HierarchyWithHasAll].[Level22].[Level33], [Measures].[Measure1])
        //} ON 0
        //FROM C

        Map<List<Member>, Object> res = new HashMap<>();
        final StringBuilder buf = new StringBuilder();
        buf.append("select {");
        buf.append(
            members.stream()
                .map(member -> "(" + member.getUniqueName() + ", " + measureUniqueName + ")")
                .collect(Collectors.joining(", "))
        );
        buf.append("} ON 0 FROM ").append(cube.getName());
        final String mdx = buf.toString();
        final Connection connection =
            cube.getCatalog().getInternalConnection();
        final Query query = connection.parseQuery(mdx);
        final Result result = connection.execute(query);
        int i = 0;
        for (Member m : members) {
            res.put(List.of(m), result.getCell(new int[]{i}).getValue());
            i++;
        }
        return res;
    }

    private static Map<List<Member>, Object> getData(Set<Member> members, List<Set<Member>> rowMembers,  String measureUniqueName, Cube cube) {
        //example
        //SELECT 
        //NON EMPTY 
        //{ [Year].[Year].[2025], [Year].[Year].[2026], [Year].[Year].[2027] } * { [OrgUnit].[OrgUnit].[Company].[Division A].[Department A1], [OrgUnit].[OrgUnit].[Company].[Division A].[Department A2], [OrgUnit].[OrgUnit].[Company].[Division B].[Department B1] } ON ROWS,
        //NON EMPTY 
        // { [Account].[Account].[Expenses].[Travel].[Hotels], [Account].[Account].[Expenses].[Personnel].[Social Security] } ON COLUMNS
        //FROM [AccountingWb]
        //WHERE ( [Measures].[AmountPlan] )

        Map<List<Member>, Object> res = new HashMap<>();
        if (rowMembers.isEmpty()) {
            return getData(members, measureUniqueName, cube);
        } else {
            final StringBuilder buf = new StringBuilder();
            buf.append("select NON EMPTY ");
            boolean starFlag = false;
            for (Set<Member> ms : rowMembers) {
                if (!starFlag) {
                    starFlag = true;
                } else {
                    buf.append(" * ");
                }
                buf.append("{ ");
                boolean commaFlag = false;
                for (Member m : ms) {
                    if (!commaFlag) {
                        commaFlag = true;
                    } else {
                        buf.append(", ");
                    }
                    buf.append(m.getUniqueName());
                }
                buf.append(" }");
            }
            buf.append(" ON ROWS, NON EMPTY { ");
            boolean commaFlag = false;
            for (Member m : members) {
                if (!commaFlag) {
                    commaFlag = true;
                } else {
                    buf.append(", ");
                }
                buf.append(m.getUniqueName());
            }
            buf.append(" } ON COLUMNS FROM ").append(cube.getName());
            buf.append(" WHERE ( ").append(measureUniqueName).append(" )");

            final String mdx = buf.toString();
            final Connection connection =
                cube.getCatalog().getInternalConnection();
            final Query query = connection.parseQuery(mdx);
            final Result result = connection.execute(query);
            org.eclipse.daanse.olap.api.result.Axis rAxis = result.getAxes()[0];
            org.eclipse.daanse.olap.api.result.Axis cAxis = result.getAxes()[1];
            List<List<Member>> rows = rAxis.getTupleList();
            List<List<Member>> cols = cAxis.getTupleList();
            for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
                List<Member> rowTuple = rows.get(rIdx);
                for (int cIdx = 0; cIdx < cols.size(); cIdx++) {
                    Object value = result.getCell(new int[]{rIdx, cIdx}).getValue();
                    if (value == null) continue;

                    List<Member> fullTuple = new ArrayList<>(rowTuple);
                    for (Member colMember : cols.get(cIdx)) {
                        if (!colMember.isMeasure()) fullTuple.add(colMember);
                    }
                    res.put(fullTuple, value);
                }
            }
            return res;
        }
    }

}
