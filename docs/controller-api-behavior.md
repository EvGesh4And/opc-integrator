# Controller API Integration Behaviour

## Previous behaviour

* `ValueManager` only cached the latest OPC values per node and environment. No information about the originating mapping key was stored, so updates never triggered any controller REST calls automatically.【F:src/main/java/ru/datana/integration/opc/component/ValueManager.java†L1-L59】
* `OpcService` registered or replaced mappings in the in-memory model table and only interacted with the OPC client (subscribe, unsubscribe, read/write). There was no coupling to the controller API layer.【F:src/main/java/ru/datana/integration/opc/service/OpcService.java†L51-L146】

## Current behaviour

### Mapping creation / replacement

* When `OpcService.replaceMappings` is called, it still updates the in-memory model and keeps the OPC client subscription logic. In addition, it now asks `ValueManager` to register every mapping descriptor, so each OPC node identifier is associated with the mapping key that came from the REST payload.【F:src/main/java/ru/datana/integration/opc/service/OpcService.java†L78-L116】【F:src/main/java/ru/datana/integration/opc/component/ValueManager.java†L28-L43】

### Processing value updates

* `ValueManager.setValue` is invoked whenever Milo pushes a new tag value. Besides caching the latest value, it looks up the mapping key by node identifier. If the key ends with `.Update`, the change is forwarded to `ControllerUpdateService` together with the previous value (if any). If there is no registered key—for example, a brand new tag that was not included in the mapping set—`ControllerUpdateService` is not called and the value is just cached.【F:src/main/java/ru/datana/integration/opc/component/ValueManager.java†L45-L57】
* `ControllerUpdateService` parses the key before the `.Update` suffix. Keys without a variable name (e.g. `State.Update`) issue controller lifecycle commands (start, start-predict, stop, optimization enable/disable). Keys that include a variable name (e.g. `power.limit_top.Update`) are routed to the appropriate PATCH endpoint: `/variables/state`, `/variables/limits`, or `/optimization` depending on the property. Unrecognised properties are ignored with a debug log.【F:src/main/java/ru/datana/integration/opc/service/ControllerUpdateService.java†L15-L88】
* REST calls are executed by `ControllerApiClient`, which builds URLs from the configured base path and handles POST/PATCH invocations with logging of successes and failures.【F:src/main/java/ru/datana/integration/opc/service/ControllerApiClient.java†L17-L86】

### Manual writes through the existing API

* The REST endpoint that allows writing OPC values still delegates to `OpcService.setValues`, which builds the subset of known mappings and forwards them to the OPC client. Keys that are not part of the declared mapping set continue to be rejected for required updates and logged for optional updates; controller API calls are not triggered here because those routes operate on the OPC server directly.【F:src/main/java/ru/datana/integration/opc/service/OpcService.java†L147-L202】

## Environment configuration

* Controller API coordinates can be supplied through environment variables: `CONTROLLER_HOST`, `CONTROLLER_PORT`, and `CONTROLLER_BASE_PATH`. Defaults are `localhost`, `80`, and `/api/controller`. Per-environment overrides can also be provided via configuration properties (`controller.api.environments[ENV_NAME]`).【F:src/main/resources/application.yaml†L41-L56】【F:src/main/java/ru/datana/integration/opc/config/ControllerApiProperties.java†L12-L45】
* The resolved base URL is concatenated with the controller identifier received from OPC mappings (the same value that is part of the mapping key) before issuing REST calls such as `/start`, `/variables/limits`, etc.【F:src/main/java/ru/datana/integration/opc/service/ControllerApiClient.java†L33-L86】
