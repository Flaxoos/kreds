/*
 *  Copyright (C) 2022 Abhijith Shivaswamy
 *   See the notice.md file distributed with this work for additional
 *   information regarding copyright ownership.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.github.crackthecodeabhi.kreds.connection

public class KredsClientConfig {

    public companion object {
        public const val NO_READ_TIMEOUT: Int = -1
    }

    public var connectTimeOutMillis: Int? = null

    public var soKeepAlive: Boolean = false

    /**
     * In Subscriber client, readTimeout can be set to -1, to never timeout from reading from subscription connection.
     */
    public var readTimeoutSeconds: Int = NO_READ_TIMEOUT

}

internal val defaultClientConfig: KredsClientConfig = KredsClientConfig()

internal val defaultSubscriberClientConfig: KredsClientConfig =
    KredsClientConfig().apply { readTimeoutSeconds = KredsClientConfig.NO_READ_TIMEOUT}

internal val defaultBlockingKredsClientConfig: KredsClientConfig =
    KredsClientConfig().apply { readTimeoutSeconds = KredsClientConfig.NO_READ_TIMEOUT}
