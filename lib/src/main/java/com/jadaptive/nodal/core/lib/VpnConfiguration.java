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

import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.sshtools.jini.INI.Section;

import uk.co.bithatch.nativeimage.annotations.Serialization;

@Serialization
public interface VpnConfiguration extends VpnAdapterConfiguration {

    public final static class Builder extends VpnAdapterConfiguration.AbstractBuilder<Builder> {

        private Optional<Integer> mtu = Optional.empty();
        private List<String> dns = new ArrayList<>();
        private List<String> addresses = new ArrayList<>();
        private List<String> preUp = new ArrayList<>();
        private List<String> postUp = new ArrayList<>();
        private List<String> preDown = new ArrayList<>();
        private List<String> postDown = new ArrayList<>();
        private Optional<String> table = Optional.empty();
        private boolean saveConfig;

        @Override
        protected void readInterfaceSection(Section iface) {
            super.readInterfaceSection(iface);
            withAddresses(iface.getAllElse("Address"));
            withDns(iface.getAllElse("DNS"));
            withMtu(iface.getIntOr("MTU"));
            withPreUp(parseMultiline(iface, "PreUp"));
            withPreDown(parseMultiline(iface, "PreDown"));
            withPostUp(parseMultiline(iface, "PostUp"));
            withPostDown(parseMultiline(iface, "PostDown"));
            withSaveConfig(iface.getBoolean("SaveConfig", false));
            withTable(iface.getOr("Table"));
        }

		private String[] parseMultiline(Section iface, String key) {
			var val = String.join(System.lineSeparator(), iface.getAllElse(key));
			return val.equals("") ? new String[0] : val.split("\r?\n");
		}

        public Builder withTable(String table) {
            return withTable(Optional.of(table));
        }

        public Builder withTable(Optional<String> table) {
            this.table = table;
            return this;
        }

        public Builder withSaveConfig() {
            return withSaveConfig(true);
        }

        public Builder withSaveConfig(boolean saveConfig) {
            this.saveConfig = saveConfig;
            return this;
        }

        public Builder addAddresses(String... addresses) {
            return addAddresses(Arrays.asList(addresses));
        }

        public Builder addAddresses(Collection<String> addresses) {
            this.addresses.addAll(addresses);
            return this;
        }

        public Builder withAddresses(String... addresses) {
            return withAddresses(Arrays.asList(addresses));
        }

        public Builder withAddresses(Collection<String> addresses) {
            this.addresses.clear();
            return addAddresses(addresses);
        }

        public Builder addDns(String... dns) {
            return addDns(Arrays.asList(dns));
        }

        public Builder addDns(Collection<String> dns) {
            this.dns.addAll(dns);
            return this;
        }

        public Builder withDns(String... dns) {
            return withDns(Arrays.asList(dns));
        }

        public Builder withDns(Collection<String> dns) {
            this.dns.clear();
            return addDns(dns);
        }

        public Builder withMtu(int mtu) {
            return withMtu(mtu == 0 ? Optional.empty() : Optional.of(mtu));
        }

        public Builder withMtu(Optional<Integer> mtu) {
            this.mtu = mtu;
            return this;
        }

		public Builder withoutMtu() {
			return withMtu(Optional.empty());
		}

        public Builder withPreUp(String... preUp) {
            return withPreUp(Arrays.asList(preUp));
        }

        public Builder withPreUp(Collection<String> preUp) {
            this.preUp.clear();
            this.preUp.addAll(preUp);
            return this;
        }

        public Builder withPreDown(String... preDown) {
            return withPreDown(Arrays.asList(preDown));
        }

        public Builder withPreDown(Collection<String> preDown) {
            this.preDown.clear();
            this.preDown.addAll(preDown);
            return this;
        }

        public Builder withPostUp(String... postUp) {
            return withPostUp(Arrays.asList(postUp));
        }

        public Builder withPostUp(Collection<String> postUp) {
            this.postUp.clear();
            this.postUp.addAll(postUp);
            return this;
        }

        public Builder withPostDown(String... postDown) {
            return withPostDown(Arrays.asList(postDown));
        }

        public Builder withPostDown(Collection<String> postDown) {
            this.postDown.clear();
            this.postDown.addAll(postDown);
            return this;
        }

        public Builder fromConfiguration(VpnConfiguration configuration) {
            super.fromConfiguration(configuration);
            withMtu(configuration.mtu());
            withDns(configuration.dns());
            withAddresses(configuration.addresses());
            withPreUp(configuration.preUp());
            withPreDown(configuration.preDown());
            withPostUp(configuration.postUp());
            withPostDown(configuration.postDown());
            withTable(configuration.table());
            withSaveConfig(configuration.saveConfig());
            return this;
        }

        public VpnConfiguration build() {
            return new DefaultVpnConfiguration(this);
        }

        @SuppressWarnings("serial")
		static class DefaultVpnConfiguration extends DefaultVpnAdapterConfiguration implements VpnConfiguration {

            private final Integer mtu;
            private final List<String> dns;
            private final List<String> addresses;
            private final String[] postUp;
            private final String[] postDown;
            private final String[] preUp;
            private final String[] preDown;
            private final String table;
            private final boolean saveConfig;

            DefaultVpnConfiguration(VpnConfiguration.Builder builder) {
                super(builder);
                mtu = builder.mtu.orElse(0);
                dns = Collections.unmodifiableList(new ArrayList<>(builder.dns));
                addresses = Collections.unmodifiableList(new ArrayList<>(builder.addresses));
                preUp = builder.preUp.toArray(new String[0]);
                preDown = builder.preDown.toArray(new String[0]);
                postUp = builder.postUp.toArray(new String[0]);
                postDown = builder.postDown.toArray(new String[0]);
                saveConfig = builder.saveConfig;
                table = builder.table.orElse(null);
            }

            @Override
            public List<String> dns() {
                return dns;
            }

            @Override
            public Optional<Integer> mtu() {
                return mtu == 0 ? Optional.empty() : Optional.of(mtu);
            }

            @Override
            public List<String> addresses() {
                return addresses;
            }

            @Override
            public String[] preUp() {
                return preUp;
            }

            @Override
            public String[] postUp() {
                return postUp;
            }

            @Override
            public String[] preDown() {
                return preDown;
            }

            @Override
            public String[] postDown() {
                return postDown;
            }

            @Override
            public Optional<String> table() {
                return Optional.ofNullable(table);
            }

            @Override
            public boolean saveConfig() {
                return saveConfig;
            }

        }
    }

    String[] preUp();

    String[] postUp();

    String[] preDown();

    String[] postDown();

    List<String> dns();

    Optional<Integer> mtu();

    List<String> addresses();

    Optional<String> table();

    boolean saveConfig();
    
    @Override
    default void write(Writer writer) {
        var doc = VpnAdapterConfiguration.basicDocWithInterface(this);
        var ifaceSection = doc.section("Interface");
        ifaceSection.put("Address", addresses());
        if(preUp().length >0)
        	ifaceSection.put("PreUp", String.join(System.lineSeparator(), preUp()));
        if(postUp().length >0)
        	ifaceSection.put("PostUp", String.join(System.lineSeparator(), postUp()));
        if(preDown().length >0)
        	ifaceSection.put("PreDown", String.join(System.lineSeparator(), preDown()));
        if(postDown().length >0)
        	ifaceSection.put("PostDown", String.join(System.lineSeparator(), postDown()));

        if(!dns().isEmpty()) {
        	ifaceSection.putAll("DNS", dns().toArray(new String[0]));
        }
        
        mtu().ifPresent(mtu -> ifaceSection.put("MTU", mtu));
        table().ifPresent(table -> ifaceSection.put("Table", table));
                
        for(var peer : peers()) {
        	VpnAdapterConfiguration.writePeer(doc, peer);
        }
        
        VpnAdapterConfiguration.writer().write(doc, writer);
    }
}
