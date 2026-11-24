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

    @SuppressWarnings("OverridableMethodCallInConstructor") // setters validate input; they stay non-final for Hibernate proxies

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
        String org = organization.trim().replaceAll("\\s+", " ");
        if (org.length() < 2 || org.length() > 60 || !org.matches("^[\\p{L}0-9][\\p{L}0-9 .&'()-]*$")
                || org.replaceAll("[^\\p{L}]", "").length() < 2) {
            throw new IllegalArgumentException("Organization name should be letters/numbers only, e.g. Computer Society.");
        }
        this.organization = org;
    }

    @Override
    public int getBorrowLimit() { return 5; }

    @Override
    public String getRole() { return "Org Officer, " + organization; }
}
