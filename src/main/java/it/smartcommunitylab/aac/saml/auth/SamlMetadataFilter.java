package it.smartcommunitylab.aac.saml.auth;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.saml.SamlIdentityAuthority;

public class SamlMetadataFilter extends OncePerRequestFilter {

    public static final String DEFAULT_FILTER_URI = SamlIdentityAuthority.AUTHORITY_URL
            + "metadata/{registrationId}";

    private final String authorityId;
    private final Saml2MetadataFilter samlMetadataFilter;

    public SamlMetadataFilter(RelyingPartyRegistrationRepository relyingPartyRegistrationRepository) {
        this(SystemKeys.AUTHORITY_SAML, relyingPartyRegistrationRepository, DEFAULT_FILTER_URI);
    }

    public SamlMetadataFilter(String authority, RelyingPartyRegistrationRepository relyingPartyRegistrationRepository,
            String filterProcessingUrl) {
        Assert.hasText(authority, "authority can not be null or empty");

        this.authorityId = authority;

        // build a converter and a resolver for the filter
        Converter<HttpServletRequest, RelyingPartyRegistration> relyingPartyRegistrationResolver = new DefaultRelyingPartyRegistrationResolver(
                relyingPartyRegistrationRepository);

        samlMetadataFilter = new Saml2MetadataFilter(relyingPartyRegistrationResolver,
                new OpenSamlMetadataResolver());

        RequestMatcher requestMatcher = new AntPathRequestMatcher(filterProcessingUrl, "GET");
        samlMetadataFilter.setRequestMatcher(requestMatcher);
    }

    @Nullable
    protected String getFilterName() {
        return getClass().getName() + "." + authorityId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // delegate
        samlMetadataFilter.doFilter(request, response, filterChain);

    }

}
