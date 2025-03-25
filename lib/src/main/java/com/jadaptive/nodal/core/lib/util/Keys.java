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
package com.jadaptive.nodal.core.lib.util;

import java.util.Base64;
import java.util.ServiceLoader;

public class Keys {

	public interface KeyPair {

		byte[] getPublicKey();

		default String getBase64PublicKey() {
			return Base64.getEncoder().encodeToString(getPublicKey());
		}

		byte[] getPrivateKey();

		default String getBase64PrivateKey() {
			return Base64.getEncoder().encodeToString(getPrivateKey());
		}

		byte[] agreement();

		byte[] sign(byte[] data);

	}

	public interface KeyPairProvider {

		default boolean verifyBase64(String publicKey, String data, String sig) {
			var decoder = Base64.getDecoder();
			return verify(decoder.decode(publicKey), decoder.decode(data), decoder.decode(sig));
		}

		boolean verify(byte[] publicKey, byte[] data, byte[] sig);

		KeyPair genkey();

		default KeyPair pubkey(String base64PrivateKey) {
			return pubkey(Base64.getDecoder().decode(base64PrivateKey));
		}

		KeyPair pubkey(byte[] privateKey);
	}

	private Keys() {
	}

	public static boolean verify(byte[] publicKey, byte[] data, byte[] sig) {
		for (var prov : ServiceLoader.load(KeyPairProvider.class, Keys.class.getClassLoader())) {
			try {
				return prov.verify(publicKey, data, sig);
			} catch (UnsupportedOperationException uoe) {
			}
		}
		throw new UnsupportedOperationException();
	}

	public static boolean verifyBase64(String publicKey, String data, String sig) {
		var decoder = Base64.getDecoder();
		return verify(decoder.decode(publicKey), decoder.decode(data), decoder.decode(sig));
	}

	public static KeyPair genkey() {
		for (var prov : ServiceLoader.load(KeyPairProvider.class, Keys.class.getClassLoader())) {
			try {
				return prov.genkey();
			} catch (UnsupportedOperationException uoe) {
			}
		}
		throw new UnsupportedOperationException();

	}

	public static KeyPair pubkeyBase64(String base64PrivateKey) {
		for (var prov : ServiceLoader.load(KeyPairProvider.class, Keys.class.getClassLoader())) {
			try {
				return prov.pubkey(Base64.getDecoder().decode(base64PrivateKey));
			} catch (UnsupportedOperationException uoe) {
			}
		}
		throw new UnsupportedOperationException();
	}

	public static KeyPair pubkey(byte[] privateKey) {
		for (var prov : ServiceLoader.load(KeyPairProvider.class, Keys.class.getClassLoader())) {
			try {
				return prov.pubkey(privateKey);
			} catch (UnsupportedOperationException uoe) {
			}
		}
		throw new UnsupportedOperationException();
	}
	
	public static  void main(String[] args) {
		//var prikey = Base64.getDecoder().decode("O+F8ZCJK45oWdatKccPXruuvojilgBaS97KLfCvx754=");
		/// hrm .... seems to be the culprit. The difference in privatekey that wireguard shows in wg showconf
		// compared to what we actually have in the interface. The last and first byte is different!
		var prikey = Base64.getDecoder().decode("OOF8ZCJK45oWdatKccPXruuvojilgBaS97KLfCvx714=");
		
		var keypair = Keys.pubkey(
				prikey
			);
		System.out.println("Auto: " + keypair.getBase64PublicKey() + " / " + keypair.getBase64PrivateKey());
		
//		var bsc = new BasicKeys();
//		var kp2 = bsc.pubkey(prikey);
//		System.out.println("Bsc: " + kp2.getBase64PublicKey() + " / " + keypair.getBase64PrivateKey());
		
		var data = "SOMETHING TO SIGN".getBytes();
		var sig = keypair.sign(data);
		var ver = Keys.verify(keypair.getPublicKey(), data, sig);
		System.out.println("VER: " + ver);
		
		
		
	}

}
