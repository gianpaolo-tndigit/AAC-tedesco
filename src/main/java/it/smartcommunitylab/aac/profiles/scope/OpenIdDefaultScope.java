package it.smartcommunitylab.aac.profiles.scope;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.model.ScopeType;
import it.smartcommunitylab.aac.profiles.model.ProfileClaimsSet;
import it.smartcommunitylab.aac.scope.Scope;

public class OpenIdDefaultScope extends AbstractProfileScope {

    @Override
    public String getScope() {
        return Config.SCOPE_PROFILE;
    }

    // TODO replace with keys for i18n
    @Override
    public String getName() {
        return "Read user's standard profile";
    }

    @Override
    public String getDescription() {
        return "Basic user profile data (name, surname, email). Read access only.";
    }

}
