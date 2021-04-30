package it.smartcommunitylab.aac.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.common.NoSuchProviderException;
import it.smartcommunitylab.aac.common.NoSuchRealmException;
import it.smartcommunitylab.aac.common.RegistrationException;
import it.smartcommunitylab.aac.common.SystemException;
import it.smartcommunitylab.aac.config.ProvidersProperties;
import it.smartcommunitylab.aac.config.ProvidersProperties.ProviderConfiguration;
import it.smartcommunitylab.aac.core.authorities.IdentityAuthority;
import it.smartcommunitylab.aac.core.base.AbstractConfigurableProvider;
import it.smartcommunitylab.aac.core.base.ConfigurableProperties;
import it.smartcommunitylab.aac.core.base.ConfigurableProvider;
import it.smartcommunitylab.aac.core.persistence.ProviderEntity;
import it.smartcommunitylab.aac.core.provider.AttributeProvider;
import it.smartcommunitylab.aac.core.provider.IdentityProvider;
import it.smartcommunitylab.aac.core.provider.IdentityService;
import it.smartcommunitylab.aac.core.service.ProviderService;
import it.smartcommunitylab.aac.core.service.RealmService;
import it.smartcommunitylab.aac.internal.provider.InternalIdentityProviderConfigMap;
import it.smartcommunitylab.aac.model.Realm;
import it.smartcommunitylab.aac.openid.provider.OIDCIdentityProviderConfigMap;
import it.smartcommunitylab.aac.saml.provider.SamlIdentityProviderConfigMap;

@Service
public class ProviderManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ProviderService providerService;

    private SessionManager sessionManager;

    private AuthorityManager authorityManager;

    private RealmService realmService;

    // keep a local map for global providers since these are not in db
    // key is providerId
    private Map<String, IdentityProvider> systemIdps;
    private Map<String, IdentityService> systemIdss;
    private Map<String, AttributeProvider> globalAttrps;

    public ProviderManager(
            AuthorityManager authorityManager,
            SessionManager sessionManager,
            ProviderService providerService, RealmService realmService,
            ProvidersProperties providers) {
        Assert.notNull(authorityManager, "authority manager is mandatory");
        Assert.notNull(sessionManager, "session manager is mandatory");
        Assert.notNull(providerService, "provider service is mandatory");
        Assert.notNull(realmService, "realm service is mandatory");

        this.authorityManager = authorityManager;
        this.sessionManager = sessionManager;
        this.providerService = providerService;
        this.realmService = realmService;

        this.systemIdps = new HashMap<>();
        this.systemIdss = new HashMap<>();
        this.globalAttrps = new HashMap<>();

        // create system idps
        // these users access administrative contexts, they will have realm=""
        // we expect no client/services in global+system realm!

        // always register the internal provider for the superuser account
        IdentityAuthority internal = authorityManager.getIdentityAuthority(SystemKeys.AUTHORITY_INTERNAL);

        ConfigurableProvider internalIdpConfig = new ConfigurableProvider(
                SystemKeys.AUTHORITY_INTERNAL, SystemKeys.AUTHORITY_INTERNAL,
                SystemKeys.REALM_SYSTEM);
        internalIdpConfig.setType(SystemKeys.RESOURCE_IDENTITY);
        logger.debug("register internal idp in system realm");
        IdentityProvider internalIdp = internal.registerIdentityProvider(internalIdpConfig);
        systemIdps.put(internalIdp.getProvider(), internalIdp);
        // global idp is an iss
        IdentityService internalIss = internal.getIdentityService(internalIdp.getProvider());
        if (internalIss != null) {
            systemIdss.put(internalIss.getProvider(), internalIss);
        }

        // process additional from config
        if (providers != null) {
            // identity providers
            for (ProviderConfiguration providerConfig : providers.getIdentity()) {
                try {
                    // check match
                    if (!SystemKeys.RESOURCE_IDENTITY.equals(providerConfig.getType())) {
                        continue;
                    }

                    // we handle only system providers, add others via bootstrap
                    if (SystemKeys.REALM_SYSTEM.equals(providerConfig.getRealm())
                            || !StringUtils.hasText(providerConfig.getRealm())) {
                        logger.debug(
                                "register provider for " + providerConfig.getType() + " in system realm: "
                                        + providerConfig.toString());

                        // translate config
                        ConfigurableProvider provider = new ConfigurableProvider(providerConfig.getAuthority(),
                                providerConfig.getProvider(), SystemKeys.REALM_SYSTEM);
                        provider.setType(providerConfig.getType());
                        provider.setName(providerConfig.getName());
                        provider.setEnabled(true);
                        for (Map.Entry<String, String> entry : providerConfig.getConfiguration().entrySet()) {
                            provider.setConfigurationProperty(entry.getKey(), entry.getValue());
                        }

                        // by default global providers persist account + attributes
                        String persistenceLevel = SystemKeys.PERSISTENCE_LEVEL_REPOSITORY;
                        if (StringUtils.hasText(providerConfig.getPersistence())) {
                            // set persistence level
                            persistenceLevel = providerConfig.getPersistence();
                        }
                        provider.setPersistence(persistenceLevel);

                        // register
                        IdentityAuthority ia = authorityManager.getIdentityAuthority(provider.getAuthority());
                        IdentityProvider idp = ia.registerIdentityProvider(provider);
                        systemIdps.put(idp.getProvider(), idp);

                        // check if we get an iss from this idp
                        IdentityService iss = ia.getIdentityService(idp.getProvider());
                        if (iss != null) {
                            systemIdss.put(iss.getProvider(), iss);
                        }

                    }
                } catch (RegistrationException | SystemException | IllegalArgumentException ex) {
                    logger.error("error registering provider :" + ex.getMessage(), ex);
                }
            }

            // attribute providers
            for (ProviderConfiguration providerConfig : providers.getAttributes()) {
                try {
                    // check match
                    if (!SystemKeys.RESOURCE_ATTRIBUTES.equals(providerConfig.getType())) {
                        continue;
                    }

                    // TODO
                } catch (SystemException | IllegalArgumentException ex) {
                    logger.error("error registering provider :" + ex.getMessage(), ex);
                }
            }
        }

        // now load all realm providers from storage
        // we iterate by authority to load consistently
        for (IdentityAuthority ia : authorityManager.listIdentityAuthorities()) {
            List<ConfigurableProvider> storeProviders = listProvidersByAuthority(ia.getAuthorityId());
            for (ConfigurableProvider provider : storeProviders) {
                // check match
                if (!SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
                    continue;
                }

                // try register
                if (provider.isEnabled()) {
                    try {
                        ia.registerIdentityProvider(provider);
                    } catch (Exception e) {
                        logger.error("error registering provider " + provider.getProvider() + " for realm "
                                + provider.getRealm() + ": " + e.getMessage());
                    }
                }
            }
        }

    }

    /*
     * Public API: realm providers only.
     * 
     * For global providers we enable only a subset of features (RO)
     * 
     * TODO add permissions
     */
    public Collection<ConfigurableProvider> listProviders(String realm) throws NoSuchRealmException {
        if (SystemKeys.REALM_GLOBAL.equals(realm) || SystemKeys.REALM_SYSTEM.equals(realm)) {
            // we do not persist in db global providers
            throw new SystemException("global providers are immutable");
        }

        Realm re = realmService.getRealm(realm);
        List<ProviderEntity> providers = providerService.listProvidersByRealm(re.getSlug());
        return providers.stream()
                .map(p -> fromEntity(p))
                .collect(Collectors.toList());
    }

    public Collection<ConfigurableProvider> listProviders(String realm, String type) throws NoSuchRealmException {
//        if (SystemKeys.REALM_GLOBAL.equals(realm) || SystemKeys.REALM_SYSTEM.equals(realm)) {
//            // we do not persist in db global providers
//            throw new SystemException("global providers are immutable");
//        }

        Realm re = realmService.getRealm(realm);
        List<ProviderEntity> providers = providerService.listProvidersByRealmAndType(re.getSlug(), type);
        return providers.stream()
                .map(p -> fromEntity(p))
                .collect(Collectors.toList());
    }

    public ConfigurableProvider getProvider(String realm, String providerId)
            throws NoSuchProviderException, NoSuchRealmException {
        Realm re = realmService.getRealm(realm);
        ProviderEntity pe = providerService.getProvider(providerId);

        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        // deprecated, let controllers/managers ask for status where needed
        // this does not pertain to configuration
//        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        return fromEntity(pe);

    }

    public ConfigurableProvider getProvider(String realm, String type, String providerId)
            throws NoSuchProviderException, NoSuchRealmException {
        Realm re = realmService.getRealm(realm);
        ProviderEntity pe = providerService.getProvider(providerId);

        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        if (!type.equals(pe.getType())) {
            throw new IllegalArgumentException("type does not match provider");
        }

        // deprecated, let controllers/managers ask for status where needed
        // this does not pertain to configuration
//        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        return fromEntity(pe);

    }

    public ConfigurableProvider addProvider(String realm,
            ConfigurableProvider provider) throws RegistrationException, SystemException, NoSuchRealmException {

        if (SystemKeys.REALM_GLOBAL.equals(realm) || SystemKeys.REALM_SYSTEM.equals(realm)) {
            // we do not persist in db global providers
            throw new RegistrationException("global providers are immutable");
        }

        Realm re = realmService.getRealm(realm);

        // generate a valid id
        ProviderEntity pe = providerService.createProvider();
        String providerId = pe.getProviderId();

        // unpack props and validate
        // TODO handle enum of authorities for validation
        String authority = provider.getAuthority();

        // we support only idp for now
        String type = provider.getType();
        if (!StringUtils.hasText(type) || !SystemKeys.RESOURCE_IDENTITY.equals(type)) {
            throw new RegistrationException("invalid provider type");
        }

        String name = provider.getName();
        String description = provider.getDescription();

        // TODO add enum
        String persistence = provider.getPersistence();
        if (!StringUtils.hasText(persistence)) {
            persistence = SystemKeys.PERSISTENCE_LEVEL_REPOSITORY;
        }

        if (!SystemKeys.PERSISTENCE_LEVEL_REPOSITORY.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_MEMORY.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_SESSION.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_NONE.equals(persistence)) {
            throw new RegistrationException("invalid persistence level");
        }

        Map<String, Serializable> configuration = null;
        if (SystemKeys.RESOURCE_IDENTITY.equals(type)) {

            // we validate config by converting to specific configMap
            ConfigurableProperties configurable = null;
            if (SystemKeys.AUTHORITY_INTERNAL.equals(authority)) {
                configurable = new InternalIdentityProviderConfigMap();
            } else if (SystemKeys.AUTHORITY_OIDC.equals(authority)) {
                configurable = new OIDCIdentityProviderConfigMap();
            } else if (SystemKeys.AUTHORITY_SAML.equals(authority)) {
                configurable = new SamlIdentityProviderConfigMap();
            }

            if (configurable == null) {
                throw new IllegalArgumentException("invalid configuration");
            }

            configurable.setConfiguration(provider.getConfiguration());
            configuration = configurable.getConfiguration();
        }

        pe = providerService.addProvider(authority, providerId, re.getSlug(),
                type,
                name, description,
                persistence,
                configuration);

        return fromEntity(pe);

    }

//    public ConfigurableProvider updateProvider(String realm, String providerId, boolean enabled)
//            throws NoSuchProviderException, NoSuchRealmException {
//        Realm re = realmService.getRealm(realm);
//        ProviderEntity pe = providerService.getProvider(providerId);
//
//        if (!re.getSlug().equals(pe.getRealm())) {
//            throw new IllegalArgumentException("realm does not match provider");
//        }
//
//        // check if active, we don't support update for active providers
//        boolean isActive = false;
//        // we support only idp now
//        if (SystemKeys.RESOURCE_IDENTITY.equals(pe.getType())) {
//            isActive = isProviderRegistered(providerId);
//        }
//
//        if (isActive) {
//            throw new IllegalArgumentException("active providers can not be updated");
//        }
//
//        // check previous status
//        if (enabled != pe.isEnabled()) {
//            // sync
//            pe = providerService.updateProvider(providerId, enabled, pe.getConfigurationMap());
//        }
//
//        return fromEntity(pe);
//
//    }

    public ConfigurableProvider updateProvider(String realm,
            String providerId, ConfigurableProvider provider)
            throws NoSuchProviderException, NoSuchRealmException {
        Realm re = realmService.getRealm(realm);
        ProviderEntity pe = providerService.getProvider(providerId);

        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        if (StringUtils.hasText(provider.getProvider()) && !providerId.equals(provider.getProvider())) {
            throw new IllegalArgumentException("configuration does not match provider");
        }

        // check if active, we don't support update for active providers
        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        if (isActive) {
            throw new IllegalArgumentException("active providers can not be updated");
        }

        // we update only props and configuration
        String name = provider.getName();
        String description = provider.getDescription();

        // TODO add enum
        String persistence = provider.getPersistence();
        if (!StringUtils.hasText(persistence)) {
            persistence = SystemKeys.PERSISTENCE_LEVEL_REPOSITORY;
        }

        if (!SystemKeys.PERSISTENCE_LEVEL_REPOSITORY.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_MEMORY.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_SESSION.equals(persistence)
                && !SystemKeys.PERSISTENCE_LEVEL_NONE.equals(persistence)) {
            throw new RegistrationException("invalid persistence level");
        }

        boolean enabled = provider.isEnabled();
        Map<String, Serializable> configuration = null;

        String type = pe.getType();
        if (SystemKeys.RESOURCE_IDENTITY.equals(type)) {
            String authority = pe.getAuthority();

            // we validate config by converting to specific configMap
            ConfigurableProperties configurable = null;
            if (SystemKeys.AUTHORITY_INTERNAL.equals(authority)) {
                configurable = new InternalIdentityProviderConfigMap();
            } else if (SystemKeys.AUTHORITY_OIDC.equals(authority)) {
                configurable = new OIDCIdentityProviderConfigMap();
            } else if (SystemKeys.AUTHORITY_SAML.equals(authority)) {
                configurable = new SamlIdentityProviderConfigMap();
            }

            if (configurable == null) {
                throw new IllegalArgumentException("invalid configuration");
            }

            configurable.setConfiguration(provider.getConfiguration());
            configuration = configurable.getConfiguration();
        }

        // update: even when enabled this provider won't be active until registration
        pe = providerService.updateProvider(providerId, enabled,
                name, description,
                persistence,
                configuration);

        return fromEntity(pe);
    }

    public void deleteProvider(String realm, String providerId)
            throws SystemException, NoSuchProviderException, NoSuchRealmException {
        Realm re = realmService.getRealm(realm);

        ProviderEntity pe = providerService.getProvider(providerId);
        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        // check if active, we don't support update for active providers
        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        if (isActive) {
            throw new IllegalArgumentException("active providers can not be deleted");
        }

        providerService.deleteProvider(providerId);

    }

    //
    public ConfigurableProvider registerProvider(
            String realm,
            String providerId) throws SystemException, NoSuchRealmException, NoSuchProviderException {

        Realm re = realmService.getRealm(realm);
        // fetch, only persisted configurations can be registered
        ProviderEntity pe = providerService.getProvider(providerId);
        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        // check if active, we don't support update for active providers
        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        if (isActive) {
            throw new IllegalArgumentException("active providers can not be registered again");
        }

        // check if already enabled in config, or update
        if (!pe.isEnabled()) {
            pe = providerService.updateProvider(providerId, true,
                    pe.getName(), pe.getDescription(),
                    pe.getPersistence(),
                    pe.getConfigurationMap());
        }

        ConfigurableProvider provider = fromEntity(pe);

        // we support only idp now
        if (SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
            registerIdentityProvider(provider);
            isActive = true;
        } else if (SystemKeys.RESOURCE_ATTRIBUTES.equals(provider.getType())) {
            // TODO attribute providers
        } else {
            throw new SystemException("unsupported provider type");
        }

        return provider;

    }

    public ConfigurableProvider unregisterProvider(
            String realm,
            String providerId) throws SystemException, NoSuchRealmException, NoSuchProviderException {

        Realm re = realmService.getRealm(realm);
        // fetch, only persisted configurations can be registered
        ProviderEntity pe = providerService.getProvider(providerId);
        if (!re.getSlug().equals(pe.getRealm())) {
            throw new IllegalArgumentException("realm does not match provider");
        }

        // check if already disabled in config, or update
        if (pe.isEnabled()) {
            pe = providerService.updateProvider(providerId, false,
                    pe.getName(), pe.getDescription(),
                    pe.getPersistence(),
                    pe.getConfigurationMap());
        }

        ConfigurableProvider provider = fromEntity(pe);

        // check if active
        boolean isActive = isProviderRegistered(pe.getType(), pe.getAuthority(), pe.getProviderId());

        if (isActive) {

            // we support only idp now
            if (SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
                unregisterIdentityProvider(provider);
                isActive = false;
            } else if (SystemKeys.RESOURCE_ATTRIBUTES.equals(provider.getType())) {
                // TODO attribute providers
            } else {
                throw new SystemException("unsupported provider type");
            }
        }

        return provider;
    }

    /*
     * Internal API
     * 
     * methods for handling providers either private or exposed for internal (core)
     * usage
     */

    /*
     * Enable/disable providers with authorities
     */

    private IdentityProvider registerIdentityProvider(ConfigurableProvider provider) throws SystemException {
        if (!provider.isEnabled()) {
            throw new IllegalArgumentException("provider is disabled");
        }

        // we support only idp now
        if (!SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
            throw new IllegalArgumentException("unsupported provider type");
        }

        IdentityAuthority a = authorityManager.getIdentityAuthority(provider.getAuthority());
        return a.registerIdentityProvider(provider);

    }

    private void unregisterIdentityProvider(ConfigurableProvider provider) throws SystemException {
        // we support only idp now
        if (!SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
            throw new IllegalArgumentException("unsupported provider type");
        }

        IdentityAuthority a = authorityManager.getIdentityAuthority(provider.getAuthority());
        String providerId = provider.getProvider();

        // terminate sessions
        sessionManager.destroyProviderSessions(providerId);
        a.unregisterIdentityProvider(provider.getRealm(), providerId);

    }

    /*
     * Public API: check provider registration with authorities
     */

    public boolean isProviderRegistered(String providerId) throws SystemException, NoSuchProviderException {
        ConfigurableProvider p = getProvider(providerId);
        return isProviderRegistered(p);
    }

    public boolean isProviderRegistered(ConfigurableProvider provider) throws SystemException {
        return isProviderRegistered(provider.getType(), provider.getAuthority(), provider.getProvider());
    }

    public boolean isProviderRegistered(String type, String authority, String providerId) throws SystemException {
        // we support only idp now
        if (SystemKeys.RESOURCE_IDENTITY.equals(type)) {
            IdentityAuthority a = authorityManager.getIdentityAuthority(authority);

            return a.hasIdentityProvider(providerId);
        } else if (SystemKeys.RESOURCE_ATTRIBUTES.equals(type)) {
            // TODO attribute providers
            throw new IllegalArgumentException("unsupported provider type");
        } else {
            throw new IllegalArgumentException("unsupported provider type");
        }

    }

    private Collection<IdentityProvider> listSystemIdentityProviders() {
        return systemIdps.values();
    }

    private Collection<IdentityService> listSystemIdentityServices() {
        return systemIdss.values();
    }

    /*
     * Persist configuration
     * 
     * only for realm providers, global are configured only via app.properties to
     * avoid mangling with administrative sessions
     */

    private List<ConfigurableProvider> listProvidersByAuthority(String authority) {
        List<ProviderEntity> providers = providerService.listProvidersByAuthority(authority);
        return providers.stream().map(p -> fromEntity(p)).collect(Collectors.toList());
    }

    private ConfigurableProvider getProvider(String providerId) throws NoSuchProviderException {
        ProviderEntity pe = providerService.getProvider(providerId);

        return fromEntity(pe);

    }

    /*
     * Identity providers
     * 
     * we expose only getters to ensure consumers won't update config. Also only
     * active (ie registered with an authority) providers are exposed.
     * 
     * we assume that registered providers are a match for stored configuration,
     * since config is immutable in authorities
     */

    public IdentityProvider findIdentityProvider(String providerId) {

        // lookup in global map first
        if (systemIdps.containsKey(providerId)) {
            return systemIdps.get(providerId);
        }
        try {
            ConfigurableProvider provider = getProvider(providerId);

            if (!SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
                return null;
            }

            // lookup in authority
            IdentityAuthority ia = authorityManager.getIdentityAuthority(provider.getAuthority());
            return ia.getIdentityProvider(providerId);
        } catch (NoSuchProviderException e) {
            return null;
        }
    }

    public IdentityProvider getIdentityProvider(String providerId) throws NoSuchProviderException {
        IdentityProvider idp = findIdentityProvider(providerId);
        if (idp == null) {
            // provider is not active or not existing
            // TODO add dedicated exception?
            throw new NoSuchProviderException("provider not found");
        }

        return idp;
    }

    // fast load, skips db lookup, returns null if missing
    public IdentityProvider fetchIdentityProvider(String authority, String providerId) {
        // lookup in global map first
        if (systemIdps.containsKey(providerId)) {
            return systemIdps.get(providerId);
        }

        // lookup in authority
        IdentityAuthority ia = authorityManager.getIdentityAuthority(authority);
        if (ia == null) {
            return null;
        }
        try {
            return ia.getIdentityProvider(providerId);
        } catch (NoSuchProviderException e) {
            return null;
        }
    }

    public Collection<IdentityProvider> getIdentityProviders(String realm) throws NoSuchRealmException {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityProviders();
        }

        Collection<ConfigurableProvider> providers = listProviders(realm, SystemKeys.RESOURCE_IDENTITY);

        // fetch each active provider from authority
        List<IdentityProvider> idps = new ArrayList<>();
        for (ConfigurableProvider provider : providers) {
            // lookup in authority
            IdentityAuthority ia = authorityManager.getIdentityAuthority(provider.getAuthority());
            try {
                IdentityProvider idp = ia.getIdentityProvider(provider.getProvider());
                if (idp != null) {
                    idps.add(idp);
                }
            } catch (NoSuchProviderException e) {
                // skip
            }

        }

        return idps;
    }

    // fast load, skips db lookup
    public Collection<IdentityProvider> fetchIdentityProviders(String realm) {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityProviders();
        }

        List<IdentityProvider> providers = new ArrayList<>();
        for (IdentityAuthority ia : authorityManager.listIdentityAuthorities()) {
            providers.addAll(ia.getIdentityProviders(realm));
        }

        return providers;

    }

    // fast load, skips db lookup
    public Collection<IdentityProvider> fetchIdentityProviders(String authority, String realm) {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityProviders().stream()
                    .filter(i -> i.getAuthority().equals(authority))
                    .collect(Collectors.toList());
        }

        IdentityAuthority ia = authorityManager.getIdentityAuthority(authority);
        if (ia != null) {
            return ia.getIdentityProviders(realm);
        }

        return null;
    }

    /*
     * Identity services
     * 
     * idp with persistence can expose an identity service for operations on stored
     * users, such as search,update,delete etc
     */

    public IdentityService findIdentityService(String providerId) {

        // lookup in global map first
        if (systemIdss.containsKey(providerId)) {
            return systemIdss.get(providerId);
        }
        try {
            ConfigurableProvider provider = getProvider(providerId);

            if (!SystemKeys.RESOURCE_IDENTITY.equals(provider.getType())) {
                return null;
            }

            // lookup in authority
            IdentityAuthority ia = authorityManager.getIdentityAuthority(provider.getAuthority());
            return ia.getIdentityService(providerId);
        } catch (NoSuchProviderException e) {
            return null;
        }
    }

    public IdentityService getIdentityService(String providerId) throws NoSuchProviderException {
        IdentityService idp = findIdentityService(providerId);
        if (idp == null) {
            // provider is not active or not existing
            // TODO add dedicated exception?
            throw new NoSuchProviderException("provider not found");
        }

        return idp;
    }

    // fast load, skips db lookup, returns null if missing
    public IdentityService fetchIdentityService(String authority, String providerId) {
        // lookup in global map first
        if (systemIdss.containsKey(providerId)) {
            return systemIdss.get(providerId);
        }

        // lookup in authority
        IdentityAuthority ia = authorityManager.getIdentityAuthority(authority);
        if (ia == null) {
            return null;
        }
        return ia.getIdentityService(providerId);
    }

    public Collection<IdentityService> getIdentityServices(String realm) throws NoSuchRealmException {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityServices();
        }

        Collection<ConfigurableProvider> providers = listProviders(realm, SystemKeys.RESOURCE_IDENTITY);

        // fetch each active provider from authority
        List<IdentityService> idps = new ArrayList<>();
        for (ConfigurableProvider provider : providers) {
            // lookup in authority
            IdentityAuthority ia = authorityManager.getIdentityAuthority(provider.getAuthority());
            IdentityService idp = ia.getIdentityService(provider.getProvider());
            if (idp != null) {
                idps.add(idp);
            }
        }

        return idps;
    }

    // fast load, skips db lookup
    public Collection<IdentityService> fetchIdentityServices(String realm) {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityServices();
        }

        List<IdentityService> providers = new ArrayList<>();
        for (IdentityAuthority ia : authorityManager.listIdentityAuthorities()) {
            providers.addAll(ia.getIdentityServices(realm));
        }

        return providers;

    }

    // fast load, skips db lookup
    public Collection<IdentityService> fetchIdentityServices(String authority, String realm) {
        if (SystemKeys.REALM_SYSTEM.equals(realm)) {
            return listSystemIdentityServices().stream()
                    .filter(i -> i.getAuthority().equals(authority))
                    .collect(Collectors.toList());
        }

        IdentityAuthority ia = authorityManager.getIdentityAuthority(authority);
        if (ia != null) {
            return ia.getIdentityServices(realm);
        }

        return null;
    }

    /*
     * Configuration templates
     */

    public Collection<ConfigurableProvider> listProviderConfigurationTemplates(String type) {
        if (SystemKeys.RESOURCE_IDENTITY.equals(type)) {
            // we support only idp templates
            List<ConfigurableProvider> templates = new ArrayList<>();
            for (IdentityAuthority ia : authorityManager.listIdentityAuthorities()) {
                templates.addAll(ia.getConfigurableProviderTemplates());
            }

            return templates;

        }
        return Collections.emptyList();

    }

    public Collection<ConfigurableProvider> listProviderConfigurationTemplates(String realm, String type) {
        Collection<ConfigurableProvider> templates = listProviderConfigurationTemplates(type);
        // keep only those matching realm or with realm == null
        return templates.stream().filter(t -> (t.getRealm() == null
                || realm.equals(t.getRealm())
                || SystemKeys.REALM_GLOBAL.equals(t.getRealm()))).collect(Collectors.toList());

    }

    /*
     * Helpers
     */

    private ConfigurableProvider fromEntity(ProviderEntity pe) {
        ConfigurableProvider cp = new ConfigurableProvider(pe.getAuthority(), pe.getProviderId(), pe.getRealm());
        cp.setType(pe.getType());
        cp.setConfiguration(pe.getConfigurationMap());
        cp.setEnabled(pe.isEnabled());
        cp.setPersistence(pe.getPersistence());

        cp.setName(pe.getName());
        cp.setDescription(pe.getDescription());

        return cp;

    }

}
