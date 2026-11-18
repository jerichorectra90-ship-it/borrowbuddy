import com.borrowbuddy.model.*;

import java.time.LocalDate;
import java.util.List;

// terminal-only demo of the backend logic. no web pages, no database, just the real model classes.
// run it from the borrowbuddy-web folder (after "mvn compile"):
//   java -cp "target/classes;$HOME\.m2\repository\jakarta\persistence\jakarta.persistence-api\3.1.0\jakarta.persistence-api-3.1.0.jar" demo/ConsoleDemo.java
public class ConsoleDemo {

    public static void main(String[] args) {
        line("1. USERS  (INHERITANCE + POLYMORPHISM: each child of User gives its own limit and role)");
        User maria = new Student("Maria Santos", "maria.santos@ust-legazpi.edu.ph", "2024-00456",
                "borrow123", new Profile("BSIT", 2, "2G", "0917 000 0001"));
        User paolo = new OrgOfficer("Paolo Reyes", "paolo.reyes@ust-legazpi.edu.ph", "2023-00789",
                "borrow123", new Profile("BSCS", 3, "3A", "Messenger: Paolo Reyes"), "Computer Society");
        User staff = new LabStaff("Engr. Dela Cruz", "delacruz@ust-legazpi.edu.ph", "STAFF-001",
                "borrow123", new Profile("Engineering Lab", 1, "Lab", "Lab Office, Room 101"));
        for (User u : List.of(maria, paolo, staff)) {
            System.out.printf("   %-16s role: %-30s borrow limit: %d   admin: %b%n",
                    u.getFullName(), u.getRole(), u.getBorrowLimit(), u.isAdmin());
        }

        line("2. ITEMS  (INHERITANCE + OVERRIDING: each child of Item has its own max days and category)");
        Item calc = new Calculator("Graphing Calculator", "TI-84 Plus", "Org-owned.", paolo);
        Item charger = new Charger("Laptop Charger 65W", "USB-C.", paolo);
        Item meter = new LabTool("Digital Multimeter", "With probes.", paolo);
        for (Item i : List.of(calc, charger, meter)) {
            System.out.printf("   %-22s category: %-26s max days: %d   status: %s%n",
                    i.getName(), i.getCategory(), i.getMaxBorrowDays(), i.getStatus());
        }

        line("3. ENCAPSULATION  (password is only stored as a hash, there is no getter)");
        System.out.println("   checkPassword(\"borrow123\") -> " + maria.checkPassword("borrow123"));
        System.out.println("   checkPassword(\"wrong\")     -> " + maria.checkPassword("wrong"));

        line("4. FULL BORROW FLOW  (Maria borrows Paolo's calculator for 3 days)");
        BorrowRecord r = new BorrowRecord(calc, maria, 3);
        show("Maria requests it", r);

        r.approve("CICS lobby", LocalDate.now(BorrowRecord.ZONE).plusDays(1).atTime(15, 0));
        show("Paolo approves + sets pickup", r);
        System.out.println("      pickup: " + r.getMeetupPlace() + " at " + r.getMeetupTime());

        r.handOver();
        show("Paolo clicks Handed over", r);
        System.out.println("      borrowed: " + r.getBorrowDate() + "   due: " + r.getDueDate());

        r.claimReturned();
        show("Maria clicks I returned it", r);

        r.markReturned();
        show("Paolo clicks Received", r);
        System.out.println("      returned on: " + r.getReturnDate() + "   late? " + r.isReturnedLate());

        line("5. RULES ARE ENFORCED  (wrong steps throw an error instead of breaking the data)");
        tryStep("Hand over an item that's already returned", r::handOver);
        tryStep("Approve with no pickup place",
                () -> new BorrowRecord(meter, maria, 1).approve("  ", LocalDate.now().atTime(9, 0)));
        tryStep("Student registers with a non UST-L email",
                () -> new Student("X", "x@gmail.com", "1", "borrow123", new Profile("BSIT", 1, "1A", "0917")));

        line("6. OVERDUE  (OVERLOADING: handOver(date) starts the borrow 4 days ago, charger max is 1 day)");
        BorrowRecord late = new BorrowRecord(charger, maria, 1);
        late.approve("Org office", LocalDate.now(BorrowRecord.ZONE).minusDays(4).atTime(13, 0));
        late.handOver(LocalDate.now(BorrowRecord.ZONE).minusDays(4));
        show("Charger, 4 days after pickup", late);
        System.out.println("      overdue? " + late.isOverdue() + "   days late: " + late.getDaysOverdue());
        late.reportNotReceived();
        System.out.println("      Paolo clicks Not received -> reported to lab staff: " + late.isReportedUnreturned());
        late.resolveByAdmin();
        show("Lab staff resolves the case", late);

        System.out.println();
        System.out.println("Done. Every step above ran the same classes the website uses.");
    }

    private static void show(String step, BorrowRecord r) {
        System.out.printf("   %-30s record: %-20s item: %s%n", step, r.getStatusLabel(), r.getItem().getStatus());
    }

    private static void tryStep(String what, Runnable step) {
        try {
            step.run();
            System.out.println("   " + what + " -> allowed (unexpected)");
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.out.println("   " + what + " -> blocked: " + e.getMessage());
        }
    }

    private static void line(String title) {
        System.out.println();
        System.out.println("== " + title);
    }
}
