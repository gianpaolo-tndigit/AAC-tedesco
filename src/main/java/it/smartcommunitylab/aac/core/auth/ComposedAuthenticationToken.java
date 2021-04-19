package it.smartcommunitylab.aac.core.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

/*
 * An auth token for authenticated user and client
 */
public class ComposedAuthenticationToken extends AbstractAuthenticationToken {

    private static final long serialVersionUID = -2640624036104536421L;

    private final UserAuthenticationToken userAuthentication;
    private final ClientAuthenticationToken clientAuthentication;

    public ComposedAuthenticationToken(
            UserAuthenticationToken userAuthentication,
            ClientAuthenticationToken clientAuthentication) {
        super(buildAuthorities(userAuthentication, clientAuthentication));
        Assert.notNull(userAuthentication, "user authentication can not be null");
        Assert.notNull(clientAuthentication, "client authentication can not be null");

        this.userAuthentication = userAuthentication;
        this.clientAuthentication = clientAuthentication;

        super.setAuthenticated(userAuthentication.isAuthenticated() && clientAuthentication.isAuthenticated());
    }

    public UserAuthenticationToken getUserAuthentication() {
        return userAuthentication;
    }

    public ClientAuthenticationToken getClientAuthentication() {
        return clientAuthentication;
    }

    public String getUserId() {
        return userAuthentication.getSubjectId();
    }

    public String getClientId() {
        return clientAuthentication.getClientId();
    }

    @Override
    public String getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this.userAuthentication.getPrincipal();
    }

    private static Collection<? extends GrantedAuthority> buildAuthorities(UserAuthenticationToken userAuthentication,
            ClientAuthenticationToken clientAuthentication) {
        // build an immutable set of authorities
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (userAuthentication != null && userAuthentication.isAuthenticated()) {
            authorities.addAll(userAuthentication.getAuthorities());
        }
        if (clientAuthentication != null && clientAuthentication.isAuthenticated()) {
            authorities.addAll(clientAuthentication.getAuthorities());
        }

        return Collections.unmodifiableList(new ArrayList<>(authorities));

    }

}
