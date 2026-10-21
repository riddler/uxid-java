package systems.riddler.uxid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object read strictly: every member the reader takes is marked, a member of the wrong type
 * fails, and {@link #done()} fails when the object still holds a member nobody took, so a case
 * carrying a key the runner does not know is a failure and a future corpus cannot be half-read.
 */
final class Fields {
  private final String where;
  private final Map<String, Object> members;
  private final Set<String> taken = new HashSet<>();

  private Fields(String where, Map<String, Object> members) {
    this.where = where;
    this.members = members;
  }

  /** Reads {@code text} as one JSON object. */
  static Fields parse(String text) {
    return of("the case", Json.parse(text));
  }

  @SuppressWarnings("unchecked")
  private static Fields of(String where, Object value) {
    if (!(value instanceof Map)) {
      throw new AssertionError(where + " is not an object");
    }
    return new Fields(where, (Map<String, Object>) value);
  }

  boolean has(String key) {
    return members.containsKey(key);
  }

  String string(String key) {
    Object value = required(key);
    if (!(value instanceof String text)) {
      throw wrongType(key, "a string");
    }
    return text;
  }

  /** A required member that is a string or {@code null}; {@code null} comes back as null. */
  String nullableString(String key) {
    Object value = required(key);
    if (value != null && !(value instanceof String)) {
      throw wrongType(key, "a string or null");
    }
    return (String) value;
  }

  /** An optional member that is a string or {@code null}; absent and {@code null} are null. */
  String optionalNullableString(String key) {
    return has(key) ? nullableString(key) : null;
  }

  /** An optional string member; absent is null. */
  String optionalString(String key) {
    return has(key) ? string(key) : null;
  }

  boolean bool(String key) {
    Object value = required(key);
    if (!(value instanceof Boolean flag)) {
      throw wrongType(key, "a boolean");
    }
    return flag;
  }

  /** An optional boolean member; absent is null. */
  Boolean optionalBool(String key) {
    return has(key) ? Boolean.valueOf(bool(key)) : null;
  }

  long integer(String key) {
    Object value = required(key);
    if (!(value instanceof Long number)) {
      throw wrongType(key, "an integer");
    }
    return number;
  }

  /** A required member that is an integer or {@code null}; {@code null} comes back as null. */
  Long nullableInteger(String key) {
    Object value = required(key);
    if (value != null && !(value instanceof Long)) {
      throw wrongType(key, "an integer or null");
    }
    return (Long) value;
  }

  /** An optional integer member; absent is null. */
  Long optionalInteger(String key) {
    return has(key) ? Long.valueOf(integer(key)) : null;
  }

  Fields object(String key) {
    return of(where + "." + key, required(key));
  }

  List<Long> integers(String key) {
    List<Long> numbers = new ArrayList<>();
    for (Object item : list(key)) {
      if (!(item instanceof Long number)) {
        throw wrongType(key, "a list of integers");
      }
      numbers.add(number);
    }
    return numbers;
  }

  List<String> strings(String key) {
    List<String> texts = new ArrayList<>();
    for (Object item : list(key)) {
      if (!(item instanceof String text)) {
        throw wrongType(key, "a list of strings");
      }
      texts.add(text);
    }
    return texts;
  }

  /** The members of a list of objects, each read strictly. */
  List<Fields> objects(String key) {
    List<Fields> items = new ArrayList<>();
    for (Object item : list(key)) {
      items.add(of(where + "." + key + "[]", item));
    }
    return items;
  }

  /** Fails when the object holds a member no reader took. */
  void done() {
    Set<String> unknown = new HashSet<>(members.keySet());
    unknown.removeAll(taken);
    if (!unknown.isEmpty()) {
      throw new AssertionError(where + " carries a key the runner does not know: " + unknown);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Object> list(String key) {
    Object value = required(key);
    if (!(value instanceof List)) {
      throw wrongType(key, "a list");
    }
    return (List<Object>) value;
  }

  private Object required(String key) {
    if (!members.containsKey(key)) {
      throw new AssertionError(where + " has no " + key);
    }
    taken.add(key);
    return members.get(key);
  }

  private AssertionError wrongType(String key, String expected) {
    return new AssertionError(where + "." + key + " is not " + expected);
  }
}
