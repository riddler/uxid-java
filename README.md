# uxid

[![MIT License][badge_license_url]](LICENSE)
[![CI][badge_ci_url]](https://github.com/riddler/uxid-java/actions/workflows/ci.yml)

User eXperience focused IDentifiers for Java: prefixed, K-sortable,
Stripe-style ids a person can read, copy and route back to their resource. An
id looks like `usr_01hf7yat0004h1`: the prefix names the resource, and the
body carries a time and randomness in lower-case Crockford Base32. The ids
follow the wire format of the Elixir reference implementation,
[uxid-ex](https://github.com/riddler/uxid-ex), and the package is checked
against that implementation's conformance corpus, every case, with no skip
list.

## Why UXID

An auto-increment key leaks how many rows a table holds and invites
enumeration; a random UUID fixes both but is long, unordered and anonymous, so
in a log line, a URL or a support thread nobody can tell what it points at, and
a double-click selects only part of it. A UXID names its resource in its
prefix, selects whole on a double-click, reads aloud without ambiguity, sorts
by creation time so it indexes well, and is generated in the application with
no coordination between nodes. Its size is tunable for low-cardinality
resources, a monotonic generator keeps a burst within one millisecond unique
and in order, and a deterministic mode maps the same input to the same id.

Because the format is shared, an id this package makes is read by the other
implementations of the format, and the other way round.

## Install

Maven:

```xml
<dependency>
  <groupId>systems.riddler</groupId>
  <artifactId>uxid</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle (Kotlin or Groovy DSL, on `mavenCentral()`):

```kotlin
implementation("systems.riddler:uxid:1.0.0")
```

## Basic usage

```java
import systems.riddler.uxid.DecodeResult;
import systems.riddler.uxid.Monotonic;
import systems.riddler.uxid.Size;
import systems.riddler.uxid.Uxid;
import systems.riddler.uxid.UxidOptions;

public class Example {
  public static void main(String[] args) {
    // A prefix names the resource; the body is a time and randomness.
    String id = Uxid.generate(UxidOptions.builder().prefix("usr").build());
    System.out.println(id.startsWith("usr_")); // true

    // Shrink the random part for low-cardinality resources.
    // Sizes: XS, S, M, L and XL (the default).
    String small = Uxid.generate(UxidOptions.builder().prefix("cus").size(Size.S).build());
    System.out.println(small.length() == "cus_".length() + 14); // true

    // Deterministic: the same prefix and input give the same id, every time.
    String named =
        Uxid.generate(UxidOptions.builder().prefix("usr").from("alice@example.com").build());
    System.out.println(named); // usr_zcvt7epac0t1ebcsjfyf7cwz25
    System.out.println(Uxid.isDeterministic(named)); // true

    // Read an id back. A malformed id is a result naming a reason, never an exception.
    DecodeResult result = Uxid.decode("cus_01hf7yat0004h1");
    if (result instanceof DecodeResult.Decoded decoded) {
      System.out.println(decoded.prefix()); // Optional[cus]
      System.out.println(decoded.size()); // S
      System.out.println(decoded.time()); // OptionalLong[1700000000000]
    }
    System.out.println(Uxid.isValid("cus_01hf7yat0004h1", "_", "cus")); // true
    System.out.println(Uxid.decode("cus_")); // Invalid[reason=EMPTY]

    // Monotonic: ids from one generator increase, also within one millisecond.
    // You hold the generator; the package keeps no global state.
    Monotonic ids = new Monotonic();
    UxidOptions options = UxidOptions.builder().prefix("usr").size(Size.M).build();
    String first = ids.generate(options);
    String second = ids.generate(options);
    System.out.println(first.compareTo(second) < 0); // true
  }
}
```

A generation request the wire format refuses, such as a random width above 10
bytes, throws a `UxidException`, an `IllegalArgumentException`; switch on its
`code()`, an `ErrorCode`, or log its `wireName()`, a stable `snake_case` name,
rather than its message. A malformed argument of another kind, such as a null
prefix or a time outside the 48-bit range, throws the usual
`NullPointerException` or `IllegalArgumentException`.

`Uxid.generate` and `Monotonic.generate` read the system clock when they need a
time, and a `SecureRandom` when they need random bytes. `UxidOptions.Builder.time`
and `byteSource` replace them; `DecodeOptions.Builder.clock` replaces the
`java.time.Clock` that `Uxid.decode` reads for a compact time. `rand` takes
fixed random bytes for test vectors: an id built from them is exactly as
guessable as those bytes.

`Uxid.decode` never throws on malformed input. It answers a
`DecodeResult.Invalid` whose `InvalidReason` is the first rule the string
breaks, in the order `empty`, `invalid_length`, `invalid_character`. A compact
time carries only its low 40 bits, so a decoded compact time takes the rest
from the clock; the arithmetic is in `long`, so a clock within about 2^40
milliseconds of `Long.MIN_VALUE` wraps silently, which no real clock does.

`Uxid.isValid(id, delimiter, requiredPrefix)` and
`Uxid.isDeterministic(id, delimiter)` take the delimiter the id was generated
with, and read no clock. `isValid` answers whether the id decodes (a body of a
length the format defines, in Crockford Base32 characters) and, given a
required prefix, whether it carries exactly that prefix. `isDeterministic`
answers whether the id decodes and its body starts with the marker `z` or `Z`.
Both are stricter than the Elixir reference, whose `valid?` accepts any
alphabet body of at least 8 characters and whose `deterministic?` checks only
the first body character: here a malformed string is neither valid nor
deterministic. The released TypeScript, Rust and .NET ports read them the
same way, and the conformance corpus does not fix these two answers.

A deterministic id hashes the prefix and the input as UTF-8, so a prefix or an
input holding an unpaired surrogate, which has no UTF-8 encoding, is refused
with an `IllegalArgumentException`. A time-based id writes its prefix as given
and accepts one.

A `Monotonic` keeps the last time and random value for each prefix and random
width it is given, and the pair of time and random value increases with each
id (so does the id string, within one letter case, delimiter and time form).
The random value alone does not: when the clock reaches a later millisecond,
or the field overflows into the next one, the id carries a fresh seed, which
can be smaller than the last value. That state grows by one entry for each
distinct prefix and random width the generator is given and lives as long as
the generator.

## Compatibility

- **Java 17 and later.** The jars are built for `maven.compiler.release` 17.
  CI proves the floor by running the tests on Temurin 17, 21 and 25.
- **No dependencies.** The package has no runtime dependency; JUnit 5 is a test
  dependency only.
- **Module `systems.riddler.uxid`.** The jar declares the module and exports its
  one package, `systems.riddler.uxid`. On the class path it is an ordinary
  package of the same name.
- **Thread safety.** A `Monotonic` is safe for concurrent use: a lock inside
  the instance orders its calls, so its ids increase in the order the calls
  take the lock, whichever thread asks. Two generators never affect each
  other, and ids from two generators carry no order between them within a
  millisecond. `Uxid.generate`, `Uxid.decode` and the validity checks keep no
  state and can be called from any thread.

## Documentation

- Look up
  - The API reference is the Javadoc shipped with the package as a Javadoc
    jar: every public type and member carries it, and a missing comment fails
    the build.
  - [The wire format](docs/wire-format.md): where the normative layout of an
    id lives, and which version of it this package follows.
  - [The changelog](CHANGELOG.md): what changed in each version.
  - [The conformance corpus](conformance): the cases every implementation of
    the wire format passes, vendored here from uxid-ex.
- Understand
  - [The reference spec, at v2.10.0][guide_wire_format_url]: the normative
    layout of an id - alphabet, bit packing, sizes, prefix grammar,
    deterministic and monotonic bodies, and decoding.
  - [Why a UXID has a prefix, a time and randomness][guide_why_url]: what each
    part of an id is for, and what you trade when you tune it.
  - [Why monotonic mode exists and what it costs][guide_monotonic_url]:
    same-millisecond uniqueness and ordering, and the security tradeoff.
  - [_Designing APIs for humans: object IDs_][stripe_ids_url]: the Stripe id
    design many of UXID's choices follow.

## Development

After checking out the repository, run `mise install` for the JDK and Maven
`mise.toml` pins. Then `mise run gate:loop` runs the inner loop (compile and
tests), and `mise run gate` runs the full quality gate: the format check, the
compile with Error Prone and every javac lint, the tests with 90 percent line
coverage, the Javadoc and sources jars with the Javadoc lint on, and the
conformance corpus check. The tests include the conformance corpus, every case
with no skip list.

A release is published by CI, never from a checkout: pushing a tag `vX.Y.Z`
that names the version in `pom.xml` runs `.github/workflows/release.yml`, which
publishes the package to Maven Central.

## Contributing

Bug reports and pull requests are welcome on GitHub at
https://github.com/riddler/uxid-java.

## License

The package is available as open source under the terms of the
[MIT License](https://opensource.org/licenses/MIT).

<!-- LINKS -->
[guide_wire_format_url]: https://github.com/riddler/uxid-ex/blob/v2.10.0/guides/wire-format.md
[guide_why_url]: https://github.com/riddler/uxid-ex/blob/v2.10.0/guides/why-prefix-time-randomness.md
[guide_monotonic_url]: https://github.com/riddler/uxid-ex/blob/v2.10.0/guides/monotonic.md
[stripe_ids_url]: https://dev.to/stripe/designing-apis-for-humans-object-ids-3o5a

<!-- BADGES -->
[badge_license_url]: https://img.shields.io/badge/license-MIT-brightgreen.svg
[badge_ci_url]: https://github.com/riddler/uxid-java/actions/workflows/ci.yml/badge.svg
