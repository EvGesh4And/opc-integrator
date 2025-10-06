package ru.datana.integration.opc.service;

import static com.google.common.collect.Sets.difference;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static ru.datana.integration.opc.util.LogConsts.IN_1;
import static ru.datana.integration.opc.util.LogConsts.IN_2;
import static ru.datana.integration.opc.util.LogConsts.IN_3;
import static ru.datana.integration.opc.util.LogConsts.OUT_0;
import static ru.datana.integration.opc.util.LogConsts.OUT_1;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.component.OpcClient;
import ru.datana.integration.opc.component.ValueManager;
import ru.datana.integration.opc.dto.Mapping;
import ru.datana.integration.opc.dto.TagValue;
import ru.datana.integration.opc.exception.InternalErrorException;
import ru.datana.integration.opc.exception.ResourceNotFoundException;
import ru.datana.integration.opc.exception.ValidException;
import ru.datana.integration.opc.request.MappingDesc;
import ru.datana.integration.opc.request.ModelMeta;
import ru.datana.integration.opc.request.ValueUpdateRequest;
import ru.datana.integration.opc.exception.ServiceException;

/**
 * @see https://github.com/eclipse/milo/wiki/Client
 * @see https://opcconnect.opcfoundation.org/2017/02/analyzing-opc-ua-communications-with-wireshark/
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpcService {
	private static final String MODEL = "MODEL";
	private static final String ENV = "ENVIRONMENT";
	private final Table<String, String, ModelMeta> models = HashBasedTable.create();
        private final OpcClient client;
        private final ValueManager valueManager;

	public Set<MappingDesc> getMappings(String name, String env) {
		log.debug(IN_2, name, env);
		var mappings = getModel(name, env).getMappings();
		log.debug(OUT_1, mappings);
		return mappings;
	}

	public Map<String, Set<MappingDesc>> getControllerMappings(String name) {
		log.debug(IN_1, name);
		var res = models.row(name).entrySet().stream()
				.collect(toMap(Entry::getKey, e -> e.getValue().getMappings()));
		log.debug(OUT_1, res);
		return res;
	}

	public void replaceMappings(String name, String env, Set<MappingDesc> mappings) {
		log.debug(IN_3, name, env, mappings);
		if (!client.isEnvironmentDeclared(env)) {
			throw new ResourceNotFoundException(ENV, env);
		}
		var model = models.get(name, env);
		if (model != null) {
			if (!client.unsubscribe(name, env)) {
				log.error("Failed to unsubscribe {}@{}", name, env);
				log.debug(OUT_0);
				return;
			}
			var modelMappings = model.getMappings();
			if (modelMappings != null) {
				modelMappings.clear();
				modelMappings.addAll(mappings);
			}
		} else {
			model = ModelMeta.builder().name(name).mappings(mappings).build();
		}
                models.put(name, env, model);
                valueManager.registerMappings(name, env, mappings);
                log.debug(OUT_0);
        }

	public void subscribe(String name, String env, Set<String> keys) {
		log.debug(IN_3, name, env, keys);
		var subscriptionMappings = getModel(name, env).getMappings();
		client.subscribe(name, env, mappingsByKeys(keys, subscriptionMappings));
		log.debug(OUT_0);
	}

	public void unsubscribe(String name, String env) {
		log.debug(IN_2, name, env);
		if (getModel(name, env) != null && !client.unsubscribe(name, env)) {
			log.error("Failed to unsubscribe {}@{}", name, env);
			throw new InternalErrorException("Failure to unsubscribe for [%s@%s]".formatted(name, env));
		}
		log.debug(OUT_0);
	}

        public Map<String, TagValue> getValues(String name, String env) {
                log.debug(IN_2, name, env);
                var res = new HashMap<String, TagValue>();
                var entries = client.getValues(name, env).entrySet();
                var descriptions = getModel(name, env).getMappings();
                for (var entry : entries) {
                        var address = entry.getKey();
                        var mapping = descriptions.stream().map(Mapping::create).filter(m -> address.equals(m.buildAddress()))
                                        .findAny().orElse(null);
                        res.put(mapping.getKey(), entry.getValue());
                }
                log.debug(OUT_1, res);
                return res;
        }

        public Map<String, TagValue> getKeysValues(String name, String env, Set<String> keys) {
                log.debug(IN_2, name, env);
                var subscriptionMappings = getModel(name, env).getMappings();
                var res = client.getAllValues(name, env, mappingsByKeys(keys, subscriptionMappings));
                log.debug(OUT_1, res);
                return res;
	}

	private void setValues(Map<String, Float> valueMappings, String name, String env, boolean isOptional) {
		var res = new HashMap<MappingDesc, Float>();
		var mappings = getModel(name, env).getMappings();
		var unknownMappings = new HashSet<String>();
		
		valueMappings.entrySet().forEach(e -> {
			var key = e.getKey();
			mappings.stream().filter(m -> key.equals(m.getKey())).findAny()
					.ifPresentOrElse(mapping -> res.put(mapping, e.getValue()), () -> unknownMappings.add(key));
		});
		
		if (unknownMappings.isEmpty() || isOptional) {
			// Если нет неизвестных маппингов или это optional параметры, отправляем значения
			if (!res.isEmpty()) {
				client.setValues(name, env, res);
			}
			
			// Если есть неизвестные маппинги и это optional параметры, просто логируем их
			if (!unknownMappings.isEmpty()) {
				log.error("Unknown mappings: {}", unknownMappings);
			}
		} else {
			// Для required параметров выбрасываем исключение при наличии неизвестных маппингов
			log.error("Unknown mappings: {}", unknownMappings);
			throw ValidException.builder().message("SetValues unknown mappings").code(404)
					.fieldErrors(unknownMappings.stream().collect(toMap(identity(), s -> "MAPPING unknown"))).build();
		}
	}

        public void removeAllMappings(String name) {
                log.debug(IN_1, name);
                var envs = new HashSet<String>();
                models.row(name).entrySet().stream().forEach(e -> {
                        var env = e.getKey();
                        client.unsubscribe(name, env);
                        envs.add(env);
                });;
                envs.forEach(e -> {
                        models.remove(name, e);
                        valueManager.remove(name, e);
                });
                log.debug(OUT_0);
        }

	public void browse(String env) {
		client.browse(env);
	}

	private @NotNull ModelMeta getModel(String name, String env) {
		if (!client.isEnvironmentDeclared(env)) {
			throw new ResourceNotFoundException(ENV, env);
		}
		return ofNullable(models.get(name, env))
				.orElseThrow(() -> new ResourceNotFoundException(MODEL, "%s@%s".formatted(name, env)));
	}

	public void setValues(String process, String env, ValueUpdateRequest request) {
		try {
			if (request.getRequired() != null) {
				setValues(request.getRequired(), process, env, false);
			}
			if (request.getOptional() != null) {
				setValues(request.getOptional(), process, env, true);
			}
		} catch (Exception e) {
			throw new ServiceException("Error setting values", e);
		}
	}

	private Set<MappingDesc> mappingsByKeys(Set<String> keys, Set<MappingDesc> subscriptionMappings) {
		var mappings = subscriptionMappings.stream().filter(m -> keys.contains(m.getKey()))
				.collect(toSet());
		if (keys.size() > mappings.size()) {
			var foundKeys = mappings.stream().map(MappingDesc::getKey).collect(toSet());
			var errors = difference(keys, foundKeys).stream().collect(toMap(identity(), k -> "Unknown mapping"));
			throw new ValidException(400, "Unknown mappings found", errors);
		}
		return mappings;
	}
}
