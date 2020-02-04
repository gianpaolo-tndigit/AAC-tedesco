/**
 *    Copyright 2015-2019 Smart Community Lab, FBK
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package it.smartcommunitylab.aac;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants and methods for managing resource visibility
 * @author raman
 *
 */
public class Config {

	public static final String DEFAULT_LANG = "en";
	
	/** User name attribute alias */
	public static final String NAME_ATTR = "it.smartcommunitylab.aac.givenname";
	/** User surname attribute alias */
	public static final String SURNAME_ATTR = "it.smartcommunitylab.aac.surname";
	/** User surname attribute alias */
	public static final String USERNAME_ATTR = "it.smartcommunitylab.aac.username";
	/** Internal Attribute Authority */ 
	public static final String IDP_INTERNAL = "internal";
	
	/** Authorization authorities */
	public enum AUTHORITY {ROLE_USER, ROLE_CLIENT, ROLE_ANY, ROLE_CLIENT_TRUSTED};

	/** Claim types */
	public enum CLAIM_TYPE {
		type_string("string"), type_number("number"), type_boolean("boolean"), type_object("object");
		private String litType;
		
		private CLAIM_TYPE(String litType) {
			this.litType = litType;
		}
		public String getLitType() {
			return litType;
		}
		private static final Map<String, CLAIM_TYPE> lookup = new HashMap<>();
		static {
			for (CLAIM_TYPE ct: CLAIM_TYPE.values()) lookup.put(ct.getLitType(), ct);
		}
		public static CLAIM_TYPE get(String s) {
			return lookup.get(s);
		}
	};

	/** Requesting device type */
	public enum DEVICE_TYPE {MOBILE, TABLET, DESKTOP, UNKNOWN, WEBVIEW};
	
	/** operation scope requiring strong 2-factor authentication */
	public static final String SCOPE_OPERATION_CONFIRMED = "operation.confirmed";
	
	/** Session attribute holding AAC OAuth2 request context */
	public static final String SESSION_ATTR_AAC_OAUTH_REQUEST = "aacOAuthRequest";

	public static final String PARAM_REMEMBER_ME= "remember-me";
	public static final String COOKIE_REMEMBER_ME= "aacrememberme";

	
	/** Predefined system role USER */
	public static final String R_USER = "ROLE_USER";
	/** Predefined system role ADMIN */
	public static final String R_ADMIN = "ROLE_ADMIN";
	/** Predefined system role R_PROVIDER */
	public static final String R_PROVIDER = "ROLE_PROVIDER";

	public static final String GRANT_TYPE_NATIVE = "native";
	public static final String GRANT_TYPE_IMPLICIT = "implicit";
	public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    public static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";
    
    public static final String RESPONSE_TYPE_NONE = "none";
    public static final String RESPONSE_TYPE_CODE = "code";
    public static final String RESPONSE_TYPE_TOKEN = "token";
    public static final String RESPONSE_TYPE_ID_TOKEN = "id_token";

	/** Open ID connect scope */
	public static final String SCOPE_OPENID = "openid";
	/** Open Id offline access (refresh token) **/
	public static final String SCOPE_OFFLINE_ACCESS = "offline_access";	
	/** basic scopes */
	public static final String SCOPE_PROFILE = "profile";
    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_PHONE = "phone";
    public static final String SCOPE_ADDRESS = "address";

	public static final String SCOPE_ACCOUNT_PROFILE = "profile.accountprofile.me";
	public static final String SCOPE_ACCOUNT_PROFILE_ALL = "profile.accountprofile.all";
	public static final String SCOPE_BASIC_PROFILE = "profile.basicprofile.me";
	public static final String SCOPE_BASIC_PROFILE_ALL = "profile.basicprofile.all";
    
	/** Predefined scope for user creation and management */
    public static final String SCOPE_USERMANAGEMENT = "usermanagement";
    public static final String SCOPE_ROLE = "user.roles.me";
    public static final String SCOPE_ROLES_READ = "user.roles.read";
    public static final String SCOPE_ROLES_WRITE = "user.roles.write";

    /** scopes for client management */
    public static final String SCOPE_CLIENTMANAGEMENT = "clientmanagement";
    public static final String SCOPE_CLIENT_ROLES_READ_ALL = "client.roles.read.all";

    /** scopes for api management */
    public static final String SCOPE_APIMANAGEMENT = "apimanagement";
    /** scopes for service management */
    public static final String SCOPE_SERVICEMANAGEMENT = "servicemanagement";
    public static final String SCOPE_SERVICEMANAGEMENT_USER = "servicemanagement.me";


    /** scopes for authorization */
    public static final String SCOPE_AUTH_MANAGE = "authorization.manage";
    public static final String SCOPE_AUTH_SCHEMA_MANAGE = "authorization.schema.manage";

	public static final String CLIENT_PARAM_SIGNED_RESPONSE_ALG = "signed_response_alg";
	public static final String CLIENT_PARAM_ENCRYPTED_RESPONSE_ALG = "encrypted_response_alg";
	public static final String CLIENT_PARAM_ENCRYPTED_RESPONSE_ENC = "encrypted_response_enc";
	public static final String CLIENT_PARAM_JWKS = "jwks";
	public static final String CLIENT_PARAM_JWKS_URI = "jwks_uri";

	public static final String SCOPE_ROLEMANAGEMENT = "user.roles.manage.all";
	
	public static final String WELL_KNOWN_URL = "/.well-known";
}
