package it.smartcommunitylab.aac.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.core.authorities.AccountServiceAuthority;
import it.smartcommunitylab.aac.core.base.AbstractSingleProviderAuthority;
import it.smartcommunitylab.aac.core.entrypoint.RealmAwareUriBuilder;
import it.smartcommunitylab.aac.core.model.ConfigurableAccountService;
import it.smartcommunitylab.aac.core.provider.FilterProvider;
import it.smartcommunitylab.aac.core.provider.ProviderConfigRepository;
import it.smartcommunitylab.aac.core.provider.UserAccountService;
import it.smartcommunitylab.aac.core.service.TranslatorProviderConfigRepository;
import it.smartcommunitylab.aac.core.service.UserEntityService;
import it.smartcommunitylab.aac.internal.persistence.InternalUserAccount;
import it.smartcommunitylab.aac.internal.provider.InternalAccountServiceConfigurationProvider;
import it.smartcommunitylab.aac.internal.provider.InternalIdentityProviderConfig;
import it.smartcommunitylab.aac.internal.provider.InternalIdentityProviderConfigMap;
import it.smartcommunitylab.aac.internal.provider.InternalAccountService;
import it.smartcommunitylab.aac.internal.provider.InternalAccountServiceConfig;
import it.smartcommunitylab.aac.internal.service.InternalUserConfirmKeyService;
import it.smartcommunitylab.aac.utils.MailService;

@Service
public class InternalAccountServiceAuthority
        extends
        AbstractSingleProviderAuthority<InternalAccountService, InternalUserAccount, ConfigurableAccountService, InternalIdentityProviderConfigMap, InternalAccountServiceConfig>
        implements
        AccountServiceAuthority<InternalAccountService, InternalUserAccount, InternalIdentityProviderConfigMap, InternalAccountServiceConfig> {

    public static final String AUTHORITY_URL = "/auth/internal/";

    // user service
    private final UserEntityService userEntityService;

    // internal account service
    private final UserAccountService<InternalUserAccount> accountService;
    private final InternalUserConfirmKeyService confirmKeyService;

    // configuration provider
    protected InternalAccountServiceConfigurationProvider configProvider;

    private MailService mailService;
    private RealmAwareUriBuilder uriBuilder;

    public InternalAccountServiceAuthority(
            UserEntityService userEntityService,
            UserAccountService<InternalUserAccount> userAccountService, InternalUserConfirmKeyService confirmKeyService,
            ProviderConfigRepository<InternalIdentityProviderConfig> registrationRepository) {
        super(SystemKeys.AUTHORITY_INTERNAL, new InternalConfigTranslatorRepository(registrationRepository));
        Assert.notNull(userEntityService, "user service is mandatory");
        Assert.notNull(userAccountService, "account service is mandatory");
        Assert.notNull(confirmKeyService, "confirm key service is mandatory");

        this.userEntityService = userEntityService;
        this.accountService = userAccountService;
        this.confirmKeyService = confirmKeyService;
    }

    @Autowired
    public void setConfigProvider(InternalAccountServiceConfigurationProvider configProvider) {
        Assert.notNull(configProvider, "config provider is mandatory");
        this.configProvider = configProvider;
    }

    @Autowired
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Autowired
    public void setUriBuilder(RealmAwareUriBuilder uriBuilder) {
        this.uriBuilder = uriBuilder;
    }

    @Override
    public InternalAccountServiceConfigurationProvider getConfigurationProvider() {
        return configProvider;
    }

    @Override
    public String getType() {
        return SystemKeys.RESOURCE_ACCOUNT;
    }

    protected InternalAccountService buildProvider(InternalAccountServiceConfig config) {
        InternalAccountService idp = new InternalAccountService(
                config.getProvider(),
                userEntityService,
                accountService, confirmKeyService,
                config, config.getRealm());

        idp.setMailService(mailService);
        idp.setUriBuilder(uriBuilder);

        return idp;
    }

    @Override
    public FilterProvider getFilterProvider() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InternalAccountService registerProvider(ConfigurableAccountService cp) {
        throw new IllegalArgumentException("direct registration not supported");
    }

    static class InternalConfigTranslatorRepository extends
            TranslatorProviderConfigRepository<InternalIdentityProviderConfig, InternalAccountServiceConfig> {

        public InternalConfigTranslatorRepository(
                ProviderConfigRepository<InternalIdentityProviderConfig> externalRepository) {
            super(externalRepository);
            setConverter((source) -> {
                InternalAccountServiceConfig config = new InternalAccountServiceConfig(source.getProvider(),
                        source.getRealm());
                config.setName(source.getName());
                config.setTitleMap(source.getTitleMap());
                config.setDescriptionMap(source.getDescriptionMap());

                // we share the same configMap
                config.setConfigMap(source.getConfigMap());
                return config;

            });
        }

    }
}
