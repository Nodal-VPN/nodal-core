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
package com.jadaptive.nodal.core.remote.node;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.util.function.Consumer;

import org.freedesktop.dbus.annotations.DBusInterfaceName;

import com.jadaptive.nodal.core.lib.VpnAddress;
import com.jadaptive.nodal.core.remote.lib.RemoteVpnAddress;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@DBusInterfaceName(RemoteVpnAddressDelegate.DBUS_INTERFACE_NAME)
@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
public class RemoteVpnAddressDelegate implements RemoteVpnAddress {
    
    private final VpnAddress delegate;
    private final Consumer<RemoteVpnAddressDelegate> onDelete;

    RemoteVpnAddressDelegate(VpnAddress delegate, Consumer<RemoteVpnAddressDelegate> onDelete) {
        this.delegate = delegate;
        this.onDelete = onDelete;
    }

    @Override
    public String getObjectPath() {
        return RemoteVpnAddress.OBJECT_PATH + "/" + nativeName();
    }

    @Override
    public boolean isUp() {
        return delegate.isUp();
    }

    @Override
    public boolean isDefaultGateway() {
        return delegate.isDefaultGateway();
    }

    @Override
    public void setDefaultGateway(String address) {
        delegate.setDefaultGateway(address);
    }

    @Override
    public void delete() throws IOException {
        try {
            delegate.delete();
        }
        finally {
            onDelete.accept(this);
        }
    }

    @Override
    public void down() throws IOException {
        delegate.down();
    }

    @Override
    public String getMac() {
        return ofNullable(delegate.getMac()).orElse("");
    }

    @Override
    public boolean isLoopback() {
        return delegate.isLoopback();
    }

    @Override
    public int getMtu() {
        return delegate.getMtu();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String displayName() {
        return delegate.displayName();
    }

    @Override
    public String nativeName() {
        return delegate.nativeName();
    }

    @Override
    public String peer() {
        return ofNullable(delegate.peer()).orElse("");
    }

    @Override
    public String table() {
        return delegate.table();
    }

    @Override
    public void mtu(int mtu) {
        delegate.mtu(mtu);
    }

    @Override
    public void up() throws IOException {
        delegate.up();
    }

    @Override
    public String shortName() {
        return delegate.shortName();
    }

    @Override
    public boolean hasVirtualName() {
        return delegate.hasVirtualName();
    }

}
