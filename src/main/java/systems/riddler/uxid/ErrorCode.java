package systems.riddler.uxid;

/**
 * The generation errors of the wire format, a closed set. Each constant's {@link #wireName()} is
 * the name the wire format and the conformance corpus give the refusal.
 *
 * <p>A {@link UxidException} carries one of these as its {@link UxidException#code() code}.
 */
public enum ErrorCode {
  /**
   * A compact time whose first character would be the deterministic marker: the low 40 bits of the
   * time are at or above 31 x 2^35.
   */
  COMPACT_TIME_OUT_OF_RANGE("compact_time_out_of_range"),
  /** Fixed random bytes whose count is not the request's random width. */
  RAND_WIDTH_MISMATCH("rand_width_mismatch"),
  /** An explicit random width above 10 bytes. */
  RAND_SIZE_TOO_LARGE("rand_size_too_large"),
  /** A deterministic body asked for with monotonic generation. */
  FROM_WITH_MONOTONIC("from_with_monotonic"),
  /** Fixed random bytes asked for with a deterministic body, which has no random field. */
  RAND_WITH_FROM("rand_with_from"),
  /** Fixed random bytes asked for with monotonic generation, which chooses the field itself. */
  RAND_WITH_MONOTONIC("rand_with_monotonic");

  private final String wireName;

  ErrorCode(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the error's name in the wire format's closed set, such as {@code rand_width_mismatch}.
   *
   * @return the wire name
   */
  public String wireName() {
    return wireName;
  }
}
