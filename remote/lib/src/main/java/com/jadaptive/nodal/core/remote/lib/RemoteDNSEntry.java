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

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteDNSEntry extends Struct {

    @Position(0)
    private String iface;
    @Position(1)
    private String[] ipv4Servers;
    @Position(2)
    private String[] ipv6Servers;
    @Position(3)
    private String[] domains;
    
    public RemoteDNSEntry(DNSEntry nativeDnsEntry) {
        this.iface = nativeDnsEntry.iface();
        this.ipv4Servers = nativeDnsEntry.ipv4Servers();
        this.ipv6Servers = nativeDnsEntry.ipv6Servers();
        this.domains = nativeDnsEntry.domains();
    }
    
    public RemoteDNSEntry() {
    }
    
    public RemoteDNSEntry(String iface, String[] ipv4Servers, String[] ipv6Servers, String[] domains) {
        super();
        this.iface = iface;
        this.ipv4Servers = ipv4Servers;
        this.ipv6Servers = ipv6Servers;
        this.domains = domains;
    }

    public String getIface() {
        return iface;
    }

    public String[] getIpv4Servers() {
        return ipv4Servers;
    }

    public String[] getIpv6Servers() {
        return ipv6Servers;
    }

    public String[] getDomains() {
        return domains;
    }

    public DNSEntry toNative() {
        var bldr = new DNSEntry.Builder();
        if(iface != null && iface.length() > 0)
            bldr.withInterface(iface);
        if(ipv4Servers != null && ipv4Servers.length > 0)
            bldr.withIpv4Servers(ipv4Servers);
        if(ipv6Servers != null && ipv6Servers.length > 0)
            bldr.withIpv6Servers(ipv6Servers);
        if(domains != null && domains.length > 0)
            bldr.withDomains(domains);
        return bldr.build();
    }

    
}
