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
package com.jadaptive.nodal.core.macos;

import java.util.Optional;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.sshtools.liftlib.OS;

/**
 * Decides which DNS integration to use on Mac. Currently networksetup and 2
 * modes of scutil are supported.
 */
public class MacOsDNSProviderFactory implements DNSProvider.Factory {

    @SuppressWarnings("unchecked")
    @Override
    public <P extends DNSProvider> Class<P>[] available() {
    	if(OS.isMacOs()) {
	        return new Class[] { SCUtilSplitDNSProvider.class, SCUtilCompatibleDNSProvider.class,
	                NetworksetupDNSProvider.class };
    	}
    	else {
    		return new Class[0];
    	}
    }

    @Override
    public DNSProvider create(Optional<Class<? extends DNSProvider>> clazz, SystemContext context) {
        if (clazz.isPresent()) {
            /* Don't use reflection her for native images' sake */
            var clazzVal = clazz.get();
            if (clazzVal.equals(SCUtilSplitDNSProvider.class)) {
                return new SCUtilSplitDNSProvider();
            } else if (clazzVal.equals(SCUtilCompatibleDNSProvider.class)) {
                return new SCUtilCompatibleDNSProvider();
            } else if (clazzVal.equals(NetworksetupDNSProvider.class)) {
                return new NetworksetupDNSProvider();
            } else
                throw new IllegalArgumentException(clazzVal.toString());
        } else {
        	return create(Optional.of(NetworksetupDNSProvider.class), context);
        }
    }

}
