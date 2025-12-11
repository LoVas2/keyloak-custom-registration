package fr.lva.keycloak.email.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.email.EmailException;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
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

/**
 * Custom Freemarker provider to send email
 */
public class CustomFreeMarkerEmailTemplateProvider extends FreeMarkerEmailTemplateProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CustomFreeMarkerEmailTemplateProvider(KeycloakSession session) {
        super(session);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void sendPasswordReset(String link, long expirationInMinutes) throws EmailException {
        try {
            sendViaBrevo("Réinitialiser le mot de passe", """
                    Quelqu'un vient de demander une réinitialisation de mot de passe pour votre compte Hachette Livre.
                    <br/>Si vous êtes à l'origine de cette requête, veuillez cliquer sur le lien ci-dessous pour le mettre à jour : <br/>
                    """ + link);
        } catch (IOException | InterruptedException e) {
            throw new EmailException(e);
        }
    }

    @Override
    public void sendVerifyEmail(String link, long expirationInMinutes) throws EmailException {
            try {
                sendViaBrevo("Vérification de l'email", """
                    Quelqu'un vient de créer un compte Hachette Livre avec votre e-mail.
                    <br/>Si vous êtes à l'origine de cette requête, veuillez cliquer sur le lien ci-dessous afin de vérifier votre adresse mail : <br/>
                    """ + link);
            } catch (IOException | InterruptedException e) {
                throw new EmailException(e);
            }
    }

    private void sendViaBrevo(String subject,
                              String htmlBody) throws IOException, InterruptedException, EmailException {

        String brevoApiKey = "test";
        String brevoSender = "no-reply@kiosque-edu.com";  // ex: no-reply@hachette.fr
        String brevoSenderName = "Hachette Livre";
        //String templateIdStr  = getRequired(config, "brevo.template-id");   // ex: "42"
        //int templateId        = Integer.parseInt(templateIdStr);

        Map<String, Object> body = buildBrevoPayload(
                // templateId,
                brevoSender,
                brevoSenderName,
                user,
                subject,
                htmlBody
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("Content-Type", "application/json; charset=UTF-8")
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
        root.put("htmlContent", htmlBody);

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
