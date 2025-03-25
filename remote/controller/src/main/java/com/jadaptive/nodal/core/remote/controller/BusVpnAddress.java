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
package com.jadaptive.nodal.core.remote.controller;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Optional;

import com.jadaptive.nodal.core.lib.VpnAddress;
import com.jadaptive.nodal.core.remote.lib.RemoteVpnAddress;

public class BusVpnAddress implements VpnAddress {

    private final RemoteVpnAddress remote;

    BusVpnAddress(RemoteVpnAddress remote) {
        this.remote = remote;
    }

    @Override
    public boolean isUp() {
        return remote.isUp();
    }

    @Override
    public boolean isDefaultGateway() {
        return remote.isDefaultGateway();
    }

    @Override
    public void setDefaultGateway(String address) {
        remote.setDefaultGateway(address);
    }

    @Override
    public void delete() throws IOException {
        remote.delete();
    }

    @Override
    public void down() throws IOException {
        remote.down();
    }

    @Override
    public String getMac() {
        var mac = remote.getMac();
        return mac.equals("") ? null : mac;
    }

    @Override
    public int getMtu() {
        return remote.getMtu();
    }

    @Override
    public String name() {
        return remote.name();
    }

    @Override
    public String displayName() {
        return remote.displayName();
    }

    @Override
    public String nativeName() {
        return remote.nativeName();
    }

    @Override
    public String peer() {
        var peer = remote.peer();
        return peer.equals("") ? null : peer;
    }

    @Override
    public String table() {
        return remote.table();
    }

    @Override
    public void mtu(int mtu) {
        remote.mtu(mtu);
    }

    @Override
    public void up() throws IOException {
        remote.up();
    }

    @Override
    public boolean isLoopback() {
        return remote.isLoopback();
    }

    @Override
    public Optional<NetworkInterface> networkInterface() {
        return Optional.empty();
    }

    @Override
    public String shortName() {
        return remote.shortName();
    }

    @Override
    public boolean hasVirtualName() {
        return remote.hasVirtualName();
    }

}
