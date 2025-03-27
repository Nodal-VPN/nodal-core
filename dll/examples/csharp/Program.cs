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
﻿using System;
using System.Runtime.InteropServices;
            
IntPtr Isolate = new IntPtr();
IntPtr Thread = new IntPtr();
var cfg = new LbvDll.GraalCreateIsolateParams();

if (LbvDll.GraalCreateIsolate(ref cfg, out Isolate, out Thread) == 0)
{
    Console.WriteLine("Created Graal Isolate, Bringing Up VPN");    
    
    var VpnHandle = LbvDll.Up(Thread,  Marshal.StringToHGlobalAnsi(@"[Interface]
PrivateKey = yLXzXXJ1pFHuykSb7U2tl5aaS3zpyP6OrfHeav4wlVk=
Address = 172.16.0.1
DNS = 127.0.0.53

[Peer]
PublicKey = K69dPM6jfmg4kbDIpQH7y/VSIMPFHQGzFJWYy9rY8h0=
Endpoint = 92.233.249.6:51820
PersistentKeepalive = 35
AllowedIPs = 127.0.0.53, 172.16.0.0/24, 192.168.91.0/24"), 0, 0);
    if (VpnHandle > 0)
    {
        Console.WriteLine("VPN is Up");
    }
    else
    {
        Console.WriteLine("Failed to bring VPN up");
    }
}
else
{
    Console.WriteLine("Failed to create Graal Isolate, VPN will not work");
}