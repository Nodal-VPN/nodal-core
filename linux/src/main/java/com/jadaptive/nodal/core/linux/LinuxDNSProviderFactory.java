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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.jadaptive.nodal.core.lib.DNSProvider;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.linux.dbus.NetworkManager;
import com.sshtools.liftlib.OS;

/**
 * Decides which DNS intergration to use on Linux from Network Manager, SystemD
 * or resolvconf.
 */
public class LinuxDNSProviderFactory implements DNSProvider.Factory {

    @SuppressWarnings("unchecked")
    @Override
    public <P extends DNSProvider> Class<P>[] available() {
    	if(OS.isLinux()) {
    		return new Class[] { OpenresolvDNSProvider.class, ResolvConfDNSProvider.class, NetworkManagerDNSProvider.class, SystemDDNSProvider.class, RawDNSProvider.class };
    	}
    	else {
    		return new Class[0];
    	}
    }

    @Override
    public DNSProvider create(Optional<Class<? extends DNSProvider>> clazz, SystemContext context) {
        if (clazz.isPresent()) {
            /* Don't use reflection her for native images' sake */
            var clazzVal = clazz.get();
            if (clazzVal.equals(OpenresolvDNSProvider.class)) {
                return new OpenresolvDNSProvider();
            }
            else if (clazzVal.equals(ResolvConfDNSProvider.class)) {
                return new ResolvConfDNSProvider();
            } else if (clazzVal.equals(NetworkManagerDNSProvider.class)) {
                return new NetworkManagerDNSProvider();
            } else if (clazzVal.equals(SystemDDNSProvider.class)) {
                return new SystemDDNSProvider();
            } else if (clazzVal.equals(RawDNSProvider.class)) {
                return new RawDNSProvider();
            }  else if (clazzVal.equals(NetconfigDNSProvider.class)) {
                return new NetconfigDNSProvider();
            } else
                throw new IllegalArgumentException(clazzVal.toString());
        } else {
            return create(Optional.of(detect(context)), context);
        }
    }

    Class<? extends DNSProvider> detect(SystemContext context) {
        File f = new File("/etc/resolv.conf");
        try {
            String p = f.getCanonicalFile().getAbsolutePath();
            if (p.equals(f.getAbsolutePath())) {
            	try {
	        		for (var l : context.commands().privileged().output("resolvconf", "--version")) {
	        			if(l.startsWith("openresolv")) {
	                        return OpenresolvDNSProvider.class;
	        			}
	        		}
            	}
            	catch(Exception e) {
            	}
                return RawDNSProvider.class;
            } else if (p.equals(runPath().toString() + "/NetworkManager/resolv.conf")) {
                return NetworkManagerDNSProvider.class;
            } else if (p.equals(runPath().toString() + "/systemd/resolve/stub-resolv.conf")) {
                return SystemDDNSProvider.class;
            } else if (p.equals(runPath().toString() + "/resolvconf/resolv.conf")) {
                return ResolvConfDNSProvider.class;
            } else if (p.equals(runPath().toString() + "/netconfig/resolv.conf")) {
                var nmIntegrated = runPath().resolve("netconfig").resolve("NetworkManager.netconfig");
                if(Files.exists(nmIntegrated)) {
                    return NetworkManagerDNSProvider.class;
                }
                else {
                    return NetconfigDNSProvider.class;
                }
            }
        } catch (IOException ioe) {
        }
        throw new UnsupportedOperationException("No supported DNS provider can be used.");
    }
    
    static Path runPath() {
		var path = Paths.get("/run");
		if(Files.exists(path)) {
			return path;
		}
		var opath = Paths.get("/var/run");
		if(Files.exists(opath)) {
			return opath;
		}
		return path;
	}
}
