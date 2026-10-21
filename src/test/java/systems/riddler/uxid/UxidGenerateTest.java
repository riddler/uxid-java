package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class UxidGenerateTest {
  private static final long TIME = 1_700_000_000_000L;
  private static final String LOWER = "0123456789abcdefghjkmnpqrstvwxyz";

  @Test // (sabotage: default size L -> the length assertion failed)
  void theDefaultIdIsAnXlBodyWithNoPrefixAtTheClockTime() {
    long before = System.currentTimeMillis();
    String id = Uxid.generate();
    long after = System.currentTimeMillis();

    assertEquals(26, id.length(), id);
    assertTrue(id.chars().allMatch(c -> LOWER.indexOf(c) >= 0), id);
    long time = standardTime(id);
    assertTrue(time >= before && time <= after, id);
  }

  @Test // (sabotage: clock read replaced by 0 -> the time range assertion failed)
  void anOptionsRequestWithNoTimeReadsTheClockAtTheCall() {
    UxidOptions options = UxidOptions.builder().prefix("usr").size(Size.XS).build();
    long before = System.currentTimeMillis();
    String id = Uxid.generate(options);
    long after = System.currentTimeMillis();

    assertTrue(id.startsWith("usr_"), id);
    long time = standardTime(id.substring(4));
    assertTrue(time >= before && time <= after, id);
  }

  @Test // (sabotage: draw(source, width + 1) at the call site -> the id assertion failed)
  void theByteSourceIsAskedForTheRandomWidthAndFillsTheField() {
    TestSources.Counting source = new TestSources.Counting();
    UxidOptions options =
        UxidOptions.builder().prefix("cus").size(Size.S).time(TIME).byteSource(source).build();

    assertEquals("cus_01hf7yat000001", Uxid.generate(options));
    assertEquals(List.of(2), source.counts);
  }

  @Test // (sabotage: zero-width shortcut removed -> the refusing source was called)
  void theByteSourceIsNotCalledForAZeroWidthField() {
    UxidOptions standardXs =
        UxidOptions.builder().size(Size.XS).time(TIME).byteSource(TestSources.refusing()).build();
    UxidOptions zeroWidth =
        UxidOptions.builder().randSize(0).time(TIME).byteSource(TestSources.refusing()).build();

    assertEquals("01hf7yat00", Uxid.generate(standardXs));
    assertEquals("01hf7yat00", Uxid.generate(zeroWidth));
  }

  @Test // (sabotage: draw length check never fires -> no IllegalStateException thrown)
  void aByteSourceAnsweringTheWrongCountIsRefused() {
    UxidOptions shortAnswer =
        UxidOptions.builder().time(TIME).byteSource(count -> new byte[count - 1]).build();
    UxidOptions nullAnswer = UxidOptions.builder().time(TIME).byteSource(count -> null).build();

    assertThrows(IllegalStateException.class, () -> Uxid.generate(shortAnswer));
    assertThrows(IllegalStateException.class, () -> Uxid.generate(nullAnswer));
  }

  @Test // (sabotage: explicit randSize ignored -> the xl width was drawn, id assertion failed)
  void anExplicitRandomWidthOverridesTheSize() {
    TestSources.Counting source = new TestSources.Counting();
    UxidOptions options =
        UxidOptions.builder().size(Size.XL).randSize(3).time(TIME).byteSource(source).build();

    assertEquals("01hf7yat00000g2", Uxid.generate(options));
    assertEquals(List.of(3), source.counts);
  }

  @Test // (sabotage: compact widths made the standard ones -> the counts assertion failed)
  void compactTimeGivesTheCompactWidthOfEachSize() {
    int[] expected = {1, 3, 6, 8, 10};
    Size[] sizes = Size.values();
    for (int i = 0; i < sizes.length; i++) {
      TestSources.Counting source = new TestSources.Counting();
      UxidOptions options =
          UxidOptions.builder()
              .size(sizes[i])
              .compactTime(true)
              .time(TIME)
              .byteSource(source)
              .build();
      String id = Uxid.generate(options);
      assertEquals(List.of(expected[i]), source.counts, sizes[i].wireName());
      assertTrue(id.startsWith("hf7yat00"), id);
    }
  }

  @Test // (sabotage: draw moved before the compact guard -> the refusing source was called)
  void aCompactTimeRefusalComesBeforeAnyDraw() {
    UxidOptions options =
        UxidOptions.builder()
            .size(Size.S)
            .compactTime(true)
            .time(1_065_151_889_408L)
            .byteSource(TestSources.refusing())
            .build();

    UxidException refusal = assertThrows(UxidException.class, () -> Uxid.generate(options));
    assertEquals(ErrorCode.COMPACT_TIME_OUT_OF_RANGE, refusal.code());
  }

  @Test // (sabotage: standard time also guarded -> assertDoesNotThrow failed)
  void aStandardTimeIsNeverRefusedForTheMarker() {
    UxidOptions options =
        UxidOptions.builder()
            .size(Size.XS)
            .compactTime(false)
            .time(1_065_151_889_408L)
            .byteSource(TestSources.refusing())
            .build();

    assertEquals("00z0000000", assertDoesNotThrow(() -> Uxid.generate(options)));
  }

  @Test // (sabotage: letter case applied to the prefix -> the prefix assertion failed)
  void letterCaseAppliesToTheBodyAndNeverToThePrefix() {
    byte[] rand = HexFormat.of().parseHex("0123");
    UxidOptions upper =
        UxidOptions.builder()
            .prefix("usr")
            .size(Size.S)
            .letterCase(LetterCase.UPPER)
            .rand(rand)
            .time(TIME)
            .build();
    UxidOptions lower =
        UxidOptions.builder().prefix("Usr").size(Size.S).rand(rand).time(TIME).build();

    assertEquals("usr_01HF7YAT0004H1", Uxid.generate(upper));
    assertEquals("Usr_01hf7yat0004h1", Uxid.generate(lower));
  }

  @Test // (sabotage: the delimiter option ignored -> the id carried _ and failed)
  void theDelimiterJoinsThePrefixAndTheBody() {
    byte[] rand = HexFormat.of().parseHex("0123");
    UxidOptions.Builder dash =
        UxidOptions.builder().prefix("usr").delimiter("-").size(Size.S).rand(rand).time(TIME);
    UxidOptions.Builder twoChars =
        UxidOptions.builder().prefix("usr").delimiter("::").size(Size.S).rand(rand).time(TIME);

    assertEquals("usr-01hf7yat0004h1", Uxid.generate(dash.build()));
    assertEquals("usr::01hf7yat0004h1", Uxid.generate(twoChars.build()));
  }

  @Test // (sabotage: prefix emitted lower-cased -> the multibyte prefix assertion failed)
  void thePrefixIsEmittedAsGiven() {
    UxidOptions options = UxidOptions.builder().prefix("Café").size(Size.XS).time(TIME).build();

    assertEquals("Café_01hf7yat00", Uxid.generate(options));
  }

  @Test // (sabotage: rand setter stores the array uncopied -> the id changed, assertion failed)
  void fixedBytesAreCopiedWhenSet() {
    byte[] rand = HexFormat.of().parseHex("0123");
    UxidOptions.Builder builder =
        UxidOptions.builder().prefix("usr").size(Size.S).rand(rand).time(TIME);
    UxidOptions options = builder.build();
    rand[0] = (byte) 0xff;

    assertEquals("usr_01hf7yat0004h1", Uxid.generate(options));
    assertEquals("usr_01hf7yat0004h1", Uxid.generate(builder.build()));
  }

  @Test // (sabotage: time range check never fires -> no IllegalArgumentException thrown)
  void aTimeOutsideFortyEightBitsIsRefusedWhenSet() {
    UxidOptions.Builder builder = UxidOptions.builder();

    assertThrows(IllegalArgumentException.class, () -> builder.time(-1));
    assertThrows(IllegalArgumentException.class, () -> builder.time(1L << 48));
    assertEquals("7zzzzzzzzz", Uxid.generate(builder.size(Size.XS).time((1L << 48) - 1).build()));
  }

  @Test // (sabotage: empty delimiter check never fires -> no exception was thrown)
  void aSetterRefusesAValueWrongOnItsOwn() {
    UxidOptions.Builder builder = UxidOptions.builder();

    assertThrows(IllegalArgumentException.class, () -> builder.delimiter(""));
    assertThrows(IllegalArgumentException.class, () -> builder.randSize(-1));
    assertThrows(NullPointerException.class, () -> builder.prefix(null));
    assertThrows(NullPointerException.class, () -> builder.size(null));
    assertThrows(NullPointerException.class, () -> builder.letterCase(null));
    assertThrows(NullPointerException.class, () -> builder.delimiter(null));
    assertThrows(NullPointerException.class, () -> builder.rand(null));
    assertThrows(NullPointerException.class, () -> builder.byteSource(null));
  }

  @Test // (sabotage: rand_size_too_large as a plain IAE -> the instance check failed)
  void aRefusalIsAnIllegalArgumentExceptionCarryingItsCode() {
    UxidOptions options = UxidOptions.builder().randSize(11).time(TIME).build();

    IllegalArgumentException refusal =
        assertThrows(IllegalArgumentException.class, () -> Uxid.generate(options));
    assertTrue(refusal instanceof UxidException, refusal.toString());
    assertEquals(ErrorCode.RAND_SIZE_TOO_LARGE, ((UxidException) refusal).code());
    assertTrue(refusal.getMessage().startsWith("rand_size_too_large: "), refusal.getMessage());
  }

  @Test // (sabotage: default source fills a discarded array -> the two draws were equal)
  void theDefaultSourceDrawsFreshBytesFromOneSharedInstance() {
    ByteSource source = ByteSource.secureRandom();
    byte[] first = source.nextBytes(10);
    byte[] second = source.nextBytes(10);

    assertSame(source, ByteSource.secureRandom());
    assertEquals(10, first.length);
    assertTrue(!java.util.Arrays.equals(first, second), "two draws of 80 bits were equal");
  }

  @Test // (sabotage: prefix UTF-8 encoded in the time path -> assertDoesNotThrow failed)
  void aTimeBasedIdKeepsAPrefixWithNoUtf8EncodingAsGiven() {
    TestSources.Counting source = new TestSources.Counting();
    UxidOptions options =
        UxidOptions.builder().prefix("us\uD800").size(Size.S).time(TIME).byteSource(source).build();

    assertEquals("us\uD800_01hf7yat000001", assertDoesNotThrow(() -> Uxid.generate(options)));
  }

  /** Reads the standard time from the first 10 characters of a body. */
  private static long standardTime(String body) {
    long time = 0;
    for (int i = 0; i < 10; i++) {
      time = time * 32 + LOWER.indexOf(body.charAt(i));
    }
    return time;
  }
}
