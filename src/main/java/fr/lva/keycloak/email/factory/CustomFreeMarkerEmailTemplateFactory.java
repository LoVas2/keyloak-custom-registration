package fr.lva.keycloak.email.factory;

import fr.lva.keycloak.email.provider.CustomFreeMarkerEmailTemplateProvider;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProviderFactory;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Freemarker Email Template factory
 */
public class CustomFreeMarkerEmailTemplateFactory extends FreeMarkerEmailTemplateProviderFactory {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public CustomFreeMarkerEmailTemplateFactory() {
        // Empty constructor
    }

    @Override
    public EmailTemplateProvider create(KeycloakSession session) {
        // Find clientId from URI
        if (shouldUseBrevoForCurrentClient(session)) {
            return new CustomFreeMarkerEmailTemplateProvider(session);
        } else {
            logger.debug("Client is not configured : {}", session.getContext().getUri().getQueryParameters());
            return new FreeMarkerEmailTemplateProvider(session);
        }
    }

    /**
     * Check if the client use the SSO mire
     *
     * @return true if the client as the theme attribute configured
     */
    private boolean shouldUseBrevoForCurrentClient(KeycloakSession session) {
        KeycloakContext context = session.getContext();
        ClientModel client = context.getClient();
        if (client == null) {
            return false;
        }

        String loginTheme = client.getAttribute("login_theme");

        return "theme-hachette".equals(loginTheme);
    }

}
