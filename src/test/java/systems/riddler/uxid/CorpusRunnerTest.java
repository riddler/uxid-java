package systems.riddler.uxid;

import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every case of every vendored case file: one parameterised test per file, one invocation per
 * line, named by the case id. There is no skip list.
 */
class CorpusRunnerTest {

  @ParameterizedTest // (sabotage: case forced to lower -> upper encode cases failed)
  @MethodSource("encodeCases")
  void everyEncodeCaseGivesItsId(String line) {
    CorpusCases.encode(line);
  }

  @ParameterizedTest // (sabotage: from given an extra x -> every deterministic case failed)
  @MethodSource("deterministicCases")
  void everyDeterministicCaseGivesItsId(String line) {
    CorpusCases.deterministic(line);
  }

  @ParameterizedTest // (sabotage: compact flag negated -> decode cases failed)
  @MethodSource("decodeCases")
  void everyDecodeCaseReadsItsAnswer(String line) {
    CorpusCases.decode(line);
  }

  @ParameterizedTest // (sabotage: reason always empty -> other invalid cases failed)
  @MethodSource("invalidCases")
  void everyInvalidCaseIsRefusedWithItsError(String line) {
    CorpusCases.invalid(line);
  }

  @ParameterizedTest // (sabotage: call time plus one -> the monotonic cases failed)
  @MethodSource("monotonicCases")
  void everyMonotonicCaseGivesItsIds(String line) {
    CorpusCases.monotonic(line);
  }

  static Stream<Named<String>> encodeCases() {
    return cases("encode.json");
  }

  static Stream<Named<String>> deterministicCases() {
    return cases("deterministic.json");
  }

  static Stream<Named<String>> decodeCases() {
    return cases("decode.json");
  }

  static Stream<Named<String>> invalidCases() {
    return cases("invalid.json");
  }

  static Stream<Named<String>> monotonicCases() {
    return cases("monotonic.json");
  }

  private static Stream<Named<String>> cases(String file) {
    return Corpus.lines(file).stream().map(line -> Named.of(CorpusCases.id(line), line));
  }
}
