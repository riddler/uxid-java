package systems.riddler.uxid;

/**
 * Why {@link Uxid#decode(String, DecodeOptions) decode} refused a string: the decode refusals of
 * the wire format, a closed set. Each constant's {@link #wireName()} is the name the wire format
 * and the conformance corpus give the refusal.
 *
 * <p>The constants are declared in the order decode checks them: a body that breaks more than one
 * rule is refused for the first it breaks.
 */
public enum InvalidReason {
  /** The body is empty: an empty string, or one that ends in the delimiter. */
  EMPTY("empty"),
  /**
   * The body's length in UTF-8 bytes is not one of the body lengths a producer emits: 10, 13, 14,
   * 18, 21, 22, 24 or 26.
   */
  INVALID_LENGTH("invalid_length"),
  /** A body character is not a Crockford Base32 symbol in either case. */
  INVALID_CHARACTER("invalid_character");

  private final String wireName;

  InvalidReason(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the refusal's name in the wire format's closed set, such as {@code invalid_length}.
   *
   * @return the wire name
   */
  public String wireName() {
    return wireName;
  }
}
