package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MonotonicTest {
  private static final long TIME = 1_700_000_000_000L;

  /** The last compact time whose first character is not the marker: 31 x 2^35 - 1. */
  private static final long LAST_COMPACT = 1_065_151_889_407L;

  @Test // (sabotage: step multiplied by 0 -> the second id assertion failed)
  void aCallInTheSameMillisecondAddsTheStepToTheLastValue() {
    TestSources.Replay draws = new TestSources.Replay(List.of(100L), List.of(7L, 1L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).prefix("usr").size(Size.S).build();

    assertEquals(plain(at(TIME).prefix("usr").size(Size.S), 2, 100), generator.generate(options));
    assertEquals(plain(at(TIME).prefix("usr").size(Size.S), 2, 107), generator.generate(options));
    assertEquals(plain(at(TIME).prefix("usr").size(Size.S), 2, 108), generator.generate(options));
    assertEquals(List.of("seed", "step", "step"), draws.calls);
  }

  @Test // (sabotage: now <= T read as now == T -> a seed was drawn, the replay ran dry)
  void aClockThatMovesBackKeepsTheLastTime() {
    TestSources.Replay draws = new TestSources.Replay(List.of(100L), List.of(3L));
    Monotonic generator = new Monotonic(draws);

    generator.generate(at(TIME).prefix("usr").size(Size.M).build());
    assertEquals(
        plain(at(TIME).prefix("usr").size(Size.M), 5, 103),
        generator.generate(at(TIME - 50).prefix("usr").size(Size.M).build()));
    assertEquals(List.of("seed", "step"), draws.calls);
  }

  @Test // (sabotage: now <= T + 1 -> a step was drawn, the replay ran dry)
  void aLaterTimeTakesAFreshSeedAndTheNewTime() {
    TestSources.Replay draws = new TestSources.Replay(List.of(100L, 5L), List.of());
    Monotonic generator = new Monotonic(draws);

    generator.generate(at(TIME).prefix("usr").size(Size.S).build());
    assertEquals(
        plain(at(TIME + 1).prefix("usr").size(Size.S), 2, 5),
        generator.generate(at(TIME + 1).prefix("usr").size(Size.S).build()));
    assertEquals(List.of("seed", "seed"), draws.calls);
  }

  @Test // (sabotage: overflow keeps T -> the id assertion failed on the time)
  void anOverflowMovesTheTimeOneMillisecondForwardWithAFreshSeed() {
    TestSources.Replay draws = new TestSources.Replay(List.of(250L, 9L), List.of(6L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).prefix("usr").size(Size.XS).build();

    generator.generate(options);
    assertEquals(
        plain(at(TIME + 1).prefix("usr").size(Size.XS).compactTime(true), 1, 9),
        generator.generate(options));
    assertEquals(List.of("seed", "step", "seed"), draws.calls);
  }

  @Test // (sabotage: key without the prefix -> cus stepped, the replay ran dry)
  void theStateIsKeptPerPrefixAndPerRandomWidth() {
    TestSources.Replay draws =
        new TestSources.Replay(List.of(1L, 2L, 3L, 4L), List.of(10L, 20L, 30L, 40L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions usrS = at(TIME).prefix("usr").size(Size.S).build();
    UxidOptions cusS = at(TIME).prefix("cus").size(Size.S).build();
    UxidOptions usrM = at(TIME).prefix("usr").size(Size.M).build();
    UxidOptions usrWidth3 = at(TIME).prefix("usr").size(Size.S).randSize(3).build();

    List<String> ids = new ArrayList<>();
    for (int round = 0; round < 2; round++) {
      ids.add(generator.generate(usrS));
      ids.add(generator.generate(cusS));
      ids.add(generator.generate(usrM));
      ids.add(generator.generate(usrWidth3));
    }

    List<String> expected =
        List.of(
            plain(at(TIME).prefix("usr").size(Size.S), 2, 1),
            plain(at(TIME).prefix("cus").size(Size.S), 2, 2),
            plain(at(TIME).prefix("usr").size(Size.M), 5, 3),
            plain(at(TIME).prefix("usr").size(Size.S).randSize(3), 3, 4),
            plain(at(TIME).prefix("usr").size(Size.S), 2, 11),
            plain(at(TIME).prefix("cus").size(Size.S), 2, 22),
            plain(at(TIME).prefix("usr").size(Size.M), 5, 33),
            plain(at(TIME).prefix("usr").size(Size.S).randSize(3), 3, 44));
    assertEquals(expected, ids);
  }

  @Test // (sabotage: one static state map -> the second generator stepped, replay dry)
  void twoGeneratorsKeepTheirOwnState() {
    TestSources.Replay one = new TestSources.Replay(List.of(1L), List.of());
    TestSources.Replay two = new TestSources.Replay(List.of(2L), List.of(1L));
    UxidOptions options = at(TIME).prefix("usr").size(Size.S).build();

    assertEquals(
        plain(at(TIME).prefix("usr").size(Size.S), 2, 1), new Monotonic(one).generate(options));
    assertEquals(
        plain(at(TIME).prefix("usr").size(Size.S), 2, 2), new Monotonic(two).generate(options));
  }

  @Test // (sabotage: step mask dropped -> the compact s id assertion failed)
  void theDefaultDrawsMaskTheByteSourceToTheStepRange() {
    Monotonic s = new Monotonic();
    UxidOptions sOptions =
        UxidOptions.builder()
            .time(TIME)
            .prefix("usr")
            .size(Size.S)
            .compactTime(true)
            .byteSource(answering("000000", "ffff"))
            .build();
    s.generate(sOptions);
    assertEquals(
        plain(at(TIME).prefix("usr").size(Size.S).compactTime(true), 3, 4096),
        s.generate(sOptions));

    Monotonic xs = new Monotonic();
    UxidOptions xsOptions =
        UxidOptions.builder()
            .time(TIME)
            .prefix("usr")
            .size(Size.XS)
            .byteSource(answering("00", "ff"))
            .build();
    xs.generate(xsOptions);
    assertEquals(
        plain(at(TIME).prefix("usr").size(Size.XS).compactTime(true), 1, 16),
        xs.generate(xsOptions));
  }

  @Test // (sabotage: step drew w bytes -> the counts assertion failed)
  void theDefaultDrawsAskTheByteSourceForTheSeedAndHalfTheWidth() {
    TestSources.Counting source = new TestSources.Counting();
    Monotonic generator = new Monotonic();
    UxidOptions options =
        UxidOptions.builder().time(TIME).prefix("usr").size(Size.M).byteSource(source).build();

    generator.generate(options);
    generator.generate(options);
    assertEquals(List.of(5, 3), source.counts);
  }

  @Test // (sabotage: draw source dropped -> the refusing byte source was called)
  void aDrawSourceReplacesTheByteSource() {
    TestSources.Replay draws = new TestSources.Replay(List.of(1L), List.of(1L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).prefix("usr").size(Size.L).build();

    generator.generate(options);
    assertEquals(plain(at(TIME).prefix("usr").size(Size.L), 7, 2), generator.generate(options));
  }

  @Test // (sabotage: compact default moved to xl -> assertDoesNotThrow failed)
  void anXsWithNoTimeFormUsesCompactTime() {
    TestSources.Replay draws = new TestSources.Replay(List.of(1L), List.of());
    UxidOptions options = at(TIME).prefix("usr").size(Size.XS).build();

    Monotonic generator = new Monotonic(draws);

    assertEquals("usr_hf7yat0001", assertDoesNotThrow(() -> generator.generate(options)));
  }

  @Test // (sabotage: width-0 check removed -> assertThrows saw the refusing draws)
  void aRandomWidthOfZeroIsRefused() {
    Monotonic generator = new Monotonic(TestSources.refusingDraws());
    UxidOptions standardXs = at(TIME).size(Size.XS).compactTime(false).build();
    UxidOptions zeroWidth = at(TIME).randSize(0).build();

    assertThrows(IllegalArgumentException.class, () -> generator.generate(standardXs));
    assertThrows(IllegalArgumentException.class, () -> generator.generate(zeroWidth));
  }

  @Test // (sabotage: width below 11 ignored -> assertDoesNotThrow failed)
  void anExplicitWidthOnAStandardXsIsStepped() {
    TestSources.Replay draws = new TestSources.Replay(List.of(1L), List.of(2L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).size(Size.XS).compactTime(false).randSize(2).build();

    assertDoesNotThrow(() -> generator.generate(options));
    assertEquals(
        plain(at(TIME).size(Size.XS).compactTime(false).randSize(2), 2, 3),
        assertDoesNotThrow(() -> generator.generate(options)));
  }

  @Test // (sabotage: cap raised to 11 -> assertThrows saw the refusing draws)
  void aRandomWidthAboveTenIsRefused() {
    Monotonic generator = new Monotonic(TestSources.refusingDraws());
    UxidOptions options = at(TIME).randSize(11).build();

    UxidException refusal = assertThrows(UxidException.class, () -> generator.generate(options));
    assertEquals(ErrorCode.RAND_SIZE_TOO_LARGE, refusal.code());
  }

  @Test // (sabotage: from skipped when rand is set -> the code assertion failed)
  void fromIsRefusedBeforeFixedRandomBytes() {
    Monotonic generator = new Monotonic(TestSources.refusingDraws());
    UxidOptions options =
        at(TIME).from("alice@example.com").rand(new byte[] {1, 2}).size(Size.S).build();

    UxidException refusal = assertThrows(UxidException.class, () -> generator.generate(options));
    assertEquals(ErrorCode.FROM_WITH_MONOTONIC, refusal.code());
  }

  @Test // (sabotage: state cleared on the from refusal -> the R + step id failed)
  void aRefusedCallLeavesTheGeneratorAsItWas() {
    TestSources.Replay draws = new TestSources.Replay(List.of(100L, 1L), List.of(5L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).prefix("usr").size(Size.S).build();

    generator.generate(options);
    assertThrows(
        UxidException.class,
        () ->
            generator.generate(at(TIME).prefix("usr").size(Size.S).from("a@example.com").build()));
    assertThrows(
        UxidException.class,
        () -> generator.generate(at(TIME).prefix("usr").size(Size.S).rand(new byte[2]).build()));
    assertThrows(
        UxidException.class,
        () -> generator.generate(at(TIME).prefix("usr").size(Size.S).randSize(11).build()));
    assertEquals(plain(at(TIME).prefix("usr").size(Size.S), 2, 105), generator.generate(options));
    assertEquals(List.of("seed", "step"), draws.calls);
  }

  @Test // (sabotage: state stored before the guard -> assertDoesNotThrow failed)
  void anOverflowOntoTheMarkerIsRefusedAndLeavesTheLastTime() {
    TestSources.Replay draws = new TestSources.Replay(List.of(0xfeL, 7L), List.of(2L, 1L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(LAST_COMPACT).prefix("usr").size(Size.XS).build();

    assertEquals(
        plain(at(LAST_COMPACT).prefix("usr").size(Size.XS).compactTime(true), 1, 0xfe),
        generator.generate(options));
    UxidException refusal = assertThrows(UxidException.class, () -> generator.generate(options));
    assertEquals(ErrorCode.COMPACT_TIME_OUT_OF_RANGE, refusal.code());
    assertEquals(
        plain(at(LAST_COMPACT).prefix("usr").size(Size.XS).compactTime(true), 1, 0xff),
        assertDoesNotThrow(() -> generator.generate(options)));
    assertEquals(List.of("seed", "step", "seed", "step"), draws.calls);
  }

  @Test // (sabotage: step range widened -> assertThrows saw nothing thrown)
  void aDrawOutsideItsRangeIsRefusedAndLeavesTheGeneratorAsItWas() {
    TestSources.Replay draws = new TestSources.Replay(List.of(100L), List.of(0L, 257L, 4L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(TIME).prefix("usr").size(Size.S).build();

    generator.generate(options);
    assertThrows(IllegalStateException.class, () -> generator.generate(options));
    assertThrows(IllegalStateException.class, () -> generator.generate(options));
    assertEquals(plain(at(TIME).prefix("usr").size(Size.S), 2, 104), generator.generate(options));
  }

  @Test // (sabotage: a short seed accepted -> assertThrows saw nothing thrown)
  void aSeedOfTheWrongLengthIsRefused() {
    UxidOptions options = at(TIME).size(Size.S).build();
    Monotonic shortSeed = new Monotonic(seeding(new byte[1]));
    Monotonic nullSeed = new Monotonic(seeding(null));
    Monotonic shortBytes = new Monotonic();
    UxidOptions shortSource =
        UxidOptions.builder().time(TIME).size(Size.S).byteSource(count -> new byte[0]).build();

    assertThrows(IllegalStateException.class, () -> shortSeed.generate(options));
    assertThrows(IllegalStateException.class, () -> nullSeed.generate(options));
    assertThrows(IllegalStateException.class, () -> shortBytes.generate(shortSource));
  }

  @Test // (sabotage: last-millisecond guard removed -> assertThrows saw a seed drawn)
  void anOverflowInTheLastMillisecondIsRefused() {
    long last = (1L << 48) - 1;
    TestSources.Replay draws = new TestSources.Replay(List.of(0xffffL), List.of(1L));
    Monotonic generator = new Monotonic(draws);
    UxidOptions options = at(last).size(Size.S).build();

    assertEquals("7zzzzzzzzzzzz1", generator.generate(options));
    assertThrows(IllegalArgumentException.class, () -> generator.generate(options));
  }

  @Test // (sabotage: clock read replaced by 0 -> the time range assertion failed)
  void aCallWithNoTimeReadsTheClock() {
    Monotonic generator = new Monotonic();
    long before = System.currentTimeMillis();
    String first = generator.generate();
    String second = generator.generate();
    long after = System.currentTimeMillis();

    assertEquals(26, first.length(), first);
    assertTrue(first.compareTo(second) < 0, first + " then " + second);
    DecodeResult.Decoded decoded = (DecodeResult.Decoded) Uxid.decode(first);
    long time = decoded.time().orElseThrow();
    assertTrue(time >= before && time <= after, first);
  }

  @Test // (sabotage: a new lock per call -> a thread's ids did not increase)
  void oneGeneratorSharedByThreadsGivesStrictlyIncreasingIds() throws Exception {
    int threads = 8;
    int perThread = 5_000;
    Monotonic generator = new Monotonic(constantDraws());
    UxidOptions options = at(TIME).prefix("usr").size(Size.XL).build();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<List<String>>> results = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  List<String> ids = new ArrayList<>(perThread);
                  for (int i = 0; i < perThread; i++) {
                    ids.add(generator.generate(options));
                  }
                  return ids;
                }));
      }
      start.countDown();
      Set<String> all = new HashSet<>();
      for (Future<List<String>> result : results) {
        List<String> ids = result.get(60, TimeUnit.SECONDS);
        for (int i = 1; i < ids.size(); i++) {
          assertTrue(ids.get(i - 1).compareTo(ids.get(i)) < 0, ids.get(i - 1) + " " + ids.get(i));
        }
        all.addAll(ids);
      }
      assertEquals(threads * perThread, all.size());
      assertTrue(
          all.contains(plain(at(TIME).prefix("usr").size(Size.XL), 10, threads * perThread - 1)));
    } finally {
      pool.shutdownNow();
    }
  }

  /** A builder with a fixed time and a byte source that fails the test if it is called. */
  private static UxidOptions.Builder at(long time) {
    return UxidOptions.builder().time(time).byteSource(TestSources.refusing());
  }

  /**
   * The id {@link Uxid#generate(UxidOptions)} makes for {@code builder} with its {@code width}-byte
   * random field fixed to {@code value}: what a monotonic call with that time and value answers.
   */
  private static String plain(UxidOptions.Builder builder, int width, long value) {
    byte[] field = new byte[width];
    long rest = value;
    for (int i = width - 1; i >= 0; i--) {
      field[i] = (byte) rest;
      rest >>>= 8;
    }
    return Uxid.generate(builder.rand(field).build());
  }

  /** A byte source answering each hex string in turn, asked for exactly its length. */
  private static ByteSource answering(String... hex) {
    List<byte[]> answers = new ArrayList<>();
    for (String h : hex) {
      answers.add(HexFormat.of().parseHex(h));
    }
    return count -> {
      assertTrue(!answers.isEmpty(), "the byte source was asked for more than its answers");
      byte[] answer = answers.remove(0);
      assertEquals(answer.length, count, "the count asked for");
      return answer;
    };
  }

  /** A draw source whose every seed is {@code seed} and every step one. */
  private static DrawSource seeding(byte[] seed) {
    return new DrawSource() {
      @Override
      public byte[] seed(int width) {
        return seed;
      }

      @Override
      public long step(int width) {
        return 1;
      }
    };
  }

  /** Seeds of zero and steps of one: safe to share, and any repeated id stays visible. */
  private static DrawSource constantDraws() {
    return seeding(new byte[10]);
  }
}
