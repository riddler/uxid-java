package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The vendored corpus is the one its manifest and {@code conformance/SOURCE.json} describe. The
 * hash is recomputed by the reference's rule (the corpus README, "The format" and "Running the
 * cases"): {@code sha256:} and the lowercase hex SHA-256 of the case files' bytes concatenated in
 * the order of the manifest's files; each {@code case_count} is the number of lines of its file. A
 * mismatch means the vendored copy is not the one recorded; it is never repaired by rewriting a
 * hash.
 */
class CorpusHashTest {
  private static final List<String> CASE_FILES =
      List.of("encode.json", "deterministic.json", "decode.json", "invalid.json", "monotonic.json");
  private static final List<String> KINDS =
      List.of("encode", "deterministic", "decode", "invalid", "monotonic");

  @Test // (sabotage: each file hashed twice -> the manifest hash assertion failed)
  void theCaseFilesHashToTheManifestsCorpusHash() {
    Corpus.Manifest manifest = Corpus.manifest();

    assertEquals(
        manifest.corpusHash(),
        Corpus.hash(manifest.files().stream().map(Corpus.Entry::file).toList()));
  }

  @Test // (sabotage: trailing empty line kept -> a case count assertion failed)
  void eachCaseCountIsTheNumberOfLinesOfItsFile() {
    for (Corpus.Entry entry : Corpus.manifest().files()) {
      assertEquals(entry.caseCount(), Corpus.lines(entry.file()).size(), entry.file());
    }
  }

  @Test // (sabotage: expected file order swapped -> the files list assertion failed)
  void theManifestListsExactlyTheFilesTheRunnerRuns() {
    Corpus.Manifest manifest = Corpus.manifest();

    assertEquals(1, manifest.version());
    assertEquals(CASE_FILES, manifest.files().stream().map(Corpus.Entry::file).toList());
    assertEquals(KINDS, manifest.files().stream().map(Corpus.Entry::kind).toList());
  }

  @Test // (sabotage: SOURCE.json hash edited -> the recorded hash assertion failed)
  void sourceJsonRecordsTheSameHashAndTheTagItCameFrom() {
    Fields source = Fields.parse(Corpus.text("SOURCE.json"));
    String repo = source.string("repo");
    String tag = source.string("tag");
    String sha = source.string("sha");
    String recorded = source.string("corpus_hash");
    source.done();

    assertEquals("riddler/uxid-ex", repo);
    assertTrue(tag.startsWith("v"), tag);
    assertTrue(sha.matches("[0-9a-f]{40}"), sha);
    assertEquals(Corpus.manifest().corpusHash(), recorded);
    assertEquals(recorded, Corpus.hash(CASE_FILES));
  }

  @Test // (sabotage: a carriage return added to a case file -> the line ending assertion failed)
  void theCaseFilesAreCheckedInWithNewlineLineEndings() {
    for (String file : CASE_FILES) {
      assertFalse(Corpus.text(file).contains("\r"), file);
    }
  }
}
