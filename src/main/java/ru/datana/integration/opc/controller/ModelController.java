package ru.datana.integration.opc.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.request.MappingDesc;
import ru.datana.integration.opc.request.ModelMeta;
import ru.datana.integration.opc.request.SubscribeRequest;
import ru.datana.integration.opc.request.ValueUpdateRequest;
import ru.datana.integration.opc.service.OpcService;
import ru.datana.integration.opc.dto.TagValue;

import ru.datana.integration.opc.exception.APIError;

@RestController
@RequestMapping("models")
@RequiredArgsConstructor
@Slf4j
public class ModelController {
	private static final ResponseEntity<Void> NO_CONTENT = ResponseEntity.noContent().build();
	private static final String PROCESSED = "processed";
	private final OpcService service;

	@Operation(summary = "Add or replace mappings to controller")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Mapping has been successfully created / updated"),
			@ApiResponse(responseCode = "400", description = "Validation error", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }),
			@ApiResponse(responseCode = "500", description = "Server error", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }), })
	@PostMapping("/{name}/{env}/mappings")
	public ResponseEntity<Void> replaceMappings(@PathVariable String name, @PathVariable String env,
			@Validated @RequestBody Set<MappingDesc> request) {
		log.debug("Replace [{}] model at [{}] environment mappings: {}", name, env, request);
		service.replaceMappings(name, env, request);
		log.debug(PROCESSED);
		return NO_CONTENT;
	}

	@Operation(summary = "Get mapping for controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Mapping for Controller for all environments"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }),
			@ApiResponse(responseCode = "500", description = "Server error", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }), })
	@GetMapping("/{name}/mappings")
	public Map<String, Set<MappingDesc>> getControllerMappings(@PathVariable String name) {
		log.debug("Get [{}] model mappings for all environments", name);
		var response = service.getControllerMappings(name);
		log.debug("Env -> Mappings: {}", response);
		return response;
	}

	@Operation(summary = "Get mapping for controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Mapping for Controller @ Environment"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }),
			@ApiResponse(responseCode = "500", description = "Server error", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }), })
	@GetMapping("/{name}/{env}/mappings")
	public Set<MappingDesc> getMappings(@PathVariable String name, @PathVariable String env) {
		log.debug("Get [{}] model mappings for [{}] environment", name, env);
		var response = service.getMappings(name, env);
		log.debug("Mappings: {}", response);
		return response;
	}

	@Operation(summary = "Delete controlle mappings from all environments")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Controller mappings was deleted at all environments") })
	@DeleteMapping("/{name}/mappings")
	public ResponseEntity<Void> removeAllMappings(@PathVariable String name) {
		log.debug("Remove [{}] model mappings from all environments", name);
		service.removeAllMappings(name);
		return NO_CONTENT;
	}

	@Operation(summary = "Subscribe controller mapping at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Load values for subscribed mapping"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }) })
	@PostMapping("/{name}/{env}/subscribe")
	public ResponseEntity<Void> subscribe(@PathVariable String name, @PathVariable String env, @RequestBody SubscribeRequest request) {
		log.debug("Subscribe [{}] model at [{}] environment. Request: {}", name, env, request);
		var keys = request.getKeys();
		service.subscribe(name, env, keys);
		return NO_CONTENT;
	}

	@Operation(summary = "Unsubscribe controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Load values for subscribed mapping"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }) })
	@PostMapping("/{name}/{env}/unsubscribe")
	public ResponseEntity<Void> unsubscribe(@PathVariable String name, @PathVariable String env) {
		log.debug("Unsubscribe [{}] model at [{}] environment", name, env);
		service.unsubscribe(name, env);
		return NO_CONTENT;
	}

	@Operation(summary = "Get mapping values for controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Load values for subscribed mapping"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }) })
	@GetMapping("/{name}/{env}/values")
        public Map<String, TagValue> getValues(@PathVariable String name, @PathVariable String env) {
                log.debug("Get values [{}] model at [{}] environment", name, env);
                var response = service.getValues(name, env);
                log.debug("Values: {}", response);
                return response;
        }

	@Operation(summary = "Get ALL mapping values for controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Load values for all mapping items"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }) })
	@Deprecated
	@PostMapping("/{name}/{env}/values/all")
        public Map<String, TagValue> getKeysValues(@PathVariable String name, @PathVariable String env, @RequestBody Set<String> keys) {
                log.debug("Get {} values for [{}] model at [{}] environment", keys, name, env);
                var response = service.getKeysValues(name, env, keys);
                log.debug("Values: {}", response);
                return response;
	}

	@Operation(summary = "Set mapping values for controller at environment")
	@ApiResponses(value = { @ApiResponse(responseCode = "204", description = "Mapping has been deleted"),
			@ApiResponse(responseCode = "404", description = "Mapping for Controller @ Environment is not found", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = APIError.class)) }) })
	@PostMapping("/{name}/{env}/values")
	public ResponseEntity<Void> setValues(@PathVariable String name, @PathVariable String env,
			@RequestBody ValueUpdateRequest request) {
		log.debug("Set values for [{}] model at [{}] environment: {}", name, env, request);
		service.setValues(name, env, request);
		log.debug(PROCESSED);
		return NO_CONTENT;
	}
}
