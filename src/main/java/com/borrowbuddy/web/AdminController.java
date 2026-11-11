package com.borrowbuddy.web;

import com.borrowbuddy.model.BorrowRecord;
import com.borrowbuddy.model.Item;
import com.borrowbuddy.model.User;
import com.borrowbuddy.repository.BorrowRecordRepository;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.repository.UserRepository;
import com.borrowbuddy.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Lab staff (admin) dashboard: system-wide overview of users, items, requests and overdue borrows.
// Only users whose isAdmin() returns true (LabStaff, via OVERRIDING) can open it.
@Controller
public class AdminController {
    private final UserRepository users;
    private final ItemRepository items;
    private final BorrowRecordRepository records;
    private final BorrowService service;
    private final CurrentUser currentUser;

    public AdminController(UserRepository users, ItemRepository items, BorrowRecordRepository records,
                           BorrowService service, CurrentUser currentUser) {
        this.users = users;
        this.items = items;
        this.records = records;
        this.service = service;
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

        model.addAttribute("user", admin);
        model.addAttribute("users", allUsers);
        model.addAttribute("userCount", allUsers.stream().filter(u -> !u.isAdmin()).count());
        model.addAttribute("itemCount", allItems.size());
        model.addAttribute("available", itemsByStatus.getOrDefault(Item.AVAILABLE, 0L));
        model.addAttribute("borrowed", itemsByStatus.getOrDefault(Item.BORROWED, 0L));
        model.addAttribute("reserved", itemsByStatus.getOrDefault(Item.RESERVED, 0L));
        model.addAttribute("pending", allRecords.stream().filter(BorrowRecord::isPending).toList());
        model.addAttribute("overdue", service.lendingRecords(admin, "overdue"));
        model.addAttribute("returnedCount", allRecords.stream().filter(BorrowRecord::isReturned).count());
        model.addAttribute("listedPerUser", listedPerUser);
        model.addAttribute("openPerUser", openPerUser);
        return "admin";
    }
}
