package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE: LabTool IS-A Item. OVERRIDING: 2-day limit.
@Entity
@DiscriminatorValue("LABTOOL")
public class LabTool extends Item {
    protected LabTool() {}

    public LabTool(String name, String description, User owner) {
        super(name, description, owner);
    }

    @Override
    public int getMaxBorrowDays() { return 2; }

    @Override
    public String getCategory() { return "Lab Tool"; }
}
