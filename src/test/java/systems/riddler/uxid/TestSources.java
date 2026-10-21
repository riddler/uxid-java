package systems.riddler.uxid;

import java.util.ArrayList;
import java.util.List;

/** Byte sources for tests. */
final class TestSources {
  private TestSources() {}

  /** A source that fails the test if it is ever called. */
  static ByteSource refusing() {
    return count -> {
      throw new AssertionError("the byte source was called for " + count + " bytes");
    };
  }

  /** A source that answers bytes 0, 1, 2 ... and records each count it was asked for. */
  static final class Counting implements ByteSource {
    final List<Integer> counts = new ArrayList<>();

    @Override
    public byte[] nextBytes(int count) {
      counts.add(count);
      byte[] bytes = new byte[count];
      for (int i = 0; i < count; i++) {
        bytes[i] = (byte) i;
      }
      return bytes;
    }
  }
}
