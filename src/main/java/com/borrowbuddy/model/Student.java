package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE: Student IS-A User. OVERRIDING: its own borrow limit and role.
@Entity
@DiscriminatorValue("STUDENT")
public class Student extends User {
    protected Student() {}

    public Student(String fullName, String ustlAccount, String studentId, String password, Profile profile) {
        super(fullName, ustlAccount, studentId, password, profile);
    }

    @Override
    public int getBorrowLimit() { return 2; }

    @Override
    public String getRole() { return "Student"; }
}
