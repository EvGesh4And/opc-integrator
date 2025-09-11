package ru.datana.integration.opc.dto;

import static lombok.AccessLevel.PRIVATE;
import static org.eclipse.milo.opcua.stack.core.types.builtin.ByteString.of;

import java.util.Set;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import ru.datana.integration.opc.exception.SubscriptionException;
import ru.datana.integration.opc.request.MappingDesc;

@Value
@Builder
@AllArgsConstructor(access = PRIVATE)
public class Mapping {
	public static Mapping create(MappingDesc desc) {
		return Mapping.builder().key(desc.getKey()).namespaceIndex(desc.getNamespaceIndex()).nodeId(buildNodeId(desc))
				.build();
	}

	private static NodeId buildNodeId(MappingDesc desc) {
		int nsIdx = desc.getNamespaceIndex();
		if (desc.getNodeId() != null) {
			return new NodeId(nsIdx, desc.getNodeId());
		} else if (desc.getTag() != null) {
			return new NodeId(nsIdx, "%s.%s".formatted(desc.getTag(), desc.getAttribute()));
		} else if (desc.getUuid() != null) {
			return new NodeId(nsIdx, desc.getUuid());
		} else if (desc.getBytes() != null) {
			return new NodeId(nsIdx, of(desc.getBytes().getBytes()));
		} else {
			throw new SubscriptionException(Set.of(new SubscriptionException.ErrorDescription("MAPPING", desc.getKey(), "Validation error: all {nodeId, tag, uuid, bytes} are empty")));
		}
	}

	@NotBlank
	String key;

	int namespaceIndex;
	NodeId nodeId;

	public String buildAddress() {
		return nodeId.getIdentifier().toString();
	}
}
