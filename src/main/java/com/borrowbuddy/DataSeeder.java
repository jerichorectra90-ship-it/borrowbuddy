package com.borrowbuddy;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// puts some demo data in the db on first run, skips it if there are users already
// pw for every demo account is Borrow@123 (has to pass the strong password rule).
// demo accounts count as already verified, so they can log in right away
@Component
public class DataSeeder implements CommandLineRunner {
    private static final String DEMO_PASSWORD = "Borrow@123";

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

        // POLYMORPHISM - diff kinds of User but they all get saved thru the same User repo
        User jericho = users.save(new Student("Jericho Rectra", "jericho.rectra@ust-legazpi.edu.ph",
                "4240123", DEMO_PASSWORD, new Profile("BSIT", 2, "2G", "Messenger: Jericho Rectra")));
        User maria = users.save(new Student("Maria Santos", "maria.santos@ust-legazpi.edu.ph",
                "4240456", DEMO_PASSWORD, new Profile("BSIT", 2, "2G", "0917 000 0001")));
        User paolo = users.save(new OrgOfficer("Paolo Reyes", "paolo.reyes@ust-legazpi.edu.ph",
                "4230789", DEMO_PASSWORD, new Profile("BSCS", 3, "3A", "Messenger: Paolo Reyes"),
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

        // returned on time - Jericho borrowed Maria's calcu last week
        BorrowRecord returned = new BorrowRecord(casio, jericho, 3);
        returned.approve("CICS lobby", BorrowRecord.today().minusDays(7).atTime(10, 0));
        returned.handOver(BorrowRecord.today().minusDays(7));
        returned.markReturned(BorrowRecord.today().minusDays(5));
        records.save(returned);

        // overdue one - Maria took the org's laptop charger for 1 day, that was 4 days ago
        BorrowRecord overdue = new BorrowRecord(laptopCharger, maria, 1);
        overdue.approve("Computer Society office", BorrowRecord.today().minusDays(4).atTime(13, 0));
        overdue.handOver(BorrowRecord.today().minusDays(4));
        records.save(overdue);

        // pending - Maria wants Jericho's soldering kit (gonna approve this one live in the demo)
        records.save(new BorrowRecord(solderKit, maria, 2));

        // pending too - Jericho asking Paolo for the TI-84
        records.save(new BorrowRecord(ti84, jericho, 3));
    }
}
