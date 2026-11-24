package com.borrowbuddy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.borrowbuddy.model.BorrowRecord;
import com.borrowbuddy.model.Item;
import com.borrowbuddy.model.OrgOfficer;
import com.borrowbuddy.model.Profile;
import com.borrowbuddy.model.Student;
import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.BorrowRecordRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.UserRepository;
import com.borrowbuddy.service.BorrowService;
import com.borrowbuddy.service.OverdueNotifier;


//   WEBSITE  (default) -> the normal website
//   TERMINAL (run with --cli) -> the flowchart menu in the console
//   User (Student / OrgOfficer / LabStaff) + Profile, Item (Calculator / LabTool / Charger),

@SpringBootApplication
@EnableScheduling // need this or the daily overdue email check won't run
@SuppressWarnings("null") // records/ids here always come from the database, so they're never null
public class BorrowBuddyApplication {
    private static final String LINE = "------------------------------------------------------------";

    private static Scanner in;
    private static BorrowService service;
    private static OverdueNotifier notifier;
    private static UserRepository users;
    private static ItemRepository items;
    private static BorrowRecordRepository records;

    public static void main(String[] args) {
        // website by default; pass --cli to get the terminal menu instead
        boolean cli = Arrays.asList(args).contains("--cli");
        if (!cli) {
            SpringApplication.run(BorrowBuddyApplication.class, args);
            return;
        }

        // terminal mode: no web server + hide the spring logs so only the flowchart steps show
        SpringApplication app = new SpringApplication(BorrowBuddyApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.main.banner-mode", "off",
                "logging.level.root", "ERROR"));
        System.out.println("Connecting to the database, please wait...");
        // try-with-resources closes the Spring context automatically when the menu ends
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            service = ctx.getBean(BorrowService.class);
            notifier = ctx.getBean(OverdueNotifier.class);
            users = ctx.getBean(UserRepository.class);
            items = ctx.getBean(ItemRepository.class);
            records = ctx.getBean(BorrowRecordRepository.class);
            in = new Scanner(System.in);

            start();
        }
        System.exit(0);
    }


    private static void start() {
        while (true) {
            title("BORROWBUDDY SYSTEM");

            // <Has an account?>  No -> Register,  Yes -> Log in
            if (!askYesNo("Has an account? (Y/N): ")) {
                register();
            }

            // [Log in] -> <Login correct?>  No -> back to Log in
            User user = login();

            // <Lab staff?>  Yes -> Admin dashboard (A),  No -> Browse or search items
            if (user.isAdmin()) {
                adminDashboard(user);
            } else {
                borrowerSide(user);
            }

            if (!askYesNo("Back to Start? (Y/N): ")) {
                System.out.println("End. Goodbye!");
                return;
            }
        }
    }

    // ===================== Register with UST-L email and student ID 

    private static void register() {
        title("REGISTER");
        while (true) {
            try {
                String email = ask("UST-L email: ");
                String studentId = ask("Student ID: ");
                if (studentId.isEmpty()) throw new IllegalArgumentException("Student ID is required.");
                if (users.findByUstlAccountIgnoreCase(email).isPresent())
                    throw new IllegalArgumentException("That UST-L email is already registered.");
                if (users.findByStudentId(studentId).isPresent())
                    throw new IllegalArgumentException("That student ID is already registered.");
                String fullName = ask("Full name: ");
                String password = ask("Password (min 8 chars, letter + number): ");
                // COMPOSITION - User HAS-A Profile
                Profile profile = new Profile(ask("Course: "), askInt("Year level (1-5): "),
                        ask("Block: "), ask("Contact info: "));
                // INHERITANCE / POLYMORPHISM - Student or OrgOfficer, both saved as a User
                User user = askYesNo("Are you an org officer? (Y/N): ")
                        ? new OrgOfficer(fullName, email, studentId, password, profile, ask("Organization: "))
                        : new Student(fullName, email, studentId, password, profile);
                users.save(user);
                System.out.println(">> Registered as " + user.getRole() + ". You can log in now.");
                return;
            } catch (IllegalArgumentException e) {
                System.out.println(">> " + e.getMessage() + " Please try again.");
            }
        }
    }

    // ===================== Log in (UST-L email or student ID + password) =====================

    private static User login() {
        title("LOG IN");
        System.out.println(" Demo: jericho.rectra@ust-legazpi.edu.ph  or  4240123 (student)");
        System.out.println("       delacruz@ust-legazpi.edu.ph (lab staff)   password: borrow123");
        System.out.println(LINE);
        while (true) {
            String account = ask("UST-L email or student ID: ");
            String password = ask("Password: ");
            Optional<User> found = account.contains("@")
                    ? users.findByUstlAccountIgnoreCase(account)
                    : users.findByStudentId(account);
            // <Login correct?>
            if (found.isPresent() && found.get().checkPassword(password)) {
                User u = found.get();
                System.out.println(">> Login correct. Welcome, " + u.getFullName() + " (" + u.getRole() + ")");
                return u;
            }
            System.out.println(">> Login incorrect. Back to Log in.");
        }
    }

    //  Lab staff = No 

    private static void borrowerSide(User user) {
        while (true) {
            user = reload(user);
            title("BORROWER / OWNER  -  " + user.getFullName());
            System.out.println(" 1. Browse or search items  ->  Request to borrow an item");
            System.out.println(" 2. Owner approves?         (answer requests for your items)");
            System.out.println(" 3. Returned on or before due date?  (your lent-out items)");
            System.out.println(" 4. Log out");
            System.out.println(LINE);
            switch (askInt("Enter choice (1-4): ")) {
                case 1 -> browseAndRequest(user);
                case 2 -> ownerApproves(user);
                case 3 -> returnCheck(user);
                case 4 -> { return; }
                default -> System.out.println(">> Pick 1 to 4.");
            }
        }
    }

    // Browse or search items - Request to borrow - System checks - Request valid?
    private static void browseAndRequest(User user) {
        while (true) {
            title("BROWSE OR SEARCH ITEMS");
            String keyword = ask("Search keyword (blank = show all): ");
            String category = ask("Category - Calculator / Lab Tool / Charger (blank = all): ");
            List<Item> found = service.searchItem(keyword, category);
            printItems(found);

            title("REQUEST TO BORROW AN ITEM");
            long itemId = askInt("Enter item ID (0 = cancel): ");
            if (itemId == 0) return;
            Item item = items.findById(itemId).orElse(null);
            if (item == null) {
                System.out.println(">> Request valid? NO - no item with ID " + itemId + ". Back to Browse.");
                continue;
            }
            int days = askInt("Days to borrow (1-" + item.getMaxBorrowDays() + "): ");

            // [System checks: not suspended, item Available, within borrow limit and allowed days]
            System.out.println(">> System checks: not suspended, item Available, within borrow limit and allowed days...");
            String result = service.requestBorrow(item, user, days);

            if (result.startsWith("Request sent")) {
                // Request valid? Yes -> Record = Pending, Item = Reserved
                System.out.println(">> Request valid? YES");
                System.out.println(">> " + result);
                System.out.println(">> Record = " + BorrowRecord.PENDING + " | Item = " + Item.RESERVED);
                System.out.println(">> Waiting for the owner (" + item.getOwner().getFullName() + ") to approve.");
                return;
            }
            // Request valid? No - back to Browse or search items
            System.out.println(">> Request valid? NO - " + result);
            System.out.println(">> Back to Browse or search items.");
        }
    }

    // Record = Pending - Owner approves?  Yes - Borrowed (due date set),  No - Declined
    private static void ownerApproves(User owner) {
        title("OWNER APPROVES?");
        List<BorrowRecord> pending = service.incomingRequests(owner);
        printRecords(pending);
        if (pending.isEmpty()) return;

        long id = askInt("Enter record ID to answer (0 = back): ");
        if (id == 0) return;

        if (askYesNo("Approve this request? (Y/N): ")) {
            String place = ask("Pickup place: ");
            String now = LocalDateTime.now(BorrowRecord.ZONE).withSecond(0).withNano(0).toString();
            StringBuilder msg = new StringBuilder();
            BorrowRecord approved = service.approveRequest(id, owner, place, now, msg);
            if (approved == null) {
                System.out.println(">> " + msg);
                return;
            }
            notifier.emailMeetupDetails(approved);
            // item is handed over right away - due date starts counting
            service.confirmHandOver(id, owner);
            BorrowRecord r = service.findRecord(id);
            System.out.println(">> Owner approves? YES");
            System.out.println(">> Record = " + r.getStatus() + " | Due date = " + r.getDueDate()
                    + " | Item = " + r.getItem().getStatus());
        } else {
            String result = service.declineRequest(id, owner);
            System.out.println(">> Owner approves? NO - " + result);
            BorrowRecord r = service.findRecord(id);
            if (r != null && r.isDeclined()) {
                System.out.println(">> Record = " + r.getStatus() + " | Item = " + r.getItem().getStatus());
                System.out.println(">> End.");
            }
        }
    }

    // <Returned on or before due date?>
    //   Yes -> Owner clicks Received -> Record = Returned, Item = Available
    //   No  -> Record shown as Overdue -> Owner reports Not received -> lab staff notified
    private static void returnCheck(User owner) {
        title("RETURNED ON OR BEFORE DUE DATE?");
        List<BorrowRecord> lent = records.findAll(Sort.by("id")).stream()
                .filter(r -> r.isActive() && r.isLentBy(owner)).toList();
        printRecords(lent);
        if (lent.isEmpty()) return;

        long id = askInt("Enter record ID (0 = back): ");
        if (id == 0) return;
        BorrowRecord r = service.findRecord(id);
        if (r == null || !r.isActive() || !r.isLentBy(owner)) {
            System.out.println(">> That is not one of your borrowed-out items.");
            return;
        }

        if (!r.isOverdue()) {
            System.out.println(">> Not overdue yet (due " + r.getDueDate() + ").");
            if (askYesNo("Owner clicks Received? (Y/N): ")) {
                printReceived(id, owner);
            }
            return;
        }

        // No -> Record shown as Overdue; daily reminder email to borrower
        System.out.println(">> Returned on or before due date? NO");
        System.out.println(">> Record shown as Overdue (" + r.getDaysOverdue() + " day(s) late).");
        System.out.println(">> Daily reminder email goes to " + r.getBorrower().getFullName() + ".");
        System.out.println(" 1. Item came back late - Owner clicks Received");
        System.out.println(" 2. Owner reports Not received (lab staff are notified)");
        switch (askInt("Enter choice (1-2): ")) {
            case 1 -> printReceived(id, owner);
            case 2 -> {
                StringBuilder msg = new StringBuilder();
                BorrowRecord reported = service.reportNotReceived(id, owner, msg);
                if (reported != null) {
                    notifier.alertAdminsOfReport(reported);
                    msg.append(" Lab staff were notified.");
                }
                System.out.println(">> " + msg);
            }
            default -> System.out.println(">> Invalid choice.");
        }
    }

    private static void printReceived(long id, User owner) {
        System.out.println(">> " + service.markReturned(id, owner));
        BorrowRecord r = service.findRecord(id);
        if (r != null && r.isReturned()) {
            System.out.println(">> Record = " + r.getStatus() + " | Item = " + r.getItem().getStatus());
            System.out.println(">> End.");
        }
    }

    // ===================== Lab staff = Yes -> Admin dashboard (A) =====================

    private static void adminDashboard(User admin) {
        while (true) {
            title("ADMIN DASHBOARD (MONITOR RECORDS)");
            List<BorrowRecord> all = records.findAll(Sort.by(Sort.Direction.DESC, "id"));
            System.out.println(" Pending: " + all.stream().filter(BorrowRecord::isPending).count()
                    + " | Borrowed: " + all.stream().filter(BorrowRecord::isActive).count()
                    + " | Overdue: " + all.stream().filter(BorrowRecord::isOverdue).count()
                    + " | Returned: " + all.stream().filter(BorrowRecord::isReturned).count());
            System.out.println(LINE);
            System.out.println(" OVERDUE / NOT RECEIVED");
            printRecords(service.lendingRecords(admin, "overdue"));

            // (A) -> [Lab staff remind or suspend the borrower] -> [lab staff resolve the case]
            System.out.println(" 1. Remind borrower (email)");
            System.out.println(" 2. Suspend borrower");
            System.out.println(" 3. Reinstate borrower");
            System.out.println(" 4. Item returned - lab staff resolve the case");
            System.out.println(" 5. Run daily overdue check now");
            System.out.println(" 6. View all records");
            System.out.println(" 7. Log out");
            System.out.println(LINE);
            switch (askInt("Enter choice (1-7): ")) {
                case 1 -> {
                    BorrowRecord r = service.findRecord((long) askInt("Record ID: "));
                    System.out.println(">> " + (r == null ? "Record not found." : notifier.remindBorrower(r)));
                }
                case 2 -> changeSuspension(admin, true);
                case 3 -> changeSuspension(admin, false);
                case 4 -> {
                    long id = askInt("Record ID: ");
                    System.out.println(">> " + service.resolveUnreturned(id, admin));
                    BorrowRecord r = service.findRecord(id);
                    if (r != null && r.isReturned()) {
                        System.out.println(">> Record = " + r.getStatus() + " | Item = " + r.getItem().getStatus());
                        System.out.println(">> End.");
                    }
                }
                case 5 -> System.out.println(">> " + notifier.runDailyCheck());
                case 6 -> printRecords(all);
                case 7 -> { return; }
                default -> System.out.println(">> Pick 1 to 7.");
            }
        }
    }

    private static void changeSuspension(User admin, boolean suspend) {
        List<BorrowRecord> overdue = service.lendingRecords(admin, "overdue");
        overdue.stream().map(BorrowRecord::getBorrower).distinct().forEach(u ->
                System.out.printf("  user ID %-3d %-20s %s%n", u.getId(), u.getFullName(),
                        u.isSuspended() ? "(suspended)" : ""));
        User target = users.findById((long) askInt("Borrower's user ID: ")).orElse(null);
        if (target == null) {
            System.out.println(">> User not found.");
            return;
        }
        try {
            if (suspend) target.suspend(admin); else target.reinstate(admin);
            users.save(target);
            System.out.println(">> " + target.getFullName() + (suspend ? " is suspended from borrowing."
                    : " can borrow again."));
        } catch (IllegalArgumentException e) {
            System.out.println(">> " + e.getMessage());
        }
    }

    // ===================== PRINTING =====================

    private static void title(String text) {
        System.out.println();
        System.out.println(LINE);
        System.out.println(" " + text);
        System.out.println(LINE);
    }

    private static void printItems(List<Item> list) {
        System.out.printf(" %-3s %-26s %-28s %-10s %-4s %s%n", "ID", "Item", "Category", "Status", "Max", "Owner");
        System.out.println(LINE);
        if (list.isEmpty()) System.out.println(" (no items found)");
        for (Item i : list) {
            System.out.printf(" %-3d %-26s %-28s %-10s %-4s %s%n", i.getId(), cut(i.getName(), 26),
                    cut(i.getCategory(), 28), i.getStatus(), i.getMaxBorrowDays() + "d", i.getOwner().getFullName());
        }
        System.out.println(LINE);
    }

    private static void printRecords(List<BorrowRecord> list) {
        if (list.isEmpty()) {
            System.out.println(" (nothing here)");
            System.out.println(LINE);
            return;
        }
        System.out.printf(" %-3s %-24s %-16s %-16s %-17s %s%n", "ID", "Item", "Borrower", "Owner", "Status", "Due");
        for (BorrowRecord r : list) {
            System.out.printf(" %-3d %-24s %-16s %-16s %-17s %s%n", r.getId(), cut(r.getItem().getName(), 24),
                    cut(r.getBorrower().getFullName(), 16), cut(r.getLender().getFullName(), 16),
                    r.getStatusLabel(), r.getDueDate() == null ? "-" : r.getDueDate());
        }
        System.out.println(LINE);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    private static User reload(User u) {
        return users.findById(u.getId()).orElse(u);
    }

    // ===================== INPUT HELPERS =====================

    private static String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println();
            System.exit(0); // input closed (ctrl+z / ctrl+d)
        }
        return in.nextLine().trim();
    }

    private static int askInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(ask(prompt));
            } catch (NumberFormatException e) {
                System.out.println(">> Please enter a number.");
            }
        }
    }

    private static boolean askYesNo(String prompt) {
        while (true) {
            String s = ask(prompt);
            if (s.equalsIgnoreCase("y")) return true;
            if (s.equalsIgnoreCase("n")) return false;
            System.out.println(">> Please enter Y or N.");
        }
    }
}
