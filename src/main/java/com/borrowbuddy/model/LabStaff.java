package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE: LabStaff IS-A User. Acts as the optional admin who monitors all records
// and overdue items, so it does not borrow or lend.
@Entity
@DiscriminatorValue("STAFF")
public class LabStaff extends User {
    protected LabStaff() {}

    public LabStaff(String fullName, String ustlAccount, String staffId, String password, Profile profile) {
        super(fullName, ustlAccount, staffId, password, profile);
    }

    @Override
    public boolean isAdmin() { return true; }

    @Override
    public int getBorrowLimit() { return 0; }

    @Override
    public String getRole() { return "Lab Staff (Admin)"; }
}
