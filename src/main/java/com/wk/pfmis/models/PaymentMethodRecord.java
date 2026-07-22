package com.wk.pfmis.models;

public class PaymentMethodRecord {
    private final int id;
    private final String methodName;
    private final String methodType;
    private final String provider;
    private final String defaultAccount;
    private final String status;
    private final String lastUsed;

    public PaymentMethodRecord(
            int id,
            String methodName,
            String methodType,
            String provider,
            String defaultAccount,
            String status,
            String lastUsed
    ) {
        this.id = id;
        this.methodName = methodName;
        this.methodType = methodType;
        this.provider = provider;
        this.defaultAccount = defaultAccount;
        this.status = status;
        this.lastUsed = lastUsed;
    }

    public int getId() {
        return id;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodType() {
        return methodType;
    }

    public String getProvider() {
        return provider;
    }

    public String getDefaultAccount() {
        return defaultAccount;
    }

    public String getStatus() {
        return status;
    }

    public String getLastUsed() {
        return lastUsed;
    }
}
