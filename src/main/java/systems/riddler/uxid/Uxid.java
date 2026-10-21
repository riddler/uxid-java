package systems.riddler.uxid;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Generates and decodes UXIDs: prefixed, K-sortable identifiers a person can read, in the wire
 * format of the Elixir {@code uxid} package.
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
 * <p>{@link #decode(String, DecodeOptions) Decoding} reads an id back to what it says about itself
 * and answers a {@link DecodeResult} rather than throwing on malformed input; {@link
 * #isValid(String, String, String)} and {@link #isDeterministic(String, String)} answer the two
 * questions most callers ask of a string.
 *
 * <p>Ids that must increase strictly within one millisecond come from a {@link Monotonic}
 * generator, an object the caller holds with its own state.
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

  /**
   * Decodes {@code id} under the delimiter {@code _}, reading a compact time against the system
   * clock in UTC: {@link #decode(String, DecodeOptions)} with the default {@link DecodeOptions}.
   *
   * @param id the string to decode
   * @return a {@link DecodeResult.Decoded} for a well-formed id, or a {@link DecodeResult.Invalid}
   *     naming the first rule the string breaks
   * @throws NullPointerException when {@code id} is null
   */
  public static DecodeResult decode(String id) {
    return decode(id, DecodeOptions.DEFAULTS);
  }

  /**
   * Decodes {@code id} under the options' delimiter, reading a compact time against the options'
   * clock.
   *
   * <p>The body is everything after the last delimiter a left-to-right scan finds, its matches not
   * overlapping, and the prefix everything before it; a string with no delimiter is a body with no
   * prefix. Under {@code --}, {@code usr---01hf7yat00} splits after {@code usr}, not at the last
   * {@code --}, and its body starts with {@code -}. The body length in UTF-8 bytes gives the size
   * and the time form:
   *
   * <table>
   *   <caption>Body length to size and time form</caption>
   *   <tr><th>Body length</th><th>Size</th><th>Time</th></tr>
   *   <tr><td>10</td><td>{@link Size#XS}</td><td>standard</td></tr>
   *   <tr><td>13</td><td>{@link Size#S}</td><td>compact</td></tr>
   *   <tr><td>14</td><td>{@link Size#S}</td><td>standard</td></tr>
   *   <tr><td>18</td><td>{@link Size#M}</td><td>standard</td></tr>
   *   <tr><td>21</td><td>{@link Size#L}</td><td>compact</td></tr>
   *   <tr><td>22</td><td>{@link Size#L}</td><td>standard</td></tr>
   *   <tr><td>24</td><td>{@link Size#XL}</td><td>compact</td></tr>
   *   <tr><td>26</td><td>{@link Size#XL}</td><td>standard</td></tr>
   * </table>
   *
   * <p>The lengths 10 and 18 are also those of a compact {@code xs} and a compact {@code m} body,
   * and they are always read as standard time: such an id decodes to its size, not compact, with a
   * time read from its first 10 characters, which is not the time it was generated at.
   *
   * <p>A body that starts with {@code z} or {@code Z} is deterministic and carries no time. A
   * standard time is the first 10 characters, the first one's low 3 bits then nine groups of 5. A
   * compact body carries only the low 40 bits of its time in its first 8 characters; the high bits
   * are the clock's, less one 2^40 ms window when the result would be more than one day ahead of
   * the clock. So the time comes back exact while the id and the clock fall in the same 2^40 ms
   * window (about 34.8 years) and the id is no more than one day ahead of the clock. With the clock
   * in the first window after the Unix epoch, the window before ends at the epoch, so a time read
   * from it is negative: nothing clamps it at zero. The clock is read only for a compact body. The
   * arithmetic is in {@code long}, so a clock within about 2^40 ms of {@link Long#MIN_VALUE} wraps
   * silently and the time read is meaningless; no real clock is there.
   *
   * <p>A string that is not a well-formed id is refused with the first rule its body breaks,
   * checked in this order: {@link InvalidReason#EMPTY} for an empty body, {@link
   * InvalidReason#INVALID_LENGTH} for a body whose length in UTF-8 bytes is not in the table above
   * (an unpaired surrogate counts as three bytes), and {@link InvalidReason#INVALID_CHARACTER} for
   * a body character that is not a Crockford Base32 symbol in either case. Any string gets an
   * answer: malformed input never throws.
   *
   * @param id the string to decode
   * @param options the delimiter and the clock
   * @return a {@link DecodeResult.Decoded} for a well-formed id, or a {@link DecodeResult.Invalid}
   *     naming the first rule the string breaks
   * @throws NullPointerException when {@code id} or {@code options} is null
   */
  public static DecodeResult decode(String id, DecodeOptions options) {
    Objects.requireNonNull(id, "id");
    Clock clock = options.clock();
    return Decoder.decode(id, options.delimiter(), clock::millis);
  }

  /**
   * Whether {@code id} is a well-formed id under the delimiter {@code _}: {@link #isValid(String,
   * String, String)} with no required prefix.
   *
   * @param id the string to check
   * @return whether {@link #decode(String) decode} answers a {@link DecodeResult.Decoded} for it
   * @throws NullPointerException when {@code id} is null
   */
  public static boolean isValid(String id) {
    return isValid(id, "_", null);
  }

  /**
   * Whether {@code id} is a well-formed id under {@code delimiter} and, when {@code requiredPrefix}
   * is neither null nor empty, carries exactly that prefix: whether {@link #decode(String,
   * DecodeOptions) decode} answers a {@link DecodeResult.Decoded} for it whose prefix is the one
   * required. A null or empty required prefix accepts any prefix or none; an id with no delimiter
   * has no prefix, so it fails any other. Validity does not depend on the clock, so this reads
   * none.
   *
   * <p>This is stricter than the Elixir reference's {@code valid?}, which accepts any alphabet body
   * of at least 8 characters: a body of a length the format does not define is not valid here. The
   * conformance corpus does not fix this answer; the TypeScript, Rust and .NET ports read it the
   * same way.
   *
   * @param id the string to check
   * @param delimiter the delimiter the id was generated with, not empty
   * @param requiredPrefix the prefix the id must carry, or null or {@code ""} for any
   * @return whether the id is well formed and carries the required prefix
   * @throws IllegalArgumentException when {@code delimiter} is empty
   * @throws NullPointerException when {@code id} or {@code delimiter} is null
   */
  public static boolean isValid(String id, String delimiter, String requiredPrefix) {
    Objects.requireNonNull(id, "id");
    DecodeResult result = Decoder.decode(id, DecodeOptions.requireDelimiter(delimiter), () -> 0L);
    if (!(result instanceof DecodeResult.Decoded decoded)) {
      return false;
    }
    return requiredPrefix == null
        || requiredPrefix.isEmpty()
        || decoded.prefix().equals(Optional.of(requiredPrefix));
  }

  /**
   * Whether {@code id} is a well-formed deterministic id under the delimiter {@code _}: {@link
   * #isDeterministic(String, String)} with {@code _}.
   *
   * @param id the string to check
   * @return whether the id is well formed and its body starts with the marker
   * @throws NullPointerException when {@code id} is null
   */
  public static boolean isDeterministic(String id) {
    return isDeterministic(id, "_");
  }

  /**
   * Whether {@code id} is a well-formed deterministic id under {@code delimiter}: one {@link
   * #decode(String, DecodeOptions) decode} answers a {@link DecodeResult.Decoded} for whose body
   * starts with the marker {@code z} or {@code Z}. A string that is not well formed is not one,
   * whatever its first body character, which is stricter than the Elixir reference's {@code
   * deterministic?}, which checks only that character. It reads no clock.
   *
   * @param id the string to check
   * @param delimiter the delimiter the id was generated with, not empty
   * @return whether the id is well formed and its body starts with the marker
   * @throws IllegalArgumentException when {@code delimiter} is empty
   * @throws NullPointerException when {@code id} or {@code delimiter} is null
   */
  public static boolean isDeterministic(String id, String delimiter) {
    Objects.requireNonNull(id, "id");
    DecodeResult result = Decoder.decode(id, DecodeOptions.requireDelimiter(delimiter), () -> 0L);
    return result instanceof DecodeResult.Decoded decoded && decoded.deterministic();
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

  /** Draws {@code width} bytes from {@code source}, refusing an answer of any other length. */
  static byte[] draw(ByteSource source, int width) {
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
