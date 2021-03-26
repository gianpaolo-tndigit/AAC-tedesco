package it.smartcommunitylab.aac.claims;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class DefaultClaimsSet implements ClaimsSet {

    private String resourceId;
    private String scope;
    private String namespace;

    private boolean isUser = false;
    private boolean isClient = false;

    private Map<String, Serializable> claims = Collections.emptyMap();

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean isUser) {
        this.isUser = isUser;
    }

    public boolean isClient() {
        return isClient;
    }

    public void setClient(boolean isClient) {
        this.isClient = isClient;
    }

    public Map<String, Serializable> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, Serializable> claims) {
        this.claims = claims;
    }

}
