package com.wk.pfmis.models;

public class LoanScheduleRecord {
    private final int id;
    private final Integer personId;
    private final String personName;
    private final String loanDirection;
    private final double principalAmount;
    private final double outstandingAmount;
    private final double interestRate;
    private final double paymentAmount;
    private final String dueDate;
    private final String frequency;
    private final String status;
    private final String notes;

    public LoanScheduleRecord(
            int id,
            Integer personId,
            String personName,
            String loanDirection,
            double principalAmount,
            double outstandingAmount,
            double interestRate,
            double paymentAmount,
            String dueDate,
            String frequency,
            String status,
            String notes
    ) {
        this.id = id;
        this.personId = personId;
        this.personName = personName;
        this.loanDirection = loanDirection;
        this.principalAmount = principalAmount;
        this.outstandingAmount = outstandingAmount;
        this.interestRate = interestRate;
        this.paymentAmount = paymentAmount;
        this.dueDate = dueDate;
        this.frequency = frequency;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public Integer getPersonId() {
        return personId;
    }

    public String getPersonName() {
        return personName;
    }

    public String getLoanDirection() {
        return loanDirection;
    }

    public double getPrincipalAmount() {
        return principalAmount;
    }

    public double getOutstandingAmount() {
        return outstandingAmount;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getPaymentAmount() {
        return paymentAmount;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
