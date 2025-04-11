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

import java.io.IOException;

import org.freedesktop.dbus.annotations.DBusBoundProperty;
import org.freedesktop.dbus.annotations.DBusProperty.Access;
import org.freedesktop.dbus.interfaces.DBusInterface;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Proxy
@Reflectable
@TypeReflect(methods = true, classes = true)
public interface RemoteVpnAddress  extends DBusInterface {

    String DBUS_INTERFACE_NAME = "com.jadaptive.nodal.core.RemoteVpnAddress";
    String OBJECT_PATH = "/" + DBUS_INTERFACE_NAME.replace('.', '/');
    
    @DBusBoundProperty
    boolean isUp();
    
    @DBusBoundProperty
    boolean isDefaultGateway();

    @DBusBoundProperty
    void setDefaultGateway(String address);

    void delete() throws IOException;

    void down() throws IOException;

    @DBusBoundProperty(access = Access.READ, name = "Mac")
    String getMac();

    @DBusBoundProperty
    boolean isLoopback();

    @DBusBoundProperty(access = Access.READ, name = "Mtu")
    int getMtu();

    @DBusBoundProperty(access = Access.READ, name = "Name")
    String name();

    @DBusBoundProperty(access = Access.READ, name = "DisplayName")
    String displayName();

    @DBusBoundProperty(access = Access.READ, name = "ShortName")
    String shortName();

    @DBusBoundProperty(access = Access.READ, name = "NativeName")
    String nativeName();

    @DBusBoundProperty(access = Access.READ, name = "HasVirtualName")
    boolean hasVirtualName();

    @DBusBoundProperty(access = Access.READ, name = "Peer")
    String peer();

    @DBusBoundProperty(access = Access.READ, name = "Table")
    String table();

    @DBusBoundProperty(access = Access.WRITE, name = "Mtu")
    void mtu(int mtu);

    void up() throws IOException;

    @DBusBoundProperty(access = Access.READ, name = "NetworkInterface")
	RemoteNetworkInterface getNetworkInterface();

}
