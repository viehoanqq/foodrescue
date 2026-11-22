package com.foodrescue.finance;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Import;
import com.foodrescue.buildingblocks.health.DatabaseHealthController;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "FoodRescue Payment & Finance API", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@EnableScheduling
@Import(DatabaseHealthController.class)
public class FinanceApplication {
    public static void main(String[] args) { SpringApplication.run(FinanceApplication.class, args); }
}
