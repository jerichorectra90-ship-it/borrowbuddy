package com.borrowbuddy.web;

import com.borrowbuddy.model.*;
import com.borrowbuddy.repository.ItemRepository;
import com.borrowbuddy.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

// Features 2-4: item listing, availability + category search, borrow requests
@Controller
public class ItemController {
    private static final List<String> CATEGORIES = List.of("Calculator", "Lab Tool", "Charger");

    private final ItemRepository items;
    private final BorrowService service;
    private final CurrentUser currentUser;

    public ItemController(ItemRepository items, BorrowService service, CurrentUser currentUser) {
        this.items = items;
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/items")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       HttpSession session, Model model) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        model.addAttribute("items", service.searchItem(keyword, category));
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("categories", CATEGORIES);
        return "items";
    }

    @GetMapping("/lend")
    public String lendForm(HttpSession session, Model model) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        if (user.isAdmin()) return "redirect:/admin";
        model.addAttribute("user", user);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("myItems", items.findAll(Sort.by("id")).stream()
                .filter(i -> i.isOwnedBy(user)).toList());
        return "lend";
    }

    @PostMapping("/items/add")
    public String add(@RequestParam String name, @RequestParam String type,
                      @RequestParam(required = false) String model,
                      @RequestParam(required = false) String description,
                      HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        if (user.isAdmin()) {
            flash.addFlashAttribute("message", "Lab staff accounts do not list items.");
            return "redirect:/items";
        }
        try {
            Item item = switch (type) {
                case "Calculator" -> new Calculator(name, model, description, user);
                case "Lab Tool" -> new LabTool(name, description, user);
                case "Charger" -> new Charger(name, description, user);
                default -> throw new IllegalArgumentException("Choose a category.");
            };
            items.save(item);
            flash.addFlashAttribute("message", "Listed " + item.getName() + ". It is now Available.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/lend";
    }

    @PostMapping("/items/request")
    public String request(@RequestParam Long itemId, @RequestParam(required = false) Integer days,
                          HttpSession session, RedirectAttributes flash) {
        User user = currentUser.get(session);
        if (user == null) return "redirect:/";
        Item item = items.findById(itemId).orElse(null);
        if (item == null) {
            flash.addFlashAttribute("message", "Item not found.");
            return "redirect:/items";
        }
        String result = (days == null)
                ? service.requestBorrow(item, user)         // overloaded: default days
                : service.requestBorrow(item, user, days);  // overloaded: custom days
        flash.addFlashAttribute("message", result);
        return "redirect:/items";
    }
}
