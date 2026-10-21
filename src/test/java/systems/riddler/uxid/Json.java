package systems.riddler.uxid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader for the conformance corpus, so the tests need no dependency. It reads
 * objects as {@link Map}, arrays as {@link List}, strings, booleans, {@code null} and integers (as
 * {@link Long}). It refuses what the corpus never holds: a number with a fraction or an exponent, a
 * duplicate key, a trailing comma and anything after the value.
 */
final class Json {
  private final String text;
  private int pos;

  private Json(String text) {
    this.text = text;
  }

  /** Reads one JSON value that fills {@code text}, or throws {@link IllegalArgumentException}. */
  static Object parse(String text) {
    Json reader = new Json(text);
    Object value = reader.value();
    reader.whitespace();
    if (reader.pos != text.length()) {
      throw reader.fail("text after the value");
    }
    return value;
  }

  private Object value() {
    whitespace();
    if (pos >= text.length()) {
      throw fail("no value");
    }
    char c = text.charAt(pos);
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> word("true", true);
      case 'f' -> word("false", false);
      case 'n' -> word("null", null);
      default -> number();
    };
  }

  private Map<String, Object> object() {
    Map<String, Object> members = new LinkedHashMap<>();
    pos++;
    if (peek('}')) {
      pos++;
      return members;
    }
    while (true) {
      if (!peek('"')) {
        throw fail("an object key");
      }
      String key = string();
      expect(':');
      if (members.containsKey(key)) {
        throw fail("duplicate key " + key);
      }
      members.put(key, value());
      if (peek(',')) {
        pos++;
      } else {
        expect('}');
        return members;
      }
    }
  }

  private List<Object> array() {
    List<Object> items = new ArrayList<>();
    pos++;
    if (peek(']')) {
      pos++;
      return items;
    }
    while (true) {
      items.add(value());
      if (peek(',')) {
        pos++;
      } else {
        expect(']');
        return items;
      }
    }
  }

  private String string() {
    StringBuilder out = new StringBuilder();
    pos++;
    while (true) {
      if (pos >= text.length()) {
        throw fail("an unterminated string");
      }
      char c = text.charAt(pos++);
      if (c == '"') {
        return out.toString();
      }
      if (c < 0x20) {
        throw fail("a control character in a string");
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (pos >= text.length()) {
        throw fail("an unterminated escape");
      }
      char escaped = text.charAt(pos++);
      switch (escaped) {
        case '"', '\\', '/' -> out.append(escaped);
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          if (pos + 4 > text.length()) {
            throw fail("a short unicode escape");
          }
          out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          pos += 4;
        }
        default -> throw fail("an unknown escape");
      }
    }
  }

  private Long number() {
    int start = pos;
    if (peek('-')) {
      pos++;
    }
    int digits = pos;
    while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
      pos++;
    }
    if (pos == digits) {
      throw fail("a value");
    }
    if (pos < text.length() && ".eE".indexOf(text.charAt(pos)) >= 0) {
      throw fail("a number that is not an integer");
    }
    return Long.valueOf(text.substring(start, pos));
  }

  private Object word(String word, Object value) {
    if (!text.startsWith(word, pos)) {
      throw fail("a value");
    }
    pos += word.length();
    return value;
  }

  private void whitespace() {
    while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
      pos++;
    }
  }

  private boolean peek(char c) {
    whitespace();
    return pos < text.length() && text.charAt(pos) == c;
  }

  private void expect(char c) {
    if (!peek(c)) {
      throw fail("'" + c + "'");
    }
    pos++;
  }

  private IllegalArgumentException fail(String what) {
    return new IllegalArgumentException("bad JSON at offset " + pos + ": expected " + what);
  }
}
