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
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.COMPENSATE_RESTORE_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.CTX_PREVIOUS_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_RECORD_UPDATED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_RETRIEVED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.SAGA_UPDATE_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_RETRIEVE_FOR_UPDATE;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_UPDATE_RECORD;

/**
 * Updates a reserve snapshot on {@code core-insurance-reserving} and compensates by writing the previous record back.
 * Status stays on the status saga.
 */
@Saga(name = SAGA_UPDATE_RECORD)
@Service
public class UpdateReserveSnapshotSaga {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    @Autowired
    public UpdateReserveSnapshotSaga(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @SagaStep(id = STEP_RETRIEVE_FOR_UPDATE)
    @StepEvent(type = EVENT_RETRIEVED)
    public Mono<ReserveSnapshotDTO> retrieveReserveSnapshotForUpdate(GetReserveSnapshotQuery query, ExecutionContext ctx) {
        return queryBus.query(query)
                .doOnNext(record -> ctx.putVariable(CTX_RECORD, record));
    }

    @SagaStep(id = STEP_UPDATE_RECORD, dependsOn = STEP_RETRIEVE_FOR_UPDATE, compensate = COMPENSATE_RESTORE_RECORD)
    @StepEvent(type = EVENT_RECORD_UPDATED)
    public Mono<ReserveSnapshotDTO> updateReserveSnapshot(UpdateReserveSnapshotCommand command, ExecutionContext ctx) {
        ReserveSnapshotDTO current = ctx.getVariableAs(CTX_RECORD, ReserveSnapshotDTO.class);
        if (current == null || current.getReserveSnapshotId() == null) {
            return Mono.error(new IllegalStateException("Reserve snapshot was not found"));
        }
        ReserveSnapshotDTO incoming = command.record();
        if (incoming == null) {
            return Mono.error(new IllegalArgumentException("Reserve snapshot is required"));
        }
        if (current.getSnapshotStatus() != null) {
            incoming.setSnapshotStatus(current.getSnapshotStatus());
        }
        ctx.putVariable(CTX_PREVIOUS_RECORD, current);
        return commandBus.send(new ApplyReserveSnapshotStatusCommand(current.getReserveSnapshotId(), incoming))
                .doOnNext(updated -> ctx.putVariable(CTX_RECORD, updated));
    }

    public Mono<Void> restoreReserveSnapshot(ReserveSnapshotDTO updated, ExecutionContext ctx) {
        ReserveSnapshotDTO previous = ctx.getVariableAs(CTX_PREVIOUS_RECORD, ReserveSnapshotDTO.class);
        if (previous == null || previous.getReserveSnapshotId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new ApplyReserveSnapshotStatusCommand(previous.getReserveSnapshotId(), previous)).then();
    }
}
