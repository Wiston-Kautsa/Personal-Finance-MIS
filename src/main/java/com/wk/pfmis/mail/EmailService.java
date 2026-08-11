package com.wk.pfmis.mail;

import com.wk.pfmis.models.EmailSettings;
import com.wk.pfmis.services.SystemEmailService;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

public final class EmailService {
    private static final EmailService INSTANCE = new EmailService();

    private final SystemEmailService systemEmailService = SystemEmailService.getInstance();

    private EmailService() {
    }

    public static EmailService getInstance() {
        return INSTANCE;
    }

    public String systemEmailAddress() {
        return cleanAddress(currentSettings().systemEmail());
    }

    public boolean isSendConfigured() {
        return currentSettings().isSendConfigured();
    }

    public boolean isReceiveConfigured() {
        return currentSettings().isReceiveConfigured();
    }

    public String sendConfigurationStatus() {
        EmailSettings settings = currentSettings();
        if (!settings.enabled()) {
            return "Email integration is disabled.";
        }
        if (settings.isSendConfigured()) {
            return "Outgoing email configured as " + settings.systemEmail() + ".";
        }
        if (!settings.smtpSsl() && !settings.smtpStartTls()) {
            return "Outgoing email requires PFMIS_SMTP_SSL=true or PFMIS_SMTP_STARTTLS=true.";
        }
        if (!settings.hasSystemEmail()) {
            return "Outgoing email needs PFMIS_SYSTEM_EMAIL configured.";
        }
        if (settings.smtpAuth() && settings.smtpPassword().isBlank()) {
            return "Outgoing email needs PFMIS_SMTP_PASSWORD configured for SMTP authentication.";
        }
        return "Outgoing email configuration is incomplete.";
    }

    public String receiveConfigurationStatus() {
        EmailSettings settings = currentSettings();
        if (!settings.enabled()) {
            return "Email integration is disabled.";
        }
        if (settings.isReceiveConfigured()) {
            return "Incoming email configured on " + settings.imapHost() + ":" + settings.imapPort() + ".";
        }
        if (!settings.imapSsl()) {
            return "Incoming email requires PFMIS_IMAP_SSL=true.";
        }
        return "Incoming email needs PFMIS_SMTP_PASSWORD configured for mailbox authentication.";
    }

    public void sendPasswordResetEmail(String toAddress, String displayName, String temporaryPassword) {
        EmailSettings settings = currentSettings();
        if (!settings.isSendConfigured()) {
            throw new IllegalStateException(sendConfigurationStatus());
        }
        if (!EmailSettings.isEmailLike(toAddress)) {
            throw new IllegalArgumentException("The selected user does not have a valid email address.");
        }
        String subject = "PFMIS password reset";
        String recipientName = hasText(displayName) ? displayName.trim() : "PFMIS user";
        String body = """
                Hello %s,

                A PFMIS password reset was requested for your account.

                Temporary password: %s

                Sign in with this temporary password, then change it from My Account.

                If you did not request this reset, contact the PFMIS administrator immediately.
                """.formatted(recipientName, temporaryPassword);
        sendPlainText(settings, toAddress, subject, body);
    }

    public void sendTestEmail(EmailSettings settings) {
        EmailSettings effectiveSettings = settings == null ? currentSettings() : settings;
        if (!effectiveSettings.isSendConfigured()) {
            throw new IllegalStateException(statusFor(effectiveSettings));
        }
        String recipient = effectiveSettings.systemEmail();
        String body = """
                PFMIS email configuration test.

                This message confirms that the configured PFMIS System Email can send application messages.
                """;
        sendPlainText(effectiveSettings, recipient, "PFMIS email configuration test", body);
    }

    public void testIncomingMailbox() {
        EmailSettings settings = currentSettings();
        if (!settings.isReceiveConfigured()) {
            throw new IllegalStateException(receiveConfigurationStatus());
        }
        Socket socket = null;
        try {
            socket = openImapSocket(settings);
            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);
            readImapUntilTagged(reader, "*");
            writeLine(writer, "A001 LOGIN " + quoteImap(settings.effectiveSmtpUsername()) + " " + quoteImap(settings.smtpPassword()));
            readImapUntilTagged(reader, "A001");
            writeLine(writer, "A002 SELECT INBOX");
            readImapUntilTagged(reader, "A002");
            writeLine(writer, "A003 LOGOUT");
            readImapUntilTagged(reader, "A003");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to connect to the configured incoming mailbox.", exception);
        } finally {
            closeQuietly(socket);
        }
    }

    private EmailSettings currentSettings() {
        return systemEmailService.currentSettings();
    }

    private String statusFor(EmailSettings settings) {
        if (!settings.enabled()) {
            return "Email integration is disabled.";
        }
        if (!settings.smtpSsl() && !settings.smtpStartTls()) {
            return "Outgoing email requires SSL/TLS or STARTTLS.";
        }
        if (!settings.hasSystemEmail()) {
            return "System Email must be configured.";
        }
        if (settings.smtpAuth() && settings.smtpPassword().isBlank()) {
            return "SMTP/App Password still requires configuration.";
        }
        return "PFMIS could not send the test email. Check the email configuration.";
    }

    private void sendPlainText(EmailSettings settings, String toAddress, String subject, String body) {
        if (!settings.smtpSsl() && !settings.smtpStartTls()) {
            throw new IllegalStateException("SMTP authentication requires SSL/TLS or STARTTLS.");
        }
        Socket socket = null;
        try {
            socket = openSmtpSocket(settings);
            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);

            expect(readSmtpResponse(reader), 220);
            sendSmtpCommand(writer, reader, "EHLO pfmis.local", 250);
            if (settings.smtpStartTls() && !settings.smtpSsl()) {
                sendSmtpCommand(writer, reader, "STARTTLS", 220);
                socket = upgradeToTls(settings, socket);
                reader = reader(socket);
                writer = writer(socket);
                sendSmtpCommand(writer, reader, "EHLO pfmis.local", 250);
            }
            if (settings.smtpAuth()) {
                authenticateSmtp(settings, writer, reader);
            }
            sendSmtpCommand(writer, reader, "MAIL FROM:<" + cleanAddress(settings.systemEmail()) + ">", 250);
            sendSmtpCommand(writer, reader, "RCPT TO:<" + cleanAddress(toAddress) + ">", 250, 251);
            sendSmtpCommand(writer, reader, "DATA", 354);
            writer.write(dotStuff(buildMessage(settings, toAddress, subject, body)));
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(readSmtpResponse(reader), 250);
            sendSmtpCommand(writer, reader, "QUIT", 221);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send email from the configured PFMIS System Email.", exception);
        } finally {
            closeQuietly(socket);
        }
    }

    private Socket openSmtpSocket(EmailSettings settings) throws IOException {
        Socket socket = settings.smtpSsl()
                ? tlsSocket(settings.smtpHost(), settings.smtpPort(), settings)
                : plainSocket(settings.smtpHost(), settings.smtpPort(), settings);
        if (socket instanceof SSLSocket sslSocket) {
            sslSocket.startHandshake();
        }
        return socket;
    }

    private Socket openImapSocket(EmailSettings settings) throws IOException {
        if (!settings.imapSsl()) {
            throw new IllegalStateException("IMAP authentication requires SSL/TLS.");
        }
        SSLSocket socket = tlsSocket(settings.imapHost(), settings.imapPort(), settings);
        socket.startHandshake();
        return socket;
    }

    private Socket plainSocket(String host, int port, EmailSettings settings) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) settings.connectTimeout().toMillis());
        socket.setSoTimeout((int) settings.readTimeout().toMillis());
        return socket;
    }

    private SSLSocket tlsSocket(String host, int port, EmailSettings settings) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new InetSocketAddress(host, port), (int) settings.connectTimeout().toMillis());
        socket.setSoTimeout((int) settings.readTimeout().toMillis());
        configureEndpointVerification(socket);
        return socket;
    }

    private Socket upgradeToTls(EmailSettings settings, Socket plainSocket) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket tlsSocket = factory.createSocket(plainSocket, settings.smtpHost(), settings.smtpPort(), true);
        tlsSocket.setSoTimeout((int) settings.readTimeout().toMillis());
        if (tlsSocket instanceof SSLSocket sslSocket) {
            configureEndpointVerification(sslSocket);
            sslSocket.startHandshake();
        }
        return tlsSocket;
    }

    private void configureEndpointVerification(SSLSocket socket) {
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(parameters);
    }

    private void authenticateSmtp(EmailSettings settings, BufferedWriter writer, BufferedReader reader) throws IOException {
        String token = "\0" + settings.effectiveSmtpUsername() + "\0" + settings.smtpPassword();
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        sendSmtpCommand(writer, reader, "AUTH PLAIN " + encoded, 235);
    }

    private String buildMessage(EmailSettings settings, String toAddress, String subject, String body) {
        return "From: " + cleanHeader(settings.fromName()) + " <" + cleanAddress(settings.systemEmail()) + ">\r\n"
                + "Reply-To: " + cleanHeader(settings.effectiveReplyTo()) + "\r\n"
                + "To: " + cleanHeader(toAddress) + "\r\n"
                + "Subject: " + cleanHeader(subject) + "\r\n"
                + "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n"
                + "\r\n"
                + normalizeLines(body);
    }

    private void sendSmtpCommand(BufferedWriter writer, BufferedReader reader, String command, int... expectedCodes) throws IOException {
        writeLine(writer, command);
        expect(readSmtpResponse(reader), expectedCodes);
    }

    private SmtpResponse readSmtpResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("SMTP server closed the connection.");
        }
        StringBuilder message = new StringBuilder(line);
        int code = smtpCode(line);
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed the connection.");
            }
            message.append('\n').append(line);
        }
        return new SmtpResponse(code, message.toString());
    }

    private int smtpCode(String line) throws IOException {
        if (line.length() < 3) {
            throw new IOException("Invalid SMTP response: " + line);
        }
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid SMTP response: " + line, exception);
        }
    }

    private void expect(SmtpResponse response, int... expectedCodes) throws IOException {
        for (int expectedCode : expectedCodes) {
            if (response.code() == expectedCode) {
                return;
            }
        }
        if (response.code() == 534 || response.code() == 535) {
            throw new IOException(authenticationFailureMessage(response));
        }
        throw new IOException("Unexpected SMTP response. Expected "
                + Arrays.toString(expectedCodes)
                + " but got " + response.code()
                + ": " + response.message());
    }

    private String authenticationFailureMessage(SmtpResponse response) {
        String message = "The SMTP server rejected the configured email username/password.";
        message += " For Gmail, set PFMIS_SMTP_PASSWORD to a Gmail App Password, not a PFMIS login password.";
        return message + " Server response: " + response.message();
    }

    private void readImapUntilTagged(BufferedReader reader, String tag) throws IOException {
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("IMAP server closed the connection.");
            }
            if ("*".equals(tag) || line.startsWith(tag + " ")) {
                String upper = line.toUpperCase(Locale.ENGLISH);
                if (upper.contains(" NO ") || upper.contains(" BAD ")) {
                    throw new IOException("Unexpected IMAP response: " + line);
                }
                return;
            }
        }
    }

    private BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    private String cleanAddress(String value) {
        String clean = cleanHeader(value).trim();
        int start = clean.indexOf('<');
        int end = clean.indexOf('>');
        if (start >= 0 && end > start) {
            return clean.substring(start + 1, end).trim();
        }
        return clean;
    }

    private String cleanHeader(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String normalizeLines(String value) {
        return (value == null ? "" : value).replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
    }

    private String dotStuff(String message) {
        String normalized = normalizeLines(message);
        if (normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        return normalized.replace("\r\n.", "\r\n..");
    }

    private String quoteImap(String value) {
        return "\"" + cleanHeader(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Preserve the original mail failure.
        }
    }

    private record SmtpResponse(int code, String message) {
    }
}
