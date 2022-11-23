package it.smartcommunitylab.aac.core.provider;

import java.util.List;

import javax.validation.constraints.NotNull;

import it.smartcommunitylab.aac.common.NoSuchCredentialException;
import it.smartcommunitylab.aac.common.RegistrationException;
import it.smartcommunitylab.aac.core.model.UserCredentials;

public interface UserCredentialsService<C extends UserCredentials> {

    public List<C> findCredentialsByRealm(@NotNull String realm);

    public C findCredentialsById(@NotNull String repository, @NotNull String id);

    public C findCredentialsByUuid(@NotNull String uuid);

    public List<C> findCredentialsByAccount(@NotNull String repository, @NotNull String accountId);

    public List<C> findCredentialsByUser(@NotNull String repository, @NotNull String userId);

    public C addCredentials(@NotNull String repository, @NotNull String id, @NotNull C reg)
            throws RegistrationException;

    public C updateCredentials(@NotNull String repository, @NotNull String id, @NotNull C reg)
            throws NoSuchCredentialException, RegistrationException;

    public void deleteCredentials(@NotNull String repository, @NotNull String id);

    public void deleteCredentialsByUser(@NotNull String repository, @NotNull String userId);

    public void deleteCredentialsByAccount(@NotNull String repository, @NotNull String account);

}
