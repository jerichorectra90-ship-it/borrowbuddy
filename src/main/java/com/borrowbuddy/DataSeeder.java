package com.borrowbuddy;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Fills the database with demo data the first time the app starts (only if there are no users yet).
// All demo accounts use the password: borrow123
@Component
public class DataSeeder implements CommandLineRunner {
    private static final String DEMO_PASSWORD = "borrow123";

    private final UserRepository users;
    private final ItemRepository items;
    private final BorrowRecordRepository records;

    public DataSeeder(UserRepository users, ItemRepository items, BorrowRecordRepository records) {
        this.users = users;
        this.items = items;
        this.records = records;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;

        // POLYMORPHISM: different User children saved through the same User repository
        User jericho = users.save(new Student("Jericho Rectra", "jericho.rectra@ust-legazpi.edu.ph",
                "2024-00123", DEMO_PASSWORD, new Profile("BSIT", 2, "2G", "Messenger: Jericho Rectra")));
        User maria = users.save(new Student("Maria Santos", "maria.santos@ust-legazpi.edu.ph",
                "2024-00456", DEMO_PASSWORD, new Profile("BSIT", 2, "2G", "0917 000 0001")));
        User paolo = users.save(new OrgOfficer("Paolo Reyes", "paolo.reyes@ust-legazpi.edu.ph",
                "2023-00789", DEMO_PASSWORD, new Profile("BSCS", 3, "3A", "Messenger: Paolo Reyes"),
                "Computer Society"));
        users.save(new LabStaff("Engr. Dela Cruz", "delacruz@ust-legazpi.edu.ph",
                "STAFF-001", DEMO_PASSWORD, new Profile("Engineering Lab", 1, "Lab", "Lab Office, Room 101")));

        Item casio = items.save(new Calculator("Scientific Calculator", "Casio fx-991ES Plus",
                "With cover. Good for Calculus and Physics exams.", maria));
        Item ti84 = items.save(new Calculator("Graphing Calculator", "TI-84 Plus",
                "Org-owned. Please return with batteries inside.", paolo));
        Item laptopCharger = items.save(new Charger("Laptop Charger 65W USB-C",
                "Org-owned. Works with most USB-C laptops.", paolo));
        items.save(new LabTool("Digital Multimeter",
                "With probes. For Physics and Electronics lab.", paolo));
        Item solderKit = items.save(new LabTool("Soldering Iron Kit",
                "Includes stand and solder wire.", jericho));
        items.save(new Charger("Phone Charger (Type-C)", "Fast charger with 1 m cable.", maria));

        // Returned on time: Jericho borrowed Maria's calculator last week
        BorrowRecord returned = new BorrowRecord(casio, jericho, 3);
        returned.approve(BorrowRecord.today().minusDays(7));
        returned.markReturned(BorrowRecord.today().minusDays(5));
        records.save(returned);

        // Overdue: Maria borrowed the org's laptop charger for 1 day, 4 days ago
        BorrowRecord overdue = new BorrowRecord(laptopCharger, maria, 1);
        overdue.approve(BorrowRecord.today().minusDays(4));
        records.save(overdue);

        // Pending: Maria asks for Jericho's soldering kit (approve this live in the demo)
        records.save(new BorrowRecord(solderKit, maria, 2));

        // Pending: Jericho asks Paolo for the TI-84
        records.save(new BorrowRecord(ti84, jericho, 3));
    }
}
