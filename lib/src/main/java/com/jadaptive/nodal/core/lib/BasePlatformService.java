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

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;

public abstract class BasePlatformService<I extends VpnAddress> implements PlatformService<I> {

    final static Logger LOG = LoggerFactory.getLogger(BasePlatformService.class);
    
    @Override
    public final void stop(VpnConfiguration configuration, VpnAdapter session) throws IOException {
        try {

            LOG.info("Stopping VPN for {}", session.address().shortName());
            
            try {
                if(!configuration.addresses().isEmpty()) {
                    var dnsOr = dns();
                    if(dnsOr.isPresent()) {
                        dnsOr.get().unset(new DNSEntry.Builder().fromConfiguration(configuration).withInterface(session.address().nativeName()).build());
                    }
                }
            }
            finally {

                try {
                    if(configuration.preDown().length > 0) {
                        var p = configuration.preDown();
                        LOG.info("Running pre-down commands. {}", String.join(" ; ", p).trim());
                        runHook(configuration, session, p);
                    }
                }
                finally {
                    session.close();
                }
            }
        } finally {
            try {
                onStop(configuration, session);
            } finally {
                if(configuration.postDown().length > 0) {
                    var p = configuration.postDown();
                    LOG.info("Running post-down commands. {}", String.join(" ; ", p).trim());
                    runHook(configuration, session, p);
                }
            }
        }
        
    }

    @Override
    public final VpnAdapter adapter(String nativeName) {
        return findAdapter(nativeName, adapters()).orElseThrow(() -> new IllegalArgumentException(String.format("No adapter %s", nativeName)));
    }

    protected void onStop(VpnConfiguration configuration, VpnAdapter session) {
        
    }

    protected boolean exists(String nativeName, Iterable<I> links) {
        try {
            return find(nativeName, links).isPresent();
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }

    protected final Optional<I> find(String nativeName, Iterable<I> links) {
        for (var link : links)
            if (Objects.equals(nativeName, link.nativeName()))
                return Optional.of(link);
        return Optional.empty();
    }

    protected final Optional<VpnAdapter> findAdapter(String nativeName, Iterable<VpnAdapter> links) {
        for (var link : links)
            if (Objects.equals(nativeName, link.address().nativeName()))
                return Optional.of(link);
        return Optional.empty();
    }
}
