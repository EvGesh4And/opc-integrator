package ru.datana.integration.opc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("ru.datana.integration.opc.config")
@EnableScheduling
public class OpcIntegrationClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpcIntegrationClientApplication.class, args);
	}
}
