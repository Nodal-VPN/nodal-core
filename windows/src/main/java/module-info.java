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
import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformServiceFactory;
import com.jadaptive.nodal.core.windows.WindowsDNSProviderFactory;
import com.jadaptive.nodal.core.windows.WindowsPlatformServiceFactory;

open module com.jadaptive.nodal.core.os {
    exports com.jadaptive.nodal.core.windows;
    requires transitive com.jadaptive.nodal.core.lib; 
    requires transitive org.slf4j;
    requires com.sshtools.liftlib;
    requires transitive com.sun.jna.platform;
    requires transitive java.prefs;
    requires transitive com.sun.jna;
    requires static uk.co.bithatch.nativeimage.annotations;
    
    provides PlatformServiceFactory with WindowsPlatformServiceFactory;
    provides DNSProvider.Factory with WindowsDNSProviderFactory;
}