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
package com.jadaptive.nodal.core.linux;

import java.io.IOException;
import java.text.MessageFormat;

import com.jadaptive.nodal.core.lib.NativeComponents.Tool;
import com.jadaptive.nodal.core.lib.SystemContext;

public class UserspaceLinuxPlatformService extends AbstractLinuxPlatformService {

    public UserspaceLinuxPlatformService(SystemContext context) {
        super(context);
    }

    @Override
    protected AbstractLinuxAddress add(String name, String nativeName, String type) throws IOException {
        context().commands().privileged().logged().result(context().nativeComponents().tool(Tool.WIREGUARD_GO), nativeName);
        return find(nativeName, addresses()).orElseThrow(() -> new IOException(MessageFormat.format("Could not find new network interface {0}", nativeName)));
    }

    @Override
    protected AbstractLinuxAddress createAddress(String name, String nativeName) {
        return new UserspaceLinuxAddress(name, nativeName, this);
    }
}
