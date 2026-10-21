# Changelog

## 1.0.0 - 2026-10-21

The first release of the Java port of uxid: prefixed, K-sortable identifiers
a person can read, wire-compatible with the Elixir `uxid` package. The Maven
coordinate is `systems.riddler:uxid`, and the module and its one exported
package are `systems.riddler.uxid`. It runs on Java 17 or later and has no
runtime dependencies. It covers time-based generation and deterministic ids
(`Uxid.generate()` and `Uxid.generate(UxidOptions)`; `UxidOptions.builder()`
and the `UxidOptions.Builder` methods `prefix`, `size`, `randSize`,
`compactTime`, `letterCase`, `delimiter`, `time`, `rand`, `byteSource` and
`from`; the enums `Size` and `LetterCase`; the `ByteSource` interface),
decoding (`Uxid.decode(String)` and `Uxid.decode(String, DecodeOptions)`;
`DecodeOptions.builder()` and the `DecodeOptions.Builder` methods `delimiter`
and `clock`; the sealed `DecodeResult` with its records
`DecodeResult.Decoded`, read through `prefix()`, `size()`, `compact()`,
`deterministic()` and `time()`, and `DecodeResult.Invalid`, read through
`reason()`; the `InvalidReason` enum), the validity checks (`Uxid.isValid`
and `Uxid.isDeterministic`), the monotonic generator (`Monotonic`, built with
`new Monotonic()` or `new Monotonic(DrawSource)`, with its `generate()` and
`generate(UxidOptions)` and the `DrawSource` interface's `seed` and `step`),
and the generation refusals (`UxidException` and its `code()`, an
`ErrorCode`). It is checked against the conformance corpus vendored from
riddler/uxid-ex at the tag `v2.10.0` (`conformance/SOURCE.json` records the
tag, its commit and the corpus hash), and every case in it passes with no
skip list.

### Added

- `Monotonic` is a generator the caller holds whose ids for one prefix and random width increase, also within one millisecond and when the clock moves back (the pair of time and random value increases, and the id string with it within one letter case, delimiter and time form; a fresh seed after a later clock or an overflow can be a smaller random value on its own): `generate()` and `generate(UxidOptions)` keep the last time and random value per prefix and random width, step the random field by a value drawn uniformly from 1 to 2^(4w) within a millisecond, and move the time one millisecond forward with a fresh seed when the field overflows. A monotonic `xs` uses compact time unless the options set the time form.
- One `Monotonic` may be shared between threads: a lock inside the instance orders the calls, so its ids still increase in the order the calls take the lock. Its state grows by one entry for each distinct prefix and random width it is given and lives as long as the generator. Nothing is kept in a static field, and two generators never affect each other.
- `DrawSource` replaces a generator's seeds and steps to replay known draws; by default they come from each call's byte source, the step masked from its bytes with no modulo bias.
- `Monotonic.generate(UxidOptions)` refuses a `from` input with `from_with_monotonic` and fixed random bytes with `rand_with_monotonic`. A call that throws leaves the generator's state as it was.
- `Uxid.generate()` makes a time-based id with no prefix, size `xl`, a standard time read from the clock and ten random bytes from `SecureRandom`.
- `Uxid.generate(UxidOptions)` takes a prefix (`""` is none), a `Size` (`Size.parse` reads `xs`, `s`, `m`, `l`, `xl` and the long spellings `xsmall` to `xlarge`), a random width, compact time, a `LetterCase`, a delimiter (`_` by default), a fixed time and fixed random bytes, the last for reproducible test vectors only.
- `ByteSource`, a functional interface taking a count, supplies the random bytes; the default, `ByteSource.secureRandom()`, draws from `SecureRandom`.
- A generation request the wire format refuses throws `UxidException`, an `IllegalArgumentException` whose `code()` is an `ErrorCode`; each code's `wireName()` is its name in the wire format's closed set (`compact_time_out_of_range`, `rand_width_mismatch`, `rand_size_too_large`, `from_with_monotonic`, `rand_with_from`, `rand_with_monotonic`).
- `UxidOptions.Builder.from(String)` asks for a deterministic id: the same prefix, input and size always give the same id, a `z` (or `Z`) marker followed by hash characters from SHA-256 at the size's standard length, such as `usr_zcvt7epac0t1ebcsjfyf7cwz25` for `usr` and `alice@example.com` at `xl`.
- A deterministic request does not read the clock, the time form, the random width or the byte source; fixed random bytes with it are refused with `rand_with_from`, and an input or prefix holding an unpaired surrogate, which has no UTF-8 encoding, is refused with `IllegalArgumentException` rather than hashed with a replacement character.
- `Uxid.decode(String)` and `Uxid.decode(String, DecodeOptions)` read an id back to a `DecodeResult`: a `DecodeResult.Decoded` with the prefix, the size, whether the time is compact, whether the body is deterministic and the time in milliseconds, or a `DecodeResult.Invalid` whose `InvalidReason` is `empty`, `invalid_length` or `invalid_character`, the first rule the body breaks in that order. Malformed input is answered, never thrown.
- `DecodeOptions` takes the delimiter and a `java.time.Clock` that supplies a compact time's high bits; a compact time more than one day ahead of the clock is read from the 2^40 ms window before, which with a clock in the first window after the Unix epoch is a negative time.
- The body lengths 10 and 18 always decode as standard time, so a compact `xs` or compact `m` id decodes to its size, not compact, with a time that is not the one it was generated at.
- `Uxid.isValid(String)` and `Uxid.isValid(String, String delimiter, String requiredPrefix)` answer whether a string is a well-formed id and, given a prefix, carries exactly that prefix; `Uxid.isDeterministic(String)` and `Uxid.isDeterministic(String, String delimiter)` answer whether it is a well-formed deterministic id. Neither reads a clock.
