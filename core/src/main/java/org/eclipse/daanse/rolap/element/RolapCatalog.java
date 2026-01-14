/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2019 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
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
 *   Stefan Bischof (bipolis.org) - initial
 */

package org.eclipse.daanse.rolap.element;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.DriverManager;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.CacheControl;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.IdentifierSegment;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.Quoting;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.access.AccessCatalog;
import org.eclipse.daanse.olap.api.access.AccessCube;
import org.eclipse.daanse.olap.api.access.AccessDatabaseColumn;
import org.eclipse.daanse.olap.api.access.AccessDatabaseSchema;
import org.eclipse.daanse.olap.api.access.AccessDatabaseTable;
import org.eclipse.daanse.olap.api.access.AccessDimension;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.access.RollupPolicy;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.DatabaseColumn;
import org.eclipse.daanse.olap.api.element.DatabaseSchema;
import org.eclipse.daanse.olap.api.element.DatabaseTable;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.NumericType;
import org.eclipse.daanse.olap.api.type.StringType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.RoleUnionGrantsException;
import org.eclipse.daanse.olap.exceptions.UnknownRoleException;
import org.eclipse.daanse.olap.query.component.FormulaImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.CacheMemberReader;
import org.eclipse.daanse.rolap.common.MemberReader;
import org.eclipse.daanse.rolap.common.MemberSource;
import org.eclipse.daanse.rolap.common.NoCacheMemberReader;
import org.eclipse.daanse.rolap.common.RolapCatalogKey;
import org.eclipse.daanse.rolap.common.RolapCatalogParameter;
import org.eclipse.daanse.rolap.common.RolapCatalogReader;
import org.eclipse.daanse.rolap.common.RolapNativeRegistry;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapStarRegistry;
import org.eclipse.daanse.rolap.common.SmartMemberReader;
import org.eclipse.daanse.rolap.common.SqlMemberSource;
import org.eclipse.daanse.rolap.common.aggmatcher.AggTableManager;
import org.eclipse.daanse.rolap.common.connection.InternalRolapConnection;
import org.eclipse.daanse.rolap.mapping.model.ColumnInternalDataType;
import org.eclipse.daanse.rolap.util.ClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RolapCatalog is a collection of {@link RolapCube}s and shared
 * {@link RolapDimension}s. It is shared betweeen {@link Connection}s. It
 * caches {@link MemberReader}s, etc.
 *
 * @see Connection
 * @author jhyde
 * @since 26 July, 2001
 */
public class RolapCatalog implements Catalog {
	public static final Logger LOGGER = LoggerFactory.getLogger(RolapCatalog.class);

	private String name;

	private String description;

	/**
	 * Internal use only.
	 */
	private Connection internalConnection;

	private RolapStarRegistry rolapStarRegistry;

	/**
	 * Holds cubes in this schema.
	 */
	private final Map<org.eclipse.daanse.rolap.mapping.model.Cube, RolapCube> mapMappingToRolapCube = new HashMap<>();

	private final Map<org.eclipse.daanse.rolap.mapping.model.DatabaseSchema, RolapDatabaseSchema> mapMappingToRolapDatabaseSchema = new HashMap<>();

	private final Map<org.eclipse.daanse.rolap.mapping.model.Table, RolapDatabaseTable> mapMappingToRolapDatabaseTable = new HashMap<>();

	private final Map<org.eclipse.daanse.rolap.mapping.model.Column, RolapDatabaseColumn> mapMappingToRolapDatabaseColumn = new HashMap<>();

	/**
	 * Maps {@link String shared hierarchy name} to {@link MemberReader}. Shared
	 * between all statements which use this connection.
	 */
	private final Map<org.eclipse.daanse.rolap.mapping.model.Dimension, MemberReader> mapSharedHierarchyToReader = new HashMap<>();

	/**
	 * Maps {@link String names of shared hierarchies} to {@link RolapHierarchy the
	 * canonical instance of those hierarchies}.
	 */
	private final Map<org.eclipse.daanse.rolap.mapping.model.Dimension, RolapHierarchy> mapSharedHierarchyNameToHierarchy = new HashMap<>();

	private List<RolapDatabaseSchema> rolapDbSchemas = new ArrayList<>();

	/**
	 * The default role for connections to this schema.
	 */
	private Role defaultRole;

	private ByteString sha512Bytes;

	/**
	 * A schema's aggregation information
	 */
	private AggTableManager aggTableManager;

	/**
	 * This is basically a unique identifier for this RolapCatalog instance used it
	 * its equals and hashCode methods.
	 */
	private final RolapCatalogKey key;

	/**
	 * Maps {@link AccessRoleMapping} to {@link Role roles }.
	 */
	private final Map<org.eclipse.daanse.rolap.mapping.model.AccessRole, Role> mapNameToRole = new HashMap<>();

	/**
	 * Maps {@link String names of sets} to {@link NamedSet named sets}.
	 */
	private final Map<String, NamedSet> mapNameToSet = new HashMap<>();

	private org.eclipse.daanse.rolap.mapping.model.Catalog mappingCatalog;

	public final List<RolapCatalogParameter> parameterList = new ArrayList<>();

	private Instant catalogLoadTime;

	/**
	 * List of warnings. Populated when a schema is created by a connection that has
	 * {@link org.eclipse.daanse.rolap.common.RolapConnectionProperties#Ignore Ignore}=true.
	 */
	private final List<Exception> warningList = new ArrayList<>();
	private MetaData metadata;

	/**
	 * Unique schema instance id that will be used to inform clients when the schema
	 * has changed.
	 *
	 *
	 * Expect a different ID for each Mondrian instance node.
	 */
	private final String id;

	private Context context;

	RolapNativeRegistry nativeRegistry;
	private final static String publicDimensionMustNotHaveForeignKey = "Dimension ''{0}'' has a foreign key. This attribute is only valid in private dimensions and dimension usages.";
	private final static String duplicateSchemaParameter = "Duplicate parameter ''{0}'' in schema";
	private final static String finalizerErrorRolapCatalog = "An exception was encountered while finalizing a RolapCatalog object instance.";
	private final static String namedSetHasBadFormula = "Named set ''{0}'' has bad formula";

	/**
	 * This is ONLY called by other constructors (and MUST be called by them) and
	 * NEVER by the Pool.
	 *
	 * @param key         Key
	 * @param rolapConnectionProps Connect properties
	 * @param context     Context
	 */
	public RolapCatalog(final RolapCatalogKey key, ConnectionProps rolapConnectionProps, final RolapContext context) {
		this.id = UUID.randomUUID().toString();
		this.key = key;
		rolapStarRegistry = new RolapStarRegistry(this, context);
		DriverManager.drivers().forEach(System.out::println);
		// the order of the next two lines is important
		this.defaultRole = RoleImpl.createRootRole(this);

		this.internalConnection = new InternalRolapConnection(context, this, rolapConnectionProps);
		context.removeConnection(internalConnection);
		context.removeStatement(internalConnection.getInternalStatement());

		this.aggTableManager = new AggTableManager(this, context);
		this.nativeRegistry = new RolapNativeRegistry(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class),
				context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class), context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class));

		load(context, rolapConnectionProps);
	}

	/**
	 * @deprecated for tests only!
	 */
	@Deprecated
    public
	RolapCatalog(RolapCatalogKey key, Connection internalConnection, final RolapContext context) {
		this.id = UUID.randomUUID().toString();
		this.key = key;
		this.defaultRole = RoleImpl.createRootRole(this);
		this.internalConnection = internalConnection;
		rolapStarRegistry = new RolapStarRegistry(this, context);
		this.nativeRegistry = new RolapNativeRegistry(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class),
				context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class), context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class));

	}

	protected void flushSegments() {
		final Connection internalConnection = getInternalConnection();
		if (internalConnection != null) {
			final CacheControl cc = internalConnection.getCacheControl(null);
			for (RolapCube cube : getCubeList()) {
				cc.flush(cc.createMeasuresRegion(cube));
			}
		}
	}

	/**
	 * Performs a sweep of the JDBC tables caches and the segment data. Only called
	 * internally when a schema and it's data must be refreshed.
	 */
	public void finalCleanUp() {
		// Cleanup the segment data.
		flushSegments();

	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RolapCatalog other)) {
			return false;
		}
		return other.key.equals(key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	protected Logger getLogger() {
		return LOGGER;
	}

	/**
	 * Method called by all constructors to load the catalog into DOM and build
	 * application mdx and sql objects.
	 *
	 * @param context  context
	 * @param connectionProps connection props
	 */
	protected void load(RolapContext context, ConnectionProps connectionProps) {

		this.context = context;
		// TODO: get from schema var
		mappingCatalog = context.getCatalogMapping();

		sha512Bytes = new ByteString(("" + mappingCatalog.hashCode()).getBytes());

		// todo: use this >jdk19
//		sha512Bytes = new ByteString(Objects.toIdentityString(xmlSchema).getBytes());

		load(mappingCatalog);

		aggTableManager.initialize(connectionProps, context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class));
		setSchemaLoadDate();
	}

	private void setSchemaLoadDate() {
		catalogLoadTime = Instant.now();
	}

	@Override
	public Instant getCatalogLoadDate() {
		return catalogLoadTime;
	}

	@Override
	public List<Exception> getWarnings() {
		return Collections.unmodifiableList(warningList);
	}

	@Override
	public Role getDefaultRole() {
		return defaultRole;
	}

//    public SchemaMapping getXMLSchema() {
//        return mappingSchema;
//    }

	@Override
	public String getName() {
		Util.assertPostcondition(name != null, "return != null");
		Util.assertPostcondition(name.length() > 0, "return.length() > 0");
		return name;
	}

	/**
	 * Returns this schema instance unique ID.
	 *
	 * @return A string representing the schema ID.
	 */
	@Override
	public String getId() {
		return this.id;
	}

	/**
	 * Returns this catalog instance unique key.
	 *
	 * @return a {@link RolapCatalogKey}.
	 */
	public RolapCatalogKey getKey() {
		return key;
	}

	@Override
	public MetaData getMetaData() {
		return metadata;
	}

	public List<RolapCatalogParameter> getParameterList() {
		return parameterList;
	}
	
	private void load(org.eclipse.daanse.rolap.mapping.model.Catalog mappingCatalog2) {
		this.name = mappingCatalog2.getName();
		if (name == null || name.equals("")) {
			throw Util.newError("<Schema> name must be set");
		}
		description=mappingCatalog2.getDescription();

		this.metadata = RolapMetaData.createMetaData(mappingCatalog2.getAnnotations());

		// Validate public dimensions.
		// me not relevant - should be validated before
//        for (MappingPrivateDimension mappingDimension : mappingSchema2.dimensions()) {
//            if (mappingDimension.foreignKey() != null) {
//                throw new MondrianException(MessageFormat.format(
//                    publicDimensionMustNotHaveForeignKey,
//                        mappingDimension.name()));
//            }
//        }

		// Create parameters.
		Set<String> parameterNames = new HashSet<>();
		for (org.eclipse.daanse.rolap.mapping.model.Parameter mappingParameter : mappingCatalog2.getParameters()) {
			String name = mappingParameter.getName();
			if (!parameterNames.add(name)) {
				throw new OlapRuntimeException(MessageFormat.format(duplicateSchemaParameter, name));
			}
			Type type;
			if (ColumnInternalDataType.STRING == mappingParameter.getDataType()) {
				type = StringType.INSTANCE;
			} else if (ColumnInternalDataType.NUMERIC == mappingParameter
					.getDataType()) {
				type = NumericType.INSTANCE;
			} else {
				type = new MemberType(null, null, null, null);
			}
			final String description = mappingParameter.getDescription();
			final boolean modifiable = mappingParameter.isModifiable();
			String defaultValue = mappingParameter.getDefaultValue();
			RolapCatalogParameter param = new RolapCatalogParameter(this, name, defaultValue, description, type,
					modifiable);
//            discard(param);
		}

		// Create cubes.
		for (org.eclipse.daanse.rolap.mapping.model.Cube cubeMapping : mappingCatalog2.getCubes()) {
//            if (cubeMapping.isEnabled()) {
			RolapCube cube = null;
			if (cubeMapping instanceof org.eclipse.daanse.rolap.mapping.model.PhysicalCube physicalCubeMapping) {
				cube = new RolapPhysicalCube(this, mappingCatalog2, physicalCubeMapping, context);
				// addCube(cubeMapping, cube);

			}
			if (cubeMapping instanceof org.eclipse.daanse.rolap.mapping.model.VirtualCube virtualCubeMapping) {
				cube = new RolapVirtualCube(this, mappingCatalog2, virtualCubeMapping, context);
				// addCube(cubeMapping, cube);
			}
//            }
		}

		// Create virtual cubes.
		// handled with cubes above
//        for (MappingVirtualCube xmlVirtualCube : mappingSchema2.virtualCubes()) {
//            if (xmlVirtualCube.enabled()) {
//                RolapCube cube =
//                    new RolapCube(this, mappingSchema2, xmlVirtualCube, context);
////                discard(cube);
//            }
//        }

		// Create named sets.
		for (org.eclipse.daanse.rolap.mapping.model.NamedSet namedSetsMapping : mappingCatalog2.getNamedSets()) {
			mapNameToSet.put(namedSetsMapping.getName(), createNamedSet(namedSetsMapping));
		}

	    for (org.eclipse.daanse.rolap.mapping.model.DatabaseSchema dbSchemaMapping : mappingCatalog2.getDbschemas()) {
	        RolapDatabaseSchema rolapDbSchema = new RolapDatabaseSchema();
	        List<DatabaseTable> rolapDbTables = new ArrayList<>();
	        rolapDbSchema.setName(rolapDbSchema.getName());

	        for (org.eclipse.daanse.rolap.mapping.model.Table table : dbSchemaMapping.getTables()) {
	            RolapDatabaseTable rolapDbTable = new RolapDatabaseTable();
	            List<DatabaseColumn> rolapDbColumns = new ArrayList<>();
	            rolapDbTable.setName(table.getName());

	            for (org.eclipse.daanse.rolap.mapping.model.Column column : table.getColumns()) {
	                RolapDatabaseColumn rolapDbColumn = new RolapDatabaseColumn();
	                rolapDbColumn.setName(column.getName());
	                rolapDbColumn.setType(column.getType()!= null ? DataTypeJdbc.fromValue(column.getType().getLiteral()) : null );
	                rolapDbColumn.setColumnSize(column.getColumnSize());
	                rolapDbColumn.setNullable(column.getNullable());
	                rolapDbColumn.setDecimalDigits(column.getDecimalDigits());
	                rolapDbColumns.add(rolapDbColumn);
	                mapMappingToRolapDatabaseColumn.put(column, rolapDbColumn);
	            }
	            rolapDbTable.setDbColumns(rolapDbColumns);
	            rolapDbTables.add(rolapDbTable);
	            mapMappingToRolapDatabaseTable.put(table, rolapDbTable);
	        }
	        rolapDbSchema.setDbTables(rolapDbTables);
	        rolapDbSchemas.add(rolapDbSchema);
	        mapMappingToRolapDatabaseSchema.put(dbSchemaMapping, rolapDbSchema);
	    }

		// Create roles.
		for (org.eclipse.daanse.rolap.mapping.model.AccessRole roleMapping : mappingCatalog2.getAccessRoles()) {
			Role role = createRole(roleMapping);
			mapNameToRole.put(roleMapping, role);
		}

		// Set default role.
		if (mappingCatalog2.getDefaultAccessRole() != null) {
			Role role = lookupRole(mappingCatalog2.getDefaultAccessRole());
			if (role == null) {

				String sb = new StringBuilder("Role '").append(mappingCatalog2.getDefaultAccessRole())
						.append("' not found").toString();

				final RuntimeException ex = new RuntimeException(sb);
				throw ex;
			} else {
				// At this stage, the only roles in mapNameToRole are
				// RoleImpl roles so it is safe to case.
				defaultRole = role;
			}
		}

	}

	/*
	 * static Scripts.ScriptDefinition toScriptDef(MappingScript script) { if
	 * (script == null) { return null; } final Scripts.ScriptLanguage language =
	 * Scripts.ScriptLanguage.lookup(script.language()); if (language == null) {
	 * throw Util.newError( new
	 * StringBuilder("Invalid script language '").append(script.language()).append(
	 * "'").toString()); } return new Scripts.ScriptDefinition(script.cdata(),
	 * language); }
	 */

	private NamedSet createNamedSet(org.eclipse.daanse.rolap.mapping.model.NamedSet namedSetsMapping) {
		final String formulaString = namedSetsMapping.getFormula();
		final Expression exp;
		try {
			exp = getInternalConnection().parseExpression(formulaString);
		} catch (Exception e) {
			throw new OlapRuntimeException(MessageFormat.format(namedSetHasBadFormula, namedSetsMapping.getName(), e));
		}
		final Formula formula = new FormulaImpl(
				new IdImpl(new IdImpl.NameSegmentImpl(namedSetsMapping.getName(), Quoting.UNQUOTED)), exp);
		return formula.getNamedSet();
	}

	private Role createRole(org.eclipse.daanse.rolap.mapping.model.AccessRole roleMapping) {
		if (!roleMapping.getReferencedAccessRoles().isEmpty()) {
			return createUnionRole(roleMapping);
		}

		RoleImpl role = new RoleImpl();
		for (org.eclipse.daanse.rolap.mapping.model.AccessCatalogGrant catalogGrantMapings : roleMapping.getAccessCatalogGrants()) {
			handleCatalogGrant(role, catalogGrantMapings);
		}
		role.makeImmutable();
		return role;
	}

	// package-local visibility for testing purposes
	public Role createUnionRole(org.eclipse.daanse.rolap.mapping.model.AccessRole roleMapping) {
		if (!roleMapping.getAccessCatalogGrants().isEmpty()) {
			throw new RoleUnionGrantsException();
		}

		List<? extends org.eclipse.daanse.rolap.mapping.model.AccessRole> referencedRoleMappings = roleMapping.getReferencedAccessRoles();
		List<Role> roleList = new ArrayList<>(referencedRoleMappings.size());
		for (org.eclipse.daanse.rolap.mapping.model.AccessRole refRoleMapping : referencedRoleMappings) {
			Role role = mapNameToRole.get(refRoleMapping);
			if (role == null) {
				throw new UnknownRoleException(refRoleMapping.getName());
			}
			roleList.add(role);
		}
		return RoleImpl.union(roleList);
	}

	// package-local visibility for testing purposes
	public void handleCatalogGrant(RoleImpl role, org.eclipse.daanse.rolap.mapping.model.AccessCatalogGrant schemaGrantMapings) {
		role.grant(this, getAccessCatalog(schemaGrantMapings.getCatalogAccess().getLiteral(), AccessCatalog.ALLOWED_SET));
		for (org.eclipse.daanse.rolap.mapping.model.AccessCubeGrant cubeGrant : schemaGrantMapings.getCubeGrants()) {
			handleCubeGrant(role, cubeGrant);
		}
		for (org.eclipse.daanse.rolap.mapping.model.AccessDatabaseSchemaGrant databaseSchemaGrant : schemaGrantMapings.getDatabaseSchemaGrants()) {
		    handleDatabaseSchemaGrant(role, databaseSchemaGrant);
		}
	}

	public void handleDatabaseSchemaGrant(RoleImpl role, org.eclipse.daanse.rolap.mapping.model.AccessDatabaseSchemaGrant databaseSchemaGrant) {
        RolapDatabaseSchema databaseSchema = lookupDatabaseSchema(databaseSchemaGrant.getDatabaseSchema());
        if (databaseSchema == null) {
            throw Util.newError(
                    new StringBuilder("Unknown databaseSchema '").append(databaseSchemaGrant.getDatabaseSchema().getName()).append("'").toString());
        }
        role.grant(databaseSchema, getAccessDatabaseSchema(databaseSchemaGrant.getDatabaseSchemaAccess().name(), AccessDatabaseSchema.ALLOWED_SET));
        for (org.eclipse.daanse.rolap.mapping.model.AccessTableGrant tableGrant : databaseSchemaGrant.getTableGrants()) {
            handleTableGrant(role, tableGrant);
        }
    }

    private void handleTableGrant(RoleImpl role, org.eclipse.daanse.rolap.mapping.model.AccessTableGrant tableGrant) {
        RolapDatabaseTable table = lookupTable(tableGrant.getTable());
        if (table == null) {
            throw Util.newError(
                    new StringBuilder("Unknown table '").append(tableGrant.getTable().getName()).append("'").toString());
        }
        role.grant(table, getAccessTable(tableGrant.getTableAccess().name(), AccessDatabaseTable.ALLOWED_SET));
        for (org.eclipse.daanse.rolap.mapping.model.AccessColumnGrant columnGrant : tableGrant.getColumnGrants()) {
            handleColumnGrant(role, columnGrant);
        }
    }

    private void handleColumnGrant(RoleImpl role, org.eclipse.daanse.rolap.mapping.model.AccessColumnGrant columnGrant) {
        RolapDatabaseColumn column = lookupColumn(columnGrant.getColumn());
        if (column == null) {
            throw Util.newError(
                    new StringBuilder("Unknown column '").append(columnGrant.getColumn().getName()).append("'").toString());
        }
        role.grant(column, getAccessColumn(columnGrant.getColumnAccess().name(), AccessDatabaseColumn.ALLOWED_SET));
    }

    private AccessDatabaseColumn getAccessColumn(String accessString, Set<AccessDatabaseColumn> allowedSet) {
        final AccessDatabaseColumn access = AccessDatabaseColumn.valueOf(accessString.toUpperCase());
        if (allowedSet.contains(access)) {
            return access; // value is ok
        }
        throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
    }

    private RolapDatabaseColumn lookupColumn(org.eclipse.daanse.rolap.mapping.model.Column column) {
        return mapMappingToRolapDatabaseColumn.get(column);
    }

    private AccessDatabaseTable getAccessTable(String accessString, Set<AccessDatabaseTable> allowedSet) {
        final AccessDatabaseTable access = AccessDatabaseTable.valueOf(accessString.toUpperCase());
        if (allowedSet.contains(access)) {
            return access; // value is ok
        }
        throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
    }

    private RolapDatabaseTable lookupTable(org.eclipse.daanse.rolap.mapping.model.Table table) {
        return mapMappingToRolapDatabaseTable.get(table);
    }

    private AccessDatabaseSchema getAccessDatabaseSchema(String accessString, Set<AccessDatabaseSchema> allowedSet) {
        final AccessDatabaseSchema access = AccessDatabaseSchema.valueOf(accessString.toUpperCase());
        if (allowedSet.contains(access)) {
            return access; // value is ok
        }
        throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
    }

    private RolapDatabaseSchema lookupDatabaseSchema(org.eclipse.daanse.rolap.mapping.model.DatabaseSchema databaseSchema) {
        return mapMappingToRolapDatabaseSchema.get(databaseSchema);
    }

    // package-local visibility for testing purposes
	public void handleCubeGrant(RoleImpl role, org.eclipse.daanse.rolap.mapping.model.AccessCubeGrant cubeGrant) {
		RolapCube cube = lookupCube(cubeGrant.getCube());
		if (cube == null) {
			throw Util.newError(
					new StringBuilder("Unknown cube '").append(cubeGrant.getCube().getName()).append("'").toString());
		}
		role.grant(cube, getAccessCube(cubeGrant.getCubeAccess().name(), AccessCube.ALLOWED_SET));

		CatalogReader reader = cube.getCatalogReader(null);
		for (org.eclipse.daanse.rolap.mapping.model.AccessDimensionGrant accessDimGrantMapping : cubeGrant.getDimensionGrants()) {
			Dimension dimension;
			if (accessDimGrantMapping.getDimension() != null) {
				dimension = lookup(cube, reader, DataType.DIMENSION, accessDimGrantMapping.getDimension().getName());// not
																														// sure
																														// here
																														// with
																														// switch
																														// to
																														// mapping
			} else {
				dimension = lookup(cube, reader, DataType.DIMENSION, "Measures");
			}
			role.grant(dimension, getAccessDimension(accessDimGrantMapping.getDimensionAccess().name(), AccessDimension.ALLOWED_SET));
		}

		for (org.eclipse.daanse.rolap.mapping.model.AccessHierarchyGrant hierarchyGrant : cubeGrant.getHierarchyGrants()) {
			handleHierarchyGrant(role, cube, reader, hierarchyGrant);
		}
	}

	// package-local visibility for testing purposes
	public void handleHierarchyGrant(RoleImpl role, RolapCube cube, CatalogReader reader,
			org.eclipse.daanse.rolap.mapping.model.AccessHierarchyGrant hierarchyGrant) {
		Hierarchy hierarchy;
		if (hierarchyGrant.getHierarchy() != null) {
			hierarchy = cube.lookupHierarchy(hierarchyGrant.getHierarchy());
		} else {
			hierarchy = lookup(cube, reader, DataType.HIERARCHY, "[Measures]");
		}

		final AccessHierarchy hierarchyAccess = getAccessHierarchy(hierarchyGrant.getHierarchyAccess().name(), AccessHierarchy.ALLOWED_SET);
		// Level topLevel = findLevelForHierarchyGrant(
		// cube, reader, hierarchyAccess, hierarchyGrant.getTopLevel(), "topLevel");
		Level topLevel = cube.lookupLevel(hierarchyGrant.getTopLevel(), hierarchy);
		// Level bottomLevel = findLevelForHierarchyGrant(
		// cube, reader, hierarchyAccess, hierarchyGrant.getBottomLevel(),
		// "bottomLevel");
		Level bottomLevel = cube.lookupLevel(hierarchyGrant.getBottomLevel(), hierarchy);
		RollupPolicy rollupPolicy;
		if (hierarchyGrant.getRollupPolicy() != null) {
			try {
				rollupPolicy = RollupPolicy.valueOf(hierarchyGrant.getRollupPolicy().getLiteral().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw Util.newError(new StringBuilder("Illegal rollupPolicy value '")
						.append(hierarchyGrant.getRollupPolicy()).append("'").toString());
			}
		} else {
			rollupPolicy = RollupPolicy.FULL;
		}
		role.grant(hierarchy, hierarchyAccess, topLevel, bottomLevel, rollupPolicy);

		final boolean ignoreInvalidMembers = reader.getContext()
		        .getConfigValue(ConfigConstants.IGNORE_INVALID_MEMBERS, ConfigConstants.IGNORE_INVALID_MEMBERS_DEFAULT_VALUE, Boolean.class);

		int membersRejected = 0;
		if (!hierarchyGrant.getMemberGrants().isEmpty()) {
			if (hierarchyAccess != AccessHierarchy.CUSTOM) {
				throw Util.newError("You may only specify <MemberGrant> if <Hierarchy> has access='custom'");
			}

			for (org.eclipse.daanse.rolap.mapping.model.AccessMemberGrant memberGrant : hierarchyGrant.getMemberGrants()) {
				Member member = reader.withLocus().getMemberByUniqueName(Util.parseIdentifier(memberGrant.getMember()),
						!ignoreInvalidMembers);
				if (member == null) {
					// They asked to ignore members that don't exist
					// (e.g. [Store].[USA].[Foo]), so ignore this grant
					// too.
					assert ignoreInvalidMembers;
					membersRejected++;
					continue;
				}
				if (member.getHierarchy() != hierarchy) {
					throw Util.newError(new StringBuilder("Member '").append(member).append("' is not in hierarchy '")
							.append(hierarchy).append("'").toString());
				}
				role.grant(member, getAccessMember(memberGrant.getMemberAccess().name(), AccessMember.ALLOWED_SET));
			}
		}

		if (membersRejected > 0 && hierarchyGrant.getMemberGrants().size() == membersRejected) {
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace(
						"Rolling back grants of Hierarchy '{}' to NONE, because it contains no valid restricted members",
						hierarchy.getUniqueName());
			}
			role.grant(hierarchy, AccessHierarchy.NONE, null, null, rollupPolicy);
		}
	}

	private <T extends OlapElement> T lookup(RolapCube cube, CatalogReader reader, DataType category, String name) {
		List<Segment> segments = Util.parseIdentifier(name);
		// noinspection unchecked
		return (T) reader.lookupCompound(cube, segments, true, category);
	}

	private Level findLevelForHierarchyGrant(RolapCube cube, CatalogReader schemaReader, AccessHierarchy hierarchyAccess,
			org.eclipse.daanse.rolap.mapping.model.Level levelMapping, String desc) {
		if (levelMapping == null) {
			return null;
		}

		if (hierarchyAccess != AccessHierarchy.CUSTOM) {
			throw Util.newError(
					new StringBuilder("You may only specify '").append(desc).append("' if access='custom'").toString());
		}
		return lookup(cube, schemaReader, DataType.LEVEL, levelMapping.getName());
	}

   private AccessCatalog getAccessCatalog(String accessString, Set<AccessCatalog> allowed) {
        final AccessCatalog access = AccessCatalog.valueOf(accessString.toUpperCase());
        if (allowed.contains(access)) {
            return access; // value is ok
        }
        throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
    }

   private AccessCube getAccessCube(String accessString, Set<AccessCube> allowed) {
       final AccessCube access = AccessCube.valueOf(accessString.toUpperCase());
       if (allowed.contains(access)) {
           return access; // value is ok
       }
       throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
   }

   private AccessDimension getAccessDimension(String accessString, Set<AccessDimension> allowed) {
       final AccessDimension access = AccessDimension.valueOf(accessString.toUpperCase());
       if (allowed.contains(access)) {
           return access; // value is ok
       }
       throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
   }

   private AccessHierarchy getAccessHierarchy(String accessString, Set<AccessHierarchy> allowed) {
       final AccessHierarchy access = AccessHierarchy.valueOf(accessString.toUpperCase());
       if (allowed.contains(access)) {
           return access; // value is ok
       }
       throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
   }

   private AccessMember getAccessMember(String accessString, Set<AccessMember> allowed) {
       final AccessMember access = AccessMember.valueOf(accessString.toUpperCase());
       if (allowed.contains(access)) {
           return access; // value is ok
       }
       throw Util.newError(new StringBuilder("Bad value access='").append(accessString).append("'").toString());
   }

	/**
	 * Finds a cube called cube in this schema; if no cube exists,
	 * failIfNotFound controls whether to raise an error or return
	 * null.
	 */
	public Cube lookupCube(final org.eclipse.daanse.rolap.mapping.model.Cube cube, final boolean failIfNotFound) {
		RolapCube mdxCube = lookupCube(cube);
		if (mdxCube == null && failIfNotFound) {
			throw new OlapRuntimeException(MessageFormat.format("MDX cube ''{0}'' not found", cube));
		}
		return mdxCube;
	}

	/**
	 * Finds a cube called 'cube' in the current catalog, or return null if no cube
	 * exists.
	 */
	public RolapCube lookupCube(final org.eclipse.daanse.rolap.mapping.model.Cube cubeMapping) {
		return mapMappingToRolapCube.get(cubeMapping);
	}

    @Override
    public Optional<RolapCube> lookupCube(String cubeName) {
        return mapMappingToRolapCube.entrySet().stream()
                .filter(e -> Util.normalizeName(e.getKey().getName()).equals(Util.normalizeName(cubeName))).findFirst()
                .map(Entry::getValue);
    }

	/**
	 * Returns an xmlCalculatedMember called 'calcMemberName' in the cube called
	 * 'cubeName' or return null if no calculatedMember or xmlCube by those name
	 * exists.
	 */
	public org.eclipse.daanse.rolap.mapping.model.CalculatedMember lookupMappingCalculatedMember(final String calcMemberName, final String cubeName) {
		for (final org.eclipse.daanse.rolap.mapping.model.Cube cube : mappingCatalog.getCubes()) {
			if (!Util.equalName(cube.getName(), cubeName)) {
				continue;
			}
			for (org.eclipse.daanse.rolap.mapping.model.CalculatedMember mappingCalcMember : cube.getCalculatedMembers()) {
				// FIXME: Since fully-qualified names are not unique, we
				// should compare unique names. Also, the logic assumes that
				// CalculatedMember.dimension is not quoted (e.g. "Time")
				// and CalculatedMember.hierarchy is quoted
				// (e.g. "[Time].[Weekly]").
				if (Util.equalName(
						// calcMemberFqName(mappingCalcMember),
						mappingCalcMember.getName(), calcMemberName)) {
					return mappingCalcMember;
				}
			}
		}
		return null;
	}

	public static String calcMemberFqName(org.eclipse.daanse.rolap.mapping.model.CalculatedMember mappingCalcMember) {
		if (mappingCalcMember.getHierarchy() != null) {
			return Util.makeFqName(mappingCalcMember.getHierarchy().getName(), mappingCalcMember.getName());
		}
		return null;
	}

	public List<RolapCube> getCubesWithStar(RolapStar star) {
		List<RolapCube> list = new ArrayList<>();
		for (RolapCube cube : mapMappingToRolapCube.values()) {
			if (star == cube.getStar()) {
				list.add(cube);
			}
		}
		return list;
	}

	/**
	 * Adds a cube to the cube name map.
	 *
	 * @param cubeMapping
	 * @see #lookupCube(String)
	 */
	protected void addCube(org.eclipse.daanse.rolap.mapping.model.Cube cubeMapping, final RolapCube cube) {
		mapMappingToRolapCube.put(cubeMapping, cube);
	}

	@Override
	public List<Cube> getCubes() {
		Collection<RolapCube> cubes = mapMappingToRolapCube.values();
		return cubes.stream()
		        .collect(Collectors.toList());
	}

	public List<RolapCube> getCubeList() {
		return new ArrayList<>(mapMappingToRolapCube.values());
	}

	RolapHierarchy getSharedHierarchy(final org.eclipse.daanse.rolap.mapping.model.Dimension name) {
		return mapSharedHierarchyNameToHierarchy.get(name);
	}

	@Override
	public NamedSet getNamedSet(String name) {
		return mapNameToSet.get(name);
	}

	@Override
	public NamedSet getNamedSet(IdentifierSegment segment) {
		// FIXME: write a map that efficiently maps segment->value, taking
		// into account case-sensitivity etc.
		for (Map.Entry<String, NamedSet> entry : mapNameToSet.entrySet()) {
			if (Util.matches(segment, entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	public Role lookupRole(final org.eclipse.daanse.rolap.mapping.model.AccessRole accessRoleMapping) {
		return mapNameToRole.get(accessRoleMapping);
	}

	@Override
	public Role lookupRole(final String roleName) {

		Optional<Role> oRole = mapNameToRole.entrySet().stream().filter(e -> roleName.equals(e.getKey().getName()))
				.findFirst().map(Entry::getValue);
		return oRole.orElse(null);
	}

	public Set<String> roleNames() {
		return mapNameToRole.keySet().stream().map(org.eclipse.daanse.rolap.mapping.model.AccessRole::getName).collect(Collectors.toSet());
	}

	@Override
	public Parameter[] getParameters() {
		return parameterList.toArray(Parameter[]::new);
	}

	/**
	 * Gets a {@link MemberReader} with which to read a hierarchy. If the hierarchy
	 * is shared (sharedName is not null), looks up a reader from a
	 * cache, or creates one if necessary.
	 *
	 *
	 * Synchronization: thread safe
	 */
	synchronized MemberReader createMemberReader(final org.eclipse.daanse.rolap.mapping.model.Dimension xmlDimension, final RolapHierarchy hierarchy,
			final String memberReaderClass) {
		MemberReader reader;
		if (xmlDimension != null) {
			reader = mapSharedHierarchyToReader.get(xmlDimension);
			if (reader == null) {
				reader = createMemberReader(hierarchy, memberReaderClass);
				// share, for other uses of the same shared hierarchy
				if (false) {
					mapSharedHierarchyToReader.put(xmlDimension, reader);
				}
				/*
				 * System.out.println("RolapCatalog.createMemberReader: "+
				 * "add to sharedHierName->Hier map"+ " sharedName=" + sharedName +
				 * ", hierarchy=" + hierarchy.getName() + ", hierarchy.dim=" +
				 * hierarchy.getDimension().getName() ); if
				 * (mapSharedHierarchyNameToHierarchy.containsKey(sharedName)) {
				 * System.out.println("RolapCatalog.createMemberReader: CONTAINS NAME"); } else {
				 * mapSharedHierarchyNameToHierarchy.put(sharedName, hierarchy); }
				 */
				mapSharedHierarchyNameToHierarchy.computeIfAbsent(xmlDimension, k -> hierarchy);

				// mapSharedHierarchyNameToHierarchy.put(sharedName, hierarchy);
			} else {
//                final RolapHierarchy sharedHierarchy = (RolapHierarchy)
//                        mapSharedHierarchyNameToHierarchy.get(sharedName);
//                final RolapDimension sharedDimension = (RolapDimension)
//                        sharedHierarchy.getDimension();
//                final RolapDimension dimension =
//                    (RolapDimension) hierarchy.getDimension();
//                Util.assertTrue(
//                        dimension.getGlobalOrdinal() ==
//                        sharedDimension.getGlobalOrdinal());
			}
		} else {
			reader = createMemberReader(hierarchy, memberReaderClass);
		}
		return reader;
	}

	/**
	 * Creates a {@link MemberReader} with which to Read a hierarchy.
	 */
	private MemberReader createMemberReader(final RolapHierarchy hierarchy, final String memberReaderClass) {
		if (memberReaderClass != null) {
			Exception e2;
			try {
				Properties properties = null;
				Class<?> clazz = ClassResolver.INSTANCE.forName(memberReaderClass, true);
				Constructor<?> constructor = clazz.getConstructor(RolapHierarchy.class, Properties.class);
				Object o = constructor.newInstance(hierarchy, properties);
				if (o instanceof MemberReader) {
					return (MemberReader) o;
				} else if (o instanceof MemberSource) {
					return new CacheMemberReader((MemberSource) o);
				} else {
					throw Util.newInternal(new StringBuilder("member reader class ").append(clazz)
							.append(" does not implement ").append(MemberSource.class).toString());
				}
			} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
				e2 = e;
			}
			throw Util.newInternal(e2, "while instantiating member reader '" + memberReaderClass);
		} else {
			SqlMemberSource source = new SqlMemberSource(hierarchy);

			LOGGER.debug("Normal cardinality for {}", hierarchy.getDimension());
			if (internalConnection.getContext().getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class)) {
				// If the cell cache is disabled, we can't cache
				// the members or else we get undefined results,
				// depending on the functions used and all.
				return new NoCacheMemberReader(source);
			} else {
				return new SmartMemberReader(source);
			}

		}
	}

	@Override
	public CatalogReader getCatalogReaderWithDefaultRole() {
		return new RolapCatalogReader(context, defaultRole, this).withLocus();
	}

	/**
	 * Returns the checksum of this schema. Returns null if
	 * RolapConnectionProperties#UseContentChecksum is set to false.
	 *
	 * @return MD5 checksum of this schema
	 */
	public ByteString getChecksum() {
		return sha512Bytes;
	}

	/**
	 * Connection for purposes of parsing and validation. Careful! It won't have the
	 * correct locale or access-control profile.
	 */
	@Override
	public Connection getInternalConnection() {
		return internalConnection;
	}

	public RolapStarRegistry getRolapStarRegistry() {
		return rolapStarRegistry;
	}

	public RolapNativeRegistry getNativeRegistry() {
		return nativeRegistry;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public List<? extends DatabaseSchema> getDatabaseSchemas() {
		return rolapDbSchemas;
	}

}
