/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.jadaptive.nodal.core.lib;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import com.sshtools.liftlib.commands.SystemCommands;

public interface SystemContext {

	/**
	 * Will be removed, as it is no longer really needed. It was only ever used by clients of  
	 * nodal-core, which should now just deal with their own queues.
	 * 
	 * @return
	 */
	@Deprecated
    ScheduledExecutorService queue();

    SystemConfiguration configuration();

    void addScriptEnvironmentVariables(VpnAdapter connection, Map<String, String> env);

    /**
     * Get an instance of {@link SystemCommands}, used to execute system commands.
     * 
     * @param args
     */
    SystemCommands commands();

    /**
     * {@link NativeComponents} manages locating, or potentially (temporarily)
     * installing various native tools, such as the <code>wg</code> command, the
     * userspace <code>wireguard-go</code> implementation and more.
     * 
     * @return configuration
     */
    NativeComponents nativeComponents();
    
    void alert(String message, Object... args);
}
