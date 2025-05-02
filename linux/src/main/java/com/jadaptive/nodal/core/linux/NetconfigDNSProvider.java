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
package com.jadaptive.nodal.core.linux;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.PlatformService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class NetconfigDNSProvider implements DNSProvider {

    private PlatformService<?> platform;

    @Override
    public List<DNSEntry> entries() throws IOException {
        var dir = netconfigPath();
        if(Files.exists(dir)) {
            var l = new ArrayList<DNSEntry>();
            try(var str = Files.newDirectoryStream(dir)) {
                for(var f : str) {
                    if(Files.isDirectory(f)) {
                        try(var istr = Files.newDirectoryStream(dir)) {
                            for(var nf : str) {
                                if(!Files.isDirectory(nf)) {
                                    dnsEntry(nf).ifPresent(d -> l.add(d));
                                }
                            }
                        }
                    }
                }
            }
            return l;
        }
        else
            return Collections.emptyList();
    }

    @Override
    public void set(DNSEntry entry) throws IOException {
        if(entry.empty()) {
            unset(entry);
        }
        else {
            var sw = new StringWriter();
            try (var pw = new PrintWriter(sw)) {
                if(entry.domains().length > 0) {
                    pw.println(String.format("DNSDOMAIN='%s'", String.join(" ", entry.domains())));
                }
                pw.println(String.format("DNSSERVERS='%s'", String.join(" ", entry.servers())));
            }
            platform.context().commands().privileged().logged().pipeTo(sw.toString(), "netconfig", "modify",
                    "-s", "nodal-core", "-i", entry.iface());
        }
        
    }

    @Override
    public void unset(DNSEntry entry) throws IOException {
        platform.context().commands().privileged().logged().result("netconfig", "remove",
                "-s", "nodal-core", "-i", entry.iface());
    }

    @Override
    public void init(PlatformService<?> platform) {
        this.platform = platform;
    }

	protected Path netconfigPath() {
		return LinuxDNSProviderFactory.runPath().resolve("netconfig");
	}

    private Optional<DNSEntry> dnsEntry(Path netconfigInterface) throws IOException {
        try(var rdr = Files.newBufferedReader(netconfigInterface)) {
            String line;
            var bldr = new DNSEntry.Builder();
            var haveIface = false;
            while( ( line = rdr.readLine() ) != null) {
                var args = line.split("=");
                if(args[0].equals("INTERFACE") && args.length > 1) {
                    haveIface = true;
                    bldr.withInterface(stripQuotes(args[1]));
                }
                else if(args[0].equals("DNSDOMAIN") && args.length > 1) {
                    bldr.addDomains(stripQuotes(args[1]).split("\\s+"));
                }
                else if(args[0].equals("DNSSERVERS") && args.length > 1) {
                    bldr.addServers(stripQuotes(args[1]).split("\\s+"));
                }
            }
            if(haveIface)
                return Optional.of(bldr.build());
            else
                return Optional.empty();
        }
    }
    
    private String stripQuotes(String str) {
        while(str.startsWith("'") || str.startsWith("\\")) {
            str = str.substring(1);
        }
        while(str.endsWith("'") || str.endsWith("\"")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }
}
