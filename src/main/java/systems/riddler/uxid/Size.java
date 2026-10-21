package systems.riddler.uxid;

import java.util.Objects;

/**
 * The size of an id: with the time form, it fixes the random field's width and the body length.
 *
 * <p>The table, from the wire format, gives the random field's width in bytes and the body length
 * in characters for a standard time (48 bits, 10 characters) and for a compact time (the low 40
 * bits, 8 characters). Compact time hands the 8 bits it frees to the random field, except at {@link
 * #XL}, whose random field is already at the 10-byte cap.
 *
 * <table>
 *   <caption>Random bytes and body length per size</caption>
 *   <tr><th>Size</th><th>Standard random bytes</th><th>Standard length</th>
 *       <th>Compact random bytes</th><th>Compact length</th></tr>
 *   <tr><td>{@code xs}</td><td>0</td><td>10</td><td>1</td><td>10</td></tr>
 *   <tr><td>{@code s}</td><td>2</td><td>14</td><td>3</td><td>13</td></tr>
 *   <tr><td>{@code m}</td><td>5</td><td>18</td><td>6</td><td>18</td></tr>
 *   <tr><td>{@code l}</td><td>7</td><td>22</td><td>8</td><td>21</td></tr>
 *   <tr><td>{@code xl}</td><td>10</td><td>26</td><td>10</td><td>24</td></tr>
 * </table>
 *
 * <p>The default size is {@link #XL}.
 */
public enum Size {
  /** Extra small: no random bytes with a standard time, one with a compact time. */
  XS("xs", "xsmall", 0, 1),
  /** Small: two random bytes with a standard time, three with a compact time. */
  S("s", "small", 2, 3),
  /** Medium: five random bytes with a standard time, six with a compact time. */
  M("m", "medium", 5, 6),
  /** Large: seven random bytes with a standard time, eight with a compact time. */
  L("l", "large", 7, 8),
  /** Extra large, the default: ten random bytes with either time form. */
  XL("xl", "xlarge", 10, 10);

  private final String wireName;
  private final String longName;
  private final int standardRandBytes;
  private final int compactRandBytes;

  Size(String wireName, String longName, int standardRandBytes, int compactRandBytes) {
    this.wireName = wireName;
    this.longName = longName;
    this.standardRandBytes = standardRandBytes;
    this.compactRandBytes = compactRandBytes;
  }

  /**
   * Returns the size's short spelling, the one the conformance corpus uses: {@code xs}, {@code s},
   * {@code m}, {@code l} or {@code xl}.
   *
   * @return the short spelling
   */
  public String wireName() {
    return wireName;
  }

  /**
   * Returns the size a spelling names: a short spelling ({@code xs}, {@code s}, {@code m}, {@code
   * l}, {@code xl}) or a long one ({@code xsmall}, {@code small}, {@code medium}, {@code large},
   * {@code xlarge}). The spellings are lower case and matched exactly.
   *
   * @param name the spelling
   * @return the size it names
   * @throws IllegalArgumentException when {@code name} is not one of the ten spellings
   * @throws NullPointerException when {@code name} is null
   */
  public static Size parse(String name) {
    Objects.requireNonNull(name, "name");
    for (Size size : values()) {
      if (size.wireName.equals(name) || size.longName.equals(name)) {
        return size;
      }
    }
    throw new IllegalArgumentException("not a size: \"" + name + "\"");
  }

  /** The random field's width in bytes for this size and time form. */
  int randBytes(boolean compact) {
    return compact ? compactRandBytes : standardRandBytes;
  }
}
