package it.smartcommunitylab.aac.webauthn.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;

import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.webauthn.persistence.WebAuthnCredential;
import it.smartcommunitylab.aac.webauthn.persistence.WebAuthnCredentialsRepository;
import it.smartcommunitylab.aac.webauthn.persistence.WebAuthnUserAccount;
import it.smartcommunitylab.aac.webauthn.persistence.WebAuthnUserAccountRepository;

/**
 * For this class, everytime we should use the 'username', we
 * will instead use a string in the following format:
 * 'realmname{@link #separator}username'. This
 * is due to the fact that yubico's library assumes that two distinct users can
 * not have the same username (https://git.io/JD5Vr).
 */
public class WebAuthnYubicoCredentialsRepository implements CredentialRepository {

    private final String providerId;
    private WebAuthnUserAccountRepository userAccountRepository;
    private WebAuthnCredentialsRepository webAuthnCredentialsRepository;

    public WebAuthnYubicoCredentialsRepository(String provider,
            WebAuthnUserAccountRepository userAccountRepository,
            WebAuthnCredentialsRepository webAuthnCredentialsRepository) {
        this.userAccountRepository = userAccountRepository;
        this.webAuthnCredentialsRepository = webAuthnCredentialsRepository;
        this.providerId = provider;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        WebAuthnUserAccount account = userAccountRepository.findByProviderAndUsername(providerId, username);
        if (account == null) {
            return Collections.emptySet();
        }
        final List<WebAuthnCredential> credentials = webAuthnCredentialsRepository
                .findByUserHandle(account.getUserHandle());
        Set<PublicKeyCredentialDescriptor> descriptors = new HashSet<>();
        for (WebAuthnCredential c : credentials) {
            Set<AuthenticatorTransport> transports = StringUtils.commaDelimitedListToSet(c.getTransports())
                    .stream()
                    .map(t -> AuthenticatorTransport.of(t))
                    .collect(Collectors.toSet());
            PublicKeyCredentialDescriptor descriptor = PublicKeyCredentialDescriptor.builder()
                    .id(ByteArray.fromBase64(c.getCredentialId())).type(PublicKeyCredentialType.PUBLIC_KEY)
                    .transports(transports)
                    .build();
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        WebAuthnUserAccount account = userAccountRepository.findByProviderAndUsername(providerId, username);
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(ByteArray.fromBase64(account.getUserHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        WebAuthnUserAccount account = userAccountRepository.findByUserHandle(userHandle.getBase64());
        if (account == null) {
            return Optional.empty();
        }
        return Optional.of(account.getUsername());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        WebAuthnUserAccount acc = userAccountRepository.findByUserHandle(userHandle.getBase64());
        if (acc == null) {
            return Optional.empty();
        }
        List<WebAuthnCredential> credentials = webAuthnCredentialsRepository.findByUserHandle(acc.getUserHandle());
        for (final WebAuthnCredential cred : credentials) {
            if (cred.getCredentialId().equals(credentialId.getBase64())) {
                return Optional.of(getRegisteredCredential(cred));
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        // In our database the credentialID already has a unique constraint
        WebAuthnCredential cred = webAuthnCredentialsRepository.findByCredentialId(credentialId.getBase64());
        if (cred == null) {
            return Collections.emptySet();
        }
        return Collections.singleton(getRegisteredCredential(cred));
    }

    private RegisteredCredential getRegisteredCredential(WebAuthnCredential credential) {
        final WebAuthnUserAccount account = userAccountRepository.getOne(credential.getUserHandle());
        return RegisteredCredential.builder().credentialId(ByteArray.fromBase64(credential.getCredentialId()))
                .userHandle(ByteArray.fromBase64(account.getUserHandle()))
                .publicKeyCose(ByteArray.fromBase64(credential.getPublicKeyCose()))
                .signatureCount(credential.getSignatureCount()).build();
    }
}