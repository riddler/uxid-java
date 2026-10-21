package systems.riddler.uxid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads the vendored conformance corpus from {@code conformance/}, relative to the project
 * directory the tests run in, and computes its hash by the corpus README's rule.
 */
final class Corpus {
  private static final Path DIR = Path.of("conformance");

  private Corpus() {}

  /** One file entry of {@code manifest.json}. */
  record Entry(String kind, String file, long caseCount) {}

  /** The parsed {@code manifest.json}. */
  record Manifest(long version, String corpusHash, List<Entry> files) {}

  static byte[] bytes(String name) {
    try {
      return Files.readAllBytes(DIR.resolve(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String text(String name) {
    return new String(bytes(name), StandardCharsets.UTF_8);
  }

  /**
   * The lines of a case file: it is split on newlines and each line is ended by one, so the text
   * after the last newline is empty and is not a case.
   */
  static List<String> lines(String name) {
    String text = text(name);
    if (!text.endsWith("\n")) {
      throw new AssertionError(name + " does not end with a newline");
    }
    List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
    lines.remove(lines.size() - 1);
    return lines;
  }

  static Manifest manifest() {
    Fields root = Fields.parse(text("manifest.json"));
    long version = root.integer("corpus_version");
    String hash = root.string("corpus_hash");
    List<Entry> files = new ArrayList<>();
    for (Fields entry : root.objects("files")) {
      files.add(new Entry(entry.string("kind"), entry.string("file"), entry.integer("case_count")));
      entry.done();
    }
    root.done();
    return new Manifest(version, hash, files);
  }

  /**
   * The corpus hash of these files: {@code sha256:} and the lowercase hex SHA-256 of their bytes
   * concatenated in the order given.
   */
  static String hash(List<String> files) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String file : files) {
        digest.update(bytes(file));
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
