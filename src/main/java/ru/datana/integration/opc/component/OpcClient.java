package ru.datana.integration.opc.component;

import static java.lang.Double.parseDouble;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.eclipse.milo.opcua.stack.client.DiscoveryClient.getEndpoints;
import static org.eclipse.milo.opcua.stack.core.AttributeId.Value;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.lookup;
import static org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText.english;
import static org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName.NULL_VALUE;
import static org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode.GOOD;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static ru.datana.integration.opc.util.LogConsts.IN_2;
import static ru.datana.integration.opc.util.LogConsts.IN_3;
import static ru.datana.integration.opc.util.LogConsts.OUT_0;
import static ru.datana.integration.opc.util.LogConsts.OUT_1;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription.StatusListener;
import org.eclipse.milo.opcua.stack.client.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.datana.integration.opc.dto.TagValue;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.config.OpcEndpointsConfiguraiton;
import ru.datana.integration.opc.config.OpcEndpointsConfiguraiton.OpcEndpoint;
import ru.datana.integration.opc.dto.Mapping;
import ru.datana.integration.opc.exception.InitializationException;
import ru.datana.integration.opc.exception.InternalErrorException;
import ru.datana.integration.opc.exception.ResourceNotFoundException;
import ru.datana.integration.opc.exception.SubscriptionException;
import ru.datana.integration.opc.listener.OpcSubscriptionListener;
import ru.datana.integration.opc.request.MappingDesc;

/**
 * @see https://documentation.unified-automation.com/uasdkcpp/1.7.0/html/L1OpcUaFundamentals.html
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpcClient implements StatusListener {
	private static final String IOTHUB = "iothub";
	private static final String SIMULATOR = "simulator";
	private static final long OPC_TIMEOUT_MS = 1000L;

	public static record LateValidationError(String address, String message) {
		public LateValidationError {
			requireNonNull(address);
			requireNonNull(message);
		}
	}

	private static final int CONNECTION_DISCONNECT_IN_SECONDS = 30;

	public static final String IOT_HUB = "IOT";
	private static final String ENV = "ENVIRONMENT";
	private static final String APP_NAME = "datana-opc-client";
	private static final String APP_URL = "urn:datana:integration:opc:client";

	private final ObjectMapper mapper;
	private final KeyStoreLoader keyStoreLoader;

	private final OpcEndpointsConfiguraiton opcConfig;
        private final ConcurrentMap<String, ConcurrentMap<String, ManagedSubscription>> subscriptions = new ConcurrentHashMap<>();
        private final ValueManager valueManager;

        private DefaultClientCertificateValidator certificateValidator;
        private final ConcurrentMap<String, OpcUaClient> clients = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, ConcurrentMap<Integer, NamespaceBinding>> namespaceCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, OpcEndpoint> failedEndpoints = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, ReentrantLock> envLocks = new ConcurrentHashMap<>();

	@Value("${subscriptionIntervalInMs:100}")
	private int subscriptionIntervalInMs;
	@Value("${envOpcConfig:}")
	private String envOpcConfig;

	@PostConstruct
	public void init() throws Exception {
		var securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "client", "security");
		createDirectories(securityTempDir);
		if (!exists(securityTempDir)) {
			throw new InitializationException("unable to create security dir: " + securityTempDir);
		}

		var pkiDir = securityTempDir.resolve("pki").toFile();
		log.info("security dir: {}", securityTempDir.toAbsolutePath());
		log.info("security pki dir: {}", pkiDir.getAbsolutePath());

		keyStoreLoader.load(securityTempDir);
		certificateValidator = new DefaultClientCertificateValidator(new DefaultTrustListManager(pkiDir));

		var providers = opcConfig.getProviders();
		if (!isBlank(envOpcConfig)) {
			log.warn("Loading environment congigurations from ENV");
			try {
				providers = mapper.readValue(envOpcConfig, new TypeReference<Set<OpcEndpoint>>() {
				});
				log.info("Receiving [{}] providers from ENV", providers.size());
			} catch (RuntimeException | JsonProcessingException e) {
				log.error("Failed to load environment configurations from ENV value: [{}]", envOpcConfig);
			}
		} else {
			log.warn("Loading environment congigurations from code");
		}

		providers.stream().filter(p -> p.getType().equals(IOTHUB)).forEach(this::connectIoTHub);
		providers.stream().filter(p -> p.getType().equals(SIMULATOR)).forEach(this::connectSimulator);
		connectAll();
	}

	@Scheduled(cron = "0 0/1 * * * *")
	public void checkAvailability() {
		log.debug("Checking ENV availabilities");
		opcConfig.getProviders().stream().forEach(this::checkAvailability);
		failedEndpoints.values().stream().forEach(this::connectEndpoint);
	}

        public void subscribe(String name, String env, Set<MappingDesc> descriptions) {
                log.debug(IN_3, name, env, descriptions);
                var lock = lockFor(env);
                lock.lock();
                try {
                        var envSubscriptions = subscriptions.computeIfAbsent(env, __ -> new ConcurrentHashMap<>());
                        ofNullable(envSubscriptions.get(name)).ifPresent(s -> unsubscribe(env, name));
                        try {
                                var client = clients.get(env);
                                if (client == null) {
                                        var endpoint = ofNullable(failedEndpoints.get(env))
                                                        .orElseThrow(() -> new ResourceNotFoundException(ENV, env));
                                        if (!connectEndpoint(endpoint)) {
                                                        throw new InternalErrorException(
                                                                        "Failure to connect to [%s] environment".formatted(env));
                                        } else {
                                                client = clients.get(env);
                                        }
                                }

                                var subscription = ManagedSubscription.createAsync(client, subscriptionIntervalInMs)
                                                .get(OPC_TIMEOUT_MS, MILLISECONDS);
                                var clientRef = client;
                                var mappings = descriptions.stream().map(desc -> toMapping(env, clientRef, desc))
                                                .collect(toList());
                                mappings.forEach(mapping -> log.info("Subscribing [{}@{}] tag [{}] with path [{}]", name, env,
                                                mapping.getKey(), mapping.buildAddress()));
                                var errors = mappings.stream().map(m -> addItem(m, subscription))
                                                .filter(Objects::nonNull)
                                                .map(ed -> new SubscriptionException.ErrorDescription(ed.message, "MAPPING",
                                                                ed.address()))
                                                .collect(toSet());
                                if (!errors.isEmpty()) {
                                        throw new SubscriptionException(errors);
                                }
                                subscription.addChangeListener(new OpcSubscriptionListener(valueManager, name, env));
                                subscription.addStatusListener(this);
                                envSubscriptions.put(name, subscription);
                                getAllValues(name, env, descriptions);
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                var message = "Failure to create [%s] subscription at [%s] environment".formatted(name, env);
                                log.error(message, e);
                                throw new InternalErrorException(message);
                        }
                } finally {
                        lock.unlock();
                }
                log.debug(OUT_0);
        }

        public boolean unsubscribe(String name, String env) {
                log.debug(IN_2, name, env);
                var lock = lockFor(env);
                lock.lock();
                try {
                        var envSubscriptions = subscriptions.get(env);
                        if (envSubscriptions == null) {
                                log.warn("Unknown subscription: {}@{}", name, env);
                                log.debug(OUT_1, true);
                                return true;
                        }
                        var subscription = envSubscriptions.remove(name);
                        if (subscription != null) {
                                try {
                                        subscription.deleteAsync().get(OPC_TIMEOUT_MS, MILLISECONDS);
                                        valueManager.remove(name, env);
                                        log.debug(OUT_1, true);
                                        return true;
                                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                        log.error("Failure to delete [%s] subscription at [%s] environment".formatted(name, env),
                                                        e);
                                        envSubscriptions.put(name, subscription);
                                }
                        } else {
                                log.warn("Unknown subscription: {}@{}", name, env);
                                log.debug(OUT_1, true);
                                return true;
                        }
                        log.debug(OUT_1, false);
                        return false;
                } finally {
                        lock.unlock();
                }
        }

        public Map<String, TagValue> getValues(String name, String env) {
                log.debug(IN_2, name, env);
                var res = valueManager.getValues(name, env);
                log.debug(OUT_1, res);
                return res;
        }

        public Map<String, TagValue> getAllValues(String name, String env, Set<MappingDesc> descriptions) {
                log.debug(IN_3, name, env, descriptions);
                var lock = lockFor(env);
                lock.lock();
                try {
                        var values = new HashMap<String, TagValue>();
                        var client = getClient(env);
                        // Do not treat Bad status as missing mapping; return it with status="Bad"
                        var mappings = descriptions.stream().map(desc -> toMapping(env, client, desc)).collect(toSet());
                        List<ReadValueId> readValueIds = new ArrayList<>();
                        mappings.forEach(m -> readValueIds.add(ReadValueId.builder().nodeId(m.getNodeId()).attributeId(Value.uid())
                                        .indexRange(null).dataEncoding(NULL_VALUE).build()));

                        try {
                                var results = client.read(0.0, TimestampsToReturn.Both, readValueIds)
                                                .get(OPC_TIMEOUT_MS, MILLISECONDS).getResults();
                                int i = 0;
                                for (var it = mappings.iterator(); it.hasNext();) {
                                        var mapping = it.next();
                                        var key = mapping.getKey();
                                        var dv = results[i];
                                        var statusCode = dv.getStatusCode();
                                        var tv = toTagValue(dv);
                                        values.put(key, tv);
                                        if (statusCode.isGood()) {
                                                valueManager.setValue(name, env,
                                                                mapping.getNodeId().getIdentifier().toString(), tv);
                                        } else if (!statusCode.isUncertain()) {
                                                // Keep returning the value with status="Bad" but log it for observability
                                                log.warn("Tag [{}] returned non-good status: {}", key, statusCode);
                                        }
                                        i++;
                                }
                                log.debug(OUT_1, values);
                                return values;
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                var message = e.getMessage();
                                log.error("Failure to get all values for [{}@{}]: {}", name, env, message);
                                throw new InternalErrorException(
                                                "Failure to load data for [%s@%s]: %s".formatted(name, env, message));
                        }
                } finally {
                        lock.unlock();
                }
        }

        public void setValues(String name, String env, Map<MappingDesc, Float> values) {
                log.debug(IN_3, name, env, values);
                var lock = lockFor(env);
                lock.lock();
                try {
                        var client = getClient(env);
                        var res = values.entrySet().stream()
                                        // TODO: remove filter when all tags will support "AllowNulls" prop
                                        .filter(e -> e.getValue() != null)
                                        .map(e -> buildWriteValue(env, client, e)).toList();
                        try {
                                var response = client.write(res).get(OPC_TIMEOUT_MS, MILLISECONDS);
                                var statusCodes = response.getResults();
                                for (int i = 0; i < statusCodes.length; i++) {
                                        var wv = res.get(i);
                                        var code = statusCodes[i];
                                        if (code.isGood()) {
                                                valueManager.setValue(name, env, wv.getNodeId().getIdentifier().toString(),
                                                                TagValue.builder()
                                                                                .value(Double.parseDouble(wv.getValue().getValue().getValue().toString()))
                                                                                .status("Good")
                                                                                .build());
                                                log.debug("success: [{}] => [{}]", wv.getNodeId().getIdentifier(), wv.getValue());
                                        } else {
                                                log.error("failure: [{}] =!> [{}]. code: [{}]", wv.getNodeId().getIdentifier(), wv.getValue(),
                                                                Arrays.toString(lookup(code.getValue()).get()));
                                        }
                                }
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                var message = e.getMessage();
                                log.error("Failure to set values for [{}@{}]: {}", name, env, message);
                                throw new InternalErrorException(
                                                "Failure to set values for [%s@%s]: %s".formatted(name, env, message));
                        }
                } finally {
                        lock.unlock();
                }
                log.debug(OUT_0);
        }

	public boolean isEnvironmentDeclared(String env) {
		return (null != clients.get(env)) || (null != failedEndpoints.get(env));
	}

	public void browse(String env) {
		// start browsing at root folder
		browseNode("", getClient(env), Identifiers.RootFolder);
	}

        private void checkAvailability(OpcEndpoint cfg) {
                String env = cfg.getName();
                var lock = lockFor(env);
                lock.lock();
                try {
                        Instant lastUpdated = valueManager.getUpdateTS(env);
                        var envSubscriptions = subscriptions.get(env);
                        if (envSubscriptions != null && !envSubscriptions.isEmpty()) {
                                if (between(lastUpdated, now()).getSeconds() > CONNECTION_DISCONNECT_IN_SECONDS) {
                                        log.warn("Reconnecting to [{}], last data were received at {}", cfg.getName(),
                                                        lastUpdated.toString());
                                        boolean isConnected = switch (cfg.getType()) {
                                        case IOTHUB -> connectIoTHub(cfg);
                                        case SIMULATOR -> connectSimulator(cfg);
                                        default -> throw new IllegalArgumentException(
                                                        "Unesupported endpoingt type: " + cfg.getType());
                                        };
                                        if (isConnected) {
                                                log.warn("Recreating [{}] subscriptions at [{}]", envSubscriptions.size(), env);
                                                envSubscriptions.entrySet().forEach(entry -> recreateSubscription(env, entry));
                                        } else {
                                                log.warn("Connection failure to [{}] environment", env);
                                        }
                                } else {
                                        log.debug("Env [{}] is assumed healthy: last update time at {}", env, lastUpdated);
                                }
                        } else {
                                log.debug("Empty subscription set at {}", env);
                        }
                } finally {
                        lock.unlock();
                }
        }

        private void recreateSubscription(String env, Map.Entry<String, ManagedSubscription> e) {
                String name = e.getKey();
                var subscription = e.getValue();
                log.warn("FAKE IMPLEMENTATION: recreating {} with [id: {}] for env [{}]", name,
                                subscription.getSubscription().getSubscriptionId(), env);
        }

        private boolean connectIoTHub(OpcEndpoint config) {
                var name = config.getName();
                log.debug("Connecting IoTHub: [{}]", name);
                var lock = lockFor(name);
                lock.lock();
                try {
                        var provider = new UsernameProvider(config.getUser(), config.getPassword());
                        var url = config.getUrl();
                        try {
                                logEndPoints(url);
                        } catch (InternalErrorException e) {
                                log.error("Failed to load [{}] IoTHub [url: {}]. Error: {}", name, url, e.getMessage());
                                return false;
                        }
                        try {
                                var client = OpcUaClient.create(url,
                                                endpoints -> endpoints.stream()
                                                                .filter(e -> e.getEndpointUrl().startsWith(config.getSelector()))
                                                                .findAny().map(d -> d.toBuilder().endpointUrl(url).build()),
                                                b -> b.setApplicationName(english(APP_NAME)).setApplicationUri(APP_URL)
                                                                .setKeyPair(keyStoreLoader.getClientKeyPair())
                                                                .setCertificate(keyStoreLoader.getClientCertificate())
                                                                .setCertificateChain(keyStoreLoader.getClientCertificateChain())
                                                                .setCertificateValidator(certificateValidator)
                                                                .setIdentityProvider(provider)
                                                                .setRequestTimeout(uint(5000)).build());
                                if (connect(client)) {
                                        clients.put(name, client);
                                        namespaceCache.remove(name);
                                        failedEndpoints.remove(name);
                                        log.debug("IotHub client [{}] is OK", name);
                                        return true;
                                }
                                log.error("Failed to connect IoTHub client [{}]", name);
                                failedEndpoints.put(config.getName(), config);
                                return false;
                        } catch (UaException | InternalErrorException e) {
                                log.error("Client creation error for " + name, e);
                                failedEndpoints.put(config.getName(), config);
                                return false;
                        }
                } finally {
                        lock.unlock();
                }
        }

        private boolean connectSimulator(OpcEndpoint config) {
                var name = config.getName();
                log.debug("Connecting Simulator: [{}]", name);
                var lock = lockFor(name);
                lock.lock();
                try {
                        var provider = new AnonymousProvider();
                        var url = config.getUrl();
                        try {
                                logEndPoints(url);
                        } catch (InternalErrorException e) {
                                log.error("Failed to load [{}] simulator [url: {}]. Error: {}", name, url, e.getMessage());
                                failedEndpoints.put(config.getName(), config);
                                return false;
                        }
                        try {
                                var client = OpcUaClient.create(url,
                                                endpoints -> ofNullable(endpoints.get(0).toBuilder().endpointUrl(url).build()),
                                                b -> b.setApplicationName(english(APP_NAME)).setApplicationUri(APP_URL)
                                                                .setKeyPair(keyStoreLoader.getClientKeyPair())
                                                                .setCertificate(keyStoreLoader.getClientCertificate())
                                                                .setCertificateChain(keyStoreLoader.getClientCertificateChain())
                                                                .setCertificateValidator(certificateValidator)
                                                                .setIdentityProvider(provider)
                                                                .setRequestTimeout(uint(5000)).build());
                                if (connect(client)) {
                                        clients.put(name, client);
                                        namespaceCache.remove(name);
                                        failedEndpoints.remove(name);
                                        log.debug("Simulator client [{}] is OK", name);
                                        return true;
                                }
                                log.error("Failed to connect simulator client [{}]", name);
                                failedEndpoints.put(config.getName(), config);
                                return false;
                        } catch (UaException | InternalErrorException e) {
                                log.error("Client creation error for " + name, e);
                                return false;
                        }
                } finally {
                        lock.unlock();
                }
        }

	private void logEndPoints(String url) {
		try {
			getEndpoints(url).get(OPC_TIMEOUT_MS, MILLISECONDS)
					.forEach(desc -> log.debug("endpoint-url: {}", desc.getEndpointUrl()));
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			var message = e.getMessage();
			log.error("Failure to log endpoints for [{}]: {}", url, message);
			throw new InternalErrorException("Failure to log endpoints for [%s]: %s".formatted(url, message));
		}
	}

	private void connectAll() {
		clients.values().forEach(this::connect);
	}

	private boolean connect(OpcUaClient client) {
		try {
			// synchronous connect
			client.connect().get(OPC_TIMEOUT_MS, MILLISECONDS);
			return true;
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			log.error("Error [at {}]: {}", client.getConfig().getEndpoint().getEndpointUrl(), e.getMessage());
			return false;
		}
	}

        private void browseNode(String indent, OpcUaClient client, NodeId browseRoot) {
                try {
                        var nodes = client.getAddressSpace().browseNodes(browseRoot);

			int i = 0;
			for (UaNode node : nodes) {
				log.info("{} Node={}", indent, node.getBrowseName().getName());
				i = i + 1;
				if (i == 5) {
					log.info("... from {} nodes", nodes.size());
					break;
				} else {
					// recursively browse to children
					browseNode(indent + "  ", client, node.getNodeId());
				}
			}
		} catch (UaException e) {
			log.error("Browsing nodeId={} failed: {}", browseRoot, e.getMessage(), e);
		}
	}

        private OpcUaClient getClient(String env) {
                return ofNullable(clients.get(env)).orElseThrow(() -> new ResourceNotFoundException(ENV, env));
        }

	private ReentrantLock lockFor(String env) {
		return envLocks.computeIfAbsent(env, __ -> new ReentrantLock());
	}

	private Mapping toMapping(String env, OpcUaClient client, MappingDesc desc) {
		var resolvedIndex = resolveNamespaceIndex(env, desc.getNamespaceIndex(), client);
		return Mapping.create(desc, resolvedIndex);
	}

	private LateValidationError addItem(Mapping m, ManagedSubscription subscription) {
		var address = m.buildAddress();
		var id = "[%s => %s] ".formatted(m.getKey(), address);
		ManagedDataItem item;
		try {
			item = subscription.createDataItem(m.getNodeId());
		} catch (NumberFormatException | UaException e) {
			log.error("Exception [{}]. {}", e.getClass().getSimpleName(), e.getMessage());
			return new LateValidationError(address, id + "createDataItem failure");
		}
		if (!item.getStatusCode().isGood()) {
			return new LateValidationError(address,
					id + "createDataItem failure with status code: " + item.getStatusCode());
		} else {
			return null;
		}
	}

	private WriteValue buildWriteValue(String env, OpcUaClient client, Entry<MappingDesc, Float> e) {
		var mapping = toMapping(env, client, e.getKey());
		var nodeId = mapping.getNodeId();
		if (e.getKey().getKey().equalsIgnoreCase("status")) {
			return new WriteValue(nodeId, Value.uid(), null, buildDataValue(e.getValue().intValue()));
		} else {
			return new WriteValue(nodeId, Value.uid(), null, buildDataValue(e.getValue()));
		}
	}

	private int resolveNamespaceIndex(String env, int requestedIndex, OpcUaClient client) {
		var cache = namespaceCache.computeIfAbsent(env, __ -> new ConcurrentHashMap<>());
		var table = client.getNamespaceTable();
		var binding = cache.compute(requestedIndex, (idx, existing) -> {
			if (existing == null || existing.uri() == null) {
				var uri = safeResolveUri(table, idx);
				if (uri != null) {
					var actual = safeResolveIndex(table, uri);
					if (actual >= 0) {
						return new NamespaceBinding(uri, actual);
					}
				}
				return new NamespaceBinding(null, idx);
			} else {
				var actual = safeResolveIndex(table, existing.uri());
				if (actual >= 0) {
					return new NamespaceBinding(existing.uri(), actual);
				}
				var uri = safeResolveUri(table, idx);
				if (uri != null) {
					actual = safeResolveIndex(table, uri);
					if (actual >= 0) {
						return new NamespaceBinding(uri, actual);
					}
				}
				return new NamespaceBinding(existing.uri(), idx);
			}
		});
		return binding.index();
	}

	private String safeResolveUri(NamespaceTable table, int index) {
		try {
			return table.getUri(index);
		} catch (Exception e) {
			log.warn("Failed to resolve namespace URI for index [{}]: {}", index, e.getMessage());
			return null;
		}
	}

	private int safeResolveIndex(NamespaceTable table, String uri) {
		if (uri == null) {
			return -1;
		}
		try {
			var resolved = table.getIndex(uri);
			return resolved != null ? resolved.intValue() : -1;
		} catch (Exception e) {
			log.warn("Failed to resolve namespace index for URI [{}]: {}", uri, e.getMessage());
			return -1;
		}
	}

	private static record NamespaceBinding(String uri, int index) {
	}


    private DataValue buildDataValue(Object value) {
        var variant = ofNullable(value).map(Variant::new).orElse(Variant.NULL_VALUE);
        var now = DateTime.now();
        var zero = UShort.valueOf(0);
        return DataValue.newValue().setValue(variant).setSourceTime(now).setSourcePicoseconds(zero).setServerTime(now)
                        .setServerPicoseconds(zero).setStatus(GOOD).build();
    }

    private static TagValue toTagValue(DataValue dv) {
        var variant = dv.getValue();
        Double val = null;
        if (variant != null && variant.getValue() != null) {
                Object obj = variant.getValue();
                if (obj instanceof Double d) {
                        val = d;
                } else if (obj instanceof Integer i) {
                        val = i.doubleValue();
                } else if (obj instanceof Float f) {
                        val = f.doubleValue();
                } else {
                        try {
                                val = Double.parseDouble(obj.toString());
                        } catch (Exception e) {
                                val = null;
                        }
                }
        }
        return TagValue.builder()
                        .value(val)
                        .sourceTimestamp(ts(dv.getSourceTime()))
                        .serverTimestamp(ts(dv.getServerTime()))
                        .status(status(dv.getStatusCode()))
                        .build();
    }

    private static String ts(DateTime dt) {
        return dt == null ? null : dt.getJavaInstant().toString();
    }

    private static String status(StatusCode code) {
        if (code == null) {
                return null;
        }
        if (code.isGood()) {
                return "Good";
        } else if (code.isUncertain()) {
                return "Uncertain";
        } else {
                return "Bad";
        }
    }

	@Override
	public void onNotificationDataLost(ManagedSubscription subscription) {
		StatusListener.super.onNotificationDataLost(subscription);
		log.warn("Subscription [id: {}] notification data lost", subscription.getSubscription().getSubscriptionId());
	}

	@Override
	public void onSubscriptionStatusChanged(ManagedSubscription subscription, StatusCode statusCode) {
		log.warn("Subscription [id: {}] status is [{}]", subscription.getSubscription().getSubscriptionId(),
				statusCode.getValue());
	}

	@Override
	public void onSubscriptionTransferFailed(ManagedSubscription subscription, StatusCode statusCode) {
		log.warn("Subscription [id: {}] transfer failed [{}]", subscription.getSubscription().getSubscriptionId(),
				statusCode.getValue());
		resubscribe(subscription);
	}

	@Override
	public void onSubscriptionWatchdogTimerElapsed(ManagedSubscription subscription) {
		log.warn("Subscription [id: {}] watchdog timer elapsed", subscription.getSubscription().getSubscriptionId());
	}

        private boolean resubscribe(ManagedSubscription failed) {
                for (var entry : subscriptions.entrySet()) {
                        var env = entry.getKey();
                        var lock = lockFor(env);
                        lock.lock();
                        try {
                                var envSubscriptions = entry.getValue();
                                for (var subEntry : envSubscriptions.entrySet()) {
                                        if (Objects.equals(subEntry.getValue(), failed)) {
                                                var name = subEntry.getKey();
                                                try {
                                                        var nodeIds = failed.getDataItems().stream().map(ManagedDataItem::getNodeId)
                                                                        .toList();
                                                        envSubscriptions.remove(name, failed);
                                                        failed.delete();

                                                        var created = ManagedSubscription
                                                                        .createAsync(failed.getClient(), subscriptionIntervalInMs)
                                                                        .get(1L, TimeUnit.SECONDS);
                                                        created.createDataItems(nodeIds);
                                                        created.addChangeListener(new OpcSubscriptionListener(valueManager, name, env));
                                                        created.addStatusListener(this);
                                                        envSubscriptions.put(name, created);
                                                } catch (InterruptedException | ExecutionException | TimeoutException
                                                                | UaException e) {
                                                        log.error("Exception [{}]. {}", e.getClass().getSimpleName(), e.getMessage());
                                                        e.printStackTrace();
                                                }
                                                return true;
                                        }
                                }
                        } finally {
                                lock.unlock();
                        }
                }
                return false;
        }

        private boolean connectEndpoint(OpcEndpoint endpoint) {
                var name = endpoint.getName();
                log.debug("Connect endpoint: [{}]", name);
                var lock = lockFor(name);
                lock.lock();
                try {
                        var type = endpoint.getType();
                        if ((type.equals(IOTHUB) && connectIoTHub(endpoint))
                                        || (type.equals(SIMULATOR) && connectSimulator(endpoint))) {
                                return connect(clients.get(name));
                        } else {
                                log.warn("Unsupported endpoint ({}) type: [{}]", endpoint.getName(), type);
                                return false;
                        }
                } finally {
                        lock.unlock();
                }
        }
}
