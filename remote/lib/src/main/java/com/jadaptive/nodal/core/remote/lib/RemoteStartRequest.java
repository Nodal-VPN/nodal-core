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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.VpnConfiguration;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteStartRequest extends Struct {

    @Position(0)
    private String nativeInterfaceName = "";

    @Position(1)
    private String interfaceName = "";

    @Position(2)
    private String configuration = "";

    @Position(3)
    private RemoteVpnPeer peer = new RemoteVpnPeer();

    public RemoteStartRequest() {
    }

    public RemoteStartRequest(StartRequest nativeStartRequest) {
        this.nativeInterfaceName = nativeStartRequest.nativeInterfaceName().orElse("");
        this.interfaceName = nativeStartRequest.interfaceName().orElse("");
        this.configuration = nativeStartRequest.configuration().write();
        this.peer = nativeStartRequest.peer().map(r -> new RemoteVpnPeer(r)).orElseGet(() -> new RemoteVpnPeer());
    }

    public RemoteStartRequest(String nativeInterfaceName, String interfaceName, String configuration, RemoteVpnPeer peer) {
		super();
		this.nativeInterfaceName = nativeInterfaceName;
		this.interfaceName = interfaceName;
		this.configuration = configuration;
		this.peer = peer;
	}

	public String getNativeInterfaceName() {
        return nativeInterfaceName;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getConfiguration() {
        return configuration;
    }

    public RemoteVpnPeer getPeer() {
        return peer;
    }

    public StartRequest toNative() {
        try {
            var cfg = new VpnConfiguration.Builder().fromFileContent(configuration).build();
            var bldr = new StartRequest.Builder(cfg);
            if (!nativeInterfaceName.equals(""))
                bldr.withNativeInterfaceName(nativeInterfaceName);
            if (!interfaceName.equals(""))
                bldr.withInterfaceName(interfaceName);
            if(peer.valid())
                bldr.withPeer(peer.toNative());
            return bldr.build();
        } catch(IOException  ioe) {
            throw new UncheckedIOException(ioe);
        }catch (ParseException pe) {
            throw new IllegalStateException(pe);
        }
    }
}
