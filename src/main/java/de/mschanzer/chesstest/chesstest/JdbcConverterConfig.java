package de.mschanzer.chesstest.chesstest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties; // Import hinzufügen
import com.zaxxer.hikari.HikariDataSource; // Import hinzufügen

import javax.sql.DataSource; // Import hinzufügen
import java.util.Arrays;
import java.util.List;

@Configuration
public class JdbcConverterConfig extends AbstractJdbcConfiguration {

    @Bean
    @Override
    @Primary
    public JdbcCustomConversions jdbcCustomConversions() {
        List<?> converters = Arrays.asList(
                new BooleanToIntegerConverter(),
                new IntegerToBooleanConverter()
        );
        return new JdbcCustomConversions(converters);
    }

    // Dies stellt sicher, dass Spring Boot's Standard-DataSourceProperties verwendet werden.
    // Es ist wichtig, dass diese Bean existiert und korrekt konfiguriert ist.
    @Bean
    @Primary // Markiere sie als primär, falls es andere DataSourceProperties-Beans gibt
    @ConfigurationProperties("spring.datasource") // Bindet die Eigenschaften aus application.properties
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    // Überschreibe die Standard-DataSource-Bean, um unseren benutzerdefinierten Wrapper zu verwenden
    @Bean
    @Primary // Stelle sicher, dass diese DataSource verwendet wird
    @ConfigurationProperties("spring.datasource.hikari") // Bindet Hikari-spezifische Eigenschaften
    public DataSource dataSource(DataSourceProperties properties) {
        // Erstelle die Standard HikariDataSource
        HikariDataSource hikariDataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        // Umwickle sie mit unserer ReadOnlyAwareDataSource
        return new ReadOnlyAwareDataSource(hikariDataSource);
    }
}