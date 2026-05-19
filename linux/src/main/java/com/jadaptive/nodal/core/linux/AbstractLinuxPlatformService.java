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

import com.jadaptive.nodal.core.lib.AbstractUnixDesktopPlatformService;
import com.jadaptive.nodal.core.lib.NATMode;
import com.jadaptive.nodal.core.lib.NATMode.MASQUERADE;
import com.jadaptive.nodal.core.lib.NATMode.SNAT;
import com.jadaptive.nodal.core.lib.NativeComponents.Tool;
import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.jadaptive.nodal.core.lib.VpnAdapterConfiguration;
import com.jadaptive.nodal.core.lib.util.OsUtil;
import com.sshtools.liftlib.ElevatedClosure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import uk.co.bithatch.nativeimage.annotations.Serialization;

public abstract class AbstractLinuxPlatformService extends AbstractUnixDesktopPlatformService<AbstractLinuxAddress> {

    private static final String POSTROUTING_VPN = "POSTROUTING_VPN";
    private static final String POSTROUTING = "POSTROUTING";
	private static final String SNAT = "SNAT";
	private static final String MASQUERADE = "MASQUERADE";

	enum IpAddressState {
        HEADER, IP, MAC
    }

    private static final String INTERFACE_PREFIX = "wg";
    private final static Logger LOG = LoggerFactory.getLogger(AbstractLinuxPlatformService.class);

    static Object lock = new Object();

    private WireGuardNetlink netlink;
    private volatile Boolean netlinkAvailable;

    public AbstractLinuxPlatformService(SystemContext context) {
        super(INTERFACE_PREFIX, context);
    }

    /**
     * Get the WireGuard Netlink client, creating it lazily.
     *
     * @return the Netlink client
     */
    public WireGuardNetlink netlink() {
        if (netlink == null) {
            netlink = new WireGuardNetlink();
        }
        return netlink;
    }

    /**
     * Check if WireGuard Generic Netlink is available (kernel module loaded).
     *
     * @return true if Netlink is available
     */
    public boolean isNetlinkAvailable() {
        if (netlinkAvailable == null) {
            synchronized (this) {
                if (netlinkAvailable == null) {
                    netlinkAvailable = netlink().isAvailable();
                    if (netlinkAvailable) {
                        LOG.info("WireGuard Generic Netlink is available, will use for kernel interfaces");
                    }
                }
            }
        }
        return netlinkAvailable;
    }

    /**
     * Check if we can communicate with the given interface without forking wg.
     * Returns true if either a UAPI socket exists (wireguard-go) or Netlink
     * is available (kernel module).
     */
    public boolean hasDirectAccess(String nativeInterfaceName) {
        return hasUAPISocket(nativeInterfaceName) || isNetlinkAvailable();
    }

    /**
     * Get device state via Netlink.
     *
     * @param nativeInterfaceName the native interface name
     * @return device state
     * @throws IOException on error
     */
    public com.jadaptive.nodal.core.lib.WireGuardUAPI.DeviceState getDeviceViaNetlink(String nativeInterfaceName) throws IOException {
        return netlink().getDevice(nativeInterfaceName);
    }

	@Override
    public final List<AbstractLinuxAddress> addresses() {
        List<AbstractLinuxAddress> l = new ArrayList<>();
        AbstractLinuxAddress lastLink = null;
        try {
            IpAddressState state = IpAddressState.HEADER;
            for (String r : context().commands().output("ip", "address")) {
                if (!r.startsWith(" ")) {
                    String[] a = r.split(":");
                    String name = a[1].trim();
                    l.add(lastLink = new KernelLinuxAddress(nativeNameToInterfaceName(name).orElse(name), name, this));
                    state = IpAddressState.MAC;
                } else if (lastLink != null) {
                    r = r.trim();
                    if (state == IpAddressState.MAC) {
                        String[] a = r.split("\\s+");
                        if (a.length > 1) {
                            String mac = lastLink.getMac();
                            if (mac != null && !mac.equals(a[1]))
                                throw new IllegalStateException("Unexpected MAC.");
                        }
                        state = IpAddressState.IP;
                    } else if (state == IpAddressState.IP) {
                        if (r.startsWith("inet ")) {
                            String[] a = r.split("\\s+");
                            if (a.length > 1) {
                                lastLink.getAddresses().add(a[1]);
                            }
                            state = IpAddressState.HEADER;
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            if (!Boolean.getBoolean("hypersocket.development")) {
                throw new IllegalStateException("Failed to get network devices.", ioe);
            }
        }
        return l;
    }

    @Override
	public boolean isIpForwardingEnabledOnSystem() {
    	var ipv4 = Paths.get("/proc/sys/net/ipv4/ip_forward");
    	var ipv6 = Paths.get("/proc/sys/net/ipv6/conf/all/forwarding");
    	return (((Files.exists(ipv4) && isEnabled(ipv4)) || !Files.exists(ipv4)) &&
    			((Files.exists(ipv4) && isEnabled(ipv6)) || !Files.exists(ipv6)));
	}

	@Override
	public boolean isValidNativeInterfaceName(String ifaceName) {
		return ifaceName.length() < 16 && !ifaceName.matches(".*\\s+.*") && !ifaceName.contains(" ") && !ifaceName.contains("/");
	}

	@Override
	public void setNat(String iface, Optional<NATMode> nat) throws IOException {
		
		/*
		 * A little more complicated that ideal algorithm used to set input interface
		 * POSTROUTING rules for MASQ -
		 * 
		 * Some ideas from here,
		 * https://superuser.com/questions/1706874/iptables-selective-masquerade.
		 * 
		 * Note, kernels prior to 5.5.x don't. But all our VMs have this.
		 * 
		 * TODO this is all IPv4 only anyway! add IPv6 support
		 * TODO maybe switch to nftables, or support both
		 */
		
		var is = getNat(iface);
		if(!Objects.equals(is.orElse(null), nat.orElse(null))) {

			var priv = context.commands().privileged();
			var ipTablesPath = Optional.ofNullable(OsUtil.getPathOfCommandInPath("iptables")).map(Path::toString).orElse("/usr/sbin/iptables");

			LOG.info("Removing existing MASQUERADE/SNAT rules for {} using {}", iface, ipTablesPath);
			
			try {
				var local = getBestLocalNic().orElseThrow(() -> new IOException("Local NIC could not be determined."));
				if(is.isPresent()) {
					var i = is.get();
					if(i instanceof SNAT snat) {
						
						for(var to : snat.to()) {
							for(var addr : NATMode.SNAT.toIpv4Addresses(to)) {
								LOG.info("Removing SNAT rules for {} to {} using {}", iface, to.getName(), addr);
								priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN,  
										"-o", iface,
										"-i", to.getName(),
										"-j", SNAT, "--to-source", addr);
							}
						}

						for(var addr : NATMode.SNAT.toIpv4Addresses(local)) {
							LOG.info("Removing SNAT rules for {} to {} using {}", iface, local.getName(), addr);
							priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN,  
									"-i", iface,
									"-j", SNAT, "--to-source", addr);
						}
						
					}
					else if(i instanceof MASQUERADE masq) {
						if(masq.out().isEmpty()) {
							LOG.info("Removing MASQ rules for {} to {}", iface, local.getName());
							priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN, "-i", local.getName(), "-j", MASQUERADE, "-o", iface);
							priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN, "-i", iface, "-j", MASQUERADE);
						}
						else {
							for(var in : masq.out()) {
								LOG.info("Removing MASQ rules for {} to {}", in.getName(), iface);
								priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN, "-i", in.getName(), "-j", MASQUERADE, "-o", iface);
							}
							LOG.info("Removing MASQ rules for {}", iface);
							priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING_VPN, "-i", iface, "-j", MASQUERADE, "*");
						}
					}
					else
						throw new UnsupportedOperationException(i.getClass().getName());
				}
				
				if(nat.isEmpty()) {
					LOG.info("Reverting to full routed mode.");
				}
				else {
					var n = nat.get();
					if(n instanceof SNAT snat) {
						
						for(var to : snat.to()) {
							for(var addr : NATMode.SNAT.toIpv4Addresses(to)) {
								LOG.info("Adding SNAT rules for {} to {} using {}", iface, to.getName(), addr);
								priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN,  
										"-o", iface,
										"-i", to.getName(),
										"-j", SNAT, "--to-source", addr);
							}
						}

						for(var addr : NATMode.SNAT.toIpv4Addresses(local)) {
							LOG.info("Adding SNAT rules for {} to {} using {}", iface, local.getName(), addr);
							priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN,  
									"-i", iface,
									"-j", SNAT, "--to-source", addr);
						}
						
					}
					else if(n instanceof MASQUERADE masq) {
						if(masq.out().isEmpty()) {
							LOG.info("Adding MASQUERADE rules for {} to {}", iface, local.getName());
							priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN, "-j", MASQUERADE, "-i", iface);
							priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN, "-j", MASQUERADE, "-i", local.getName(), "-o", iface);
						}
						else {
							for(var in : masq.out()) {
								LOG.info("Adding MASQUERADE rules for {} to {}", in.getName(), iface);
								priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN, "-i", in.getName(), "-j", MASQUERADE, "-o", iface);
							}
							LOG.info("Adding MASQUERADE rules for {}", iface);
							priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING_VPN, "-i", iface, "-j", MASQUERADE);
						}
					}
					else
						throw new UnsupportedOperationException(n.getClass().getName());
				}
			}
			finally {
				var haveSpecialTable = false;
				var needSpecialTable = false;
				for (@SuppressWarnings("unused") var l : priv.output(ipTablesPath, "-t", "nat", "-L", POSTROUTING, "-v", "-n").stream().skip(2).toList()) {
					haveSpecialTable = true;
					break;
				}
				for (@SuppressWarnings("unused") var l : priv.output(ipTablesPath, "-t", "nat", "-L", POSTROUTING_VPN, "-v", "-n").stream().skip(2).toList()) {
					needSpecialTable = true;
					break;
				}
				if(haveSpecialTable != needSpecialTable) {
					if(needSpecialTable) {
						LOG.info("Adding POSTROUTING -> POSTROUTING_VPN rule");
						priv.run(ipTablesPath, "-t", "nat", "-A", POSTROUTING,  "-j", POSTROUTING_VPN);
					}
					else {
						LOG.info("Removing POSTROUTING -> POSTROUTING_VPN rule");
						priv.run(ipTablesPath, "-t", "nat", "-D", POSTROUTING,  "-j", POSTROUTING_VPN);
					}
				}
			}
		}
	}

	@Override
	public Optional<String> nativeNameToInterfaceName(String name) {
		return Optional.empty();
	}

	@Override
	public Optional<String> interfaceNameToNativeName(String name) {
		return Optional.empty();
	}

	@Override
	public Optional<NATMode> getNat(String ifaceName) throws IOException {
		
		
		String foundMASQIface = null;
		List<String> foundMASQOuts = new ArrayList<>();
		String foundSNATIface = null;
		List<String> foundSNATOuts = new ArrayList<>();
		List<String> foundSNATTos = new ArrayList<>();
		
		for (var l : context.commands().privileged().output("iptables", "-t", "nat", "-L", POSTROUTING_VPN, "-v", "-n")) {
			var els = l.trim().split("\\s+");
			if(els.length > 6 && els[2].equals(MASQUERADE) && els[5].equals(ifaceName) && els[6].equals("*")) {
				foundMASQIface = els[5];
			}
			else if(els.length > 6 && els[2].equals(MASQUERADE) && els[6].equals(ifaceName)) {
				foundMASQOuts.add(els[5]);				
			}
			else if(els.length > 9 && els[2].equals(SNAT) && els[5].equals(ifaceName) && els[6].equals("*")) {
				foundSNATIface = els[5];
			}
			else if(els.length > 9 && els[2].equals(SNAT) && els[6].equals(ifaceName)) {
				foundSNATOuts.add(els[5]);
				if(els[9].startsWith(els[9].substring(3))) {
					foundSNATTos.add(els[5]);
				}
			}
		}
		
		if(foundMASQIface != null) {
			return Optional.of(new NATMode.MASQUERADE(foundMASQOuts.stream().map(out -> {
				try {
					return NetworkInterface.getByName(out);
				} catch (SocketException e) {
					throw new UncheckedIOException(e);
				}
			}).collect(Collectors.toSet())));
		}
		
		if(foundSNATIface != null) {
			return Optional.of(new NATMode.SNAT(foundMASQOuts.stream().map(out -> {
				try {
					return NetworkInterface.getByName(out);
				} catch (SocketException e) {
					throw new UncheckedIOException(e);
				}
			}).collect(Collectors.toSet())));
		}
		return Optional.empty();
	}

	@Override
	public void setIpForwardingEnabledOnSystem(boolean ipForwarding) {
    	var ipv4 = Paths.get("/proc/sys/net/ipv4/ip_forward");
    	var ipv6 = Paths.get("/proc/sys/net/ipv6/conf/all/forwarding");
    	var ipv4Exists = Files.exists(ipv4);
		var ipv6Exists = Files.exists(ipv6);
		if(ipv4Exists || ipv6Exists) {
			try {
	    		if(ipv4Exists) {
	    			context.commands().privileged().task(new SetIpForwarding(ipv4.toString(), ipForwarding));
	    		}
	    		if(ipv6Exists) {
	    			context.commands().privileged().task(new SetIpForwarding(ipv6.toString(), ipForwarding));
	    		}
			}
			catch(Exception e) {
				throw new IllegalStateException("Failed to change IP forwarding.", e);
			}
    	}
    	else {
    		super.setIpForwardingEnabledOnSystem(ipForwarding);
    	}
	}

	protected abstract AbstractLinuxAddress createAddress(String name, String nativeName);

    @Override
    protected final AbstractLinuxAddress createVirtualInetAddress(NetworkInterface nif) throws IOException {
        var ip = createAddress(nativeNameToInterfaceName(nif.getName()).orElse(nif.getName()), nif.getName());
        for (var addr : nif.getInterfaceAddresses()) {
            ip.getAddresses().add(addr.getAddress().toString());
        }
        return ip;
    }

    @Override
    public final Optional<Gateway> defaultGateway() {
        try {
	        for (String line : context().commands().privileged().output("ip", "route")) {
	            if (line.startsWith("default via")) {
	                String[] args = line.split("\\s+");
	                if (args.length > 4) {
	                    return Optional.of(new Gateway(args[4], args[2]));
	                }
	            }
	        }
        }
        catch(IOException ioe) {
        	throw new UncheckedIOException(ioe);
        }
        return Optional.empty();
    }

    @Override
    protected final void onStart(StartRequest startRequest, VpnAdapter session) throws IOException {

    	LOG.info("Creating new table for VPN NAT rules");
    	try {
    		context.commands().privileged().run("iptables", "-t", "nat", "-N", POSTROUTING_VPN);
    	}
    	catch(Exception e) {
    		if(LOG.isDebugEnabled())
        		LOG.info("Didn't create create new {} table for VPN NAT rules, probably already exists.", POSTROUTING_VPN, e);
    		else
    			LOG.info("Didn't create create new {} table for VPN NAT rules, probably already exists. {}", POSTROUTING_VPN, e.getMessage());
    	}
		
		var configuration  = startRequest.configuration();
        var ip = findAddress(startRequest);

        /* Set the address reserved */
        if (configuration.addresses().size() > 0)
            ip.setAddresses(configuration.addresses().get(0));

        var transformedConfig = transform(configuration);
        if (hasUAPISocket(ip.nativeName())) {
            LOG.info("Activating Wireguard configuration for {} via UAPI socket", ip.shortName());
            try {
                var adapterConfig = new VpnAdapterConfiguration.Builder()
                        .fromFileContent(transformedConfig.write())
                        .build();
                uapi().setConfiguration(ip.nativeName(), adapterConfig);
            } catch (ParseException e) {
                throw new IOException("Failed to parse transformed configuration", e);
            }
            LOG.info("Activated Wireguard configuration for {}", ip.shortName());
        } else if (isNetlinkAvailable()) {
            LOG.info("Activating Wireguard configuration for {} via Netlink", ip.shortName());
            try {
                var adapterConfig = new VpnAdapterConfiguration.Builder()
                        .fromFileContent(transformedConfig.write())
                        .build();
                netlink().setConfiguration(ip.nativeName(), adapterConfig);
            } catch (ParseException e) {
                throw new IOException("Failed to parse transformed configuration", e);
            }
            LOG.info("Activated Wireguard configuration for {}", ip.shortName());
        } else {
            Path tempFile = Files.createTempFile("wg", ".cfg");
            try {
                try (Writer writer = Files.newBufferedWriter(tempFile)) {
                    transformedConfig.write(writer);
                }
                
                // TEMP
                try(BufferedReader reader =  Files.newBufferedReader(tempFile)) {
                	String line;
                	while( ( line = reader.readLine()) != null) {
                		LOG.info("{}", line);
                	}
                }
                
                LOG.info("Activating Wireguard configuration for {} (in {})", ip.shortName(), tempFile);
                context().commands().privileged().logged().result(context().nativeComponents().tool(Tool.WG), "setconf", ip.name(),
                        tempFile.toString());
                LOG.info("Activated Wireguard configuration for {}", ip.shortName());
            } finally {
                Files.delete(tempFile);
            }
        }

        /*
         * About to start connection. The "last handshake" should be this value or later
         * if we get a valid connection
         */
        var connectionStarted = Instant.ofEpochMilli(((System.currentTimeMillis() / 1000l) - 1) * 1000l);

        /* Bring up the interface (will set the given MTU) */
        ip.mtu(configuration.mtu().or(() -> context.configuration().defaultMTU()).orElse(0));
        LOG.info("Bringing up {}", ip.shortName());
        ip.up();
        session.attachToInterface(ip);

        /*
         * Wait for the first handshake. As soon as we have it, we are 'connected'. If
         * we don't get a handshake in that time, then consider this a failed
         * connection. We don't know WHY, just it has failed
         * 
         * Note, this only works if the client has a persistent keep-alive
         */
		var peer = startRequest.peer();
        if (peer.isPresent() && context.configuration().connectTimeout().isPresent()) {
            waitForFirstHandshake(configuration, session, connectionStarted, peer,
                    context.configuration().connectTimeout().get());
        }

        /* DNS */
        try {
            if (configuration.addresses().size() > 0)
            	dns(configuration, ip);
        } catch (IOException | RuntimeException ioe) {
            try {
                session.close();
            } catch (Exception e) {
            }
            throw ioe;
        }

        /* Set the routes */
        try {
            LOG.info("Setting routes for {}", ip.shortName());
            addRoutes(session);
        } catch (IOException | RuntimeException ioe) {
            try {
                session.close();
            } catch (Exception e) {
            }
            throw ioe;
        }

    }

    private boolean isEnabled(Path path) {
		try(var rdr = Files.newBufferedReader(path)) {
    		return rdr.readLine().equals("1");
    	}
    	catch(IOException ioe) {
    		throw new UncheckedIOException(ioe);
    	}
	}

    @SuppressWarnings("serial")
	@Serialization
    public final static class SetIpForwarding implements ElevatedClosure<Serializable, Serializable> {
    	
    	private String path;
		private boolean enable;

		public SetIpForwarding() {} 
    	
		SetIpForwarding(String path, boolean enable) {
    		this.path = path;
    		this.enable = enable;
    	}

		@Override
		public Serializable call(ElevatedClosure<Serializable, Serializable> proxy) throws Exception {
			try(var rdr = Files.newBufferedWriter(Paths.get(path))) {
	    		rdr.write(enable ? "1" : "0");
	    	}
	    	catch(IOException ioe) {
	    		throw new UncheckedIOException(ioe);
	    	}
			return null;
		}
    	
    }

    // --- Netlink-aware overrides ---
    // These override the parent's UAPI-or-CLI methods to add a Netlink
    // path for kernel WireGuard interfaces (which have no UAPI socket).

    @Override
    public void reconfigure(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
        var nativeName = adapter.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            uapi().setConfiguration(nativeName, configuration);
        } else if (isNetlinkAvailable()) {
            LOG.debug("Using Netlink for setconf on {}", nativeName);
            netlink().setConfiguration(nativeName, configuration);
        } else {
            super.reconfigure(adapter, configuration);
            return; // super already calls addRoutes
        }
        addRoutes(adapter);
    }

    @Override
    public void sync(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
        var nativeName = adapter.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            uapi().syncConfiguration(nativeName, configuration);
        } else if (isNetlinkAvailable()) {
            LOG.debug("Using Netlink for syncconf on {}", nativeName);
            netlink().syncConfiguration(nativeName, configuration);
        } else {
            super.sync(adapter, configuration);
            return;
        }
        addRoutes(adapter);
    }

    @Override
    public void append(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
        var nativeName = adapter.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            uapi().appendConfiguration(nativeName, configuration);
        } else if (isNetlinkAvailable()) {
            LOG.debug("Using Netlink for addconf on {}", nativeName);
            netlink().appendConfiguration(nativeName, configuration);
        } else {
            super.append(adapter, configuration);
            return;
        }
        addRoutes(adapter);
    }

    @Override
    public void remove(VpnAdapter adapter, String publicKey) throws IOException {
        var nativeName = adapter.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            uapi().removePeer(nativeName, publicKey);
        } else if (isNetlinkAvailable()) {
            LOG.debug("Using Netlink to remove peer on {}", nativeName);
            netlink().removePeer(nativeName, publicKey);
        } else {
            super.remove(adapter, publicKey);
        }
    }

    @Override
    public Instant getLatestHandshake(com.jadaptive.nodal.core.lib.VpnAddress iface, String publicKey) throws IOException {
        if (hasUAPISocket(iface.nativeName())) {
            return super.getLatestHandshake(iface, publicKey);
        }
        if (isNetlinkAvailable()) {
            var device = netlink().getDevice(iface.nativeName());
            for (var peer : device.peers()) {
                if (peer.publicKey().equals(publicKey)) {
                    return peer.lastHandshake();
                }
            }
            return Instant.ofEpochSecond(0);
        }
        return super.getLatestHandshake(iface, publicKey);
    }

    @Override
    protected Optional<String> getPublicKey(String interfaceName) throws IOException {
        if (hasUAPISocket(interfaceName)) {
            return super.getPublicKey(interfaceName);
        }
        if (isNetlinkAvailable()) {
            try {
                var device = netlink().getDevice(interfaceName);
                var pk = device.publicKey();
                if (pk == null || pk.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(pk);
            } catch (IOException e) {
                LOG.debug("Netlink error for getPublicKey on {}, falling back to wg CLI", interfaceName, e);
            }
        }
        return super.getPublicKey(interfaceName);
    }

    @Override
    public com.jadaptive.nodal.core.lib.VpnInterfaceInformation information(VpnAdapter adapter) {
        var iface = adapter.address();
        if (hasUAPISocket(iface.nativeName())) {
            return super.information(adapter);
        }
        if (isNetlinkAvailable()) {
            try {
                var device = netlink().getDevice(iface.nativeName());
                var peers = new java.util.ArrayList<com.jadaptive.nodal.core.lib.VpnPeerInformation>();
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

                    peers.add(new com.jadaptive.nodal.core.lib.VpnPeerInformation() {
                        @Override public long tx() { return thisTx; }
                        @Override public long rx() { return thisRx; }
                        @Override public Instant lastHandshake() { return thisHandshake; }
                        @Override public Optional<String> error() { return Optional.empty(); }
                        @Override public Optional<java.net.InetSocketAddress> remoteAddress() { return peerState.remoteAddress(); }
                        @Override public java.util.List<String> allowedIps() { return peerState.allowedIps(); }
                        @Override public String publicKey() { return peerState.publicKey(); }
                        @Override public Optional<String> presharedKey() { return peerState.presharedKey(); }
                    });
                }

                var ifaceName = iface.name();
                var finalRx = totalRx;
                var finalTx = totalTx;
                var finalHandshake = maxHandshake;

                return new com.jadaptive.nodal.core.lib.VpnInterfaceInformation() {
                    @Override public String interfaceName() { return ifaceName; }
                    @Override public long tx() { return finalTx; }
                    @Override public long rx() { return finalRx; }
                    @Override public java.util.List<com.jadaptive.nodal.core.lib.VpnPeerInformation> peers() { return peers; }
                    @Override public Instant lastHandshake() { return Instant.ofEpochMilli(finalHandshake); }
                    @Override public Optional<String> error() { return Optional.empty(); }
                    @Override public Optional<Integer> listenPort() { return device.listenPort() == 0 ? Optional.empty() : Optional.of(device.listenPort()); }
                    @Override public Optional<Integer> fwmark() { return device.fwmark() == 0 ? Optional.empty() : Optional.of(device.fwmark()); }
                    @Override public String publicKey() { return device.publicKey(); }
                    @Override public String privateKey() { return device.privateKey(); }
                };
            } catch (IOException e) {
                LOG.debug("Netlink error for information on {}, falling back to wg CLI", iface.nativeName(), e);
            }
        }
        return super.information(adapter);
    }

    @Override
    public VpnAdapterConfiguration configuration(VpnAdapter adapter) {
        var nativeName = adapter.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            return super.configuration(adapter);
        }
        if (isNetlinkAvailable()) {
            try {
                var device = netlink().getDevice(nativeName);
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
                    var peerBuilder = new com.jadaptive.nodal.core.lib.VpnPeer.Builder()
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
            } catch (IOException e) {
                LOG.debug("Netlink error for configuration on {}, falling back to wg CLI", nativeName, e);
            }
        }
        return super.configuration(adapter);
    }

    @Override
    protected java.util.Collection<String> getAllowedIps(VpnAdapter session) throws IOException {
        var nativeName = session.address().nativeName();
        if (hasUAPISocket(nativeName)) {
            return super.getAllowedIps(session);
        }
        if (isNetlinkAvailable()) {
            var device = netlink().getDevice(nativeName);
            var result = new java.util.ArrayList<String>();
            for (var peer : device.peers()) {
                if (!peer.allowedIps().isEmpty()) {
                    var sb = new StringBuilder(peer.publicKey());
                    for (var ip : peer.allowedIps()) {
                        sb.append('\t').append(ip);
                    }
                    result.add(sb.toString());
                }
            }
            return result;
        }
        return super.getAllowedIps(session);
    }
}
