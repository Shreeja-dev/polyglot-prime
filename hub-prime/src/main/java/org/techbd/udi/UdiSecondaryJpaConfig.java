package org.techbd.udi;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.techbd.conf.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@org.springframework.context.annotation.Configuration
public class UdiSecondaryJpaConfig {

    @Bean(name = "udiSecondaryDataSource")
    @Lazy
    @ConditionalOnProperty(name = "org.techbd.udi.secondary.jdbc.url")
    @ConfigurationProperties(prefix = "org.techbd.udi.secondary.jdbc")
    public DataSource udiSecondaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public DSLContext dslSecondary() {
        return new DefaultDSLContext(configurationSecondary());
    }

    public org.jooq.Configuration configurationSecondary() {
        final var jooqConfiguration = new DefaultConfiguration();
        jooqConfiguration.set(connectionProvider());
        jooqConfiguration.setSQLDialect(SQLDialect.POSTGRES);
        // jooqConfiguration
        // .set(new DefaultExecuteListenerProvider(exceptionTransformer()));
        return jooqConfiguration;
    }

    @Bean
    public DataSourceConnectionProvider connectionProvider() {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(udiSecondaryDataSource()));
    }

    /**
     * TODO: Add comment as to why this method is needed.
     *
     * @return
     */
    @Bean
    public ObjectMapper objectMapper() {
        return Configuration.objectMapper;
    }
}
