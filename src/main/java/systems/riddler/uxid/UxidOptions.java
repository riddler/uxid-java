package systems.riddler.uxid;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The options of one {@link Uxid#generate(UxidOptions) generate} request, built with {@link
 * #builder()}. An instance is immutable and safe to share between threads and calls.
 *
 * <p>Every option has a default, so {@code UxidOptions.builder().build()} asks for what {@link
 * Uxid#generate()} makes: no prefix, size {@link Size#XL}, a standard time read from the clock at
 * the call, lower case, the delimiter {@code _} and random bytes from {@link
 * ByteSource#secureRandom()}. Setting {@link Builder#from(String) from} asks for a deterministic id
 * instead.
 *
 * <pre>{@code
 * UxidOptions options = UxidOptions.builder().prefix("usr").size(Size.S).build();
 * String id = Uxid.generate(options); // usr_ followed by a 14-character body
 *
 * UxidOptions named = UxidOptions.builder().prefix("usr").from("alice@example.com").build();
 * String same = Uxid.generate(named); // usr_zcvt7epac0t1ebcsjfyf7cwz25, every time
 * }</pre>
 */
public final class UxidOptions {
  /** The latest time a standard time field holds, in milliseconds: 2^48 - 1. */
  static final long MAX_TIME = (1L << 48) - 1;

  private final String prefix;
  private final Size size;
  private final Integer randSize;
  private final Boolean compactTime;
  private final LetterCase letterCase;
  private final String delimiter;
  private final Long time;
  private final byte[] rand;
  private final ByteSource byteSource;
  private final byte[] from;

  private UxidOptions(Builder builder) {
    this.prefix = builder.prefix;
    this.size = builder.size;
    this.randSize = builder.randSize;
    this.compactTime = builder.compactTime;
    this.letterCase = builder.letterCase;
    this.delimiter = builder.delimiter;
    this.time = builder.time;
    this.rand = builder.rand == null ? null : builder.rand.clone();
    this.byteSource = builder.byteSource;
    this.from = builder.from;
  }

  /**
   * Returns a builder holding every default.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  String prefix() {
    return prefix;
  }

  Size size() {
    return size;
  }

  /** The explicit random width in bytes, or null for the size's own width. */
  Integer randSize() {
    return randSize;
  }

  /** The explicit time form (true for compact), or null when it was not set. */
  Boolean compactTime() {
    return compactTime;
  }

  LetterCase letterCase() {
    return letterCase;
  }

  String delimiter() {
    return delimiter;
  }

  /** The explicit time in milliseconds, or null to read the clock at the call. */
  Long time() {
    return time;
  }

  /** A copy of the fixed random bytes, or null to draw them from the byte source. */
  byte[] rand() {
    return rand == null ? null : rand.clone();
  }

  ByteSource byteSource() {
    return byteSource;
  }

  /** The deterministic input as UTF-8, or null for a time-based id. Never handed out. */
  byte[] fromUtf8() {
    return from;
  }

  /** Whether fixed random bytes are set, without copying them. */
  boolean hasRand() {
    return rand != null;
  }

  /**
   * Builds a {@link UxidOptions}. Each setter refuses a value that is wrong on its own; a
   * combination of values that contradicts the wire format is refused by {@link
   * Uxid#generate(UxidOptions)} with a {@link UxidException}. A builder is not safe to share
   * between threads.
   */
  public static final class Builder {
    private String prefix = "";
    private Size size = Size.XL;
    private Integer randSize;
    private Boolean compactTime;
    private LetterCase letterCase = LetterCase.LOWER;
    private String delimiter = "_";
    private Long time;
    private byte[] rand;
    private ByteSource byteSource = ByteSource.secureRandom();
    private byte[] from;

    private Builder() {}

    /**
     * Sets the prefix, emitted before the delimiter exactly as given. The empty string, the
     * default, is no prefix: the id is the bare body, with no delimiter. A prefix may contain the
     * delimiter.
     *
     * @param prefix the prefix, or {@code ""} for none
     * @return this builder
     * @throws NullPointerException when {@code prefix} is null
     */
    public Builder prefix(String prefix) {
      this.prefix = Objects.requireNonNull(prefix, "prefix");
      return this;
    }

    /**
     * Sets the size, which fixes the random field's width; the default is {@link Size#XL}. {@link
     * Size#parse(String)} reads the short and long spellings.
     *
     * @param size the size
     * @return this builder
     * @throws NullPointerException when {@code size} is null
     */
    public Builder size(Size size) {
      this.size = Objects.requireNonNull(size, "size");
      return this;
    }

    /**
     * Sets the random field's width in bytes, overriding the width the size and time form give. A
     * body built with a width outside the size table has the table's layout but not one of its
     * lengths, so a decoder refuses it. A width above 10 is refused at generation with {@link
     * ErrorCode#RAND_SIZE_TOO_LARGE}.
     *
     * @param randSize the width in bytes, 0 or more
     * @return this builder
     * @throws IllegalArgumentException when {@code randSize} is negative
     */
    public Builder randSize(int randSize) {
      if (randSize < 0) {
        throw new IllegalArgumentException("randSize must be 0 or more, got " + randSize);
      }
      this.randSize = randSize;
      return this;
    }

    /**
     * Chooses the time form: {@code true} for a compact time (the low 40 bits of the time, 8
     * characters), {@code false} for a standard time (48 bits, 10 characters), the default. Compact
     * time gives most sizes one more random byte. A compact time whose first character would be the
     * deterministic marker (from mid-2038 until the 40-bit window rolls over in 2039) is refused at
     * generation with {@link ErrorCode#COMPACT_TIME_OUT_OF_RANGE}.
     *
     * @param compactTime {@code true} for a compact time
     * @return this builder
     */
    public Builder compactTime(boolean compactTime) {
      this.compactTime = compactTime;
      return this;
    }

    /**
     * Sets the letter case of the body; the default is {@link LetterCase#LOWER}. The prefix keeps
     * its own case.
     *
     * @param letterCase the body's letter case
     * @return this builder
     * @throws NullPointerException when {@code letterCase} is null
     */
    public Builder letterCase(LetterCase letterCase) {
      this.letterCase = Objects.requireNonNull(letterCase, "letterCase");
      return this;
    }

    /**
     * Sets the delimiter between the prefix and the body; the default is {@code _}. A decoder must
     * use the same one: it is not recorded in the id. A delimiter made of alphabet characters makes
     * the prefix and body ambiguous on decode and is not supported.
     *
     * @param delimiter the delimiter, not empty
     * @return this builder
     * @throws IllegalArgumentException when {@code delimiter} is empty
     * @throws NullPointerException when {@code delimiter} is null
     */
    public Builder delimiter(String delimiter) {
      Objects.requireNonNull(delimiter, "delimiter");
      if (delimiter.isEmpty()) {
        throw new IllegalArgumentException("the delimiter must not be empty");
      }
      this.delimiter = delimiter;
      return this;
    }

    /**
     * Fixes the id's time, in milliseconds since the Unix epoch. When it is not set, the time is
     * read from {@link System#currentTimeMillis()} at each call.
     *
     * @param millis the time, from 0 to 2^48 - 1
     * @return this builder
     * @throws IllegalArgumentException when {@code millis} is outside 0 to 2^48 - 1
     */
    public Builder time(long millis) {
      this.time = requireTime(millis);
      return this;
    }

    /**
     * Fixes the random field to these bytes, for reproducible ids such as test vectors. This is not
     * a source of randomness: an id built from fixed bytes is exactly as guessable as those bytes,
     * so production code leaves it unset and lets the byte source draw them. The count must equal
     * the request's random width, or generation is refused with {@link
     * ErrorCode#RAND_WIDTH_MISMATCH}; the array is copied.
     *
     * @param rand the random field's bytes
     * @return this builder
     * @throws NullPointerException when {@code rand} is null
     */
    public Builder rand(byte[] rand) {
      this.rand = Objects.requireNonNull(rand, "rand").clone();
      return this;
    }

    /**
     * Sets where random bytes come from; the default is {@link ByteSource#secureRandom()}. The
     * source must be cryptographically secure. It is not called when fixed random bytes are set,
     * when the random width is 0, or for a deterministic id.
     *
     * @param byteSource the source
     * @return this builder
     * @throws NullPointerException when {@code byteSource} is null
     */
    public Builder byteSource(ByteSource byteSource) {
      this.byteSource = Objects.requireNonNull(byteSource, "byteSource");
      return this;
    }

    /**
     * Asks for a deterministic id: one derived from {@code from} and the prefix by SHA-256, so the
     * same prefix, input and size always give the same id, in either letter case. The body is the
     * marker ({@code z}, or {@code Z} in upper case) followed by hash characters, at the size's
     * standard body length. The time, the time form, the random width and the byte source are not
     * read. Fixed random bytes contradict it and are refused at generation with {@link
     * ErrorCode#RAND_WITH_FROM}.
     *
     * <p>A deterministic id is as guessable as its input: never build one from a secret and treat
     * the id as unguessable.
     *
     * <p>The input is hashed as its UTF-8 bytes. A string with no UTF-8 encoding, one holding an
     * unpaired surrogate, is refused here rather than given a replacement character, which would
     * give two different strings one id.
     *
     * @param from the input, any string with a UTF-8 encoding; the empty string is an input too
     * @return this builder
     * @throws IllegalArgumentException when {@code from} holds an unpaired surrogate
     * @throws NullPointerException when {@code from} is null
     */
    public Builder from(String from) {
      this.from = utf8(Objects.requireNonNull(from, "from"), "from");
      return this;
    }

    /**
     * Returns the options this builder holds. The builder may be changed and built again.
     *
     * @return the options
     */
    public UxidOptions build() {
      return new UxidOptions(this);
    }
  }

  /**
   * Returns the UTF-8 bytes of {@code text}, refusing a string with no UTF-8 encoding (an unpaired
   * surrogate) rather than replacing it.
   */
  static byte[] utf8(String text, String name) {
    try {
      ByteBuffer encoded =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(text));
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException(
          name + " holds an unpaired surrogate and has no UTF-8 encoding", e);
    }
  }

  /** Returns {@code millis} when it fits a standard time field, else refuses it. */
  static long requireTime(long millis) {
    if (millis < 0 || millis > MAX_TIME) {
      throw new IllegalArgumentException(
          "the time must be from 0 to 2^48 - 1 milliseconds, got " + millis);
    }
    return millis;
  }
}
