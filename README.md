<a href="https://fireflysoftware.io/openplatform/">
<picture>
  <source media="(max-width: 640px) and (prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-narrow-dark.svg">
  <source media="(max-width: 640px)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-narrow-light.svg">
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-dark.svg">
  <img src="https://raw.githubusercontent.com/fireflysoftwaresolutions/.github/main/profile/assets/banner-light.svg" alt="Firefly OpenPlatform - Domain Insurance - Reserving" width="100%">
</picture>
</a>

# Firefly OpenPlatform - Domain Insurance - Reserving

Developed by the Firefly Software Foundation. [dev@fireflysoftware.io](mailto:dev@fireflysoftware.io)

[Website](https://fireflysoftware.io/) · [OpenPlatform](https://fireflysoftware.io/openplatform/) · [Module catalogue](https://fireflysoftware.io/openplatform/modules/)

Domain layer microservice for reserve snapshots within the Firefly platform. This service orchestrates create, update, status change, close, and query against `core-insurance-reserving` with compensating saga steps.

Repository: [https://github.com/fireflysoftwaresolutions/domain-insurance-reserving](https://github.com/fireflysoftwaresolutions/domain-insurance-reserving)

---

## Overview

`domain-insurance-reserving` sits between experience services and `core-insurance-reserving`. Write operations run through the FireflyFramework saga engine. Queries use the CQRS query bus. The domain depends on the core SDK only. It does not import another core's internal packages. Identifiers that point at parties, contracts, products, or policies stay UUIDs owned by those cores.

It creates, updates, changes status, and closes reserve snapshots through the reserving core. It does not build a reporting extract, and it does not certify IFRS 17 or Solvency II.

### Key features

- **Create** -- register a reserve snapshot through `core-insurance-reserving`, with compensation that deletes the core write
- **Update** -- read the current reserve snapshot and write the new field values through `updateReserveSnapshot` on `core-insurance-reserving`. Status is left unchanged. Compensation writes the previous record back.
- **Status change** -- read the current reserve snapshot and update its status through `core-insurance-reserving`. This core keeps status on the record, so the status saga does not append a separate history row. Compensation writes the previous status back through the core.
- **Close** -- Accept sets ACCEPTED through the status saga. The reserving core does not restrict that move or delete, so remove deletes the snapshot whenever it exists.
- **Query** -- read one reserve snapshot by identifier
- **Compensation** -- a failed later step asks the core to undo the work already accepted
- **CQRS** -- commands and queries travel on separate buses
- **Reactive I/O** -- Spring WebFlux with virtual threads enabled

## Architecture

### Module structure

| Module | Purpose |
|--------|---------|
| `domain-insurance-reserving-core` | Saga workflows, commands, handlers, and the application service |
| `domain-insurance-reserving-interfaces` | Request contracts for a status change and a closing reason |
| `domain-insurance-reserving-infra` | SDK client factory and downstream base-path configuration |
| `domain-insurance-reserving-web` | Spring Boot application, REST API, actuator, and OpenAPI |
| `domain-insurance-reserving-sdk` | Java client generated from the OpenAPI document |

### Dependency flow

```
web --> interfaces --> core --> infra --> core-insurance-reserving-sdk
sdk (generated from openapi.yml)
```

### Saga map

| Saga | Core calls | Compensation |
|------|------------|--------------|
| `RegisterReserveSnapshotSaga` | `createReserveSnapshot` on `ReserveSnapshotsApi` | Deletes the record through `deleteReserveSnapshot` |
| `UpdateReserveSnapshotSaga` | Reads the current record, then writes field changes through `updateReserveSnapshot` on `ReserveSnapshotsApi`. Status is left unchanged. | Writes the previous record back through `updateReserveSnapshot` |
| `UpdateReserveSnapshotStatusSaga` | Reads the current record, then updates its status through `updateReserveSnapshot` | Writes the previous status back through the core |
| Query | `getReserveSnapshotById` on `ReserveSnapshotsApi` | None. A read has nothing to undo. |

Status values accepted by the core: `DRAFT`, `SUBMITTED`, `ACCEPTED`.

## Setup

### Prerequisites

- Java 25
- Apache Maven 3.9+
- `core-insurance-reserving-sdk` 26.10.3
- Apache Kafka on `localhost:9092` when event publishing is left at the default

### Environment

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_ADDRESS` | `localhost` | Bind address |
| `SERVER_PORT` | `8077` | HTTP port |
| `ENDPOINT_CORE_INSURANCE_RESERVING` | `http://localhost:8080` | Base URL of `core-insurance-reserving` |

### Build and test

```bash
mvn clean verify
```

### Run

```bash
ENDPOINT_CORE_INSURANCE_RESERVING=http://localhost:8080 mvn spring-boot:run -pl domain-insurance-reserving-web
```

Swagger UI (non-production): [http://localhost:8077/swagger-ui.html](http://localhost:8077/swagger-ui.html)

## API

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/api/v1/reserve-snapshots` | Runs `RegisterReserveSnapshotSaga` |
| `GET` | `/api/v1/reserve-snapshots/{reserveSnapshotId}` | Queries the core |
| `PUT` | `/api/v1/reserve-snapshots/{reserveSnapshotId}/status` | Runs `UpdateReserveSnapshotStatusSaga` |
| `PUT` | `/api/v1/reserve-snapshots/{reserveSnapshotId}` | Runs `UpdateReserveSnapshotSaga` |
| `POST` | `/api/v1/reserve-snapshots/{reserveSnapshotId}/accept` | Accept reserve snapshot. Runs `UpdateReserveSnapshotStatusSaga` and sets `ACCEPTED`. |
| `DELETE` | `/api/v1/reserve-snapshots/{reserveSnapshotId}` | Deletes the record through `core-insurance-reserving` when that core allows removal |

The status request body is `status` plus an optional `statusReason`.

Accept sets ACCEPTED through the status saga. The reserving core does not restrict that move or delete, so remove deletes the snapshot whenever it exists.
A closing command body is an optional `statusReason`.

