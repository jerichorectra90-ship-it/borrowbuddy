package com.borrowbuddy.service;

import com.borrowbuddy.model.BorrowRecord;
import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.BorrowRecordRepository;
import com.borrowbuddy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.borrowbuddy.service.EmailService.esc;

// Email notifications for items that were not returned on their due date.
//  - Every morning (8:00 AM Manila time) it emails each overdue borrower a reminder
//    (at most every 2 days per record) and emails lab staff a summary of all overdue borrowers.
//  - Lab staff are emailed right away when a lender reports an item as "Not received".
//  - Lab staff can also send a reminder or run the check manually from the admin dashboard.
@Service
public class OverdueNotifier {
    private static final int REMINDER_EVERY_DAYS = 2;

    private final BorrowRecordRepository records;
    private final UserRepository users;
    private final EmailService email;
    private final String siteUrl;

    public OverdueNotifier(BorrowRecordRepository records, UserRepository users, EmailService email,
                           @Value("${SITE_URL:https://borrowbuddy-rxdv.onrender.com}") String siteUrl) {
        this.records = records;
        this.users = users;
        this.email = email;
        this.siteUrl = siteUrl;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila")
    public void scheduledDailyCheck() {
        runDailyCheck();
    }

    // Returns a short summary for the admin dashboard.
    @Transactional
    public String runDailyCheck() {
        List<BorrowRecord> overdue = records.findAll(Sort.by("dueDate")).stream()
                .filter(BorrowRecord::isOverdue).toList();
        if (overdue.isEmpty()) {
            return "Overdue check done: nothing is overdue, so no emails were needed.";
        }
        int reminders = 0;
        for (BorrowRecord r : overdue) {
            if (r.getLastReminderDate() == null
                    || ChronoUnit.DAYS.between(r.getLastReminderDate(), BorrowRecord.today()) >= REMINDER_EVERY_DAYS) {
                if (sendBorrowerReminder(r)) reminders++;
            }
        }
        int admins = emailAdmins("Overdue items: " + overdue.size() + " borrower(s) have not returned items",
                adminSummaryHtml(overdue));
        return "Overdue check done: " + overdue.size() + " overdue record(s), " + reminders
                + " borrower reminder(s) sent, summary sent to " + admins + " lab staff address(es).";
    }

    // Lab staff clicked "Email reminder" on one record.
    @Transactional
    public String remindBorrower(BorrowRecord r) {
        if (!r.isOverdue()) {
            return "That record is not overdue.";
        }
        return sendBorrowerReminder(r)
                ? "Reminder emailed to " + r.getBorrower().getFullName() + "."
                : "The reminder could not be sent. See the Email log tab.";
    }

    // Lender clicked "Not received": alert lab staff right away.
    public void alertAdminsOfReport(BorrowRecord r) {
        String html = "<p>A lender reported that a borrowed item was <b>not returned</b>.</p>"
                + recordTable(List.of(r))
                + "<p>Open the <a href=\"" + siteUrl + "/admin\">BorrowBuddy admin dashboard</a> to follow up.</p>";
        emailAdmins("Not returned: " + r.getItem().getName() + " (borrowed by " + r.getBorrower().getFullName() + ")", html);
    }

    private boolean sendBorrowerReminder(BorrowRecord r) {
        User b = r.getBorrower();
        String html = "<p>Hi " + esc(b.getFullName()) + ",</p>"
                + "<p>This is a reminder from BorrowBuddy that the item below was due on <b>"
                + r.getDueDate() + "</b> and is now <b>" + r.getDaysOverdue() + " day(s) overdue</b>.</p>"
                + recordTable(List.of(r))
                + "<p>Please return it to " + esc(r.getLender().getFullName()) + " (" + esc(r.getLender().getProfile().getContactInfo())
                + ") as soon as possible. Lab staff may suspend borrowing until the item is returned.</p>"
                + "<p>— BorrowBuddy, UST-Legazpi</p>";
        boolean ok = email.send(b.getUstlAccount(), b.getFullName(),
                "Overdue: please return " + r.getItem().getName(), html);
        if (ok) {
            r.recordReminderSent();
            records.save(r);
        }
        return ok;
    }

    private int emailAdmins(String subject, String html) {
        Set<String> to = new LinkedHashSet<>();
        if (!email.getAdminEmail().isEmpty()) {
            to.add(email.getAdminEmail());
        } else {
            users.findAll().stream().filter(User::isAdmin).forEach(u -> to.add(u.getUstlAccount()));
        }
        int sent = 0;
        for (String address : to) {
            if (email.send(address, "Lab staff", subject, html)) sent++;
        }
        return sent;
    }

    private String adminSummaryHtml(List<BorrowRecord> overdue) {
        return "<p>These borrowers have not returned items on their due date:</p>"
                + recordTable(overdue)
                + "<p>Manage them on the <a href=\"" + siteUrl + "/admin\">BorrowBuddy admin dashboard</a>.</p>";
    }

    private static String recordTable(List<BorrowRecord> list) {
        StringBuilder sb = new StringBuilder("<table cellpadding=\"6\" style=\"border-collapse:collapse;border:1px solid #ddd\">"
                + "<tr style=\"background:#f3f4f8\"><th align=\"left\">Item</th><th align=\"left\">Borrower</th>"
                + "<th align=\"left\">Owner</th><th align=\"left\">Due</th><th align=\"left\">Days late</th><th align=\"left\">Reported</th></tr>");
        for (BorrowRecord r : list) {
            sb.append("<tr><td>").append(esc(r.getItem().getName()))
              .append("</td><td>").append(esc(r.getBorrower().getFullName())).append("<br><small>")
              .append(esc(r.getBorrower().getUstlAccount())).append(" · ").append(esc(r.getBorrower().getProfile().getContactInfo()))
              .append("</small></td><td>").append(esc(r.getLender().getFullName()))
              .append("</td><td>").append(r.getDueDate())
              .append("</td><td><b style=\"color:#c62828\">").append(r.getDaysOverdue())
              .append("</b></td><td>").append(r.isReportedUnreturned() ? "Lender: not received" : "—")
              .append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }
}
