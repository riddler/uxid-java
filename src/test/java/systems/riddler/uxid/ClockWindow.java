package systems.riddler.uxid;

/**
 * The decoder clocks the compact decode cases of the conformance corpus hold for, computed with the
 * arithmetic of the corpus README ("Compact times and the decoder's clock") at uxid-ex v2.10.0.
 */
final class ClockWindow {
  /** One day in milliseconds, how far ahead of the clock a compact time may be. */
  static final long ONE_DAY = 86_400_000L;

  /** The time the corpus's compact cases carry, except the guard case. */
  static final long CASE_TIME = 1_700_000_000_000L;

  /** The largest compact time field the guard allows, the guard case's low 40 bits. */
  static final long GUARD_LAST_VALID = 1_065_151_889_407L;

  /**
   * The first clock that recovers every case, inclusive: a clock no more than one day behind the
   * cases at {@link #CASE_TIME}.
   */
  static final long START = CASE_TIME - ONE_DAY;

  /**
   * The first clock past the window, exclusive: from here 2^40 + {@link #GUARD_LAST_VALID} is no
   * longer more than one day ahead of the clock, so the guard case is not stepped back.
   */
  static final long END = (1L << 40) + GUARD_LAST_VALID - ONE_DAY;

  private ClockWindow() {}
}
