package com.foodrescue.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.foodrescue.buildingblocks.health.DatabaseHealthController;

@SpringBootApplication
@EnableScheduling
@Import(DatabaseHealthController.class)
public class FinanceApplication {
    public static void main(String[] args) { SpringApplication.run(FinanceApplication.class, args); }
}
