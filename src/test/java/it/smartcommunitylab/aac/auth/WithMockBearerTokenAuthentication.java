/*
 * Copyright 2023 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylab.aac.auth;

import it.smartcommunitylab.aac.Config;
import it.smartcommunitylab.aac.SystemKeys;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.security.test.context.support.TestExecutionEvent;
import org.springframework.security.test.context.support.WithSecurityContext;

/*
 * Mock authentication in security context as bearer token
 * Use as annotation on methods/classes to inject a mocked authentication token
 *
 * This class can be used with resource server/token introspector configs without
 * mock components as long as the HTTP Authorization header is avoided in requests:
 *  - the bearer token resolver will skip processing due to missing header
 *  - the introspector won't execute, leaving no authentication in the security context
 *  - the factory will build a new security context for the current invocation
 *  - the authorization interceptor will read from the context the mocked auth
 *
 *  By default the annotation will produce an authentication for
 *  - sub "000"
 *  - ROLE_USER
 *  - realm:system
 *  - no scopes
 */

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WithSecurityContext(factory = MockBearerTokenAuthenticationFactory.class)
public @interface WithMockBearerTokenAuthentication {
    @AliasFor("value")
    String subject() default "00000000-0000-0000-0000-000000000000";

    @AliasFor("subject")
    String value() default "00000000-0000-0000-0000-000000000000";

    String realm() default SystemKeys.REALM_SYSTEM;

    String[] authorities() default { Config.R_USER };

    String[] scopes() default {};

    @AliasFor(annotation = WithSecurityContext.class)
    TestExecutionEvent setupBefore() default TestExecutionEvent.TEST_METHOD;

    String token() default "mocktoken00000000-0000";
}
