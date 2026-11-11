package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE: Calculator IS-A Item. OVERRIDING: 3-day limit and a category that shows the model.
@Entity
@DiscriminatorValue("CALCULATOR")
public class Calculator extends Item {
    private String model;

    protected Calculator() {}

    public Calculator(String name, String model, String description, User owner) {
        super(name, description, owner);
        this.model = (model == null || model.isBlank()) ? null : model.trim();
    }

    public String getModel() { return model; }

    @Override
    public int getMaxBorrowDays() { return 3; }

    @Override
    public String getCategory() {
        return model == null ? "Calculator" : "Calculator (" + model + ")";
    }
}
