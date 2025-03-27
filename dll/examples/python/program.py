#
# Copyright ©2023-2025 LogonBox Ltd
# All changes post March 2025 Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this
# software and associated documentation files (the “Software”), to deal in the Software
# without restriction, including without limitation the rights to use, copy, modify,
# merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to the following
# conditions:
#
# The above copyright notice and this permission notice shall be included in all copies
# or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
# INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#

from ctypes import *

dll = CDLL("../../target/liblbv.so")
isolate = c_void_p()
isolatethread = c_void_p()
dll.graal_create_isolate(None, byref(isolate), byref(isolatethread))
dll.up.restype = c_long
result = dll.up(isolatethread, c_char_p(bytes("""
    [Interface]
    PrivateKey = SNG/stVFz0fyoa7LJU4/kMmzg5vmgTFR3GNu2o5q3WQ=
    Address = 172.16.11.1
    DNS = 172.16.1.101,jadaptive.local

    [Peer]
    PublicKey = OW9Im40fr3Lq6knUMy/mObQ2jr332ESXulZM9OannyI=
    Endpoint = 3.251.31.162:51820
    PersistentKeepalive = 30
    AllowedIPs = 172.16.11.0/24, 172.16.1.0/24
    """, "utf8")), 0, 0)
