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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
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

import com.jadaptive.nodal.core.lib.DNSProvider.DNSEntry;
import com.jadaptive.nodal.core.lib.NativeComponents.Tool;
import com.jadaptive.nodal.core.lib.Prefs.PrefType;
import com.jadaptive.nodal.core.lib.ipmath.AbstractIp;
import com.jadaptive.nodal.core.lib.ipmath.Ipv4;
import com.jadaptive.nodal.core.lib.ipmath.Ipv4Range;
import com.jadaptive.nodal.core.lib.ipmath.Ipv6;
import com.jadaptive.nodal.core.lib.ipmath.Ipv6Range;
import com.jadaptive.nodal.core.lib.util.IpUtil;
import com.jadaptive.nodal.core.lib.util.Util;

public abstract class AbstractDesktopPlatformService<I extends VpnAddress> extends AbstractPlatformService<I> {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractDesktopPlatformService.class);
	
	protected Path tempCommandDir;

    private Optional<DNSProvider> dnsProvider;
	
	protected AbstractDesktopPlatformService(String interfacePrefix, SystemContext context) {
		super(interfacePrefix, context);
	}
	
	protected final I findAddress(StartRequest startRequest)
			throws IOException {

		var addresses = addresses();
		var configuration = startRequest.configuration();
		var resolver = new InterfaceNameResolver(this);
		var result = resolver.resolve(configuration, startRequest.interfaceName(), startRequest.nativeInterfaceName());
		var resolvedInterfaceName = result.resolvedName();
		var interfaceName = result.interfaceName();

		I ip = null;
		
		/* If a particular native interface has been resolved, then see if it is
		 * available. If it is, we can re-use if the public key is the same
		 */
		

		if (resolvedInterfaceName.isPresent()) {
			var nativeName = resolvedInterfaceName.get();
			var addr = find(nativeName, addresses);
			if (addr.isEmpty()) {
				LOG.info("No existing unused interfaces, creating new one {} for public key {}.", nativeName,
						configuration.publicKey());
				ip = map(interfaceName.orElse(nativeName), nativeName, "wireguard");
				if (ip == null)
					throw new IOException("Failed to create virtual IP address.");
				LOG.info("Created {}", ip.shortName());
			} else {
				var publicKey = getPublicKey(nativeName);
				if (publicKey.isPresent()) {
					if(publicKey.get().equals(configuration.publicKey())) {
						ip = addr.get();
					}
					else {
						throw new IOException(MessageFormat.format("{0} is already in use", nativeName));
					}
				}
			}
		}

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		if (ip == null) {
			int maxIface = -1;
			for (var i = 0; i < MAX_INTERFACES; i++) {
				var name = getInterfacePrefix() + i;
				LOG.info("Looking for {}", name);
				if (exists(name, addresses)) {
					/* Interface exists, is it connected? */
					var publicKey = getPublicKey(name);
					if (publicKey.isEmpty()) {
						/* No addresses, wireguard not using it, but something else might be */
						var mappedTo = nativeNameToInterfaceName(name); 
						if(mappedTo.isPresent()) {
							LOG.info("{} is in use, mapped to {}.", name, mappedTo.get());
							ip = address(name);
							maxIface = i;
						}
						else {
							LOG.info("{} appears to be being used by something other than wireguard, skipping.", name);
						} 
					} else if (publicKey.get().equals(configuration.publicKey())) {
						throw new IllegalStateException(String
								.format("Peer with public key %s on %s is already active.", publicKey.get(), name));
					} else {
						LOG.info("{} is already in use.", name);
					}
				} else if (maxIface == -1) {
					/* This one is the next free number */
					maxIface = i;
					LOG.info("{} is next free interface.", name);
					break;
				}
			}
			if (maxIface == -1)
				throw new IOException(String.format("Exceeds maximum of %d interfaces.", MAX_INTERFACES));

			if (ip == null) {
				var nativeName = getInterfacePrefix() + maxIface;
				LOG.info("No existing unused interfaces, creating new one {} for public key .", nativeName,
						configuration.publicKey());
				ip = map(interfaceName.orElse(nativeName), nativeName, "wireguard");
				if (ip == null)
					throw new IOException("Failed to create virtual IP address.");
				LOG.info("Created {}", ip.shortName());
			} else
				LOG.info("Using existing {}", ip.shortName());
		}
		return ip;
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
        	var dnsOr = context.configuration().dnsIntegrationMethod();
        	var allSrvs = ServiceLoader.load(DNSProvider.Factory.class, getClass().getClassLoader());
        	var preferredFailed = false;
        	
        	if(dnsOr.isPresent()) {
        		var dns = dnsOr.get();
        		for(var provFactory : allSrvs.stream().collect(Collectors.toList())) {
    				var provs = provFactory.get().available();
            		for(var provClazz : provs) {
            			try {
    						if(preferredFailed || provClazz.getName().equals(dns)) {							
    	        				var provInstance = provFactory.get().create(Optional.of(provClazz), context);
    	        				dnsProvider = Optional.of(provInstance);
    	                        dnsProvider.get().init(this);
    	                        return dnsProvider;
    						}
            			}
            			catch(UnsupportedOperationException uoe) {
            				break;
            			}
            		}
            	}	
        		throw new IllegalStateException("Request " + dns + ", but it was not available.");
        	}
			
            
			
			/* Fallback to the first that works */
			for(var provFactory : allSrvs.stream().collect(Collectors.toList())) {
				if(provFactory.get().available().length > 0) {
	    			try {
	    				dnsProvider = Optional.of(provFactory.get().create(Optional.empty(), context));
	                    dnsProvider.get().init(this);
	                    return dnsProvider;
	    			}
	    			catch(UnsupportedOperationException uoe) {
	    				// 
	    			}
				}
        	}
			
			dnsProvider = Optional.empty();
        }
        return dnsProvider;
    }

	protected Path extractCommand(String platform, String arch, String name) throws IOException {
		LOG.info("Extracting command {} for platform {} on arch {}", name, platform, arch);
		try(var in = getClass().getResource("/" + platform + "-" + arch + "/" + name).openStream()) {
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
	public final VpnAdapter start(StartRequest startRequest) throws IOException {		
	    
	    var session = new VpnAdapter(this);
        var config = startRequest.configuration();
        
		if(config.preUp().length > 0)  {
            var p = config.preUp();
            LOG.info("Running pre-up commands. {}", String.join("; ", p).trim());
            runHook(config, session, p);
        };
	    
        try {
			onStart(startRequest, session);
		} catch(IOException ioe) {
			throw ioe;
		} catch(RuntimeException re) {
		    throw (RuntimeException)re;
		} catch (Exception e) {
			throw new IOException("Failed to start.", e);
		}
    
        var gw = defaultGatewayPeer();
        if(gw.isPresent() && config.peers().contains(gw.get())) {
			try {
				var addr = gw.get().endpointAddress().orElseThrow(() -> new IllegalStateException("No endpoint for peer."));
				var iface = defaultGateway().
						map(Gateway::nativeIface).
						orElseThrow(() -> new IllegalStateException("No current default gateway."));
				onSetDefaultGateway(new Gateway(iface, addr));
			}
			catch(Exception e) { 
				LOG.error("Failed to setup routing.", e);
			}
		}

        if(config.postUp().length > 0)  {
		    var p = config.postUp();
            LOG.info("Running post-up commands. {}", String.join("; ", p).trim());
            runHook(config, session, p);
		};
		
		return session;
	}

	@Override
	protected final void onStop(VpnConfiguration configuration, VpnAdapter session) {
		try {
			try {
				if(defaultGatewayPeer().isPresent() && configuration.peers().contains(defaultGatewayPeer().get())) {
					try {
					    resetDefaultGatewayPeer();
					}
					catch(Exception e) { 
						LOG.error("Failed to tear down routing.", e);
					}
				}
			}
			finally {
				onStopped(configuration, session);
			}
		}
		finally {
			unmap(session.address().name());
		}
	}
	
	protected final void unmap(String name) {
		try {
			var nativeName = context().commands().privileged().task(new Prefs.RemoveKey(getNameToNativeNameNode(), name));
			if(nativeName != null) {
				context().commands().privileged().task(new Prefs.RemoveKey(getNativeNameToNameNode(), nativeName));
			}
			LOG.info("Unmapped interface names {} -> {}", name, nativeName == null ? "<null>" : nativeName);
		} catch (Exception e) {
			LOG.error("Failed to un-map interface names.", e);
		}
	}

	protected final I map(String name, String nativeName, String type) throws IOException {
		var addr = add(name, nativeName, type);
		try {
			context().commands().privileged().task(new Prefs.PutValue(getNameToNativeNameNode(), name, nativeName, PrefType.STRING));
			context().commands().privileged().task(new Prefs.PutValue(getNativeNameToNameNode(), nativeName, name, PrefType.STRING));
		} catch (Exception e) {
			throw new IOException("Failed to map interface names", e);
		}
		LOG.info("Mapping interface names {} -> {}", name, nativeName);
		return addr;
	}

	protected abstract I add(String name, String nativeName, String type) throws IOException;

	protected void onStopped(VpnConfiguration configuration, VpnAdapter session) {
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
		    var gw = defaultGatewayPeer();
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

	protected boolean isMatchesPrefix(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}
	
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return isMatchesPrefix(nif);
	}

	protected abstract void onStart(StartRequest startRequest, VpnAdapter session) throws Exception;
	
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
				var lastHandshake = getLatestHandshake(ip, peer.publicKey());
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
					LOG.error("Original error.", e);
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
		
        var gw = defaultGatewayPeer();
        transformBldr.fromConfiguration(configuration);
        transformBldr.withPeers();
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
    			
    			String ignoreAddresses = System.getProperty("nodal.ignoreAddresses", "");
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

        env.put("LBVPN_LOCAL_MAC", Util.getBestLocalhostMAC());
        env.put("LBVPN_LOCAL_DEVICE_NAME", Util.getDeviceName());
        
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
		try {
		    env.put("LBVPN_IP_MAC", addr.getMac());
		}
		catch(Exception e) {
            env.put("LBVPN_IP_MAC", "00:00:00:00:00:00");
		}
        env.put("LBVPN_IP_NAME", addr.name());
        env.put("LBVPN_IP_NATIVE_NAME", addr.nativeName());
        env.put("LBVPN_IP_SHORT_NAME", addr.shortName());
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

	// TODO I'd like to replace the use of preferences on all platforms to use files
	// in a state directory (/var/run/wireguard) as the code below used to do in the mac impl.
	// for now preferences are fine until everything else is working


//    public static UserspaceMacOsAddress ofName(String name,
//            UserspaceMacOsPlatformService platformService) {
//        try {
//            return new UserspaceMacOsAddress(name, platformService.context().commands().privileged().task(new GetNativeName(name)), platformService);
//        } catch(IOException ioe) {
//            throw new UncheckedIOException(ioe);
//        } catch(RuntimeException re) {
//            throw re;
//        } catch (Exception e) {
//            throw new IllegalStateException(e);
//        }
//    }
//
//    public static UserspaceMacOsAddress ofNativeName(String nativeName,
//            UserspaceMacOsPlatformService platformService) {
//        try {
//            var name = platformService.context().commands().privileged().task(new GetName(nativeName));
//            return new UserspaceMacOsAddress(name, nativeName, platformService);
//        } catch(IOException ioe) {
//            throw new UncheckedIOException(ioe);
//        } catch(RuntimeException re) {
//            throw re;
//        } catch (Exception e) {
//            throw new IllegalStateException(e);
//        }
//    }

//    @SuppressWarnings("serial")
//    @Serialization
//    public final static class GetNativeName implements ElevatedClosure<String, Serializable> {
//
//        private String name;
//
//        public GetNativeName() {
//        }
//
//        GetNativeName(String name) {
//            this.name = name;
//        }
//
//        @Override
//        public String call(ElevatedClosure<String, Serializable> proxy) throws Exception {
//            var namePath = Paths.get(String.format("/var/run/wireguard/%s.name", name));
//            if(!Files.exists(namePath))
//                return name;
//
//            String iface;
//            try(var in = Files.newBufferedReader(namePath)) {
//                iface = in.readLine();
//            }
//            var socketPath = Paths.get(String.format("/var/run/wireguard/%s.sock", iface));
//            if(!Files.exists(socketPath))
//                return name;
//
//            var secDiff = Files.getLastModifiedTime(socketPath).to(TimeUnit.SECONDS) - Files.getLastModifiedTime(namePath).to(TimeUnit.SECONDS) ;
//            if(secDiff > 2 || secDiff < -2)
//                return name;
//
//            return iface;
//        }
//    }
//
//    @SuppressWarnings("serial")
//    @Serialization
//    public final static class GetName implements ElevatedClosure<String, Serializable> {
//
//        private String realInterace;
//
//        public GetName() {
//        }
//
//        GetName(String realInterace) {
//            this.realInterace = realInterace;
//        }
//
//        @Override
//        public String call(ElevatedClosure<String, Serializable> proxy) throws Exception {
//            var dir = Paths.get("/var/run/wireguard");
//            if(Files.exists(dir)) {
//				try(var nameFiles = Files.newDirectoryStream(dir, f->f.getFileName().toString().endsWith(".name"))) {
//	                for(var f : nameFiles) {
//	                    String iface;
//	                    try(var in = Files.newBufferedReader(f)) {
//	                        iface = in.readLine();
//	                    }
//	                    if(realInterace.equals(iface)) {
//	                        var n = f.getFileName().toString();
//	                        return n.substring(0, n.lastIndexOf('.'));
//	                    }
//	                }
//	            }
//            }
//            return realInterace;
//        }
//    }
}
