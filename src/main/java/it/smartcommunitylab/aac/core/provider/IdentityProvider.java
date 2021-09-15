package it.smartcommunitylab.aac.core.provider;

import java.util.Map;

import it.smartcommunitylab.aac.common.NoSuchUserException;
import it.smartcommunitylab.aac.core.auth.ExtendedAuthenticationProvider;
import it.smartcommunitylab.aac.core.auth.UserAuthenticatedPrincipal;
import it.smartcommunitylab.aac.core.base.ConfigurableProperties;
import it.smartcommunitylab.aac.core.model.UserIdentity;

public interface IdentityProvider extends ResourceProvider {

    public static final String ATTRIBUTE_MAPPING_FUNCTION = "attributeMapping";

    /*
     * Config
     */
    public String getName();

    public String getDescription();

    public ConfigurableProperties getConfiguration();

    /*
     * auth provider
     */
    public ExtendedAuthenticationProvider getAuthenticationProvider();

    /*
     * internal providers
     */
    public AccountProvider getAccountProvider();

    /*
     * subjects are global, we can resolve
     */

    public SubjectResolver getSubjectResolver();

    /*
     * convert identities from authenticatedPrincipal. for usage during login
     * 
     * if given a subjectId the idp should update the account
     */

    public UserIdentity convertIdentity(UserAuthenticatedPrincipal principal, String subjectId)
            throws NoSuchUserException;

    /*
     * Login
     * 
     * at least one between url and entryPoint is required to dispatch requests. Url
     * is required to be presented in login forms, while authEntrypoint can handle
     * different kind of requests.
     */

    public String getAuthenticationUrl();

//    public AuthenticationEntryPoint getAuthenticationEntryPoint();

    public String getDisplayMode();

    /*
     * Additional urls
     */
    public Map<String, String> getActionUrls();
}
