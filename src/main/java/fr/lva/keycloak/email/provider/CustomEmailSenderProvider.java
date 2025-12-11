package fr.lva.keycloak.email.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.email.DefaultEmailSenderProvider;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class CustomEmailSenderProvider implements EmailSenderProvider {

    private static final Logger LOG = Logger.getLogger(CustomEmailSenderProvider.class);

    private final KeycloakSession session;
    private final DefaultEmailSenderProvider defaultSender;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CustomEmailSenderProvider(KeycloakSession session) {
        this.session = session;
        this.defaultSender = new DefaultEmailSenderProvider(session);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void send(Map<String, String> config,
                     UserModel user,
                     String subject,
                     String textBody,
                     String htmlBody) throws EmailException {

        if (shouldUseBrevoForCurrentClient()) {
            LOG.debugf("Using Brevo API for user %s, subject=%s", user.getEmail(), subject);
            try {
                sendViaBrevo(config, user, subject, textBody, htmlBody);
            } catch (Exception e) {
                LOG.error("Failed to send email via Brevo, falling back to default SMTP", e);
                // fallback SMTP Keycloak
                defaultSender.send(config, user, subject, textBody, htmlBody);
            }
        } else {
            defaultSender.send(config, user, subject, textBody, htmlBody);
        }
    }

    @Override
    public void send(Map<String, String> config,
                     String address,
                     String subject,
                     String textBody,
                     String htmlBody) throws EmailException {
        defaultSender.send(config, address, subject, textBody, htmlBody);
    }

    @Override
    public void close() {
        // Nothing to do
    }

    /**
     * Check if the client use the SSO mire
     * @return true if the client as the theme attribute configured
     */
    private boolean shouldUseBrevoForCurrentClient() {
        KeycloakContext context = session.getContext();
        ClientModel client = context.getClient();
        if (client == null) {
            return false;
        }

        String loginTheme = client.getAttribute("login_theme");

        return "theme-hachette".equals(loginTheme);
    }

    private void sendViaBrevo(Map<String, String> config,
                              UserModel user,
                              String subject,
                              String textBody,
                              String htmlBody) throws IOException, InterruptedException, EmailException {

        String brevoApiKey    = "api-key";
        String brevoSender    = "no-reply@kiosque-edu.com";  // ex: no-reply@hachette.fr
        String brevoSenderName= "Hachette Livre";
        //String templateIdStr  = getRequired(config, "brevo.template-id");   // ex: "42"
        //int templateId        = Integer.parseInt(templateIdStr);

        Map<String, Object> body = buildBrevoPayload(
                // templateId,
                brevoSender,
                brevoSenderName,
                user,
                subject,
                textBody,
                htmlBody
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .header("api-key", brevoApiKey)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new EmailException("Brevo API call failed with status " + status);
        }
    }

    private Map<String, Object> buildBrevoPayload(//int templateId,
                                                  String senderEmail,
                                                  String senderName,
                                                  UserModel user,
                                                  String subject,
                                                  String textBody,
                                                  String htmlBody) {

        Map<String, Object> root = new HashMap<>();

        // sender
        Map<String, Object> sender = new HashMap<>();
        sender.put("email", senderEmail);
        sender.put("name", senderName);
        root.put("sender", sender);

        // destinataire
        Map<String, Object> toRecipient = new HashMap<>();
        toRecipient.put("email", user.getEmail());
        //toRecipient.put("name", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        root.put("to", java.util.List.of(toRecipient));

        // template
        //root.put("templateId", templateId);

        // subject optionnel (peut être overridé par le template)
        root.put("subject", subject);
        root.put("htmlContent", textBody);

        // params = variables utilisables dans le template, ex {{ params.firstName }}
       /* Map<String, Object> params = new HashMap<>();
        params.put("username", user.getUsername());
        params.put("firstName", user.getFirstName());
        params.put("lastName", user.getLastName());
        params.put("email", user.getEmail());
        params.put("kcSubject", subject);
        params.put("kcTextBody", textBody);
        params.put("kcHtmlBody", htmlBody);

        root.put("params", params);*/

        return root;
    }

}
