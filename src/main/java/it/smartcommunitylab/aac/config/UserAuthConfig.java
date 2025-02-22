package it.smartcommunitylab.aac.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import it.smartcommunitylab.aac.core.AuthorityManager;
import it.smartcommunitylab.aac.core.ExtendedUserAuthenticationManager;
import it.smartcommunitylab.aac.core.service.IdentityProviderAuthorityService;
import it.smartcommunitylab.aac.core.service.SubjectService;
import it.smartcommunitylab.aac.core.service.UserEntityService;

@Configuration
@Order(15)
public class UserAuthConfig {

    @Autowired
    private AuthorityManager authorityManager;

    @Autowired
    private IdentityProviderAuthorityService identityProviderAuthorityService;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private UserEntityService userService;

    @Bean
    public ExtendedUserAuthenticationManager extendedAuthenticationManager() throws Exception {
        return new ExtendedUserAuthenticationManager(authorityManager, identityProviderAuthorityService, userService,
                subjectService);
    }

}
