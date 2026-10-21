package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cases copied from the conformance corpus at uxid-ex v2.10.0 ({@code
 * conformance/deterministic.json} and the deterministic refusal of {@code
 * conformance/invalid.json}), each named by its case id. The full corpus runner replaces this
 * hand-picked subset.
 */
class DeterministicCorpusTest {

  @ParameterizedTest(name = "{0}") // (sabotage: count byte left out -> every case failed)
  @CsvSource(
      nullValues = "null",
      value = {
        "deterministic/golden-usr-xl, usr, alice@example.com, xl, lower,"
            + " usr_zcvt7epac0t1ebcsjfyf7cwz25",
        "deterministic/golden-cus-s, cus, acme-corp, s, lower, cus_zt9vnyfd85bff0",
        "deterministic/golden-no-prefix-m, null, alice@example.com, m, lower, zqz4cwzgyw8ks97869",
        "deterministic/usr-xs, usr, alice@example.com, xs, lower, usr_z1yhw20vag",
        "deterministic/usr-s, usr, alice@example.com, s, lower, usr_z5rtsrkhx2h4dr",
        "deterministic/usr-m, usr, alice@example.com, m, lower, usr_zt8naccefxhwnrmscd",
        "deterministic/usr-l, usr, alice@example.com, l, lower, usr_zwbz6myvgz7ehe3nceap5n",
        "deterministic/usr-xl, usr, alice@example.com, xl, lower, usr_zcvt7epac0t1ebcsjfyf7cwz25",
        "deterministic/empty-prefix-xl, '', alice@example.com, xl, lower,"
            + " z4krgegf6drn563pvqhya4tsx9",
        "deterministic/no-prefix-xl, null, alice@example.com, xl, lower,"
            + " z4krgegf6drn563pvqhya4tsx9",
        "deterministic/usr-xl-upper, usr, alice@example.com, xl, upper,"
            + " usr_ZCVT7EPAC0T1EBCSJFYF7CWZ25",
        "deterministic/non-ascii-from-m, cus, café, m, lower, cus_zfsx9se75671f9kdjv",
      })
  void aDeterministicCaseGivesTheCorpusId(
      String id, String prefix, String from, String size, String letterCase, String expected) {
    UxidOptions.Builder builder =
        UxidOptions.builder()
            .from(from)
            .size(Size.parse(size))
            .letterCase(letterCase.equals("upper") ? LetterCase.UPPER : LetterCase.LOWER)
            .byteSource(TestSources.refusing());
    if (prefix != null) {
      builder.prefix(prefix);
    }
    assertEquals(expected, Uxid.generate(builder.build()), id);
  }

  @ParameterizedTest(name = "{0}") // (sabotage: rand_with_from check removed -> nothing thrown)
  @CsvSource("invalid/generate-rand-with-from, usr, alice@example.com, 00010203040506070809")
  void fixedBytesWithADeterministicBodyAreRefused(
      String id, String prefix, String from, String rand) {
    UxidOptions options =
        UxidOptions.builder()
            .prefix(prefix)
            .from(from)
            .rand(HexFormat.of().parseHex(rand))
            .byteSource(TestSources.refusing())
            .build();
    UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options), id);
    assertEquals(ErrorCode.RAND_WITH_FROM, refusal.code(), id);
  }
}
