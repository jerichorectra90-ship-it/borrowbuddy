package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE - LabStaff IS-A User. basically the admin, just watches all the records
// and overdue stuff, so no borrowing or lending for them
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
