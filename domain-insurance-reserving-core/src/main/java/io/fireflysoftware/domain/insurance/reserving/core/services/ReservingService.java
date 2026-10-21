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

package io.fireflysoftware.domain.insurance.reserving.core.services;

import io.fireflysoftware.core.insurance.reserving.sdk.model.ReserveSnapshotDTO;
import io.fireflysoftware.domain.insurance.reserving.core.commands.CreateReserveSnapshotCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotStatusCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for reserve snapshot orchestration.
 */
public interface ReservingService {

    /**
     * Creates a reserve snapshot through the register saga.
     *
     * @param command the create command
     * @return the record returned by the core
     */
    Mono<ReserveSnapshotDTO> create(CreateReserveSnapshotCommand command);

    /**
     * Moves a reserve snapshot to a new status through the core.
     *
     * @param command the status change
     * @return the updated record
     */
    Mono<ReserveSnapshotDTO> updateStatus(UpdateReserveSnapshotStatusCommand command);

    /**
     * Reads one reserve snapshot.
     *
     * @param reserveSnapshotId the core identifier
     * @return the record, or empty when the core has none
     */
    Mono<ReserveSnapshotDTO> get(UUID reserveSnapshotId);

    /**
     * Updates fields on a reserve snapshot and leaves its status unchanged.
     *
     * @param command the update
     * @return the record returned by the core
     */
    Mono<ReserveSnapshotDTO> update(UpdateReserveSnapshotCommand command);

    /**
     * Accepts a reserve snapshot through the reserving core.
     *
     * @param reserveSnapshotId the core identifier
     * @param statusReason optional reason stored by the status saga when the core keeps one
     * @return the updated record
     */
    Mono<ReserveSnapshotDTO> accept(UUID reserveSnapshotId, String statusReason);

    /**
     * Deletes a reserve snapshot when the core lifecycle allows removal.
     *
     * @param reserveSnapshotId the core identifier
     * @return completion when the core has deleted the record
     */
    Mono<Void> remove(UUID reserveSnapshotId);
}
