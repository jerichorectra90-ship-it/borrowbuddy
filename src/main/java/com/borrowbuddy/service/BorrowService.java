package com.borrowbuddy.service;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.BorrowRecordRepository;
import com.borrowbuddy.repository.ItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BorrowService {
    private final ItemRepository items;
    private final BorrowRecordRepository records;

    public BorrowService(ItemRepository items, BorrowRecordRepository records) {
        this.items = items;
        this.records = records;
    }

    // ===================== OVERLOADING: searchItem =====================
    // Search by keyword in the name or description
    public List<Item> searchItem(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return items.findAll(Sort.by("id")).stream()
                .filter(i -> i.getName().toLowerCase().contains(k)
                        || (i.getDescription() != null && i.getDescription().toLowerCase().contains(k)))
                .toList();
    }

    // Search by keyword AND category (POLYMORPHISM: each child Item gives its own getCategory())
    public List<Item> searchItem(String keyword, String category) {
        if (category == null || category.isBlank()) {
            return searchItem(keyword);
        }
        String c = category.trim().toLowerCase();
        return searchItem(keyword).stream()
                .filter(i -> i.getCategory().toLowerCase().startsWith(c))
                .toList();
    }

    // ===================== OVERLOADING: requestBorrow =====================
    // Default period: the item's own maximum (POLYMORPHISM: getMaxBorrowDays() differs per child)
    @Transactional
    public String requestBorrow(Item item, User borrower) {
        return requestBorrow(item, borrower, item.getMaxBorrowDays());
    }

    // Custom number of days
    @Transactional
    public String requestBorrow(Item item, User borrower, int days) {
        if (borrower.isAdmin()) {
            return "Lab staff accounts monitor records and do not borrow items.";
        }
        if (borrower.isSuspended()) {
            return "Your borrowing is suspended by lab staff because of an unreturned item. Please contact the lab office.";
        }
        if (item.isOwnedBy(borrower)) {
            return "You cannot borrow your own item.";
        }
        if (!item.isAvailable()) {
            return item.getName() + " is " + item.getStatus() + " right now.";
        }
        // POLYMORPHISM: Student and OrgOfficer each return their own limit
        if (countOpenBorrows(borrower) >= borrower.getBorrowLimit()) {
            return "You reached your limit of " + borrower.getBorrowLimit()
                    + " items (pending requests count too).";
        }
        if (days < 1 || days > item.getMaxBorrowDays()) {
            return "You can borrow " + item.getName() + " for 1 to " + item.getMaxBorrowDays() + " day(s).";
        }
        BorrowRecord request = new BorrowRecord(item, borrower, days); // item becomes Reserved
        items.save(item);
        records.save(request);
        return "Request sent to " + item.getOwner().getFullName()
                + ". " + item.getName() + " is Reserved until they respond.";
    }

    @Transactional
    public String approveRequest(Long recordId, User owner) {
        BorrowRecord r = records.findById(recordId).orElse(null);
        if (r == null || !r.isLentBy(owner)) {
            return "Only the item owner can approve this request.";
        }
        if (!r.isPending()) {
            return "This request was already handled.";
        }
        r.approve(); // item becomes Borrowed, due date is set
        items.save(r.getItem());
        records.save(r);
        return "Approved. " + r.getBorrower().getFullName() + " must return "
                + r.getItem().getName() + " by " + r.getDueDate() + ".";
    }

    @Transactional
    public String declineRequest(Long recordId, User owner) {
        BorrowRecord r = records.findById(recordId).orElse(null);
        if (r == null || !r.isLentBy(owner)) {
            return "Only the item owner can decline this request.";
        }
        if (!r.isPending()) {
            return "This request was already handled.";
        }
        r.decline(); // item becomes Available again
        items.save(r.getItem());
        records.save(r);
        return "Declined. " + r.getItem().getName() + " is Available again.";
    }

    // Lender: "Received" - the borrower gave the item back.
    @Transactional
    public String markReturned(Long recordId, User owner) {
        BorrowRecord r = records.findById(recordId).orElse(null);
        if (r == null || !r.isLentBy(owner)) {
            return "Only the item owner can confirm the item was received.";
        }
        if (!r.isActive()) {
            return "This item is not currently borrowed.";
        }
        boolean wasOverdue = r.isOverdue();
        r.markReturned(); // return date saved, item becomes Available
        items.save(r.getItem());
        records.save(r);
        return r.getItem().getName() + " received back" + (wasOverdue ? " (late)." : ".");
    }

    // Lender: "Not received" - the due date passed and the item was not returned. Returns the record
    // so the controller can alert lab staff, or null with the reason in the message holder.
    @Transactional
    public BorrowRecord reportNotReceived(Long recordId, User owner, StringBuilder message) {
        BorrowRecord r = records.findById(recordId).orElse(null);
        if (r == null || !r.isLentBy(owner)) {
            message.append("Only the item owner can report an item as not received.");
            return null;
        }
        if (r.isReportedUnreturned()) {
            message.append("You already reported this item. Lab staff are handling it.");
            return null;
        }
        try {
            r.reportNotReceived();
        } catch (IllegalStateException e) {
            message.append(e.getMessage());
            return null;
        }
        records.save(r);
        message.append("Reported to lab staff: ").append(r.getBorrower().getFullName())
               .append(" has not returned ").append(r.getItem().getName()).append(".");
        return r;
    }

    // Lab staff: the unreturned item is back, close the case.
    @Transactional
    public String resolveUnreturned(Long recordId, User admin) {
        if (admin == null || !admin.isAdmin()) {
            return "Only lab staff can resolve unreturned items.";
        }
        BorrowRecord r = records.findById(recordId).orElse(null);
        if (r == null) {
            return "Record not found.";
        }
        try {
            r.resolveByAdmin();
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        items.save(r.getItem());
        records.save(r);
        return "Resolved: " + r.getItem().getName() + " is marked returned and Available again.";
    }

    public BorrowRecord findRecord(Long recordId) {
        return records.findById(recordId).orElse(null);
    }

    public long countOpenBorrows(User borrower) {
        return records.findAll().stream()
                .filter(r -> r.isBorrowedBy(borrower) && (r.isPending() || r.isActive()))
                .count();
    }

    // Requests waiting for this owner's approval
    public List<BorrowRecord> incomingRequests(User owner) {
        return newestFirst().stream()
                .filter(r -> r.isPending() && r.isLentBy(owner))
                .toList();
    }

    // This borrower's requests that have not become lending records
    public List<BorrowRecord> myRequests(User borrower) {
        return newestFirst().stream()
                .filter(r -> r.isBorrowedBy(borrower) && (r.isPending() || r.isDeclined()))
                .toList();
    }

    // Lending records (approved borrows). Lab staff see everyone's; others see their own.
    public List<BorrowRecord> lendingRecords(User viewer, String filter) {
        return newestFirst().stream()
                .filter(r -> r.isActive() || r.isReturned())
                .filter(r -> viewer.isAdmin() || r.isLentBy(viewer) || r.isBorrowedBy(viewer))
                .filter(r -> switch (filter == null ? "all" : filter) {
                    case "active" -> r.isActive() && !r.isOverdue();
                    case "overdue" -> r.isOverdue();
                    case "returned" -> r.isReturned();
                    default -> true;
                })
                .toList();
    }

    public long countOverdue(User viewer) {
        return lendingRecords(viewer, "overdue").size();
    }

    private List<BorrowRecord> newestFirst() {
        return records.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }
}
