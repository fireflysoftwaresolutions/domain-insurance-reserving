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
import io.fireflysoftware.domain.insurance.reserving.core.commands.UpdateReserveSnapshotCommand;
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
 * Verifies a field update keeps the current status and can be restored.
 */
@ExtendWith(MockitoExtension.class)
class UpdateReserveSnapshotSagaTest {

    @Mock
    private CommandBus commandBus;

    @Mock
    private QueryBus queryBus;

    private UpdateReserveSnapshotSaga saga;

    @BeforeEach
    void setUp() {
        saga = new UpdateReserveSnapshotSaga(commandBus, queryBus);
    }

    @Test
    void update_keepsTheCurrentStatus() {
        UUID id = UUID.randomUUID();
        ReserveSnapshotDTO current = new ReserveSnapshotDTO(id);
        current.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
        ReserveSnapshotDTO incoming = new ReserveSnapshotDTO();
        incoming.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.ACCEPTED);
        when(commandBus.send(any(ApplyReserveSnapshotStatusCommand.class))).thenAnswer(invocation ->
                Mono.just(invocation.getArgument(0, ApplyReserveSnapshotStatusCommand.class).record()));

        ExecutionContext ctx = ExecutionContext.forSaga(ReserveSnapshotSagaConstants.SAGA_UPDATE_RECORD, "test");
        ctx.putVariable(ReserveSnapshotSagaConstants.CTX_RECORD, current);

        StepVerifier.create(saga.updateReserveSnapshot(new UpdateReserveSnapshotCommand(id, incoming), ctx))
                .assertNext(saved -> assertThat(saved.getSnapshotStatus()).isEqualTo(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT))
                .verifyComplete();

        ArgumentCaptor<ApplyReserveSnapshotStatusCommand> captor = ArgumentCaptor.forClass(ApplyReserveSnapshotStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().reserveSnapshotId()).isEqualTo(id);
        assertThat(captor.getValue().record().getSnapshotStatus()).isEqualTo(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
        assertThat(ctx.getVariableAs(ReserveSnapshotSagaConstants.CTX_PREVIOUS_RECORD, ReserveSnapshotDTO.class)).isSameAs(current);
    }

    @Test
    void restore_writesThePreviousRecordBack() {
        UUID id = UUID.randomUUID();
        ReserveSnapshotDTO previous = new ReserveSnapshotDTO(id);
        previous.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
        ReserveSnapshotDTO updated = new ReserveSnapshotDTO(id);
        updated.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.ACCEPTED);
        when(commandBus.send(any(ApplyReserveSnapshotStatusCommand.class))).thenReturn(Mono.just(previous));

        ExecutionContext ctx = ExecutionContext.forSaga(ReserveSnapshotSagaConstants.SAGA_UPDATE_RECORD, "test");
        ctx.putVariable(ReserveSnapshotSagaConstants.CTX_PREVIOUS_RECORD, previous);

        StepVerifier.create(saga.restoreReserveSnapshot(updated, ctx))
                .verifyComplete();

        ArgumentCaptor<ApplyReserveSnapshotStatusCommand> captor = ArgumentCaptor.forClass(ApplyReserveSnapshotStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().record()).isSameAs(previous);
    }
}
