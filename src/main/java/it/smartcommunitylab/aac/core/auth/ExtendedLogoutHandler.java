package it.smartcommunitylab.aac.core.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

//@Component
public class ExtendedLogoutHandler  {
    
    private String[] COOKIE_NAMES = {"JSESSIONID", "csrftoken"};
    
    private SecurityContextLogoutHandler contextLogoutHandler;
    private CookieClearingLogoutHandler cookieLogoutHandler;
    
    public ExtendedLogoutHandler() {
        contextLogoutHandler = new SecurityContextLogoutHandler();
        cookieLogoutHandler = new CookieClearingLogoutHandler(COOKIE_NAMES);

    }
    
//    @Override
//    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
//        // let context process logout
//        contextLogoutHandler.logout(request, response, authentication);
//        
//        // clear cookies
//        // TODO
//    }
}
