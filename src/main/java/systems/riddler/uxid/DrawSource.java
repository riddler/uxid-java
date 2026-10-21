package systems.riddler.uxid;

/**
 * Where a {@link Monotonic} generator takes its random values from: a fresh random field, the seed,
 * and the step that moves the field on within a millisecond.
 *
 * <p>A generator built with {@link Monotonic#Monotonic()} has no draw source of its own: it draws
 * from each call's {@link UxidOptions.Builder#byteSource(ByteSource) byte source}, as {@link
 * Monotonic} describes. A draw source of your own, passed to {@link
 * Monotonic#Monotonic(DrawSource)}, replaces those draws, and the byte source is then never called.
 * It exists to replay known values, as a test or a conformance case does, and is never a source of
 * randomness for real ids: a monotonic id is exactly as hard to guess as its draws.
 *
 * <p>A generator calls its draw source with its lock held, so a draw source used by one generator
 * is called by one thread at a time; one shared by several generators must be safe for that.
 */
public interface DrawSource {

  /**
   * Returns a fresh random value for a random field of {@code width} bytes: a new array of exactly
   * {@code width} bytes, read as one big-endian unsigned integer.
   *
   * @param width the random field's width in bytes, from 1 to 10
   * @return a new array of {@code width} bytes
   */
  byte[] seed(int width);

  /**
   * Returns a step drawn uniformly from 1 to 2^(4 x {@code width}) inclusive, half the random
   * field's bit width.
   *
   * @param width the random field's width in bytes, from 1 to 10
   * @return the step, from 1 to 2^(4 x {@code width})
   */
  long step(int width);
}
