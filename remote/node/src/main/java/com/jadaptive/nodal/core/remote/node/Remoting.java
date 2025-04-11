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
package com.jadaptive.nodal.core.remote.node;

public class Remoting {

	public static String unescapeNameForDBus(String name) {
		var b = new StringBuilder();
		var l = name.length();
		for(var i = 0 ; i < l ; i++) {
			if(name.startsWith("_0x")) {
				name = name.substring(3);
				i += 3;
				while(!name.startsWith("_")) {
					b.append((char)Integer.parseInt(name.substring(0, 2)));
					name = name.substring(2);
					i += 2;
				}
				name = name.substring(1);
			}
			else
				b.append(name.charAt(i));
		}
		return b.toString();
	}

	public static String escapeNameForDBus(String name) {
		var b = new StringBuilder();
		var e = false;
		for(var c : name.toCharArray()) {
			if((c >= 'a' && c <= 'z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))  {
				if(e) {
					b.append('_');
					e = false;
				}
				b.append(c);
			}
			else {
				if(!e) {
					b.append("_0x");
					e = true;
				}
				b.append(String.format("%02x", (int)c));
			}
		}
		return b.toString();
	}
}
