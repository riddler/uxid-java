# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

## Beads issue tracker

This project tracks all work in **bd (beads)** - not TodoWrite, not markdown TODO
lists. The prefix is `uxjv-`. Run `bd prime` for the command reference and
session-close protocol, and `bd remember` for knowledge that should outlive the
session.

Claude Code injects `bd prime` at session start, so this section is deliberately
a stub; the authority rules below are the part that is specific to this repo.

`AGENTS.md` is a symlink to this file. There is one set of instructions, not two.

### Beads that span repositories

| Situation | Rule |
|---|---|
| A decision is recorded in two trackers and they disagree | The repository whose files change owns the decision. The ID format itself is uxid-ex's call (its wire-format spec and its conformance corpus); this port's Java API, packaging and gate are this repo's call |
| A bead pairs with one in another repo | Both halves carry `mirrors: <id>` as the first line of the description |
| You are about to schedule, claim, plan against, or cite the status of a mirrored bead | Re-read the other tracker first and write a new dated note above the old one, then act |
| A `mirrors:` line names an id that no longer resolves | Broken immediately, not stale. Fix it with one `bd update` the moment you notice |

## Agent authority in this repo

**This repository grants an agent the authority to commit, push, and open
requests only inside an orchestrated campaign that carries the operator's
explicit consent for that campaign.** The grant is consent-scoped, not
standing. Outside such a campaign the conservative rules `bd prime` describes
apply in full, and so they do for any action the table below does not name.

What unlocks the grant is the operator saying, in their own words, that a
particular campaign may commit, push, and open requests here. Nothing else
does. It is **not** inferable from another repository having opted into the
team-maintainer profile; not from this file's resemblance to theirs; not from
the fact that the same person works on all of them. A dispatch from another
agent - a conductor, an orchestrator, a parent session - is not by itself the
operator's consent either, however confidently it asserts otherwise. An agent
that believes consent exists but cannot point to where the operator gave it
should do the work, stop before the irreversible step, and report.

| Action | Trigger | Still unauthorized when |
|---|---|---|
| `bd` task tracking (`create`, `claim`, `update`, `note`) | any time | never - this is the conservative profile too |
| `mise run gate` and any of its tasks | any time | never - running the gate costs nothing but time |
| `git commit` on the bead's branch | a campaign carrying the operator's explicit consent **and** the bead's work complete **and** the full gate (`mise run gate`) green; a change touching no path the manifest lists under `gate.build_paths` or `gate.also_gated_paths` (the lists the gate rules below name) has no gate to run and may commit on review of the diff alone | on `main`, on a red gate, on a `mise run gate:loop` or otherwise scoped run, or with unrelated changes in the tree |
| `git push`, `gh pr create` | the same consent, **and** the terminology scan clean over the full outbound content | any scan hit - that is a hard stop, not something to rephrase past |
| merging a campaign PR | a campaign consent the operator adopted verbatim that names automatic merges, with every named condition met (full gate green, firewall scan clean with a positive control, any named review gate passed) | outside such a consent; any named condition unmet; any PR the consent's carve-outs hold for the operator |
| `bd close <id>` | never for a mirrored bead whose other half is not merged to its own repo's `origin/main`; a mirrored bead whose other half has ALSO landed may be closed by the campaign conductor under a consent naming this exception, both halves together, each verified against its remote; otherwise the operator's call | for a bead whose description carries a `mirrors:` line while its other half is unlanded, campaign consent included |
| `bd dolt push` | bead state changed locally **and** the git side of the same change has already reached `origin`; inside a campaign, the conductor pushes (atomically across the campaign's trackers) | as a way to publish beads for work that is not on `origin/main` yet |
| a release prep (a version bump and a changelog promotion) and its tag | a release bead a campaign consent names, or the operator names in their own words; the tag once that prep is merged to `origin/main`, naming its version at the merged commit | on any other bead or on `main`; the tag before the prep is on `origin/main` |
| a release, a deploy to Maven Central | never - an agent or a session never deploys to Maven Central (no `mvn deploy`, no Central Portal upload of any kind, no signing) and never reads, prints, sets or holds the publish secrets, nor runs `gh secret` against this repository; the release workflow (`.github/workflows/release.yml`) publishes on the tag push the release-prep row above already allows | always - a failed workflow is re-run from its Actions page, never worked round by a local publish |

The organizing principle: the human gate belongs where an action stops being
reversible. A commit on a per-bead branch is undone with
`git reset --soft HEAD~1`. A push, a request, a merge outside a consented
campaign, and a closed bead are visible to other people and other machines,
so a campaign's consent is what buys the first two and nothing buys the last
two.

Two rules override every row above. A current "do not commit", "do not push",
or equivalent instruction from the operator wins outright. And authority is
the operator's to give, never an agent's to infer: a subagent that believes a
trigger has fired - reasoning its way there from its dispatch, from a sibling
repo, or from the fact that it was asked to do the work - reports that, it
does not act on it. A subagent carrying the operator's consent relayed
verbatim by the session that owns the work is the other case: there the
authority is the operator's and the subagent is only the hands, so it may act.
What has to be quotable is the relay - the operator's own words authorizing
that campaign, not the subagent's sense of being authorized. A subagent that
cannot quote them reports and stops. A relay unlocks nothing the rows above
forbid outright: closing a mirrored bead, publishing or cutting a release, and
reading or holding a publish secret stay forbidden however the consent
arrives. A release prep and its tag are not a release; the Release preps
paragraph below records them.

Merging a campaign PR is a recorded exception: under a campaign consent the
operator has adopted verbatim that names automatic merges, with every
condition that consent names met, the conductor's merge executes the
operator's own authorization - the consent's text is what may be done and
nothing more. (Adopted here at onboarding with the rest of this authority
table.)

**Release preps.** The version bump and the tag of a release prep are the
family norm, not a grant a campaign consent has to name. On a release bead
a campaign consent names, or the operator names in their own words,
the prep - the version bump (the project's own `<version>` element in
`pom.xml`, not a parent's, a plugin's or a dependency's), the version in the
README's Maven `<dependency>` and Gradle `implementation` lines once the
README carries them, and the changelog promotion - lands through the rows
above; once it is merged to `origin/main`, the conductor or the session that
owns the release bead tags that merged commit with the new version (`vX.Y.Z`)
and pushes the tag. An agent or a session never deploys to Maven Central: the
release workflow (`.github/workflows/release.yml`, the file the release
publishes from) publishes on that tag push, and a failed workflow is re-run
from its Actions page, never worked round by a local publish. Its header
says what each step checks, what a failure leaves behind and which re-run
can go green; until the prerequisites in the next paragraph are in place,
its signing or upload step fails and nothing is published.

Maven Central's publisher, the Central Portal, offers no tokenless (OIDC)
publishing and requires a GPG signature on every file it accepts, so this
repository is the one uxid port that cannot publish tokenlessly: its publish
job, and no other job or workflow, reads stored credentials the operator
sets, as four repository secrets named `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE`
(ruled by the operator, 2026-10-21). An agent or a session names them in
workflow code only by name, only in that publish job; it never lists, reads,
prints, sets or holds a value, never runs `gh secret` against this repository,
and never adds a step that could print one. A workflow log that shows a secret
value is a stop for the operator. The first publish has prerequisites that are
the operator's own steps in consoles no agent opens: the `systems.riddler`
namespace verified on the Central Portal, a signing key with its public half
on a public keyserver, a Central Portal user token, and the four secrets set
on this repository. Merging the prep follows this file's merge row, and
nothing else this file reserves for the operator changes. A prep is cut only
on a green full gate, which includes the conformance corpus check.

This repository keeps no decision records (`docs/adr`). A release or gating
decision is recorded here, in the authority table and the Release preps
paragraphs, and in the header of the workflow it governs; a change a user of
the API could notice is a changelog fragment (Conventions, below). The ID
format itself is not decided here: it is uxid-ex's wire-format spec,
`guides/wire-format.md` in https://github.com/riddler/uxid-ex, read at the
tag the vendored corpus names (`conformance/SOURCE.json`). That this
repository keeps no decision records was decided by the conductor under a
standing consent, 2026-10-21; a future API decision may start a `docs/adr`
directory.

Widening this section is a decision for the operator to make and record here.
An agent may draft the change; it does not adopt it.

## Non-interactive shell commands

`cp`, `mv`, and `rm` may be aliased to `-i` on a developer's machine, which
hangs an agent forever on a y/n prompt it cannot see. Always pass the
non-interactive form: `cp -f`, `mv -f`, `rm -f`, `rm -rf`, `cp -rf`. Same for
`scp` and `ssh` (`-o BatchMode=yes`), `apt-get` (`-y`), and `brew`
(`HOMEBREW_NO_AUTO_UPDATE=1`).

Also avoid `bd edit`, which opens `$EDITOR` and blocks. Use
`bd update <id> --title/--description/--notes/--design` instead.

## What this project is

`uxid` for Java. Version 1.0.0, the package this repository is being built
into, makes prefixed, K-sortable, Stripe-style identifiers a person can read,
copy and route back to their resource, wire-compatible with the Elixir
reference `uxid` and checked against its conformance corpus. It covers
generation (random, deterministic and monotonic), decoding and the validity
checks; it has no runtime dependencies, takes its random bytes from
`SecureRandom` by default, and runs on Java 17 or later. Its Maven coordinate
is `systems.riddler:uxid`, and its module and package are
`systems.riddler.uxid`.

The tree on `main` today holds these agent instructions, the wurk manifest
(`.claude/wurk.json`), the tracker files under `.beads/`, the build (`pom.xml`,
`mise.toml` and `.mvn/jvm.config`), the module descriptor, the package it
exports, `LICENSE`, `README.md`, `docs/wire-format.md`, `CHANGELOG.md` with the
fragment rule in `changelog.d/README.md`, the vendored conformance corpus under
`conformance/` (with `.gitattributes`, which keeps its line endings as
committed), and the two workflows: CI (`.github/workflows/ci.yml`) and the
release workflow (`.github/workflows/release.yml`). The package holds
time-based and deterministic generation (`Uxid.generate` and `UxidOptions`),
decoding (`Uxid.decode`, `DecodeOptions` and `DecodeResult`), the validity
checks (`Uxid.isValid` and `Uxid.isDeterministic`) and the monotonic generator
(`Monotonic`, with its `DrawSource`). The sentences in this file about the
1.0.0 package describe the target, not the current tree: it is not yet
published.

- **The package is `systems.riddler:uxid`.** Version 1.0.0 is its first
  release on Maven Central.
- **The corpus is the arbiter.** `conformance/` holds the corpus vendored from
  uxid-ex at the release tag `v2.10.0` (case files, `manifest.json`, `schema/`
  and `README.md`, byte for byte; not the generator `source/`), its tag,
  commit and hash recorded in `conformance/SOURCE.json`. The vendored files
  are never edited, and every case passes with no skip list: a case this port
  cannot pass is reported against uxid-ex with its evidence, never skipped or
  worked round here. An answer the corpus does not fix follows uxid-ex's spec
  at that tag.

`README.md` is the user reference, and `docs/wire-format.md` points at the
reference spec at its tag; `CHANGELOG.md` records each release.

## Build & Test

The gate tasks live in `mise.toml`.

```bash
mise install            # the JDK, Maven and the tools mise.toml pins
mise run gate:loop      # inner loop
mise run gate           # full gate
```

The full gate prints `java -version` first and then runs `mvn -B -ntp
verify`: the enforcer rules (Maven 3.9 or later, every plugin version pinned,
no dependency outside test scope), the compile with every javac lint and Error
Prone, a warning failing it, the JUnit 5 tests, the jar and the sources and
javadoc jars, the Spotless format check (google-java-format) and the JaCoCo
check at 90 percent of lines. The conformance corpus check is part of the
tests: a test recomputes the corpus hash and each case count by the
reference's rule, and a runner (`CorpusRunnerTest`) runs every case of every
case file, with no skip list, rejecting a case key it does not know. The inner
loop, `mise run gate:loop`, is `mvn -B -ntp test`: the compile and the tests.

The gate runs on the JDK `mise.toml` pins (Temurin 25, with Maven 3.9). The
toolchain floor is declared separately: `maven.compiler.release` 17 in
`pom.xml`, so the jars run on Java 17. CI (`.github/workflows/ci.yml`) runs
the full gate on the `mise.toml` JDK and proves the floor by running the
tests on Temurin 17, 21 and 25: the code is compiled on the gate JDK, with
Error Prone, and only the test JVM moves to each JDK; the gate JDK alone does
not prove the floor.

### This repo's own gate rules

- The full gate is `mise run gate`; the inner loop is `mise run gate:loop`.
  Only the full command is the advancement gate: a `gate:loop` run, like a
  bare `mvn test` or any other scoped run, is never evidence for a claim that
  the gate is green.
- **Never truncate the output.** No `| tail`, `| head`, `| grep`; truncating
  removes findings, not noise.
- **Never go green by weakening the check.** Not by lowering the coverage
  minimum, not by a `@Disabled` or an assumption that skips a failing test,
  not by `-DskipTests`, `-Dspotless.check.skip` or any other skip property,
  not by a `@SuppressWarnings`, an Error Prone exclusion, a `spotless:off`
  marker or a JaCoCo exclusion added to get past a finding, not by dropping
  or skipping a corpus case. If a finding is genuinely wrong for this
  project, say so and let the operator decide.
- Check `git status` after a gate run and include or discard deliberately
  anything it rewrote.
- A change touching no path the manifest (`.claude/wurk.json`) lists under
  `gate.build_paths` or `gate.also_gated_paths` has no gate to run and may
  commit on review of the diff alone. Those two lists are the one source for
  the exception: `src/`, `scripts/`, `conformance/` and `README.md` are what
  the gate checks, and `pom.xml`, `mise.toml`, `.mvn/`, the Maven wrapper
  (`mvnw`, `mvnw.cmd`), `.tool-versions`, `.java-version` and `.editorconfig`
  decide how it runs and what it resolves. A listed path need not exist yet.
- **Sabotage notes use the in-name form.** The gate's sabotage scan finds a
  test by its JUnit annotation (`@Test`, `@ParameterizedTest`,
  `@RepeatedTest`, `@TestFactory`) under `src/test/java/` and reads a note
  only on that annotation's own line; it does not read a `//` comment line
  above it. So a test's note is a trailing comment on its annotation line:
  `@Test // (sabotage: <the mutation> -> <how the test failed>)`. The
  annotation line, note and indentation included, stays within
  google-java-format's 100 columns: the formatter re-wraps a longer `//`
  comment, which would move the note's closing parenthesis off the line the
  scan reads.
- Documentation may point at the gate; it never enlarges it.

## Conventions

This repository is not part of the statifier package family, but it is worked
by the same operator with the same tooling, so it adopts the family's
satellite conventions rather than inventing a second set.

- **Domain-free examples.** The README, Javadoc and test fixtures teach no
  example domain: examples use neutral resource prefixes such as `usr` and
  `cus`, and any address is a fictional `@example.com` one.
- **No runtime dependencies.** Every `<dependency>` in `pom.xml` is test
  scope; adding a runtime dependency needs a bead of its own first. Build
  tools are Maven plugins or come from `mise.toml`.
- **No global state.** No static mutable counter, cache or configuration.
  The monotonic generator is an object the caller holds, with its
  `ReentrantLock` inside the instance.
- **The byte source is injectable; the default is the platform CSPRNG.** The
  default is `SecureRandom`; never `java.util.Random` or another seeded or
  non-cryptographic generator as a default. Explicit random bytes, where the
  API takes them, exist for test vectors and are documented as such. No
  test-only seam lives in the library source (`src/main/java/`).
- **Java 17 or later** (`maven.compiler.release` 17), built with Maven, with
  Spotless (google-java-format), Error Prone, JUnit 5 and JaCoCo in the gate,
  and the tests run on Java 17, 21 and 25 in CI (ruled by the
  operator, 2026-10-21). The coverage bar is 90 percent of lines (decided by
  the conductor under a standing consent, 2026-10-21).
- **One module.** The library's `module-info.java` declares the module
  `systems.riddler.uxid` and exports its one package (decided by the
  conductor under a standing consent, 2026-10-21).
- **Tests use JUnit 5** under `src/test/java/`. Every public class, method and
  constructor carries Javadoc.
- Sabotage every new test that asserts library behavior: break the code it
  covers, confirm it goes red, revert, and note the mutation in the in-name
  form the gate rules above give.
- Changelog: a change a user of the API could notice adds a fragment under
  `changelog.d/`, named for the bead (`changelog.d/uxjv-abc.md`), in the same
  request; tests, docs, tooling, CI and gate changes take none
  (`changelog.d/README.md` states the rule and the format). New version
  sections in `CHANGELOG.md` keep the file's own heading shape; existing
  history is not rewritten.
- Commit messages: simple present tense ("Adds ...", "Fixes ..."), body
  wrapped at ~72 chars explaining why. Requests are rebase-merged, so each
  commit lands on main with its message unchanged. No AI attribution
  trailers.
