package ru.datana.integration.opc.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.config.ControllerApiProperties;

@Component
@RequiredArgsConstructor
@Slf4j
public class ControllerApiClient {
        private final RestTemplate restTemplate;
        private final ControllerApiProperties properties;

        public void start(String env, String controllerId) {
                post(env, controllerId, "/start");
        }

        public void startPredict(String env, String controllerId) {
                post(env, controllerId, "/start-predict");
        }

        public void stop(String env, String controllerId) {
                post(env, controllerId, "/stop");
        }

        public void updateStates(String env, String controllerId, Map<String, Object> payload) {
                patch(env, controllerId, "/variables/state", payload);
        }

        public void updateLimits(String env, String controllerId, Map<String, Object> payload) {
                patch(env, controllerId, "/variables/limits", payload);
        }

        public void updateOptimization(String env, String controllerId, Map<String, Object> payload) {
                patch(env, controllerId, "/optimization", payload);
        }

        private void post(String env, String controllerId, String suffix) {
                var url = buildUrl(env, controllerId, suffix);
                if (url == null) {
                        return;
                }
                try {
                        restTemplate.postForEntity(url, null, Void.class);
                        log.debug("POST {} succeeded", url);
                } catch (RestClientException e) {
                        log.error("POST {} failed: {}", url, e.getMessage());
                }
        }

        private void patch(String env, String controllerId, String suffix, Map<String, Object> payload) {
                var url = buildUrl(env, controllerId, suffix);
                if (url == null) {
                        return;
                }
                var headers = new HttpHeaders();
                headers.setContentType(APPLICATION_JSON);
                var entity = new HttpEntity<>(payload, headers);
                try {
                        restTemplate.exchange(url, HttpMethod.PATCH, entity, Void.class);
                        log.debug("PATCH {} payload {} succeeded", url, payload);
                } catch (RestClientException e) {
                        log.error("PATCH {} failed: {}. Payload: {}", url, e.getMessage(), payload);
                }
        }

        private String buildUrl(String env, String controllerId, String suffix) {
                var base = properties.resolveBaseUrl(env);
                if (base == null || base.isBlank()) {
                        log.error("Controller base URL is not configured for environment [{}]", env);
                        return null;
                }
                if (!base.endsWith("/")) {
                        base += "/";
                }
                if (suffix == null) {
                        suffix = "";
                }
                return base + controllerId + suffix;
        }
}
