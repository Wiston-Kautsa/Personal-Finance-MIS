package com.wk.pfmis.models;

import java.time.Duration;

public record EmailSettings(
        boolean enabled,
        String systemEmail,
        String fromName,
        String replyTo,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        boolean smtpAuth,
        boolean smtpStartTls,
        boolean smtpSsl,
        String imapHost,
        int imapPort,
        boolean imapSsl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration writeTimeout
) {
    public EmailSettings {
        systemEmail = clean(systemEmail);
        fromName = clean(fromName).isBlank() ? "PFMIS" : clean(fromName);
        replyTo = clean(replyTo);
        smtpHost = clean(smtpHost);
        smtpUsername = clean(smtpUsername);
        smtpPassword = smtpPassword == null ? "" : smtpPassword;
        imapHost = clean(imapHost);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(15) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        writeTimeout = writeTimeout == null ? Duration.ofSeconds(30) : writeTimeout;
    }

    public boolean hasSystemEmail() {
        return isEmailLike(systemEmail);
    }

    public String effectiveReplyTo() {
        return isEmailLike(replyTo) ? replyTo : systemEmail;
    }

    public String effectiveSmtpUsername() {
        return smtpUsername.isBlank() ? systemEmail : smtpUsername;
    }

    public boolean isSendConfigured() {
        return enabled
                && hasSystemEmail()
                && !smtpHost.isBlank()
                && smtpPort > 0
                && (smtpSsl || smtpStartTls)
                && (!smtpAuth || (!effectiveSmtpUsername().isBlank() && !smtpPassword.isBlank()));
    }

    public boolean isReceiveConfigured() {
        return enabled
                && !imapHost.isBlank()
                && imapPort > 0
                && imapSsl
                && !effectiveSmtpUsername().isBlank()
                && !smtpPassword.isBlank();
    }

    public EmailSettings withoutPassword() {
        return new EmailSettings(
                enabled,
                systemEmail,
                fromName,
                replyTo,
                smtpHost,
                smtpPort,
                smtpUsername,
                "",
                smtpAuth,
                smtpStartTls,
                smtpSsl,
                imapHost,
                imapPort,
                imapSsl,
                connectTimeout,
                readTimeout,
                writeTimeout
        );
    }

    public static boolean isEmailLike(String value) {
        String clean = clean(value);
        int at = clean.indexOf('@');
        return at > 0 && at < clean.length() - 1 && clean.indexOf('@', at + 1) < 0;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
