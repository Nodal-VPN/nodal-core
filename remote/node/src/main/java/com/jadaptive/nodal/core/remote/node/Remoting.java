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
