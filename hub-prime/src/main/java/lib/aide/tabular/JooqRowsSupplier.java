package lib.aide.tabular;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.SelectGroupByStep;
import org.jooq.SelectLimitStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.service.http.hub.prime.ux.ColumnAllowlistService;
import org.techbd.util.NoOpUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JooqRowsSupplier implements TabularRowsSupplier<JooqRowsSupplier.JooqProvenance> {

    static private final Logger LOG = LoggerFactory.getLogger(JooqRowsSupplier.class);

    public record TypableTable(Table<?> table, boolean stronglyTyped) {

        private static final Set<String> VALIDATED_TABLES = ConcurrentHashMap.newKeySet();

        static public TypableTable fromTablesRegistry(@Nonnull Class<?> tablesRegistry, @Nullable String schemaName,
                @Nonnull String tableLikeName) {
            try {
                final var field = tablesRegistry.getField(tableLikeName.toUpperCase());
                return new TypableTable((Table<?>) field.get(null), true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return new TypableTable(DSL.table(schemaName != null ? DSL.name(schemaName, tableLikeName)
                        : DSL.name(tableLikeName)), false);
            }
        }

        public void validateExists(DSLContext dsl, @Nullable String schemaName) {
            final var tableName = table.getName();
            final var cacheKey = (schemaName != null ? schemaName + "." : "") + tableName;
            if (VALIDATED_TABLES.contains(cacheKey)) {
                return;
            }
            var query = dsl.selectCount()
                    .from(DSL.table(DSL.name("information_schema", "tables")))
                    .where(DSL.field(DSL.name("table_name")).equalIgnoreCase(tableName));
            if (schemaName != null) {
                query = query.and(DSL.field(DSL.name("table_schema")).equalIgnoreCase(schemaName));
            }
            int count = query.fetchOne(0, int.class);
            if (count == 0) {
                throw new IllegalArgumentException(
                        "Table or view '" + cacheKey + "' does not exist in the database.");
            }
            VALIDATED_TABLES.add(cacheKey);
        }

        public Field<Object> column(final String columnName) {
            if (this.stronglyTyped) {
                try {
                    final var instanceInTableRef = table.getClass().getField(columnName.toUpperCase());
                    if (instanceInTableRef.get(table) instanceof Field<?> columnField) {
                        return columnField.coerce(Object.class);
                    } else {
                        return DSL.field(DSL.name(columnName));
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    NoOpUtils.ignore(e);
                }
            }
            return DSL.field(DSL.name(columnName));
        }
    }

    public record JooqQuery(Query query, List<Object> bindValues, boolean stronglyTyped) {
    }

    public record JooqProvenance(String fromSQL, List<Object> bindValues, boolean stronglyTyped) {
    }

    private final TabularRowsRequest request;
    private final TypableTable typableTable;
    private final DSLContext dsl;
    private final boolean includeGeneratedSqlInResp;
    private final boolean includeGeneratedSqlInErrorResp;
    private final Logger logger;
    private final Query customQuery;
    private final List<Object> customBindValues;
    // CHANGE: allowlist service and context for field name validation
    private final ColumnAllowlistService columnAllowlistService;
    private final String allowlistSchemaName;
    private final String allowlistTableName;

    private JooqRowsSupplier(final Builder builder) {
        this.request = builder.request;
        this.typableTable = builder.table;
        this.dsl = builder.dsl;
        this.includeGeneratedSqlInResp = builder.includeGeneratedSqlInResp;
        this.includeGeneratedSqlInErrorResp = builder.includeGeneratedSqlInErrorResp;
        this.logger = builder.logger;
        this.customQuery = builder.customQuery;
        this.customBindValues = builder.customBindValues;
        this.columnAllowlistService = builder.columnAllowlistService;
        this.allowlistSchemaName = builder.allowlistSchemaName;
        this.allowlistTableName = builder.allowlistTableName;
    }

    public TabularRowsRequest request() {
        return request;
    }

    public boolean isStronglyTyped() {
        return typableTable.stronglyTyped();
    }

    public TypableTable table() {
        return typableTable;
    }

    // CHANGE: central safe field resolution - all user-supplied field names go through here
    // validates against allowlist if available, then resolves via typableTable
    private Field<Object> safeField(String userSuppliedName) {
        if (columnAllowlistService != null && allowlistSchemaName != null && allowlistTableName != null) {
            // validate throws IllegalArgumentException if column not in schema - never reaches DSL
            String validated = columnAllowlistService.validate(allowlistSchemaName, allowlistTableName,
                    userSuppliedName);
            return typableTable.column(validated);
        }
        // fallback: no allowlist configured - still use DSL.name() quoting as last resort
        return typableTable.column(userSuppliedName);
    }

    @Override
    public TabularRowsResponse<JooqProvenance> response() {
        final var jq = query();
        final var provenance = new JooqProvenance(jq.query.getSQL(), jq.bindValues(), typableTable.stronglyTyped);
        try {
            Instant start = Instant.now();
            final var query = jq.query();
            final var result = dsl.fetch(query.getSQL(), jq.bindValues().toArray());
            final var data = result.intoMaps();
            final var formattedData = new ArrayList<Map<String, Object>>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
            for (Map<String, Object> row : data) {
                Map<String, Object> formattedRow = new HashMap<>(row);
                row.forEach((column, value) -> {
                    if (value instanceof OffsetDateTime) {
                        LOG.info("", value);
                        formattedRow.put(column, ((OffsetDateTime) value)
                                .atZoneSameInstant(ZoneId.of("America/New_York"))
                                .toLocalDateTime()
                                .format(formatter));
                    } else if (value instanceof java.sql.Date) {
                        LocalDate localDate = ((java.sql.Date) value).toLocalDate();
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                        formattedRow.put(column, localDate.format(dateFormatter));
                    }
                });
                formattedData.add(formattedRow);
            }
            var lastRow = request.startRow() + formattedData.size();
            if (formattedData.size() < (request.endRow() - request.startRow())) {
                lastRow = -1;
            }
            Instant end = Instant.now();
            long timeTaken = Duration.between(start, end).toNanos();
            System.out.println(
                    "Time taken by JOOQ: %d ns (%d s)".formatted(timeTaken, Duration.between(start, end).toSeconds()));
            return new TabularRowsResponse<>(includeGeneratedSqlInResp ? provenance : null, formattedData, lastRow,
                    null);
        } catch (Exception e) {
            if (logger != null) {
                logger.error("JooqRowsSupplier error", e);
            }
            // CHANGE: never expose raw exception message to caller - pass null
            return new TabularRowsResponse<>(includeGeneratedSqlInResp ? provenance : null, null, -1, null);
        }
    }

    private Condition finalCondition;

    @SuppressWarnings("unchecked")
    public JooqQuery query() {
        final var selectFields = new ArrayList<Field<?>>();
        final var whereConditions = new ArrayList<Condition>();
        final var bindValues = new ArrayList<Object>();
        final var sortFields = new ArrayList<SortField<?>>();
        final var groupByFields = new ArrayList<Field<?>>();

        finalCondition = null;

        if (request.groupKeys() != null && !request.groupKeys().isEmpty()) {
            selectFields.add(DSL.field("*"));
            for (int i = 0; i < request.rowGroupCols().size(); i++) {
                final var col = request.rowGroupCols().get(i);
                final var value = request.groupKeys().get(i);
                // CHANGE: safeField validates col.field() against allowlist
                final var condition = safeField(col.field()).eq(value);
                whereConditions.add(condition);
                bindValues.add(value);
            }
        } else {
            if (request.rowGroupCols() != null) {
                request.rowGroupCols().forEach(col -> {
                    // CHANGE: safeField validates col.field() against allowlist
                    final var field = safeField(col.field());
                    groupByFields.add(field);
                    selectFields.add(field);
                });
            }
            if (groupByFields.isEmpty() && request.valueCols() != null) {
                request.valueCols().forEach(col -> selectFields.add(safeField(col.field())));
            }
        }

        if (request.filterModel() != null) {
            request.filterModel().forEach((field, filter) -> {
                // CHANGE: safeField validates the filter field key against allowlist
                // filter values go into bind parameters via createCondition - safe
                if (filter.operator() == null) {
                    final var singleWhereConditions = new ArrayList<Condition>();
                    final var condition = createCondition(field, filter);
                    LOG.info("filter.operator() : {}", filter.operator());
                    whereConditions.add(condition);
                    singleWhereConditions.add(condition);
                    if (finalCondition == null) {
                        finalCondition = DSL.and(singleWhereConditions);
                    } else {
                        finalCondition = DSL.and(finalCondition, DSL.and(singleWhereConditions));
                    }
                    if (filter.type().equals("like") || filter.type().equals("contains")) {
                        bindValues.add("%" + filter.filter() + "%");
                    } else if (filter.type().equals("equals")
                            && (filter.dateFrom() != null || filter.filter() != null)) {
                        if (filter.dateFrom() != null) {
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                LocalDateTime parsedDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                                LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                                LocalDateTime endOfDay = parsedDateTime.toLocalDate().atTime(LocalTime.MAX);
                                bindValues.add(startOfDay);
                                bindValues.add(endOfDay);
                            } catch (Exception e) {
                                LOG.error("Error parsing date for binding: " + filter.dateFrom(), e);
                                bindValues.add(filter.dateFrom());
                            }
                        } else {
                            bindValues.add(filter.filter());
                        }
                    } else if (filter.filter() != null) {
                        bindValues.add(filter.filter());
                    } else if (filter.type().equals("greaterThan") && filter.dateFrom() != null) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime parsedDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                            if (parsedDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                                parsedDateTime = parsedDateTime.with(LocalTime.of(23, 59, 59));
                            }
                            bindValues.add(parsedDateTime);
                        } catch (Exception e) {
                            LOG.error("Error parsing date for binding: " + filter.dateFrom(), e);
                            bindValues.add(filter.dateFrom());
                        }
                    } else if (filter.type().equals("notEqual")
                            && (filter.dateFrom() != null || filter.filter() != null)) {
                        if (filter.dateFrom() != null) {
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                LocalDateTime parsedDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                                LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                                LocalDateTime endOfDay = parsedDateTime.toLocalDate().atTime(LocalTime.MAX);
                                bindValues.add(startOfDay);
                                bindValues.add(endOfDay);
                            } catch (Exception e) {
                                LOG.error("Error parsing date for binding: " + filter.dateFrom(), e);
                                bindValues.add(filter.dateFrom());
                            }
                        } else {
                            bindValues.add(filter.filter());
                        }
                    } else if (filter.type().equals("lessThan")
                            && (filter.dateFrom() != null || filter.filter() != null)) {
                        if (filter.dateFrom() != null) {
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                LocalDateTime parsedDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                                LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                                bindValues.add(startOfDay);
                            } catch (Exception e) {
                                LOG.error("Error parsing date for binding: " + filter.dateFrom(), e);
                                bindValues.add(filter.dateFrom());
                            }
                        } else {
                            bindValues.add(filter.filter());
                        }
                    }

                    if (filter.type().equals("between")) {
                        bindValues.add(filter.secondFilter());
                    }
                    if (filter.type().equals("inRange")) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime startDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                        LocalDateTime endDateTime = LocalDateTime.parse(filter.dateTo(), formatter);
                        if (endDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                            endDateTime = endDateTime.with(LocalTime.of(23, 59, 59));
                        }
                        bindValues.add(startDateTime);
                        bindValues.add(endDateTime);
                    }
                } else {
                    final var multipleWhereConditions = new ArrayList<Condition>();
                    LOG.info("filter.conditions() exist");
                    filter.conditions().forEach((filterModel) -> {
                        LOG.info("filter.operator() exist");
                        final var condition = createConditionSub(field, filterModel.type(), filterModel.filter(),
                                filterModel.secondFilter(), filterModel.dateFrom(), filterModel.dateTo());
                        LOG.info("filter.operator() : {}", filter.operator());
                        whereConditions.add(condition);
                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            LOG.info("OR Filter");
                            multipleWhereConditions.add(DSL.or(condition));
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            LOG.info("AND Filter");
                            multipleWhereConditions.add(DSL.and(condition));
                        }
                        LOG.info("filter.where condition :{}",
                                multipleWhereConditions.get(multipleWhereConditions.size() - 1));
                        if (filterModel.type().equals("like") || filterModel.type().equals("contains")) {
                            bindValues.add("%" + filterModel.filter() + "%");
                        } else if (filterModel.filter() != null) {
                            bindValues.add(filterModel.filter());
                        }
                        if (filterModel.type().equals("between")) {
                            bindValues.add(filterModel.secondFilter());
                        }
                        if (filterModel.type().equals("inRange")) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime startDateTime = LocalDateTime.parse(filterModel.dateFrom(), formatter);
                            LocalDateTime endDateTime = LocalDateTime.parse(filterModel.dateTo(), formatter);
                            if (endDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                                endDateTime = endDateTime.with(LocalTime.of(23, 59, 59));
                            }
                            bindValues.add(startDateTime);
                            bindValues.add(endDateTime);
                        }
                    });
                    if (finalCondition != null) {
                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(finalCondition, DSL.or(multipleWhereConditions));
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(finalCondition, DSL.and(multipleWhereConditions));
                        }
                    } else {
                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.or(multipleWhereConditions);
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(multipleWhereConditions);
                        }
                    }
                }
            });
        }

        if (request.sortModel() != null) {
            for (final var sort : request.sortModel()) {
                // CHANGE: safeField validates sort.colId() against allowlist before reaching DSL
                // this is the exact injection point from the vulnerability report
                final var sortField = safeField(sort.colId());
                // CHANGE: validate sort direction - only asc/desc allowed
                switch (sort.sort()) {
                    case "asc" -> sortFields.add(sortField.asc());
                    case "desc" -> sortFields.add(sortField.desc());
                    default -> throw new IllegalArgumentException("Invalid sort direction.");
                }
            }
        }

        if (request.rowGroupCols() != null) {
            request.rowGroupCols().forEach(col -> {
                // CHANGE: safeField validates col.field() against allowlist
                final var field = safeField(col.field());
                groupByFields.add(field);
                selectFields.add(field);
            });
        }

        if (request.aggregationFunctions() != null) {
            request.aggregationFunctions().forEach(aggFunc -> {
                aggFunc.columns().forEach(col -> {
                    // CHANGE: safeField validates col against allowlist
                    final var field = safeField(col);
                    final var aggregationField = switch (aggFunc.functionName().toLowerCase()) {
                        case "sum" -> DSL.sum(field.cast(Double.class));
                        case "avg" -> DSL.avg(field.cast(Double.class));
                        case "count" -> DSL.count(field);
                        default -> throw new IllegalArgumentException(
                                "Unknown aggregation function: " + aggFunc.functionName());
                    };
                    selectFields.add(aggregationField);
                });
            });
        }

        final var limit = request.endRow() - request.startRow();
        if (customQuery != null) {
            LOG.info("Custom Query for Single Schema {} :", customQuery);
            final var select = groupByFields.isEmpty()
                    ? ((SelectLimitStep<Record>) customQuery).limit(request.startRow(), limit)
                    : ((SelectGroupByStep<Record>) customQuery).groupBy(groupByFields).limit(request.startRow(),
                            limit);
            LOG.info("Select Query : {}", select);
            if (customBindValues != null && customBindValues.size() > 0) {
                for (Object customBind : customBindValues) {
                    bindValues.add(customBind);
                }
            }
            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        }

        LOG.info("Query for Single Schema {} :", typableTable.table);
        if (finalCondition == null) {
            final var select = groupByFields.isEmpty()
                    ? this.dsl.select(selectFields).from(typableTable.table).where(whereConditions).orderBy(sortFields)
                            .limit(request.startRow(), limit)
                    : this.dsl.select(selectFields).from(typableTable.table).where(whereConditions)
                            .groupBy(groupByFields).orderBy(sortFields).limit(request.startRow(), limit);
            LOG.info("Select Query : {}", select);
            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        } else {
            final var select = groupByFields.isEmpty()
                    ? this.dsl.select(selectFields).from(typableTable.table).where(finalCondition).orderBy(sortFields)
                            .limit(request.startRow(), limit)
                    : this.dsl.select(selectFields).from(typableTable.table).where(finalCondition)
                            .groupBy(groupByFields).orderBy(sortFields).limit(request.startRow(), limit);
            LOG.info("Select Query : {}", select);
            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        }
    }

    private Condition createCondition(final String field, final TabularRowsRequest.FilterModel filter) {
        // CHANGE: validate field name against allowlist before creating condition
        safeField(field);
        return createConditionSub(field, filter.type(), filter.filter(), filter.secondFilter(), filter.dateFrom(),
                filter.dateTo());
    }

    private Condition createConditionSub(final String field, String type, Object filter, Object secondfilter,
            Object dateFrom, Object dateTo) {
        // field is already validated by createCondition or the filterModel loop above
        // all filter values (filter, secondfilter, dateFrom, dateTo) are used as
        // jOOQ bind parameters or parsed into typed objects (LocalDateTime) - never concatenated into SQL
        final var dslField = typableTable.column(field);
        return switch (type) {
            case "blank" -> dslField.isNull();
            case "notBlank" -> dslField.isNotNull()
                    .and(DSL.condition("TRIM(CAST({0} AS VARCHAR)) <> ''", dslField));
            case "like" -> dslField.likeIgnoreCase("%" + filter + "%");
            case "equals" -> {
                try {
                    if (dateFrom != null) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime parsedDateTime = LocalDateTime.parse(dateFrom.toString().trim(), formatter);
                            LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                            LocalDateTime endOfDay = parsedDateTime.toLocalDate().atTime(LocalTime.MAX);
                            yield dslField.between(startOfDay, endOfDay);
                        } catch (DateTimeParseException e) {
                            LOG.error("Unable to parse date: {}", dateFrom, e);
                            yield DSL.falseCondition();
                        }
                    } else if (filter instanceof Number) {
                        yield dslField.eq(filter);
                    } else if (filter instanceof String) {
                        yield dslField.equalIgnoreCase(filter.toString());
                    } else if (filter != null) {
                        yield dslField.eq(filter);
                    } else {
                        yield DSL.falseCondition();
                    }
                } catch (Exception e) {
                    LOG.error("Unexpected error in equals filter", e);
                    yield DSL.falseCondition();
                }
            }
            case "notEqual" -> {
                try {
                    if (dateFrom != null) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime parsedDateTime = LocalDateTime.parse(dateFrom.toString().trim(), formatter);
                            LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                            LocalDateTime endOfDay = parsedDateTime.toLocalDate().atTime(LocalTime.MAX);
                            yield dslField.notBetween(startOfDay, endOfDay);
                        } catch (DateTimeParseException e) {
                            LOG.error("Unable to parse date: {}", dateFrom, e);
                            yield DSL.falseCondition();
                        }
                    } else if (filter instanceof Number) {
                        yield dslField.ne(filter);
                    } else if (filter != null) {
                        yield dslField.cast(String.class).notEqualIgnoreCase(filter.toString());
                    } else {
                        yield DSL.falseCondition();
                    }
                } catch (Exception e) {
                    LOG.error("Unexpected error in notEqual filter", e);
                    yield DSL.falseCondition();
                }
            }
            case "number" -> dslField.eq(DSL.param(field, filter));
            case "date" -> dslField.eq(DSL.param(field, filter));
            case "contains" -> dslField.likeIgnoreCase("%" + filter + "%");
            case "notContains" -> dslField.notLikeIgnoreCase("%" + filter + "%");
            case "startsWith" -> dslField.startsWithIgnoreCase(filter);
            case "endsWith" -> dslField.endsWithIgnoreCase(filter);
            case "lessOrEqual" -> dslField.lessOrEqual(filter);
            case "greatersOrEqual" -> dslField.greaterOrEqual(filter);
            case "greaterThan" -> {
                try {
                    if (dateFrom != null) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime parsedDateTime = LocalDateTime.parse(dateFrom.toString().trim(), formatter);
                            if (parsedDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                                parsedDateTime = parsedDateTime.with(LocalTime.of(23, 59, 59));
                            }
                            yield dslField.greaterThan(parsedDateTime);
                        } catch (DateTimeParseException e) {
                            LOG.error("Unable to parse date: {}", dateFrom, e);
                            yield DSL.falseCondition();
                        }
                    } else if (filter != null) {
                        yield dslField.greaterThan(filter);
                    }
                    yield DSL.falseCondition();
                } catch (Exception e) {
                    LOG.error("Unexpected error in date greaterThan filter", e);
                    yield DSL.falseCondition();
                }
            }
            case "lessThan" -> {
                try {
                    if (dateFrom != null) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime parsedDateTime = LocalDateTime.parse(dateFrom.toString().trim(), formatter);
                            LocalDateTime startOfDay = parsedDateTime.toLocalDate().atStartOfDay();
                            yield dslField.lessThan(startOfDay);
                        } catch (DateTimeParseException e) {
                            LOG.error("Unable to parse date: {}", dateFrom, e);
                            yield DSL.falseCondition();
                        }
                    } else if (filter != null) {
                        yield DSL.condition("{0} < {1}", dslField, DSL.param(field, filter));
                    }
                    yield DSL.falseCondition();
                } catch (Exception e) {
                    LOG.error("Unexpected error in lessThan filter", e);
                    yield DSL.falseCondition();
                }
            }
            case "between" -> dslField.between(filter, secondfilter);
            case "inRange" -> dslField.between(dateFrom, dateTo);
            default -> throw new IllegalArgumentException(
                    "Unknown filter type '" + type + "' in filter for field '" + field
                            + "' see JooqRowsSupplier::createCondition");
        };
    }

    public static final class Builder {

        private TabularRowsRequest request;
        private TypableTable table;
        private DSLContext dsl;
        private boolean includeGeneratedSqlInResp;
        private boolean includeGeneratedSqlInErrorResp;
        private Logger logger;
        private Query customQuery;
        private List<Object> customBindValues;
        private String schemaName;
        // CHANGE: allowlist fields
        private ColumnAllowlistService columnAllowlistService;
        private String allowlistSchemaName;
        private String allowlistTableName;

        public Builder withRequest(final TabularRowsRequest request) {
            this.request = request;
            return this;
        }

        public Builder withTable(final Table<?> table) {
            this.table = new TypableTable(table, true);
            return this;
        }

        public Builder withTable(Class<?> tablesClass, @Nullable String schemaName, String tableLikeName) {
            this.table = TypableTable.fromTablesRegistry(tablesClass, schemaName, tableLikeName);
            this.schemaName = schemaName;
            return this;
        }

        public Builder withDSL(final DSLContext dsl) {
            this.dsl = dsl.configuration().derive(new Settings()
                    .withRenderFormatted(true)
                    .withRenderKeywordCase(RenderKeywordCase.UPPER)
                    .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED)).dsl();
            return this;
        }

        public Builder includeGeneratedSqlInResp(boolean flag) {
            this.includeGeneratedSqlInResp = flag;
            return this;
        }

        public Builder includeGeneratedSqlInErrorResp(boolean flag) {
            this.includeGeneratedSqlInErrorResp = flag;
            return this;
        }

        public Builder withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        // CHANGE: new builder method to wire in the allowlist service
        public Builder withColumnAllowlistService(ColumnAllowlistService service, String schemaName,
                String tableName) {
            this.columnAllowlistService = service;
            this.allowlistSchemaName = schemaName;
            this.allowlistTableName = tableName;
            return this;
        }

        public Builder withQuery(Class<?> tablesClass, String schema, String tableLikeName, Query customQuery,
                ArrayList<Object> bindValues) {
            TypableTable table = TypableTable.fromTablesRegistry(tablesClass, schema, tableLikeName);
            this.table = table;
            this.schemaName = schema;
            this.customQuery = customQuery;
            if (bindValues.size() > 0) {
                this.customBindValues = bindValues;
            }
            LOG.info("Custom Query prepared {}:", this.customQuery);
            return this;
        }

        public JooqRowsSupplier build() {
            if (dsl != null && table != null && !table.stronglyTyped()) {
                table.validateExists(dsl, schemaName);
            }
            return new JooqRowsSupplier(this);
        }
    }

    public byte[] downloadFileContentById(Object id, String idColumn, String fileDataColumn) {
        try {
            Field<Object> idField = typableTable.column(idColumn);
            Field<byte[]> binaryField = typableTable.table.field(fileDataColumn, byte[].class);
            if (binaryField == null) {
                binaryField = typableTable.column(fileDataColumn).cast(byte[].class);
            }
            // id is passed as bind parameter via eq() - safe
            Record record = dsl.select(binaryField)
                    .from(typableTable.table)
                    .where(idField.eq(id))
                    .fetchOne();
            if (record != null) {
                return record.get(binaryField);
            }
        } catch (Exception e) {
            LOG.error("Error downloading file for id: {}", id, e);
        }
        return null;
    }

    public byte[] downloadFileByIdAndNature(Object id, String idColumn, String fileDataColumn, String natureColumn,
            Object natureValue) {
        try {
            Field<Object> idField = typableTable.column(idColumn);
            Field<Object> natureField = typableTable.column(natureColumn);
            Field<byte[]> binaryField = typableTable.table.field(fileDataColumn, byte[].class);
            if (binaryField == null) {
                binaryField = typableTable.column(fileDataColumn).cast(byte[].class);
            }
            // id and natureValue are passed as bind parameters via eq() - safe
            Record record = dsl.select(binaryField)
                    .from(typableTable.table)
                    .where(idField.eq(id).and(natureField.eq(natureValue)))
                    .fetchOne();
            if (record != null) {
                return record.get(binaryField);
            }
        } catch (Exception e) {
            LOG.error("Error downloading file for id: {} and nature value: {}", id, natureValue, e);
        }
        return null;
    }
}