package it.smartcommunitylab.aac.profiles.service;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import it.smartcommunitylab.aac.common.InvalidDefinitionException;
import it.smartcommunitylab.aac.core.model.Attribute;
import it.smartcommunitylab.aac.core.model.UserAttributes;
import it.smartcommunitylab.aac.core.model.UserIdentity;
import it.smartcommunitylab.aac.model.User;
import it.smartcommunitylab.aac.profiles.model.AbstractProfile;

/*
 * Profile extractors are converters which take a user and build a profile according to a given schema
 * 
 * Core implementations leverage a pre-made schema but implementations can extend the model.
 * Do note that not all method are required, implementations could choose to return one or more.
 */
public abstract class UserProfileExtractor {

    /*
     * Get the profile from the given identity, where possible
     */
    public abstract AbstractProfile extractUserProfile(UserIdentity identity)
            throws InvalidDefinitionException;

    /*
     * Get the profile from the default/primary identity, or from all identities
     * plus attributes We don't enforce implementations to choose an extraction
     * policy.
     * 
     * This method is *required* to return a valid profile.
     */
    public abstract AbstractProfile extractUserProfile(User user)
            throws InvalidDefinitionException;

    /*
     * Get a collection of profiles, where the user can be represented by more than
     * one. For example in case of multiple identities.
     */

    public abstract Collection<? extends AbstractProfile> extractUserProfiles(User user)
            throws InvalidDefinitionException;

    // lookup an attribute in multiple sets, return first match
    protected String getAttribute(Collection<UserAttributes> attributes, String key, String... identifier) {
        Set<UserAttributes> sets = attributes.stream()
                .filter(a -> ArrayUtils.contains(identifier, a.getIdentifier()))
                .collect(Collectors.toSet());

        for (UserAttributes uattr : sets) {
            Optional<Attribute> attr = uattr.getAttributes().stream().filter(a -> a.getKey().equals(key)).findFirst();
            if (attr.isPresent()) {
                return attr.get().getValue().toString();
            }
        }

        return null;

    }
}
