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
package com.jadaptive.nodal.core.remote.lib;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;
import com.jadaptive.nodal.core.lib.VpnPeerInformation;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteVpnInterfaceInformation extends Struct {

    @Position(0)
    private String interfaceName = "";

    @Position(1)
    private long tx;

    @Position(2)
    private long rx;

    @Position(3)
    private RemoteVpnPeerInformation[] peers = new RemoteVpnPeerInformation[0];

    @Position(4)
    private long lastHandshake;

    @Position(5)
    private String publicKey = "";

    @Position(6)
    private String privateKey = "";

    @Position(7)
    private int listenPort;

    @Position(8)
    private int fwmark;

    @Position(9)
    private String error = "";

    public RemoteVpnInterfaceInformation() {
    }

    public RemoteVpnInterfaceInformation(String interfaceName, long tx, long rx, RemoteVpnPeerInformation[] peers,
            long lastHandshake, String publicKey, String privateKey, int listenPort, int fwmark, String error) {
        super();
        this.interfaceName = interfaceName;
        this.tx = tx;
        this.rx = rx;
        this.peers = peers;
        this.lastHandshake = lastHandshake;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.listenPort = listenPort;
        this.fwmark = fwmark;
        this.error = error;
    }

    public RemoteVpnInterfaceInformation(VpnInterfaceInformation information) {
        this.interfaceName = information.interfaceName();
        this.tx = information.tx();
        this.rx = information.rx();
        this.peers = information.peers().
                stream().
                map(RemoteVpnPeerInformation::new).
                toList().
                toArray(new RemoteVpnPeerInformation[0]);
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public long getTx() {
        return tx;
    }

    public long getRx() {
        return rx;
    }

    public RemoteVpnPeerInformation[] getPeers() {
        return peers;
    }

    public long getLastHandshake() {
        return lastHandshake;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public int getListenPort() {
        return listenPort;
    }

    public int getFwmark() {
        return fwmark;
    }

    public String getError() {
        return error;
    }

    @SuppressWarnings("serial")
    public VpnInterfaceInformation toNative() {
        return new VpnInterfaceInformation() {

            @Override
            public long tx() {
                return tx;
            }

            @Override
            public long rx() {
                return rx;
            }

            @Override
            public String publicKey() {
                return publicKey;
            }

            @Override
            public String privateKey() {
                return publicKey;
            }

            @Override
            public List<VpnPeerInformation> peers() {
                return Arrays.asList(peers).stream().map(RemoteVpnPeerInformation::toNative).toList();
            }

            @Override
            public Optional<Integer> listenPort() {
                return listenPort == 0 ? Optional.empty() : Optional.of(listenPort);
            }

            @Override
            public Instant lastHandshake() {
                return Instant.ofEpochMilli(lastHandshake);
            }

            @Override
            public String interfaceName() {
                return interfaceName;
            }

            @Override
            public Optional<Integer> fwmark() {
                return fwmark == 0 ? Optional.empty() : Optional.of(fwmark);
            }

            @Override
            public Optional<String> error() {
                return error.equals("") ? Optional.empty() : Optional.of(error);
            }
        };
    }
}
