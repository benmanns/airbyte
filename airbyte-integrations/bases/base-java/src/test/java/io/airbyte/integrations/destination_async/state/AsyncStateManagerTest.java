/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination_async.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.airbyte.commons.json.Jsons;
import io.airbyte.integrations.destination_async.buffers.BufferManager;
import io.airbyte.integrations.destination_async.buffers.StreamAwareQueue;
import io.airbyte.protocol.models.v0.AirbyteMessage;
import io.airbyte.protocol.models.v0.AirbyteMessage.Type;
import io.airbyte.protocol.models.v0.AirbyteStateMessage;
import io.airbyte.protocol.models.v0.AirbyteStateMessage.AirbyteStateType;
import io.airbyte.protocol.models.v0.AirbyteStreamState;
import io.airbyte.protocol.models.v0.StreamDescriptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AsyncStateManagerTest {

  private static final String STREAM_NAME = "id_and_name";
  private static final String STREAM_NAME2 = STREAM_NAME + 2;
  private static final String STREAM_NAME3 = STREAM_NAME + 3;
  private static final StreamDescriptor STREAM1_DESC = new StreamDescriptor()
      .withName(STREAM_NAME);
  private static final StreamDescriptor STREAM2_DESC = new StreamDescriptor()
      .withName(STREAM_NAME2);
  private static final StreamDescriptor STREAM3_DESC = new StreamDescriptor()
          .withName(STREAM_NAME3);

  private static final AirbyteMessage GLOBAL_STATE_MESSAGE1 = new AirbyteMessage()
      .withType(Type.STATE)
      .withState(new AirbyteStateMessage()
          .withType(AirbyteStateType.GLOBAL).withData(Jsons.jsonNode(1)));
  private static final AirbyteMessage GLOBAL_STATE_MESSAGE2 = new AirbyteMessage()
          .withType(Type.STATE)
          .withState(new AirbyteStateMessage()
                  .withType(AirbyteStateType.GLOBAL).withData(Jsons.jsonNode(2)));
  private static final AirbyteMessage STREAM1_STATE_MESSAGE1 = new AirbyteMessage()
      .withType(Type.STATE)
      .withState(new AirbyteStateMessage()
          .withType(AirbyteStateType.STREAM)
          .withStream(new AirbyteStreamState().withStreamDescriptor(STREAM1_DESC).withStreamState(Jsons.jsonNode(1))));
  private static final AirbyteMessage STREAM1_STATE_MESSAGE2 = new AirbyteMessage()
      .withType(Type.STATE)
      .withState(new AirbyteStateMessage()
          .withType(AirbyteStateType.STREAM)
          .withStream(new AirbyteStreamState().withStreamDescriptor(STREAM1_DESC).withStreamState(Jsons.jsonNode(2))));


  @Test
  void testBasic() {
    final AsyncStateManager stateManager = new AsyncStateManager();

    final var firstStateId = stateManager.getStateIdAndIncrement(STREAM1_DESC);
    final var secondStateId = stateManager.getStateIdAndIncrement(STREAM1_DESC);
    assertEquals(firstStateId, secondStateId);

    stateManager.decrement(firstStateId, 2);
    // because no state message has been tracked, there is nothing to flush yet.
    var flushed = stateManager.flushStates();
    assertEquals(0, flushed.size());

    stateManager.trackState(STREAM1_STATE_MESSAGE1);
    flushed = stateManager.flushStates();
    assertEquals(List.of(STREAM1_STATE_MESSAGE1), flushed);
  }

  @Nested
  class GlobalState {
    @Test
    void testEmptyQueuesGlobalState() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      // GLOBAL
      stateManager.trackState(GLOBAL_STATE_MESSAGE1);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE1), stateManager.flushStates());

      assertThrows(IllegalArgumentException.class, () -> stateManager.trackState(STREAM1_STATE_MESSAGE1));
    }

    @Test
    void testConversion() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      final var preConvertId0 = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      final var preConvertId1 = simulateIncomingRecords(STREAM2_DESC, 10, stateManager);
      final var preConvertId2 = simulateIncomingRecords(STREAM3_DESC, 10, stateManager);
      assertEquals(3, Set.of(preConvertId0, preConvertId1, preConvertId2).size());

      stateManager.trackState(GLOBAL_STATE_MESSAGE1);

      // Since this is actually a global state, we can only flush after all streams are done.
      stateManager.decrement(preConvertId0, 10);
      assertEquals(List.of(), stateManager.flushStates());
      stateManager.decrement(preConvertId1, 10);
      assertEquals(List.of(), stateManager.flushStates());
      stateManager.decrement(preConvertId2, 10);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE1), stateManager.flushStates());
    }

    @Test
    void testCorrectFlushingOneStream() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      final var preConvertId0 = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      stateManager.trackState(GLOBAL_STATE_MESSAGE1);
      stateManager.decrement(preConvertId0, 10);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE1), stateManager.flushStates());

      final var afterConvertId1 = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      stateManager.trackState(GLOBAL_STATE_MESSAGE2);
      stateManager.decrement(afterConvertId1, 10);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE2), stateManager.flushStates());
    }

    @Test
    void testCorrectFlushingManyStreams() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      final var preConvertId0 = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      final var preConvertId1 = simulateIncomingRecords(STREAM2_DESC, 10, stateManager);
      assertNotEquals(preConvertId0, preConvertId1);
      stateManager.trackState(GLOBAL_STATE_MESSAGE1);
      stateManager.decrement(preConvertId0, 10);
      stateManager.decrement(preConvertId1, 10);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE1), stateManager.flushStates());

      final var afterConvertId0 = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      final var afterConvertId1 = simulateIncomingRecords(STREAM2_DESC, 10, stateManager);
      assertEquals(afterConvertId0, afterConvertId1);
      stateManager.trackState(GLOBAL_STATE_MESSAGE2);
      stateManager.decrement(afterConvertId0, 20);
      assertEquals(List.of(GLOBAL_STATE_MESSAGE2), stateManager.flushStates());
    }
  }

  @Nested
  class PerStreamState {
    @Test
    void testEmptyQueues() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      // GLOBAL
      stateManager.trackState(STREAM1_STATE_MESSAGE1);
      assertEquals(List.of(STREAM1_STATE_MESSAGE1), stateManager.flushStates());

      assertThrows(IllegalArgumentException.class, () -> stateManager.trackState(GLOBAL_STATE_MESSAGE1));
    }

    @Test
    void testCorrectFlushingOneStream() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      var stateId = simulateIncomingRecords(STREAM1_DESC, 3, stateManager);
      stateManager.trackState(STREAM1_STATE_MESSAGE1);
      stateManager.decrement(stateId, 3);
      assertEquals(List.of(STREAM1_STATE_MESSAGE1), stateManager.flushStates());

      stateId = simulateIncomingRecords(STREAM1_DESC, 10, stateManager);
      stateManager.trackState(STREAM1_STATE_MESSAGE2);
      stateManager.decrement(stateId, 10);
      assertEquals(List.of(STREAM1_STATE_MESSAGE2), stateManager.flushStates());

    }

    @Test
    void testCorrectFlushingManyStream() {
      final AsyncStateManager stateManager = new AsyncStateManager();

      final var stream1StateId = simulateIncomingRecords(STREAM1_DESC, 3, stateManager);
      final var stream2StateId = simulateIncomingRecords(STREAM2_DESC, 7, stateManager);

      stateManager.trackState(STREAM1_STATE_MESSAGE1);
      stateManager.decrement(stream1StateId, 3);
      assertEquals(List.of(STREAM1_STATE_MESSAGE1), stateManager.flushStates());

      stateManager.decrement(stream2StateId, 4);
      assertEquals(List.of(), stateManager.flushStates());
      stateManager.trackState(STREAM1_STATE_MESSAGE2);
      stateManager.decrement(stream2StateId, 3);
      // only flush state if counter is 0.
      assertEquals(List.of(STREAM1_STATE_MESSAGE2), stateManager.flushStates());
    }
  }

  private static long simulateIncomingRecords(final StreamDescriptor desc, final long count, final AsyncStateManager manager) {
    var stateId = 0L;
    for (int i = 0; i < count; i++) {
      stateId = manager.getStateIdAndIncrement(desc);
    }
    return stateId;
  }
}
