package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cases copied from the conformance corpus at uxid-ex v2.10.0 ({@code conformance/monotonic.json}
 * and the monotonic refusals of {@code conformance/invalid.json}), each named by its case id. A
 * list is written space-separated. The full corpus runner replaces this hand-picked copy.
 */
class MonotonicCorpusTest {

  @ParameterizedTest(name = "{0}") // (sabotage: overflow keeps T -> the two overflow cases failed)
  @CsvSource(
      nullValues = "null",
      value = {
        "monotonic/xl-same-millisecond, usr, xl, false, 123456789, null, 1 1099511627776 42,"
            + " 1700000000000 1700000000000 1700000000000 1700000000000,"
            + " usr_01hf7yat0000000000003nqk8n usr_01hf7yat0000000000003nqk8p"
            + " usr_01hf7yat0000000001003nqk8p usr_01hf7yat0000000001003nqka0",
        "monotonic/m-clock-moves-back, usr, m, false, 1000, null, 5 1048576 7,"
            + " 1700000000000 1699999999999 1699999999990 1700000000000,"
            + " usr_01hf7yat00000000z8 usr_01hf7yat00000000zd usr_01hf7yat00000100zd"
            + " usr_01hf7yat00000100zm",
        "monotonic/s-overflow-bumps-time, usr, s, false, 65000, 1234, 200 250 100 7 3,"
            + " 1700000000000 1700000000000 1700000000000 1700000000000 1700000000000"
            + " 1700000000001,"
            + " usr_01hf7yat00zqm0 usr_01hf7yat00ztr0 usr_01hf7yat00zyn0 usr_01hf7yat010k90"
            + " usr_01hf7yat010kc1 usr_01hf7yat010ke0",
        "monotonic/xs-compact-overflow-clock-back, usr, xs, true, 250, 9, 5 16,"
            + " 1700000000000 1700000000000 1699999999999,"
            + " usr_hf7yat00z2 usr_hf7yat00z7 usr_hf7yat0111",
        "monotonic/l-compact-single, usr, l, true, 0, null, '', 1700000000000,"
            + " usr_hf7yat000000000000000",
        "monotonic/s-compact-no-prefix-largest-step, null, s, true, 0, null, 4096,"
            + " 1700000000000 1700000000000, hf7yat0000000 hf7yat0000800",
      })
  void aMonotonicCaseGivesTheCorpusIds(
      String id,
      String prefix,
      String size,
      boolean compact,
      long initialSeed,
      Long overflowSeed,
      String steps,
      String times,
      String expected) {
    List<Long> seeds = new ArrayList<>(List.of(initialSeed));
    if (overflowSeed != null) {
      seeds.add(overflowSeed);
    }
    TestSources.Replay draws = new TestSources.Replay(seeds, longs(steps));
    Monotonic generator = new Monotonic(draws);

    List<String> ids = new ArrayList<>();
    for (long time : longs(times)) {
      UxidOptions.Builder builder =
          UxidOptions.builder()
              .size(Size.parse(size))
              .compactTime(compact)
              .time(time)
              .byteSource(TestSources.refusing());
      if (prefix != null) {
        builder.prefix(prefix);
      }
      ids.add(generator.generate(builder.build()));
    }

    assertEquals(Arrays.asList(expected.split(" ", -1)), ids, id);
    assertTrue(draws.drained(), id + ": every recorded draw is drawn");
  }

  @Test // (sabotage: from check skipped with a prefix -> assertThrows saw draws)
  void fromWithAMonotonicGeneratorIsRefused() {
    UxidOptions options = UxidOptions.builder().prefix("usr").from("alice@example.com").build();
    Monotonic generator = new Monotonic(TestSources.refusingDraws());

    UxidException refusal = assertThrows(UxidException.class, () -> generator.generate(options));
    assertEquals(
        ErrorCode.FROM_WITH_MONOTONIC, refusal.code(), "invalid/generate-from-with-monotonic");
  }

  @Test // (sabotage: rand check skipped with a prefix -> assertThrows saw draws)
  void fixedRandomBytesWithAMonotonicGeneratorAreRefused() {
    UxidOptions options =
        UxidOptions.builder()
            .prefix("usr")
            .rand(HexFormat.of().parseHex("0102"))
            .size(Size.S)
            .time(1_700_000_000_000L)
            .build();
    Monotonic generator = new Monotonic(TestSources.refusingDraws());

    UxidException refusal = assertThrows(UxidException.class, () -> generator.generate(options));
    assertEquals(
        ErrorCode.RAND_WITH_MONOTONIC, refusal.code(), "invalid/generate-rand-with-monotonic");
  }

  private static List<Long> longs(String list) {
    List<Long> values = new ArrayList<>();
    for (String value : list.trim().split(" ", -1)) {
      if (!value.isEmpty()) {
        values.add(Long.parseLong(value));
      }
    }
    return values;
  }
}
