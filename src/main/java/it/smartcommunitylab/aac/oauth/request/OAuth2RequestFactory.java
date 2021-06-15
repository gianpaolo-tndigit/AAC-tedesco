package it.smartcommunitylab.aac.oauth.request;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.common.exceptions.UnsupportedGrantTypeException;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.model.ScopeType;
import it.smartcommunitylab.aac.model.User;
import it.smartcommunitylab.aac.oauth.flow.FlowExtensionsService;
import it.smartcommunitylab.aac.oauth.flow.OAuthFlowExtensions;
import it.smartcommunitylab.aac.oauth.model.AuthorizationGrantType;
import it.smartcommunitylab.aac.oauth.model.OAuth2ClientDetails;
import it.smartcommunitylab.aac.oauth.model.ResponseType;
import it.smartcommunitylab.aac.scope.Scope;
import it.smartcommunitylab.aac.scope.ScopeRegistry;

public class OAuth2RequestFactory implements OAuth2TokenRequestFactory, OAuth2AuthorizationRequestFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private FlowExtensionsService flowExtensionsService;
    private ScopeRegistry scopeRegistry;

    public OAuth2RequestFactory() {

    }

    public void setScopeRegistry(ScopeRegistry scopeRegistry) {
        this.scopeRegistry = scopeRegistry;
    }

    public void setFlowExtensionsService(FlowExtensionsService flowExtensionsService) {
        this.flowExtensionsService = flowExtensionsService;
    }

    @Override
    public TokenRequest createTokenRequest(Map<String, String> requestParameters, OAuth2ClientDetails clientDetails) {

        // required parameters
        String clientId = requestParameters.get("client_id");
        String grantType = requestParameters.get("grant_type");

        // check if we didn't receive clientId, use authentication info
        if (clientId == null) {
            clientId = clientDetails.getClientId();
        }

        // check extensions and integrate modifications
        if (flowExtensionsService != null) {
            OAuthFlowExtensions ext = flowExtensionsService.getOAuthFlowExtensions(clientDetails);
            if (ext != null) {

                Map<String, String> parameters = ext.onBeforeTokenGrant(requestParameters, clientDetails);
                if (parameters != null) {
                    // merge parameters into request params
                    requestParameters.putAll(parameters);
                    // enforce base params consistency
                    // TODO rewrite with proper merge with exclusion list
                    requestParameters.put("client_id", clientId);
                    requestParameters.put("grant_type", grantType);
                }
            }
        }

        Set<String> scopes = delimitedStringToSet(decodeParameters(requestParameters.get("scope")));

        // we collect serviceIds as resourceIds to mark these as audience
        // TODO fix this, services are AUDIENCE not resources!
        // we need a field in tokenRequest
        Set<String> resourceIds = delimitedStringToSet(decodeParameters(requestParameters.get("resource")));

        // also load resources derived from requested scope
        resourceIds.addAll(extractResourceIds(scopes));

        // depend on flow
        // use per flow token request subtype
        AuthorizationGrantType authorizationGrantType = AuthorizationGrantType.parse(grantType);
        if (authorizationGrantType == AUTHORIZATION_CODE) {
            String code = requestParameters.get("code");
            String redirectUri = requestParameters.get("redirect_uri");

            logger.trace("create token request for " + clientId
                    + " grantType " + grantType
                    + " code " + String.valueOf(code)
                    + " redirectUri " + String.valueOf(redirectUri)
                    + " resource ids " + resourceIds.toString());

            return new AuthorizationCodeTokenRequest(requestParameters, clientId, code, redirectUri, resourceIds, null);
        }
        if (authorizationGrantType == IMPLICIT) {
            // we can't build an implicit token request from params, only from authRequest
            throw new UnsupportedGrantTypeException("Grant type not supported: " + grantType);
        }
        if (authorizationGrantType == PASSWORD) {
            String username = requestParameters.get("username");
            String password = requestParameters.get("password");
            Set<String> requestScopes = extractScopes(scopes, clientDetails.getScope(), false);

            logger.trace("create token request for " + clientId
                    + " grantType " + grantType
                    + " user " + username
                    + " scopes " + requestScopes.toString()
                    + " resource ids " + resourceIds.toString());

            return new ResourceOwnerPasswordTokenRequest(requestParameters, clientId,
                    username, password,
                    requestScopes, resourceIds,
                    null);
        }

        if (authorizationGrantType == CLIENT_CREDENTIALS) {
            Set<String> requestScopes = extractScopes(scopes, clientDetails.getScope(), true);

            logger.trace("create token request for " + clientId
                    + " grantType " + grantType
                    + " client " + clientId
                    + " scopes " + requestScopes.toString()
                    + " resource ids " + resourceIds.toString());

            return new TokenRequest(requestParameters, clientId, authorizationGrantType.getValue(), requestScopes,
                    resourceIds,
                    null);
        }

        if (authorizationGrantType == REFRESH_TOKEN) {
            // refresh tokens can ask scopes, but by default will get those in original
            // request and not the client default
            Set<String> requestScopes = scopes;

            logger.trace("create token request for " + clientId
                    + " grantType " + grantType
                    + " client " + clientId
                    + " scope " + requestScopes.toString()
                    + " resource id " + resourceIds.toString());

            return new TokenRequest(requestParameters, clientId, authorizationGrantType.getValue(), requestScopes,
                    resourceIds,
                    null);
        }

        throw new UnsupportedGrantTypeException("Grant type not supported: " + grantType);

    }

    @Override
    public TokenRequest createTokenRequest(AuthorizationRequest authorizationRequest, String grantType) {
        AuthorizationGrantType authorizationGrantType = AuthorizationGrantType.parse(grantType);
        // only implicit grant can convert request
        if (authorizationGrantType == IMPLICIT) {
            // get parameters from authorization request
            return new ImplicitTokenRequest(authorizationRequest);
        }

        throw new UnsupportedGrantTypeException("Grant type not supported: " + grantType);

    }

    @Override
    public OAuth2Request createOAuth2Request(TokenRequest tokenRequest, OAuth2ClientDetails client) {
        return tokenRequest.createOAuth2Request(client);
    }

    @Override
    public AuthorizationRequest createAuthorizationRequest(Map<String, String> requestParameters,
            OAuth2ClientDetails clientDetails, User user) {

        // required parameters
        String clientId = requestParameters.get("client_id");
        String responseType = requestParameters.get("response_type");
        Set<String> responseTypes = delimitedStringToSet(decodeParameters(responseType));

        // optional
        String state = requestParameters.get("state");

        // check if we didn't receive clientId, use authentication info
        if (clientId == null) {
            clientId = clientDetails.getClientId();
        }

        // check extensions and integrate modifications
        if (flowExtensionsService != null) {
            OAuthFlowExtensions ext = flowExtensionsService.getOAuthFlowExtensions(clientDetails);
            if (ext != null) {

                Map<String, String> parameters = ext.onBeforeUserApproval(requestParameters, user, clientDetails);
                if (parameters != null) {
                    // merge parameters into request params
                    requestParameters.putAll(parameters);
                    // enforce base params consistency
                    // TODO rewrite with proper merge with exclusion list
                    requestParameters.put("client_id", clientId);
                    requestParameters.put("response_type", responseType);
                    requestParameters.put("state", state);

                }
            }
        }

        Set<String> scopes = delimitedStringToSet(decodeParameters(requestParameters.get("scope")));
        String redirectUri = requestParameters.get("redirect_uri");
        String responseMode = requestParameters.get("response_mode");
        if (responseMode == null) {
            responseMode = (responseTypes.contains("token") | responseTypes.contains("id_token")) ? "fragment"
                    : "query";
        }

        Set<String> requestScopes = extractScopes(scopes, clientDetails.getScope(), false);

        // we collect serviceIds as resourceIds to mark these as audience
        // TODO fix this, services are AUDIENCE not resources!
        // we need a field in tokenRequest
        Set<String> resourceIds = delimitedStringToSet(decodeParameters(requestParameters.get("resource")));

        // also load resources derived from requested scope
        resourceIds.addAll(extractResourceIds(scopes));

        Set<String> audience = delimitedStringToSet(decodeParameters(requestParameters.get("audience")));

        Set<String> prompt = delimitedStringToSet(decodeParameters(requestParameters.get("prompt")));

        logger.trace("create authorization request for " + clientId
                + " response type " + responseTypes.toString()
                + " response mode " + String.valueOf(responseMode)
                + " redirect " + String.valueOf(redirectUri)
                + " scope " + requestScopes.toString()
                + " resource ids " + resourceIds.toString()
                + " audience ids " + audience.toString());

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(requestParameters,
                Collections.<String, String>emptyMap(),
                clientId, requestScopes,
                resourceIds, null, false,
                state, redirectUri,
                responseTypes);

        // extensions
        Map<String, Serializable> extensions = authorizationRequest.getExtensions();

        // audience
        extensions.put("audience", StringUtils.collectionToCommaDelimitedString(audience));

        // response mode
        extensions.put("response_mode", responseMode);

        // support NONCE
        String nonce = requestParameters.get("nonce");
        if (StringUtils.hasText(nonce)) {
            extensions.put("nonce", nonce);
        }

        // support prompt
        if (!prompt.isEmpty()) {
            extensions.put("prompt", StringUtils.collectionToCommaDelimitedString(prompt));
        }

        return authorizationRequest;
    }

    @Override
    public OAuth2Request createOAuth2Request(AuthorizationRequest request, OAuth2ClientDetails clientDetails) {
        return request.createOAuth2Request();
    }

    private Set<String> extractScopes(Set<String> scopes, Collection<String> clientScopes, boolean isClient) {
        ScopeType type = isClient ? ScopeType.CLIENT : ScopeType.USER;

        if ((scopes == null || scopes.isEmpty())) {
            // when request scopes are empty or null use all scopes registered by client
            scopes = new HashSet<>(clientScopes);

            if (scopeRegistry != null) {
                // keep only those matching request type
                scopes = clientScopes.stream().filter(
                        s -> {
                            Scope sc = scopeRegistry.findScope(s);
                            if (sc == null) {
                                return false;
                            }

                            return (sc.getType() == type || sc.getType() == ScopeType.GENERIC);
                        })
                        .collect(Collectors.toSet());

            }

        }

        return scopes;

        // TODO rework 2FA
//        boolean addStrongOperationScope = false;
//        if (scopes.contains(Config.SCOPE_OPERATION_CONFIRMED)) {
//            Object authDetails = SecurityContextHolder.getContext().getAuthentication().getDetails();
//            if (authDetails != null && authDetails instanceof AACOAuthRequest) {
//                if (((AACOAuthRequest) authDetails).isMobile2FactorConfirmed()) {
//                    addStrongOperationScope = true;
//                }
//                // clear for inappropriate access
//                ((AACOAuthRequest) authDetails).unsetMobile2FactorConfirmed();
//            }
//            if (!addStrongOperationScope) {
//                throw new InvalidScopeException("The operation.confirmed scope is not authorized by user");
//            }
//        }
// disable check, validator will take care
//        Set<String> allowedScopes = scopes;
//
//        if (scopeRegistry != null) {
//            // keep only those matching request type
//            Set<Scope> scs = scopes.stream().map(s -> {
//                return scopeRegistry.findScope(s);
//            })
//                    .filter(s -> s != null)
//                    .filter(s -> (s.getType() == ScopeType.GENERIC || s.getType() == type))
//                    .collect(Collectors.toSet());
//
//            allowedScopes = scs.stream().map(s -> s.getScope()).collect(Collectors.toSet());
//
//        }
//
//        return allowedScopes;
    }

    private Set<String> extractResourceIds(Set<String> scopes) {
        if (scopeRegistry != null) {
            return scopes.stream().map(s -> {
                return scopeRegistry.findScope(s);
            })
                    .filter(s -> s != null)
                    .map(s -> s.getResourceId())
                    .collect(Collectors.toSet());
        }

        return Collections.emptySet();

    }

    private Set<String> delimitedStringToSet(String str) {
        String[] tokens = StringUtils.delimitedListToStringArray(str, DELIMITER);
        return new LinkedHashSet<>(Arrays.asList(tokens));
    }

    /*
     * Workaround for badly formatted param
     */
    private String decodeParameters(String value) {
        String result = value;
        if (StringUtils.hasText(result)) {

            // check if spaces are still encoded as %20
            if (result.contains("%20")) {
                // replace with spaces
                result = result.replace("%20", DELIMITER);
            }

            // check if strings are separated with comma (out of spec)
            if (result.contains(",")) {
                result = result.replace(",", DELIMITER);
            }
        }

        return result;
    }

    private final static String DELIMITER = " ";

    private final static AuthorizationGrantType AUTHORIZATION_CODE = AuthorizationGrantType.AUTHORIZATION_CODE;
    private final static AuthorizationGrantType IMPLICIT = AuthorizationGrantType.IMPLICIT;
    private final static AuthorizationGrantType CLIENT_CREDENTIALS = AuthorizationGrantType.CLIENT_CREDENTIALS;
    private final static AuthorizationGrantType PASSWORD = AuthorizationGrantType.PASSWORD;
    private final static AuthorizationGrantType REFRESH_TOKEN = AuthorizationGrantType.REFRESH_TOKEN;

}