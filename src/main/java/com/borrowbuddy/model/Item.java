package com.borrowbuddy.model;

import jakarta.persistence.*;

// INHERITANCE - parent class of Calculator, LabTool, Charger
// ENCAPSULATION - only BorrowRecord can change the status (the setter is package-private),
// that way the availability never goes out of sync w/ the borrow records
@Entity
@Table(name = "items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "item_type")
public abstract class Item {
    public static final String AVAILABLE = "Available";
    public static final String RESERVED = "Reserved";
    public static final String BORROWED = "Borrowed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 500)
    private String description;

    private String status;

    @ManyToOne(optional = false)
    private User owner; // COMPOSITION - Item HAS-A owner

    protected Item() {}

    @SuppressWarnings("OverridableMethodCallInConstructor") // setters validate input; they stay non-final for Hibernate proxies

    protected Item(String name, String description, User owner) {
        setName(name);
        setDescription(description);
        if (owner == null) {
            throw new IllegalArgumentException("Item owner is required.");
        }
        this.owner = owner;
        this.status = AVAILABLE;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public User getOwner() { return owner; }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        this.name = name.trim();
    }

    public void setDescription(String description) {
        String d = description == null ? "" : description.trim();
        if (d.length() > 500) {
            throw new IllegalArgumentException("Description must be 500 characters or fewer.");
        }
        this.description = d.isEmpty() ? null : d;
    }

    void setStatus(String status) {
        if (!AVAILABLE.equals(status) && !RESERVED.equals(status) && !BORROWED.equals(status)) {
            throw new IllegalArgumentException("Status must be Available, Reserved, or Borrowed.");
        }
        this.status = status;
    }

    public boolean isAvailable() { return AVAILABLE.equals(status); }

    public boolean isOwnedBy(User user) { return owner.isSameUser(user); }

    // child classes OVERRIDE this part
    public abstract int getMaxBorrowDays();

    public abstract String getCategory();
}
