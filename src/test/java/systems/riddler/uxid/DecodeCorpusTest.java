package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cases copied from the conformance corpus at uxid-ex v2.10.0 ({@code conformance/decode.json} and
 * the decode refusals of {@code conformance/invalid.json}), each named by its case id. The full
 * corpus runner replaces this hand-picked subset.
 */
class DecodeCorpusTest {

  @ParameterizedTest(name = "{0}") // (sabotage: time read from char 2 -> cases failed)
  @CsvSource(
      nullValues = "null",
      value = {
        "decode/xl-standard, usr_01hf7yat0004hmasw9nf6yy093, usr, xl, false, false, 1700000000000",
        "decode/xl-compact, usr_hf7yat0004hmasw9nf6yy093, usr, xl, true, false, 1700000000000",
        "decode/l-standard, usr_01hf7yat0004hmasw9nf61, usr, l, false, false, 1700000000000",
        "decode/l-compact, usr_hf7yat0004hmasw9nf6yf, usr, l, true, false, 1700000000000",
        "decode/m-standard, usr_01hf7yat0004hmasw9, usr, m, false, false, 1700000000000",
        "decode/m-compact-reads-as-standard, usr_hf7yat0004hmasw9n3, usr, m, false, false,"
            + " 51950139736068",
        "decode/s-standard, usr_01hf7yat0004h1, usr, s, false, false, 1700000000000",
        "decode/s-compact, usr_hf7yat0004hm5, usr, s, true, false, 1700000000000",
        "decode/xs-standard, usr_01hf7yat00, usr, xs, false, false, 1700000000000",
        "decode/xs-compact-reads-as-standard, usr_hf7yat0001, usr, xs, false, false,"
            + " 51950139736065",
        "decode/xl-standard-upper, usr_01HF7YAT0004HMASW9NF6YY093, usr, xl, false, false,"
            + " 1700000000000",
        "decode/xl-standard-no-prefix, 01hf7yat0004hmasw9nf6yy093, null, xl, false, false,"
            + " 1700000000000",
        "decode/s-standard-prefix-with-delimiter, a_b_01hf7yat000001, a_b, s, false, false,"
            + " 1700000000000",
        "decode/s-compact-guard-last-valid, usr_yzzzzzzz04hm5, usr, s, true, false, 1065151889407",
        "decode/deterministic-usr-xl, usr_zcvt7epac0t1ebcsjfyf7cwz25, usr, xl, false, true, null",
        "decode/deterministic-cus-s, cus_zt9vnyfd85bff0, cus, s, false, true, null",
        "decode/deterministic-no-prefix-m, zqz4cwzgyw8ks97869, null, m, false, true, null",
        "decode/deterministic-upper-marker, usr_ZCVT7EPAC0T1EBCSJFYF7CWZ25, usr, xl, false, true,"
            + " null",
      })
  void aDecodeCaseReadsTheCorpusAnswerAcrossTheClockWindow(
      String id,
      String input,
      String prefix,
      String size,
      boolean compact,
      boolean deterministic,
      Long time) {
    DecodeResult.Decoded expected =
        new DecodeResult.Decoded(
            Optional.ofNullable(prefix),
            Size.parse(size),
            compact,
            deterministic,
            time == null ? OptionalLong.empty() : OptionalLong.of(time));
    for (long now : new long[] {ClockWindow.START, ClockWindow.END - 1}) {
      DecodeOptions options =
          DecodeOptions.builder()
              .clock(Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
              .build();
      assertEquals(expected, Uxid.decode(input, options), id + " at " + now);
    }
  }

  @ParameterizedTest(name = "{0}") // (sabotage: 2-byte char as 1 byte -> non-ascii cases failed)
  @CsvSource({
    "invalid/decode-empty-string, '', empty",
    "invalid/decode-ends-in-delimiter, usr_, empty",
    "invalid/decode-prefix-with-delimiter-ends-in-delimiter, a_b_, empty",
    "invalid/decode-delimiter-only, _, empty",
    "invalid/decode-length-11, usr_01hf7yat000, invalid_length",
    "invalid/decode-length-25, usr_01hf7yat00000g40r40m30e20, invalid_length",
    "invalid/decode-length-27, usr_01hf7yat00000g40r40m30e2090, invalid_length",
    "invalid/decode-no-delimiter-short, usr, invalid_length",
    "invalid/decode-character-in-time, usr_01hu7yat00000g40r40m30e209, invalid_character",
    "invalid/decode-character-in-random, usr_01hf7yat00000g4lr40m30e209, invalid_character",
    "invalid/decode-upper-o-in-random, usr_01hf7yat00000g40r40mO0e209, invalid_character",
    "invalid/decode-hyphen-in-body, usr_01hf7yat00-00g40r40m30e209, invalid_character",
    "invalid/decode-character-in-deterministic, usr_zcvt7epac0t1ebcsjfyf7cwzi5, invalid_character",
    "invalid/decode-non-ascii-table-length, 01hf7yat00000g40r40m30e2é, invalid_character",
    "invalid/decode-non-ascii-off-table-length, 01hf7yat00000g40r40m30e20é, invalid_length",
    "invalid/decode-length-and-character, usr_01hf7yat00u, invalid_length",
  })
  void aDecodeRefusalCaseIsRefusedWithTheCorpusReason(String id, String input, String reason) {
    DecodeResult.Invalid invalid =
        assertInstanceOf(DecodeResult.Invalid.class, Uxid.decode(input), id);
    assertEquals(reason, invalid.reason().wireName(), id);
  }
}
