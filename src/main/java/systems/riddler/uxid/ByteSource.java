package systems.riddler.uxid;

import java.security.SecureRandom;

/**
 * Where an id's random field comes from: given a count, it returns that many random bytes.
 *
 * <p>The default, {@link #secureRandom()}, draws from {@link SecureRandom}. A replacement must be a
 * cryptographically secure source as well: the random field is what keeps two ids made in the same
 * millisecond apart, and what keeps an id from being guessed. Inject one to use a different secure
 * source; to reproduce a fixed id for a test vector, set {@link UxidOptions.Builder#rand(byte[])
 * fixed random bytes} instead.
 */
@FunctionalInterface
public interface ByteSource {

  /**
   * Returns {@code count} random bytes, in a new array of exactly that length. The count is the
   * random field's width, from 1 to 10; a request with no random field does not call the source.
   *
   * @param count the number of bytes wanted
   * @return a new array of {@code count} random bytes
   */
  byte[] nextBytes(int count);

  /**
   * Returns the default source, which draws from one {@link SecureRandom} shared by every caller.
   * {@code SecureRandom} is safe to use from several threads at once.
   *
   * @return the default source
   */
  static ByteSource secureRandom() {
    return SecureRandomSource.INSTANCE;
  }
}
