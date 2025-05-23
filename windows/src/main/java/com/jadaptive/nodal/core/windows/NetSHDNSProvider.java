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
package com.jadaptive.nodal.core.windows;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformService;
import com.jadaptive.nodal.core.lib.util.OsUtil;
import com.jadaptive.nodal.core.lib.util.Util;
import com.sshtools.liftlib.ElevatedClosure;
import com.sshtools.liftlib.commands.SystemCommands.ProcessRedirect;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import uk.co.bithatch.nativeimage.annotations.Serialization;

@Deprecated
public class NetSHDNSProvider implements DNSProvider {
    private final static Logger LOG = LoggerFactory.getLogger(NetSHDNSProvider.class);

    private PlatformService<?> platform;

    @Override
    public void init(PlatformService<?> platform) {
        this.platform = platform;
    }

    @Override
    public List<DNSEntry> entries() throws IOException {
        DNSEntry.Builder bldr = null;
        var entries = new ArrayList<DNSEntry>();
        for(var line : platform.context().commands().logged().output("netsh", "interface", "ipv4", "show", "dnsservers")) {
            line = line.trim();

            if(line.endsWith("\"")) {
                if(bldr != null) {
                    entries.add(bldr.build());
                }
                
                var idx = line.lastIndexOf('"', line.length() - 2);
                var name =  line.substring(idx + 1, idx);
                bldr = new DNSEntry.Builder();
                bldr.withInterface(name);
            }
            else {
                if(line.startsWith("Register "))
                    continue;
                else {
                    var arr = line.split("\\s+");
                    try {
                        var addr = InetAddress.getByName(arr[arr.length - 1]);
                        bldr.addServers(addr.getHostAddress());
                    }
                    catch(Exception e) {
                    }
                }
            }
            
        }
        if(bldr != null) {
            entries.add(bldr.build());
        }
        return entries;
    }

    @Override
    public void set(DNSEntry entry) throws IOException {
        var commands = platform.context().commands();
        var name = entry.iface();
        
        /* Ipv4 */
        var dnsAddresses = entry.ipv4Servers();
        if (dnsAddresses.length > 2) {
            LOG.warn(
                    "Windows only supports a maximum of 2 DNS servers. {} were supplied, the last {} will be ignored.",
                    dnsAddresses.length, dnsAddresses.length - 2);
        }

        commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv4",
                "delete", "dnsservers", name, "all"));
        if (dnsAddresses.length > 0) {
            commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv4",
                    "add", "dnsserver", name, dnsAddresses[0], "index=1", "no"));
        }
        if (dnsAddresses.length > 1) {
            commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv4",
                    "add", "dnsserver", name, dnsAddresses[1], "index=2", "no"));
        }

        /* Ipv6 */
        dnsAddresses = entry.ipv6Servers();
        if (dnsAddresses.length > 2) {
            LOG.warn(
                    "Windows only supports a maximum of 2 DNS servers. {} were supplied, the last {} will be ignored.",
                    dnsAddresses.length, dnsAddresses.length - 2);
        }

        commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv6",
                "delete", "dnsservers", name, "all"));
        if (dnsAddresses.length > 0) {
            commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv6",
                    "add", "dnsserver", name, dnsAddresses[0], "index=1", "no"));
        }
        if (dnsAddresses.length > 1) {
            commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv6",
                    "add", "dnsserver", name, dnsAddresses[1], "index=2", "no"));
        }

        try {
            commands.privileged().logged().task(new SetDomainSearch(entry.domains()));
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
        
        var commands = platform.context().commands();
        var name = entry.iface();
        
        /* Ipv4 */
        commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv4",
                "delete", "dnsservers", name, "all"));

        /* Ipv6 */
        commands.privileged().logged().stdout(ProcessRedirect.DISCARD).result(OsUtil.debugCommandArgs("netsh", "interface", "ipv6",
                "delete", "dnsservers", name, "all"));
        
        try {
            platform.context().commands().privileged().task(new UnsetDomainSearch(entry.domains()));
        } catch (IOException e) {
            throw e;
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        } catch (Exception e) {
            throw new IOException("Failed to set DNS.", e);
        }
    }

    @SuppressWarnings("serial")
    @Serialization
    public final static class SetDomainSearch implements ElevatedClosure<Serializable, Serializable> {

        private String[] domains;

        public SetDomainSearch() {
        }

        SetDomainSearch(String[] domains) {
            this.domains = domains;
        }

        @Override
        public Serializable call(ElevatedClosure<Serializable, Serializable> proxy) throws Exception {
            String currentDomains = null;
            try {
                currentDomains = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList");
            } catch (Exception e) {
                //
            }
            var newDomainList = new LinkedHashSet<String>(Util.isBlank(currentDomains) ? Collections.emptySet()
                    : Arrays.asList(currentDomains.split(",")));
            for (var dnsName : domains) {
                if (!newDomainList.contains(dnsName)) {
                    LOG.info("Adding domain {} to search", dnsName);
                    newDomainList.add(dnsName);
                }
            }
            var newDomains = String.join(",", newDomainList);
            if (!Objects.equals(currentDomains, newDomains)) {
                LOG.info("Final domain search {}", newDomains);
                Advapi32Util.registrySetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList", newDomains);
            }
            
            return null;
        }
    }

    @SuppressWarnings("serial")
    @Serialization
    public final static class UnsetDomainSearch implements ElevatedClosure<Serializable, Serializable> {

        private String[] domains;

        public UnsetDomainSearch() {
        }

        UnsetDomainSearch(String[] domains) {
            this.domains = domains;
        }

        @Override
        public Serializable call(ElevatedClosure<Serializable, Serializable> proxy) throws Exception {
            String currentDomains;
            try {
                currentDomains = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                    "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList");
            }
            catch(Exception e) {
                LOG.debug("Failed to get current domain search list.", e);
                currentDomains = null;
            }
            var currentDomainList = new LinkedHashSet<String>(
                    Util.isBlank(currentDomains) ? Collections.emptySet() : Arrays.asList(currentDomains));
            for (var dnsName : domains) {
                LOG.info(String.format("Removing domain %s from search", dnsName));
                currentDomainList.remove(dnsName);
            }
            
            var newDomains = String.join(",", currentDomainList);
            if (!Objects.equals(currentDomains, newDomains)) {
                LOG.info("Final domain search {}", newDomains);
                Advapi32Util.registrySetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList", newDomains);
            }
            
            return null;
        }
    }
}
