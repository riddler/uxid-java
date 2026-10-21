### Added

- `UxidOptions.Builder.from(String)` asks for a deterministic id: the same prefix, input and size always give the same id, a `z` (or `Z`) marker followed by hash characters from SHA-256 at the size's standard length, such as `usr_zcvt7epac0t1ebcsjfyf7cwz25` for `usr` and `alice@example.com` at `xl`.
- A deterministic request does not read the clock, the time form, the random width or the byte source; fixed random bytes with it are refused with `rand_with_from`, and an input or prefix holding an unpaired surrogate, which has no UTF-8 encoding, is refused with `IllegalArgumentException` rather than hashed with a replacement character.
