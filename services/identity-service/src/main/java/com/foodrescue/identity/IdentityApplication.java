package com.foodrescue.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.foodrescue.buildingblocks.health.DatabaseHealthController;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.foodrescue.identity")
@EntityScan("com.foodrescue.identity.infrastructure.adapter.out.persistence.entity")
@EnableJpaRepositories("com.foodrescue.identity.infrastructure.adapter.out.persistence")
@EnableScheduling
@Import(DatabaseHealthController.class)
public class IdentityApplication {
    public static void main(String[] args) { SpringApplication.run(IdentityApplication.class, args); }
}
