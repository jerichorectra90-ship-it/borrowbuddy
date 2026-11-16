package com.borrowbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.ZoneId;

// COMPOSITION - a BorrowRecord HAS-A Item, a borrower and a lender (lender = whoever owns the item)
// one record = one whole borrow, from the request up to the return. goes like:
//   Pending (item Reserved) -> Borrowed (owner said ok) -> Returned (owner marked it)
//   Pending -> Declined (item goes back to Available)

@Entity
@Table(name = "borrow_records")
public class BorrowRecord {
    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    public static final String PENDING = "Pending";
    public static final String DECLINED = "Declined";
    public static final String BORROWED = "Borrowed";
    public static final String RETURNED = "Returned";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Item item;

    @ManyToOne(optional = false)
    private User borrower;

    @ManyToOne(optional = false)
    private User lender;

    private String status;
    private int requestedDays;
    private LocalDate requestDate;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;

    // stuff for tracking unreturned items. all nullable so the columns can still be added to the old table
    private Boolean reportedUnreturned;   // lender hit "Not received" after the due date
    private LocalDate reportedDate;
    private LocalDate lastReminderDate;   // last time we emailed the borrower about it
    private Boolean resolvedByAdmin;      // lab staff closed the case already

    protected BorrowRecord() {}

    // new record = just a borrow REQUEST for now. item stays Reserved til the owner answers
    public BorrowRecord(Item item, User borrower, int days) {
        this.item = item;
        this.borrower = borrower;
        this.lender = item.getOwner();
        this.requestedDays = days;
        this.requestDate = today();
        this.status = PENDING;
        item.setStatus(Item.RESERVED);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // OVERLOADING - approve today, or pass a start date (that one's for the demo data)
    public void approve() {
        approve(today());
    }

    public void approve(LocalDate startDate) {
        requireStatus(PENDING);
        if (requestDate.isAfter(startDate)) {
            requestDate = startDate;
        }
        status = BORROWED;
        borrowDate = startDate;
        dueDate = startDate.plusDays(requestedDays);
        item.setStatus(Item.BORROWED);
    }

    public void decline() {
        requireStatus(PENDING);
        status = DECLINED;
        item.setStatus(Item.AVAILABLE);
    }

    // OVERLOADING again - return today or on some date (demo data too)
    public void markReturned() {
        markReturned(today());
    }

    public void markReturned(LocalDate date) {
        requireStatus(BORROWED);
        status = RETURNED;
        returnDate = date;
        item.setStatus(Item.AVAILABLE);
    }

    // lender side: due date passed and the item's still not back, so it gets bumped up to lab staff
    public void reportNotReceived() {
        if (!isOverdue()) {
            throw new IllegalStateException("You can report an item as not received only after its due date.");
        }
        reportedUnreturned = true;
        reportedDate = today();
    }

    // lab staff side: item's finally back so close the case
    public void resolveByAdmin() {
        if (!isOverdue()) {
            throw new IllegalStateException("Only overdue records can be resolved by lab staff.");
        }
        markReturned(today());
        resolvedByAdmin = true;
    }

    public void recordReminderSent() {
        lastReminderDate = today();
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("This record is " + status + ", not " + expected + ".");
        }
    }

    public Long getId() { return id; }
    public Item getItem() { return item; }
    public User getBorrower() { return borrower; }
    public User getLender() { return lender; }
    public String getStatus() { return status; }
    public int getRequestedDays() { return requestedDays; }
    public LocalDate getRequestDate() { return requestDate; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }

    public boolean isPending() { return PENDING.equals(status); }
    public boolean isDeclined() { return DECLINED.equals(status); }
    public boolean isActive() { return BORROWED.equals(status); }
    public boolean isReturned() { return RETURNED.equals(status); }

    public boolean isOverdue() {
        return isActive() && today().isAfter(dueDate);
    }

    public long getDaysOverdue() {
        return isOverdue() ? java.time.temporal.ChronoUnit.DAYS.between(dueDate, today()) : 0;
    }

    public boolean isReportedUnreturned() { return Boolean.TRUE.equals(reportedUnreturned); }
    public boolean isResolvedByAdmin() { return Boolean.TRUE.equals(resolvedByAdmin); }
    public LocalDate getReportedDate() { return reportedDate; }
    public LocalDate getLastReminderDate() { return lastReminderDate; }

    public boolean isReturnedLate() {
        return isReturned() && returnDate.isAfter(dueDate);
    }

    public boolean isLentBy(User user) { return lender.isSameUser(user); }

    public boolean isBorrowedBy(User user) { return borrower.isSameUser(user); }

    public String getStatusLabel() {
        if (isOverdue()) return "Overdue";
        if (isReturnedLate()) return "Returned late";
        return status;
    }
}
