package io.binarycodes.whichday;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Empties every table. One database serves the whole suite, so a test that seeds
 * anything starts here: the fresh-service-per-method isolation the unit tests used to
 * get went with the map they were isolating.
 */
public class TestDatabase {

    private final JdbcTemplate jdbc;

    public TestDatabase(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Read from {@code information_schema} rather than a hard-coded list: a migration
     * that adds a table would otherwise leave it filling up for the rest of the run,
     * and the failure would land in whichever test happened to go next. Referential
     * integrity goes off for the duration because H2 refuses to truncate a table
     * another one points at, in any order.
     */
    public void empty() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            tableNames().forEach(table -> jdbc.execute("TRUNCATE TABLE \"" + table + "\""));
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /**
     * How many rows a table holds. For the assertions that are about a table nothing
     * above the service package can see — the {@code account} repository is
     * package-private, and rightly so.
     */
    public int rowsIn(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<String> tableNames() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'PUBLIC'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);
    }
}
