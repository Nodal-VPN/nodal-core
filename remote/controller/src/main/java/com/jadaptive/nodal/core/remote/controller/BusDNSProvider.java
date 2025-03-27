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
package com.jadaptive.nodal.core.remote.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;
import com.jadaptive.nodal.core.remote.lib.RemoteDNSEntry;
import com.jadaptive.nodal.core.remote.lib.RemoteDNSProvider;
import com.jadaptive.nodal.core.lib.PlatformService;

public class BusDNSProvider implements DNSProvider {
    
    private final RemoteDNSProvider remote;

    public BusDNSProvider(RemoteDNSProvider remote) {
        this.remote  = remote;
    }

    @Override
    public void init(PlatformService<?> platform) {
    }

    @Override
    public List<DNSEntry> entries() throws IOException {
        var entries = remote.entries();
        return Arrays.asList(entries).stream().map(RemoteDNSEntry::toNative).toList();
    }

    @Override
    public void set(DNSEntry entry) throws IOException {
        remote.set(new RemoteDNSEntry(entry));
    }

    @Override
    public void unset(DNSEntry entry) throws IOException {
        remote.unset(new RemoteDNSEntry(entry));        
    }

    @Override
    public Optional<DNSEntry> entry(String iface) throws IOException {
        try {
            return Optional.of(remote.entry(iface).toNative());
        }
        catch(DBusExecutionException dbe) {
            return Optional.empty();
        }
    }

    @Override
    public void unset(String iface) throws IOException {
        remote.unsetIface(iface);
    }

}
