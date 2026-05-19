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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.NativeComponents.Tool;
import com.jadaptive.nodal.core.lib.util.OsUtil;
import com.jadaptive.nodal.core.lib.util.Util;

public abstract class AbstractUnixDesktopPlatformService<I extends VpnAddress>
		extends AbstractDesktopPlatformService<I> {
	private final static Logger LOG = LoggerFactory.getLogger(AbstractUnixDesktopPlatformService.class);

	private WireGuardUAPI uapi;

	public AbstractUnixDesktopPlatformService(String interfacePrefix, SystemContext context) {
		super(interfacePrefix, context);
	}

	/**
	 * Get the UAPI client instance, creating it lazily.
	 * 
	 * @return the UAPI client
	 */
	public WireGuardUAPI uapi() {
		if (uapi == null) {
			uapi = createUAPI();
		}
		return uapi;
	}

	/**
	 * Create a {@link WireGuardUAPI} instance. Override to customize the socket directory.
	 * 
	 * @return a new UAPI client
	 */
	protected WireGuardUAPI createUAPI() {
		return new WireGuardUAPI();
	}

	/**
	 * Check whether the UAPI socket is available for the given interface.
	 * 
	 * @param nativeInterfaceName the native interface name
	 * @return true if a UAPI socket exists for this interface
	 */
	public boolean hasUAPISocket(String nativeInterfaceName) {
		return uapi().hasSocket(nativeInterfaceName);
	}

	@Override
	public List<VpnAdapter> adapters() {
		try {
			var m = new HashMap<String, VpnAdapter>();
			for (var line : context.commands().output(context.nativeComponents().tool(Tool.WG), "show", "interfaces")) {
				for (var ifaceName : line.split("\\s+")) {
					var addr = address(ifaceName);
					var iface = configureExistingSession(addr);
					if(m.containsKey(addr.name())) {
						if(addr.name().equals(addr.nativeName())) {
							LOG.warn("Replacing interface {} [{}], as an interface with the same name already exists.", addr.name(), addr.nativeName());
							m.put(addr.name(), iface);
						}
						else {
							LOG.warn("Skipping interface {} [{}], an interface with the same name already exists.", addr.name(), addr.nativeName());
						}
					}
					else {
						m.put(addr.name(), iface);
					}
				}
			}
			return m.values().stream().toList();
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public void reconfigure(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
		var nativeName = adapter.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			LOG.debug("Using UAPI socket for setconf on {}", nativeName);
			uapi().setConfiguration(nativeName, configuration);
		} else {
			super.reconfigure(adapter, configuration);
		}
		addRoutes(adapter);
	}

	@Override
	public void sync(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
		var nativeName = adapter.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			LOG.debug("Using UAPI socket for syncconf on {}", nativeName);
			uapi().syncConfiguration(nativeName, configuration);
		} else {
			super.sync(adapter, configuration);
		}
		addRoutes(adapter);
	}

	@Override
	public void append(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
		var nativeName = adapter.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			LOG.debug("Using UAPI socket for addconf on {}", nativeName);
			uapi().appendConfiguration(nativeName, configuration);
		} else {
			super.append(adapter, configuration);
		}
		addRoutes(adapter);
	}

	@Override
	public void remove(VpnAdapter adapter, String publicKey) throws IOException {
		var nativeName = adapter.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			LOG.debug("Using UAPI socket to remove peer on {}", nativeName);
			uapi().removePeer(nativeName, publicKey);
		} else {
			super.remove(adapter, publicKey);
		}
	}

	@Override
	protected void onSetDefaultGateway(Gateway gateway) {
		LOG.info("Routing traffic all through {} on {}", gateway.address(), gateway.nativeIface());
		try {
			context.commands().privileged().logged().run("ip", "route", "add", "default", "via", gateway.address(), "dev", gateway.nativeIface());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	protected void onResetDefaultGateway(Gateway gateway) {
		LOG.info("Stopping routing traffic all through {} on {}", gateway.address(), gateway.nativeIface());
		try {
			context.commands().privileged().logged().run("ip", "route", "del", "default", "via", gateway.address(), "dev", gateway.nativeIface());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Instant getLatestHandshake(VpnAddress iface, String publicKey) throws IOException {
		if (hasUAPISocket(iface.nativeName())) {
			var device = uapi().getDevice(iface.nativeName());
			for (var peer : device.peers()) {
				if (peer.publicKey().equals(publicKey)) {
					return peer.lastHandshake();
				}
			}
			return Instant.ofEpochSecond(0);
		}
		// Fallback to wg CLI
		for (String line : context.commands().privileged().output(context.nativeComponents().tool(Tool.WG), "show",
				iface.nativeName(), "latest-handshakes")) {
			String[] args = line.trim().split("\\s+");
			if (args.length == 2) {
				if (args[0].equals(publicKey)) {
					return Instant.ofEpochSecond(Long.parseLong(args[1]));
				}
			}
		}
		return Instant.ofEpochSecond(0);
	}

    @Override
    protected String getDefaultScriptInterpreterSuffix() {
        return ".sh";
    }

    @Override
    protected String[] getDefaultScriptInterpreterArgs() throws IOException {
        try {
            return new String[] { OsUtil.getPathOfCommandInPathOrFail("bash").toString() };
        }
        catch(IOException ioe) {
            return new String[] { OsUtil.getPathOfCommandInPathOrFail("sh").toString() };
        }
    }

    @Override
    protected final void transformInterface(VpnConfiguration configuration, VpnConfiguration.Builder writer) {
        /* DNS, Addresses handled separately */
        writer.withAddresses();
        writer.withDns();
        writer.withoutMtu();
    }

	@Override
	protected Optional<String> getPublicKey(String interfaceName) throws IOException {
		if (hasUAPISocket(interfaceName)) {
			try {
				var device = uapi().getDevice(interfaceName);
				var pk = device.publicKey();
				if (pk == null || pk.isEmpty()) {
					return Optional.empty();
				}
				return Optional.of(pk);
			} catch (IOException e) {
				LOG.debug("UAPI socket error for {}, falling back to wg CLI", interfaceName, e);
			}
		}
		// Fallback to wg CLI
		try {
			var iterator = context.commands().privileged()
					.silentOutput(context.nativeComponents().tool(Tool.WG), "show", interfaceName, "public-key")
					.iterator();
			var pk = iterator.hasNext() ? iterator.next().trim() : "";
			if (pk.equals("(none)") || pk.equals(""))
				return Optional.empty();
			else
				return Optional.of(pk);

		} catch (UncheckedIOException uioe) {
			var ioe = uioe.getCause();
			if (ioe.getMessage() != null && (ioe.getMessage().indexOf("The system cannot find the file specified") != -1
					|| ioe.getMessage().indexOf("Unable to access interface: No such file or directory") != -1))
				return Optional.empty();
			else
				throw ioe;
		}
	}

	@SuppressWarnings("serial")
	@Override
	public VpnInterfaceInformation information(VpnAdapter adapter) {
		var iface = adapter.address();
		if (hasUAPISocket(iface.nativeName())) {
			try {
				return informationFromUAPI(iface);
			} catch (IOException e) {
				LOG.debug("UAPI socket error for {}, falling back to wg CLI", iface.nativeName(), e);
			}
		}
		return informationFromCli(adapter);
	}

	private VpnInterfaceInformation informationFromUAPI(VpnAddress iface) throws IOException {
		var device = uapi().getDevice(iface.nativeName());
		var peers = new ArrayList<VpnPeerInformation>();
		var totalRx = 0L;
		var totalTx = 0L;
		var maxHandshake = 0L;

		for (var peerState : device.peers()) {
			var thisRx = peerState.rxBytes();
			var thisTx = peerState.txBytes();
			var thisHandshake = peerState.lastHandshake();
			totalRx += thisRx;
			totalTx += thisTx;
			maxHandshake = Math.max(maxHandshake, thisHandshake.toEpochMilli());

			peers.add(new VpnPeerInformation() {
				@Override public long tx() { return thisTx; }
				@Override public long rx() { return thisRx; }
				@Override public Instant lastHandshake() { return thisHandshake; }
				@Override public Optional<String> error() { return Optional.empty(); }
				@Override public Optional<InetSocketAddress> remoteAddress() { return peerState.remoteAddress(); }
				@Override public List<String> allowedIps() { return peerState.allowedIps(); }
				@Override public String publicKey() { return peerState.publicKey(); }
				@Override public Optional<String> presharedKey() { return peerState.presharedKey(); }
			});
		}

		var ifaceName = iface.name();
		var finalRx = totalRx;
		var finalTx = totalTx;
		var finalHandshake = maxHandshake;

		return new VpnInterfaceInformation() {
			@Override public String interfaceName() { return ifaceName; }
			@Override public long tx() { return finalTx; }
			@Override public long rx() { return finalRx; }
			@Override public List<VpnPeerInformation> peers() { return peers; }
			@Override public Instant lastHandshake() { return Instant.ofEpochMilli(finalHandshake); }
			@Override public Optional<String> error() { return Optional.empty(); }
			@Override public Optional<Integer> listenPort() { return device.listenPort() == 0 ? Optional.empty() : Optional.of(device.listenPort()); }
			@Override public Optional<Integer> fwmark() { return device.fwmark() == 0 ? Optional.empty() : Optional.of(device.fwmark()); }
			@Override public String publicKey() { return device.publicKey(); }
			@Override public String privateKey() { return device.privateKey(); }
		};
	}

	@SuppressWarnings("serial")
	private VpnInterfaceInformation informationFromCli(VpnAdapter adapter) {
		try {
			var iface = adapter.address();
			var peers = new ArrayList<VpnPeerInformation>();
			var lastHandshake = new AtomicLong(0l);
			var rx = new AtomicLong(0l);
			var tx = new AtomicLong(0l);
			var port = new AtomicInteger();
			var fwmark = new AtomicInteger();
			var publicKey = new StringBuffer();
			var privateKey = new StringBuffer();

			for (var line : context.commands().privileged().output(context.nativeComponents().tool(Tool.WG), "show",
					iface.nativeName(), "dump")) {
				var st = new StringTokenizer(line);
				if (st.countTokens() == 4) {
					privateKey.append(st.nextToken());
					publicKey.append(st.nextToken());
					port.set(Integer.parseInt(st.nextToken()));
					fwmark.set(Util.parseFwMark(st.nextToken()));
				} else {
					var peerPublicKey = st.nextToken();
					var presharedKeyVal = st.nextToken();
					Optional<String> presharedKey;
					if (presharedKeyVal.equals("(none)")) {
						presharedKey = Optional.empty();
					} else {
						presharedKey = Optional.of(presharedKeyVal);
					}
					var remoteAddress = Optional.of(OsUtil.parseInetSocketAddress(st.nextToken()));
					var allowedIps = Arrays.asList(st.nextToken().split(","));
					var thisLastHandshake = Instant.ofEpochSecond(Long.parseLong(st.nextToken()));
					var thisRx = Long.parseLong(st.nextToken());
					var thisTx = Long.parseLong(st.nextToken());

					lastHandshake.set(Math.max(lastHandshake.get(), thisLastHandshake.toEpochMilli()));
					rx.addAndGet(thisRx);
					tx.addAndGet(thisTx);

					peers.add(new VpnPeerInformation() {
						@Override public long tx() { return thisTx; }
						@Override public long rx() { return thisRx; }
						@Override public Instant lastHandshake() { return thisLastHandshake; }
						@Override public Optional<String> error() { return Optional.empty(); }
						@Override public Optional<InetSocketAddress> remoteAddress() { return remoteAddress; }
						@Override public List<String> allowedIps() { return allowedIps; }
						@Override public String publicKey() { return peerPublicKey; }
						@Override public Optional<String> presharedKey() { return presharedKey; }
					});
				}
			}
			return new VpnInterfaceInformation() {
				@Override public String interfaceName() { return iface.name(); }
				@Override public long tx() { return tx.get(); }
				@Override public long rx() { return rx.get(); }
				@Override public List<VpnPeerInformation> peers() { return peers; }
				@Override public Instant lastHandshake() { return Instant.ofEpochMilli(lastHandshake.get()); }
				@Override public Optional<String> error() { return Optional.empty(); }
				@Override public Optional<Integer> listenPort() { return port.get() == 0 ? Optional.empty() : Optional.of(port.get()); }
				@Override public Optional<Integer> fwmark() { return fwmark.get() == 0 ? Optional.empty() : Optional.of(fwmark.get()); }
				@Override public String publicKey() { return publicKey.toString(); }
				@Override public String privateKey() { return privateKey.toString(); }
			};
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public VpnAdapterConfiguration configuration(VpnAdapter adapter) {
		var nativeName = adapter.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			try {
				return configurationFromUAPI(nativeName);
			} catch (IOException e) {
				LOG.debug("UAPI socket error for {}, falling back to wg CLI", nativeName, e);
			}
		}
		return configurationFromCli(adapter);
	}

	private VpnAdapterConfiguration configurationFromUAPI(String nativeName) throws IOException {
		var device = uapi().getDevice(nativeName);
		var builder = new VpnAdapterConfiguration.Builder();
		if (!device.privateKey().isEmpty()) {
			builder.withPrivateKey(device.privateKey());
		}
		if (device.listenPort() > 0) {
			builder.withListenPort(device.listenPort());
		}
		if (device.fwmark() > 0) {
			builder.withFwMark(device.fwmark());
		}
		for (var peerState : device.peers()) {
			var peerBuilder = new VpnPeer.Builder()
					.withPublicKey(peerState.publicKey())
					.withAllowedIps(peerState.allowedIps());
			peerState.presharedKey().ifPresent(peerBuilder::withPresharedKey);
			peerState.remoteAddress().ifPresent(addr ->
					peerBuilder.withEndpoint(addr.getHostString() + ":" + addr.getPort()));
			if (peerState.persistentKeepalive() > 0) {
				peerBuilder.withPersistentKeepalive(peerState.persistentKeepalive());
			}
			builder.addPeers(peerBuilder.build());
		}
		return builder.build();
	}

	private VpnAdapterConfiguration configurationFromCli(VpnAdapter adapter) {
		try {
			try {
				return new VpnAdapterConfiguration.Builder()
						.fromFileContent(String.join(System.lineSeparator(), context.commands().privileged().output(
								context.nativeComponents().tool(Tool.WG), "showconf", adapter.address().nativeName())))
						.build();
			} catch (ParseException e) {
				throw new IOException("Failed to parse configuration.", e);
			}
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	protected final void addRoutes(VpnAdapter session) throws IOException {

		/* Set routes from the known allowed-ips supplies by Wireguard. */
		session.allows().clear();

		for (var s : getAllowedIps(session)) {
			var t = new StringTokenizer(s);
			if (t.hasMoreTokens()) {
				t.nextToken();
				while (t.hasMoreTokens())
					session.allows().add(t.nextToken());
			}
		}

		/*
		 * Sort by network subnet size (biggest first)
		 */
		Collections.sort(session.allows(), (a, b) -> {
			var sa = a.split("/");
			var sb = b.split("/");
			Integer ia = sa.length == 1 ? 0 : Integer.parseInt(sa[1]);
			Integer ib = sb.length == 1 ? 0 : Integer.parseInt(sb[1]);
			var r = ia.compareTo(ib);
			if (r == 0) {
				return a.compareTo(b);
			} else
				return r * -1;
		});
		/* Actually add routes */
		((AbstractUnixAddress<?>) session.address()).setRoutes(session.allows());
	}

	protected Collection<String> getAllowedIps(VpnAdapter session) throws IOException {
		var nativeName = session.address().nativeName();
		if (hasUAPISocket(nativeName)) {
			var device = uapi().getDevice(nativeName);
			var result = new ArrayList<String>();
			for (var peer : device.peers()) {
				if (!peer.allowedIps().isEmpty()) {
					// Format: "publickey\tallowedip1 allowedip2 ..."
					var sb = new StringBuilder(peer.publicKey());
					for (var ip : peer.allowedIps()) {
						sb.append('\t').append(ip);
					}
					result.add(sb.toString());
				}
			}
			return result;
		}
		return context().commands().privileged().output(context().nativeComponents().tool(Tool.WG), "show",
				nativeName, "allowed-ips");
	}
}