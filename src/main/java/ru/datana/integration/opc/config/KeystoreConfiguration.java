package ru.datana.integration.opc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keystore")
public record KeystoreConfiguration(String algo, String filename, String password) {
}
