package com.wk.pfmis.models;

public class HouseholdMonthMember {
    private final int id;
    private final String budgetMonth;
    private final String personName;
    private final String relationship;
    private final String presenceStatus;
    private final String joinedDate;
    private final String leftDate;
    private final double shareWeight;
    private final String memberType;
    private final String durationScope;
    private final String notes;

    public HouseholdMonthMember(
            int id,
            String budgetMonth,
            String personName,
            String relationship,
            String presenceStatus,
            String joinedDate,
            String leftDate,
            double shareWeight,
            String notes
    ) {
        this(id, budgetMonth, personName, relationship, presenceStatus, joinedDate, leftDate, shareWeight, "MEMBER", "MONTH_ONLY", notes);
    }

    public HouseholdMonthMember(
            int id,
            String budgetMonth,
            String personName,
            String relationship,
            String presenceStatus,
            String joinedDate,
            String leftDate,
            double shareWeight,
            String memberType,
            String durationScope,
            String notes
    ) {
        this.id = id;
        this.budgetMonth = budgetMonth;
        this.personName = personName;
        this.relationship = relationship;
        this.presenceStatus = presenceStatus;
        this.joinedDate = joinedDate;
        this.leftDate = leftDate;
        this.shareWeight = shareWeight;
        this.memberType = memberType == null || memberType.isBlank() ? "MEMBER" : memberType;
        this.durationScope = durationScope == null || durationScope.isBlank() ? "MONTH_ONLY" : durationScope;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getBudgetMonth() {
        return budgetMonth;
    }

    public String getPersonName() {
        return personName;
    }

    public String getRelationship() {
        return relationship;
    }

    public String getPresenceStatus() {
        return presenceStatus;
    }

    public String getJoinedDate() {
        return joinedDate;
    }

    public String getLeftDate() {
        return leftDate;
    }

    public double getShareWeight() {
        return shareWeight;
    }

    public String getMemberType() {
        return memberType;
    }

    public String getDurationScope() {
        return durationScope;
    }

    public boolean isBudgetOwner() {
        return "OWNER".equalsIgnoreCase(memberType);
    }

    public boolean isOngoing() {
        return "FOREVER".equalsIgnoreCase(durationScope);
    }

    public String getNotes() {
        return notes;
    }
}
