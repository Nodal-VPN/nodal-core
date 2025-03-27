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
package com.jadaptive.nodal.core.lib;

import java.util.Optional;

public final class StartRequest {

	public final static class Builder {
		private Optional<String> nativeInterfaceName = Optional.empty();
		private Optional<String> interfaceName = Optional.empty();
		private final VpnConfiguration configuration;
		private Optional<VpnPeer> peer = Optional.empty();
		
		public Builder(VpnConfiguration configuration) {
			this.configuration = configuration;
		}

		public Builder withInterfaceName(String interfaceName) {
			return withInterfaceName(Optional.of(interfaceName));
		}

		public Builder withInterfaceName(Optional<String> interfaceName) {
			this.interfaceName = interfaceName;
			return this;
		}

		public Builder withNativeInterfaceName(String nativeInterfaceName) {
			return withNativeInterfaceName(Optional.of(nativeInterfaceName));
		}

		public Builder withNativeInterfaceName(Optional<String> nativeInterfaceName) {
			this.nativeInterfaceName = nativeInterfaceName;
			return this;
		}

		public Builder withPeer(VpnPeer peer) {
			return withPeer(Optional.of(peer));
		}
		
		public Builder withPeer(Optional<VpnPeer> peer) {
			this.peer = peer;
			return  this;
		}
		

		public StartRequest build() {
			return new StartRequest(this);
		}
	}

	private final Optional<String> nativeInterfaceName;
	private final Optional<String> interfaceName;
	private final VpnConfiguration configuration;
	private final Optional<VpnPeer> peer;

	private StartRequest(Builder bldr) {
		if(bldr.nativeInterfaceName.isPresent() && !bldr.interfaceName.isPresent()) {
			throw new IllegalStateException("If a native interface name is provided, the wireguard interface name must also be supplied.");
		}
		this.nativeInterfaceName = bldr.nativeInterfaceName;
		this.interfaceName = bldr.interfaceName;
		this.configuration = bldr.configuration;
		this.peer = bldr.peer;
	}

	public Optional<String> nativeInterfaceName() {
		return nativeInterfaceName;
	}

	public Optional<String> interfaceName() {
		return interfaceName;
	}

	public VpnConfiguration configuration() {
		return configuration;
	}

	public Optional<VpnPeer> peer() {
		return peer;
	}
	
}
