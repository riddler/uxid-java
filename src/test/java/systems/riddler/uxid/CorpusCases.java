package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Runs one conformance case, given as its line of a case file, by the corpus README's rule for its
 * kind. Each case is read strictly (see {@link Fields}): a key the runner does not know fails the
 * case, and so does a missing one. A {@code null} or empty prefix in a case is no prefix. The
 * corpus does not fix the order in which a generation request's refusals are checked, so no case
 * here relies on one.
 */
final class CorpusCases {
  private CorpusCases() {}

  /** The id of a case line, for naming a test. */
  static String id(String line) {
    return Fields.parse(line).string("id");
  }

  /**
   * An encode case: the id built from {@code input} with the random field fixed to {@code rand}.
   */
  static void encode(String line) {
    Fields root = Fields.parse(line);
    String id = root.string("id");
    Fields input = root.object("input");
    UxidOptions.Builder builder =
        UxidOptions.builder()
            .size(Size.parse(input.string("size")))
            .compactTime(input.bool("compact"))
            .letterCase(letterCase(input.string("case")))
            .time(input.integer("time"))
            .rand(HexFormat.of().parseHex(input.string("rand")))
            .byteSource(TestSources.refusing());
    prefix(builder, input.nullableString("prefix"));
    String expected = root.string("expected");
    input.done();
    root.done();

    assertEquals(expected, Uxid.generate(builder.build()), id);
  }

  /** A deterministic case: the id of {@code input.from} under the prefix, size and case. */
  static void deterministic(String line) {
    Fields root = Fields.parse(line);
    String id = root.string("id");
    Fields input = root.object("input");
    UxidOptions.Builder builder =
        UxidOptions.builder()
            .from(input.string("from"))
            .size(Size.parse(input.string("size")))
            .letterCase(letterCase(input.string("case")))
            .byteSource(TestSources.refusing());
    prefix(builder, input.nullableString("prefix"));
    String expected = root.string("expected");
    input.done();
    root.done();

    assertEquals(expected, Uxid.generate(builder.build()), id);
  }

  /**
   * A decode case, run at both ends of the decoder clock window the corpus README gives for its
   * compact cases: the first clock inclusive and the last before the end.
   */
  static void decode(String line) {
    Fields root = Fields.parse(line);
    String id = root.string("id");
    String text = root.string("input");
    Fields answer = root.object("expected");
    Decoded expected =
        new Decoded(
            answer.nullableString("prefix"),
            Size.parse(answer.string("size")),
            answer.bool("compact"),
            answer.bool("deterministic"),
            answer.nullableInteger("time"));
    answer.done();
    root.done();

    for (long now : new long[] {ClockWindow.START, ClockWindow.END - 1}) {
      DecodeOptions options =
          DecodeOptions.builder()
              .clock(Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
              .build();
      DecodeResult.Decoded decoded =
          assertInstanceOf(DecodeResult.Decoded.class, Uxid.decode(text, options), id);
      assertEquals(expected.prefix(), decoded.prefix().orElse(null), id + " prefix at " + now);
      assertEquals(expected.size(), decoded.size(), id + " size at " + now);
      assertEquals(expected.compact(), decoded.compact(), id + " compact at " + now);
      assertEquals(
          expected.deterministic(), decoded.deterministic(), id + " deterministic at " + now);
      assertEquals(expected.time(), timeOf(decoded.time()), id + " time at " + now);
    }
  }

  /**
   * An invalid case: a string the decoder refuses with the reason, or generation options refused
   * with the error, by name.
   */
  static void invalid(String line) {
    Fields root = Fields.parse(line);
    String id = root.string("id");
    Fields input = root.object("input");
    String expectedError = root.string("expected_error");
    root.done();

    if (input.has("decode")) {
      String text = input.string("decode");
      input.done();
      DecodeResult.Invalid invalid =
          assertInstanceOf(DecodeResult.Invalid.class, Uxid.decode(text), id);
      assertEquals(expectedError, invalid.reason().wireName(), id);
      return;
    }

    Fields request = input.object("generate");
    input.done();
    UxidOptions.Builder builder = UxidOptions.builder().byteSource(TestSources.refusing());
    prefix(builder, request.optionalNullableString("prefix"));
    String size = request.optionalString("size");
    if (size != null) {
      builder.size(Size.parse(size));
    }
    Boolean compact = request.optionalBool("compact");
    if (compact != null) {
      builder.compactTime(compact);
    }
    String letterCase = request.optionalString("case");
    if (letterCase != null) {
      builder.letterCase(letterCase(letterCase));
    }
    Long time = request.optionalInteger("time");
    if (time != null) {
      builder.time(time);
    }
    String rand = request.optionalString("rand");
    if (rand != null) {
      builder.rand(HexFormat.of().parseHex(rand));
    }
    Long randSize = request.optionalInteger("rand_size");
    if (randSize != null) {
      builder.randSize(Math.toIntExact(randSize));
    }
    String from = request.optionalString("from");
    if (from != null) {
      builder.from(from);
    }
    Boolean monotonic = request.optionalBool("monotonic");
    request.done();

    UxidOptions options = builder.build();
    UxidException refusal =
        assertThrows(
            UxidException.class,
            () -> {
              if (Boolean.TRUE.equals(monotonic)) {
                new Monotonic(TestSources.refusingDraws()).generate(options);
              } else {
                Uxid.generate(options);
              }
            },
            id);
    assertEquals(expectedError, refusal.code().wireName(), id);
  }

  /** A monotonic case: one generator replayed with the case's draws, called once per time. */
  static void monotonic(String line) {
    Fields root = Fields.parse(line);
    String id = root.string("id");
    Fields input = root.object("input");
    boolean compact = input.bool("compact");
    String prefix = input.nullableString("prefix");
    Size size = Size.parse(input.string("size"));
    List<Long> times = input.integers("times");
    List<Long> seeds = new ArrayList<>(List.of(input.integer("initial_seed")));
    List<Long> steps = input.integers("steps");
    Long overflowSeed = input.nullableInteger("overflow_seed");
    if (overflowSeed != null) {
      seeds.add(overflowSeed);
    }
    List<String> expected = root.strings("expected");
    input.done();
    root.done();

    TestSources.Replay draws = new TestSources.Replay(seeds, steps);
    Monotonic generator = new Monotonic(draws);
    List<String> ids = new ArrayList<>();
    for (long time : times) {
      UxidOptions.Builder builder =
          UxidOptions.builder()
              .size(size)
              .compactTime(compact)
              .time(time)
              .byteSource(TestSources.refusing());
      prefix(builder, prefix);
      ids.add(generator.generate(builder.build()));
    }

    assertEquals(expected, ids, id);
    assertTrue(draws.drained(), id + ": every draw the case carries is drawn");
  }

  private record Decoded(
      String prefix, Size size, boolean compact, boolean deterministic, Long time) {}

  private static Long timeOf(OptionalLong time) {
    return time.isPresent() ? Long.valueOf(time.getAsLong()) : null;
  }

  /** A null or empty prefix is no prefix, which is the builder's default. */
  private static void prefix(UxidOptions.Builder builder, String prefix) {
    builder.prefix(Optional.ofNullable(prefix).orElse(""));
  }

  private static LetterCase letterCase(String name) {
    return switch (name) {
      case "lower" -> LetterCase.LOWER;
      case "upper" -> LetterCase.UPPER;
      default -> throw new AssertionError("not a case: " + name);
    };
  }
}
