# BorrowBuddy

A Peer-to-Peer Academic Resource Borrowing System for UST-Legazpi Students.
Java 17 + Spring Boot 3.5 (web pages with Thymeleaf), Supabase (Postgres) database, deployed on Render.

## Deliverable 2: where each must-have feature is

| # | Feature | Where |
|---|---------|-------|
| 1 | UST-L login and student profile | Log in with UST-L email or student ID + password. Register page and Profile page (full name, course, year level, block, contact info). `AuthController` |
| 2 | Item listing | "Lend an item" page: name, category, description; owner is the logged-in user, who also sees their own listings there. `ItemController.lendForm / add` |
| 3 | Item availability status | Every item shows Available / Reserved / Borrowed. Search by keyword and/or category. `BorrowService.searchItem` |
| 4 | Borrow request and approval | Borrower clicks Request (item becomes Reserved). Owner approves (becomes Borrowed) or declines (back to Available) on the Requests page. `BorrowRecord.approve / decline` |
| 5 | Lending records with due dates | My records page: borrower, owner, borrowed date, due date, return date. Lab staff see all records. |
| 6 | Return and overdue tracking | Only the lender (owner) confirms **Received**, or reports **Not received** after the due date, which escalates the case to lab staff. Overdue items are flagged in red, with an Overdue filter. `BorrowRecord.isOverdue / reportNotReceived` |
| + | Admin dashboard (Lab staff) | Lab staff land on `/admin`: totals, pending requests, all users. Only lab staff manage borrowers who did not return items: email reminder, suspend / reinstate borrowing, resolve the case. `AdminController` |
| + | Overdue email notifications | Every day at 8:00 AM (Manila) overdue borrowers get a reminder and lab staff get a summary; lab staff are emailed immediately when a lender reports "Not received". Sent through the Brevo email API and logged on the dashboard. `OverdueNotifier`, `EmailService` |

Out of scope (scope note): payments, delivery, item deposits.

## Deliverable 3: where each OOP concept is

| Concept | In the code |
|---------|-------------|
| Encapsulation (private fields + controlled access) | All fields in `User`, `Profile`, `Item`, `BorrowRecord` are private. Setters validate input (UST-L email domain, year level 1-5, required fields). The password has no getter and is stored only as a hash (`setPassword`, `checkPassword`). A user's suspension can only change through `suspend(admin)` / `reinstate(admin)`, which reject non-admins. `Item.setStatus` is package-private, so only `BorrowRecord` can change an item's status. |
| Inheritance (IS-A) | `Student`, `OrgOfficer`, `LabStaff` extend `User`. `Calculator`, `LabTool`, `Charger` extend `Item`. |
| Overriding (child gives specialized behavior) | `getBorrowLimit()` (Student 2, OrgOfficer 5), `getRole()`, `isAdmin()` (LabStaff), `getMaxBorrowDays()` (Calculator 3, LabTool 2, Charger 1), `getCategory()`. |
| Polymorphism (common reference, different child behavior) | `BorrowService.requestBorrow` works with `Item` and `User` references and calls `item.getMaxBorrowDays()` and `borrower.getBorrowLimit()`; each child answers differently. `searchItem` filters by `getCategory()`. The pages list mixed item types through one `List<Item>`. |
| Overloading (same name, different parameters) | `BorrowService.searchItem(keyword)` / `searchItem(keyword, category)`; `requestBorrow(item, user)` / `requestBorrow(item, user, days)`; `BorrowRecord.approve()` / `approve(date)`; `markReturned()` / `markReturned(date)`. |
| Composition (HAS-A) | `User` has a `Profile`. `Item` has an owner `User`. `BorrowRecord` has an `Item`, a borrower, and a lender. |

## Live demo
https://borrowbuddy-rxdv.onrender.com

Demo data (sample students, an org officer, a lab staff admin, items and borrow records) is added automatically on first start.
