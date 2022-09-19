package it.smartcommunitylab.aac.webauthn.service;

import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.RegistrationFailedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.common.NoSuchProviderException;
import it.smartcommunitylab.aac.common.NoSuchUserException;
import it.smartcommunitylab.aac.common.RegistrationException;
import it.smartcommunitylab.aac.core.provider.ProviderConfigRepository;
import it.smartcommunitylab.aac.core.provider.UserAccountService;
import it.smartcommunitylab.aac.internal.persistence.InternalUserAccount;
import it.smartcommunitylab.aac.webauthn.auth.WebAuthnAuthenticationException;
import it.smartcommunitylab.aac.webauthn.model.CredentialCreationInfo;
import it.smartcommunitylab.aac.webauthn.model.WebAuthnRegistrationRequest;
import it.smartcommunitylab.aac.webauthn.persistence.WebAuthnUserCredentialsRepository;
import it.smartcommunitylab.aac.webauthn.provider.WebAuthnCredentialsServiceConfig;

@Service
public class WebAuthnRegistrationRpService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${application.url}")
    private String applicationUrl;

    private final UserAccountService<InternalUserAccount> userAccountService;
    private final WebAuthnUserCredentialsRepository credentialsRepository;

    // TODO evaluate removal and pass config as param in ops
    private final ProviderConfigRepository<WebAuthnCredentialsServiceConfig> registrationRepository;

    // leverage a local cache for fetching rps
    // TODO cache invalidation or check on load or drop cache
    private final LoadingCache<String, RelyingParty> registrations = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(new CacheLoader<String, RelyingParty>() {
                @Override
                public RelyingParty load(final String providerId) throws Exception {
                    WebAuthnCredentialsServiceConfig config = registrationRepository.findByProviderId(providerId);

                    if (config == null) {
                        throw new NoSuchProviderException();
                    }

                    // build RP configuration
                    URL publicAppUrl = new URL(applicationUrl);
                    Set<String> origins = Collections.singleton(applicationUrl);

                    RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                            .id(publicAppUrl.getHost())
                            .name(config.getRealm())
                            .build();

                    WebAuthnYubicoCredentialsRepository webauthnRepository = new WebAuthnYubicoCredentialsRepository(
                            config.getRepositoryId(), userAccountService, credentialsRepository);

                    RelyingParty rp = RelyingParty.builder()
                            .identity(rpIdentity)
                            .credentialRepository(webauthnRepository)
                            .allowUntrustedAttestation(config.isAllowedUnstrustedAttestation())
                            .allowOriginPort(false)
                            .allowOriginSubdomain(false)
                            .origins(origins)
                            .build();

                    return rp;

                }
            });

    public WebAuthnRegistrationRpService(UserAccountService<InternalUserAccount> userAccountService,
            WebAuthnUserCredentialsRepository credentialsRepository,
            ProviderConfigRepository<WebAuthnCredentialsServiceConfig> registrationRepository) {
        Assert.notNull(userAccountService, "user account service is mandatory");
        Assert.notNull(credentialsRepository, "credentials repository is mandatory");
        Assert.notNull(registrationRepository, "provider registration repository is mandatory");

        this.userAccountService = userAccountService;
        this.credentialsRepository = credentialsRepository;
        this.registrationRepository = registrationRepository;
    }

    public RelyingParty getRelyingParty(String registrationId) {
        Assert.hasText(registrationId, "id can not be null or empty");

        try {
            return registrations.get(registrationId);
        } catch (ExecutionException e) {
            return null;
        }
    }

    /*
     * Registration ceremony
     * 
     * an already registered user can add credentials. For first login use either
     * confirmation link or account link via session
     */

    public CredentialCreationInfo startRegistration(String registrationId,
            String username, String displayName)
            throws NoSuchUserException, RegistrationException, NoSuchProviderException {
        logger.debug("start registration for {} with provider {}", StringUtils.trimAllWhitespace(username),
                StringUtils.trimAllWhitespace(registrationId));

        WebAuthnCredentialsServiceConfig config = registrationRepository.findByProviderId(registrationId);
        RelyingParty rp = getRelyingParty(registrationId);
        if (config == null || rp == null) {
            throw new NoSuchProviderException();
        }

//        // load account to check status
//        InternalUserAccount account = userAccountService.findAccountById(config.getRepositoryId(), username);
//        if (account == null) {
//            throw new NoSuchUserException();
//        }

        Optional<ByteArray> userHandle = rp.getCredentialRepository().getUserHandleForUsername(username);
        if (userHandle.isEmpty()) {
            throw new NoSuchUserException();
        }

        // build a new identity for this key
        String userDisplayName = StringUtils.hasText(displayName) ? displayName : username;
        UserIdentity identity = UserIdentity.builder()
                .name(username)
                .displayName(userDisplayName)
                .id(userHandle.get())
                .build();

        // build config
        AuthenticatorSelectionCriteria authenticatorSelection = AuthenticatorSelectionCriteria.builder()
                .residentKey(config.getRequireResidentKey())
                .userVerification(config.getRequireUserVerification())
                .build();

        int timeout = config.getRegistrationTimeout() * 1000;
        StartRegistrationOptions startRegistrationOptions = StartRegistrationOptions.builder()
                .user(identity)
                .authenticatorSelection(authenticatorSelection)
                .timeout(timeout)
                .build();

        PublicKeyCredentialCreationOptions options = rp.startRegistration(startRegistrationOptions);

        CredentialCreationInfo info = new CredentialCreationInfo();
        info.setUserHandle(userHandle.get());
        info.setOptions(options);

        return info;
    }

    public RegistrationResult finishRegistration(
            String registrationId, WebAuthnRegistrationRequest request,
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc)
            throws RegistrationException, NoSuchUserException,
            NoSuchProviderException {
        WebAuthnCredentialsServiceConfig config = registrationRepository.findByProviderId(registrationId);
        RelyingParty rp = getRelyingParty(registrationId);
        if (config == null || rp == null) {
            throw new NoSuchProviderException();
        }

        if (request == null) {
            throw new RegistrationException();
        }

        try {
            logger.debug("finish registration for {} with provider {}", request.getUserHandle(),
                    StringUtils.trimAllWhitespace(registrationId));

            CredentialCreationInfo info = request.getCredentialCreationInfo();

            // parse response
            PublicKeyCredentialCreationOptions options = info.getOptions();
            RegistrationResult result = rp
                    .finishRegistration(FinishRegistrationOptions.builder().request(options).response(pkc).build());

            boolean attestationIsTrusted = result.isAttestationTrusted();
            logger.debug("registration attestation is trusted: ", String.valueOf(attestationIsTrusted));

            if (!attestationIsTrusted && !rp.isAllowUntrustedAttestation()) {
                throw new WebAuthnAuthenticationException("_", "Untrusted attestation");
            }

            return result;
        } catch (RegistrationFailedException e) {
            throw new RegistrationException(e.getMessage());
        }
    }

}
