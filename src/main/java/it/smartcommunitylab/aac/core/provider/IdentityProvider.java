package it.smartcommunitylab.aac.core.provider;

import java.util.Collection;
import java.util.Map;

import it.smartcommunitylab.aac.common.NoSuchUserException;
import it.smartcommunitylab.aac.core.auth.ExtendedAuthenticationProvider;
import it.smartcommunitylab.aac.core.base.AbstractIdentityProviderConfig;
import it.smartcommunitylab.aac.core.model.UserAccount;
import it.smartcommunitylab.aac.core.model.UserAuthenticatedPrincipal;
import it.smartcommunitylab.aac.core.model.UserIdentity;

/*
 * Identity providers handle authentication for users and produce a valid user identity
 * 
 * An identity is composed by an account, bounded to the provider, and one or more attribute sets.
 * At minimum, we expect every provider to fulfill core attribute sets (basic, email, openid, account).
 */

public interface IdentityProvider<I extends UserIdentity, U extends UserAccount, P extends UserAuthenticatedPrincipal, C extends AbstractIdentityProviderConfig>
        extends ResourceProvider {

    public static final String ATTRIBUTE_MAPPING_FUNCTION = "attributeMapping";

    /*
     * Config
     */
    public String getName();

    public String getDescription();

    public String getDisplayMode();

    public C getConfig();

    /*
     * auth provider
     */
    public ExtendedAuthenticationProvider<P, U> getAuthenticationProvider();

    /*
     * internal providers
     */
    public AccountProvider<U> getAccountProvider();

    public IdentityAttributeProvider<P, U> getAttributeProvider();

    /*
     * subjects are global, we can resolve
     */

    public SubjectResolver<U> getSubjectResolver();

    /*
     * convert identities from authenticatedPrincipal. Used for login only.
     * 
     * If given a subjectId the provider should update the account
     */

    public I convertIdentity(P principal, String userId)
            throws NoSuchUserException;

    /*
     * fetch identities from this provider
     * 
     * implementations are not required to support this
     */

    // identityId is provider-specific
    public I getIdentity(String identityId) throws NoSuchUserException;

    public I getIdentity(String identityId, boolean fetchAttributes)
            throws NoSuchUserException;

    /*
     * fetch for user
     * 
     * opt-in, loads identities outside login for persisted accounts linked to the
     * subject
     * 
     * providers implementing this will enable the managers to fetch identities
     * outside the login flow!
     */

    public Collection<I> listIdentities(String userId);

    public Collection<I> listIdentities(String userId, boolean fetchAttributes);

    /*
     * Delete accounts.
     * 
     * Implementations are required to implement this, even as a no-op. At minimum
     * we expect providers to clean up any local registration or cache.
     */
    public void deleteIdentity(String identityId) throws NoSuchUserException;

    public void deleteIdentities(String userId);

    /*
     * Login
     * 
     * Url is required to be presented in login forms, while authEntrypoint can
     * handle different kind of requests.
     */

    public String getAuthenticationUrl();

//    public AuthenticationEntryPoint getAuthenticationEntryPoint();

    /*
     * Additional action urls
     */
    public Map<String, String> getActionUrls();
}
