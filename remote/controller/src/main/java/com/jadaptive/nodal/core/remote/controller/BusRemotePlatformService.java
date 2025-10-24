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

import com.jadaptive.nodal.core.lib.BasePlatformService;
import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.NATMode;
import com.jadaptive.nodal.core.lib.NetworkInterfaceInfo;
import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.jadaptive.nodal.core.lib.VpnAdapterConfiguration;
import com.jadaptive.nodal.core.lib.VpnAddress;
import com.jadaptive.nodal.core.lib.VpnConfiguration;
import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;
import com.jadaptive.nodal.core.lib.VpnPeer;
import com.jadaptive.nodal.core.remote.lib.RemoteDNSProvider;
import com.jadaptive.nodal.core.remote.lib.RemoteNATMode;
import com.jadaptive.nodal.core.remote.lib.RemotePlatformService;
import com.jadaptive.nodal.core.remote.lib.RemoteStartRequest;
import com.jadaptive.nodal.core.remote.lib.RemoteVpnPeer;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class BusRemotePlatformService extends BasePlatformService<BusVpnAddress> {
    
    private final static Logger LOG = LoggerFactory.getLogger(BusRemotePlatformService.class);

    private final RemotePlatformService remote;
    private final SystemContext context;
    private final Optional<DNSProvider> dnsProvider;

    static Optional<RemoteDNSProvider> getDNSProvider(DBusConnection connection) throws DBusException {
        try {
            return Optional.of(connection.getRemoteObject(RemotePlatformService.BUS_NAME, RemoteDNSProvider.OBJECT_PATH,
                    RemoteDNSProvider.class));
        } catch (DBusExecutionException dbee) {
            return Optional.empty();
        }
    }

    public BusRemotePlatformService(String busName, SystemContext context, DBusConnection connection) throws DBusException {
        this(context, connection.getRemoteObject(busName, RemotePlatformService.OBJECT_PATH,
                RemotePlatformService.class), getDNSProvider(connection));
    }

    public BusRemotePlatformService(SystemContext context, RemotePlatformService remote,
            Optional<RemoteDNSProvider> dnsProvider) {
        this.remote = remote;
        this.context = context;
        this.dnsProvider = dnsProvider.map(BusDNSProvider::new);
    }

    @Override
    public boolean adapterExists(String nativeName) {
        return remote.adapterExists(nativeName);
    }

    @Override
    public List<VpnAdapter> adapters() {
        return Arrays.asList(remote.adapters()).stream().map(a -> new VpnAdapter(this, adapterAddress(a)))
                .toList();
    }

    protected Optional<VpnAddress> adapterAddress(String a) {
        try {
            return Optional.of(address(a));
        }
        catch(Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public BusVpnAddress address(String name) {
        return new BusVpnAddress(remote.address(name));
    }

    @Override
    public List<BusVpnAddress> addresses() {
        return Arrays.asList(remote.addresses()).stream().map(BusVpnAddress::new).toList();
    }

    @Override
    public boolean addressExists(String nativeName) {
        return remote.addressExists(nativeName);
    }

    @Override
    public void append(VpnAdapter vpnAdapter, VpnAdapterConfiguration cfg) throws IOException {
        try {
            remote.append(vpnAdapter.address().nativeName(), cfg.write());
        }
        catch(RuntimeException re) {
            LOG.error("Failed to append to network configuration.",  re);
            throw re;
        }

    }

    @Override
    public VpnAdapterConfiguration configuration(VpnAdapter adapter) {
        try {
            return new VpnAdapterConfiguration.Builder()
                    .fromFileContent(remote.configuration(adapter.address().nativeName())).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SystemContext context() {
        return context;
    }

    @Override
    public Optional<Gateway> defaultGateway() {
        var gw = remote.defaultGateway();
        if (gw.length == 0)
            return Optional.empty();
        else
            return Optional.of(new Gateway(gw[0], gw[1]));
    }

    @Override
    public void defaultGateway(Optional<Gateway> iface) {
        try {
            iface.ifPresentOrElse(i -> {
                remote.defaultGateway(new String[] { i.nativeIface(), i.address() });
            }, () -> {
                remote.defaultGateway(new String[0]);
            });
        }
        catch(RuntimeException re) {
            LOG.error("Failed to set default gateway.",  re);
            throw re;
        }
    }

    @Override
    public Optional<VpnPeer> defaultGatewayPeer() {
        var remotePeer =  remote.defaultGatewayPeer();
        if(remotePeer.valid())
            return Optional.of(remotePeer.toNative());
        else
            return Optional.empty();
    }

    @Override
    public void defaultGatewayPeer(VpnPeer peer) throws IOException {
        remote.defaultGatewayPeer(new RemoteVpnPeer(peer)); 
    }

    @Override
    public Optional<DNSProvider> dns() {
        return dnsProvider;
    }

    @Override
    public Optional<VpnAdapter> getByPublicKey(String publicKey) throws IOException {
        var iface = remote.getByPublicKey(publicKey);
        return iface.equals("") ? Optional.empty() : Optional.of(adapter(iface));
    }

    @Override
    public Instant getLatestHandshake(VpnAddress address, String publicKey) throws IOException {
        return Instant.ofEpochMilli(remote.getLatestHandshake(address.nativeName(), publicKey));
    }

    @Override
    public Optional<NATMode> getNat(String iface) throws IOException {
        return remote.getNat(iface).toNative();
    }

    @Override
    public VpnInterfaceInformation information(VpnAdapter adapter) {
        return remote.information(adapter.address().nativeName()).toNative();
    }

    @Override
    public Optional<String> interfaceNameToNativeName(String name) {
        var nname = remote.interfaceNameToNativeName(name);
        return nname.equals("") ? Optional.empty() : Optional.of(nname);
    }

    @Override
    public boolean isIpForwardingEnabledOnSystem() {
        return remote.isIpForwardingEnabledOnSystem();
    }

    @Override
    public boolean isValidNativeInterfaceName(String name) {
        return remote.isValidNativeInterfaceName(name);
    }

    @Override
    public Optional<String> nativeNameToInterfaceName(String name) {
        var iname = remote.nativeNameToInterfaceName(name);
        return iname.equals("") ? Optional.empty() : Optional.of(iname);
    }

    @Override
    public void openToEveryone(Path path) throws IOException {
        throw new UnsupportedOperationException("Not applicable to remote VPN");
    }

    @Override
    public void reconfigure(VpnAdapter vpnAdapter, VpnAdapterConfiguration cfg) throws IOException {
        try {
            remote.reconfigure(vpnAdapter.address().nativeName(), cfg.write());
        }
        catch(RuntimeException re) {
            LOG.error("Failed to reconfigure.",  re);
            throw re;
        }
    }

    @Override
    public void remove(VpnAdapter vpnAdapter, String publicKey) throws IOException {
        try {
            remote.remove(vpnAdapter.address().nativeName(), publicKey);
        }
        catch(RuntimeException re) {
            LOG.error("Failed to remove adapter.",  re);
            throw re;
        }
    }

    @Override
    public void resetDefaultGatewayPeer() throws IOException {
        try {
            remote.resetDefaultGatewayPeer();
        }
        catch(RuntimeException re) {
            LOG.error("Failed to reset default gateway peer.",  re);
            throw re;
        }
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
        throw new UnsupportedOperationException("Not applicable to remote VPN");
    }

    @Override
    public void runHook(VpnConfiguration configuration, VpnAdapter session, String... hookScript) throws IOException {
        try {
            remote.runHook(configuration.write(), session.address().nativeName(), hookScript);
        }
        catch(RuntimeException re) {
            LOG.error("Failed to run hook.",  re);
            throw re;
        }
    }

    @Override
    public void setIpForwardingEnabledOnSystem(boolean ipForwarding) {
        try {
            remote.setIpForwardingEnabledOnSystem(ipForwarding);
        }
        catch(RuntimeException re) {
            LOG.error("Failed to set IP forwarding.",  re);
            throw re;
        }
    }

    @Override
    public void setNat(String iface, Optional<NATMode> nat) throws IOException {
        try {
            remote.setNat(iface, new RemoteNATMode(nat));
        }
        catch(RuntimeException re) {
            LOG.error("Failed to set NAT mode.",  re);
            throw re;
        }
    }

    @Override
    public VpnAdapter start(StartRequest startRequest) throws IOException {
        try {
            return adapter(remote.start(new RemoteStartRequest(startRequest)));
        }
        catch(RuntimeException re) {
            LOG.error("Failed to start network.",  re);
            throw re;
        }
    }

    @Override
    public void sync(VpnAdapter vpnAdapter, VpnAdapterConfiguration cfg) throws IOException {
        try {
            remote.sync(vpnAdapter.address().nativeName(), cfg.write());
        }
        catch(RuntimeException re) {
            LOG.error("Failed to sync configuration.",  re);
            throw re;
        }
    }

	@Override
	public Optional<NetworkInterfaceInfo<?>> getBestLocalNic() {
		try {
			return Optional.of(remote.getBestLocalNic());
		}
		catch(Exception e) {
			return Optional.empty();
		}
	}

	@Override
	public List<NetworkInterfaceInfo<?>> getBestLocalNics() {
		return Arrays.asList(remote.getBestLocalNics());
	}

}
