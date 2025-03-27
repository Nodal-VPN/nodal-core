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
import java.util.Collection;

import com.jadaptive.nodal.core.lib.AbstractUnixAddress;

public class KernelMacOsAddress extends AbstractUnixAddress<KernelMacOsPlatformService> {

	public KernelMacOsAddress(KernelMacOsPlatformService platform, String name, String nativeName) {
		super(name, nativeName, platform);
	}

	@Override
	public boolean isUp() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void delete() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void down() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void up() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getMac() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String displayName() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void setRoutes(Collection<String> allows) throws IOException {
		throw new UnsupportedOperationException("TODO");		
	}

}
