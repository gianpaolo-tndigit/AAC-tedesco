package it.smartcommunitylab.aac.oauth.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.common.exceptions.UnauthorizedClientException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import it.smartcommunitylab.aac.core.AuthenticationHelper;
import it.smartcommunitylab.aac.core.auth.ClientAuthenticationToken;
import it.smartcommunitylab.aac.oauth.ExtTokenStore;

/**
 * OAuth2.0 Token Revocation controller as of RFC7009:
 * https://tools.ietf.org/html/rfc7009
 *
 */
@Controller
@Api(tags = { "OAuth 2.0 Token Revocation" })
public class TokenRevocationEndpoint {

    public static final String TOKEN_REVOCATION_URL = "/oauth/revoke";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ExtTokenStore tokenStore;

    @Autowired
    private AuthenticationHelper authHelper;

    /**
     * Revoke the access token and the associated refresh token.
     * 
     * @param token
     */
    @ApiOperation(value = "Revoke token")
    @RequestMapping(method = RequestMethod.POST, value = TOKEN_REVOCATION_URL)
    public ResponseEntity<String> revokeTokenWithParam(
            @RequestParam String token,
            @RequestParam(required = false, defaultValue = "access_token") String token_type_hint) {
        try {
            logger.debug("request revoke of token " + token + " hint " + String.valueOf(token_type_hint));

            // get auth for client
            ClientAuthenticationToken clientAuth = authHelper.getClientAuthentication();
            if (clientAuth == null) {
                // this endpoint is accessible only to authenticated clients
                throw new UnauthorizedClientException("client authentication missing or invalid");
            }

            // load token from store

            OAuth2Authentication auth = null;
            OAuth2RefreshToken refreshToken = null;
            OAuth2AccessToken accessToken = null;

            // check hint
            if ("refresh_token".equals(token_type_hint)) {
                // load refresh token if present
                refreshToken = tokenStore.readRefreshToken(token);
                if (refreshToken != null) {
                    logger.trace("load auth for refresh token " + refreshToken.getValue());

                    // load authentication
                    auth = tokenStore.readAuthenticationForRefreshToken(refreshToken);
                }
            }

            if (auth == null) {
                // either token is access_token, or
                // as per spec, try as access_token independently of hint
                accessToken = tokenStore.readAccessToken(token);
                if (accessToken != null) {
                    logger.trace("load auth for access token " + accessToken.getValue());

                    // load authentication
                    auth = tokenStore.readAuthentication(accessToken);
                }

            }

            if (auth == null) {
                // not found
                throw new InvalidTokenException("no token for value " + token);
            }

            // check if client is authorized to remove this
            String clientId = auth.getOAuth2Request().getClientId();
            logger.trace("token clientId " + clientId);

            // load auth from context (basic auth or post if supported)
            logger.trace("client auth requesting revoke  " + String.valueOf(clientAuth.getClientId()));

            if (clientId.equals(clientAuth.getClientId())) {

                // remove all
                if (refreshToken != null) {
                    logger.trace("remove refresh token for " + refreshToken.getValue());
                    tokenStore.removeRefreshToken(refreshToken);

                    // should also remove access tokens binded to refresh
                    accessToken = tokenStore.readAccessTokenForRefreshToken(refreshToken.getValue());
                }

                if (accessToken != null) {
                    // DISABLED removal of refresh for access
//                    // check if refresh embedded
//                    if (accessToken.getRefreshToken() != null) {
//                        logger.trace("remove refresh token for " + accessToken.getRefreshToken().getValue());
//                        tokenStore.removeRefreshToken(accessToken.getRefreshToken());
//                    }
                    logger.trace("remove access token for " + accessToken.getValue());
                    tokenStore.removeAccessToken(accessToken);
                }

            } else {
                throw new UnauthorizedClientException("client is not the owner of the token");
            }

            return ResponseEntity.ok("");

        } catch (Exception e) {
            logger.error("Error revoke for token: " + e.getMessage());
            // TODO map error response as per
            // https://tools.ietf.org/html/rfc7009#section-2.1
            return ResponseEntity.ok("");
        }

    }
}
