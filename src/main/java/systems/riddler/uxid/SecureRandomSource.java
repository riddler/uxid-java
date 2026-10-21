package systems.riddler.uxid;

import java.security.SecureRandom;

/**
 * The default {@link ByteSource}: one {@link SecureRandom}, created on first use and shared, as
 * {@link java.util.UUID#randomUUID()} shares its own. It holds no configuration and nothing a
 * caller can observe or change.
 */
final class SecureRandomSource implements ByteSource {
  static final SecureRandomSource INSTANCE = new SecureRandomSource();

  private final SecureRandom random = new SecureRandom();

  private SecureRandomSource() {}

  @Override
  public byte[] nextBytes(int count) {
    byte[] bytes = new byte[count];
    random.nextBytes(bytes);
    return bytes;
  }
}
