package fr.lva.keycloak.authentication.forms;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

import java.util.List;
import java.util.stream.Collectors;

public class CustomPersonalDataFormAction implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "custom-personal-data-form";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Custom Personal Data Form";
    }

    @Override
    public String getHelpText() {
        return "Second step of custom registration flow";
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
        // Called before the FormAuthenticator.render method -> Add any additional
        // attributes that are needed for the form
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        UserProfileProvider profileProvider = context.getSession().getProvider(UserProfileProvider.class);
        UserProfile profile = profileProvider.create(UserProfileContext.REGISTRATION, formData);

        try {
            profile.validate();
            context.success();
        } catch (ValidationException e) {
            List<FormMessage> errors = e.getErrors().stream()
                    .map(error -> new FormMessage(error.getAttribute(), error.getMessage(), error.getMessageParameters()))
                    .collect(Collectors.toList());
            context.error(Errors.INVALID_REGISTRATION);
            context.validationError(formData, errors);
        }
    }

    @Override
    public void success(FormContext context) {
        // Called after validate method success
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        // Stocker les données personnelles dans la session pour les étapes suivantes
        context.getAuthenticationSession().setAuthNote("civility", formData.getFirst("civility"));
        context.getAuthenticationSession().setAuthNote("lastName", formData.getFirst("lastName"));
        context.getAuthenticationSession().setAuthNote("firstName", formData.getFirst("firstName"));
        context.getAuthenticationSession().setAuthNote("profile", formData.getFirst("profile"));
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
