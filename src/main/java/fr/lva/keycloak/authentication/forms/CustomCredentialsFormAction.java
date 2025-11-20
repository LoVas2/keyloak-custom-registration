package fr.lva.keycloak.authentication.forms;

import fr.lva.keycloak.services.messages.Messages;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.validation.Validation;

import java.util.ArrayList;
import java.util.List;

public class CustomCredentialsFormAction implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "custom-credentials-form";

    @Override
    public String getId() {
        return PROVIDER_ID;
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
    public FormAction create(KeycloakSession keycloakSession) {
        return this;
    }

    // Form Actions methods
    @Override
    public void buildPage(FormContext formContext, LoginFormsProvider loginFormsProvider) {
        // Called before the FormAuthenticator.render method -> Add any additional attributes that are needed for the form
    }

    @Override
    public void validate(ValidationContext context) {
        // First method called for form processing. It validates the user inputs and challenges again if there are any errors
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();
        context.getEvent().detail(Details.REGISTER_METHOD, "form");

        if (Validation.isBlank(formData.getFirst("email"))) {
            errors.add(new FormMessage("email", Messages.MISSING_EMAIL));
        } else if (!formData.getFirst("email").equals(formData.getFirst("email-confirm"))) {
            errors.add(new FormMessage("email-confirm", Messages.INVALID_EMAIL_CONFIRM));
        }

        // check personal datas

        // Checks whether the password field is blank
        if (Validation.isBlank(formData.getFirst(("password")))) {
            errors.add(new FormMessage("password", Messages.MISSING_PASSWORD));
        }
        // Checks whether the password and confirm-password fields have same input
        else if (!formData.getFirst("password").equals(formData.getFirst("password-confirm"))) {
            errors.add(new FormMessage("password-confirm", Messages.INVALID_PASSWORD_CONFIRM));
        }

        // Check whether there are any errors – if yes then send context.error
        // else call context.success
        if (!errors.isEmpty()) {
            context.error(Errors.INVALID_REGISTRATION);
            formData.remove("password");
            formData.remove("password-confirm");
            context.validationError(formData, errors);
        } else {
            context.success();
        }
    }

    @Override
    public void success(FormContext context) {
        // Called after validate method success
        MultivaluedMap<String, String> formData =
                context.getHttpRequest().getDecodedFormParameters();

        context.getAuthenticationSession()
                .setAuthNote("username", formData.getFirst("username"));
        context.getAuthenticationSession()
                .setAuthNote("password", formData.getFirst("password"));
        context.getAuthenticationSession()
                .setAuthNote("password-confirm", formData.getFirst("password-confirm"));
    }

    @Override
    public boolean configuredFor(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {
        return false;
    }

    @Override
    public void setRequiredActions(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {

    }

    @Override
    public boolean requiresUser() {
        // Always false for registration
        return false;
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
