### Added

- `Monotonic` is a generator the caller holds whose ids for one prefix and random width increase strictly, also within one millisecond and when the clock moves back: `generate()` and `generate(UxidOptions)` keep the last time and random value per prefix and random width, step the random field by a value drawn uniformly from 1 to 2^(4w) within a millisecond, and move the time one millisecond forward with a fresh seed when the field overflows. A monotonic `xs` uses compact time unless the options set the time form.
- One `Monotonic` may be shared between threads: a lock inside the instance orders the calls, so its ids still increase strictly. Nothing is kept in a static field, and two generators never affect each other.
- `DrawSource` replaces a generator's seeds and steps to replay known draws; by default they come from each call's byte source, the step masked from its bytes with no modulo bias.
- `Monotonic.generate(UxidOptions)` refuses a `from` input with `from_with_monotonic` and fixed random bytes with `rand_with_monotonic`. A call that throws leaves the generator's state as it was.
