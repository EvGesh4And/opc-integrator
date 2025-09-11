package ru.datana.integration.opc.controller;

import static ru.datana.integration.opc.component.OpcClient.IOT_HUB;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.service.OpcService;

@RestController
@RequestMapping("test")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class TestController {
	private final OpcService service;

	@GetMapping("browse")
	public void browse() {
		log.debug("Browse");
		service.browse(IOT_HUB);
	}
}
