package it.smartcommunitylab.aac.core;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import it.smartcommunitylab.aac.common.NoSuchScopeException;
import it.smartcommunitylab.aac.scope.Scope;
import it.smartcommunitylab.aac.scope.ScopeRegistry;
import it.smartcommunitylab.aac.scope.ScopeService;

@Service
public class ScopeManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private ScopeRegistry scopeRegistry;

    public Collection<Scope> listScopes() {
        // fetch from registry
        return scopeRegistry.listScopes();
    }

    public Scope getScope(String scope) throws NoSuchScopeException {
        // from registry
        Scope s = scopeRegistry.findScope(scope);
        if (s == null) {
            throw new NoSuchScopeException();
        }

        return s;
    }

    public Scope addScope(Scope scope) {
        // TODO validate
        validateScope(scope);

        // persist
        Scope s = scopeService.addScope(scope.getScope(), scope.getResourceId(), scope.getName(),
                scope.getDescription(), scope.getType());

        // add to registry
        scopeRegistry.registerScope(s);

        return s;

    }

    public Scope updateScope(String scope, Scope s) throws NoSuchScopeException {
        // TODO validate
        validateScope(s);

        // unregister
        scopeRegistry.unregisterScope(scope);

        // persist
        s = scopeService.updateScope(scope, s.getName(),
                s.getDescription(), s.getType());

        // re-add to registry
        scopeRegistry.registerScope(s);

        return s;

    }

    public void deleteScope(String scope) {
        // unregister
        scopeRegistry.unregisterScope(scope);

        // remove from db
        scopeService.deleteScope(scope);

    }

    private void validateScope(Scope scope) {
        // TODO

    }

}