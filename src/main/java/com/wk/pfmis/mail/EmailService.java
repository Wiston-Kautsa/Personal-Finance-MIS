package com.wk.pfmis.mail;

import com.wk.pfmis.config.AppConfig;

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
    private static final int SOCKET_TIMEOUT_MILLIS = 20_000;

    private final String smtpHost;
    private final int smtpPort;
    private final boolean smtpStartTls;
    private final boolean smtpSsl;
    private final String imapHost;
    private final int imapPort;
    private final boolean imapSsl;
    private final String fromAddress;
    private final String replyToAddress;
    private final String username;
    private final String password;

    private EmailService() {
        smtpHost = AppConfig.get("PFMIS_SMTP_HOST", "smtp.gmail.com");
        smtpPort = AppConfig.getInt("PFMIS_SMTP_PORT", 587);
        smtpStartTls = AppConfig.getBoolean("PFMIS_SMTP_STARTTLS", true);
        smtpSsl = AppConfig.getBoolean("PFMIS_SMTP_SSL", false);
        imapHost = AppConfig.get("PFMIS_IMAP_HOST", "imap.gmail.com");
        imapPort = AppConfig.getInt("PFMIS_IMAP_PORT", 993);
        imapSsl = AppConfig.getBoolean("PFMIS_IMAP_SSL", true);
        fromAddress = AppConfig.get("PFMIS_MAIL_FROM", "");
        replyToAddress = AppConfig.get("PFMIS_MAIL_REPLY_TO", fromAddress);
        username = AppConfig.get("PFMIS_MAIL_USERNAME", fromAddress);
        password = AppConfig.get("PFMIS_MAIL_PASSWORD", "");
    }

    public static EmailService getInstance() {
        return INSTANCE;
    }

    public String systemEmailAddress() {
        return cleanAddress(fromAddress);
    }

    public boolean isSendConfigured() {
        return hasText(smtpHost)
                && smtpPort > 0
                && (smtpSsl || smtpStartTls)
                && isEmailLike(fromAddress)
                && hasText(username)
                && hasText(password);
    }

    public boolean isReceiveConfigured() {
        return hasText(imapHost)
                && imapPort > 0
                && imapSsl
                && hasText(username)
                && hasText(password);
    }

    public String sendConfigurationStatus() {
        if (isSendConfigured()) {
            return "Outgoing email configured as " + systemEmailAddress() + ".";
        }
        if (!smtpSsl && !smtpStartTls) {
            return "Outgoing email requires PFMIS_SMTP_SSL=true or PFMIS_SMTP_STARTTLS=true.";
        }
        return "Outgoing email needs PFMIS_MAIL_PASSWORD in .env for " + systemEmailAddress() + ".";
    }

    public String receiveConfigurationStatus() {
        if (isReceiveConfigured()) {
            return "Incoming email configured on " + imapHost + ":" + imapPort + ".";
        }
        if (!imapSsl) {
            return "Incoming email requires PFMIS_IMAP_SSL=true.";
        }
        return "Incoming email needs PFMIS_MAIL_PASSWORD in .env.";
    }

    public void sendPasswordResetEmail(String toAddress, String displayName, String temporaryPassword) {
        if (!isSendConfigured()) {
            throw new IllegalStateException(sendConfigurationStatus());
        }
        if (!isEmailLike(toAddress)) {
            throw new IllegalArgumentException("The selected user does not have a valid email address.");
        }
        String subject = "PFMIS password reset";
        String recipientName = hasText(displayName) ? displayName.trim() : "PFMIS user";
        String body = """
                Hello %s,

                A PFMIS password reset was requested for your account.

                Temporary password: %s

                Sign in with this temporary password, then change it from My Account.

                If you did not request this reset, contact the Super Administrator immediately.
                """.formatted(recipientName, temporaryPassword);
        sendPlainText(toAddress, subject, body);
    }

    public void testIncomingMailbox() {
        if (!isReceiveConfigured()) {
            throw new IllegalStateException(receiveConfigurationStatus());
        }
        Socket socket = null;
        try {
            socket = openImapSocket();
            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);
            readImapUntilTagged(reader, "*");
            writeLine(writer, "A001 LOGIN " + quoteImap(username) + " " + quoteImap(password));
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

    private void sendPlainText(String toAddress, String subject, String body) {
        if (!smtpSsl && !smtpStartTls) {
            throw new IllegalStateException("SMTP authentication requires SSL/TLS or STARTTLS.");
        }
        Socket socket = null;
        try {
            socket = openSmtpSocket();
            BufferedReader reader = reader(socket);
            BufferedWriter writer = writer(socket);

            expect(readSmtpResponse(reader), 220);
            sendSmtpCommand(writer, reader, "EHLO pfmis.local", 250);
            if (smtpStartTls && !smtpSsl) {
                sendSmtpCommand(writer, reader, "STARTTLS", 220);
                socket = upgradeToTls(socket);
                reader = reader(socket);
                writer = writer(socket);
                sendSmtpCommand(writer, reader, "EHLO pfmis.local", 250);
            }
            authenticateSmtp(writer, reader);
            sendSmtpCommand(writer, reader, "MAIL FROM:<" + cleanAddress(fromAddress) + ">", 250);
            sendSmtpCommand(writer, reader, "RCPT TO:<" + cleanAddress(toAddress) + ">", 250, 251);
            sendSmtpCommand(writer, reader, "DATA", 354);
            writer.write(dotStuff(buildMessage(toAddress, subject, body)));
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(readSmtpResponse(reader), 250);
            sendSmtpCommand(writer, reader, "QUIT", 221);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send the password reset email.", exception);
        } finally {
            closeQuietly(socket);
        }
    }

    private Socket openSmtpSocket() throws IOException {
        Socket socket = smtpSsl ? tlsSocket(smtpHost, smtpPort) : plainSocket(smtpHost, smtpPort);
        if (socket instanceof SSLSocket sslSocket) {
            sslSocket.startHandshake();
        }
        return socket;
    }

    private Socket openImapSocket() throws IOException {
        if (!imapSsl) {
            throw new IllegalStateException("IMAP authentication requires SSL/TLS.");
        }
        SSLSocket socket = tlsSocket(imapHost, imapPort);
        socket.startHandshake();
        return socket;
    }

    private Socket plainSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLIS);
        socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        return socket;
    }

    private SSLSocket tlsSocket(String host, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLIS);
        socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        configureEndpointVerification(socket);
        return socket;
    }

    private Socket upgradeToTls(Socket plainSocket) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket tlsSocket = factory.createSocket(plainSocket, smtpHost, smtpPort, true);
        tlsSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
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

    private void authenticateSmtp(BufferedWriter writer, BufferedReader reader) throws IOException {
        String token = "\0" + username + "\0" + password;
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        sendSmtpCommand(writer, reader, "AUTH PLAIN " + encoded, 235);
    }

    private String buildMessage(String toAddress, String subject, String body) {
        return "From: PFMIS <" + cleanAddress(fromAddress) + ">\r\n"
                + "Reply-To: " + cleanHeader(replyToAddress) + "\r\n"
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
        if (smtpHost.toLowerCase(Locale.ENGLISH).contains("gmail")) {
            message += " For Gmail, set PFMIS_MAIL_PASSWORD in .env to a Google App Password, not the PFMIS password or the normal Google account password.";
        }
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

    private boolean isEmailLike(String value) {
        String clean = cleanAddress(value);
        return clean.contains("@") && !clean.startsWith("@") && !clean.endsWith("@");
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
