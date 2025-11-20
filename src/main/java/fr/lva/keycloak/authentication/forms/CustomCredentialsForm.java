package fr.lva.keycloak.authentication.forms;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.FormAuthenticator;
import org.keycloak.authentication.FormAuthenticatorFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.RegisterBean;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class CustomCredentialsForm implements FormAuthenticator, FormAuthenticatorFactory {

    public static final String PROVIDER_ID = "custom-credentials-form";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public FormAuthenticator create(KeycloakSession keycloakSession) {
        return this;
    }

    @Override
    public String getDisplayType() {
        return "Custom Credentials Form";
    }

    @Override
    public String getHelpText() {
        return "First step of custom registration flow";
    }

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED
    };

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public Response render(FormContext context, LoginFormsProvider form) {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>(context.getHttpRequest().getDecodedFormParameters());

        String loginHint = context.getAuthenticationSession()
                .getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);

        if (loginHint != null && !loginHint.isEmpty()) {
            RealmModel realm = context.getRealm();
            if (realm.isRegistrationEmailAsUsername()) {
                String value = formData.getFirst("email");
                if (value == null || value.trim().isEmpty()) {
                    formData.putSingle("email", loginHint);
                }
            } else {
                String value = formData.getFirst("username");
                if (value == null || value.trim().isEmpty()) {
                    formData.putSingle("username", loginHint);
                }
            }
        }
        form.setFormData(formData);

        RegisterBean rb = new RegisterBean(new MultivaluedHashMap<>(), context.getSession());
        //legacy bean for static template
        form.setAttribute("register", rb);
        //bean for dynamic template
        form.setAttribute("profile", rb);

        return form.createForm("register-custom-credentials.ftl");
    }

    // Unused methods from Factory //
    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }

    @Override
    public void init(Config.Scope scope) {
        // Nothing here
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // Nothing here
    }

    @Override
    public void close() {
        // Nothing here
    }
}
