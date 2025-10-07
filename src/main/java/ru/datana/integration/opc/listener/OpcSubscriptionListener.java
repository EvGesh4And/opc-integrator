package ru.datana.integration.opc.listener;

import java.util.List;

import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription.ChangeListener;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription.StatusListener;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.datana.integration.opc.component.ValueManager;
import ru.datana.integration.opc.dto.TagValue;
import ru.datana.integration.opc.exception.DataProcessingException;

@RequiredArgsConstructor
@Slf4j
public class OpcSubscriptionListener implements ChangeListener, StatusListener {
	private final ValueManager mngr;
	private final String name;
	private final String env;

	@Override
	public void onDataReceived(List<ManagedDataItem> dataItems, List<DataValue> dataValues) {
                if (!dataItems.isEmpty()) {
                        log.debug("[{}] values received for {}", dataItems.size(), name);
                        var itemIt = dataItems.iterator();
                        for (var valueIt = dataValues.iterator(); valueIt.hasNext();) {
                                var nodeId = itemIt.next().getNodeId();
                                var id = nodeId.getIdentifier().toString();
                                var dv = valueIt.next();
                                var variant = dv.getValue();
                                var idType = variant.getDataType().map(ExpandedNodeId::getType);
                                if (idType.isEmpty()) {
                                        mngr.setValue(name, env, id, TagValue.builder()
                                                        .status(status(dv.getStatusCode()))
                                                        .sourceTimestamp(ts(dv.getSourceTime()))
                                                        .serverTimestamp(ts(dv.getServerTime()))
                                                        .build());
                } else if (!idType.map(IdType.Numeric::equals).orElse(false).booleanValue()) {
                        throw new DataProcessingException("[%s@%s] Unsupported value type [%s] for node id [%s]"
                                        .formatted(name, env, idType.orElse(null), id));
                } else {
                        var rawValue = variant.getValue();
                        Double res = null;
                        if (rawValue instanceof Number number) {
                                res = number.doubleValue();
                        } else if (rawValue instanceof Boolean bool) {
                                res = bool ? 1.0 : 0.0;
                        } else if (rawValue != null) {
                                try {
                                        res = Double.parseDouble(rawValue.toString());
                                } catch (NumberFormatException e) {
                                        log.warn("[{}@{}] Unable to parse value [{}] for node id [{}]", name, env,
                                                                        rawValue, id);
                                                }
                                        }
                                        if (res == null) {
                                                log.debug("[{}@{}] Skip value [{}] for node id [{}] due to unsupported type {}", name,
                                                                env, rawValue, id, rawValue == null ? null : rawValue.getClass());
                                                continue;
                                        }
                                        mngr.setValue(name, env, id,
                                                        TagValue.builder().value(res)
                                                                        .status(status(dv.getStatusCode()))
                                                                        .sourceTimestamp(ts(dv.getSourceTime()))
                                                                        .serverTimestamp(ts(dv.getServerTime()))
                                                                        .build());
                                }
                        }
                } else {
                        log.debug("Empty data received for {}@{}", name, env);
                }
	}

	@Override
	public void onKeepAliveReceived() {
		log.debug("Keep alive recieved for {}@{}", name, env);
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
	}

	@Override
        public void onSubscriptionWatchdogTimerElapsed(ManagedSubscription subscription) {
                log.warn("Subscription [id: {}] watchdog timer elapsed", subscription.getSubscription().getSubscriptionId());
        }

        private static String ts(org.eclipse.milo.opcua.stack.core.types.builtin.DateTime dt) {
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
}
