package it.smartcommunitylab.aac.profiles.claims;

import java.util.Collection;

import org.springframework.stereotype.Component;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.common.InvalidDefinitionException;
import it.smartcommunitylab.aac.model.User;
import it.smartcommunitylab.aac.profiles.model.AbstractProfile;
import it.smartcommunitylab.aac.profiles.model.OpenIdProfile;
import it.smartcommunitylab.aac.profiles.service.OpenIdProfileExtractor;

@Component
public class OpenIdAddressProfileClaimsExtractor extends ProfileClaimsExtractor {
    
    private final OpenIdProfileExtractor extractor;

    public OpenIdAddressProfileClaimsExtractor() {
        this.extractor = new OpenIdProfileExtractor();
    }

    @Override
    public String getScope() {
        return Config.SCOPE_ADDRESS;
    }

    @Override
    protected AbstractProfile buildUserProfile(User user, Collection<String> scopes)
            throws InvalidDefinitionException {

        if (!scopes.contains(Config.SCOPE_OPENID)) {
            return null;
        }

        OpenIdProfile profile = extractor.extractUserProfile(user);

        // narrow down
        return profile.toAddressProfile();
    }

}