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
package com.jadaptive.nodal.core.lib.util.impl;


import java.util.Base64;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.whispersystems.curve25519.JavaCurve25519Provider;

import com.jadaptive.nodal.core.lib.util.Keys.KeyPair;
import com.jadaptive.nodal.core.lib.util.Keys.KeyPairProvider;

import uk.co.bithatch.nativeimage.annotations.OtherReflectable;
import uk.co.bithatch.nativeimage.annotations.OtherReflectables;
import uk.co.bithatch.nativeimage.annotations.Reflectable;

@OtherReflectables(
    @OtherReflectable(all = true, value = JavaCurve25519Provider.class)
)
@Reflectable
public class WhisperKeys implements KeyPairProvider {

	private static class KeyPairImpl implements KeyPair {
		private final Curve25519KeyPair keyPair;
		private final Curve25519 provider;

		public KeyPairImpl(Curve25519KeyPair keyPair, Curve25519 provider) {
			this.keyPair = keyPair;
			this.provider = provider;
		}

		@Override
		public byte[] getPublicKey() {
			return keyPair.getPublicKey();
		}

		@Override
		public byte[] getPrivateKey() {
			return keyPair.getPrivateKey();
		}

		@Override
		public final byte[] agreement() {
			return provider.calculateAgreement(getPublicKey(), getPublicKey());
		}

		@Override
		public final byte[] sign(byte[] data) {
			return provider.calculateSignature(getPrivateKey(), data);
		}

	}

	private final Curve25519 provider;

	public WhisperKeys() {
		provider = Curve25519.getInstance(Curve25519.JAVA);
	}

	@Override
	public KeyPair genkey() {
		return new KeyPairImpl(provider.generateKeyPair(), provider);
	}

	@Override
	public KeyPair pubkey(byte[] privateKey) {
		/* TODO no API for this! horrible hack that always uses pure Java basic provider */
		var basic = new BasicKeys();
		var pubkeyPair = basic.pubkey(privateKey);
		return new KeyPair() {
			
			@Override
			public byte[] sign(byte[] data) {
				return provider.calculateSignature(getPrivateKey(), data);
			}
			
			@Override
			public byte[] getPublicKey() {
				return pubkeyPair.getPublicKey();
			}
			
			@Override
			public byte[] getPrivateKey() {
				return privateKey;
			}
			
			@Override
			public byte[] agreement() {
				return provider.calculateAgreement(getPublicKey(), getPublicKey());
			}
		};
	}

	@Override
	public boolean verify(byte[] publicKey, byte[] data, byte[] sig) {
		return provider.verifySignature(publicKey, data, sig);
	}

    public static  void main(String[] args) {
		//var prikey = Base64.getDecoder().decode("O+F8ZCJK45oWdatKccPXruuvojilgBaS97KLfCvx754=");
		/// hrm .... seems to be the culprit. The difference in privatekey that wireguard shows in wg showconf
		// compared to what we actually have in the interface. The last and first byte is different!
		var prikey = Base64.getDecoder().decode("OOF8ZCJK45oWdatKccPXruuvojilgBaS97KLfCvx714=");
		
		var prov = new WhisperKeys();
		var keypair = prov.pubkey(prikey);
		System.out.println("Auto: " + keypair.getBase64PublicKey() + " / " + keypair.getBase64PrivateKey());
		
//		var bsc = new BasicKeys();
//		var kp2 = bsc.pubkey(prikey);
//		System.out.println("Bsc: " + kp2.getBase64PublicKey() + " / " + keypair.getBase64PrivateKey());
		
		var data = "SOMETHING TO SIGN".getBytes();
		var sig = keypair.sign(data);
		var ver = prov.verify(keypair.getPublicKey(), data, sig);
		System.out.println("VER: " + ver);
		
		
		
	}
}
