package systems.riddler.uxid;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What {@link Uxid#decode(String, DecodeOptions) decode} answers for a string: a {@link Decoded}
 * for a well-formed id, or an {@link Invalid} naming the rule the string broke. Decoding never
 * throws on malformed input; the caller inspects the result.
 *
 * <pre>{@code
 * DecodeResult result = Uxid.decode("cus_01hf7yat0004h1");
 * if (result instanceof DecodeResult.Decoded decoded) {
 *   decoded.prefix(); // Optional[cus]
 *   decoded.size(); // Size.S
 *   decoded.time(); // OptionalLong[1700000000000]
 * } else if (result instanceof DecodeResult.Invalid invalid) {
 *   invalid.reason().wireName(); // such as "invalid_length"
 * }
 * }</pre>
 */
public sealed interface DecodeResult permits DecodeResult.Decoded, DecodeResult.Invalid {

  /**
   * What a well-formed id says about itself: its prefix, the size and time form its body length
   * gives, whether its body is deterministic, and the time it carries. The random field, and a
   * deterministic body's hash, are not read back.
   *
   * @param prefix everything before the delimiter the body follows, or empty when the id has no
   *     delimiter. An id that starts with the delimiter, which no producer emits, has the prefix
   *     {@code ""}, best read as no prefix
   * @param size the size the body length gives
   * @param compact whether the body length is a compact-time one (13, 21 or 24). The lengths 10 and
   *     18 are always read as standard time, so a compact {@code xs} or compact {@code m} id
   *     decodes with this {@code false}
   * @param deterministic whether the body starts with the deterministic marker {@code z} or {@code
   *     Z}
   * @param time the time the body carries, in milliseconds since the Unix epoch, or empty for a
   *     deterministic body, which carries none. A compact time's high bits come from the decoder's
   *     clock, so it can be before the epoch
   */
  record Decoded(
      Optional<String> prefix, Size size, boolean compact, boolean deterministic, OptionalLong time)
      implements DecodeResult {

    /**
     * Creates a decoded value.
     *
     * @throws NullPointerException when {@code prefix}, {@code size} or {@code time} is null
     */
    public Decoded {
      Objects.requireNonNull(prefix, "prefix");
      Objects.requireNonNull(size, "size");
      Objects.requireNonNull(time, "time");
    }
  }

  /**
   * A refused string, with the first rule it broke.
   *
   * @param reason the rule the body broke
   */
  record Invalid(InvalidReason reason) implements DecodeResult {

    /**
     * Creates a refusal.
     *
     * @throws NullPointerException when {@code reason} is null
     */
    public Invalid {
      Objects.requireNonNull(reason, "reason");
    }
  }
}
