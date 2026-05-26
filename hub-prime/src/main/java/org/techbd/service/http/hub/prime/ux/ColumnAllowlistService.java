package org.techbd.service.http.hub.prime.ux;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ColumnAllowlistService {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnAllowlistService.class);

    private final DSLContext dsl;

    // key: "schemaName.tableName" (lowercase), value: set of lowercase column names
    private volatile Map<String, Set<String>> allowedColumns = new ConcurrentHashMap<>();

    public ColumnAllowlistService(@Qualifier("primaryDslContext") DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostConstruct
    public void load() {
        refresh();
    }

    public void refresh() {
        Map<String, Set<String>> fresh = new ConcurrentHashMap<>();
        dsl.select(
                DSL.field(DSL.name("table_schema")),
                DSL.field(DSL.name("table_name")),
                DSL.field(DSL.name("column_name")))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .fetch()
                .forEach(r -> {
                    String schema = r.get(0, String.class);
                    String table = r.get(1, String.class);
                    String column = r.get(2, String.class);
                    if (schema != null && table != null && column != null) {
                        String key = schema.toLowerCase() + "." + table.toLowerCase();
                        fresh.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                                .add(column.toLowerCase());
                    }
                });
        this.allowedColumns = fresh;
        LOG.info("ColumnAllowlistService loaded {} table/view entries from information_schema.",
                fresh.size());
    }

    // validate a user-supplied column name against the actual schema
    // schemaName and tableName are trusted (already validated by controller pattern)
    // throws IllegalArgumentException if column is not found - do NOT echo userInput in message
    public String validate(String schemaName, String tableName, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException("Field name must not be blank.");
        }
        String normalized = userInput.strip().toLowerCase();
        String key = schemaName.toLowerCase() + "." + tableName.toLowerCase();
        Set<String> cols = allowedColumns.getOrDefault(key, Collections.emptySet());
        if (!cols.contains(normalized)) {
            LOG.warn("ColumnAllowlistService: rejected field '{}' for table '{}'", normalized, key);
            throw new IllegalArgumentException("Invalid field name.");
        }
        return normalized;
    }

    // returns true/false without throwing - useful for filtering maps
    public boolean isValid(String schemaName, String tableName, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        String key = schemaName.toLowerCase() + "." + tableName.toLowerCase();
        Set<String> cols = allowedColumns.getOrDefault(key, Collections.emptySet());
        return cols.contains(userInput.strip().toLowerCase());
    }
}