package ru.datana.integration.opc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "key-pair")
public record KeyPairEntryConfiguration(int length, String clientAlias) {
}
