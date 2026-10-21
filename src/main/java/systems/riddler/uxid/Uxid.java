package systems.riddler.uxid;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates UXIDs: prefixed, K-sortable identifiers a person can read, in the wire format of the
 * Elixir {@code uxid} package.
 *
 * <p>A generated id is an optional prefix and the delimiter, then a body: a time field (a standard
 * 48-bit time in 10 characters, or a compact 40-bit time in 8) followed by a random field, in
 * Crockford Base32. Among ids of one prefix, size and case with a standard time, an id made in a
 * later millisecond sorts after one made in an earlier millisecond, compared character by
 * character.
 *
 * <p>A deterministic id, asked for with {@link UxidOptions.Builder#from(String)}, has instead a
 * body of the marker {@code z} (or {@code Z}) and hash characters: the same prefix, input and size
 * always give the same id, and it sorts after every time-based id of its prefix and case.
 *
 * <pre>{@code
 * String id = Uxid.generate(); // a 26-character body, no prefix
 * String user = Uxid.generate(UxidOptions.builder().prefix("usr").size(Size.M).build());
 * String named =
 *     Uxid.generate(UxidOptions.builder().prefix("usr").from("alice@example.com").build());
 * }</pre>
 *
 * <p>Every method is static and safe to call from several threads at once; the class holds no
 * configuration.
 */
public final class Uxid {
  /** The widest random field at any size, in bytes. */
  static final int MAX_RAND_BYTES = 10;

  /** The deterministic marker's value, which a compact time may not start with. */
  private static final long MARKER = 31;

  private Uxid() {}

  /**
   * Generates an id with every default: no prefix, size {@link Size#XL} with a standard time read
   * from the clock now, lower case, and ten random bytes from {@link ByteSource#secureRandom()}.
   *
   * @return a 26-character id
   */
  public static String generate() {
    return generate(UxidOptions.builder().build());
  }

  /**
   * Generates an id from {@code options}.
   *
   * <p>The time is the options' time, or {@link System#currentTimeMillis()} read now. The random
   * field's width is the options' random width, or the width the size and time form give; its bytes
   * are the options' fixed bytes, or that many bytes from the options' byte source.
   *
   * <p>When the options carry a {@link UxidOptions.Builder#from(String) from} input, the id is
   * deterministic instead: the digest is SHA-256 over the prefix's UTF-8 bytes, a zero byte, the
   * input's UTF-8 bytes, a zero byte and one byte holding the hash character count of the size (9,
   * 13, 17, 21 or 25 for {@code xs} to {@code xl}), and the body is the marker followed by the
   * first count x 5 bits of the digest, 5 bits per character. The clock, the time form, the random
   * width and the byte source are not read.
   *
   * @param options the request
   * @return the id
   * @throws UxidException with {@link ErrorCode#COMPACT_TIME_OUT_OF_RANGE} for a compact time whose
   *     first character would be the deterministic marker; {@link ErrorCode#RAND_SIZE_TOO_LARGE}
   *     for a random width above 10 bytes; {@link ErrorCode#RAND_WIDTH_MISMATCH} for fixed random
   *     bytes whose count is not the random width; {@link ErrorCode#RAND_WITH_FROM} for fixed
   *     random bytes with a deterministic input, whatever their count
   * @throws IllegalArgumentException when the clock reads outside 0 to 2^48 - 1 milliseconds, or,
   *     for a deterministic id, when the prefix holds an unpaired surrogate and has no UTF-8
   *     encoding
   * @throws IllegalStateException when the byte source returns null or a count of bytes other than
   *     the one asked for
   * @throws NullPointerException when {@code options} is null
   */
  public static String generate(UxidOptions options) {
    byte[] from = options.fromUtf8();
    if (from != null) {
      if (options.hasRand()) {
        throw new UxidException(
            ErrorCode.RAND_WITH_FROM,
            "fixed random bytes and a deterministic input are mutually exclusive: a deterministic"
                + " id has no random field");
      }
      return deterministic(options, from);
    }

    Long fixedTime = options.time();
    long time = fixedTime == null ? UxidOptions.requireTime(System.currentTimeMillis()) : fixedTime;
    boolean compact = Boolean.TRUE.equals(options.compactTime());

    Integer explicitWidth = options.randSize();
    int width = explicitWidth == null ? options.size().randBytes(compact) : explicitWidth;
    if (width > MAX_RAND_BYTES) {
      throw new UxidException(
          ErrorCode.RAND_SIZE_TOO_LARGE,
          "the random field is at most " + MAX_RAND_BYTES + " bytes, got " + width);
    }
    byte[] rand = options.rand();
    if (rand != null && rand.length != width) {
      throw new UxidException(
          ErrorCode.RAND_WIDTH_MISMATCH,
          "fixed random bytes must be " + width + " bytes for this request, got " + rand.length);
    }

    long compactTime = time & ((1L << 40) - 1);
    if (compact && compactTime >>> 35 == MARKER) {
      throw new UxidException(
          ErrorCode.COMPACT_TIME_OUT_OF_RANGE,
          "a compact time from "
              + time
              + " ms would start with the deterministic marker; use a standard time");
    }
    if (rand == null) {
      rand = width == 0 ? new byte[0] : draw(options.byteSource(), width);
    }

    StringBuilder id = new StringBuilder(options.prefix().length() + 30);
    if (!options.prefix().isEmpty()) {
      id.append(options.prefix()).append(options.delimiter());
    }
    if (compact) {
      Base32.appendInteger(id, compactTime, 8, options.letterCase());
    } else {
      Base32.appendInteger(id, time, 10, options.letterCase());
    }
    Base32.appendBytes(id, rand, options.letterCase());
    return id.toString();
  }

  private static String deterministic(UxidOptions options, byte[] from) {
    int count = options.size().hashChars();
    MessageDigest sha256 = sha256();
    sha256.update(UxidOptions.utf8(options.prefix(), "the prefix"));
    sha256.update((byte) 0);
    sha256.update(from);
    sha256.update((byte) 0);
    sha256.update((byte) count);
    byte[] digest = sha256.digest();

    StringBuilder id = new StringBuilder(options.prefix().length() + 30);
    if (!options.prefix().isEmpty()) {
      id.append(options.prefix()).append(options.delimiter());
    }
    Base32.appendInteger(id, MARKER, 1, options.letterCase());
    Base32.appendLeadingBits(id, digest, count, options.letterCase());
    return id.toString();
  }

  /** A new SHA-256 digest; every Java platform is required to provide the algorithm. */
  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("this Java platform provides no SHA-256", e);
    }
  }

  private static byte[] draw(ByteSource source, int width) {
    byte[] bytes = source.nextBytes(width);
    if (bytes == null || bytes.length != width) {
      throw new IllegalStateException(
          "the byte source returned "
              + (bytes == null ? "null" : bytes.length + " bytes")
              + " when asked for "
              + width);
    }
    return bytes;
  }
}
