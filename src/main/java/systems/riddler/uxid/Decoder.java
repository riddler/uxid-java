package systems.riddler.uxid;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** Reads a string to a {@link DecodeResult} under the wire format's decoding rules. */
final class Decoder {
  /** The span of a compact time field, 2^40 milliseconds. */
  static final long COMPACT_WINDOW = 1L << 40;

  /**
   * How far ahead of the decoder's clock a compact time may be before it is read as one from the
   * window before: one day in milliseconds.
   */
  static final long ONE_DAY = 86_400_000L;

  private Decoder() {}

  /**
   * Decodes {@code id} under {@code delimiter}, a non-empty string, calling {@code clock} only for
   * a compact body.
   */
  static DecodeResult decode(String id, String delimiter, LongSupplier clock) {
    int at = lastDelimiter(id, delimiter);
    Optional<String> prefix = at < 0 ? Optional.empty() : Optional.of(id.substring(0, at));
    String body = at < 0 ? id : id.substring(at + delimiter.length());

    if (body.isEmpty()) {
      return new DecodeResult.Invalid(InvalidReason.EMPTY);
    }
    int length = utf8Length(body);
    Size size = sizeOf(length);
    if (size == null) {
      return new DecodeResult.Invalid(InvalidReason.INVALID_LENGTH);
    }
    for (int i = 0; i < body.length(); i++) {
      if (Base32.value(body.charAt(i)) < 0) {
        return new DecodeResult.Invalid(InvalidReason.INVALID_CHARACTER);
      }
    }

    boolean compact = length == 13 || length == 21 || length == 24;
    char first = body.charAt(0);
    boolean deterministic = first == 'z' || first == 'Z';
    OptionalLong time;
    if (deterministic) {
      time = OptionalLong.empty();
    } else if (compact) {
      time = OptionalLong.of(compactTime(read(body, 0, 8), clock.getAsLong()));
    } else {
      time = OptionalLong.of((read(body, 0, 1) & 7) << 45 | read(body, 1, 10));
    }
    return new DecodeResult.Decoded(prefix, size, compact, deterministic, time);
  }

  /**
   * The index of the last delimiter a left-to-right scan finds, its matches not overlapping, or -1
   * when there is none. Under {@code --}, {@code a---b} splits after {@code a}, as the reference's
   * split does, not at the last {@code --}.
   */
  private static int lastDelimiter(String id, String delimiter) {
    int at = -1;
    int from = 0;
    while (true) {
      int found = id.indexOf(delimiter, from);
      if (found < 0) {
        return at;
      }
      at = found;
      from = found + delimiter.length();
    }
  }

  /**
   * The length of {@code text} in UTF-8 bytes. An unpaired surrogate counts as three bytes, the
   * width of the replacement character U+FFFD.
   */
  private static int utf8Length(String text) {
    int bytes = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < 0x80) {
        bytes += 1;
      } else if (c < 0x800) {
        bytes += 2;
      } else if (Character.isHighSurrogate(c)
          && i + 1 < text.length()
          && Character.isLowSurrogate(text.charAt(i + 1))) {
        bytes += 4;
        i++;
      } else {
        bytes += 3;
      }
    }
    return bytes;
  }

  /** The size a body length gives, or null for a length a producer never emits. */
  private static Size sizeOf(int length) {
    return switch (length) {
      case 10 -> Size.XS;
      case 13, 14 -> Size.S;
      case 18 -> Size.M;
      case 21, 22 -> Size.L;
      case 24, 26 -> Size.XL;
      default -> null;
    };
  }

  /** Reads the symbols from {@code start} to {@code end} as one integer, 5 bits each. */
  private static long read(String body, int start, int end) {
    long value = 0;
    for (int i = start; i < end; i++) {
      value = value << 5 | Base32.value(body.charAt(i));
    }
    return value;
  }

  /**
   * The time of the compact field {@code low} under the clock {@code now}: the clock's bits above
   * bit 40 with {@code low}, less one window when that is more than one day ahead of the clock. The
   * window is the clock's rounded down, so with the clock in the first window after the epoch (or
   * before the epoch) the window before ends at or before the epoch and the time can be negative;
   * nothing clamps it at zero.
   */
  static long compactTime(long low, long now) {
    long time = Math.floorDiv(now, COMPACT_WINDOW) * COMPACT_WINDOW + low;
    if (time - now > ONE_DAY) {
      time -= COMPACT_WINDOW;
    }
    return time;
  }
}
