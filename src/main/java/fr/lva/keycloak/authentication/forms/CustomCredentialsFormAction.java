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
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.validation.Validation;

import java.util.ArrayList;
import java.util.List;

import static org.keycloak.services.messages.Messages.*;

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
        // Called before the FormAuthenticator.render method -> Add any additional
        // attributes that are needed for the form
    }

    @Override
    public void validate(ValidationContext context) {
        // First method called for form processing. It validates the user inputs and
        // challenges again if there are any errors
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();
        context.getEvent().detail(Details.REGISTER_METHOD, "form");

        String email = formData.getFirst("email");
        String emailConfirm = formData.getFirst("email-confirm");
        String password = formData.getFirst("password");
        String passwordConfirm = formData.getFirst("password-confirm");

        // ===== EMAIL VALIDATION =====

        if (Validation.isBlank(email)) {
            errors.add(new FormMessage("email", MISSING_EMAIL));
        } else {
            // Check email format
            if (!Validation.isEmailValid(email)) {
                errors.add(new FormMessage("email", INVALID_EMAIL));
            }

            // Check email is free to use
            UserModel existingUser = context.getSession().users()
                    .getUserByEmail(context.getRealm(), email);
            if (existingUser != null) {
                // Build password reset URL
                String resetUrl = context.getSession().getContext().getUri()
                        .getBaseUriBuilder()
                        .path("realms")
                        .path(context.getRealm().getName())
                        .path("login-actions")
                        .path("reset-credentials")
                        .build()
                        .toString();
                // Add frontend error with reset password link inside
                errors.add(new FormMessage("email", EMAIL_EXISTS, resetUrl));
            }
        }

        if (!Validation.isBlank(email) && !email.equals(emailConfirm)) {
            errors.add(new FormMessage("email-confirm", Messages.INVALID_EMAIL_CONFIRM));
        }

        // ===== PASSWORD VALIDATION =====

        if (Validation.isBlank(password)) {
            errors.add(new FormMessage("password", MISSING_PASSWORD));
        } else {
            // Check password policies
            // pass email as username for validation as user is null
            PolicyError policyError = context.getSession()
                    .getProvider(PasswordPolicyManagerProvider.class)
                    .validate(email, password);
            if (policyError != null) {
                errors.add(new FormMessage("password", policyError.getMessage(), policyError.getParameters()));
            }
        }

        if (!Validation.isBlank(password) && !password.equals(passwordConfirm)) {
            errors.add(new FormMessage("password-confirm", INVALID_PASSWORD_CONFIRM));
        }

        if (!errors.isEmpty()) {
            context.error(Errors.INVALID_REGISTRATION);
            // Remove passwords from form
            formData.remove("password");
            formData.remove("password-confirm");
            context.validationError(formData, errors);
        } else {
            context.success();
        }
    }

    @Override
    public void success(FormContext context) {
        // Save user attributes in session to retrieve them later
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        context.getAuthenticationSession()
                .setAuthNote("email", formData.getFirst("email"));
        context.getAuthenticationSession()
                .setAuthNote("password", formData.getFirst("password"));

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
