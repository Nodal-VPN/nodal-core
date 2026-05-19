/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.jadaptive.nodal.core.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

/**
 * JNA bindings for Linux Netlink socket operations used to communicate
 * with the WireGuard kernel module via Generic Netlink.
 */
public interface CLibrary extends Library {

    CLibrary INSTANCE = Native.load("c", CLibrary.class);

    // Socket constants
    int AF_NETLINK = 16;
    int SOCK_DGRAM = 2;
    int SOCK_CLOEXEC = 0x80000;
    int NETLINK_GENERIC = 16;

    // Netlink message types
    int NLMSG_DONE = 3;
    int NLMSG_ERROR = 2;
    int NLMSG_MIN_TYPE = 0x10;

    // Netlink message flags
    int NLM_F_REQUEST = 0x01;
    int NLM_F_ACK = 0x04;
    int NLM_F_DUMP = 0x300;

    // Generic Netlink controller
    int GENL_ID_CTRL = NLMSG_MIN_TYPE;

    // Generic Netlink controller commands
    int CTRL_CMD_GETFAMILY = 3;

    // Generic Netlink controller attributes
    int CTRL_ATTR_FAMILY_ID = 1;
    int CTRL_ATTR_FAMILY_NAME = 2;

    // Netlink header length (16 bytes)
    int NLMSG_HDRLEN = 16;

    // Generic Netlink header length (4 bytes)
    int GENL_HDRLEN = 4;

    // Netlink alignment
    int NLMSG_ALIGNTO = 4;

    // --- Netlink structures ---

    @Structure.FieldOrder({"nl_family", "nl_pad", "nl_pid", "nl_groups"})
    class SockaddrNl extends Structure {
        public short nl_family = AF_NETLINK;
        public short nl_pad;
        public int nl_pid;
        public int nl_groups;
    }

    // --- Syscalls ---

    int socket(int domain, int type, int protocol);

    int bind(int sockfd, SockaddrNl addr, int addrlen);

    int sendto(int sockfd, byte[] buf, int len, int flags, SockaddrNl dest, int destLen);

    int recvfrom(int sockfd, byte[] buf, int len, int flags, SockaddrNl src, int[] srcLen);

    int close(int fd);

    int getpid();

    String strerror(int errnum);

    /**
     * Utility to align a value to NLMSG_ALIGNTO (4-byte boundary).
     */
    static int nlmsgAlign(int len) {
        return (len + NLMSG_ALIGNTO - 1) & ~(NLMSG_ALIGNTO - 1);
    }
}
