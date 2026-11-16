package com.borrowbuddy.service;

import com.borrowbuddy.model.Notification;
import com.borrowbuddy.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// sends the emails thru Brevo's HTTP API. Render free plan blocks the normal SMTP ports
// so we had to go w/ an HTTPS email API instead. every try gets logged in the notifications table
//
// env vars it needs:
//   BREVO_API_KEY    - api key from Brevo (SMTP & API > API keys)
//   MAIL_FROM        - sender address, has to be verified in Brevo
//   ADMIN_EMAIL      - where the lab staff alerts go (if empty it uses the lab staff accounts' emails)
//   MAIL_REDIRECT_TO - optional, sends ALL emails to this one inbox (handy for demos w/ the sample accounts)
@Service
public class EmailService {
    private final NotificationRepository log;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String apiKey;
    private final String from;
    private final String adminEmail;
    private final String redirectTo;
    private final String apiUrl;

    public EmailService(NotificationRepository log,
                        @Value("${BREVO_API_KEY:}") String apiKey,
                        @Value("${MAIL_FROM:}") String from,
                        @Value("${ADMIN_EMAIL:}") String adminEmail,
                        @Value("${MAIL_REDIRECT_TO:}") String redirectTo,
                        @Value("${BREVO_API_URL:https://api.brevo.com/v3/smtp/email}") String apiUrl) {
        this.log = log;
        this.apiKey = apiKey.trim();
        this.from = from.trim();
        this.adminEmail = adminEmail.trim();
        this.redirectTo = redirectTo.trim();
        this.apiUrl = apiUrl.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    public String getAdminEmail() { return adminEmail; }

    public String getRedirectTo() { return redirectTo; }

    // true = Brevo took the email
    public boolean send(String to, String toName, String subject, String html) {
        String actualTo = redirectTo.isEmpty() ? to : redirectTo;
        String actualSubject = redirectTo.isEmpty() ? subject : subject + " [for " + to + "]";

        if (!isConfigured()) {
            log.save(new Notification(to, subject, Notification.NOT_CONFIGURED,
                    "Email is not set up yet (BREVO_API_KEY / MAIL_FROM missing)."));
            return false;
        }
        try {
            String body = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", "BorrowBuddy", "email", from),
                    "to", List.of(Map.of("email", actualTo, "name", toName == null ? actualTo : toName)),
                    "subject", actualSubject,
                    "htmlContent", html));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() / 100 == 2;
            String detail = redirectTo.isEmpty() ? null : "Redirected to " + redirectTo;
            log.save(new Notification(to, subject, ok ? Notification.SENT : Notification.FAILED,
                    ok ? detail : "Brevo error " + response.statusCode() + ": " + response.body()));
            return ok;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.save(new Notification(to, subject, Notification.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage()));
            return false;
        }
    }

    // escape whatever the user typed before it goes in the html email
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
