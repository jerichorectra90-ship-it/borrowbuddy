package com.borrowbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.ZoneId;

// COMPOSITION: a BorrowRecord HAS-A Item, a borrower, and a lender (the item owner).
// One record follows a borrow from request to return:
//   Pending (item Reserved) -> Borrowed (owner approved) -> Returned (owner marked it)
//   Pending -> Declined (item back to Available)
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

    protected BorrowRecord() {}

    // A new record is a borrow REQUEST. The item becomes Reserved until the owner responds.
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

    // OVERLOADING: approve today, or approve with a given start date (used for demo data)
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

    // OVERLOADING: return today, or on a given date (used for demo data)
    public void markReturned() {
        markReturned(today());
    }

    public void markReturned(LocalDate date) {
        requireStatus(BORROWED);
        status = RETURNED;
        returnDate = date;
        item.setStatus(Item.AVAILABLE);
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
