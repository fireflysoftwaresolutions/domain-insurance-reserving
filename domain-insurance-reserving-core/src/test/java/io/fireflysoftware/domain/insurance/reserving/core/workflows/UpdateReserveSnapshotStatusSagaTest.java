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
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotStatusCommand;
import io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies a status change is written through the core and can be restored.
 */
@ExtendWith(MockitoExtension.class)
class UpdateReserveSnapshotStatusSagaTest {

    @Mock
    private CommandBus commandBus;

    @Mock
    private QueryBus queryBus;

    private UpdateReserveSnapshotStatusSaga saga;

    @BeforeEach
    void setUp() {
        saga = new UpdateReserveSnapshotStatusSaga(commandBus, queryBus);
    }

    @Test
    void updateStatus_sendsTheRequestedStatusToTheCore() {
        UUID id = UUID.randomUUID();
        ReserveSnapshotDTO current = new ReserveSnapshotDTO(id);
        current.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
        when(commandBus.send(any(ApplyReserveSnapshotStatusCommand.class))).thenReturn(Mono.just(current));

        ExecutionContext ctx = ExecutionContext.forSaga(ReserveSnapshotSagaConstants.SAGA_UPDATE_STATUS, "test");
        ctx.putVariable(ReserveSnapshotSagaConstants.CTX_RECORD, current);

        StepVerifier.create(saga.updateReserveSnapshotStatus(new UpdateReserveSnapshotStatusCommand(id, "SUBMITTED", "Caller request"), ctx))
                .expectNext(current)
                .verifyComplete();

        ArgumentCaptor<ApplyReserveSnapshotStatusCommand> captor = ArgumentCaptor.forClass(ApplyReserveSnapshotStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().reserveSnapshotId()).isEqualTo(id);
        assertThat(captor.getValue().record().getSnapshotStatus()).isEqualTo(ReserveSnapshotDTO.SnapshotStatusEnum.SUBMITTED);
        assertThat(ctx.getVariableAs(ReserveSnapshotSagaConstants.CTX_PREVIOUS_STATUS, String.class)).isEqualTo("DRAFT");
    }

    @Test
    void restore_writesThePreviousStatusBack() {
        UUID id = UUID.randomUUID();
        ReserveSnapshotDTO updated = new ReserveSnapshotDTO(id);
        updated.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.SUBMITTED);
        when(commandBus.send(any(ApplyReserveSnapshotStatusCommand.class))).thenReturn(Mono.just(updated));

        ExecutionContext ctx = ExecutionContext.forSaga(ReserveSnapshotSagaConstants.SAGA_UPDATE_STATUS, "test");
        ctx.putVariable(ReserveSnapshotSagaConstants.CTX_PREVIOUS_STATUS, "DRAFT");

        StepVerifier.create(saga.restoreReserveSnapshotStatus(updated, ctx))
                .verifyComplete();

        ArgumentCaptor<ApplyReserveSnapshotStatusCommand> captor = ArgumentCaptor.forClass(ApplyReserveSnapshotStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().record().getSnapshotStatus()).isEqualTo(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
    }
}
