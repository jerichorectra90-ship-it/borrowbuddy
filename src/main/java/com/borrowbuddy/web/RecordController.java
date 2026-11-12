package com.borrowbuddy.web;

import com.borrowbuddy.model.BorrowRecord;
import com.borrowbuddy.model.User;
import com.borrowbuddy.service.BorrowService;
import com.borrowbuddy.service.OverdueNotifier;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

// Feature 4: owner approval. Features 5-6: lending records, returns, overdue tracking.
@Controller
public class RecordController {
    private static final List<String> FILTERS = List.of("all", "active", "overdue", "returned");

    private final BorrowService service;
    private final OverdueNotifier notifier;
    private final CurrentUser currentUser;

    public RecordController(BorrowService service, OverdueNotifier notifier, CurrentUser currentUser) {
        this.service = service;
        this.notifier = notifier;
        this.currentUser = currentUser;
    }

    @GetMapping("/requests")
    public String requests(HttpSession session, Model model) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        if (user.isAdmin()) return "redirect:/records";
        model.addAttribute("user", user);
        model.addAttribute("incoming", service.incomingRequests(user));
        model.addAttribute("outgoing", service.myRequests(user));
        return "requests";
    }

    @PostMapping("/requests/approve")
    public String approve(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        flash.addFlashAttribute("message", service.approveRequest(recordId, user));
        return "redirect:/requests";
    }

    @PostMapping("/requests/decline")
    public String decline(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        flash.addFlashAttribute("message", service.declineRequest(recordId, user));
        return "redirect:/requests";
    }

    @GetMapping("/records")
    public String records(@RequestParam(defaultValue = "all") String filter,
                          HttpSession session, Model model) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        String f = FILTERS.contains(filter) ? filter : "all";
        model.addAttribute("user", user);
        model.addAttribute("filter", f);
        model.addAttribute("filters", FILTERS);
        model.addAttribute("records", service.lendingRecords(user, f));
        model.addAttribute("overdueCount", service.countOverdue(user));
        return "records";
    }

    @PostMapping("/records/return")
    public String markReturned(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        flash.addFlashAttribute("message", service.markReturned(recordId, user));
        return "redirect:/records";
    }

    // Lender: the item was not returned by the due date. Escalate to lab staff by email.
    @PostMapping("/records/not-received")
    public String notReceived(@RequestParam Long recordId, HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        StringBuilder message = new StringBuilder();
        BorrowRecord reported = service.reportNotReceived(recordId, user, message);
        if (reported != null) {
            notifier.alertAdminsOfReport(reported);
            message.append(" Lab staff were notified.");
        }
        flash.addFlashAttribute("message", message.toString());
        return "redirect:/records";
    }
}
