package com.wordonline.server.local;

import javax.sql.DataSource;

/**
 * The in-memory database of a locally hosted server and the version of the static data loaded into
 * it, which the launcher reports in the {@code ready} line.
 */
public record LocalDatabase(DataSource dataSource, String staticVersion) {
}
