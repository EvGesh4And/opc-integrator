package ru.datana.integration.opc.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SetValuesRequest {
    private Map<String, Float> required;
    private Map<String, Float> optional;
}