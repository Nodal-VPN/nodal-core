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

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.InterfaceAddressInfo;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteInterfaceAddress extends Struct implements InterfaceAddressInfo {

    @Position(0)
    private String address;
    @Position(1)
    private String broadcast;
    @Position(2)
    private int networkPrefixLength;
    
    public RemoteInterfaceAddress(InterfaceAddressInfo nativeInterfaceAddress) {
        this.address= nativeInterfaceAddress.getAddress();
        this.broadcast = nativeInterfaceAddress.getBroadcast();
        this.networkPrefixLength = nativeInterfaceAddress.getNetworkPrefixLength();
    }
    
    public RemoteInterfaceAddress() {
    }

	public RemoteInterfaceAddress(String address, String broadcast, int networkPrefixLength) {
		super();
		this.address = address;
		this.broadcast = broadcast;
		this.networkPrefixLength = networkPrefixLength;
	}

	@Override
	public final String getAddress() {
		return address;
	}

	@Override
	public final String getBroadcast() {
		return broadcast;
	}

	@Override
	public final int getNetworkPrefixLength() {
		return networkPrefixLength;
	}
    
    
    
}
