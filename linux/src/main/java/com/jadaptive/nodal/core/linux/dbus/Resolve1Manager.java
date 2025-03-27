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
package com.jadaptive.nodal.core.linux.dbus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;

import com.jadaptive.nodal.core.lib.ipmath.Ipv4;
import com.jadaptive.nodal.core.lib.ipmath.Ipv6;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@DBusInterfaceName("org.freedesktop.resolve1.Manager")
@Proxy
public interface Resolve1Manager extends DBusInterface {
	static final int AF_INET6 = 10;
	static final int AF_INET = 2;


    @Reflectable
    @TypeReflect(fields = true)
	public class SetLinkDNSStruct extends Struct {

		@Position(0)
		private int addressFamily;

		@Position(1)
		private List<Byte> address;

		public SetLinkDNSStruct(String address) {
			try {
				Ipv6 v6 = Ipv6.of(address);
				addressFamily = AF_INET6;
				this.address = Arrays.asList(toObjects(v6.asBigInteger().toByteArray()));
			} catch (IllegalArgumentException iae) {
				Ipv4 v4 = Ipv4.of(address);
				addressFamily = AF_INET;
				this.address = Arrays.asList(toObjects(intToBytes(v4.asBigInteger().intValue())));
			}
		}

		public SetLinkDNSStruct(int addressFamily, List<Byte> address) {
			this.addressFamily = addressFamily;
			this.address = address;
		}

		public int getAddressFamily() {
			return addressFamily;
		}

		public List<Byte> getAddress() {
			return address;
		}
	}

    @Reflectable
    @TypeReflect(fields = true)
	public class SetLinkDomainsStruct extends Struct {
		@Position(0)
		private final String domain;
		@Position(1)
		private final boolean searchDomain;

		public SetLinkDomainsStruct(String domain, boolean searchDomain) {
			this.domain = domain;
			this.searchDomain = searchDomain;
		}

		public String getDomain() {
			return domain;
		}

		public boolean getMember1() {
			return searchDomain;
		}

	}
    
    @Reflectable
    @TypeReflect(fields = true)
    public class RootDNSPropertyStruct extends Struct {

        @Position(0)
        private int index;

        @Position(1)
        private int addressFamily;

        @Position(2)
        private List<Byte> address;


        public RootDNSPropertyStruct(int index, int addressFamily, List<Byte> address) {
            this.index = index;
            this.addressFamily = addressFamily;
            this.address = address;
        }

        @SuppressWarnings("unchecked")
        public RootDNSPropertyStruct(Object[] arr) {
            index = (Integer)arr[0];
            addressFamily = (Integer)arr[1];
            address = (List<Byte>)arr[2];
        }

        public int getIndex() {
            return index;
        }

        public int getAddressFamily() {
            return addressFamily;
        }

        public List<Byte> getAddress() {
            return address;
        }
    }

    @Reflectable
    @TypeReflect(fields = true)
    public class RootDomainsPropertyStruct extends Struct {
        @Position(0)
        private final int index;
        @Position(1)
        private final String domain;
        @Position(2)
        private final boolean searchDomain;

        public RootDomainsPropertyStruct(int index, String domain, boolean searchDomain) {
            this.index = index;
            this.domain = domain;
            this.searchDomain = searchDomain;
        }
        
        public RootDomainsPropertyStruct(Object[] arr) {
            index = (Integer) arr[0];
            domain = (String)arr[1];
            searchDomain = (Boolean)arr[2];
        }

        public int getIndex() {
            return index;
        }

        public String getDomain() {
            return domain;
        }

        public boolean getMember1() {
            return searchDomain;
        }

    }

	/**
	 * The SetLinkDNS() method sets the DNS servers to use on a specific interface.
	 * This call (and the following ones) may be used by network management software
	 * to configure per-interface DNS settings. It takes a network interface index
	 * as well as an array of DNS server IP address records. Each array item
	 * consists of an address family (either AF_INET or AF_INET6), followed by a
	 * 4-byte or 16-byte array with the raw address data. This call is a one-call
	 * shortcut for retrieving the Link object for a network interface using
	 * GetLink() (see above) and then invoking the SetDNS() call (see below) on it.
	 * 
	 * Network management software integrating with resolved is recommended to
	 * invoke this method (and the five below) after the interface appeared in the
	 * kernel (and thus after a network interface index has been assigned) but
	 * before the network interfaces is activated (set IFF_UP on) so that all
	 * settings take effect during the full time the network interface is up. It is
	 * safe to alter settings while the interface is up, however. Use the
	 * RevertLink() (described below) to reset all per-interface settings made.
	 * 
	 * @param index     index
	 * @param addresses addresses
	 */
	void SetLinkDNS(int index, List<SetLinkDNSStruct> addresses);

	/**
	 * The SetLinkDomains() method sets the search and routing domains to use on a
	 * specific network interface for DNS look-ups. It take a network interface
	 * index plus an array of domains, each with a boolean parameter indicating
	 * whether the specified domain shall be used as search domain (false), or just
	 * as routing domain (true). Search domains are used for qualifying single-label
	 * names into FQDN when looking up hostnames, as well as for making routing
	 * decisions on which interface to send queries ending in the domain to. Routing
	 * domains are not used for single-label name qualification, and are only used
	 * for routing decisions. Pass the search domains in the order they shall be
	 * used.
	 * 
	 * @param index   index
	 * @param domains domains
	 */
	void SetLinkDomains(int index, List<SetLinkDomainsStruct> domains);

	/**
	 * The RevertLink() method may be used to revert all per-link settings done with
	 * the six calls described above to the defaults again.
	 * 
	 * @param index index
	 */
	void RevertLink(int index);

	/**
	 *  Flush caches
	 */
	void FlushCaches();
	
	DBusPath GetLink(int index);

    static Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        Arrays.setAll(bytes, n -> bytesPrim[n]);

        return bytes;
    }

    static  byte[] intToBytes(int myInteger){
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(myInteger).array();
    }
}
