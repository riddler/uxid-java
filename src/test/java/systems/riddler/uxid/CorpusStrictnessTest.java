package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/** The runner reads a case strictly, takes a null prefix as none and skips nothing. */
class CorpusStrictnessTest {
  private static final String ENCODE =
      "{\"expected\":\"0000000000\",\"id\":\"encode/x\",\"input\":{\"case\":\"lower\","
          + "\"compact\":false,\"prefix\":null,\"rand\":\"\",\"size\":\"xs\",\"time\":0}}";

  @Test // (sabotage: done() never fails -> the extra key was accepted)
  void aKeyTheRunnerDoesNotKnowAtTheTopOfACaseFails() {
    String line = ENCODE.replace("{\"expected\"", "{\"extra\":1,\"expected\"");

    AssertionError failure = assertThrows(AssertionError.class, () -> CorpusCases.encode(line));
    assertTrue(failure.getMessage().contains("extra"), failure.getMessage());
  }

  @Test // (sabotage: done() never fails -> the extra key was accepted)
  void aKeyTheRunnerDoesNotKnowInACasesInputFails() {
    String line = ENCODE.replace("\"case\":\"lower\"", "\"case\":\"lower\",\"zone\":\"utc\"");

    AssertionError failure = assertThrows(AssertionError.class, () -> CorpusCases.encode(line));
    assertTrue(failure.getMessage().contains("zone"), failure.getMessage());
  }

  @Test // (sabotage: done() never fails -> the extra option was accepted)
  void aKeyTheRunnerDoesNotKnowInARefusedRequestFails() {
    String line =
        "{\"expected_error\":\"rand_size_too_large\",\"id\":\"invalid/x\",\"input\":{\"generate\":"
            + "{\"prefix\":\"usr\",\"rand_size\":11,\"retries\":3,\"size\":\"xl\",\"time\":0}}}";

    AssertionError failure = assertThrows(AssertionError.class, () -> CorpusCases.invalid(line));
    assertTrue(failure.getMessage().contains("retries"), failure.getMessage());
  }

  @Test // (sabotage: a missing key read as null -> the missing key assertion failed)
  void aKeyMissingFromACaseFails() {
    String line = ENCODE.replace("\"prefix\":null,", "");

    AssertionError failure = assertThrows(AssertionError.class, () -> CorpusCases.encode(line));
    assertTrue(failure.getMessage().contains("has no prefix"), failure.getMessage());
  }

  @Test // (sabotage: null prefix read as the text null -> the no prefix assertion failed)
  void aNullPrefixAndAnEmptyOneReadAsNoPrefix() {
    assertDoesNotThrow(() -> CorpusCases.encode(ENCODE));
    assertDoesNotThrow(() -> CorpusCases.encode(ENCODE.replace("null", "\"\"")));
    String line = ENCODE.replace("\"prefix\":null", "\"prefix\":\"usr\"");

    assertThrows(AssertionFailedError.class, () -> CorpusCases.encode(line));
  }

  @Test // (sabotage: answer not compared -> the wrong answer passed)
  void aCaseWhoseAnswerIsWrongFailsInsteadOfBeingSkipped() {
    String line = ENCODE.replace("\"expected\":\"0000000000\"", "\"expected\":\"0000000001\"");

    assertThrows(AssertionFailedError.class, () -> CorpusCases.encode(line));
  }

  @Test // (sabotage: duplicate key check disabled -> the duplicate assertion failed)
  void theJsonReaderRefusesWhatTheCorpusNeverHolds() {
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1.5}"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,]"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} x"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("\"open"));
  }

  @Test // (sabotage: unicode escape read as ? -> the value assertion failed)
  void theJsonReaderReadsEveryValueShapeTheCorpusUses() {
    Object value =
        Json.parse("{\"s\":\"caf\\u00e9\\n\",\"n\":-7,\"b\":[true,false,null],\"o\":{}}");

    assertEquals(
        Map.of("s", "café\n", "n", -7L, "b", Json.parse("[true,false,null]"), "o", Map.of()),
        value);
    assertInstanceOf(List.class, Json.parse("[]"));
  }
}
