package it.smartcommunitylab.aac.openid.provider;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.core.base.AbstractConfigurableProvider;
import it.smartcommunitylab.aac.core.base.ConfigurableProvider;
import it.smartcommunitylab.aac.openid.OIDCIdentityAuthority;

public class OIDCIdentityProviderConfig extends AbstractConfigurableProvider {

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_FACEBOOK = "facebook";
    public static final String PROVIDER_GITHUB = "github";

    public static final String DEFAULT_REDIRECT_URL = "{baseUrl}" + OIDCIdentityAuthority.AUTHORITY_URL
            + "{action}/{registrationId}";

    protected OIDCIdentityProviderConfig(String provider, String realm) {
        super(SystemKeys.AUTHORITY_OIDC, provider, realm);
    }

    @Override
    public String getType() {
        return SystemKeys.RESOURCE_IDENTITY;
    }

    // TODO map attributes? we could simply use clientRegistation
    // TODO validate? we could simply use clientRegistation

    public ClientRegistration toClientRegistration() {
        // read base params from configMap
        String clientId = getConfigurationProperty("clientId");
        String clientSecret = getConfigurationProperty("clientSecret");
        String clientName = getConfigurationProperty("clientName");

        // read default params from configMap
        String clientAuthenticationMethod = getProperty("clientAuthenticationMethod",
                ClientAuthenticationMethod.BASIC.getValue());

        String[] scope = StringUtils.commaDelimitedListToStringArray(getProperty("scope", "openid,profile,email"));

        String userNameAttributeName = getProperty("userNameAttributeName", "sub");

        // read provider params
        // explicit config
        String authorizationUri = getConfigurationProperty("authorizationUri");
        String tokenUri = getConfigurationProperty("tokenUri");
        String jwkSetUri = getConfigurationProperty("jwkSetUri");
        String userInfoUri = getConfigurationProperty("userInfoUri");

        // TODO add autoconfiguration support
        String issuerUri = getConfigurationProperty("issuerUri");

        // via builder,
        // providerId is unique, use as registrationId
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(getProvider());
        // get templates if naming matches
        ClientRegistration.Builder template = getCommonBuilder(getProvider(), clientName);
        if (template != null) {
            // use template
            builder = template;
        } else {
            // set with default if not from template
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(clientAuthenticationMethod));
            builder.scope(scope);
            builder.userNameAttributeName(userNameAttributeName);
        }

        // we support only authCode
        // TODO check PKCE
        builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        // add our redirect template
        builder.redirectUriTemplate(DEFAULT_REDIRECT_URL);

        // set client
        builder.clientId(clientId);
        builder.clientSecret(clientSecret);
        builder.clientName(clientName);

        // set only non null
        if (StringUtils.hasText(authorizationUri)) {
            builder.authorizationUri(authorizationUri);
        }

        if (StringUtils.hasText(tokenUri)) {
            builder.tokenUri(tokenUri);
        }

        if (StringUtils.hasText(userInfoUri)) {
            builder.userInfoUri(userInfoUri);
        }

        if (StringUtils.hasText(jwkSetUri)) {
            builder.jwkSetUri(jwkSetUri);
        }

        // DISABLED, need spring security next version
//        if (StringUtils.hasText(issuerUri)) {
//            builder.issuerUri(issuerUri);
//        }

        return builder.build();

    }

    public static ClientRegistration.Builder getCommonBuilder(String registrationId, String name) {
        ClientRegistration.Builder builder = null;
        if (PROVIDER_GOOGLE.equals(name)) {
            builder = CommonOAuth2Provider.GOOGLE.getBuilder(registrationId);          
        } else if (PROVIDER_FACEBOOK.equals(name)) {
            builder =  CommonOAuth2Provider.FACEBOOK.getBuilder(registrationId);
        } else if (PROVIDER_GITHUB.equals(name)) {
            builder = CommonOAuth2Provider.GITHUB.getBuilder(registrationId);
        }

        // TODO add additional templates (and read from config!)

        return builder;

    }

    private String getProperty(String key, String defaultValue) {
        if (StringUtils.hasText(getConfigurationProperty(key))) {
            return getConfigurationProperty(key);
        }

        return defaultValue;
    }

    /*
     * builders
     */
    public static ConfigurableProvider toConfigurableProvider(OIDCIdentityProviderConfig op) {
        ConfigurableProvider cp = new ConfigurableProvider(SystemKeys.AUTHORITY_OIDC, op.getProvider(), op.getRealm());
        cp.setType(SystemKeys.RESOURCE_IDENTITY);
        cp.setConfiguration(op.getConfiguration());

        return cp;
    }

    public static OIDCIdentityProviderConfig fromConfigurableProvider(ConfigurableProvider cp) {
        OIDCIdentityProviderConfig op = new OIDCIdentityProviderConfig(cp.getProvider(), cp.getRealm());
        op.setConfiguration(cp.getConfiguration());

        return op;

    }

}