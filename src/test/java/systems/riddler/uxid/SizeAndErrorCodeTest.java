package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeAndErrorCodeTest {

  @ParameterizedTest(name = "{0} is {1}") // (sabotage: long spellings unread -> 5 cases failed)
  @CsvSource({
    "xs, XS", "xsmall, XS", "s, S", "small, S", "m, M",
    "medium, M", "l, L", "large, L", "xl, XL", "xlarge, XL",
  })
  void bothSpellingsOfASizeParse(String spelling, Size size) {
    assertSame(size, assertDoesNotThrow(() -> Size.parse(spelling)));
  }

  @Test // (sabotage: parse falls back to XL -> no IllegalArgumentException was thrown)
  void anUnknownSpellingIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> Size.parse("XL"));
    assertThrows(IllegalArgumentException.class, () -> Size.parse("xxl"));
    assertThrows(NullPointerException.class, () -> Size.parse(null));
  }

  @Test // (sabotage: XL's wire name xlarge -> the list assertion failed)
  void aSizesWireNameIsTheCorpusSpelling() {
    List<String> names = Arrays.stream(Size.values()).map(Size::wireName).toList();
    assertEquals(List.of("xs", "s", "m", "l", "xl"), names);
  }

  @Test // (sabotage: rand_with_from renamed rand_with_hash -> the set assertion failed)
  void theErrorCodesAreTheClosedSetOfGenerationErrors() {
    List<String> names =
        Arrays.stream(ErrorCode.values()).map(ErrorCode::wireName).collect(Collectors.toList());
    assertEquals(
        List.of(
            "compact_time_out_of_range",
            "rand_width_mismatch",
            "rand_size_too_large",
            "from_with_monotonic",
            "rand_with_from",
            "rand_with_monotonic"),
        names);
  }
}
