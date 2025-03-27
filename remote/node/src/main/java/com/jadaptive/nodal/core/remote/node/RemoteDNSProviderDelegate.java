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
package com.jadaptive.nodal.core.remote.node;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.remote.lib.RemoteDNSEntry;
import com.jadaptive.nodal.core.remote.lib.RemoteDNSProvider;

public class RemoteDNSProviderDelegate implements RemoteDNSProvider {

    private final DNSProvider delegate;

    public RemoteDNSProviderDelegate(DNSProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getObjectPath() {
        return RemoteDNSProvider.OBJECT_PATH;
    }

    @Override
    public RemoteDNSEntry[] entries() {
        try {
            return delegate.entries().stream().map(RemoteDNSEntry::new).toList().toArray(new RemoteDNSEntry[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RemoteDNSEntry entry(String iface) {
        try {
            return new RemoteDNSEntry(delegate.entry(iface)
                    .orElseThrow(() -> new IllegalArgumentException("No such entry with interface " + iface)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void set(RemoteDNSEntry entry) {
        try {
            delegate.set(entry.toNative());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void unset(RemoteDNSEntry entry) {
        try {
            delegate.unset(entry.toNative());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void unsetIface(String iface) {
        try {
            delegate.unset(iface);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }
}
