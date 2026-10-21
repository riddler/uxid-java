package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class UxidDecodeTest {
  private static final long TIME = 1_700_000_000_000L;
  private static final Clock AT_TIME = Clock.fixed(Instant.ofEpochMilli(TIME), ZoneOffset.UTC);

  @Test // (sabotage: wrong wire name on INVALID_CHARACTER -> it failed)
  void theReasonsAreTheWireNamesInRefusalOrder() {
    assertEquals(
        List.of(InvalidReason.EMPTY, InvalidReason.INVALID_LENGTH, InvalidReason.INVALID_CHARACTER),
        List.of(InvalidReason.values()));
    assertEquals("empty", InvalidReason.EMPTY.wireName());
    assertEquals("invalid_length", InvalidReason.INVALID_LENGTH.wireName());
    assertEquals("invalid_character", InvalidReason.INVALID_CHARACTER.wireName());
  }

  @Test // (sabotage: surrogate bound check removed -> assertDoesNotThrow failed)
  void malformedInputIsAnsweredNeverThrown() {
    List<String> inputs =
        List.of(
            "\ud800",
            "usr_\udc00",
            "usr_01hf7yat0\ud83d\ude00",
            "usr_01hf7yat00\ud83d",
            "__________",
            "usr_" + "0".repeat(10_000),
            "usr_01hf7yat0\u0000",
            "usr_\u00e9\u00e9\u00e9\u00e9\u00e9");
    for (String input : inputs) {
      DecodeResult result = assertDoesNotThrow(() -> Uxid.decode(input), input);
      assertInstanceOf(DecodeResult.Invalid.class, result, input);
    }
  }

  @Test // (sabotage: 2-byte char counted as 1 -> the first assertion failed)
  void theBodyLengthIsCountedInUtf8Bytes() {
    assertEquals(reason(InvalidReason.INVALID_CHARACTER), Uxid.decode("01hf7yat\u00e9"));
    assertEquals(reason(InvalidReason.INVALID_CHARACTER), Uxid.decode("01hf7ya\u20ac"));
    assertEquals(reason(InvalidReason.INVALID_CHARACTER), Uxid.decode("01hf7y\ud83d\ude00"));
    assertEquals(reason(InvalidReason.INVALID_CHARACTER), Uxid.decode("01hf7ya\ud800"));
  }

  @Test // (sabotage: length 21 not compact -> the l compact assertion failed)
  void aGeneratedIdDecodesToItsSizeAndTimeForm() {
    for (Size size : Size.values()) {
      for (boolean compact : new boolean[] {false, true}) {
        String id =
            Uxid.generate(
                UxidOptions.builder()
                    .prefix("cus")
                    .size(size)
                    .compactTime(compact)
                    .time(TIME)
                    .build());
        DecodeResult.Decoded decoded = decoded(id);
        boolean ambiguous = compact && (size == Size.XS || size == Size.M);
        assertEquals(size, decoded.size(), id);
        assertEquals(compact && !ambiguous, decoded.compact(), id);
        assertEquals(Optional.of("cus"), decoded.prefix(), id);
        assertFalse(decoded.deterministic(), id);
        if (!ambiguous) {
          assertEquals(OptionalLong.of(TIME), decoded.time(), id);
        }
      }
    }
  }

  @Test // (sabotage: length 18 read compact -> the compact assertion failed)
  void bodyLengthsTenAndEighteenDecodeToTheStandardReading() {
    for (Size size : List.of(Size.XS, Size.M)) {
      String id =
          Uxid.generate(UxidOptions.builder().size(size).compactTime(true).time(TIME).build());
      DecodeResult.Decoded decoded = decoded(id);
      assertEquals(size, decoded.size(), id);
      assertFalse(decoded.compact(), id);
      long standard = (Base32.value(id.charAt(0)) & 7L) << 45;
      for (int i = 1; i < 10; i++) {
        standard |= (long) Base32.value(id.charAt(i)) << (5 * (9 - i));
      }
      assertEquals(OptionalLong.of(standard), decoded.time(), id);
    }
  }

  @Test // (sabotage: upper-case folding removed -> the equality failed)
  void eitherCaseDecodesToTheSameValue() {
    assertEquals(decoded("usr_hf7yat0004hm5"), decoded("usr_HF7YAT0004HM5"));
    assertEquals(decoded("usr_01hf7yat0004h1"), decoded("usr_01Hf7YaT0004h1"));
  }

  @Test // (sabotage: scan resumed at found + 1 -> the --- assertion failed)
  void theDelimiterIsTheOptionsOneSplitWithoutOverlap() {
    DecodeOptions dashes = DecodeOptions.builder().delimiter("--").clock(AT_TIME).build();
    DecodeResult.Decoded decoded = decoded("usr--01hf7yat00", dashes);
    assertEquals(Optional.of("usr"), decoded.prefix());
    assertEquals(Optional.of("a--b"), decoded("a--b--01hf7yat00", dashes).prefix());
    assertEquals(reason(InvalidReason.INVALID_LENGTH), Uxid.decode("usr---01hf7yat00", dashes));
    assertEquals(reason(InvalidReason.INVALID_LENGTH), Uxid.decode("usr--01hf7yat00"));
  }

  @Test // (sabotage: at <= 0 read as no prefix -> the first assertion failed)
  void anIdThatStartsWithTheDelimiterHasTheEmptyPrefix() {
    assertEquals(Optional.of(""), decoded("_01hf7yat00").prefix());
    assertEquals(Optional.empty(), decoded("01hf7yat00").prefix());
  }

  @Test // (sabotage: clock null check removed -> assertThrows failed)
  void theOptionsRefuseAnEmptyOrNullDelimiterAndANullClock() {
    assertThrows(IllegalArgumentException.class, () -> DecodeOptions.builder().delimiter(""));
    assertThrows(NullPointerException.class, () -> DecodeOptions.builder().delimiter(null));
    assertThrows(NullPointerException.class, () -> DecodeOptions.builder().clock(null));
    assertThrows(NullPointerException.class, () -> Uxid.decode(null));
    assertThrows(NullPointerException.class, () -> Uxid.decode("01hf7yat00", null));
  }

  @Test // (sabotage: size null check removed -> assertThrows failed)
  void theResultRecordsRefuseNullComponents() {
    assertThrows(NullPointerException.class, () -> new DecodeResult.Invalid(null));
    assertThrows(
        NullPointerException.class,
        () -> new DecodeResult.Decoded(null, Size.S, false, false, OptionalLong.empty()));
    assertThrows(
        NullPointerException.class,
        () -> new DecodeResult.Decoded(Optional.empty(), null, false, false, OptionalLong.empty()));
    assertThrows(
        NullPointerException.class,
        () -> new DecodeResult.Decoded(Optional.empty(), Size.S, false, false, null));
    assertTrue(new DecodeResult.Invalid(InvalidReason.EMPTY).equals(reason(InvalidReason.EMPTY)));
  }

  private static DecodeResult reason(InvalidReason reason) {
    return new DecodeResult.Invalid(reason);
  }

  private static DecodeResult.Decoded decoded(String id) {
    return decoded(id, DecodeOptions.builder().clock(AT_TIME).build());
  }

  private static DecodeResult.Decoded decoded(String id, DecodeOptions options) {
    return assertInstanceOf(DecodeResult.Decoded.class, Uxid.decode(id, options), id);
  }
}
