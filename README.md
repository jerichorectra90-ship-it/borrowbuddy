# BorrowBuddy

A Peer-to-Peer Academic Resource Borrowing System for UST-Legazpi Students.
Java 25 (LTS) + Spring Boot (web pages with Thymeleaf), Supabase (Postgres) database, deployed on Render.

## Deliverable 2: where each must-have feature is

| # | Feature | Where |
|---|---------|-------|
| 1 | UST-L login and student profile | Log in with UST-L email or student ID + password. Register page and Profile page (full name, course, year level, block, contact info). `AuthController` |
| 2 | Item listing | "Lend an item" form: name, category, description; owner is the logged-in user. `ItemController.add` |
| 3 | Item availability status | Every item shows Available / Reserved / Borrowed. Search by keyword and/or category. `BorrowService.searchItem` |
| 4 | Borrow request and approval | Borrower clicks Request (item becomes Reserved). Owner approves (becomes Borrowed) or declines (back to Available) on the Requests page. `BorrowRecord.approve / decline` |
| 5 | Lending records with due dates | My records page: borrower, owner, borrowed date, due date, return date. Lab staff see all records. |
| 6 | Return and overdue tracking | Only the owner can "Mark returned". Overdue items are flagged in red, with an Overdue filter. `BorrowRecord.isOverdue` |

Out of scope (scope note): payments, delivery, item deposits.

## Deliverable 3: where each OOP concept is

| Concept | In the code |
|---------|-------------|
| Encapsulation (private fields + controlled access) | All fields in `User`, `Profile`, `Item`, `BorrowRecord` are private. Setters validate input (UST-L email domain, year level 1-5, required fields). The password has no getter and is stored only as a hash (`setPassword`, `checkPassword`). `Item.setStatus` is package-private, so only `BorrowRecord` can change an item's status. |
| Inheritance (IS-A) | `Student`, `OrgOfficer`, `LabStaff` extend `User`. `Calculator`, `LabTool`, `Charger` extend `Item`. |
| Overriding (child gives specialized behavior) | `getBorrowLimit()` (Student 2, OrgOfficer 5), `getRole()`, `isAdmin()` (LabStaff), `getMaxBorrowDays()` (Calculator 3, LabTool 2, Charger 1), `getCategory()`. |
| Polymorphism (common reference, different child behavior) | `BorrowService.requestBorrow` works with `Item` and `User` references and calls `item.getMaxBorrowDays()` and `borrower.getBorrowLimit()`; each child answers differently. `searchItem` filters by `getCategory()`. The pages list mixed item types through one `List<Item>`. |
| Overloading (same name, different parameters) | `BorrowService.searchItem(keyword)` / `searchItem(keyword, category)`; `requestBorrow(item, user)` / `requestBorrow(item, user, days)`; `BorrowRecord.approve()` / `approve(date)`; `markReturned()` / `markReturned(date)`. |
| Composition (HAS-A) | `User` has a `Profile`. `Item` has an owner `User`. `BorrowRecord` has an `Item`, a borrower, and a lender. |

## Demo accounts (password: borrow123)
- Student: jericho.rectra@ust-legazpi.edu.ph (or student ID 2024-00123)
- Student: maria.santos@ust-legazpi.edu.ph
- Org officer: paolo.reyes@ust-legazpi.edu.ph
- Lab staff (admin): delacruz@ust-legazpi.edu.ph

Demo data includes one returned record, one overdue record, and two pending requests.
Suggested live demo: log in as Jericho, approve Maria's soldering kit request, then show My records.

## Run locally
1. Create a Supabase project. Click **Connect**, choose **Session pooler**, and copy host, user, and password.
2. Set environment variables:
   - `DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`
   - `DB_USER=<pooler user, looks like postgres.xxxxx>`
   - `DB_PASSWORD=<your database password>`
3. Run `mvn spring-boot:run`, then open http://localhost:8080
   Tables are created automatically and demo data is added on first start.

If you ran an older version of this project on the same database, delete the tables
`borrow_records`, `items`, `app_users` in Supabase first so they are recreated cleanly.

## Deploy on Render
1. Push this folder to a GitHub repo (do not commit passwords).
2. Render > New > Web Service > connect the repo > Runtime: Docker.
3. Add the same 3 environment variables.
4. Deploy, then open the Render URL on your laptop and phone.
5. The free tier sleeps when idle. Open the link 5 to 10 minutes before presenting.
