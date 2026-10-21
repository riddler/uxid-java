package systems.riddler.uxid;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A monotonic generator: for one prefix and random width, the time and random value of each id it
 * makes are greater than the last one's, also when several fall in one millisecond and when the
 * clock moves back, so among ids of one letter case, delimiter and time form each sorts after the
 * last as a string. It follows the "Monotonic generation" rule of the wire format. A monotonic id
 * is an ordinary time-based id of its size and length: nothing in it says how it was made, and
 * {@link Uxid#decode(String)} reads it as any other.
 *
 * <p>Each call takes a {@link UxidOptions} as {@link Uxid#generate(UxidOptions)} does, and the
 * generator keeps its state per prefix and per random width: for each such pair it has seen, the
 * last time {@code T} it gave and the last random value {@code R}, the random field read as one
 * big-endian unsigned integer of {@code w} bytes. For a call at time {@code now} (the options'
 * time, or {@link System#currentTimeMillis()} read at the call):
 *
 * <ol>
 *   <li>with no state for the pair, or {@code now} later than {@code T}, the id carries {@code now}
 *       and a fresh random value, the seed;
 *   <li>with {@code now} equal to or earlier than {@code T}, the id carries {@code T} and {@code R
 *       + step}, where {@code step} is drawn uniformly from 1 to 2^(4w) inclusive, half the field's
 *       bit width;
 *   <li>when {@code R + step} is 2^(8w) or more, the id carries {@code T + 1} and a fresh seed
 *       instead.
 * </ol>
 *
 * <p>The draws come from the {@link DrawSource} the generator was built with, or, for a generator
 * built with {@link #Monotonic()}, from each call's {@link
 * UxidOptions.Builder#byteSource(ByteSource) byte source}: a seed is {@code w} bytes, read
 * big-endian, and a step is the low {@code 4w} bits of {@code ceil(w / 2)} bytes, read big-endian,
 * plus one. The range of a step holds a power of two values, so the masked bits are exactly uniform
 * over it, with no modulo bias and nothing redrawn.
 *
 * <p>The step is not a plus-one counter: consecutive ids in a millisecond are spread over a window
 * of 2^(4w) values. That stops trivial enumeration, but it is a mitigation, not unpredictability,
 * and a small random field stays small: a standard {@code s} id has 16 random bits. A compact time
 * holds only the low 40 bits of the time, so compact ids keep their order only within one 2^40 ms
 * window, as every compact id does.
 *
 * <p>At size {@link Size#XS} the generator uses compact time unless the options set the time form,
 * because a standard {@code xs} has no random field to step and a compact {@code xs} has a one-byte
 * field. The random width (the size's, or the options' {@link UxidOptions.Builder#randSize(int)
 * random width}) must be at least one byte, so an {@code xs} with an explicit standard time and no
 * random width is refused.
 *
 * <p><b>Thread safety.</b> One generator may be shared between threads. A {@link
 * java.util.concurrent.locks.ReentrantLock} inside the instance orders the calls: each call reads
 * the time, draws and updates the state with the lock held, so the ids one generator makes for one
 * prefix and random width increase strictly, as above, in the order the calls take the lock,
 * whichever thread makes them. The draw source or byte source is called with the lock held. The
 * state lives in the instance alone, with nothing kept in a static field, so two generators never
 * affect each other, and ids from two generators carry no order between them within a millisecond.
 * The state for each pair is kept for the generator's lifetime.
 *
 * <p><b>A refused call.</b> A call that throws leaves the generator as it was: the state of every
 * pair stays what it was before the call, and the next call starts from it. Draws a refused call
 * already made are not given back. So after a step that overflows the field onto a compact time the
 * wire format refuses, a later call at or before {@code T} draws a new step: it is refused again if
 * that step also overflows the field, and with a step that does not, it answers an id at {@code T}.
 *
 * <pre>{@code
 * Monotonic ids = new Monotonic();
 * UxidOptions options = UxidOptions.builder().prefix("usr").size(Size.S).build();
 * String first = ids.generate(options);
 * String second = ids.generate(options); // sorts after first, also in the same millisecond
 * }</pre>
 */
public final class Monotonic {
  /** The deterministic marker's value, which a compact time may not start with. */
  private static final long MARKER = 31;

  private final ReentrantLock lock = new ReentrantLock();
  private final DrawSource draws;
  private final Map<Key, Last> state = new HashMap<>();

  /**
   * Builds a generator that draws its seeds and steps from each call's {@link
   * UxidOptions.Builder#byteSource(ByteSource) byte source}, by default {@link
   * ByteSource#secureRandom()}.
   */
  public Monotonic() {
    this.draws = null;
  }

  /**
   * Builds a generator that takes its seeds and steps from {@code draws}; the options' byte source
   * is never called. This replays known draws, as a test or a conformance case does; ids built from
   * known draws are exactly as guessable as those draws, so production code uses {@link
   * #Monotonic()}.
   *
   * @param draws the source of every seed and step
   * @throws NullPointerException when {@code draws} is null
   */
  public Monotonic(DrawSource draws) {
    this.draws = Objects.requireNonNull(draws, "draws");
  }

  /**
   * Generates the next id with every default: no prefix, size {@link Size#XL} with a standard time
   * read from the clock now, lower case, and draws from {@link ByteSource#secureRandom()} unless
   * the generator has a draw source.
   *
   * @return a 26-character id
   * @throws IllegalArgumentException when the clock reads outside 0 to 2^48 - 1 milliseconds, or
   *     the field overflows in millisecond 2^48 - 1
   * @throws IllegalStateException when a draw answers outside its range
   */
  public String generate() {
    return generate(UxidOptions.builder().build());
  }

  /**
   * Generates the next id for the options' prefix and random width, under the rule the class
   * describes. The options' prefix, size, random width, time form, letter case, delimiter, time and
   * byte source mean what they mean to {@link Uxid#generate(UxidOptions)}, with the same defaults,
   * except that an {@link Size#XS} with no time form set uses compact time.
   *
   * <p>A call that throws leaves the generator as it was (see the class description).
   *
   * @param options the request
   * @return the id
   * @throws UxidException with {@link ErrorCode#FROM_WITH_MONOTONIC} when the options carry a
   *     {@link UxidOptions.Builder#from(String) from} input, since a deterministic id has no random
   *     field to step; {@link ErrorCode#RAND_WITH_MONOTONIC} for fixed {@link
   *     UxidOptions.Builder#rand(byte[]) random bytes}, since the generator chooses the field;
   *     {@link ErrorCode#RAND_SIZE_TOO_LARGE} for a random width above 10 bytes; {@link
   *     ErrorCode#COMPACT_TIME_OUT_OF_RANGE} for a compact time the id would carry whose first
   *     character would be the deterministic marker, also when that time is the millisecond after
   *     {@code T}, reached by an overflow. The first two are checked in that order before anything
   *     else.
   * @throws IllegalArgumentException when the random width is 0 bytes (an {@code xs} with an
   *     explicit standard time and no random width, or a random width of 0); when the clock reads
   *     outside 0 to 2^48 - 1 milliseconds; or when the field overflows in millisecond 2^48 - 1,
   *     the last a time field holds
   * @throws IllegalStateException when a byte source returns null or a count of bytes other than
   *     the one asked for, or a draw source answers a seed of the wrong length or a step outside 1
   *     to 2^(4w)
   * @throws NullPointerException when {@code options} is null
   */
  public String generate(UxidOptions options) {
    if (options.fromUtf8() != null) {
      throw new UxidException(
          ErrorCode.FROM_WITH_MONOTONIC,
          "a monotonic generator makes no deterministic id: it has no random field to step");
    }
    if (options.hasRand()) {
      throw new UxidException(
          ErrorCode.RAND_WITH_MONOTONIC,
          "a monotonic generator takes no fixed random bytes: it chooses the random field");
    }
    Boolean explicitCompact = options.compactTime();
    boolean compact =
        explicitCompact == null ? options.size() == Size.XS : explicitCompact.booleanValue();
    Integer explicitWidth = options.randSize();
    int width = explicitWidth == null ? options.size().randBytes(compact) : explicitWidth;
    if (width > Uxid.MAX_RAND_BYTES) {
      throw new UxidException(
          ErrorCode.RAND_SIZE_TOO_LARGE,
          "the random field is at most " + Uxid.MAX_RAND_BYTES + " bytes, got " + width);
    }
    if (width == 0) {
      throw new IllegalArgumentException(
          "a monotonic id needs a random field to step, but this request's random width is 0"
              + " bytes; leave the time form unset at size xs, or set a random width from 1 to 10");
    }

    lock.lock();
    try {
      Long fixedTime = options.time();
      long now =
          fixedTime == null ? UxidOptions.requireTime(System.currentTimeMillis()) : fixedTime;
      Key key = new Key(options.prefix(), width);
      Last last = state.get(key);
      Last next;
      if (last != null && now <= last.time()) {
        BigInteger value = last.value().add(BigInteger.valueOf(step(options, width)));
        if (value.bitLength() <= 8 * width) {
          next = new Last(last.time(), value);
        } else if (last.time() == UxidOptions.MAX_TIME) {
          throw new IllegalArgumentException(
              "the random field overflowed in millisecond 2^48 - 1, the last a time field holds");
        } else {
          next = new Last(last.time() + 1, seed(options, width));
        }
      } else {
        next = new Last(now, seed(options, width));
      }

      String id = format(options, compact, width, next);
      state.put(key, next);
      return id;
    } finally {
      lock.unlock();
    }
  }

  private static String format(UxidOptions options, boolean compact, int width, Last next) {
    long compactTime = next.time() & ((1L << 40) - 1);
    if (compact && compactTime >>> 35 == MARKER) {
      throw new UxidException(
          ErrorCode.COMPACT_TIME_OUT_OF_RANGE,
          "a compact time from "
              + next.time()
              + " ms would start with the deterministic marker; use a standard time");
    }
    StringBuilder id = new StringBuilder(options.prefix().length() + 30);
    if (!options.prefix().isEmpty()) {
      id.append(options.prefix()).append(options.delimiter());
    }
    if (compact) {
      Base32.appendInteger(id, compactTime, 8, options.letterCase());
    } else {
      Base32.appendInteger(id, next.time(), 10, options.letterCase());
    }
    Base32.appendBytes(id, toBytes(next.value(), width), options.letterCase());
    return id.toString();
  }

  /** A fresh random value for a field of {@code width} bytes. */
  private BigInteger seed(UxidOptions options, int width) {
    byte[] bytes =
        draws == null
            ? Uxid.draw(options.byteSource(), width)
            : checkSeed(draws.seed(width), width);
    return new BigInteger(1, bytes);
  }

  /** A step from 1 to 2^(4w), uniform over it. */
  private long step(UxidOptions options, int width) {
    int bits = 4 * width;
    long max = 1L << bits;
    if (draws == null) {
      byte[] bytes = Uxid.draw(options.byteSource(), (width + 1) / 2);
      long drawn = 0;
      for (byte b : bytes) {
        drawn = (drawn << 8) | (b & 0xff);
      }
      return (drawn & (max - 1)) + 1;
    }
    long step = draws.step(width);
    if (step < 1 || step > max) {
      throw new IllegalStateException(
          "the draw source answered the step " + step + ", outside 1 to 2^" + bits);
    }
    return step;
  }

  private static byte[] checkSeed(byte[] seed, int width) {
    if (seed == null || seed.length != width) {
      throw new IllegalStateException(
          "the draw source answered a seed of "
              + (seed == null ? "null" : seed.length + " bytes")
              + " for a field of "
              + width);
    }
    return seed.clone();
  }

  /** The {@code width}-byte big-endian form of {@code value}, which is below 2^(8 x width). */
  private static byte[] toBytes(BigInteger value, int width) {
    byte[] raw = value.toByteArray();
    byte[] bytes = new byte[width];
    int copy = Math.min(raw.length, width);
    System.arraycopy(raw, raw.length - copy, bytes, width - copy, copy);
    return bytes;
  }

  /** The pair the state is kept for. */
  private record Key(String prefix, int width) {}

  /** The last id's time and random value for one pair. */
  private record Last(long time, BigInteger value) {}
}
