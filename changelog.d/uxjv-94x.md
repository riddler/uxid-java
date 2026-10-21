### Added

- `Uxid.generate()` makes a time-based id with no prefix, size `xl`, a standard time read from the clock and ten random bytes from `SecureRandom`.
- `Uxid.generate(UxidOptions)` takes a prefix (`""` is none), a `Size` (`Size.parse` reads `xs`, `s`, `m`, `l`, `xl` and the long spellings `xsmall` to `xlarge`), a random width, compact time, a `LetterCase`, a delimiter (`_` by default), a fixed time and fixed random bytes, the last for reproducible test vectors only.
- `ByteSource`, a functional interface taking a count, supplies the random bytes; the default, `ByteSource.secureRandom()`, draws from `SecureRandom`.
- A generation request the wire format refuses throws `UxidException`, an `IllegalArgumentException` whose `code()` is an `ErrorCode`; each code's `wireName()` is its name in the wire format's closed set (`compact_time_out_of_range`, `rand_width_mismatch`, `rand_size_too_large`, `from_with_monotonic`, `rand_with_from`, `rand_with_monotonic`).
