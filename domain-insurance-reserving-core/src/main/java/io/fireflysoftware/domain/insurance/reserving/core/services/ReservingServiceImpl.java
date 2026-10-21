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
import io.fireflysoftware.domain.insurance.reserving.core.commands.GetReserveSnapshotQuery;
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotStatusCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.RemoveReserveSnapshotCommand;
import io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs the reserve snapshot sagas and reads through the query bus.
 */
@Service
public class ReservingServiceImpl implements ReservingService {

    private final SagaEngine sagaEngine;
    private final QueryBus queryBus;
    private final CommandBus commandBus;

    public ReservingServiceImpl(SagaEngine sagaEngine, QueryBus queryBus, CommandBus commandBus) {
        this.sagaEngine = sagaEngine;
        this.queryBus = queryBus;
        this.commandBus = commandBus;
    }

    @Override
    public Mono<ReserveSnapshotDTO> create(CreateReserveSnapshotCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ReserveSnapshotSagaConstants.STEP_REGISTER, command)
                .build();
        return sagaEngine.execute(ReserveSnapshotSagaConstants.SAGA_REGISTER, inputs)
                .flatMap(result -> outcome(result, ReserveSnapshotSagaConstants.STEP_REGISTER, ReserveSnapshotDTO.class));
    }

    @Override
    public Mono<ReserveSnapshotDTO> updateStatus(UpdateReserveSnapshotStatusCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ReserveSnapshotSagaConstants.STEP_RETRIEVE, new GetReserveSnapshotQuery(command.reserveSnapshotId()))
                .forStepId(ReserveSnapshotSagaConstants.STEP_UPDATE_STATUS, command)
                .build();
        return sagaEngine.execute(ReserveSnapshotSagaConstants.SAGA_UPDATE_STATUS, inputs)
                .flatMap(result -> outcome(result, ReserveSnapshotSagaConstants.STEP_UPDATE_STATUS, ReserveSnapshotDTO.class));
    }

    @Override
    public Mono<ReserveSnapshotDTO> get(UUID reserveSnapshotId) {
        return queryBus.query(new GetReserveSnapshotQuery(reserveSnapshotId));
    }


    @Override
    public Mono<ReserveSnapshotDTO> update(UpdateReserveSnapshotCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ReserveSnapshotSagaConstants.STEP_RETRIEVE_FOR_UPDATE, new GetReserveSnapshotQuery(command.reserveSnapshotId()))
                .forStepId(ReserveSnapshotSagaConstants.STEP_UPDATE_RECORD, command)
                .build();
        return sagaEngine.execute(ReserveSnapshotSagaConstants.SAGA_UPDATE_RECORD, inputs)
                .flatMap(result -> outcome(result, ReserveSnapshotSagaConstants.STEP_UPDATE_RECORD, ReserveSnapshotDTO.class));
    }

    @Override
    public Mono<ReserveSnapshotDTO> accept(UUID reserveSnapshotId, String statusReason) {
        return updateStatus(new UpdateReserveSnapshotStatusCommand(reserveSnapshotId, "ACCEPTED", statusReason));
    }

    @Override
    public Mono<Void> remove(UUID reserveSnapshotId) {
        return get(reserveSnapshotId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Reserve snapshot was not found")))
                .flatMap(current -> commandBus.send(new RemoveReserveSnapshotCommand(reserveSnapshotId)));
    }

    private static <T> Mono<T> outcome(SagaResult result, String stepId, Class<T> type) {
        if (result.isFailed()) {
            Throwable error = result.error().orElseGet(() ->
                    new IllegalStateException("Saga " + result.sagaName() + " failed"));
            return Mono.error(error);
        }
        return Mono.justOrEmpty(result.resultOf(stepId, type))
                .switchIfEmpty(Mono.error(new IllegalStateException("Saga step " + stepId + " produced no result")));
    }
}
