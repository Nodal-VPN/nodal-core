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
package com.jadaptive.nodal.core.windows;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.AbstractDesktopPlatformService;
import com.jadaptive.nodal.core.lib.NativeComponents.Tool;
import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.jadaptive.nodal.core.lib.VpnAdapterConfiguration;
import com.jadaptive.nodal.core.lib.VpnConfiguration;
import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;
import com.jadaptive.nodal.core.lib.VpnPeer;
import com.jadaptive.nodal.core.lib.VpnPeerInformation;
import com.jadaptive.nodal.core.lib.util.OsUtil;
import com.jadaptive.nodal.core.windows.WindowsSystemServices.Status;
import com.jadaptive.nodal.core.windows.WindowsSystemServices.Win32Service;
import com.jadaptive.nodal.core.windows.WindowsSystemServices.XAdvapi32;
import com.jadaptive.nodal.core.windows.WindowsSystemServices.XWinsvc;
import com.sshtools.liftlib.ElevatedClosure;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.ptr.PointerByReference;

import uk.co.bithatch.nativeimage.annotations.Resource;
import uk.co.bithatch.nativeimage.annotations.Serialization;

@Resource("win32-x84-64/.*")
public class WindowsPlatformService extends AbstractDesktopPlatformService<WindowsAddress> {

	private static final String WIREGUARD_TUNNEL = "WireGuard Tunnel";
	
	public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";
	public final static String SID_WORLD = "S-1-1-0";
	public final static String SID_USERS = "S-1-5-32-545";
	public final static String SID_SYSTEM = "S-1-5-18";

	public static final String TUNNEL_SERVICE_NAME_PREFIX = "NodalTunnel";

	private static final String INTERFACE_PREFIX = "net";

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformService.class);

	private static final int SERVICE_INSTALL_TIMEOUT = Integer
			.parseInt(System.getProperty("nodal.serviceInstallTimeout", "10"));

	private static Preferences PREFS = null;

	public static Preferences getInterfaceNode(String name) {
		return getInterfacesNode().node(name);
	}

	public static Preferences getInterfacesNode() {
		return getPreferences().node("interfaces");
	}

	public static String getBestRealName(String sid, String name) {
		try {
			if (sid == null)
				throw new NullPointerException();
			var acc = Advapi32Util.getAccountBySid(sid);
			return acc.name;
		} catch (Exception e) {
			/* Fallback to i18n */
			LOG.warn("Falling back to I18N strings to determine best real group name for {}", name);
			return WindowsFileSecurity.BUNDLE.getString(name);
		}
	}

	public static Preferences getPreferences() {
		if (PREFS == null) {
			/* Test whether we can write to system preferences */
			try {
				PREFS = Preferences.systemRoot();
				PREFS.put("test", "true");
				PREFS.flush();
				PREFS.remove("test");
				PREFS.flush();
			} catch (Exception bse) {
				System.out.println("Fallback to usering user preferences for public key -> interface mapping.");
				PREFS = Preferences.userRoot();
			}
		}
		return PREFS;
	}

	public WindowsPlatformService(SystemContext context) {
		super(INTERFACE_PREFIX, context);
		

		var wireguardDll = Paths.get("C:\\Windows\\System32\\wireguard.dll");
		if(!Files.exists(wireguardDll)) {
			/* TODO: At some point, check if this is really still needed. I'm sure ive see it work without it */
			var tool = Paths.get(context().nativeComponents().tool(Tool.WIREGUARD));
			try {
				context.alert("Installing wireguard.dll");
				context.commands().privileged().result("cmd", "/c", "copy", "/y", tool.toAbsolutePath().toString(), wireguardDll.toString());
			}
			catch(Exception e) {
				LOG.warn("Failed to install wireguard DLL to C:\\Windows\\System32. You may have connectivity problems.", e);
			}
		}
	}

	@FunctionalInterface
	public interface ServiceCall<R> {
		R accept(Win32Service srv) throws IOException;
	}

	@FunctionalInterface
	public interface ServiceRun {
		void accept(Win32Service srv) throws IOException;
	}

	@Override
	public void openToEveryone(Path path) throws IOException {
		WindowsFileSecurity.openToEveryone(path);
	}

	@Override
	public void restrictToUser(Path path) throws IOException {
		WindowsFileSecurity.restrictToUser(path);
	}

	@Override
	public List<WindowsAddress> addresses() {
		return ips(false);
	}

	@Override
	public List<VpnAdapter> adapters() {
		return ips(true).stream().map(addr -> configureExistingSession(addr)).collect(Collectors.toList());
	}

	private List<WindowsAddress> ips(boolean wireguardInterface) {
		/* https://stackoverflow.com/questions/38803545/java-networkinterface-getname-broken-on-windows */
			
		Set<WindowsAddress> ips = new LinkedHashSet<>();

		/* netsh first */
		try {
			for (var line : context().commands().privileged().output("netsh", "interface", "ip", "show", "interfaces")) {
				line = line.trim();
				if (line.equals("") || line.startsWith("Idx") || line.startsWith("---"))
					continue;
				var s = new StringTokenizer(line);
				s.nextToken(); // Idx
				if (s.hasMoreTokens()) {
					s.nextToken(); // Met
					if (s.hasMoreTokens()) {
						s.nextToken(); // MTU
						s.nextToken(); // Status
						var b = new StringBuilder();
						while (s.hasMoreTokens()) {
							if (b.length() > 0)
								b.append(' ');
							b.append(s.nextToken());
						}
						var ifName = b.toString();
						var matchesPrefix = isMatchesPrefix(ifName);
						if (!wireguardInterface || ( wireguardInterface && matchesPrefix)) {
							WindowsAddress vaddr = new WindowsAddress(nativeNameToInterfaceName(ifName).orElse(ifName), ifName, matchesPrefix ? WIREGUARD_TUNNEL : ifName, this);
							ips.add(vaddr);
						}
					}

				}
			}
		} catch (Exception e) {
			LOG.error("No netsh?", e);
		}

		try {
			String name = null;

			/*
			 * NOTE: Workaround. NetworkInterface.getNetworkInterfaces() doesn't discover
			 * active WireGuard interfaces for some reason, so use ipconfig /all to create a
			 * merged list.
			 */
			for (var line : context().commands().privileged().output("ipconfig", "/all")) {
				line = line.trim();
				if (line.startsWith("Unknown adapter")) {
					var args = line.split("\\s+");
					if (args.length > 1 && args[2].startsWith(getInterfacePrefix())) {
						name = args[2].split(":")[0];
					}
				} else if (name != null && line.startsWith("Description ")) {
					var args = line.split(":");
					if (args.length > 1) {
						var description = args[1].trim();
						if (description.startsWith(WIREGUARD_TUNNEL)) {
							var vaddr = new WindowsAddress(nativeNameToInterfaceName(name).orElse(name), name, description, this);
							ips.add(vaddr);
							break;
						}
					}
				}
			}

		} catch (Exception e) {
			LOG.error("Failed to list interfaces via Java.", e);
		}

		return new ArrayList<WindowsAddress>(ips);
	}

	@Override
	protected void onSetDefaultGateway(Gateway gateway) {
		LOG.info("Routing traffic all through {} on {}", gateway.address(), gateway.nativeIface());
		try {
			context().commands().privileged().logged().run("route", "add", gateway.address(), gateway.nativeIface());
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	protected void onResetDefaultGateway(Gateway gateway) {
		LOG.info("Stopping routing traffic all through {} on {}", gateway.address(), gateway.nativeIface());
		try {
			context().commands().privileged().logged().run("route", "delete", gateway.address(), gateway.nativeIface());
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public Optional<Gateway> defaultGateway() {
		Gateway gw = null;
		try {
			String netname = null;
			var ips = ips(false);
			for (var line : context().commands().privileged().output("ipconfig")) {
				if (gw == null) {
					if(line.startsWith(" ")) {
						netname = line.trim();
						netname = netname.substring(netname.length() - 1);
					}
					line = line.trim();
					if (line.startsWith("Default Gateway ")) {
						int idx = line.indexOf(":");
						if (idx != -1) {
							var addr = line.substring(idx + 1).trim();
							if (!addr.equals("0.0.0.0") && netname != null) {
								var fnetname = netname;
								var iface = ips.stream().filter(ip -> ip.displayName().equals(fnetname)).findFirst();
								if(iface.isPresent()) {
									gw = new Gateway(iface.get().nativeName(), addr);
								}
							}
						}
					}
				}
			}
			return Optional.ofNullable(gw);
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	protected Optional<String> getPublicKey(String interfaceName) throws IOException {
		try (var adapter = new WireguardLibrary.Adapter(interfaceName)) {
			var wgIface = adapter.getConfiguration();
			return Optional.of(wgIface.publicKey.toString());
		} catch (IllegalArgumentException iae) {
			return Optional.empty();
		}
	}

	@Override
	protected void onStart(StartRequest startRequest, VpnAdapter session) throws Exception {
		var configuration  = startRequest.configuration();
		var peer = startRequest.peer();
        var ip = findAddress(startRequest);

		var cwd = context().nativeComponents().binDir();
		var confDir = cwd.resolve("conf").resolve("connections");
		if (!Files.exists(confDir))
			Files.createDirectories(confDir);

		/* Get the driver specific configuration for this platform */
		var transformedConfiguration = transform(configuration);

		/* Install service for the network interface */
		context.alert("Installing service for {0}", ip.nativeName());
		var tool = Paths.get(context().nativeComponents().tool(Tool.NETWORK_CONFIGURATION_SERVICE));
		var install = context().commands().privileged().logged().task(new InstallService(
			ip.nativeName(), 
			cwd.toAbsolutePath().toString(), 
			confDir.toAbsolutePath().toString(), 
			tool.toAbsolutePath().toString(), 
			transformedConfiguration.write())
		).booleanValue();
		/*
		 * About to start connection. The "last handshake" should be this value or later
		 * if we get a valid connection
		 */
		var connectionStarted = Instant.ofEpochMilli(((System.currentTimeMillis() / 1000l) - 1) * 1000l);

		LOG.info("Waiting {} seconds for service to settle.", context.configuration().serviceWait().toSeconds());
		try {
			Thread.sleep(context.configuration().serviceWait().toMillis());
		} catch (InterruptedException e) {
		}
		LOG.info("Service should be settled.");
		context.alert("Service for {0} started", ip.nativeName());

		if (ip.isUp()) {
			LOG.info("Service for {} is already up.", ip.shortName());
		} else {
			LOG.info("Bringing up {}", ip.shortName());
			try {
				ip.mtu(configuration.mtu().or(() -> context.configuration().defaultMTU()).orElse(0));
				ip.up();
			} catch (IOException | RuntimeException ioe) {
				/* Just installed service failed, clean it up */
				if (install) {
					ip.delete();
				}
				throw ioe;
			}
		}

		session.attachToInterface(ip);

		/*
		 * Wait for the first handshake. As soon as we have it, we are 'connected'. If
		 * we don't get a handshake in that time, then consider this a failed
		 * connection. We don't know WHY, just it has failed
		 */
		if (peer.isPresent() && context.configuration().connectTimeout().isPresent()) {
			waitForFirstHandshake(configuration, session, connectionStarted, peer,
					context.configuration().connectTimeout().get());
		}

		/* DNS (optional with Windows kernel driver) */
		try {
			dns(configuration, ip);
		} catch (IOException | RuntimeException ioe) {
			try {
				session.close();
			} catch (Exception e) {
			}
			throw ioe;
		}
	}

	@Override
	protected void onInit(SystemContext ctx) {
		/*
		 * Check for an remove any wireguard interface services that are stopped (they
		 * should either be running or not exist
		 */
		try {
		    context().commands().privileged().task(new CleanUpStaleInterfaces());
		} catch (Exception e) {
			LOG.error("Failed to clean up stale interfaces.", e);
		}
	}

	@Override
	protected WindowsAddress add(String name, String nativeName, String type) throws IOException {
		return new WindowsAddress(name, nativeName, WIREGUARD_TUNNEL, this);
	}

	@Override
	protected WindowsAddress createVirtualInetAddress(NetworkInterface nif) throws IOException {
		throw new UnsupportedOperationException("Windows network interface names from Java's NetworkInterface are just made up and bear no resemblance to reality.");
	}

	@Override
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return nif.getDisplayName().startsWith(WIREGUARD_TUNNEL);
	}

	protected boolean isWireGuardInterface(WindowsAddress nif) {
		return  nif.displayName().startsWith(WIREGUARD_TUNNEL) || isMatchesPrefix(nif.displayName());
	}

	protected boolean isMatchesPrefix(WindowsAddress nif) {
		return isMatchesPrefix(nif.name());
	}

	protected boolean isMatchesPrefix(String name) {
		return name.startsWith(getInterfacePrefix());
	}

	@Override
	protected void transformInterface(VpnConfiguration configuration, VpnConfiguration.Builder writer) {
		if (!configuration.addresses().isEmpty()) {
			writer.withAddresses(configuration.addresses());
		}
		
		var dnsProvider = dns();
		if(dnsProvider.isEmpty() || dnsProvider.get() instanceof NullDNSProvider) {
			writer.withDns(configuration.dns());
		}
		
		writer.withoutMtu();
	}

	@Override
	public void runHook(VpnConfiguration configuration, VpnAdapter session, String... hookScript) throws IOException {
		runHookViaPipeToShell(configuration, session, OsUtil.getPathOfCommandInPathOrFail("cmd.exe").toString(), "/c",
				String.join(" & ", hookScript).trim());
	}

	@Override
	protected void runCommand(List<String> commands) throws IOException {
	    context().commands().privileged().logged().run(commands.toArray(new String[0]));
	}

	@Override
	public VpnInterfaceInformation information(VpnAdapter vpnAdapter) {
		try {
			return context().commands().privileged().logged().task(new GetInformation(vpnAdapter.address().name(), vpnAdapter.address().nativeName()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public VpnAdapterConfiguration configuration(VpnAdapter vpnAdapter) {
		try {
			return context().commands().privileged().logged().task(new GetConfiguration(vpnAdapter.address().nativeName()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class GetConfiguration implements ElevatedClosure<VpnAdapterConfiguration, Serializable> {

		private String nativeName;

		public GetConfiguration() {
		}

		GetConfiguration(String nativeName) {
			this.nativeName = nativeName;
		}

		@Override
		public VpnAdapterConfiguration call(ElevatedClosure<VpnAdapterConfiguration, Serializable> proxy)
				throws Exception {
			var cfgBldr = new VpnAdapterConfiguration.Builder();
			try (var adapter = new WireguardLibrary.Adapter(nativeName)) {
				var wgIface = adapter.getConfiguration();
				cfgBldr.withPrivateKey(wgIface.privateKey.toString());
				cfgBldr.withPublicKey(wgIface.publicKey.toString());
				cfgBldr.withListenPort(wgIface.listenPort);
				for (var peer : wgIface.peers) {
					var peerBldr = new VpnPeer.Builder();
					peerBldr.withPublicKey(peer.publicKey.toString());
					peerBldr.withPersistentKeepalive(peer.PersistentKeepalive);
					if (peer.endpoint != null)
						peerBldr.withEndpoint(peer.endpoint);
					if (peer.presharedKey != null)
						peerBldr.withPresharedKey(peer.presharedKey.toString());
					for (var allowed : peer.allowedIPs) {
						peerBldr.addAllowedIps(allowed.address.getHostAddress() + "/" + allowed.cidr);
					}
					var peerCfg = peerBldr.build();
					cfgBldr.addPeers(peerCfg);
				}
				return cfgBldr.build();
			}
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class GetInformation implements ElevatedClosure<VpnInterfaceInformation, Serializable> {

		@Serialization
		public static final class WindowsVpnInterfaceInformation implements VpnInterfaceInformation {
			private String ifacePublicKey;
			private ArrayList<VpnPeerInformation> peers;
			private long txV;
			private int ifaceListenPort;
			private String ifacePrivateKey;
			private long hs;
			private long rxV;
			private String name;
			
			public WindowsVpnInterfaceInformation() {
			}

			public WindowsVpnInterfaceInformation(String name, String ifacePublicKey, ArrayList<VpnPeerInformation> peers, long txV,
					int ifaceListenPort, String ifacePrivateKey, long hs, long rxV) {
				this.ifacePublicKey = ifacePublicKey;
				this.peers = peers;
				this.txV = txV;
				this.ifaceListenPort = ifaceListenPort;
				this.ifacePrivateKey = ifacePrivateKey;
				this.hs = hs;
				this.rxV = rxV;
				this.name = name;
			}

			@Override
			public long tx() {
				return txV;
			}

			@Override
			public Optional<String> error() {
				return Optional.empty();
			}

			@Override
			public long rx() {
				return rxV;
			}

			@Override
			public List<VpnPeerInformation> peers() {
				return peers;
			}

			@Override
			public String interfaceName() {
				return name;
			}

			@Override
			public Instant lastHandshake() {
				return Instant.ofEpochMilli(hs);
			}

			@Override
			public Optional<Integer> listenPort() {
				return Optional.of(ifaceListenPort);
			}

			@Override
			public Optional<Integer> fwmark() {
				return Optional.empty();
			}

			@Override
			public String publicKey() {
				return ifacePublicKey;
			}

			@Override
			public String privateKey() {
				return ifacePrivateKey;
			}
		}

		@Serialization
		public static final class WindowsVpnPeerInformation implements VpnPeerInformation {
			private long thisHandshake;
			private String presharedKey;
			private long pRx;
			private String peerPublicKey;
			private List<String> allowedIps;
			private long pTx;
			
			public WindowsVpnPeerInformation() {
			}

			public WindowsVpnPeerInformation(long thisHandshake, String presharedKey, long pRx,
					String peerPublicKey, List<String> allowedIps, long pTx) {
				this.thisHandshake = thisHandshake;
				this.presharedKey = presharedKey;
				this.pRx = pRx;
				this.peerPublicKey = peerPublicKey;
				this.allowedIps = allowedIps;
				this.pTx = pTx;
			}

			@Override
			public long tx() {
				return pTx;
			}

			@Override
			public long rx() {
				return pRx;
			}

			@Override
			public Instant lastHandshake() {
				return Instant.ofEpochMilli(thisHandshake);
			}

			@Override
			public Optional<String> error() {
				return Optional.empty();
			}

			@Override
			public Optional<InetSocketAddress> remoteAddress() {
				/* TODO: Not available? */
				return Optional.empty();
			}

			@Override
			public String publicKey() {
				return peerPublicKey;
			}

			@Override
			public Optional<String> presharedKey() {
				return Optional.ofNullable(presharedKey);
			}

			@Override
			public List<String> allowedIps() {
				return allowedIps;
			}
		}

		private String name;
		private String nativeName;

		public GetInformation() {
		}

		GetInformation(String name, String nativeName) {
			this.name = name;
			this.nativeName = nativeName;
		}

		@Override
		public VpnInterfaceInformation call(ElevatedClosure<VpnInterfaceInformation, Serializable> proxy)
				throws Exception {
			var lastHandshake = new AtomicLong(0);
			try (var adapter = new WireguardLibrary.Adapter(nativeName)) {
				var wgIface = adapter.getConfiguration();
				var tx = new AtomicLong(0);
				var rx = new AtomicLong(0);
				var peers = new ArrayList<VpnPeerInformation>();
				for (var peer : wgIface.peers) {
					var thisHandshake = peer.lastHandshake.orElse(Instant.ofEpochSecond(0));
					lastHandshake.set(Math.max(lastHandshake.get(), thisHandshake.toEpochMilli()));
					tx.addAndGet(peer.txBytes);
					rx.addAndGet(peer.rxBytes);
					peers.add(new WindowsVpnPeerInformation(thisHandshake.toEpochMilli(),
							peer.presharedKey == null ? null : peer.presharedKey.toString(), peer.rxBytes,
							peer.publicKey.toString(),
							new ArrayList<>(Arrays.asList(peer.allowedIPs).stream()
									.map(a -> String.format("%s/%d", a.address.getHostAddress(), a.cidr))
									.collect(Collectors.toList())),
							peer.txBytes));
				}
				
				var ifacePublicKey = wgIface.publicKey.toString();
				var ifacePrivateKey = wgIface.privateKey.toString();
				var ifaceListenPort = wgIface.listenPort;
				var txV = tx.get();
				var rxV = rx.get();
				var hs = lastHandshake.get();

				return new WindowsVpnInterfaceInformation(name, ifacePublicKey, peers, txV, ifaceListenPort, ifacePrivateKey, hs,
						rxV);
			}
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class CleanUpStaleInterfaces implements ElevatedClosure<Serializable, Serializable> {

		@Override
		public Serializable call(ElevatedClosure<Serializable, Serializable> proxy) throws Exception {
			try (var srvs = new WindowsSystemServices()) {
				for (var service : srvs.getServices()) {
					if (service.getNativeName().startsWith(TUNNEL_SERVICE_NAME_PREFIX)
							&& (service.getStatus() == Status.STOPPED || service.getStatus() == Status.PAUSED
									|| service.getStatus() == Status.UNKNOWN)) {
						try {
							service.uninstall();
						} catch (Exception e) {
							LOG.error("Failed to uninstall dead service {}", service.getNativeName(), e);
						}
					}
				}
			}
			return null;
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class InstallService implements ElevatedClosure<Boolean, Serializable> {

		private String name;
		private String cwd;
		private String exe;
		private String confDir;
		private String configuration;

		public InstallService() {
		}

		InstallService(String name, String cwd, String confDir, String exe, String configuration) {
			this.name = name;
			this.cwd = cwd;
			this.exe = exe;
			this.confDir = confDir;
			this.configuration = configuration;
		}

		@Override
		public Boolean call(ElevatedClosure<Boolean, Serializable> proxy) throws Exception {
			/*
			 * We need to set up file descriptors here so that the pipe has correct
			 * 'security descriptor' in windows. It derives this from the permissions on the
			 * folder the configuration file is stored in.
			 * 
			 * This took a lot of finding :\
			 * 
			 */
			var securityDescriptor = new PointerByReference();
			XAdvapi32.INSTANCE.ConvertStringSecurityDescriptorToSecurityDescriptor(
					"O:BAG:BAD:PAI(A;OICI;FA;;;BA)(A;OICI;FA;;;SY)", 1, securityDescriptor, null);
			if (!Advapi32.INSTANCE.SetFileSecurity(confDir,
					WinNT.OWNER_SECURITY_INFORMATION | WinNT.GROUP_SECURITY_INFORMATION | WinNT.DACL_SECURITY_INFORMATION,
					securityDescriptor.getValue())) {
				var err = Kernel32.INSTANCE.GetLastError();
				throw new IOException(String.format("Failed to set file security on '%s'. %d. %s", confDir, err,
						Kernel32Util.formatMessageFromLastErrorCode(err)));
			}
			
			try(var wrtr = new PrintWriter(Files.newBufferedWriter(Paths.get(confDir).resolve(name + ".conf")))) {
				wrtr.println(configuration);	
			}

			try (var srvs = new WindowsSystemServices()) {
				var install = false;
				if (!srvs.hasService(TUNNEL_SERVICE_NAME_PREFIX + "$" + name)) {
					install = true;
					install();
				} else
					LOG.info("Service for {} already exists.", name);

				/* The service may take a short while to appear */
				int i = 0;
				for (; i < SERVICE_INSTALL_TIMEOUT; i++) {
					if (srvs.hasService(TUNNEL_SERVICE_NAME_PREFIX + "$" + name))
						break;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						throw new IOException("Interrupted.", e);
					}
				}
				if (i == 10)
					throw new IOException(String.format(
							"Service for %s cannot be found, suggesting installation failed, please check logs.",
							name));

				return install;
			}
		}

		private void install() throws IOException {
			LOG.info("Installing service for {}", name);
			var cmd = new StringBuilder();

			LOG.info("Using network configuration service at {}", exe);
			cmd.append('"');
			cmd.append(exe);
			cmd.append('"');
			cmd.append(' ');
			cmd.append("/service");
			cmd.append(' ');
			cmd.append('"');
			cmd.append(cwd);
			cmd.append('"');
			cmd.append(' ');
			cmd.append('"');
			cmd.append(name);
			cmd.append('"');

			install(TUNNEL_SERVICE_NAME_PREFIX + "$" + name, "Nodal Tunnel for " + name,
					"Manage a single tunnel Nodal (" + name + ")", new String[] { "Nsi", "TcpIp" },
					"LocalSystem", null, cmd.toString(), WinNT.SERVICE_DEMAND_START, false, null, false,
					XWinsvc.SERVICE_SID_TYPE_UNRESTRICTED);

			LOG.info("Installed service for {} ({})", name, cmd);
		}

		void install(String serviceName, String displayName, String description, String[] dependencies, String account,
				String password, String command, int winStartType, boolean interactive,
				Winsvc.SERVICE_FAILURE_ACTIONS failureActions, boolean delayedAutoStart, DWORD sidType)
				throws IOException {

			var advapi32 = XAdvapi32.INSTANCE;

			var desc = new XWinsvc.SERVICE_DESCRIPTION();
			desc.lpDescription = description;

			var serviceManager = WindowsSystemServices.getManager(null, Winsvc.SC_MANAGER_ALL_ACCESS);
			try {

				var dwServiceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
				if (interactive)
					dwServiceType |= WinNT.SERVICE_INTERACTIVE_PROCESS;

				var service = advapi32.CreateService(serviceManager, serviceName, displayName,
						Winsvc.SERVICE_ALL_ACCESS, dwServiceType, winStartType, WinNT.SERVICE_ERROR_NORMAL, command,
						null, null, (dependencies == null ? "" : String.join("\0", dependencies)) + "\0", account,
						password);

				if (service != null) {
					try {
						var success = false;
						if (failureActions != null) {
							success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_FAILURE_ACTIONS,
									failureActions);
							if (!success) {
								var err = Native.getLastError();
								throw new IOException(String.format("Failed to set failure actions. %d. %s", err,
										Kernel32Util.formatMessageFromLastErrorCode(err)));
							}
						}

						success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_DESCRIPTION, desc);
						if (!success) {
							var err = Native.getLastError();
							throw new IOException(String.format("Failed to set description. %d. %s", err,
									Kernel32Util.formatMessageFromLastErrorCode(err)));
						}

						if (delayedAutoStart) {
							var delayedDesc = new XWinsvc.SERVICE_DELAYED_AUTO_START_INFO();
							delayedDesc.fDelayedAutostart = true;
							success = advapi32.ChangeServiceConfig2(service,
									Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO, delayedDesc);
							if (!success) {
								var err = Native.getLastError();
								throw new IOException(String.format("Failed to set autostart. %d. %s", err,
										Kernel32Util.formatMessageFromLastErrorCode(err)));
							}
						}

						/*
						 * https://github.com/WireGuard/wireguard-windows/tree/master/embeddable-dll-
						 * service
						 */
						if (sidType != null) {
							var info = new XWinsvc.SERVICE_SID_INFO();
							info.dwServiceSidType = sidType;
							success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_SERVICE_SID_INFO,
									info);
							if (!success) {
								var err = Native.getLastError();
								throw new IOException(String.format("Failed to set SERVICE_SID_INFO. %d. %s", err,
										Kernel32Util.formatMessageFromLastErrorCode(err)));
							}
						}

					} finally {
						advapi32.CloseServiceHandle(service);
					}
				} else {
					var err = Kernel32.INSTANCE.GetLastError();
					throw new IOException(String.format("Failed to install. %d. %s", err,
							Kernel32Util.formatMessageFromLastErrorCode(err)));

				}
			} finally {
				advapi32.CloseServiceHandle(serviceManager);
			}
		}
	}
}
