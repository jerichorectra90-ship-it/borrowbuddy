package com.borrowbuddy.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// COMPOSITION - a BorrowRecord HAS-A Item, a borrower and a lender (lender = whoever owns the item)
// one record = one whole borrow, from the request up to the return. goes like:
//   Pending (item Reserved)
//     -> Approved (owner said ok + set a pickup place/time, item still Reserved)
//     -> Borrowed (owner handed it over, due date starts counting)
//     -> [Return pending] (borrower says "I returned it", still Borrowed til the owner confirms)
//     -> Returned (owner clicked Received)
//   Pending -> Declined   (item goes back to Available)
//   Approved -> Cancelled (borrower never picked it up, item goes back to Available)

@Entity
@Table(name = "borrow_records")
public class BorrowRecord {
    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    public static final String PENDING = "Pending";
    public static final String DECLINED = "Declined";
    public static final String APPROVED = "Approved";
    public static final String CANCELLED = "Cancelled";
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

    // pickup meetup, set by the lender when approving. nullable too (old records don't have it)
    @Column(length = 120)
    private String meetupPlace;
    private LocalDateTime meetupTime;

    // the day the borrower clicked "I returned it". null = they haven't
    private LocalDate returnClaimedDate;

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

    // owner says yes and sets where + when the borrower picks it up.
    // item stays Reserved bc the borrower doesn't have it yet
    public void approve(String place, LocalDateTime time) {
        requireStatus(PENDING);
        String p = place == null ? "" : place.trim();
        if (p.isEmpty()) {
            throw new IllegalArgumentException("Pickup place is required.");
        }
        if (p.length() > 120) {
            throw new IllegalArgumentException("Pickup place must be 120 characters or fewer.");
        }
        if (time == null) {
            throw new IllegalArgumentException("Pickup date and time are required.");
        }
        status = APPROVED;
        meetupPlace = p;
        meetupTime = time;
    }

    public void decline() {
        requireStatus(PENDING);
        status = DECLINED;
        item.setStatus(Item.AVAILABLE);
    }

    // OVERLOADING - hand over today, or on some date (that one's for the demo data)
    public void handOver() {
        handOver(today());
    }

    // owner gave the item to the borrower at the meetup. NOW the due date starts counting
    public void handOver(LocalDate startDate) {
        requireStatus(APPROVED);
        if (requestDate.isAfter(startDate)) {
            requestDate = startDate;
        }
        status = BORROWED;
        borrowDate = startDate;
        dueDate = startDate.plusDays(requestedDays);
        item.setStatus(Item.BORROWED);
    }

    // borrower didn't show up for pickup, owner cancels it
    public void cancelPickup() {
        requireStatus(APPROVED);
        status = CANCELLED;
        item.setStatus(Item.AVAILABLE);
    }

    // borrower side: "I returned it". doesn't close the record, the owner still has to confirm
    public void claimReturned() {
        requireStatus(BORROWED);
        if (isReturnPending()) {
            throw new IllegalStateException("You already said you returned it. Waiting for the owner to confirm.");
        }
        returnClaimedDate = today();
    }

    // owner says it never came back, so the borrower's "I returned it" gets cancelled
    public void rejectReturnClaim() {
        returnClaimedDate = null;
    }

    // OVERLOADING again - return on the day the borrower said they gave it back (or today),
    // or on some date (demo data too)
    public void markReturned() {
        markReturned(returnClaimedDate != null ? returnClaimedDate : today());
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
    public String getMeetupPlace() { return meetupPlace; }
    public LocalDateTime getMeetupTime() { return meetupTime; }
    public LocalDate getReturnClaimedDate() { return returnClaimedDate; }

    public boolean isPending() { return PENDING.equals(status); }
    public boolean isDeclined() { return DECLINED.equals(status); }
    public boolean isApproved() { return APPROVED.equals(status); }
    public boolean isCancelled() { return CANCELLED.equals(status); }
    public boolean isActive() { return BORROWED.equals(status); }
    public boolean isReturned() { return RETURNED.equals(status); }

    // borrower clicked "I returned it" but the owner hasn't confirmed yet
    public boolean isReturnPending() { return isActive() && returnClaimedDate != null; }

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
        if (isApproved()) return "Waiting for pickup";
        if (isReturnPending()) return "Return pending";
        if (isOverdue()) return "Overdue";
        if (isReturnedLate()) return "Returned late";
        return status;
    }
}
