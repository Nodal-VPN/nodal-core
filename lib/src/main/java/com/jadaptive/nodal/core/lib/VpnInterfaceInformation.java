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

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import uk.co.bithatch.nativeimage.annotations.Serialization;

@Serialization
public interface VpnInterfaceInformation extends Serializable {

    @SuppressWarnings("serial")
	VpnInterfaceInformation EMPTY = new VpnInterfaceInformation() {

        @Override
        public long rx() {
            return 0;
        }

        @Override
        public long tx() {
            return 0;
        }

        @Override
        public String interfaceName() {
            return "";
        }

        @Override
        public List<VpnPeerInformation> peers() {
            return Collections.emptyList();
        }

        @Override
        public Instant lastHandshake() {
            return Instant.ofEpochSecond(0);
        }

        @Override
        public Optional<String> error() {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> listenPort() {
            return Optional.empty();
        }

        @Override
        public Optional<Integer> fwmark() {
            return Optional.empty();
        }

        @Override
        public String publicKey() {
            return "";
        }

        @Override
        public String privateKey() {
            return "";
        }
    };
    
    String interfaceName();

    long tx();

    long rx();
    
    List<VpnPeerInformation> peers();

    Instant lastHandshake();
    
    String publicKey();
    
    String privateKey();
    
    /**
     * Actual listening port if it can be determined.
     * 
     * @return listening port or empty if cannot be determined
     */
    Optional<Integer> listenPort();
    
    Optional<Integer> fwmark();

    Optional<String> error();

    default Optional<VpnPeerInformation> peer(String publicKey) {
        for(var peer : peers()) {
            if(peer.publicKey().equals(publicKey))
                return Optional.of(peer);
        }
        return Optional.empty();
    }

}
