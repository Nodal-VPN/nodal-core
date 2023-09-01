package com.logonbox.vpn.drivers.macos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class SCUtilCompatibleDNSProvider extends AbstractSCUtilDNSProvider {
    final static Logger LOG = LoggerFactory.getLogger(SCUtilCompatibleDNSProvider.class);

    @SuppressWarnings("unchecked")
    @Override
    public List<DNSEntry> entries() throws IOException {
        var l = new ArrayList<DNSEntry>();
        for (var key : scutil.list(".*/Network/Service/.*/DNS")) {
            var bldr = new DNSEntry.Builder();
            var dict = scutil.get(key);
            var rootDict = scutil.get(key.substring(0,key.lastIndexOf('/')));
            bldr.withInterface((String)rootDict.getOrDefault("UserDefinedName", dict.key().split("/")[3]));
            bldr.addServers((Collection<String>) dict.get("ServerAddresses"));
            bldr.addDomains((Collection<String>) dict.getOrDefault("SearchDomains", Collections.emptyList()));
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
        dict.put("SearchDomains", Arrays.asList(entry.domains()));
        dict.set();

        var rootDict = scutil.dictionary(String.format("State:/Network/Service/%s", entry.iface()));
        rootDict.put("UserDefinedName", entry.iface());
        rootDict.set();
    }

    @Override
    public void unset(DNSEntry entry) throws IOException {
        LOG.info("Removing resolver");
        scutil.remove(String.format("State:/Network/Service/%s/DNS", entry.iface()));
        scutil.remove(String.format("State:/Network/Service/%s", entry.iface()));
    }

}
