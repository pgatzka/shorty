# CLAUDE.md

Guidance for AI assistants (and humans) working on Shorty. Read README.md for what the app does and how it is deployed.

## Build and test

```bash
./mvnw spotless:apply                            # always format before committing; CI runs spotless:check
./mvnw test-compile checkstyle:check pmd:check   # Error Prone runs inside javac
./mvnw verify                                    # unit (*Test, surefire) + integration (*IT, failsafe) + JaCoCo gate
./mvnw spring-boot:run                           # needs `docker compose up -d` for PostgreSQL
```

- Integration tests use Testcontainers (`TestcontainersConfiguration`) and need Docker.
- Render tests encode real (tiny) videos and need `ffmpeg` on the PATH. `VideoRendererTest` fails fast if it is missing.
- Error Prone needs the `--add-exports` flags in `.mvn/jvm.config`. Do not delete that file.
- The Vaadin frontend bundle is built by `vaadin-maven-plugin:build-frontend` during `package`. `vaadin-dev` is optional, so the jar runs in production mode.

## Decisions (keep unless the user changes them)

- Java 25, Spring Boot 4.2.x, Vaadin 25.3.x, Spring Modulith 2.2.x. Vaadin 25.3's official starter targets Boot 4.1, but the full test suite (including browserless UI tests) passes on 4.2. Watch Vaadin release notes when bumping either.
- Base package `io.github.pgatzka.shorty`. Modules: `sorting`, `render`, `youtube`, `publishing`, `schedule`, `analytics`, `ui`.
- PostgreSQL + Flyway (`src/main/resources/db/migration`, never edit an applied migration; add `V<n+1>__*.sql`). Hibernate runs with `ddl-auto: validate`.
- Rendering: Java2D frames (`TYPE_3BYTE_BGR`) piped as rawvideo into ffmpeg (libx264 + AAC), with beeps synthesized as PCM in `AudioTrack`. No extra graphics libraries.
- Fixed playback speed (`shorty.render.steps-per-second`). `BarCountPlanner` picks the largest bar count whose video stays ≤ `max-duration-seconds` (175, must stay < 180).
- One render worker (`renderExecutor`, a single thread): renders and uploads never run in parallel.
- YouTube OAuth uses a "Web application" client with a `localhost` redirect. When the UI is opened via LAN IP, the user pastes the redirect URL into the Post now screen. The refresh token is stored AES-256-GCM-encrypted (`TokenCipher`, key from `SHORTY_ENCRYPTION_KEY`).
- No login, home network only. No secrets in the repository (it is public).

## Conventions

- Package by module, layers inside: `view`/`web` → `service` → `repository` → `domain`.
  - Views and controllers never use repositories.
  - Domain packages have no Spring, Vaadin or Google imports (JPA annotations are fine).
  - Other modules may only use a module's `domain` and `service` packages (marked `@NamedInterface`).
- Cross-module calls go through services. Keep the dependency graph acyclic: `ui → schedule/analytics → publishing → render/youtube`, `render → sorting`.
- Singletons in the DB (channel settings, schedule settings, YouTube credential) use `id = 1` with a check constraint.
- Configuration lives in `@ConfigurationProperties` records with defaults (`RenderProperties`, `PublishingProperties`, `YouTubeProperties`). Inject `Clock` for anything time-dependent.
- Errors meant for the user are `YouTubeException` / `RenderException` / `IllegalArgumentException` with a readable message. Views show them as notifications, and publishing writes them into the upload history.
- Views expose package-private accessors for their fields so browserless tests (`ViewTestBase`, `SpringBrowserlessTest` + `@MockitoBean`) can drive them.

## Quality rules (CI enforces all of them)

- Formatting: Spotless with palantir-java-format.
- Error Prone (compile errors), Checkstyle (`config/checkstyle/checkstyle.xml`), PMD (`config/pmd/ruleset.xml`). Fix findings instead of suppressing them. If a suppression is unavoidable, scope it to one method and say why.
- `ArchitectureTest`: Modulith `verify()` plus ArchUnit layer rules.
- JaCoCo: at least 80 % line **and** branch coverage over unit + integration tests. Sonar requires the same on new code.
- New behaviour needs tests: unit tests with Mockito/AssertJ, Testcontainers ITs for persistence, browserless tests for views.

## CI/CD and safety

- Workflows: `ci.yml`, `security.yml`, `release.yml`, `dependabot-auto-merge.yml`; shared setup in `.github/actions/setup-java-maven`.
- Pin every action to a full commit SHA with a version comment. Give every workflow/job explicit least-privilege `permissions:`.
- The self-hosted runner (`shorty-deploy` label) may only be used by the `deploy` job in `release.yml`. Never add a `pull_request`-triggered job that runs on it.
- Check workflow changes with `actionlint` (`.github/actionlint.yaml` declares the runner label).
