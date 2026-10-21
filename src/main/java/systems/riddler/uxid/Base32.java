package systems.riddler.uxid;

/**
 * Crockford Base32 as the wire format writes it: most significant bit first, 5 bits per character,
 * with no padding. A group shorter than 5 bits is written as its raw value.
 */
final class Base32 {
  private static final String LOWER = "0123456789abcdefghjkmnpqrstvwxyz";
  private static final String UPPER = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private Base32() {}

  private static String alphabet(LetterCase letterCase) {
    return letterCase == LetterCase.UPPER ? UPPER : LOWER;
  }

  /**
   * Appends {@code chars} characters holding {@code value}, whose width is at most {@code chars x
   * 5} bits; the first group is the short one when the width is not a multiple of 5.
   */
  static void appendInteger(StringBuilder out, long value, int chars, LetterCase letterCase) {
    String alphabet = alphabet(letterCase);
    for (int i = chars - 1; i >= 0; i--) {
      out.append(alphabet.charAt((int) ((value >>> (5 * i)) & 31)));
    }
  }

  /** Appends {@code bytes} as {@code ceil(8 x n / 5)} characters, the short group last. */
  static void appendBytes(StringBuilder out, byte[] bytes, LetterCase letterCase) {
    String alphabet = alphabet(letterCase);
    int buffer = 0;
    int bits = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        out.append(alphabet.charAt((buffer >>> bits) & 31));
      }
      buffer &= (1 << bits) - 1;
    }
    if (bits > 0) {
      out.append(alphabet.charAt(buffer));
    }
  }

  /**
   * Appends the first {@code chars x 5} bits of {@code bytes} as {@code chars} characters, with no
   * short group; {@code bytes} holds at least that many bits.
   */
  static void appendLeadingBits(StringBuilder out, byte[] bytes, int chars, LetterCase letterCase) {
    String alphabet = alphabet(letterCase);
    int buffer = 0;
    int bits = 0;
    int next = 0;
    for (int i = 0; i < chars; i++) {
      if (bits < 5) {
        buffer = (buffer << 8) | (bytes[next++] & 0xff);
        bits += 8;
      }
      bits -= 5;
      out.append(alphabet.charAt((buffer >>> bits) & 31));
      buffer &= (1 << bits) - 1;
    }
  }
}
