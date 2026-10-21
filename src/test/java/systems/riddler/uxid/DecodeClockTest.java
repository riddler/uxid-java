package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class DecodeClockTest {
  private static final String COMPACT_S = "usr_hf7yat0004hm5";
  private static final String GUARD = "usr_yzzzzzzz04hm5";
  private static final long WINDOW = 1L << 40;

  @Test // (sabotage: END minus a day dropped -> the END assertion failed)
  void theWindowEndsAreTheOnesTheCorpusReadmeStates() {
    assertEquals(1_699_913_600_000L, ClockWindow.START);
    assertEquals(2_164_577_117_183L, ClockWindow.END);
  }

  @Test // (sabotage: > ONE_DAY made >= -> the START assertion failed)
  void theWindowStartIsInclusiveAndTheMillisecondBeforeItMissesTheCase() {
    assertEquals(OptionalLong.of(ClockWindow.CASE_TIME), timeAt(COMPACT_S, ClockWindow.START));
    assertEquals(
        OptionalLong.of(ClockWindow.CASE_TIME - WINDOW), timeAt(COMPACT_S, ClockWindow.START - 1));
  }

  @Test // (sabotage: > ONE_DAY made >= -> the END - 1 assertion failed)
  void theWindowEndIsExclusiveAndItsLastMillisecondRecoversTheGuardCase() {
    assertEquals(OptionalLong.of(ClockWindow.GUARD_LAST_VALID), timeAt(GUARD, ClockWindow.END - 1));
    assertEquals(
        OptionalLong.of(WINDOW + ClockWindow.GUARD_LAST_VALID), timeAt(GUARD, ClockWindow.END));
  }

  @Test // (sabotage: time clamped at 0 -> the negative assertion failed)
  void aClockInTheFirstWindowStepsBackToANegativeTimeWithNoClamp() {
    long low = ClockWindow.CASE_TIME - WINDOW;
    assertEquals(OptionalLong.of(low - WINDOW), timeAt(COMPACT_S, 0));
    assertEquals(OptionalLong.of(-499_023_255_552L), timeAt(COMPACT_S, 0));
    assertEquals(OptionalLong.of(low), timeAt(COMPACT_S, low));
  }

  @Test // (sabotage: floorDiv made / -> the -WINDOW / 2 assertion failed)
  void aClockBeforeTheEpochReadsFromTheWindowEndingAtTheEpoch() {
    long low = ClockWindow.CASE_TIME - WINDOW;
    assertEquals(OptionalLong.of(low - WINDOW), timeAt(COMPACT_S, -1));
    assertEquals(OptionalLong.of(low - 2 * WINDOW), timeAt(COMPACT_S, -WINDOW / 2));
  }

  @Test // (sabotage: clock read up front -> the refusing clock failed it)
  void theClockIsReadOnlyForACompactBody() {
    DecodeOptions refusing = DecodeOptions.builder().clock(new RefusingClock()).build();
    assertEquals(
        OptionalLong.of(ClockWindow.CASE_TIME), decoded("usr_01hf7yat0004h1", refusing).time());
    assertEquals(OptionalLong.empty(), decoded("cus_zt9vnyfd85bff0", refusing).time());
    assertInstanceOf(DecodeResult.Invalid.class, Uxid.decode("usr_hf7yat0004hm", refusing));
  }

  private static OptionalLong timeAt(String id, long now) {
    DecodeOptions options =
        DecodeOptions.builder()
            .clock(Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
            .build();
    return decoded(id, options).time();
  }

  private static DecodeResult.Decoded decoded(String id, DecodeOptions options) {
    return assertInstanceOf(DecodeResult.Decoded.class, Uxid.decode(id, options), id);
  }

  /** A clock that fails the test if it is ever read. */
  private static final class RefusingClock extends Clock {
    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      throw new AssertionError("the clock was read");
    }

    @Override
    public long millis() {
      throw new AssertionError("the clock was read");
    }
  }
}
