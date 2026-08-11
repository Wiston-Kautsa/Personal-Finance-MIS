package com.wk.pfmis.security;

public enum RiskLevel {
    NORMAL,
    SENSITIVE,
    HIGH,
    CRITICAL;

    public boolean covers(RiskLevel requestedLevel) {
        RiskLevel requested = requestedLevel == null ? NORMAL : requestedLevel;
        return ordinal() >= requested.ordinal();
    }
}
