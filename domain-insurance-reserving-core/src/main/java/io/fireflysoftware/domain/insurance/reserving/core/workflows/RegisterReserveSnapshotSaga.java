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
import io.fireflysoftware.domain.insurance.reserving.core.commands.CreateReserveSnapshotCommand;
import io.fireflysoftware.domain.insurance.reserving.core.commands.RemoveReserveSnapshotCommand;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.COMPENSATE_REMOVE;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.COMPENSATE_REMOVE_INITIAL_STATUS;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_INITIAL_STATUS_RECORDED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.EVENT_REGISTERED;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.SAGA_REGISTER;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_RECORD_INITIAL_STATUS;
import static io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants.STEP_REGISTER;

/**
 * Creates a reserve snapshot on {@code core-insurance-reserving} and compensates by deleting it.
 */
@Saga(name = SAGA_REGISTER)
@Service
public class RegisterReserveSnapshotSaga {

    private final CommandBus commandBus;

    @Autowired
    public RegisterReserveSnapshotSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = STEP_REGISTER, compensate = COMPENSATE_REMOVE)
    @StepEvent(type = EVENT_REGISTERED)
    public Mono<ReserveSnapshotDTO> registerReserveSnapshot(CreateReserveSnapshotCommand command, ExecutionContext ctx) {
        return commandBus.send(command)
                .doOnNext(created -> ctx.putVariable(CTX_RECORD, created));
    }

    public Mono<Void> removeReserveSnapshot(ReserveSnapshotDTO created) {
        if (created == null || created.getReserveSnapshotId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveReserveSnapshotCommand(created.getReserveSnapshotId()));
    }

}
