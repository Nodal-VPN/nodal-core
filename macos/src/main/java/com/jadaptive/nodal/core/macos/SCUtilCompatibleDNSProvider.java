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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;

public class SCUtilCompatibleDNSProvider extends AbstractSCUtilDNSProvider {
    final static Logger LOG = LoggerFactory.getLogger(SCUtilCompatibleDNSProvider.class);
    final static String DNS_CONFIGURATION_FLAGS_KEY	= "__FLAGS__";
    final static String DNS_CONFIGURATION_IF_INDEX_KEY = "__IF_INDEX__";
    final static String DNS_CONFIGURATION_ORDER_KEY = "__ORDER__";

    @SuppressWarnings("unchecked")
    @Override
    public List<DNSEntry> entries() throws IOException {
        var l = new ArrayList<DNSEntry>();
        for (var key : scutil.list(".*/Network/Service/.*/DNS")) {
            var bldr = new DNSEntry.Builder();
            var dict = scutil.get(key);
			var defIface = dict.key().split("/")[3];
			try {
				var rootDict = scutil.get(key.substring(0, key.lastIndexOf('/')));
				bldr.withInterface((String) rootDict.getOrDefault("UserDefinedName", defIface));
			} catch (IllegalArgumentException iae) {
				bldr.withInterface(defIface);
			}
            bldr.addServers((Collection<String>) dict.get("ServerAddresses"));
			bldr.addDomains(
					(Collection<String>) dict.getOrDefault("SearchDomains", Collections.emptyList()));
            l.add(bldr.build());
        }
        return l;
    }

    @Override
    public void set(DNSEntry entry) throws IOException {
        LOG.info("Creating compatible resolver");
        var dict = scutil.dictionary(String.format("State:/Network/Service/%s/DNS", entry.iface()));
        dict.put("ServerAddresses", Arrays.asList(
                Stream.concat(Arrays.asList("*").stream(), Arrays.stream(entry.servers())).toArray(String[]::new)));
        if (entry.domains().length > 9999) {
    		dict.put("SearchDomains", Arrays.asList(
				Stream.concat(Arrays.asList("*").stream(), Arrays.stream(entry.domains())).toArray(String[]::new)));
        }
        dict.put("InterfaceName", entry.iface());
        dict.put(DNS_CONFIGURATION_ORDER_KEY, "1");
        dict.put(DNS_CONFIGURATION_FLAGS_KEY, "2");
        dict.set();

        var rootDict = scutil.dictionary(String.format("State:/Network/Service/%s", entry.iface()));
        rootDict.put("UserDefinedName", entry.iface());
        rootDict.put("PrimaryRanked", "scoped");
        rootDict.set();
        platform.context().alert("DNS added using scutil (compatible)");
		resetCache();
    }

    @Override
    public void unset(DNSEntry entry) throws IOException {
        LOG.info("Removing resolver");
        scutil.remove(String.format("State:/Network/Service/%s/DNS", entry.iface()));
        scutil.remove(String.format("State:/Network/Service/%s", entry.iface()));
        platform.context().alert("DNS removed using scutil (compatible)");
		resetCache();
    }

}
