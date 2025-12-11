package fr.lva.keycloak.email.factory;

import fr.lva.keycloak.email.provider.CustomEmailSenderProvider;
import org.keycloak.Config;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.email.EmailSenderProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class CustomEmailSenderProviderFactory implements EmailSenderProviderFactory {

    // Leave default value to surcharge default SPI
    public static final String ID = "default";

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        return new CustomEmailSenderProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Nothing to do
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public String getId() {
        return ID;
    }
}