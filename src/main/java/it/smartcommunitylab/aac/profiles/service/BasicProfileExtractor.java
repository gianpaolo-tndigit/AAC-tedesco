package it.smartcommunitylab.aac.profiles.service;

import java.util.Collection;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.common.InvalidDefinitionException;
import it.smartcommunitylab.aac.core.model.UserIdentity;
import it.smartcommunitylab.aac.model.User;
import it.smartcommunitylab.aac.profiles.model.BasicProfile;

public class BasicProfileExtractor extends UserProfileExtractor {

    @Override
    public BasicProfile extractUserProfile(User user)
            throws InvalidDefinitionException {

        // fetch identities
        Collection<UserIdentity> identities = user.getIdentities();

        if (identities.isEmpty()) {
            return null;
        }

        // TODO decide how to merge identities into a single profile
        // for now get first identity, should be last logged in
        BasicProfile profile = identities.iterator().next().toBasicProfile();

        return profile;
    }

}