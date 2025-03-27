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
package com.jadaptive.nodal.core.remote.lib;

import org.freedesktop.dbus.interfaces.DBusInterface;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
public interface RemoteDNSProvider extends DBusInterface {
    String DBUS_INTERFACE_NAME = "com.jadaptive.nodal.core.RemoteDNSProvider";
    String OBJECT_PATH = "/" + DBUS_INTERFACE_NAME.replace('.', '/');
    
    /**
     * Get all current DNS configuration. See {@link DNSProvider#entries()}.
     * 
     * @return dns configuration entries
     */
    RemoteDNSEntry[] entries();

    /**
     * Get an entry given the interface name. See {@link DNSProvider#entry(String)}.
     * 
     * @param iface interface name
     * @return dns entry
     */
    RemoteDNSEntry entry(String iface);

    /**
     * Make the provided DNS configuration active. See {@link DNSProvider#set(DNSEntry)}.
     * 
     * @param entry DNS configuration
     */
    void set(RemoteDNSEntry entry);

    /**
     * Unset the provided DNS configuration (make it inactive). See {@link DNSProvider#unset(DNSEntry)}.
     * 
     * @param entry DNS configuration to deactivate.
     */
    void unset(RemoteDNSEntry entry);

    /**
     * Unset any configured DNS given the interface name. See {@link DNSProvider#unset(String)}.
     * 
     * @param iface
     */
    void unsetIface(String iface);
}
