package fr.lva.keycloak.authentication.forms;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.sessions.AuthenticationSessionModel;
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

        // Fetch all datas from previous steps cause UserProfile valides all data
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String email = authSession.getAuthNote("email");
        if (email != null && !email.isEmpty()) {
            formData.putSingle("email", email);
            formData.putSingle("username", email);
        }

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
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        context.getAuthenticationSession().setAuthNote("civility", formData.getFirst("civility"));
        context.getAuthenticationSession().setAuthNote("lastName", formData.getFirst("lastName"));
        context.getAuthenticationSession().setAuthNote("firstName", formData.getFirst("firstName"));
        // Save Profiles list as CSV format
        List<String> profileValues = formData.get("profile");
        if (profileValues != null && !profileValues.isEmpty()) {
            String profilesAsString = profileValues.stream()
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .collect(Collectors.joining(","));
            context.getAuthenticationSession().setAuthNote("profile", profilesAsString);
        }
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
