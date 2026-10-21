package systems.riddler.uxid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

  /** A draw source that fails the test if it is ever called. */
  static DrawSource refusingDraws() {
    return new DrawSource() {
      @Override
      public byte[] seed(int width) {
        throw new AssertionError("a seed was drawn for " + width + " bytes");
      }

      @Override
      public long step(int width) {
        throw new AssertionError("a step was drawn for " + width + " bytes");
      }
    };
  }

  /**
   * A draw source that answers recorded values in order, as the corpus gives them: each seed an
   * unsigned integer written big-endian in the field's width, each step as it is. It records each
   * draw as {@code seed} or {@code step} in {@link #calls}.
   */
  static final class Replay implements DrawSource {
    final List<String> calls = new ArrayList<>();
    private final Deque<Long> seeds = new ArrayDeque<>();
    private final Deque<Long> steps = new ArrayDeque<>();

    Replay(List<Long> seeds, List<Long> steps) {
      this.seeds.addAll(seeds);
      this.steps.addAll(steps);
    }

    @Override
    public byte[] seed(int width) {
      calls.add("seed");
      Long value = seeds.poll();
      if (value == null) {
        throw new AssertionError("no seed left to draw");
      }
      byte[] bytes = new byte[width];
      long rest = value;
      for (int i = width - 1; i >= 0; i--) {
        bytes[i] = (byte) rest;
        rest >>>= 8;
      }
      return bytes;
    }

    @Override
    public long step(int width) {
      calls.add("step");
      Long value = steps.poll();
      if (value == null) {
        throw new AssertionError("no step left to draw");
      }
      return value;
    }

    /** Whether every recorded seed and step was drawn. */
    boolean drained() {
      return seeds.isEmpty() && steps.isEmpty();
    }
  }
}
