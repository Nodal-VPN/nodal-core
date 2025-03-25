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

import java.util.Optional;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

import com.jadaptive.nodal.core.lib.NATMode;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(fields = true, constructors = true)
public class RemoteNATMode extends Struct {

    @Position(0)
    private String mode = "";
    @Position(1)
    private String[] names = new String[0];
    
    public RemoteNATMode(Optional<NATMode> nativeNATMode) {
        this.mode = nativeNATMode.isPresent() ?  nativeNATMode.get().getClass().getSimpleName() : "";
        this.names = nativeNATMode.isPresent() ?  nativeNATMode.get().names().toArray(new String[0]) : new String[0];
    }
    
    public RemoteNATMode() {
    }
    
    public RemoteNATMode(String mode, String[] names) {
		super();
		this.mode = mode;
		this.names = names;
	}

	public Optional<NATMode> toNative() {
        if(mode.equals(NATMode.MASQUERADE.class.getSimpleName())) {
            return Optional.of(NATMode.MASQUERADE.forNames(names));
        }
        else if(mode.equals(NATMode.SNAT.class.getSimpleName())) {
            return Optional.of(NATMode.SNAT.forNames(names));
        }
        else if(mode.equals("")){
            return Optional.empty();
        }
        else {
            throw new UnsupportedOperationException("Unsupported NAT mode.");
        }
    }

	public String getMode() {
		return mode;
	}

	public String[] getNames() {
		return names;
	}

    
}
