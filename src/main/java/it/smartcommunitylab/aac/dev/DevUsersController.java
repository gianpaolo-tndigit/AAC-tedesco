package it.smartcommunitylab.aac.dev;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.SystemKeys;
import it.smartcommunitylab.aac.common.NoSuchProviderException;
import it.smartcommunitylab.aac.common.NoSuchRealmException;
import it.smartcommunitylab.aac.common.NoSuchUserException;
import it.smartcommunitylab.aac.core.UserManager;
import it.smartcommunitylab.aac.model.User;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@ApiIgnore
@RequestMapping("/console/dev")
public class DevUsersController {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserManager userManager;

    /*
     * Users
     */
    @GetMapping("/realms/{realm}/users")
    @PreAuthorize("hasAuthority('" + Config.R_ADMIN + "') or hasAuthority(#realm+':ROLE_ADMIN')")
    public ResponseEntity<Page<User>> getRealmUsers(
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String realm,
            @RequestParam(required = false) String q, Pageable pageRequest) throws NoSuchRealmException {
        return ResponseEntity.ok(userManager.searchUsers(realm, q, pageRequest));
    }

    @GetMapping("/realms/{realm}/users/{subjectId:.*}")
    @PreAuthorize("hasAuthority('" + Config.R_ADMIN + "') or hasAuthority(#realm+':ROLE_ADMIN')")
    public ResponseEntity<User> getRealmUser(
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String realm,
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String subjectId)
            throws NoSuchRealmException, NoSuchUserException {
        User user = userManager.getUser(realm, subjectId);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/realms/{realm}/users/{subjectId:.*}")
    @PreAuthorize("hasAuthority('" + Config.R_ADMIN + "') or hasAuthority(#realm+':ROLE_ADMIN')")
    public ResponseEntity<Void> deleteRealmUser(
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String realm,
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String subjectId)
            throws NoSuchRealmException, NoSuchUserException {
        User curUser = userManager.curUser(realm);
        if (curUser.getSubjectId().equals(subjectId)) {
            throw new IllegalArgumentException("Cannot delete current user");
        }
        userManager.removeUser(realm, subjectId);
        return ResponseEntity.ok(null);
    }

    @PutMapping("/realms/{realm}/users/{subjectId:.*}/roles")
    @PreAuthorize("hasAuthority('" + Config.R_ADMIN + "') or hasAuthority(#realm+':ROLE_ADMIN')")
    public ResponseEntity<User> updateRealmUserRoles(
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String realm,
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String subjectId,
            @RequestBody RolesBean bean) throws NoSuchRealmException, NoSuchUserException {
        userManager.updateRealmAuthorities(realm, subjectId, bean.getRoles());
        return ResponseEntity.ok(userManager.getUser(realm, subjectId));
    }

    @PostMapping("/realms/{realm}/users/invite")
    @PreAuthorize("hasAuthority('" + Config.R_ADMIN + "') or hasAuthority(#realm+':ROLE_ADMIN')")
    public ResponseEntity<Void> inviteRealmUser(
            @PathVariable @Valid @Pattern(regexp = SystemKeys.SLUG_PATTERN) String realm,
            @RequestBody InvitationBean bean)
            throws NoSuchRealmException, NoSuchUserException, NoSuchProviderException {
        userManager.inviteUser(realm, bean.getUsername(), bean.getSubjectId(), bean.getRoles());
        return ResponseEntity.ok(null);
    }

    /*
     * DTO
     */
    public static class RolesBean {

        private List<String> roles;

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

    }

    public static class InvitationBean {

        private String username, subjectId;

        private List<String> roles;

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(String subjectId) {
            this.subjectId = subjectId;
        }

    }

}
