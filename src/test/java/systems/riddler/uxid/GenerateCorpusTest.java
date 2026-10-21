package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cases copied from the conformance corpus at uxid-ex v2.10.0 ({@code conformance/encode.json} and
 * the generate refusals of {@code conformance/invalid.json}), each named by its case id. The full
 * corpus runner replaces this hand-picked subset.
 */
class GenerateCorpusTest {

  @ParameterizedTest(name = "{0}") // (sabotage: short group padded -> xs, s, m and l cases failed)
  @CsvSource(
      nullValues = "null",
      value = {
        "encode/xs-standard-lower, usr, xs, false, lower, '', 1700000000000, usr_01hf7yat00",
        "encode/xs-compact-upper, usr, xs, true, upper, 01, 1700000000000, usr_HF7YAT0001",
        "encode/s-standard-lower, usr, s, false, lower, 0123, 1700000000000, usr_01hf7yat0004h1",
        "encode/s-compact-lower, usr, s, true, lower, 012345, 1700000000000, usr_hf7yat0004hm5",
        "encode/m-standard-upper, usr, m, false, upper, 0123456789, 1700000000000,"
            + " usr_01HF7YAT0004HMASW9",
        "encode/m-compact-lower, usr, m, true, lower, 0123456789ab, 1700000000000,"
            + " usr_hf7yat0004hmasw9n3",
        "encode/l-standard-lower, usr, l, false, lower, 0123456789abcd, 1700000000000,"
            + " usr_01hf7yat0004hmasw9nf61",
        "encode/l-compact-upper, usr, l, true, upper, 0123456789abcdef, 1700000000000,"
            + " usr_HF7YAT0004HMASW9NF6YF",
        "encode/xl-standard-lower, usr, xl, false, lower, 0123456789abcdef0123, 1700000000000,"
            + " usr_01hf7yat0004hmasw9nf6yy093",
        "encode/xl-compact-lower, usr, xl, true, lower, 0123456789abcdef0123, 1700000000000,"
            + " usr_hf7yat0004hmasw9nf6yy093",
        "encode/xs-compact-rand-all-ones, cus, xs, true, lower, ff, 1700000000000, cus_hf7yat00z7",
        "encode/s-standard-rand-all-ones, cus, s, false, lower, ffff, 1700000000000,"
            + " cus_01hf7yat00zzz1",
        "encode/l-compact-rand-all-ones, cus, l, true, lower, ffffffffffffffff, 1700000000000,"
            + " cus_hf7yat00zzzzzzzzzzzzf",
        "encode/xl-standard-rand-all-ones, cus, xl, false, lower, ffffffffffffffffffff,"
            + " 1700000000000, cus_01hf7yat00zzzzzzzzzzzzzzzz",
        "encode/xs-standard-time-zero, null, xs, false, lower, '', 0, 0000000000",
        "encode/xs-standard-time-max, null, xs, false, lower, '', 281474976710655, 7zzzzzzzzz",
        "encode/s-compact-guard-last-valid, usr, s, true, lower, 012345, 1065151889407,"
            + " usr_yzzzzzzz04hm5",
        "encode/xl-standard-no-prefix, null, xl, false, lower, 0123456789abcdef0123,"
            + " 1700000000000, 01hf7yat0004hmasw9nf6yy093",
        "encode/xl-standard-empty-prefix, '', xl, false, lower, 0123456789abcdef0123,"
            + " 1700000000000, 01hf7yat0004hmasw9nf6yy093",
        "encode/s-standard-prefix-with-delimiter, a_b, s, false, lower, 0123, 1700000000000,"
            + " a_b_01hf7yat0004h1",
      })
  void anEncodeCaseGivesTheCorpusId(
      String id,
      String prefix,
      String size,
      boolean compact,
      String letterCase,
      String rand,
      long time,
      String expected) {
    UxidOptions.Builder builder =
        UxidOptions.builder()
            .size(Size.parse(size))
            .compactTime(compact)
            .letterCase(letterCase.equals("upper") ? LetterCase.UPPER : LetterCase.LOWER)
            .rand(HexFormat.of().parseHex(rand))
            .time(time)
            .byteSource(TestSources.refusing());
    if (prefix != null) {
      builder.prefix(prefix);
    }
    assertEquals(expected, Uxid.generate(builder.build()), id);
  }

  @ParameterizedTest(name = "{0}") // (sabotage: guard compares to 32 -> both cases threw nothing)
  @CsvSource({
    "invalid/generate-compact-time-at-guard, 2164663517184",
    "invalid/generate-compact-time-at-guard-previous-window, 1065151889408",
  })
  void aCompactTimeAtTheMarkerIsRefused(String id, long time) {
    UxidOptions options =
        UxidOptions.builder()
            .prefix("usr")
            .size(Size.S)
            .compactTime(true)
            .rand(HexFormat.of().parseHex("010203"))
            .time(time)
            .build();
    UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options), id);
    assertEquals(ErrorCode.COMPACT_TIME_OUT_OF_RANGE, refusal.code(), id);
  }

  @ParameterizedTest(name = "{0}") // (sabotage: width check never fires -> all three threw nothing)
  @CsvSource({
    "invalid/generate-rand-too-wide, s, 010203",
    "invalid/generate-rand-too-narrow, xl, 010203040506070809",
    "invalid/generate-rand-on-xs-standard, xs, 01",
  })
  void fixedBytesOfTheWrongWidthAreRefused(String id, String size, String rand) {
    UxidOptions options =
        UxidOptions.builder()
            .prefix("usr")
            .size(Size.parse(size))
            .rand(HexFormat.of().parseHex(rand))
            .time(1_700_000_000_000L)
            .build();
    UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options), id);
    assertEquals(ErrorCode.RAND_WIDTH_MISMATCH, refusal.code(), id);
  }

  @ParameterizedTest(name = "{0}") // (sabotage: cap raised to 11 -> the refusing source was called)
  @CsvSource("invalid/generate-rand-size-11, 11")
  void aRandomWidthAboveTenIsRefused(String id, int randSize) {
    UxidOptions options =
        UxidOptions.builder()
            .prefix("usr")
            .size(Size.XL)
            .randSize(randSize)
            .time(1_700_000_000_000L)
            .byteSource(TestSources.refusing())
            .build();
    UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options), id);
    assertEquals(ErrorCode.RAND_SIZE_TOO_LARGE, refusal.code(), id);
  }
}
