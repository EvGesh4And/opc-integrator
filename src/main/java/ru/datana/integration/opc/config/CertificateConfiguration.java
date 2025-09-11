package ru.datana.integration.opc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "certificate")
public record CertificateConfiguration(String clientAlias, String commonName, String organization, String orgUnit,
		String locality, String state, String countryCode, String applicationUrl, String dnsName, String ipAddress) {
}
