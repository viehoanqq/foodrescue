package com.foodrescue.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.*;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaRepositories(basePackageClasses = PaymentRepository.class)
@EnableTransactionManagement(proxyTargetClass = true)
@Import({JpaFinanceQueries.class, JpaFinanceLedger.class})
class JpaTestDatabase {
    @Bean
    DataSource dataSource() {
        return new DriverManagerDataSource(
                System.getenv("FINANCE_TEST_DB_URL"), "postgres", "finance_test");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
        var f = new LocalContainerEntityManagerFactoryBean();
        f.setDataSource(ds);
        f.setPackagesToScan("com.foodrescue.finance.infrastructure.adapter.out.persistence.entity");
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto", "validate"));
        return f;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(
                        com.fasterxml.jackson.databind.SerializationFeature
                                .WRITE_DATES_AS_TIMESTAMPS);
    }
}
