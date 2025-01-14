/*******************************************************************************
 * Copyright 2015-2019 Smart Community Lab, FBK
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
 ******************************************************************************/

package it.smartcommunitylab.aac.common;

import it.smartcommunitylab.aac.SystemKeys;

/**
 * @author raman
 *
 */
public class RegistrationException extends RuntimeException {
    private static final long serialVersionUID = SystemKeys.AAC_COMMON_SERIAL_VERSION;

    public static final String ERROR = "error.registration";

    private final String error;

    public RegistrationException() {
        super();
        this.error = ERROR;
    }

    public RegistrationException(String message) {
        super(message);
        this.error = ERROR;
    }

    public RegistrationException(String error, String message) {
        super(message);
        this.error = error;
    }

    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
        this.error = ERROR;
    }

    public RegistrationException(String error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public String getError() {
        return error;
    }

    @Override
    public String getMessage() {
        if (super.getMessage() != null) {
            return getError().concat(".").concat(super.getMessage());
        }

        return getError();
    }
}
