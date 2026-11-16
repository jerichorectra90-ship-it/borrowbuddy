package com.borrowbuddy.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// INHERITANCE - OrgOfficer IS-A User, they lend out the org's shared equipment
// OVERRIDING - bigger borrow limit + the role shows which org
@Entity
@DiscriminatorValue("OFFICER")
public class OrgOfficer extends User {
    private String organization;

    protected OrgOfficer() {}

    public OrgOfficer(String fullName, String ustlAccount, String studentId, String password,
                      Profile profile, String organization) {
        super(fullName, ustlAccount, studentId, password, profile);
        setOrganization(organization);
    }

    public String getOrganization() { return organization; }

    public void setOrganization(String organization) {
        if (organization == null || organization.isBlank()) {
            throw new IllegalArgumentException("Organization name is required for org officers.");
        }
        this.organization = organization.trim();
    }

    @Override
    public int getBorrowLimit() { return 5; }

    @Override
    public String getRole() { return "Org Officer, " + organization; }
}
