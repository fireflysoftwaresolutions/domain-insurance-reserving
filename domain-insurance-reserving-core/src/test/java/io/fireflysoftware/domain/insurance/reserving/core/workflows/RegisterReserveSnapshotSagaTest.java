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
import io.fireflysoftware.domain.insurance.reserving.core.constants.ReserveSnapshotSagaConstants;

import org.fireflyframework.cqrs.command.CommandBus;
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
 * Verifies the register saga stores the core result and can compensate by deleting it.
 */
@ExtendWith(MockitoExtension.class)
class RegisterReserveSnapshotSagaTest {

    @Mock
    private CommandBus commandBus;

    private RegisterReserveSnapshotSaga saga;

    @BeforeEach
    void setUp() {
        saga = new RegisterReserveSnapshotSaga(commandBus);
    }

    @Test
    void register_storesTheRecordReturnedByTheCore() {
        UUID id = UUID.randomUUID();
        ReserveSnapshotDTO created = new ReserveSnapshotDTO(id);
        created.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);
        when(commandBus.send(any(CreateReserveSnapshotCommand.class))).thenReturn(Mono.just(created));

        ExecutionContext ctx = ExecutionContext.forSaga(ReserveSnapshotSagaConstants.SAGA_REGISTER, "test");
        ReserveSnapshotDTO request = new ReserveSnapshotDTO();
        request.setSnapshotStatus(ReserveSnapshotDTO.SnapshotStatusEnum.DRAFT);

        StepVerifier.create(saga.registerReserveSnapshot(new CreateReserveSnapshotCommand(request), ctx))
                .expectNext(created)
                .verifyComplete();

        assertThat(ctx.getVariableAs(ReserveSnapshotSagaConstants.CTX_RECORD, ReserveSnapshotDTO.class).getReserveSnapshotId()).isEqualTo(id);
    }

    @Test
    void remove_sendsDeleteForTheCreatedRecord() {
        UUID id = UUID.randomUUID();
        when(commandBus.send(any(RemoveReserveSnapshotCommand.class))).thenReturn(Mono.empty());

        StepVerifier.create(saga.removeReserveSnapshot(new ReserveSnapshotDTO(id)))
                .verifyComplete();

        ArgumentCaptor<RemoveReserveSnapshotCommand> captor = ArgumentCaptor.forClass(RemoveReserveSnapshotCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().reserveSnapshotId()).isEqualTo(id);
    }

}
