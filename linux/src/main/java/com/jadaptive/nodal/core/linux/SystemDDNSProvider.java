/**
 * Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
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
package com.jadaptive.nodal.core.linux;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformService;
import com.jadaptive.nodal.core.linux.dbus.Resolve1Manager;
import com.jadaptive.nodal.core.linux.dbus.Resolve1Manager.RootDNSPropertyStruct;
import com.jadaptive.nodal.core.linux.dbus.Resolve1Manager.RootDomainsPropertyStruct;
import com.sshtools.liftlib.ElevatedClosure;

import uk.co.bithatch.nativeimage.annotations.Serialization;

public class SystemDDNSProvider implements DNSProvider {
    private final static Logger LOG = LoggerFactory.getLogger(SystemDDNSProvider.class);

    private static final String RESOLVE1_BUS_NAME = "org.freedesktop.resolve1";

    private PlatformService<?> platform;

    @Override
    public void init(PlatformService<?> platform) {
        this.platform = platform;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DNSEntry> entries() throws IOException {
        var l = new ArrayList<DNSEntry>();
        try (var conn = DBusConnectionBuilder.forSystemBus().build()) {

            var mgr = conn.getRemoteObject(RESOLVE1_BUS_NAME, "/org/freedesktop/resolve1", Resolve1Manager.class);
            var props = conn.getRemoteObject(RESOLVE1_BUS_NAME, mgr.getObjectPath(), Properties.class);
            var domains = (ArrayList<Object[]>) props.Get("org.freedesktop.resolve1.Manager", "Domains");
            var dns = (ArrayList<Object[]>) props.Get("org.freedesktop.resolve1.Manager", "DNS");
            var dnsMap = new HashMap<String, DNSEntry.Builder>();
            for (var arr : dns) {
                var obj = new RootDNSPropertyStruct(arr);
                try {
                    var name = indexToName(obj.getIndex());
                    var bldr = dnsMap.get(name);
                    if (bldr == null) {
                        bldr = new DNSEntry.Builder();
                        bldr.withInterface(name);
                        dnsMap.put(name, bldr);
                    }
                    bldr.addServers(LinuxPlatformServiceFactory.bytesToIpAddress(obj.getAddress()));

                } catch (Exception e) {
                    LOG.debug("Skipping {}, error occurred.", obj.getIndex(), e);
                }
            }
            for (var arr : domains) {
                var obj = new RootDomainsPropertyStruct(arr);
                try {
                    var name = indexToName(obj.getIndex());
                    var bldr = dnsMap.get(name);
                    if (bldr == null) {
                        bldr = new DNSEntry.Builder();
                        bldr.withInterface(name);
                        dnsMap.put(name, bldr);
                    }
                    bldr.addDomains(obj.getDomain());

                } catch (Exception e) {
                    LOG.debug("Skipping {}, error occurred.", obj.getIndex(), e);
                }
            }

            for (var entry : dnsMap.entrySet()) {
                l.add(entry.getValue().build());
            }

        } catch (DBusException dbe) {
            throw new IOException("Failed to connect to system bus.", dbe);
        }
        return l;
    }

    @Override
    public void set(DNSEntry entry) throws IOException {
        try {
            platform.context().commands().privileged()
                    .task(new UpdateSystemD(entry.servers(), entry.domains(), nameToIndex(entry.iface())));
        } catch (IOException e) {
            throw e;
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        } catch (Exception e) {
            throw new IOException("Failed to set DNS.", e);
        }
    }

    @Override
    public void unset(DNSEntry entry) throws IOException {
        try {
            platform.context().commands().privileged()
                    .task(new UpdateSystemD(new String[0], new String[0], nameToIndex(entry.iface())));
        } catch (IOException e) {
            throw e;
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        } catch (Exception e) {
            throw new IOException("Failed to set DNS.", e);
        }

    }

    private String indexToName(int index) {
        try {
            return NetworkInterface.getByIndex(index).getName();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to get interface for index " + index + ".", e);
        }
    }

    private int nameToIndex(String name) {
        try {
            return NetworkInterface.getByName(name).getIndex();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to get interface for name " + name + ".", e);
        }
    }

    @SuppressWarnings("serial")
    @Serialization
    public final static class UpdateSystemD implements ElevatedClosure<Serializable, Serializable> {

        private String[] dns;
        private String[] domains;
        private int index;

        public UpdateSystemD() {
        }

        UpdateSystemD(String[] dns, String[] domains, int index) {
            this.dns = dns;
            this.domains = domains;
            this.index = index;
        }

        @Override
        public Serializable call(ElevatedClosure<Serializable, Serializable> proxy) throws Exception {
            try (var conn = DBusConnectionBuilder.forSystemBus().build()) {

                var mgr = conn.getRemoteObject(RESOLVE1_BUS_NAME, "/org/freedesktop/resolve1", Resolve1Manager.class);

                if (dns.length == 0) {
                    LOG.info(String.format("Reverting DNS via SystemD. Index is %d", index));
                    mgr.RevertLink(index);
                } else {
                    LOG.info(String.format("Setting DNS via SystemD. Index is %d", index));
                    mgr.SetLinkDNS(index, Arrays.asList(dns).stream()
                            .map((addr) -> new Resolve1Manager.SetLinkDNSStruct(addr)).collect(Collectors.toList()));
                    mgr.SetLinkDomains(index,
                            Arrays.asList(domains).stream()
                                    .map((addr) -> new Resolve1Manager.SetLinkDomainsStruct(addr, false))
                                    .collect(Collectors.toList()));
                }
                return null;

            } catch (DBusException dbe) {
                throw new IOException("Failed to connect to system bus.", dbe);
            }
        }
    }
}
