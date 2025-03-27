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
package com.jadaptive.nodal.core.macos;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.AbstractUnixDesktopPlatformService;
import com.jadaptive.nodal.core.lib.PlatformService.Gateway;
import com.jadaptive.nodal.core.lib.StartRequest;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.VpnAdapter;

public class KernelMacOsPlatformService extends AbstractUnixDesktopPlatformService<KernelMacOsAddress> {

	final static Logger LOG = LoggerFactory.getLogger(KernelMacOsPlatformService.class);

	private static final String INTERFACE_PREFIX = "wg";

	public KernelMacOsPlatformService(SystemContext context) {
		super(INTERFACE_PREFIX, context);
	}

	@Override
	public Optional<Gateway> defaultGateway()  {
		return UserspaceMacOsPlatformService.getDefaultGateway(context());
	}

	@Override
	protected KernelMacOsAddress createVirtualInetAddress(NetworkInterface nif) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	protected void onStart(StartRequest startRequest, VpnAdapter session)
			throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

    @Override
    protected void runCommand(List<String> commands) throws IOException {
        context().commands().privileged().logged().run(commands.toArray(new String[0]));
    }

    @Override
    public List<KernelMacOsAddress> addresses() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    protected KernelMacOsAddress add(String name, String nativeName, String type) throws IOException {
        throw new UnsupportedOperationException("TODO");
    }


}
