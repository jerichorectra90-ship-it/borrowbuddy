package com.borrowbuddy.web;

import com.borrowbuddy.model.BorrowRecord;
import com.borrowbuddy.model.Item;
import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.BorrowRecordRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.NotificationRepository;
import com.borrowbuddy.repository.UserRepository;
import com.borrowbuddy.service.BorrowService;
import com.borrowbuddy.service.EmailService;
import com.borrowbuddy.service.OverdueNotifier;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Lab staff (admin) dashboard. Only users whose isAdmin() returns true (LabStaff, via OVERRIDING)
// can open it. Lab staff are the only ones who manage borrowers who did not return items:
// email reminders, suspending / reinstating borrowing, and resolving unreturned cases.
@Controller
public class AdminController {
    private static final List<String> TABS = List.of("unreturned", "pending", "users", "emails");

    private final UserRepository users;
    private final ItemRepository items;
    private final BorrowRecordRepository records;
    private final NotificationRepository notifications;
    private final BorrowService service;
    private final OverdueNotifier notifier;
    private final EmailService email;
    private final CurrentUser currentUser;

    public AdminController(UserRepository users, ItemRepository items, BorrowRecordRepository records,
                           NotificationRepository notifications, BorrowService service,
                           OverdueNotifier notifier, EmailService email, CurrentUser currentUser) {
        this.users = users;
        this.items = items;
        this.records = records;
        this.notifications = notifications;
        this.service = service;
        this.notifier = notifier;
        this.email = email;
        this.currentUser = currentUser;
    }

    @GetMapping("/admin")
    public String dashboard(HttpSession session, Model model) {
        User admin = currentUser.get(session);
        if (admin == null) return "redirect:/";
        if (!admin.isAdmin()) return "redirect:/items";

        List<User> allUsers = users.findAll(Sort.by("id"));
        List<Item> allItems = items.findAll(Sort.by("id"));
        List<BorrowRecord> allRecords = records.findAll(Sort.by(Sort.Direction.DESC, "id"));

        Map<String, Long> itemsByStatus = allItems.stream()
                .collect(Collectors.groupingBy(Item::getStatus, Collectors.counting()));
        Map<Long, Long> listedPerUser = allItems.stream()
                .collect(Collectors.groupingBy(i -> i.getOwner().getId(), Collectors.counting()));
        Map<Long, Long> openPerUser = allUsers.stream()
                .collect(Collectors.toMap(User::getId, service::countOpenBorrows));
        Map<Long, Long> overduePerUser = allRecords.stream().filter(BorrowRecord::isOverdue)
                .collect(Collectors.groupingBy(r -> r.getBorrower().getId(), Collectors.counting()));

        model.addAttribute("user", admin);
        model.addAttribute("users", allUsers);
        model.addAttribute("userCount", allUsers.stream().filter(u -> !u.isAdmin()).count());
        model.addAttribute("suspendedCount", allUsers.stream().filter(User::isSuspended).count());
        model.addAttribute("itemCount", allItems.size());
        model.addAttribute("available", itemsByStatus.getOrDefault(Item.AVAILABLE, 0L));
        model.addAttribute("borrowed", itemsByStatus.getOrDefault(Item.BORROWED, 0L));
        model.addAttribute("reserved", itemsByStatus.getOrDefault(Item.RESERVED, 0L));
        model.addAttribute("pending", allRecords.stream().filter(BorrowRecord::isPending).toList());
        model.addAttribute("overdue", service.lendingRecords(admin, "overdue"));
        model.addAttribute("returnedCount", allRecords.stream().filter(BorrowRecord::isReturned).count());
        model.addAttribute("listedPerUser", listedPerUser);
        model.addAttribute("openPerUser", openPerUser);
        model.addAttribute("overduePerUser", overduePerUser);
        model.addAttribute("notifications", notifications.findTop15ByOrderByIdDesc());
        model.addAttribute("emailReady", email.isConfigured());
        model.addAttribute("emailRedirect", email.getRedirectTo());
        return "admin";
    }

    @PostMapping("/admin/remind")
    public String remind(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User admin = currentUser.get(session);
        if (admin == null || !admin.isAdmin()) return "redirect:/";
        BorrowRecord r = service.findRecord(recordId);
        flash.addFlashAttribute("message", r == null ? "Record not found." : notifier.remindBorrower(r));
        return "redirect:/admin#unreturned";
    }

    @PostMapping("/admin/resolve")
    public String resolve(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User admin = currentUser.get(session);
        if (admin == null || !admin.isAdmin()) return "redirect:/";
        flash.addFlashAttribute("message", service.resolveUnreturned(recordId, admin));
        return "redirect:/admin#unreturned";
    }

    @PostMapping("/admin/suspend")
    public String suspend(@RequestParam Long userId, @RequestParam(defaultValue = "users") String tab,
                          HttpSession session, RedirectAttributes flash) {
        return changeSuspension(userId, true, tab, session, flash);
    }

    @PostMapping("/admin/reinstate")
    public String reinstate(@RequestParam Long userId, @RequestParam(defaultValue = "users") String tab,
                            HttpSession session, RedirectAttributes flash) {
        return changeSuspension(userId, false, tab, session, flash);
    }

    @PostMapping("/admin/run-check")
    public String runCheck(HttpSession session, RedirectAttributes flash) {
        User admin = currentUser.get(session);
        if (admin == null || !admin.isAdmin()) return "redirect:/";
        flash.addFlashAttribute("message", notifier.runDailyCheck());
        return "redirect:/admin#emails";
    }

    private String changeSuspension(Long userId, boolean suspend, String tab, HttpSession session, RedirectAttributes flash) {
        String back = "redirect:/admin#" + (TABS.contains(tab) ? tab : "users");
        User admin = currentUser.get(session);
        if (admin == null || !admin.isAdmin()) return "redirect:/";
        User target = users.findById(userId).orElse(null);
        if (target == null) {
            flash.addFlashAttribute("message", "User not found.");
            return back;
        }
        try {
            if (suspend) target.suspend(admin); else target.reinstate(admin);
            users.save(target);
            flash.addFlashAttribute("message", target.getFullName()
                    + (suspend ? " is suspended and cannot request items until reinstated."
                               : " can borrow again."));
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("message", e.getMessage());
        }
        return back;
    }
}
