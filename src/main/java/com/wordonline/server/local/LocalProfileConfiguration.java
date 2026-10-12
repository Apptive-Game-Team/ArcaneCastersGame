package com.wordonline.server.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Replaces the external dependencies of a shared server for a server the desktop client runs:
 * PostgreSQL becomes an in-memory H2 database loaded from the client's static data, and the account
 * server's JSON Web Key Set becomes the client's init token. {@link LocalInit} and
 * {@link LocalProtocol} are registered by {@link LocalLauncher} before the context refreshes.
 */
@Configuration
@Profile("local")
public class LocalProfileConfiguration {

    // NON_KEYWORDS=VALUE: parameter_values.value is read unquoted, and VALUE is a keyword in H2.
    // A fresh name per context: H2 keeps a named in-memory database for the whole JVM
    // (DB_CLOSE_DELAY=-1), and a second context in the same JVM must not find the first one's tables.
    private static final String JDBC_URL_TEMPLATE = "jdbc:h2:mem:local-%s;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
            + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=VALUE";

    /**
     * Built and filled before any repository or cache can read it: everything that touches the
     * database depends on this bean, so the import finishes before the first query and before the web
     * server opens its port.
     */
    @Bean
    public LocalDatabase localDatabase(LocalInit localInit) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("local-h2");
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl(JDBC_URL_TEMPLATE.formatted(UUID.randomUUID()));
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(8);
        HikariDataSource dataSource = new HikariDataSource(config);
        try {
            String staticVersion = new LocalStaticDataImporter().importInto(dataSource, localInit.staticDataPath());
            return new LocalDatabase(dataSource, staticVersion);
        } catch (RuntimeException e) {
            dataSource.close();
            throw e;
        }
    }

    @Bean
    public DataSource dataSource(LocalDatabase localDatabase) {
        return localDatabase.dataSource();
    }

    // Tomcat's document root would otherwise be a fresh folder in java.io.tmpdir, which Spring Boot's
    // launcher reads before LocalLauncher can point it at dataDir. A low integrity process on Windows
    // (see LocalLauncher) may not write to %TEMP%, so the folder is named here instead.
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> localDocumentRoot(LocalInit localInit) {
        return factory -> {
            Path documentRoot = localInit.temporaryDirectory().resolve("docbase");
            try {
                Files.createDirectories(documentRoot);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            factory.setDocumentRoot(documentRoot.toFile());
        };
    }

    // The one JwtDecoder in a local context; JwtProvider injects it by that interface.
    @Bean
    public LocalTokenJwtDecoder jwtDecoder(LocalInit localInit) {
        return new LocalTokenJwtDecoder(localInit.token());
    }
}
