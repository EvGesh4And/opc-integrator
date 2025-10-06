package ru.datana.integration.opc.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "controller.api")
public class ControllerApiProperties {
        private String host = "localhost";
        private Integer port = 80;
        private String basePath = "/api/controller";
        private String baseUrl;
        private Map<String, String> environments = new HashMap<>();

        public String resolveBaseUrl(String env) {
                var override = environments.get(env);
                if (override != null && !override.isBlank()) {
                        return normalizeBase(override);
                }
                if (baseUrl != null && !baseUrl.isBlank()) {
                        return normalizeBase(baseUrl);
                }
                var hostPart = host == null ? "" : host;
                var portPart = port == null ? "" : ":" + port;
                var normalizedBasePath = normalizePath(basePath);
                return normalizeBase("http://%s%s%s".formatted(hostPart, portPart, normalizedBasePath));
        }

        private static String normalizeBase(String value) {
                if (value == null || value.isBlank()) {
                        return "";
                }
                var trimmed = value.trim();
                if (trimmed.endsWith("/")) {
                        return trimmed.substring(0, trimmed.length() - 1);
                }
                return trimmed;
        }

        private static String normalizePath(String value) {
                if (value == null || value.isBlank()) {
                        return "";
                }
                var trimmed = value.trim();
                if (!trimmed.startsWith("/")) {
                        trimmed = "/" + trimmed;
                }
                return trimmed;
        }
}
