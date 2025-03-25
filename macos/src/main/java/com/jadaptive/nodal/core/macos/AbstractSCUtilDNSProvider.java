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
package com.jadaptive.nodal.core.macos;

import static com.jadaptive.nodal.core.lib.util.OsUtil.debugCommandArgs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformService;

public abstract class AbstractSCUtilDNSProvider implements DNSProvider {
    final static Logger LOG = LoggerFactory.getLogger(AbstractSCUtilDNSProvider.class);

    protected PlatformService<?> platform;
    protected SCUtil scutil;

    @Override
    public void init(PlatformService<?> platform) {
        this.platform = platform;   
        scutil = new SCUtil(platform);
    }

    @Override
    public List<DNSEntry> entries() throws IOException {
        var l = new ArrayList<DNSEntry>();
        DNSEntry.Builder bldr = null;
        for (var line : platform.context().commands().output(debugCommandArgs("scutil", "--dns"))) {
            line = line.trim();
            if(line.startsWith("resolver ")) {
                bldr = new DNSEntry.Builder();
            }
            else if(bldr != null && line.startsWith("search domain")) {
                bldr.addDomains(line.split(":")[1].trim());
            }
            else if(bldr != null && line.startsWith("nameserver[")) {
                bldr.addServers(line.split(":")[1].trim());
            }
            else if(bldr != null && line.startsWith("if_index")) {
                var iface = line.split(" ")[3].trim();
                bldr.withInterface(iface.substring(1, iface.length() - 1));
                l.add(bldr.build());
            }
        }
        
        return l;
    }

	protected void resetCache() throws IOException {
		platform.context().commands().privileged().logged().result("dscacheutil", "-flushcache");
        platform.context().commands().privileged().logged().result("killall", "-HUP", "mDNSResponder");
	}
}
