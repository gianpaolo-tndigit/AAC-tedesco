/*
 * Copyright 2023 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylab.aac.password.auth;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.accounts.persistence.UserAccountService;
import it.smartcommunitylab.aac.internal.model.InternalUserAccount;
import it.smartcommunitylab.aac.password.provider.PasswordIdentityCredentialsService;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class UsernamePasswordAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final UserAccountService<InternalUserAccount> userAccountService;
    private final PasswordIdentityCredentialsService passwordService;

    private final String providerId;
    private final String repositoryId;

    public UsernamePasswordAuthenticationProvider(
        String providerId,
        UserAccountService<InternalUserAccount> userAccountService,
        PasswordIdentityCredentialsService passwordService,
        String repositoryId,
        String realm
    ) {
        Assert.hasText(providerId, "provider can not be null or empty");
        Assert.notNull(userAccountService, "account service is mandatory");
        Assert.notNull(passwordService, "password service is mandatory");
        Assert.hasText(repositoryId, "repository id can not be null or empty");

        this.userAccountService = userAccountService;
        this.passwordService = passwordService;

        this.providerId = providerId;
        this.repositoryId = repositoryId;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(
            UsernamePasswordAuthenticationToken.class,
            authentication,
            "Only UsernamePasswordAuthenticationToken is supported"
        );

        UsernamePasswordAuthenticationToken authRequest = (UsernamePasswordAuthenticationToken) authentication;

        String username = authRequest.getUsername();
        String password = authRequest.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BadCredentialsException("missing required parameters in request");
        }

        try {
            InternalUserAccount account = userAccountService.findAccountById(repositoryId, username);
            if (account == null) {
                throw new BadCredentialsException("invalid request");
            }

            // delegate password check
            if (!passwordService.verifyPassword(username, password)) {
                throw new BadCredentialsException("invalid request");
            }

            // set ourselves as provider
            account.setAuthority(SystemKeys.AUTHORITY_PASSWORD);
            account.setProvider(providerId);

            // always grant user role
            // we really don't have any additional role on accounts, aac roles are set on
            // subject
            Set<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority(Config.R_USER));

            // build a valid token
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username,
                password,
                account,
                authorities
            );

            return auth;
        } catch (Exception e) {
            logger.error(e.getMessage());
            // don't leak
            throw new BadCredentialsException("invalid request");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
