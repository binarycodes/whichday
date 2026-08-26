# Coding Conventions

Project-wide rules. Once a pattern is established here, follow it without prompting.

Everything here describes Whichday as it is. A rule that names a class, a file or a
property is one you can go and check, and a rule that stops being true is a bug in this
file.

## 1. Naming

- Full, meaningful identifiers everywhere (variables, params, lambda captures, locals). No abbreviations beyond well-known ones — `id` and `uuid` are fine, `org` for organizer is not.
- Methods named for what they do, not how.
- Lambda parameters get real names; a genuinely unused one is `ignored` / `unused`.
- Constants replace magic numbers.

## 2. Comments

- Default to none — names, structure, and types carry intent.
- Comment only the non-obvious, and only the *why*.
- No section-divider banners. Class-level Javadoc for one-paragraph orientation is fine.
- Delete stale narrative and TODOs.

## 3. Code structure

- One concern per class. A view composes named components and holds the one method that ties them together; anything with a shape of its own — a calendar, a ballot, a tally list, a row — is its own class under the feature's `ui/component/` or `ui/` package with a focused API.
- Screens extend `Screen`, which owns the column and the pinned footer, and add content through `body(...)` / `footer(...)`. A screen about one poll extends `PollScreen`, which parses the route id, turns unknown ids away, and gives `render()` for rebuilding after a change.
- **A view builds in `beforeEnter`, not in its constructor.** Vaadin reuses a view instance when the route it is asked for is the one already showing, so a constructor-only build leaves whatever it drew the first time.
- **Turning a navigation away happens in `PollScreen.redirect`**, which forwards before anything is built, so the browser sees one navigation and the abandoned screen is never rendered. Navigating from inside the event instead leaves a half-built screen behind it.
- Components extend the closest Vaadin primitive and expose a focused API (`setValue` / `show` / `withAction` / `addValueChangeListener`). Stop extending it when its shadow layout fights the design.
- Vaadin `Button` takes an icon and a label and nothing else — `add()` is private. A control that needs three pieces of content (a date numeral, a weekday, a vote count) is a `NativeButton`, with `aria-pressed` when it is a toggle.
- A menu on a bare avatar is a `ContextMenu`, not a `MenuBar`: a menu bar brings a button and an overflow arrow the design does not draw, and suppressing both means styling into its shadow root.
- **An irreversible action gets a screen, not a dialog.** There is no dialog anywhere in this application, and locking a day — final: no more answers, no different day, nothing to undo — is confirmed on `SettleView` instead. A screen can say what is about to happen, carry the choice a tie still needs, and be cancelled with the back chevron every other screen has. A one-tap commit on a screen somebody came to *read* is the shape to avoid.
- Helpers live with the thing they help.
- Prefer a named private method over a long inline block.

## 4. Java

- Records for immutable value types. Every type that crosses out of a service is one — `Poll`, `Ballot`, `PollSummary`, `Person`, `Caller` — built on the spot, so a screen holds a snapshot rather than a view into the store.
- **JPA entities take no Lombok.** Hibernate wants a non-final class with a no-arg constructor and reads fields directly; it does not want accessors. Generated ones would put a `setOpenedAt` next to `StoredPoll.closeOn`, which stamps once, and a `setCandidateDays` next to `replaceCandidateDays`, which prunes the ballots as it goes. Those are rules, not boilerplate. `StoredBallotKey` is the one exception — an `@IdClass` is genuinely nothing but fields — and it carries `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`. Lombok is declared `<optional>true</optional>`.
- `var` when the right-hand side makes the type obvious.
- Multi-line strings use text blocks with `.formatted(...)`, never `+`.
- **A startup failure says what to set.** The house idiom is a text-block constant and an `IllegalStateException` naming the environment variable — `LoginSecurityConfig.MISCONFIGURED`, `AccessMode.named`. An unresolved `${...}` placeholder binds as the literal string rather than failing, so `value.contains("${")` is how "an environment variable nobody set" is spelled.

## 5. Vaadin

- A composite input extends `CustomField<T>` and propagates its inner changes with `updateValue()`. `MonthCalendar` and `DayBallot` are both `CustomField<Set<LocalDate>>`, which is what lets a screen treat "the days chosen" as one value with one listener.
- `ValueChangeMode.LAZY` on text inputs that drive live work: a search that fires per keystroke, a draft field that writes as you type.
- Presenters are `@VaadinSessionScope`, and they are the only session-scoped beans. They pair the shared store with the one thing that is per-session — who is looking — so no view passes a viewer into a service call.
- **Every message goes through `Toast`, never `Notification.show`.** Vaadin's default puts a notification bottom-left for five seconds, which on a phone is exactly where the primary action is — so a message about a button sat on top of it. `Toast` moves it to the top and keeps the five seconds.
- **A Vaadin component that paints itself in its own shadow root is styled through `::part(...)`, not the host.** The notification card is the example: styling the host as well drew a second card around the first.
- Screens read every user-visible string through `getTranslation` (§7) and every colour through a token (§6).

## 6. CSS

- One file per concern; the entry stylesheet (`styles.css`) holds only `@import` lines, so touching it is what busts the browser cache for a changed partial — which is what `./run.sh styles` does.
- Colors are semantic tokens in `colors.css`, referenced via `var(--color-…)`. No raw hex / rgb / `color-mix` / named colors elsewhere.
- Light and dark are Aura's `color-scheme`, not a class on `html`. Own tokens adapt with the CSS `light-dark()` function; the scheme is set from Java with `Page::setColorScheme`. There is no `html.dark` selector.
- Prefer Vaadin / theme variables over hard-coded values: `--vaadin-*` base properties (`--vaadin-text-color`, `--vaadin-border-color`, `--vaadin-radius-m`, `--vaadin-gap-s`) and `--aura-*` for what only Aura defines (`--aura-accent-color`, `--aura-font-weight-medium`, `--aura-shadow-xs`).
- Style Vaadin components through their own custom properties (`--vaadin-button-background`) rather than `background` on the host, which the component's base styles win against.
- `box-sizing: border-box` is set globally in `shell.css`. Padded full-width containers otherwise overflow their parent at phone widths.
- **The stylesheet's partials need their own `permitAll` in login mode.** Vaadin permits the resources it knows about, which includes the `@StyleSheet` entry point but not the files that entry point `@import`s. Left authenticated they redirect to the provider, and a 302 carries no content type, so the browser refuses every one of them as "not a supported stylesheet MIME type" and the application renders unstyled.
- One-paragraph header comment per file; no section dividers.

## 7. Internationalization

- No user-facing string literals in code. Every label, title, hint, placeholder, tooltip, validation message, notification and aria-label is a key in `src/main/resources/vaadin-i18n/translations.properties`.
- Resolve via `getTranslation(key, args…)` on a `Component`. A non-component helper takes the calling `Component` and resolves through it rather than reaching for a static lookup — `Counts`, `DateText`, `AccountLabels`, `MailLink`.
- Reuse and parameterise shared keys; namespace them by screen or concern (`create.*`, `days.*`, `share.*`, `ballot.*`, `results.*`, `polls.*`, `identity.*`, `nav.*`, `count.*`).
- Counts that can be one carry both forms (`count.days.one` / `count.days.many`) and the code picks on the number — `MessageFormat` alone cannot. The choice is made once in a helper, not at every call site.
- **Where a mode changes the wording, the branch lives in one helper, not in the screens.** `Counts.progress(…, anonymous)` and `AccountLabels.of(…, anonymous)` are the shape: the component is handed what it should say and does not learn which mode it is in.
- Page titles come from `HasDynamicTitle`. Never `switch` on a translated string; switch on an enum.
- **A value that takes arguments doubles its apostrophes; one that takes none leaves them single.** Vaadin runs a value through `MessageFormat` only when the call passes arguments, and MessageFormat treats `'` as a quoting character — so `results.nudge` needs `hasn''t` and `identity.title` must not. Neither mistake fails a build, so `TranslationsTest` guards both.

## 8. Responsive layout

- **Mobile first, and the design is drawn as 390×844 phone frames.** `.app-shell` centres one column capped at `--screen-max-width` against Aura's page background rather than letting a phone screen stretch across a desktop. A screen fills the viewport so that a footer pinned with `margin-top: auto` sits at the bottom of the phone rather than under the last paragraph.
- Containers fill width (`setWidthFull()`); no fixed pixel widths.
- No horizontal overflow at any width from ~375px up.
- Verify at mobile and desktop widths before declaring done.

## 9. Project layout

- Feature-based packaging: `base/` for shared infrastructure, feature packages (`people/`, `poll/`) with their own `domain/` / `service/` / `ui/`, plus `ui/presenter/` for the session-scoped classes the screens talk to.
- Dependencies run one way: `ui` → `ui/presenter` → `service` → storage, never backwards. A service imports no Vaadin: a change listener is how a component learns to redraw itself, so listeners and the orchestration around them belong to the presenter, which is also the only layer that needs to be session-scoped.
- One public class per file; helpers stay private static unless reused.

## 10. Persistence

- **Embedded H2, one file, `MODE=PostgreSQL`.** This is the one place the project departs from a server database, and it is a decision rather than an omission: the point is one container with nothing else to bring up. `docs/REQUIREMENTS.md` §9 is the record. `MODE=PostgreSQL` and portable migrations keep it from being a one-way door — `V1` is SQL a real PostgreSQL runs unchanged, so moving is a URL and a dependency rather than a rewrite.
- Flyway owns the schema (`db/migration/V*.sql`) and `spring.jpa.hibernate.ddl-auto=validate` fails startup on drift rather than rewriting it. **A migration is immutable once written** — editing even a comment changes its checksum — so `V1`'s comments point at files that no longer exist and stay wrong on purpose.
- **Entities and repositories are package-private and never leave the service package.** The service translates them to records before anything above sees them, which keeps the domain immutable and keeps every entity inside the transaction that loaded it, so a stateful UI framework never meets a detached one. A view that tries to import an entity does not compile; that is the point.
- **A person is an email address.** `poll.organizer_email`, `poll_invitee.email`, `ballot.voter_email` — there is no user foreign key anywhere. The one place a name lives is the `account` table, so an invitee who has never signed in needs no row and one who signs in later is named everywhere at once. `PollService.addressOf` normalises on the way in, because an address written in mixed case is a row nothing can find again.
- **Readers go through `require`; writers go through `requireForUpdate`**, which takes a row lock held until the transaction commits. That pairing replaced a `synchronized` on every method: a monitor inside a transactional proxy is released before the commit, so it reads like a guarantee and is not one.
- `spring.jpa.open-in-view=false`. A screen holds a record and never an entity, so a lazy read outside a transaction is a bug, and this is what makes it fail loudly instead of hiding until the next thing moves.
- A path-like setting names the directory, not the whole URL: `WHICHDAY_DATA_DIR`, so moving the database file cannot drop `MODE=PostgreSQL` on the way past.
- **Nothing is stored forever.** Two retention windows delete a poll — a few days after it ends, and unconditionally at its maximum age — and `docs/REQUIREMENTS.md` §9 is the record. A new table that hangs off `poll` needs `on delete cascade` or the sweep leaves its rows behind; one that does not needs a rule of its own, because the only reason the sweep reaches anything is that a poll is the root of it.

## 10a. Security headers

- Spring Security writes its headers as the response commits, and the response Vaadin renders a page into never commits that way — so the application's own routes come out with no headers at all while static resources get the full set. `SecurityHeadersConfig` fixes this with an `ObjectPostProcessor<HeaderWriterFilter>` calling `setShouldWriteHeadersEagerly(true)`. Verify a header change by curling an application route (`/`, `/poll/…`), never only a static file.
- Headers are configured once, in `SecurityHeadersConfig`, and not in either mode's chain — they are the same whichever way a deployment lets people in — and not in a parallel servlet filter either, so Spring Security keeps the conditional ones (HSTS only over HTTPS) conditional.
- HSTS only goes out when the app believes the request was secure, and behind a TLS-terminating proxy it only ever sees plain HTTP. `FORWARD_HEADERS_STRATEGY=native` opts into trusting the proxy's `X-Forwarded-*` headers; it stays unset by default because those headers are client-supplied and spoofable with nothing in front.

## 10b. Access control

- **There are two ways in and a deployment picks one**, with `WHICHDAY_ACCESS_MODE`, defaulting to `anonymous`. The variable names a Spring profile outright (`application-login.properties`, `application-anonymous.properties`), and each of those files sets `whichday.access.mode` — which is what the code keys `@ConditionalOnProperty` on, never `@Profile`, so a test stays free to compose `@ActiveProfiles`. One `SecurityFilterChain` per mode, one `ViewerSession` per mode, and `AccessMode` as a bean for the handful of places that genuinely branch. `docs/REQUIREMENTS.md` §1 is the record.
- **The OIDC block lives in the login profile's file, and that is load-bearing.** Boot resolves an issuer by fetching its discovery document at startup, so an anonymous deployment that inherited `issuer-uri` from `application.properties` would hang on a provider it has no reason to reach. Blanking it is not the same thing — that fails as "issuer cannot be empty". The key has to be genuinely absent.
- **No product name appears in `src/main`.** The registration is called `oidc`, not after whatever the development stack happens to run, because Spring builds `/oauth2/authorization/oidc` and the `/login/oauth2/code/oidc` callback from that id — so a vendor named there would end up in the application's own URLs and in every deployment's configuration. Whichday needs discovery, the authorization-code flow, an id token and RP-initiated logout, and nothing beyond them. Keycloak belongs to `environment/dev/` and to the test fixtures only.
- In `login` mode, `LoginSecurityConfig` leaves `VaadinSecurityConfigurer` on its defaults and points `oauth2LoginPage` at the registration, so an unauthenticated request redirects to the provider rather than to a login view of ours — the application collects no credentials and never sees one. It refuses to start without a client id and secret.
- **A route with no access annotation is denied, and so is its parent layout's.** In `login` mode navigation access control is on, so every `@Route` carries `@PermitAll` — and so does `MainLayout`. The checker looks at each parent layout a route names before it looks at the route, and an unannotated layout denies everything behind it: the symptom is a `RouteNotFoundError` and a log line about the view allowing "broader access than the layout". "Authenticated" is the whole authorization model — there are no roles.
- In `anonymous` mode, `AnonymousSecurityConfig` turns navigation access control **off** rather than changing every route to `@AnonymousAllowed`: Vaadin reads `@PermitAll` as *authenticated*, and nobody is, so leaving the checker on would refuse every screen. With it off, Vaadin's request rules can no longer classify a URL by which view it reaches, so the `permitAll` goes in an explicit `authorizeHttpRequests` and `enableAuthorizedRequestsConfiguration(false)` stops the configurer adding a second — otherwise every navigation logs that it could not tell whether the URL was public.
- **Who may do what is decided in `PollService`, never by which button a screen draws.** The screens do hide what is not yours, and that is a courtesy; a hidden button is not a check. Anonymous mode branches in exactly three places there — the link stands in for an invitation on read, a voter joins the invitee list as they answer, and a six-digit admin code is a second way to be the organizer. The code travels with the call as `Caller`, so it is never read from the session inside the service.
- **A refusal says as much as the caller already knows and no more.** In login mode a stranger gets the same `IllegalArgumentException` an id nobody issued gets, because "you are not on the list" confirms there is a list, which confirms the poll; somebody already on the poll is refused by name, since they can see it and there is nothing left to withhold. Anonymous mode has two answers rather than three — anybody who reached the call is holding the link, and the link already showed them the poll.
- A logout that only drops our own session leaves the provider's intact and the next visit signs straight back in. `OidcClientInitiatedLogoutSuccessHandler` is what makes the button mean what it says. Anonymous mode has no provider to log out of, so it closes the Vaadin session instead.
- **`AuthenticationContext` stays behind `ViewerSession`.** It is Vaadin's presenter-shaped API for who is signed in, but it only answers login mode's version of the question — so the interface is the seam, and no component takes it directly.

## 11. Build & CI

- Run build / test / frontend tasks through `./run.sh <task>` (`deps`, `compile`, `bundle`, `styles`, `test`, `verify`, `run`, `preview`, `package`, `clean`, `resetdb`, `env`), which pins JDK 21 and resolves Maven from SDKMAN. **Never invoke `mvn` directly and never set `JAVA_HOME` by hand** — a bare `mvn` may pick JDK 25, under which Lombok silently generates no getters and the build dies with bogus "cannot find symbol" errors. `./run.sh deps` warms the local repository ahead of a build; every other task resolves what it needs as it goes. After a `@CssImport(themeFor=…)` / `@JsModule` change run `./run.sh bundle`; after editing an `@import`-ed CSS partial run `./run.sh styles`.
- `./run.sh env` brings up the development Keycloak, which **only login mode needs** — anonymous mode is the default and has no provider at all. Which containers the stack has is `environment/dev/`'s decision, so adding one there needs no change to `run.sh`. The stack is defined twice, `compose.yaml` under docker and `quadlet/` under podman, and keeping the two in step is manual.
- **`run.sh` is shared between projects as it stands and names none of them.** Whichday's answers live in `run.conf` — the project name, the JDK, `CONTAINER_REQUIRED="true"` because `env` brings up a container, the `it` profile and the `build.commit` property. Anything left out falls back to the default Vaadin layout, so the file names only what is a decision. A task the runner does not have goes in `run.tasks.sh` as a `task_<name>` function, dispatched by name and listed under `help` through `project_usage`; `resetdb` is the only one. Keeping both out of `run.sh` is what lets this project take a newer runner without a merge — so a runner improvement is made upstream and copied, never patched here.
- `./run.sh verify` clears the cached bundles before building. A `dev.bundle` left by `./run.sh run` makes the frontend build report "a production mode bundle build is not needed", and a test then opens a page whose client bundle fails to boot.
- **Every build carries the commit SHA.** `maven-enforcer-plugin` rejects a build without `-Dbuild.commit=<7-40 hex>`, and `run.sh` resolves it from the working tree, so an unidentifiable artefact cannot be produced. CI passes `${{ github.sha }}` and runs `mvn verify` on push (Temurin JDK 21); on `main` it then builds, attests and signs the image.
- **A `FROM` names its registry**: `docker.io/library/maven:…`, never a bare `maven:…`. Docker silently assumes Docker Hub; Podman refuses to guess, so a bare name fails the build on the first `FROM` with "short-name resolution enforced but cannot prompt without a TTY" wherever there is no terminal to answer — CI, a hook, an agent.
- **A directory the container writes to is a declared `VOLUME`**, created and `chown`ed to the runtime user *before* the `VOLUME` line, since a build step writing to a declared volume's path is discarded. That ordering is what lets a fresh named volume come out owned by the right uid with nothing for the operator to fix, and the declaration is what stops the data landing in the image layer when nobody mounts anything.
- **An environment variable a deployment must set appears in four places**: the property file that reads it, both README deployment samples — the compose block and the Quadlet block, which are maintained as a pair — and `CONTRIBUTING.md` when a local run needs it.
- Tests live alongside the package they cover: unit tests for the service layer, browserless tests for the views. `TestDatabase` empties every table in `@BeforeEach`, since one in-memory database serves the whole suite, and it reads the table list from `information_schema` so a new migration needs no change there.
- **Every Spring test names its access mode and then `test`, in that order.** `@WhichdayTest` is `{"login", "test"}` and `@AnonymousWhichdayTest` is `{"anonymous", "test"}`; the order is what lets `test` override what the mode's file brought. Two cached contexts, deliberately — the modes differ in which beans exist, so there is nothing to switch at runtime. Use the composed annotation rather than repeating its parts: the context cache is keyed on the configuration, so a class that drifts by one annotation quietly bootstraps a third context and pays for it.
- *Profile* files deliberately, not `application.properties` under `src/test/resources`: that name shadows the main file wholesale and takes the datasource and Flyway settings with it.
- **A profile can override a key but never remove one, and an issuer beats explicit endpoints.** Boot resolves an issuer by fetching its discovery document at startup, so a test that inherited `provider.oidc.issuer-uri` would hang the whole context, and blanking it fails as "issuer cannot be empty". The `test` profile therefore points the registration at a second provider (`offline`) whose endpoints cannot resolve, leaving the inherited issuer unreferenced. The registration id stays `oidc`, because that is what builds the callback path.
- **Verify a configuration change against the real property files.** An `ApplicationContextRunner` with no `@ActiveProfiles` and no `ConfigDataApplicationContextInitializer` loads none of them, so it will happily confirm a registration that the actual suite cannot build. Both of the OIDC startup failures in this repository's history got past exactly that kind of check.
- **Identity is stubbed, never authenticated.** `StubIdentity` puts a real `OAuth2AuthenticationToken` into the security context rather than replacing `ViewerSession`, because a browserless test bypasses the servlet filter chain but not `SpringNavigationAccessControl`, and because going through the token means the application's own `AuthenticatedViewerSession` is what the tests exercise, claims and all. Anonymous tests need none of it: a second visitor there is a second `AnonymousViewerSession` with a presenter of its own, since an identity belongs to a Vaadin session and a test method has one. No tier starts a provider — `docs/issues/0006` records that the real token exchange is untested.
- Session-scoped beans cannot be `@Autowired` into a browserless test's fields — the Vaadin session does not exist that early. Take the `ApplicationContext` and resolve them in `@BeforeEach`.
- JaCoCo holds `io.binarycodes.whichday.*.service` and `io.binarycodes.whichday.*.ui.presenter` at 80% instruction coverage, so new code there needs tests. `<includes>` for a `PACKAGE` rule take dot notation; slash notation matches nothing and the gate silently passes.
- **Surefire and failsafe pass `@{argLine}`, and `argLine` has an empty default in `<properties>`.** JaCoCo's prepare-agent overwrites that property with the coverage agent, so anything the plugins add to `argLine` — the Mockito agent, currently — has to be appended late or it replaces the coverage agent and the gate then measures nothing. The empty default is what keeps `-Djacoco.skip=true` working: with no property to resolve, `@{argLine}` reaches the forked JVM literally and it refuses to start. After touching either argLine, check that `target/jacoco.exec` is still written and that the gate still *fails* for an under-covered run — a detached agent looks exactly like a passing build.
- The compiler runs `-Xlint:all` with `-Werror`. A warning is a build failure, not a note.
- Conventional Commits: `<type>[(scope)][!]: <description>`, type one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`. Subject ≤100 chars, single line, no body, no `Co-Authored-By`. A `commit-msg` hook in `.githooks/` enforces this; enable per clone with `git config core.hooksPath .githooks`.

## 12. Working principles

- Verify each change visually or by test before declaring done.
- Preserve look-and-feel during refactors; a pixel change is a separate task.
- Refactor in small focused steps — extract, rename, run tests, then move on.
- **The README describes the application as it is now**, not as it was or as it might become. No migration notes, no historical asides, no upgrade paths for a scenario an earlier version left behind. Behaviour the code still performs is current state and belongs there; advice for data nobody has is not. When a feature changes or a concern is dropped, the README text that existed only to serve it goes in the same change.
- **`docs/REQUIREMENTS.md` records what the application has to do and every decision taken getting there, with the reason.** `docs/issues/` is its companion: one file per known defect or gap, saying what is wrong, what it costs and what fixing it would take, and it goes when the fix lands. A consequence accepted on purpose belongs in the requirements; one nobody wants belongs in an issue.
