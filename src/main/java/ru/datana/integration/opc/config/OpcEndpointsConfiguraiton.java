package ru.datana.integration.opc.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties(prefix = "opc")
@Data
public class OpcEndpointsConfiguraiton {
	@Data
	public static class OpcEndpoint {
		@Data
		public static class Namespace {
			public enum AddressType {
				NUMERIC,
				STRING,
				UUID,
				OPAQUE
			}
			private int id;
			private AddressType addressType;
		}
		String name;
		String url;
		String type;
		String selector;
		String user;
		String password;
		Set<Namespace> namespaces;
	}

	private Set<OpcEndpoint> providers;
}
