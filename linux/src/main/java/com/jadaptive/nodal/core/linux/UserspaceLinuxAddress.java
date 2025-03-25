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

import java.io.File;
import java.io.IOException;

import com.jadaptive.nodal.core.lib.util.OsUtil;

public class UserspaceLinuxAddress extends AbstractLinuxAddress {

    UserspaceLinuxAddress(String name, String nativeName, AbstractLinuxPlatformService platform) {
        super(name, nativeName, platform);
    }

    @Override
    protected void onDelete() throws IOException {
        commands.privileged().logged().result(OsUtil.debugCommandArgs("rm", "-f", getSocketFile().getAbsolutePath()));
    }

    @Override
    public boolean isUp() {
        return getSocketFile().exists();
    }

    protected File getSocketFile() {
        return new File(LinuxDNSProviderFactory.runPath().toString() + "/wireguard/" + nativeName() + ".sock");
    }

}
