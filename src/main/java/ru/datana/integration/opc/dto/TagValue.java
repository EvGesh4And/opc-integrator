package ru.datana.integration.opc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagValue {
    Double value;
    String sourceTimestamp;
    String serverTimestamp;
    String status;
}
