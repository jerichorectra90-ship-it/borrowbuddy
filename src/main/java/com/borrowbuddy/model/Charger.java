package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE: Charger IS-A Item. OVERRIDING: 1-day limit, since chargers are needed back fast.
@Entity
@DiscriminatorValue("CHARGER")
public class Charger extends Item {
    protected Charger() {}

    public Charger(String name, String description, User owner) {
        super(name, description, owner);
    }

    @Override
    public int getMaxBorrowDays() { return 1; }

    @Override
    public String getCategory() { return "Charger"; }
}
