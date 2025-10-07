package ru.datana.integration.opc.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfiguration {

        @Bean
        RestTemplate restTemplate(RestTemplateBuilder builder) {
                return builder
                                .requestFactory(this::httpComponentsClientHttpRequestFactory)
                                .build();
        }

        private HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory() {
                CloseableHttpClient httpClient = HttpClientBuilder.create()
                                .setDefaultRequestConfig(RequestConfig.custom()
                                                .setRedirectsEnabled(false)
                                                .build())
                                .build();
                return new HttpComponentsClientHttpRequestFactory(httpClient);
        }
}
