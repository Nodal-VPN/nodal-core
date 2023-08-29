/**
 * Copyright © 2023 LogonBox Limited (support@logonbox.com)
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
package com.logonbox.vpn.drivers.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.AbstractIp;
import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import com.logonbox.vpn.drivers.lib.DNSProvider.DNSEntry;
import com.logonbox.vpn.drivers.lib.NativeComponents.Tool;
import com.logonbox.vpn.drivers.lib.util.IpUtil;
import com.logonbox.vpn.drivers.lib.util.Util;

public abstract class AbstractDesktopPlatformService<I extends VpnAddress> extends AbstractPlatformService<I> {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractDesktopPlatformService.class);
	
	protected Path tempCommandDir;

    private Optional<DNSProvider> dnsProvider;
	
	protected AbstractDesktopPlatformService(String interfacePrefix, SystemContext context) {
		super(interfacePrefix, context);
	}

    @Override 
    public void remove(VpnAdapter adapter, String publicKey) throws IOException {
    	context.commands().privileged().run(context.nativeComponents().tool(Tool.WG), "set", adapter.address().name(), "peer", publicKey, "remove");
    }

    @Override
	public void reconfigure(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
    	var path = Files.createTempFile("wg", ".cfg");
    	try {
    		configuration.write(path);
        	context.commands().privileged().run(context.nativeComponents().tool(Tool.WG), "setconf", adapter.address().name(), path.toString());
    	}
    	finally {
    		Files.delete(path);
    	}
	}

	@Override
	public void sync(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
		var path = Files.createTempFile("wg", ".cfg");
    	try {
    		configuration.write(path);
        	context.commands().privileged().run(context.nativeComponents().tool(Tool.WG), "syncconf", adapter.address().name(), path.toString());
    	}
    	finally {
    		Files.delete(path);
    	}
		
	}

	@Override
	public void append(VpnAdapter adapter, VpnAdapterConfiguration configuration) throws IOException {
		var path = Files.createTempFile("wg", ".cfg");
    	try {
    		configuration.write(path);
        	context.commands().privileged().run(context.nativeComponents().tool(Tool.WG), "addconf", adapter.address().name(), path.toString());
    	}
    	finally {
    		Files.delete(path);
    	}
	}

    @Override
    public final Optional<DNSProvider> dns() {
        if(dnsProvider == null) {
            var srvs = ServiceLoader.load(DNSProvider.Factory.class).stream().collect(Collectors.toList());
            if(srvs.size() == 0) {
                LOG.warn("No DNS provider factories found for this platform, DNS settings will be ignored.");
                dnsProvider = Optional.empty();
            }
            else {
                if(srvs.size() > 1) {
                    LOG.warn("Found multiple DNS provider factories, only the first will be used. This may be incorrect.");
                }
                try {
                    // TODO configuration of preferred when set
                    dnsProvider = Optional.of(srvs.get(0).get().create(Optional.empty()));
                    dnsProvider.get().init(this);
                }
                catch(UnsupportedOperationException uoe) {
                    LOG.warn("A DNS provider factory was found, but it could not detect any supported DNS providers.");
                    dnsProvider = Optional.empty();
                }
            }
        }
        return dnsProvider;
    }

	protected Path extractCommand(String platform, String arch, String name) throws IOException {
		LOG.info("Extracting command {} for platform {} on arch {}", name, platform, arch);
		try(InputStream in = getClass().getResource("/" + platform + "-" + arch + "/" + name).openStream()) {
			Path path = getTempCommandDir().resolve(name);
			try(OutputStream out = Files.newOutputStream(path)) {
				in.transferTo(out);
			}
			path.toFile().deleteOnExit();
			Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
			LOG.info("Extracted command {} for platform {} on arch {} to {}", name, platform, arch, path);
			return path;
		}
	}

	protected Path getTempCommandDir() throws IOException {
		if(tempCommandDir == null)
			tempCommandDir = Files.createTempDirectory("vpn");
		return tempCommandDir;
	}

	@Override
	public final VpnAdapter start(Optional<String> interfaceName, VpnConfiguration configuration, Optional<VpnPeer> peer) throws IOException {		
	    
	    var session = new VpnAdapter(this);

        if(configuration.preUp().length > 0)  {
            var p = configuration.preUp();
            LOG.info("Running pre-up commands. {}", String.join("; ", p).trim());
            runHook(configuration, session, p);
        };
	    
        try {
			onStart(interfaceName, configuration, session, peer);
		} catch(IOException ioe) {
			throw ioe;
		} catch(RuntimeException re) {
		    throw (RuntimeException)re;
		} catch (Exception e) {
			throw new IOException("Failed to start.", e);
		}
    
        var gw = defaultGateway();
        if(gw.isPresent() && configuration.peers().contains(gw.get())) {
			try {
				onSetDefaultGateway(gw.get());
			}
			catch(Exception e) { 
				LOG.error("Failed to setup routing.", e);
			}
		}

        if(configuration.postUp().length > 0)  {
		    var p = configuration.postUp();
            LOG.info("Running post-up commands. {}", String.join("; ", p).trim());
            runHook(configuration, session, p);
		};
		
		return session;
	}

	@Override
	protected final void onStop(VpnConfiguration configuration, VpnAdapter session) {
		if(defaultGateway().isPresent() && configuration.peers().contains(defaultGateway().get())) {
			try {
			    resetDefaulGateway();
			}
			catch(Exception e) { 
				LOG.error("Failed to tear down routing.", e);
			}
		}
	}

    @Override
	public List<I> addresses() {
		List<I> ips = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				I vaddr = createVirtualInetAddress(nif);
				if (vaddr != null)
					ips.add(vaddr);
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to get interfaces.", e);
		}
		return ips;
	}

	protected abstract I createVirtualInetAddress(NetworkInterface nif) throws IOException;

	protected void dns(VpnConfiguration configuration, I ip) throws IOException {
		if(configuration.dns().isEmpty()) {
		    var gw = defaultGateway();
			if(gw.isPresent() && configuration.peers().contains(gw.get()))
				LOG.warn("No DNS servers configured for this connection and all traffic is being routed through the VPN. DNS is unlikely to work.");
			else  
				LOG.info("No DNS servers configured for this connection.");
		}
		else {
			LOG.info("Configuring DNS servers for {} as {}", ip.shortName(), configuration.dns());
		}
		var dnsOr = dns();
		if(dnsOr.isPresent())
		    dnsOr.get().set(new DNSEntry.Builder().fromConfiguration(configuration).withInterface(ip.nativeName()).build());
		
	}

	protected abstract String getDefaultGateway() throws IOException;

	protected boolean isMatchesPrefix(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}
	
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return isMatchesPrefix(nif);
	}

	protected abstract void onStart(Optional<String> interfaceName, VpnConfiguration configuration, VpnAdapter logonBoxVPNSession, Optional<VpnPeer> peer) throws Exception;
	
	protected void waitForFirstHandshake(VpnConfiguration configuration, VpnAdapter session, Instant connectionStarted, Optional<VpnPeer> peerOr, Duration timeout)
			throws IOException {
	    if(configuration.peers().size() != 1) {
	        LOG.info("Not waiting for handshake, there are either no or multiple peers.");
	        return;
	    }
	    
	    if(peerOr.isEmpty()) {
            LOG.info("Not waiting for handshake, no peer specified.");
            return;
	    }

        var peer = peerOr.get();
	    var ip = session.address();
	    
	    if(peer.endpointAddress().isEmpty()) {
            LOG.info("Not waiting for handshake, the peer has no endpoint.");
            return;
	    }
        
        LOG.info("Waiting for handshake for {} seconds. Hand shake should be after {}", timeout.toSeconds(), connectionStarted.toEpochMilli());
        
		for(int i = 0 ; i < timeout.toSeconds() ; i++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new IOException(String.format("Interrupted connecting to %s", ip.shortName()));
			}
			try {
				var lastHandshake = getLatestHandshake(ip.name(), peer.publicKey());
				if(lastHandshake.equals(connectionStarted) || lastHandshake.isAfter(connectionStarted)) {
					/* Ready ! */
					return;
				}
			}
			catch(RuntimeException iae) {
				try {
					ip.down();
				}
				catch(Exception e) {
					LOG.error("Failed to stop after error.", e);
				}
				finally {
				    ip.delete();
				}
				throw iae;
			}
		}

		/* Failed to connect in the given time. Clean up and report an exception */
		try {
			ip.down();
		}
		catch(Exception e) {
			LOG.error("Failed to stop after timeout.", e);
		} finally {
		    ip.delete();
		}
		
		var endpointAddress = peer.endpointAddress().orElseThrow(() -> new IllegalStateException("No endpoint address."));
		var endpointName = peer.endpointAddress().map(a -> {
		    try {
	            return InetAddress.getByName(a).getHostName();
	        }
	        catch(Exception e) {
	            return endpointAddress;
	        }   
		}).orElse(endpointAddress);
		throw new NoHandshakeException(String.format("No handshake received from %s (%s) for %s within %d seconds.", endpointAddress, endpointName, ip.shortName(), timeout.toSeconds()));
	}
	
	protected final VpnConfiguration transform(VpnConfiguration configuration) {
		var transformBldr = new VpnConfiguration.Builder();
		
        var gw = defaultGateway();
        
		transformBldr.withPrivateKey(configuration.privateKey());
		transformInterface(configuration, transformBldr);
		for(var peer : configuration.peers()) {
			var transformPeerBldr = new VpnPeer.Builder();
			transformPeerBldr.withPeer(peer);
    		var allowedIps = new ArrayList<>(peer.allowedIps());
    		if(gw.isPresent() && peer.equals(gw.get())) {
    			transformPeerBldr.withAllowedIps("0.0.0.0/0");
    		}	
    		else {
    			if(context.configuration().ignoreLocalRoutes()) {
    				/* Filter out any routes that would cover the addresses of any interfaces
    				 * we already have
    				 */
    				Set<AbstractIp<?, ?>> localAddresses = new HashSet<>();
    				try {
    					for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
    						NetworkInterface ni = en.nextElement();
    						if(!ni.isLoopback() && ni.isUp()) { 
    							for(Enumeration<InetAddress> addrEn = ni.getInetAddresses(); addrEn.hasMoreElements(); ) {
    								InetAddress addr = addrEn.nextElement();
    								try {
    									localAddresses.add(IpUtil.parse(addr.getHostAddress()));
    								}
    								catch(IllegalArgumentException iae) {
    									// Ignore
    								}
    							}
    						}
    					}
    				}
    				catch(SocketException se) {
    					//
    				}
    
    				for(String route : new ArrayList<>(allowedIps)) {
    					try {
    						try {
    							Ipv4Range range = Ipv4Range.parseCidr(route);
    							for(AbstractIp<?, ?> laddr : localAddresses) {
    								if(laddr instanceof Ipv4 && range.contains((Ipv4)laddr)) {
    									// Covered by route. 
    									LOG.info("Filtering out route {} as it covers an existing local interface address.", route);
    									allowedIps.remove(route);
    									break;
    								}
    							}
    						}
    						catch(IllegalArgumentException iae) {
    							/* Single ipv4 address? */
    							Ipv4 routeIpv4 = Ipv4.of(route);
    							if(localAddresses.contains(routeIpv4)) {
    								// Covered by route. 
    								LOG.info("Filtering out route {} as it covers an existing local interface address.", route);
    								allowedIps.remove(route);
    								break;
    							}
    						}
    					}
    					catch(IllegalArgumentException iae) {
    						try {
    							Ipv6Range range = Ipv6Range.parseCidr(route);
    							for(AbstractIp<?, ?> laddr : localAddresses) {
    								if(laddr instanceof Ipv6 && range.contains((Ipv6)laddr)) {
    									// Covered by route. 
    									LOG.info("Filtering out route {} as it covers an existing local interface address.", route);
    									allowedIps.remove(route);
    									break;
    								}
    							}
    						}
    						catch(IllegalArgumentException iae2) {
    							/* Single ipv6 address? */
    							Ipv6 routeIpv6 = Ipv6.of(route);
    							if(localAddresses.contains(routeIpv6)) {
    								// Covered by route. 
    								LOG.info("Filtering out route {} as it covers an existing local interface address.", route);
    								allowedIps.remove(route);
    								break;
    							}
    						}
    					}
    				}
    			}
    			
    			String ignoreAddresses = System.getProperty("logonbox.vpn.ignoreAddresses", "");
    			if(ignoreAddresses.length() > 0) {
    				for(String ignoreAddress : ignoreAddresses.split(",")) {
    					allowedIps.remove(ignoreAddress);
    				}
    			}
    			transformPeerBldr.withAllowedIps(allowedIps);
    		}
    		transformPeer(configuration, peer, transformPeerBldr);
    		transformBldr.addPeers(peer);
		}
		return transformBldr.build();
	}
	
	protected void transformInterface(VpnConfiguration configuration, VpnConfiguration.Builder writer) {
	}

	protected void transformPeer(VpnConfiguration configuration, VpnPeer peer, VpnPeer.Builder writer) {
	}

	protected void runHookViaPipeToShell(VpnConfiguration connection, VpnAdapter session, String... args) throws IOException {
		if(LOG.isDebugEnabled()) {
			LOG.debug("Executing hook");
			for(String arg : args) {
				LOG.debug("    {}", arg);
			}
		}
		Map<String, String> env = new HashMap<String, String>();
		if(connection != null) {
		    env.put("LBVPN_ADDRESS", String.join(",", connection.addresses()));
			env.put("LBVPN_USER_PUBLIC_KEY", connection.publicKey());
			env.put("LBVPN_DNS", String.join(" ", connection.dns()));
			env.put("LBVPN_MTU", String.valueOf(connection.mtu().orElse(0)));
			var idx = 1;
			for(var peer : connection.peers()) {
			    if(peer.endpointAddress().isPresent()) {
    	            env.put("LBVPN_ENDPOINT_ADDRESS_" + idx, peer.endpointAddress().get());
    	            env.put("LBVPN_ENDPOINT_PORT_"+ idx, String.valueOf(peer.endpointPort().orElse(0)));
    	            env.put("LBVPN_PEER_PUBLIC_KEY_"+ idx, peer.publicKey());
    	            idx++;
			    }
			}
		}
		context.addScriptEnvironmentVariables(session, env);
		
		var addr = session.address();
        env.put("LBVPN_IP_MAC", addr.getMac());
        env.put("LBVPN_IP_NAME", addr.name());
        env.put("LBVPN_IP_DISPLAY_NAME", addr.displayName());
        env.put("LBVPN_IP_PEER", addr.peer());
        env.put("LBVPN_IP_TABLE", addr.table());
		if(LOG.isDebugEnabled()) {
			LOG.debug("Environment:-");
			for(Map.Entry<String, String> en : env.entrySet()) {
				LOG.debug("    {} = {}", en.getKey(), en.getValue());
			}
		}

        LOG.debug("Command Output: ");
        var errorMessage = new StringBuffer();
		int ret = context.commands().privileged().logged().env(env).consume((line) -> {
            LOG.debug("    {}", line);
            if(line.startsWith("[ERROR] ")) {
                errorMessage.setLength(0); // TODO really only keep last error message. I'd like to change this, but may break any users of this (we know there is at least one)
                errorMessage.append(line.substring(8));
            }
		}, args);
		
		LOG.debug("Exit: {}", ret);
		if(ret != 0) {
			if(errorMessage.length() == 0)
				throw new IOException(String.format("Hook exited with non-zero status of %d.", ret));
			else
				throw new IOException(errorMessage.toString());
		}
		
	}

	@Override
	public void runHook(VpnConfiguration configuration, VpnAdapter session, String... hookScript) throws IOException {
		for(String cmd : hookScript) {
		    runCommand(Util.parseQuotedString(cmd));
		}
	}

	protected abstract void runCommand(List<String> commands) throws IOException;
	
}
