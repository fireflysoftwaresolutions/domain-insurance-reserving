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

package io.fireflysoftware.domain.insurance.reserving.core.workflows;

import io.fireflysoftware.core.insurance.reserving.sdk.model.ReserveSnapshotDTO;
import io.fireflysoftware.domain.insurance.reserving.core.commands.ApplyReserveSnapshotStatusCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.GetReserveSnapshotQuery;
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotStatusCommand;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.COMPENSATE_REMOVE_STATUS_HISTORY;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.COMPENSATE_RESTORE;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.CTX_PREVIOUS_STATUS;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_RETRIEVED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_STATUS_HISTORY_RECORDED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_STATUS_UPDATED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.SAGA_UPDATE_STATUS;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_RECORD_STATUS_HISTORY;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_RETRIEVE;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_UPDATE_STATUS;

/**
 * Changes a reserve snapshot status by reading it and writing the new status through the core.
 */
@Saga(name = SAGA_UPDATE_STATUS)
@Service
public class UpdateReserveSnapshotStatusSaga {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    @Autowired
    public UpdateReserveSnapshotStatusSaga(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @SagaStep(id = STEP_RETRIEVE)
    @StepEvent(type = EVENT_RETRIEVED)
    public Mono<ReserveSnapshotDTO> retrieveReserveSnapshot(GetReserveSnapshotQuery query, ExecutionContext ctx) {
        return queryBus.query(query)
                .doOnNext(record -> ctx.putVariable(CTX_RECORD, record));
    }

    @SagaStep(id = STEP_UPDATE_STATUS, dependsOn = STEP_RETRIEVE, compensate = COMPENSATE_RESTORE)
    @StepEvent(type = EVENT_STATUS_UPDATED)
    public Mono<ReserveSnapshotDTO> updateReserveSnapshotStatus(UpdateReserveSnapshotStatusCommand command, ExecutionContext ctx) {
        ReserveSnapshotDTO current = ctx.getVariableAs(CTX_RECORD, ReserveSnapshotDTO.class);
        if (current == null || current.getReserveSnapshotId() == null) {
            return Mono.error(new IllegalStateException("ReserveSnapshot was not found"));
        }
        if (current.getSnapshotStatus() == null) {
            return Mono.error(new IllegalStateException("ReserveSnapshot has no current status"));
        }
        if (command.status() == null || command.status().isBlank()) {
            return Mono.error(new IllegalArgumentException("Status is required"));
        }
        ctx.putVariable(CTX_PREVIOUS_STATUS, current.getSnapshotStatus().getValue());
        current.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.fromValue(command.status()));
        return commandBus.send(new ApplyReserveSnapshotStatusCommand(current.getReserveSnapshotId(), current))
                .doOnNext(updated -> ctx.putVariable(CTX_RECORD, updated));
    }

    public Mono<Void> restoreReserveSnapshotStatus(ReserveSnapshotDTO updated, ExecutionContext ctx) {
        if (updated == null || updated.getReserveSnapshotId() == null) {
            return Mono.empty();
        }
        String previous = ctx.getVariableAs(CTX_PREVIOUS_STATUS, String.class);
        if (previous == null || previous.isBlank()) {
            return Mono.empty();
        }
        updated.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.fromValue(previous));
        return commandBus.send(new ApplyReserveSnapshotStatusCommand(updated.getReserveSnapshotId(), updated)).then();
    }

}
