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
package com.jadaptive.nodal.core.remote.lib;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.VpnPeer;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteVpnPeer extends Struct {
    @Position(0)
    private String endpointAddress = "";

    @Position(1)
    private int endpointPort = 0;
    
    @Position(2)
    private String publicKey = "";

    @Position(3)
    private int persistentKeepalive = 0;
    
    @Position(4)
    private String[] allowedIps = new String[0];
    
    @Position(5)
    private String presharedKey = "";

    
    public RemoteVpnPeer() {
        
    }
    
    public RemoteVpnPeer(String endpointAddress, int endpointPort, String publicKey, int persistentKeepalive,
			String[] allowedIps, String presharedKey) {
		super();
		this.endpointAddress = endpointAddress;
		this.endpointPort = endpointPort;
		this.publicKey = publicKey;
		this.persistentKeepalive = persistentKeepalive;
		this.allowedIps = allowedIps;
		this.presharedKey = presharedKey;
	}

	public RemoteVpnPeer(VpnPeer peer) {
        this.publicKey = peer.publicKey();
        this.endpointAddress = peer.endpointAddress().orElse("");
        this.endpointPort = peer.endpointPort().orElse(0);
        this.persistentKeepalive = peer.persistentKeepalive().orElse(0);
        this.presharedKey = peer.presharedKey().orElse("");
    }

    public boolean valid() {
        return !publicKey.equals("");
    }
    
    public VpnPeer toNative() {
        var bldr = new VpnPeer.Builder();
        bldr.withPublicKey(publicKey);
        if(!endpointAddress.equals("")) {
            bldr.withEndpointAddress(endpointAddress);
        }
        if(endpointPort > 0) {
            bldr.withEndpointPort(endpointPort);
        }
        if(persistentKeepalive > 0) {
            bldr.withPersistentKeepalive(persistentKeepalive);
        }
        bldr.withAllowedIps(allowedIps);
        if(!presharedKey.equals("")) {
            bldr.withPresharedKey(presharedKey);
        }
        return bldr.build();
    }

    public String getEndpointAddress() {
        return endpointAddress;
    }

    public int getEndpointPort() {
        return endpointPort;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public int getPersistentKeepalive() {
        return persistentKeepalive;
    }

    public String[] getAllowedIps() {
        return allowedIps;
    }

    public String getPresharedKey() {
        return presharedKey;
    }

}
