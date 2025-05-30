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
import java.util.prefs.Preferences;

import com.sshtools.liftlib.ElevatedClosure;

import uk.co.bithatch.nativeimage.annotations.Serialization;

public class Prefs {
	public enum PrefType {
		STRING, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE;

		public Object parse(String val) {
			switch (this) {
			case BOOLEAN:
				return Boolean.parseBoolean(val);
			case INTEGER:
				return Integer.parseInt(val);
			case LONG:
				return Long.parseLong(val);
			case FLOAT:
				return Float.parseFloat(val);
			case DOUBLE:
				return Double.parseDouble(val);
			default:
				return val;
			}
		}

		public static PrefType getApparentType(String val) {
			if ("".equals(val)) {
				return PrefType.STRING;
			} else if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
				return PrefType.BOOLEAN;
			} else {
				try {
					Integer.parseInt(val);
					return PrefType.INTEGER;
				} catch (NumberFormatException nfe3) {
					try {
						Long.parseLong(val);
						return PrefType.LONG;
					} catch (NumberFormatException nfe4) {
						try {
							Float.parseFloat(val);
							return PrefType.FLOAT;
						} catch (NumberFormatException nfe) {
							try {
								Double.parseDouble(val);
								return PrefType.DOUBLE;
							} catch (NumberFormatException nfe2) {
								return PrefType.STRING;
							}
						}
					}
				}

			}
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public abstract static class AbstractPrivileged<R extends Serializable>
			implements ElevatedClosure<R, Serializable> {

		protected final boolean system;
		protected final String pathName;
		

		protected AbstractPrivileged(Preferences node) {
			this(!node.isUserNode(), node.absolutePath());
		}

		protected AbstractPrivileged(boolean system, String pathName) {
			this.system = system;
			this.pathName = pathName;
		}

		protected Preferences getPrefs() {
			var node = system ? Preferences.systemRoot() : Preferences.userRoot();
			var anode = node.node(pathName);
			return anode;
		}
	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class GetValue extends AbstractPrivileged<String> {

		private final String key;
		private final String defaultValue;
		
		public GetValue(Preferences node, String key, String defaultValue) {
			super(node);
			this.key = key;
			this.defaultValue = defaultValue;
		}

		public GetValue(boolean system, String pathName, String key, String defaultValue) {
			super(system, pathName);
			this.key = key;
			this.defaultValue = defaultValue;
		}

		@Override
		public String call(ElevatedClosure<String, Serializable> arg0) throws Exception {
			return getPrefs().get(key, defaultValue);
		}

	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class RemoveKey extends AbstractPrivileged<String> {

		private final String key;
		
		public RemoveKey(Preferences node, String key) {
			super(node);
			this.key = key;
		}

		public RemoveKey(boolean system, String pathName, String key) {
			super(system, pathName);
			this.key = key;
		}

		@Override
		public String call(ElevatedClosure<String, Serializable> arg0) throws Exception {
			Preferences prefs = getPrefs();
			var val = prefs.get(key, null);
			prefs.remove(key);
			prefs.flush();
			return val;
		}

	}

	@SuppressWarnings("serial")
	@Serialization
	public final static class PutValue extends AbstractPrivileged<Serializable> {

		private final String key;
		private Object value;
		private PrefType type;

		public PutValue(Preferences node, String key, Object value, PrefType type) {
			super(node);
			this.key = key;
			this.value = value;
			this.type = type;
		}

		public PutValue(boolean system, String pathName, String key, Object value, PrefType type) {
			super(system, pathName);
			this.key = key;
			this.value = value;
			this.type = type;
		}

		@Override
		public Serializable call(ElevatedClosure<Serializable, Serializable> arg0) throws Exception {
			var prefs = getPrefs();
			switch (type) {
			case STRING:
				prefs.put(key, (String) value);
				break;
			case BOOLEAN:
				prefs.putBoolean(key, (Boolean) value);
				break;
			case DOUBLE:
				prefs.putDouble(key, (Double) value);
				break;
			case FLOAT:
				prefs.putFloat(key, (Float) value);
				break;
			case INTEGER:
				prefs.putInt(key, (Integer) value);
				break;
			case LONG:
				prefs.putLong(key, (Long) value);
				break;
			default:
				prefs.put(key, String.valueOf(value));
				break;
			}
			prefs.flush();
			return null;
		}

	}
}
