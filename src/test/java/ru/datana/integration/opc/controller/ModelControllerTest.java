package ru.datana.integration.opc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.datana.integration.opc.exception.APIError;

@SpringBootTest
@AutoConfigureMockMvc
class ModelControllerTest {
	@Autowired
	MockMvc mvc;
	@Autowired
	private ObjectMapper mapper;

	@Test
	void test() throws Exception {
		mvc.perform(get("/models/{name}/{env}/mappings", "MODEL", "ENV")).andExpect(status().isNotFound())
				.andExpect(content().string(containsString("ENVIRONMENT")));
	}

	@Test
	void restClient() {
		var client = RestClient.builder().requestFactory(new MockMvcClientHttpRequestFactory(mvc)).build();
		var error = client.get().uri("/models/MODEL/ENV/mappings")
				.exchange((req, resp) -> mapper.readValue(resp.getBody(), APIError.class));
		assertThat(error).isNotNull().extracting("code", "description").containsExactly("404.404", "ENVIRONMENT is not found for ENV");
	}
}
