package it.smartcommunitylab.aac.core.provider;

import java.util.Collection;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.common.NoSuchCredentialException;
import it.smartcommunitylab.aac.common.NoSuchUserException;
import it.smartcommunitylab.aac.common.RegistrationException;
import it.smartcommunitylab.aac.core.model.ConfigMap;
import it.smartcommunitylab.aac.core.model.ConfigurableCredentialsProvider;
import it.smartcommunitylab.aac.core.model.UserCredentials;

/*
 * Credentials service handles credentials associated to a single user account,
 * which is handled by a given account service in the same realm 
 */

public interface AccountCredentialsService<R extends UserCredentials, M extends ConfigMap, C extends CredentialsServiceConfig<M>>
        extends ConfigurableResourceProvider<R, ConfigurableCredentialsProvider, M, C> {

//    /*
//     * Set current credential (if only one is allowed) or all credentials
//     */
//
//    public R getCredentials(String accountId) throws NoSuchUserException;
//
//    public R setCredentials(String accountId, UserCredentials credentials) throws NoSuchUserException;
//
//    public void resetCredentials(String accountId) throws NoSuchUserException;
//

    /*
     * Set specific credentials
     */

    public Collection<R> listCredentials(String accountId) throws NoSuchUserException;

    public R addCredentials(String username, UserCredentials uc) throws NoSuchUserException;

    public R getCredentials(String accountId, String credentialsId)
            throws NoSuchUserException, NoSuchCredentialException;

    public R setCredentials(String accountId, String credentialsId, UserCredentials credentials)
            throws NoSuchUserException, RegistrationException, NoSuchCredentialException;

//    public void resetCredentials(String accountId, String credentialsId)
//            throws NoSuchUserException, NoSuchCredentialException;

    public R revokeCredentials(String accountId, String credentialsId)
            throws NoSuchUserException, NoSuchCredentialException;

    public void deleteCredentials(String accountId, String credentialsId)
            throws NoSuchUserException, NoSuchCredentialException;

    public void deleteCredentials(String accountId);

//    /*
//     * Action urls
//     */
//    public String getSetUrl() throws NoSuchUserException;
//
//    /*
//     * At least one between resetLink or resetCredentials is required to support
//     * reset. Credentials used for login should be resettable, while those used for
//     * MFA should be removed or revoked.
//     */
//    public String getResetUrl();

    default public String getType() {
        return SystemKeys.RESOURCE_CREDENTIALS;
    }

}
