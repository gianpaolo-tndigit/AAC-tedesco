package it.smartcommunitylab.aac.openid.apple.provider;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.openid.apple.auth.AppleClientAuthenticationParametersConverter;
import it.smartcommunitylab.aac.openid.apple.service.AppleOidcUserService;
import it.smartcommunitylab.aac.attributes.OpenIdAttributesSet;
import it.smartcommunitylab.aac.common.InvalidDefinitionException;
import it.smartcommunitylab.aac.common.SystemException;
import it.smartcommunitylab.aac.core.model.AttributeSet;
import it.smartcommunitylab.aac.core.provider.IdentityProvider;
import it.smartcommunitylab.aac.openid.auth.OIDCAuthenticationException;
import it.smartcommunitylab.aac.openid.auth.OIDCAuthenticationToken;
import it.smartcommunitylab.aac.openid.model.OIDCUserAuthenticatedPrincipal;
import it.smartcommunitylab.aac.openid.persistence.OIDCUserAccount;
import it.smartcommunitylab.aac.openid.persistence.OIDCUserAccountId;
import it.smartcommunitylab.aac.openid.persistence.OIDCUserAccountRepository;
import it.smartcommunitylab.aac.openid.provider.OIDCAuthenticationProvider;
import it.smartcommunitylab.aac.openid.provider.OIDCIdentityProvider;

public class AppleAuthenticationProvider
        extends OIDCAuthenticationProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OIDCUserAccountRepository accountRepository;
    private final AppleIdentityProviderConfig config;

    private final OidcAuthorizationCodeAuthenticationProvider oidcProvider;

    public AppleAuthenticationProvider(
            String providerId,
            OIDCUserAccountRepository accountRepository,
            AppleIdentityProviderConfig config,
            String realm) {
        super(SystemKeys.AUTHORITY_APPLE, providerId, accountRepository, config.toOidcProviderConfig(), realm);
        Assert.notNull(accountRepository, "account repository is mandatory");
        Assert.notNull(config, "provider config is mandatory");

        this.config = config;
        this.accountRepository = accountRepository;

        // build appropriate client auth request converter
        OAuth2AuthorizationCodeGrantRequestEntityConverter requestEntityConverter = new OAuth2AuthorizationCodeGrantRequestEntityConverter();
        requestEntityConverter.addParametersConverter(new AppleClientAuthenticationParametersConverter<>(config));

        // we support only authCode login
        DefaultAuthorizationCodeTokenResponseClient accessTokenResponseClient = new DefaultAuthorizationCodeTokenResponseClient();
        accessTokenResponseClient.setRequestEntityConverter(requestEntityConverter);

        // use custom user service to load from id_token + user response
        this.oidcProvider = new OidcAuthorizationCodeAuthenticationProvider(accessTokenResponseClient,
                new AppleOidcUserService());

    }

    @Override
    public Authentication doAuthenticate(Authentication authentication) throws AuthenticationException {
        // extract registrationId and check if matches our providerId
        OAuth2LoginAuthenticationToken loginAuthenticationToken = (OAuth2LoginAuthenticationToken) authentication;
        String registrationId = loginAuthenticationToken.getClientRegistration().getRegistrationId();
        if (!getProvider().equals(registrationId)) {
            // this login is not for us, let others process it
            return null;
        }

        // TODO extract codeResponse + tokenResponse for audit
        String authorizationRequest = loginAuthenticationToken.getAuthorizationExchange().getAuthorizationRequest()
                .getAuthorizationRequestUri();
        String authorizationResponse = loginAuthenticationToken.getAuthorizationExchange().getAuthorizationResponse()
                .getRedirectUri();

        try {
            // delegate to oidc provider
            Authentication auth = oidcProvider.authenticate(authentication);

            if (auth != null) {
                // convert to our authToken and clear exchange information, those are not
                // serializable..
                OAuth2LoginAuthenticationToken authenticationToken = (OAuth2LoginAuthenticationToken) auth;
                // extract sub identifier
                String subject = authenticationToken.getPrincipal().getAttribute(IdTokenClaimNames.SUB);
                if (!StringUtils.hasText(subject)) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_request"));
                }

                // check if account is present and locked
                OIDCUserAccount account = accountRepository.findOne(new OIDCUserAccountId(getProvider(), subject));
                if (account != null && account.isLocked()) {
                    throw new OIDCAuthenticationException(new OAuth2Error("invalid_request"), "account not available",
                            authorizationRequest,
                            authorizationResponse, null, null);
                }

                auth = new OIDCAuthenticationToken(
                        subject,
                        authenticationToken.getPrincipal(),
                        authenticationToken.getAccessToken(),
                        authenticationToken.getRefreshToken(),
                        Collections.singleton(new SimpleGrantedAuthority(Config.R_USER)));
            }

            return auth;
        } catch (OAuth2AuthenticationException e) {
            throw new OIDCAuthenticationException(e.getError(), e.getMessage(), authorizationRequest,
                    authorizationResponse, null, null);
        }
    }

    @Override
    protected OIDCUserAuthenticatedPrincipal createUserPrincipal(Object principal) {
        // we need to unpack user and fetch properties
        OAuth2User oauthDetails = (OAuth2User) principal;

        // upstream subject identifier
        String subject = oauthDetails.getAttribute(IdTokenClaimNames.SUB);

        // name is always available, is mapped via provider configuration
        String username = oauthDetails.getName();

        // we still don't have userId
        String userId = null;

        // rebuild details to clear authorities
        // by default they contain the response body, ie. the full accessToken +
        // everything else

        // fetch attributes from user when provided
        // these are provided only at first login, so update when not null
        String firstName = oauthDetails.getAttribute("firstName");
        String lastName = oauthDetails.getAttribute("lastName");

        // need account to load saved attributes
        OIDCUserAccount account = accountRepository.findOne(new OIDCUserAccountId(getProvider(), subject));
        if (account != null) {
            firstName = firstName != null ? firstName : account.getGivenName();
            lastName = lastName != null ? lastName : account.getFamilyName();
        }

        // bind principal to ourselves
        OIDCUserAuthenticatedPrincipal user = new OIDCUserAuthenticatedPrincipal(getAuthority(), getProvider(),
                getRealm(),
                userId, subject);
        user.setUsername(username);
        user.setPrincipal(oauthDetails);

        // custom attribute mapping
        if (executionService != null && StringUtils.hasText(customMappingFunction)) {
            try {
                // get all attributes from principal except jwt attrs
                // TODO handle all attributes not only strings.
                Map<String, Serializable> principalAttributes = user.getAttributes().entrySet().stream()
                        .filter(e -> !ArrayUtils.contains(OIDCIdentityProvider.JWT_ATTRIBUTES, e.getKey()))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                // execute script
                Map<String, Serializable> customAttributes = executionService.executeFunction(
                        IdentityProvider.ATTRIBUTE_MAPPING_FUNCTION,
                        customMappingFunction, principalAttributes);

                // update map
                if (customAttributes != null) {
                    // replace map
                    principalAttributes = customAttributes.entrySet().stream()
                            .filter(e -> !ArrayUtils.contains(OIDCIdentityProvider.JWT_ATTRIBUTES, e.getKey()))
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                    user.setAttributes(principalAttributes);
                }
            } catch (SystemException | InvalidDefinitionException ex) {
                logger.debug("error mapping principal attributes via script: " + ex.getMessage());
            }
        }

        // map attributes to openid set and flatten to string
        AttributeSet oidcAttributeSet = openidMapper.mapAttributes(user.getAttributes());
        Map<String, String> oidcAttributes = oidcAttributeSet.getAttributes()
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.exportValue()));

        // fetch email when available
        String email = oidcAttributes.get(OpenIdAttributesSet.EMAIL);

        boolean emailVerified = StringUtils.hasText(oidcAttributes.get(OpenIdAttributesSet.EMAIL_VERIFIED))
                ? Boolean.parseBoolean(oidcAttributes.get(OpenIdAttributesSet.EMAIL_VERIFIED))
                : false;

        // update principal
        user.setEmail(email);
        user.setEmailVerified(emailVerified);

        // save attributes from user as standard
        Map<String, Serializable> userAttributes = new HashMap<>();
        userAttributes.put(OpenIdAttributesSet.GIVEN_NAME, firstName);
        userAttributes.put(OpenIdAttributesSet.FAMILY_NAME, lastName);
        user.setAttributes(userAttributes);

        return user;
    }

}