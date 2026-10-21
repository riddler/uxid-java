package systems.riddler.uxid;

/**
 * Thrown when a generation request contradicts itself or the wire format. Its {@link #code()} is
 * the refusal's name in the wire format's closed set of generation errors.
 *
 * <p>An option value that is wrong on its own (a null, an empty delimiter, a negative random width,
 * a time outside 48 bits) is refused when it is set, with a plain {@link IllegalArgumentException}
 * or {@link NullPointerException}, not with this exception.
 */
public final class UxidException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  /** The refusal's name. */
  private final ErrorCode code;

  UxidException(ErrorCode code, String detail) {
    super(code.wireName() + ": " + detail);
    this.code = code;
  }

  /**
   * Returns the refusal's name in the wire format's closed set of generation errors.
   *
   * @return the error code
   */
  public ErrorCode code() {
    return code;
  }
}
