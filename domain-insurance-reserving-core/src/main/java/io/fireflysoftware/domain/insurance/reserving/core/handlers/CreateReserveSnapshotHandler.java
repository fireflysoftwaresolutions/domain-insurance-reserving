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
import io.fireflysoftware.domain.insurance.reserving.core.commands.CreateReserveSnapshotCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Creates a reserve snapshot by calling {@code core-insurance-reserving}.
 */
@Slf4j
@CommandHandlerComponent
public class CreateReserveSnapshotHandler extends CommandHandler<CreateReserveSnapshotCommand, ReserveSnapshotDTO> {

    private final ReserveSnapshotsApi reserveSnapshotsApi;

    public CreateReserveSnapshotHandler(ReserveSnapshotsApi reserveSnapshotsApi) {
        this.reserveSnapshotsApi = reserveSnapshotsApi;
    }

    @Override
    protected Mono<ReserveSnapshotDTO> doHandle(CreateReserveSnapshotCommand command) {
        log.debug("Creating reserve snapshot");
        return reserveSnapshotsApi.createReserveSnapshot(command.record(), UUID.randomUUID().toString());
    }
}
