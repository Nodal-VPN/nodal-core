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

import java.util.Arrays;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.NetworkInterfaceInfo;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteNetworkInterface extends Struct implements NetworkInterfaceInfo<RemoteInterfaceAddress> {

	@Position(0)
	private String name;
	@Position(1)
	private String displayName;
	@Position(2)
	private String hardwareAddress;
	@Position(3)
	private int mtu;
	@Position(4)
	private int index;
	@Position(5)
	private RemoteInterfaceAddress[] interfaceAddresses;

	public RemoteNetworkInterface(NetworkInterfaceInfo<?> nativeInterface) {
		this.name = nativeInterface.getName();
		this.displayName = nativeInterface.getDisplayName();
		this.hardwareAddress = nativeInterface.getHardwareAddress();
		this.mtu = nativeInterface.getMtu();
		this.index = nativeInterface.getIndex();
		this.interfaceAddresses = Arrays.asList(nativeInterface.getInterfaceAddresses()).stream().map(RemoteInterfaceAddress::new).toList().toArray(new RemoteInterfaceAddress[0]);
	}

	public RemoteNetworkInterface() {
	}

	public RemoteNetworkInterface(String name, String displayName, String hardwareAddress, int mtu, int index,
			RemoteInterfaceAddress[] interfaceAddresses) {
		super();
		this.name = name;
		this.displayName = displayName;
		this.hardwareAddress = hardwareAddress;
		this.mtu = mtu;
		this.index = index;
		this.interfaceAddresses = interfaceAddresses;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final String getDisplayName() {
		return displayName;
	}

	@Override
	public final String getHardwareAddress() {
		return hardwareAddress;
	}

	@Override
	public final int getMtu() {
		return mtu;
	}

	@Override
	public final int getIndex() {
		return index;
	}

	@Override
	public final RemoteInterfaceAddress[] getInterfaceAddresses() {
		return interfaceAddresses;
	}


}
