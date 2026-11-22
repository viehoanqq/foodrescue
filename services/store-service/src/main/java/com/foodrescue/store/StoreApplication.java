package com.foodrescue.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.foodrescue.buildingblocks.health.DatabaseHealthController;

@SpringBootApplication
@EnableScheduling
@Import(DatabaseHealthController.class)
public class StoreApplication {
    public static void main(String[] args) { SpringApplication.run(StoreApplication.class, args); }
}
