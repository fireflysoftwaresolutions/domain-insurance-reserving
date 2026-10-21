/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.domain.insurance.reserving.core.handlers;

import io.fireflysoftware.core.insurance.reserving.sdk.api.ReserveSnapshotsApi;
import io.fireflysoftware.core.insurance.reserving.sdk.model.ReserveSnapshotDTO;
import io.fireflysoftware.domain.insurance.reserving.core.commands.GetReserveSnapshotQuery;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Loads one reserve snapshot from the core.
 */
@Slf4j
@QueryHandlerComponent
public class GetReserveSnapshotHandler extends QueryHandler<GetReserveSnapshotQuery, ReserveSnapshotDTO> {

    private final ReserveSnapshotsApi reserveSnapshotsApi;

    public GetReserveSnapshotHandler(ReserveSnapshotsApi reserveSnapshotsApi) {
        this.reserveSnapshotsApi = reserveSnapshotsApi;
    }

    @Override
    protected Mono<ReserveSnapshotDTO> doHandle(GetReserveSnapshotQuery query) {
        log.debug("Fetching reserve snapshot: reserveSnapshotId={}", query.reserveSnapshotId());
        return reserveSnapshotsApi.getReserveSnapshotById(query.reserveSnapshotId(), UUID.randomUUID().toString());
    }
}
