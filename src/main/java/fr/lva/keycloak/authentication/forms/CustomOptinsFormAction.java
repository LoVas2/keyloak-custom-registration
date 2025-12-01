package fr.lva.keycloak.authentication.forms;

import jakarta.ws.rs.core.MultivaluedMap;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.ServicesLogger;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;
import org.keycloak.util.JsonSerialization;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomOptinsFormAction implements FormAction, FormActionFactory {

    private static final Logger LOGGER = Logger.getLogger(CustomOptinsFormAction.class);
    public static final String PROVIDER_ID = "custom-optins-form";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Custom Optins Form";
    }

    @Override
    public String getHelpText() {
        return "Third and last step of custom registration flow";
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
    public void buildPage(FormContext context, LoginFormsProvider form) {
        // Search for reCAPTCHA execution in the authentication flow
        AuthenticationFlowModel flow = context.getRealm().getAuthenticationFlowById(
                context.getAuthenticationSession().getAuthNote("flow_id")
        );

        if (flow != null) {
            List<AuthenticationExecutionModel> executions = context.getRealm()
                    .getAuthenticationExecutionsStream(flow.getId())
                    .collect(Collectors.toList());

            for (AuthenticationExecutionModel execution : executions) {
                if (execution.getAuthenticator() != null &&
                        execution.getAuthenticator().equals("registration-recaptcha-action") &&
                        execution.getRequirement() == AuthenticationExecutionModel.Requirement.REQUIRED) {

                    // reCAPTCHA is configured in the flow
                    AuthenticatorConfigModel config = context.getRealm()
                            .getAuthenticatorConfigById(execution.getAuthenticatorConfig());

                    if (config != null && config.getConfig() != null) {
                        Map<String, String> captchaConfig = config.getConfig();
                        String siteKey = captchaConfig.get("site.key");

                        if (siteKey != null && !siteKey.isEmpty()) {
                            form.setAttribute("recaptchaRequired", true);
                            form.setAttribute("recaptchaSiteKey", siteKey);
                            String userLanguageTag = context.getSession().getContext()
                                    .resolveLocale(context.getUser()).toLanguageTag();
                            form.addScript("https://www.google.com/recaptcha/api.js?hl=" + userLanguageTag);
                            return;
                        }
                    }
                }
            }
        }

        // reCAPTCHA not found or not configured
        form.setAttribute("recaptchaRequired", false);
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();

        // ===== reCAPTCHA VALIDATION =====
        // Check if reCAPTCHA is configured in the flow
        AuthenticationFlowModel flow = context.getRealm().getAuthenticationFlowById(
                context.getAuthenticationSession().getAuthNote("flow_id")
        );

        if (flow != null) {
            List<AuthenticationExecutionModel> executions = context.getRealm()
                    .getAuthenticationExecutionsStream(flow.getId())
                    .collect(Collectors.toList());

            for (AuthenticationExecutionModel execution : executions) {
                if (execution.getAuthenticator() != null &&
                        execution.getAuthenticator().equals("registration-recaptcha-action") &&
                        execution.getRequirement() == AuthenticationExecutionModel.Requirement.REQUIRED) {

                    // reCAPTCHA is required, validate it
                    AuthenticatorConfigModel config = context.getRealm()
                            .getAuthenticatorConfigById(execution.getAuthenticatorConfig());

                    if (config != null && config.getConfig() != null) {
                        String secretKey = config.getConfig().get("secret.key");
                        String captchaResponse = formData.getFirst("g-recaptcha-response");

                        if (!validateRecaptcha(context, captchaResponse, secretKey)) {
                            errors.add(new FormMessage(null, "recaptchaFailed"));
                        }
                    }
                    break;
                }
            }
        }

        // If reCAPTCHA validation failed, return early
        if (!errors.isEmpty()) {
            context.error(Errors.INVALID_REGISTRATION);
            context.validationError(formData, errors);
            return;
        }

        // ===== RESTORE DATA FROM PREVIOUS STEPS =====
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String email = authSession.getAuthNote("email");
        String civility = authSession.getAuthNote("civility");
        String lastName = authSession.getAuthNote("lastName");
        String firstName = authSession.getAuthNote("firstName");
        String profilesCsv = authSession.getAuthNote("profile");

        if (email != null && !email.isEmpty()) {
            formData.putSingle("email", email);
            formData.putSingle("username", email);
        }
        if (civility != null) formData.putSingle("civility", civility);
        if (lastName != null) formData.putSingle("lastName", lastName);
        if (firstName != null) formData.putSingle("firstName", firstName);
        if (profilesCsv != null && !profilesCsv.isEmpty()) {
            List<String> profiles = Arrays.asList(profilesCsv.split(","));
            formData.put("profile", profiles);
        }

        // ===== USER PROFILE VALIDATION =====
        UserProfileProvider profileProvider = context.getSession().getProvider(UserProfileProvider.class);
        UserProfile profile = profileProvider.create(UserProfileContext.REGISTRATION, formData);

        try {
            profile.validate();
            context.success();
        } catch (ValidationException e) {
            List<FormMessage> validationErrors = e.getErrors().stream()
                    .map(error -> new FormMessage(error.getAttribute(), error.getMessage(),
                            error.getMessageParameters()))
                    .collect(Collectors.toList());
            context.error(Errors.INVALID_REGISTRATION);
            context.validationError(formData, validationErrors);
        }
    }

    @Override
    public void success(FormContext context) {
        // ==============================
        // FINAL STEP CREATE USER
        // ==============================

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // ===== Fetch data from previous steps =====

        // Step 1 : credentials
        String email = authSession.getAuthNote("email");
        String password = authSession.getAuthNote("password");

        // Step 2 : personal data
        String civility = authSession.getAuthNote("civility");
        String lastName = authSession.getAuthNote("lastName");
        String firstName = authSession.getAuthNote("firstName");
        String profilesCsv = authSession.getAuthNote("profile"); // Format CSV: "Enseignant,Directeur"

        // Step 3 : optins and current page
        String uai = formData.getFirst("uai");
        String newsletter = formData.getFirst("newsletter");
        String cgu = formData.getFirst("cgu");

        // ===== SAVE USER =====

        UserModel user = session.users().addUser(realm, email);
        user.setEnabled(true);
        user.setEmail(email);
        user.setEmailVerified(false);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        if (civility != null && !civility.isEmpty()) {
            user.setSingleAttribute("civility", civility);
        }
        if (profilesCsv != null && !profilesCsv.isEmpty()) {
            List<String> profiles = Arrays.asList(profilesCsv.split(","));
            user.setAttribute("profile", profiles);
        }
        if (uai != null && !uai.isEmpty()) {
            user.setSingleAttribute("uai", uai);
        }
        user.setSingleAttribute("newsletter", newsletter != null ? "true" : "false");
        user.setSingleAttribute("cgu", cgu != null ? "true" : "false");

        // ===== DEFINE PASSWORD =====

        UserCredentialModel passwordCredential = UserCredentialModel.password(password);
        user.credentialManager().updateCredential(passwordCredential);

        // ===== ASSOCIATE USER TO CURRENT SESSION =====
        context.setUser(user);

        // ===== LOGS & EVENTS =====
        context.getEvent().user(user);
        context.getEvent().success();
    }

    // Helper method to validate reCAPTCHA
    private boolean validateRecaptcha(ValidationContext context, String captchaResponse, String secretKey) {
        if (captchaResponse == null || captchaResponse.isEmpty()) {
            return false;
        }

        CloseableHttpClient httpClient = context.getSession()
                .getProvider(HttpClientProvider.class).getHttpClient();
        HttpPost post = new HttpPost("https://www.google.com/recaptcha/api/siteverify");

        List<NameValuePair> formparams = new LinkedList<>();
        formparams.add(new BasicNameValuePair("secret", secretKey));
        formparams.add(new BasicNameValuePair("response", captchaResponse));
        formparams.add(new BasicNameValuePair("remoteip", context.getConnection().getRemoteAddr()));

        try {
            UrlEncodedFormEntity form = new UrlEncodedFormEntity(formparams, "UTF-8");
            post.setEntity(form);
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                InputStream content = response.getEntity().getContent();
                try {
                    Map json = JsonSerialization.readValue(content, Map.class);
                    return Boolean.TRUE.equals(json.get("success"));
                } finally {
                    EntityUtils.consumeQuietly(response.getEntity());
                }
            }
        } catch (Exception e) {
            ServicesLogger.LOGGER.recaptchaFailed(e);
        }
        return false;
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
