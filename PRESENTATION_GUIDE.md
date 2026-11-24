# BorrowBuddy — Presentation & Live-Edit Survival Guide

Goal of this doc: make sure you can (1) explain any part of the code fluently, and
(2) if your prof changes something on the spot and asks you to fix it, you know
exactly where to look and how to recover fast.

## 1. Elevator pitch

BorrowBuddy is a Spring Boot (Java 17) + Thymeleaf web app where UST-Legazpi
students/org officers lend and borrow small items (calculators, lab tools,
chargers). It runs on Postgres (Supabase) in prod, uses session-based login
(no Spring Security), and the whole thing is built as a teaching example of
OOP: every model class has a comment in it tagged INHERITANCE / ENCAPSULATION /
POLYMORPHISM / OVERLOADING / OVERRIDING / COMPOSITION — read those comments,
they're your talking points.

## 2. Architecture (who calls who)

```
Browser
   |
   v
web/*Controller.java   <- @Controller, handles HTTP, reads HttpSession via CurrentUser
   |
   v
service/BorrowService  <- all borrowing business rules + state transitions live here
service/OverdueNotifier, EmailService  <- reminder / alert emails
   |
   v
repository/*Repository  <- Spring Data JPA interfaces, no SQL written by hand
   |
   v
model/*.java  <- JPA @Entity classes, this is where validation + OOP structure lives
```

Rule of thumb when the prof asks "where would you change X":
- Changing a validation rule / wording of an error → **model/** (setters) or **service/BorrowService**
- Changing what shows up on a page / a new form field → **templates/*.html** + the controller that adds it to `Model`
- Changing a borrowing rule (limits, days, who can do what) → **service/BorrowService.java**
- Changing roles or user types → **model/User.java** + its subclasses (Student, OrgOfficer, LabStaff)
- Changing item types → **model/Item.java** + its subclasses (Calculator, LabTool, Charger)

## 3. OOP pillars — exact locations to point to

| Pillar | Where | What to say |
|---|---|---|
| Inheritance | [User.java](src/main/java/com/borrowbuddy/model/User.java) is abstract; [Student](src/main/java/com/borrowbuddy/model/Student.java), [OrgOfficer](src/main/java/com/borrowbuddy/model/OrgOfficer.java), [LabStaff](src/main/java/com/borrowbuddy/model/LabStaff.java) extend it. Same pattern: [Item.java](src/main/java/com/borrowbuddy/model/Item.java) → [Calculator](src/main/java/com/borrowbuddy/model/Calculator.java), [LabTool](src/main/java/com/borrowbuddy/model/LabTool.java), [Charger](src/main/java/com/borrowbuddy/model/Charger.java) | "One table per hierarchy" via `@Inheritance(strategy = SINGLE_TABLE)` + `@DiscriminatorColumn` |
| Encapsulation | `User.passwordHash` has no getter, only `setPassword`/`checkPassword`. `Item.setStatus` is package-private — only `BorrowRecord` can change an item's status | Prevents the app from ever "forgetting" to hash a password or desyncing item status from the actual borrow record |
| Polymorphism | `BorrowService.requestBorrow` uses `item.getMaxBorrowDays()` and `borrower.getBorrowLimit()` without knowing the concrete subclass | Same service code behaves differently per Student vs OrgOfficer, per Calculator vs Charger |
| Overriding | `getBorrowLimit()`, `getRole()`, `isAdmin()` in User subclasses; `getMaxBorrowDays()`, `getCategory()` in Item subclasses | |
| Overloading | `BorrowService.searchItem(keyword)` / `searchItem(keyword, category)`; `requestBorrow(item, borrower)` / `requestBorrow(item, borrower, days)`; `BorrowRecord.handOver()` / `handOver(date)`; `markReturned()` / `markReturned(date)` | |
| Composition | `User` HAS-A `Profile` (`@Embedded`); `Item` HAS-A owner `User`; `BorrowRecord` HAS-A `Item` + two `User`s (borrower, lender) | |
| Abstraction | `User.getBorrowLimit()`/`getRole()` and `Item.getMaxBorrowDays()`/`getCategory()` are `abstract` — subclasses must implement them | |

If you want a zero-dependency way to re-demo these live without the DB/web
server, run [demo/ConsoleDemo.java](demo/ConsoleDemo.java) — it's a plain
`main()` that exercises all the model classes directly. Good fallback if the
web app ever breaks mid-presentation.

## 4. The borrow lifecycle (the thing most likely to get quizzed)

State machine lives entirely in [BorrowRecord.java](src/main/java/com/borrowbuddy/model/BorrowRecord.java):

```
Pending --approve()--> Approved --handOver()--> Borrowed --markReturned()--> Returned
   |                       |
   decline()           cancelPickup()
   v                       v
Declined              Cancelled (item back to Available)
```

Side paths off `Borrowed`:
- Borrower calls `claimReturned()` → sets `returnClaimedDate`, status stays `Borrowed` ("Return pending")
- Owner can `rejectReturnClaim()` to undo that
- Owner calls `markReturned()` → actually closes it, item goes `Available`
- If overdue and owner clicks "Not received" → `reportNotReceived()` escalates to lab staff
- Lab staff calls `resolveByAdmin()` to force-close an overdue record

Each transition calls `requireStatus(expected)` first — if the prof asks "what
happens if I call handOver() twice", the answer is: it throws
`IllegalStateException("This record is Borrowed, not Approved.")`. That's by
design, not a bug — point this out confidently if they try to "break" it.

Trace of one request end to end (good to have memorized):
1. `ItemController.request()` → `BorrowService.requestBorrow(item, user[, days])` → item becomes `Reserved`, new `BorrowRecord` saved as `Pending`.
2. `RecordController.approve()` → `BorrowService.approveRequest(...)` → record becomes `Approved`, meetup place/time set, then `OverdueNotifier.emailMeetupDetails()` fires.
3. `RecordController.handOver()` → `confirmHandOver()` → `Borrowed`, due date = pickup date + requested days.
4. `RecordController.claimReturn()` → `claimReturned()` → emails owner.
5. `RecordController.markReturned()` → `Returned`, item `Available` again.

## 5. File cheat-sheet (jump straight here when asked "show me X")

- Login / register / profile: [AuthController.java](src/main/java/com/borrowbuddy/web/AuthController.java)
- Session handling ("who's logged in"): [CurrentUser.java](src/main/java/com/borrowbuddy/web/CurrentUser.java) — stores just the `userId` `Long` in `HttpSession`
- Browse/search items, list a new item, send a borrow request: [ItemController.java](src/main/java/com/borrowbuddy/web/ItemController.java)
- Approve/decline/handover/return/overdue reporting: [RecordController.java](src/main/java/com/borrowbuddy/web/RecordController.java)
- Admin dashboard (lab staff only): [AdminController.java](src/main/java/com/borrowbuddy/web/AdminController.java)
- All borrowing business rules: [BorrowService.java](src/main/java/com/borrowbuddy/service/BorrowService.java)
- Email sending + who gets notified when: [OverdueNotifier.java](src/main/java/com/borrowbuddy/service/OverdueNotifier.java), [EmailService.java](src/main/java/com/borrowbuddy/service/EmailService.java)
- Password hashing (PBKDF2, no plaintext ever stored): [PasswordHasher.java](src/main/java/com/borrowbuddy/security/PasswordHasher.java)
- Column-level encryption for sensitive fields (e.g. contact info) at rest: [EncryptedStringConverter.java](src/main/java/com/borrowbuddy/security/EncryptedStringConverter.java)
- Demo/sample data on first boot (password for every demo account is `borrow123`): [DataSeeder.java](src/main/java/com/borrowbuddy/DataSeeder.java)
- Pages: `src/main/resources/templates/*.html` (Thymeleaf), shared bits in [fragments.html](src/main/resources/templates/fragments.html)
- DB connection / config: [application.properties](src/main/resources/application.properties) — reads `DB_URL`, `DB_USER`, `DB_PASSWORD` from env vars; `spring.jpa.hibernate.ddl-auto=update` means **tables auto-sync from your entity classes**, you never hand-write SQL migrations

## 6. Running it locally — have this ready before you walk in

You need a Postgres connection (Supabase or any Postgres). Set env vars, then:

```powershell
$env:DB_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:DB_USER = "<user>"
$env:DB_PASSWORD = "<password>"
mvn spring-boot:run
```

Then open `http://localhost:8080`. Demo accounts (all password `borrow123`):
- `jericho.rectra@ust-legazpi.edu.ph` (Student)
- `maria.santos@ust-legazpi.edu.ph` (Student)
- `paolo.reyes@ust-legazpi.edu.ph` (Org Officer)
- `delacruz@ust-legazpi.edu.ph` (Lab Staff / admin)

These only get seeded once, on a totally empty `app_users` table (see
`DataSeeder.run` — `if (users.count() > 0) return;`). If the DB already has
users, you won't see fresh demo data; that's expected, not a bug.

No automated tests exist in this project (`src/test` is empty) — don't claim
there are any if asked; be upfront that validation is covered by the manual
rule-checks in the model/service classes instead (mention `ConsoleDemo.java`
section 5, "Rules are enforced", as your evidence it works).

## 7. If your prof edits the code live and asks you to fix it

**Fastest recovery loop:**
```powershell
mvn compile          # quick syntax/type check without a full package
mvn spring-boot:run  # restart the server to see the change (Ctrl+C first to stop old one)
```
There's no DevTools hot-reload configured, so every code change needs a
restart to see in the browser. Say this upfront so it doesn't look like the
app is "stuck" when nothing updates after an edit.

**Where edits are low-risk** (safe to improvise):
- Controller methods — adding a `@RequestParam`, a new `model.addAttribute(...)`, a new `@GetMapping`/`@PostMapping` route
- Templates (`.html`) — Thymeleaf is read fresh each request in dev if you're not caching views; a template-only change often doesn't even need a restart if `spring.thymeleaf.cache=false` is set (it isn't currently — add `spring.thymeleaf.cache=false` to `application.properties` if the prof wants live template edits)
- New getter/validation tweaks inside existing setters in `model/`

**Where edits are higher-risk** (think before touching, these will NOT compile or will silently break data integrity if done wrong):
- Entity field additions: adding a new field to `User`/`Item`/`BorrowRecord` is fine (Hibernate `ddl-auto=update` will `ALTER TABLE` it in), but:
  - it must have a getter, and if validated, a setter that's called from the constructor
  - **never add a `@Column(nullable = false)` field to an existing table with existing rows** — Hibernate will try to add a NOT NULL column with no default and the migration will fail at startup. Make new required-looking fields nullable (`Boolean`/wrapper types, like `suspended` already does) with a comment like the existing ones ("nullable so the column can be added to the existing table").
  - If removing/renaming a field, Hibernate `update` mode does **not** drop/rename columns — stale columns just sit there unused. That's fine to mention if asked "does removing this break the DB", answer: no, it leaves an orphan column, data-loss-safe by accident.
- `BorrowService` method signatures: controllers call these by exact name + arg order. If you rename a method or change param order here, you must update the matching controller call in the same edit, or you get a compile error, not a runtime one — so the build will just fail loudly, which is actually easier to recover from than a silent bug.
- `requireStatus(...)` checks in `BorrowRecord`: if your prof asks you to "skip" a status check to allow some new transition, do it explicitly (add a new allowed status to the check) rather than deleting the check — deleting it is what breaks the state machine's invariants (e.g. you could hand over an item that was never approved).
- Abstract methods (`getBorrowLimit`, `getRole`, `getMaxBorrowDays`, `getCategory`): if your prof asks you to add a new subclass (new item type, new user role), you MUST implement every abstract method in the parent, add a unique `@DiscriminatorValue("...")`, and add a case anywhere a `switch` enumerates types (see `ItemController.add()`'s `switch (type) { case "Calculator" -> ...}` — new item types need a new `case` there and probably a new entry in `ItemController.CATEGORIES`).

**If the server won't start after an edit**, the error is almost always one of:
1. A missing getter/setter the constructor or a template references → compiler error, read the line number, it's exact.
2. A Thymeleaf template referencing a model attribute that no longer exists → runtime 500 page, error names the missing attribute/expression.
3. A bad `@Column`/entity change Hibernate can't apply → look for `SchemaManagementException` in the console on startup; fix is almost always "make the new column nullable."

## 8. Things to pre-empt before the prof even asks

- **No Spring Security** — login is hand-rolled via `HttpSession` + `CurrentUser`. If asked "why not Spring Security," answer: scope decision to keep the auth logic visible and simple for a class project; the password hashing itself (PBKDF2 + random salt) is still done properly by hand in `PasswordHasher`.
- **No CSRF protection** on POST forms — reasonable follow-up question; honest answer is it's out of scope for this assignment, Spring Security would normally add it.
- **No automated tests** — be upfront, point to `ConsoleDemo.java` as the manual verification instead.
- **`@SuppressWarnings("null")`** on a couple of controllers — these exist because IDs/records always come from the DB in context, not because null-safety was ignored; explain the reasoning if asked instead of looking defensive.
- **Email sending** (`EmailService`) can run in a "redirect" mode when not configured (`email.isConfigured()`/`getRedirectTo()` shown on the admin dashboard) — check that file if the prof asks "does it really send email" so you know whether you're in live or stub mode during the demo.

## 9. 60-second summary if asked "walk me through your code"

"It's a Spring MVC app: controllers in `web/` handle requests and only talk to
services, `BorrowService` holds every borrowing rule, and the model classes in
`model/` are JPA entities that double as the OOP demo — `User` and `Item` are
abstract parents with three subclasses each, overriding borrow limits, roles,
max-borrow-days and categories. `BorrowRecord` is a little state machine with
six statuses and guards every transition with `requireStatus`, so bad
sequences throw instead of corrupting data. Repositories are plain Spring Data
JPA, Postgres is the real datastore, and `DataSeeder` seeds four demo accounts
and a few items/records the first time the DB is empty."
