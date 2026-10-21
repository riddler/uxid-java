package systems.riddler.uxid;

import java.time.Clock;
import java.util.Objects;

/**
 * The options of one {@link Uxid#decode(String, DecodeOptions) decode} call beside the string,
 * built with {@link #builder()}. An instance is immutable and safe to share between threads and
 * calls.
 *
 * <p>The defaults are what {@link Uxid#decode(String)} uses: the delimiter {@code _} and the system
 * clock in UTC, {@link Clock#systemUTC()}.
 *
 * <pre>{@code
 * DecodeOptions options =
 *     DecodeOptions.builder()
 *         .delimiter("--")
 *         .clock(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC))
 *         .build();
 * DecodeResult result = Uxid.decode("usr--hf7yat0004hm5", options);
 * }</pre>
 */
public final class DecodeOptions {
  /** Every default: the delimiter {@code _} and the system clock in UTC. */
  static final DecodeOptions DEFAULTS = builder().build();

  private final String delimiter;
  private final Clock clock;

  private DecodeOptions(Builder builder) {
    this.delimiter = builder.delimiter;
    this.clock = builder.clock;
  }

  /**
   * Returns a builder holding every default.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  String delimiter() {
    return delimiter;
  }

  Clock clock() {
    return clock;
  }

  /** Refuses a null or empty delimiter, the one check every delimiter-taking call shares. */
  static String requireDelimiter(String delimiter) {
    Objects.requireNonNull(delimiter, "delimiter");
    if (delimiter.isEmpty()) {
      throw new IllegalArgumentException("the delimiter must not be empty");
    }
    return delimiter;
  }

  /**
   * Builds a {@link DecodeOptions}. Each setter refuses a value that is wrong on its own. A builder
   * is not safe to share between threads.
   */
  public static final class Builder {
    private String delimiter = "_";
    private Clock clock = Clock.systemUTC();

    private Builder() {}

    /**
     * Sets the delimiter between the prefix and the body; the default is {@code _}. It must be the
     * one the id was generated with, since an id does not record it. A delimiter made of Crockford
     * Base32 characters makes the split ambiguous and is not supported.
     *
     * @param delimiter the delimiter, not empty
     * @return this builder
     * @throws IllegalArgumentException when {@code delimiter} is empty
     * @throws NullPointerException when {@code delimiter} is null
     */
    public Builder delimiter(String delimiter) {
      this.delimiter = requireDelimiter(delimiter);
      return this;
    }

    /**
     * Sets the decoder's clock, which supplies a compact time's high bits; the default is {@link
     * Clock#systemUTC()}. Only its {@link Clock#millis() millis} are read, once per call, and only
     * for a compact body. A fixed clock makes the decoding of compact ids reproducible.
     *
     * @param clock the clock
     * @return this builder
     * @throws NullPointerException when {@code clock} is null
     */
    public Builder clock(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
      return this;
    }

    /**
     * Builds the options.
     *
     * @return the options
     */
    public DecodeOptions build() {
      return new DecodeOptions(this);
    }
  }
}
