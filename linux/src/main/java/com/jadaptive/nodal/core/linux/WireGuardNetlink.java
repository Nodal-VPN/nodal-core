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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.VpnAdapterConfiguration;
import com.jadaptive.nodal.core.lib.WireGuardUAPI;
import com.jadaptive.nodal.core.lib.util.Keys;

/**
 * Communicates with the WireGuard kernel module via Generic Netlink,
 * eliminating the need to fork the {@code wg} command-line tool on
 * systems using kernel WireGuard (where no UAPI socket exists).
 * <p>
 * The WireGuard kernel module registers a Generic Netlink family named
 * {@code "wireguard"} with two commands:
 * <ul>
 *   <li>{@code WG_CMD_GET_DEVICE} (0) — retrieve device configuration and statistics</li>
 *   <li>{@code WG_CMD_SET_DEVICE} (1) — set device configuration</li>
 * </ul>
 * <p>
 * This implementation uses JNA to call the Linux socket/sendto/recvfrom syscalls
 * directly with {@code AF_NETLINK} / {@code NETLINK_GENERIC}.
 *
 * @see <a href="https://www.wireguard.com/xplatform/#linux-kernel-module">WireGuard Linux Kernel Module</a>
 */
public final class WireGuardNetlink {

    private static final Logger LOG = LoggerFactory.getLogger(WireGuardNetlink.class);

    // WireGuard Generic Netlink family name
    private static final String WG_GENL_NAME = "wireguard";
    private static final int WG_GENL_VERSION = 1;

    // WireGuard commands
    static final int WG_CMD_GET_DEVICE = 0;
    static final int WG_CMD_SET_DEVICE = 1;

    // WireGuard device attributes (WGDEVICE_A_*)
    static final int WGDEVICE_A_UNSPEC = 0;
    static final int WGDEVICE_A_IFINDEX = 1;
    static final int WGDEVICE_A_IFNAME = 2;
    static final int WGDEVICE_A_PRIVATE_KEY = 3;
    static final int WGDEVICE_A_PUBLIC_KEY = 4;
    static final int WGDEVICE_A_FLAGS = 5;
    static final int WGDEVICE_A_LISTEN_PORT = 6;
    static final int WGDEVICE_A_FWMARK = 7;
    static final int WGDEVICE_A_PEERS = 8;

    // WireGuard peer attributes (WGPEER_A_*)
    static final int WGPEER_A_UNSPEC = 0;
    static final int WGPEER_A_PUBLIC_KEY = 1;
    static final int WGPEER_A_PRESHARED_KEY = 2;
    static final int WGPEER_A_FLAGS = 3;
    static final int WGPEER_A_ENDPOINT = 4;
    static final int WGPEER_A_PERSISTENT_KEEPALIVE_INTERVAL = 5;
    static final int WGPEER_A_LAST_HANDSHAKE_TIME = 6;
    static final int WGPEER_A_RX_BYTES = 7;
    static final int WGPEER_A_TX_BYTES = 8;
    static final int WGPEER_A_ALLOWEDIPS = 9;
    static final int WGPEER_A_PROTOCOL_VERSION = 10;

    // WireGuard allowed IP attributes (WGALLOWEDIP_A_*)
    static final int WGALLOWEDIP_A_UNSPEC = 0;
    static final int WGALLOWEDIP_A_FAMILY = 1;
    static final int WGALLOWEDIP_A_IPADDR = 2;
    static final int WGALLOWEDIP_A_CIDR_MASK = 3;

    // WireGuard device flags
    static final int WGDEVICE_F_REPLACE_PEERS = 1;

    // WireGuard peer flags
    static final int WGPEER_F_REMOVE_ME = 1;
    static final int WGPEER_F_REPLACE_ALLOWEDIPS = 2;

    // Netlink attribute type flag for nested attributes
    static final int NLA_F_NESTED = 0x8000;

    // Address families
    static final int AF_INET = 2;
    static final int AF_INET6 = 10;

    // Sequence counter
    private final AtomicInteger seqCounter = new AtomicInteger(1);

    // Cached family ID
    private volatile int familyId = -1;

    /**
     * Check if the WireGuard Generic Netlink family is available (i.e. the
     * kernel module is loaded).
     *
     * @return true if the wireguard Netlink family can be resolved
     */
    public boolean isAvailable() {
        try {
            resolveFamilyId();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the full device state, equivalent to {@code wg show <iface> dump}.
     *
     * @param interfaceName the network interface name
     * @return parsed device state
     * @throws IOException if the Netlink communication fails
     */
    public WireGuardUAPI.DeviceState getDevice(String interfaceName) throws IOException {
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();

        // Build the request: GENL header + WGDEVICE_A_IFNAME attribute
        var ifnameBytes = (interfaceName + "\0").getBytes(StandardCharsets.US_ASCII);
        var attrLen = 4 + ifnameBytes.length; // NLA header (4) + payload
        var paddedAttrLen = CLibrary.nlmsgAlign(attrLen);
        var payloadLen = CLibrary.GENL_HDRLEN + paddedAttrLen;
        var totalLen = CLibrary.NLMSG_HDRLEN + payloadLen;

        var buf = ByteBuffer.allocate(CLibrary.nlmsgAlign(totalLen));
        buf.order(ByteOrder.nativeOrder());

        // Netlink header
        buf.putInt(totalLen);                                    // nlmsg_len
        buf.putShort((short) familyId);                          // nlmsg_type
        buf.putShort((short) (CLibrary.NLM_F_REQUEST | CLibrary.NLM_F_DUMP)); // nlmsg_flags
        buf.putInt(seq);                                         // nlmsg_seq
        buf.putInt(0);                                           // nlmsg_pid (let kernel assign)

        // Generic Netlink header
        buf.put((byte) WG_CMD_GET_DEVICE);                       // cmd
        buf.put((byte) WG_GENL_VERSION);                         // version
        buf.putShort((short) 0);                                 // reserved

        // WGDEVICE_A_IFNAME attribute
        buf.putShort((short) attrLen);                           // nla_len
        buf.putShort((short) WGDEVICE_A_IFNAME);                 // nla_type
        buf.put(ifnameBytes);
        // Pad to alignment
        while (buf.position() < CLibrary.NLMSG_HDRLEN + CLibrary.GENL_HDRLEN + paddedAttrLen) {
            buf.put((byte) 0);
        }

        var response = netlinkTransaction(buf.array(), buf.position(), seq);
        return parseGetDeviceResponse(interfaceName, response);
    }

    /**
     * Set device configuration, equivalent to {@code wg setconf}.
     *
     * @param interfaceName the network interface name
     * @param config        the adapter configuration
     * @throws IOException if the Netlink communication fails
     */
    public void setConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();

        var msg = buildSetDeviceMessage(familyId, seq, interfaceName, config, true);
        var response = netlinkTransaction(msg, msg.length, seq);
        checkAckResponse(response);
    }

    /**
     * Synchronize configuration, equivalent to {@code wg syncconf}.
     *
     * @param interfaceName the network interface name
     * @param config        the adapter configuration
     * @throws IOException if the Netlink communication fails
     */
    public void syncConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        // Get current peers
        var current = getDevice(interfaceName);
        var currentPeerKeys = new java.util.HashSet<String>();
        for (var peer : current.peers()) {
            currentPeerKeys.add(peer.publicKey());
        }
        var newPeerKeys = new java.util.HashSet<String>();
        for (var peer : config.peers()) {
            newPeerKeys.add(peer.publicKey());
        }

        // Remove peers not in new config
        for (var existingKey : currentPeerKeys) {
            if (!newPeerKeys.contains(existingKey)) {
                removePeer(interfaceName, existingKey);
            }
        }

        // Set the config (without replace_peers, so existing peers that
        // are also in the new config are updated, not removed)
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();
        var msg = buildSetDeviceMessage(familyId, seq, interfaceName, config, false);
        var response = netlinkTransaction(msg, msg.length, seq);
        checkAckResponse(response);
    }

    /**
     * Append peers to configuration, equivalent to {@code wg addconf}.
     *
     * @param interfaceName the network interface name
     * @param config        the adapter configuration (only peers are used)
     * @throws IOException if the Netlink communication fails
     */
    public void appendConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();
        var msg = buildSetDeviceMessage(familyId, seq, interfaceName, config, false);
        var response = netlinkTransaction(msg, msg.length, seq);
        checkAckResponse(response);
    }

    /**
     * Remove a peer by public key.
     *
     * @param interfaceName the network interface name
     * @param publicKey     the base64-encoded public key
     * @throws IOException if the Netlink communication fails
     */
    public void removePeer(String interfaceName, String publicKey) throws IOException {
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();

        // Build minimal set message with the peer flagged for removal
        var buf = ByteBuffer.allocate(4096);
        buf.order(ByteOrder.nativeOrder());

        // Skip nlmsg header (will fill in at end)
        buf.position(CLibrary.NLMSG_HDRLEN);

        // GENL header
        buf.put((byte) WG_CMD_SET_DEVICE);
        buf.put((byte) WG_GENL_VERSION);
        buf.putShort((short) 0);

        // WGDEVICE_A_IFNAME
        putStringAttr(buf, WGDEVICE_A_IFNAME, interfaceName);

        // WGDEVICE_A_PEERS (nested)
        var peersStart = buf.position();
        buf.putShort((short) 0); // placeholder for length
        buf.putShort((short) (WGDEVICE_A_PEERS | NLA_F_NESTED));

        // Single peer (nested)
        var peerStart = buf.position();
        buf.putShort((short) 0);
        buf.putShort((short) (0 | NLA_F_NESTED)); // index 0

        // WGPEER_A_PUBLIC_KEY
        putKeyAttr(buf, WGPEER_A_PUBLIC_KEY, publicKey);

        // WGPEER_A_FLAGS = REMOVE_ME
        putU32Attr(buf, WGPEER_A_FLAGS, WGPEER_F_REMOVE_ME);

        // Close peer
        var peerEnd = buf.position();
        buf.putShort(peerStart, (short) (peerEnd - peerStart));
        alignBuffer(buf);

        // Close peers
        var peersEnd = buf.position();
        buf.putShort(peersStart, (short) (peersEnd - peersStart));
        alignBuffer(buf);

        // Fill in nlmsg header
        fillNlmsgHeader(buf, familyId, seq, CLibrary.NLM_F_REQUEST | CLibrary.NLM_F_ACK);

        var msg = new byte[buf.position()];
        buf.rewind();
        buf.get(msg);

        var response = netlinkTransaction(msg, msg.length, seq);
        checkAckResponse(response);
    }

    /**
     * Set the fwmark on an interface.
     *
     * @param interfaceName the network interface name
     * @param fwmark        the firewall mark value
     * @throws IOException if the Netlink communication fails
     */
    public void setFwMark(String interfaceName, int fwmark) throws IOException {
        var familyId = resolveFamilyId();
        var seq = seqCounter.getAndIncrement();

        var buf = ByteBuffer.allocate(4096);
        buf.order(ByteOrder.nativeOrder());
        buf.position(CLibrary.NLMSG_HDRLEN);

        // GENL header
        buf.put((byte) WG_CMD_SET_DEVICE);
        buf.put((byte) WG_GENL_VERSION);
        buf.putShort((short) 0);

        putStringAttr(buf, WGDEVICE_A_IFNAME, interfaceName);
        putU32Attr(buf, WGDEVICE_A_FWMARK, fwmark);

        fillNlmsgHeader(buf, familyId, seq, CLibrary.NLM_F_REQUEST | CLibrary.NLM_F_ACK);

        var msg = new byte[buf.position()];
        buf.rewind();
        buf.get(msg);

        var response = netlinkTransaction(msg, msg.length, seq);
        checkAckResponse(response);
    }

    /**
     * Get the fwmark for an interface.
     *
     * @param interfaceName the network interface name
     * @return the fwmark value, or 0 if not set
     * @throws IOException if the Netlink communication fails
     */
    public int getFwMark(String interfaceName) throws IOException {
        return getDevice(interfaceName).fwmark();
    }

    // --- Private implementation ---

    private int resolveFamilyId() throws IOException {
        if (familyId > 0) {
            return familyId;
        }
        synchronized (this) {
            if (familyId > 0) {
                return familyId;
            }
            familyId = lookupGenlFamily(WG_GENL_NAME);
            LOG.debug("Resolved wireguard Generic Netlink family ID: {}", familyId);
            return familyId;
        }
    }

    private int lookupGenlFamily(String familyName) throws IOException {
        var seq = seqCounter.getAndIncrement();

        var nameBytes = (familyName + "\0").getBytes(StandardCharsets.US_ASCII);
        var attrLen = 4 + nameBytes.length;
        var paddedAttrLen = CLibrary.nlmsgAlign(attrLen);
        var payloadLen = CLibrary.GENL_HDRLEN + paddedAttrLen;
        var totalLen = CLibrary.NLMSG_HDRLEN + payloadLen;

        var buf = ByteBuffer.allocate(CLibrary.nlmsgAlign(totalLen));
        buf.order(ByteOrder.nativeOrder());

        // Netlink header
        buf.putInt(totalLen);
        buf.putShort((short) CLibrary.GENL_ID_CTRL);
        buf.putShort((short) (CLibrary.NLM_F_REQUEST));
        buf.putInt(seq);
        buf.putInt(0);

        // GENL header
        buf.put((byte) CLibrary.CTRL_CMD_GETFAMILY);
        buf.put((byte) 1); // version
        buf.putShort((short) 0);

        // CTRL_ATTR_FAMILY_NAME
        buf.putShort((short) attrLen);
        buf.putShort((short) CLibrary.CTRL_ATTR_FAMILY_NAME);
        buf.put(nameBytes);
        while (buf.position() < totalLen) buf.put((byte) 0);

        var response = netlinkTransaction(buf.array(), buf.position(), seq);

        // Parse response for CTRL_ATTR_FAMILY_ID
        for (var msg : response) {
            var rb = ByteBuffer.wrap(msg);
            rb.order(ByteOrder.nativeOrder());

            var nlmsgLen = rb.getInt();
            var nlmsgType = rb.getShort() & 0xFFFF;
            rb.getShort(); // flags
            rb.getInt();   // seq
            rb.getInt();   // pid

            if (nlmsgType == CLibrary.NLMSG_ERROR) {
                var err = rb.getInt();
                if (err != 0) {
                    throw new IOException("Netlink error resolving family '" + familyName + "': errno=" + (-err));
                }
                continue;
            }

            // Skip GENL header
            rb.get(); rb.get(); rb.getShort();

            // Parse attributes
            while (rb.remaining() >= 4) {
                var attrStart = rb.position();
                var nlaLen = rb.getShort() & 0xFFFF;
                var nlaType = rb.getShort() & 0xFFFF;
                if (nlaLen < 4) break;
                var dataLen = nlaLen - 4;

                if (nlaType == CLibrary.CTRL_ATTR_FAMILY_ID && dataLen >= 2) {
                    return rb.getShort() & 0xFFFF;
                }

                // Skip to next attribute
                rb.position(attrStart + CLibrary.nlmsgAlign(nlaLen));
            }
        }

        throw new IOException("Could not resolve Generic Netlink family ID for '" + familyName + "'");
    }

    private List<byte[]> netlinkTransaction(byte[] msg, int len, int seq) throws IOException {
        var c = CLibrary.INSTANCE;
        var fd = c.socket(CLibrary.AF_NETLINK, CLibrary.SOCK_DGRAM | CLibrary.SOCK_CLOEXEC, CLibrary.NETLINK_GENERIC);
        if (fd < 0) {
            throw new IOException("Failed to create Netlink socket: " + c.strerror(com.sun.jna.Native.getLastError()));
        }
        try {
            // Bind
            var local = new CLibrary.SockaddrNl();
            local.nl_family = CLibrary.AF_NETLINK;
            local.nl_pid = 0; // let kernel assign
            local.nl_groups = 0;
            if (c.bind(fd, local, local.size()) < 0) {
                throw new IOException("Failed to bind Netlink socket: " + c.strerror(com.sun.jna.Native.getLastError()));
            }

            // Send
            var dest = new CLibrary.SockaddrNl();
            dest.nl_family = CLibrary.AF_NETLINK;
            dest.nl_pid = 0; // kernel
            var sent = c.sendto(fd, msg, len, 0, dest, dest.size());
            if (sent < 0) {
                throw new IOException("Failed to send Netlink message: " + c.strerror(com.sun.jna.Native.getLastError()));
            }

            // Receive (possibly multi-part)
            var messages = new ArrayList<byte[]>();
            var recvBuf = new byte[65536];
            var done = false;

            while (!done) {
                var srcLen = new int[]{0};
                var received = c.recvfrom(fd, recvBuf, recvBuf.length, 0, null, srcLen);
                if (received < 0) {
                    throw new IOException("Failed to receive Netlink response: " + c.strerror(com.sun.jna.Native.getLastError()));
                }
                if (received == 0) break;

                // Parse individual nlmsg messages from the buffer
                var offset = 0;
                while (offset < received) {
                    if (offset + 4 > received) break;
                    var rb = ByteBuffer.wrap(recvBuf, offset, received - offset);
                    rb.order(ByteOrder.nativeOrder());

                    var nlmsgLen = rb.getInt();
                    if (nlmsgLen < CLibrary.NLMSG_HDRLEN || offset + nlmsgLen > received) break;

                    var nlmsgType = rb.getShort() & 0xFFFF;

                    if (nlmsgType == CLibrary.NLMSG_DONE) {
                        done = true;
                        break;
                    }
                    if (nlmsgType == CLibrary.NLMSG_ERROR) {
                        // Copy the message for error checking
                        var msgCopy = new byte[nlmsgLen];
                        System.arraycopy(recvBuf, offset, msgCopy, 0, nlmsgLen);
                        messages.add(msgCopy);
                        done = true;
                        break;
                    }

                    var msgCopy = new byte[nlmsgLen];
                    System.arraycopy(recvBuf, offset, msgCopy, 0, nlmsgLen);
                    messages.add(msgCopy);

                    offset += CLibrary.nlmsgAlign(nlmsgLen);
                }

                // If NLM_F_MULTI was not set in the first message, we're done after one recv
                if (!done && messages.size() > 0) {
                    var firstMsg = ByteBuffer.wrap(messages.get(0));
                    firstMsg.order(ByteOrder.nativeOrder());
                    firstMsg.getInt(); // len
                    firstMsg.getShort(); // type
                    var flags = firstMsg.getShort() & 0xFFFF;
                    if ((flags & 0x02) == 0) { // NLM_F_MULTI = 0x02
                        done = true;
                    }
                }
            }

            return messages;
        } finally {
            c.close(fd);
        }
    }

    private WireGuardUAPI.DeviceState parseGetDeviceResponse(String interfaceName, List<byte[]> messages) throws IOException {
        var privateKey = "";
        var publicKey = "";
        var listenPort = 0;
        var fwmark = 0;
        var peers = new ArrayList<WireGuardUAPI.PeerState>();

        for (var msg : messages) {
            var rb = ByteBuffer.wrap(msg);
            rb.order(ByteOrder.nativeOrder());

            var nlmsgLen = rb.getInt();
            var nlmsgType = rb.getShort() & 0xFFFF;
            var nlmsgFlags = rb.getShort() & 0xFFFF;
            rb.getInt(); // seq
            rb.getInt(); // pid

            if (nlmsgType == CLibrary.NLMSG_ERROR) {
                var err = rb.getInt();
                if (err != 0) {
                    throw new IOException("WireGuard Netlink get error: errno=" + (-err));
                }
                continue;
            }

            // Skip GENL header
            rb.get(); rb.get(); rb.getShort();

            // Parse device attributes
            while (rb.remaining() >= 4) {
                var attrStart = rb.position();
                var nlaLen = rb.getShort() & 0xFFFF;
                var nlaType = rb.getShort() & 0x3FFF; // strip nested flag
                if (nlaLen < 4) break;

                switch (nlaType) {
                    case WGDEVICE_A_PRIVATE_KEY -> {
                        var key = new byte[32];
                        rb.get(key);
                        if (!isAllZeros(key)) {
                            privateKey = Base64.getEncoder().encodeToString(key);
                        }
                    }
                    case WGDEVICE_A_PUBLIC_KEY -> {
                        var key = new byte[32];
                        rb.get(key);
                        if (!isAllZeros(key)) {
                            publicKey = Base64.getEncoder().encodeToString(key);
                        }
                    }
                    case WGDEVICE_A_LISTEN_PORT -> {
                        listenPort = rb.getShort() & 0xFFFF;
                    }
                    case WGDEVICE_A_FWMARK -> {
                        fwmark = rb.getInt();
                    }
                    case WGDEVICE_A_PEERS -> {
                        var peersEnd = attrStart + CLibrary.nlmsgAlign(nlaLen);
                        peers.addAll(parsePeers(rb, peersEnd));
                    }
                    default -> { /* skip */ }
                }

                rb.position(attrStart + CLibrary.nlmsgAlign(nlaLen));
            }
        }

        // Derive public key if we only have private key
        if (!privateKey.isEmpty() && publicKey.isEmpty()) {
            try {
                publicKey = Keys.pubkeyBase64(privateKey).getBase64PublicKey();
            } catch (Exception e) {
                LOG.warn("Could not derive public key from private key", e);
            }
        }

        return new WireGuardUAPI.DeviceState(interfaceName, privateKey, publicKey, listenPort, fwmark, peers);
    }

    private List<WireGuardUAPI.PeerState> parsePeers(ByteBuffer rb, int peersEnd) {
        var peers = new ArrayList<WireGuardUAPI.PeerState>();

        while (rb.position() < peersEnd && rb.remaining() >= 4) {
            var peerAttrStart = rb.position();
            var peerNlaLen = rb.getShort() & 0xFFFF;
            rb.getShort(); // type (index | NLA_F_NESTED)
            if (peerNlaLen < 4) break;

            var peerEnd = peerAttrStart + CLibrary.nlmsgAlign(peerNlaLen);
            var peer = parseSinglePeer(rb, peerEnd);
            if (peer != null) {
                peers.add(peer);
            }
            rb.position(peerEnd);
        }

        return peers;
    }

    private WireGuardUAPI.PeerState parseSinglePeer(ByteBuffer rb, int peerEnd) {
        String peerPublicKey = null;
        String peerPresharedKey = null;
        String endpoint = null;
        var allowedIps = new ArrayList<String>();
        long lastHandshakeSec = 0;
        long rxBytes = 0;
        long txBytes = 0;
        int keepalive = 0;

        while (rb.position() < peerEnd && rb.remaining() >= 4) {
            var attrStart = rb.position();
            var nlaLen = rb.getShort() & 0xFFFF;
            var nlaType = rb.getShort() & 0x3FFF;
            if (nlaLen < 4) break;

            switch (nlaType) {
                case WGPEER_A_PUBLIC_KEY -> {
                    var key = new byte[32];
                    rb.get(key);
                    peerPublicKey = Base64.getEncoder().encodeToString(key);
                }
                case WGPEER_A_PRESHARED_KEY -> {
                    var key = new byte[32];
                    rb.get(key);
                    if (!isAllZeros(key)) {
                        peerPresharedKey = Base64.getEncoder().encodeToString(key);
                    }
                }
                case WGPEER_A_ENDPOINT -> {
                    endpoint = parseEndpoint(rb, nlaLen - 4);
                }
                case WGPEER_A_PERSISTENT_KEEPALIVE_INTERVAL -> {
                    keepalive = rb.getShort() & 0xFFFF;
                }
                case WGPEER_A_LAST_HANDSHAKE_TIME -> {
                    // struct timespec64: int64 sec, int64 nsec
                    lastHandshakeSec = rb.getLong();
                    rb.getLong(); // nsec (ignored)
                }
                case WGPEER_A_RX_BYTES -> {
                    rxBytes = rb.getLong();
                }
                case WGPEER_A_TX_BYTES -> {
                    txBytes = rb.getLong();
                }
                case WGPEER_A_ALLOWEDIPS -> {
                    var aipsEnd = attrStart + CLibrary.nlmsgAlign(nlaLen);
                    allowedIps.addAll(parseAllowedIps(rb, aipsEnd));
                }
                default -> { /* skip */ }
            }

            rb.position(attrStart + CLibrary.nlmsgAlign(nlaLen));
        }

        if (peerPublicKey == null) return null;

        return new WireGuardUAPI.PeerState(
            peerPublicKey, peerPresharedKey, endpoint,
            allowedIps, lastHandshakeSec, rxBytes, txBytes, keepalive
        );
    }

    private List<String> parseAllowedIps(ByteBuffer rb, int aipsEnd) {
        var result = new ArrayList<String>();

        while (rb.position() < aipsEnd && rb.remaining() >= 4) {
            var aipStart = rb.position();
            var aipLen = rb.getShort() & 0xFFFF;
            rb.getShort(); // type (index | NLA_F_NESTED)
            if (aipLen < 4) break;

            var aipEnd = aipStart + CLibrary.nlmsgAlign(aipLen);

            int family = 0;
            byte[] addr = null;
            int cidr = 0;

            while (rb.position() < aipEnd && rb.remaining() >= 4) {
                var attrStart = rb.position();
                var nlaLen = rb.getShort() & 0xFFFF;
                var nlaType = rb.getShort() & 0x3FFF;
                if (nlaLen < 4) break;

                switch (nlaType) {
                    case WGALLOWEDIP_A_FAMILY -> family = rb.getShort() & 0xFFFF;
                    case WGALLOWEDIP_A_IPADDR -> {
                        var dataLen = nlaLen - 4;
                        addr = new byte[dataLen];
                        rb.get(addr);
                    }
                    case WGALLOWEDIP_A_CIDR_MASK -> cidr = rb.get() & 0xFF;
                    default -> { /* skip */ }
                }
                rb.position(attrStart + CLibrary.nlmsgAlign(nlaLen));
            }

            if (addr != null) {
                try {
                    var inetAddr = java.net.InetAddress.getByAddress(addr);
                    result.add(inetAddr.getHostAddress() + "/" + cidr);
                } catch (Exception e) {
                    LOG.warn("Failed to parse allowed IP", e);
                }
            }

            rb.position(aipEnd);
        }

        return result;
    }

    private String parseEndpoint(ByteBuffer rb, int dataLen) {
        // struct sockaddr_in (AF_INET) or sockaddr_in6 (AF_INET6)
        var startPos = rb.position();
        var family = rb.getShort() & 0xFFFF;

        try {
            if (family == AF_INET && dataLen >= 8) {
                // sockaddr_in: family(2) + port(2) + addr(4) + padding(8)
                int port = rb.getShort() & 0xFFFF;
                var addr = new byte[4];
                rb.get(addr);
                var inetAddr = java.net.InetAddress.getByAddress(addr);
                return inetAddr.getHostAddress() + ":" + port;
            } else if (family == AF_INET6 && dataLen >= 28) {
                // sockaddr_in6: family(2) + port(2) + flowinfo(4) + addr(16) + scope_id(4)
                int port = rb.getShort() & 0xFFFF;
                rb.getInt(); // flowinfo
                var addr = new byte[16];
                rb.get(addr);
                var inetAddr = java.net.InetAddress.getByAddress(addr);
                return "[" + inetAddr.getHostAddress() + "]:" + port;
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse endpoint", e);
        }
        return null;
    }

    private byte[] buildSetDeviceMessage(int familyId, int seq, String interfaceName,
                                          VpnAdapterConfiguration config, boolean replacePeers) throws IOException {
        var buf = ByteBuffer.allocate(65536);
        buf.order(ByteOrder.nativeOrder());

        // Skip nlmsg header
        buf.position(CLibrary.NLMSG_HDRLEN);

        // GENL header
        buf.put((byte) WG_CMD_SET_DEVICE);
        buf.put((byte) WG_GENL_VERSION);
        buf.putShort((short) 0);

        // WGDEVICE_A_IFNAME
        putStringAttr(buf, WGDEVICE_A_IFNAME, interfaceName);

        // WGDEVICE_A_PRIVATE_KEY
        try {
            putKeyAttr(buf, WGDEVICE_A_PRIVATE_KEY, config.privateKey());
        } catch (IllegalStateException ise) {
            // No private key available
        }

        // WGDEVICE_A_LISTEN_PORT
        config.listenPort().ifPresent(port -> putU16Attr(buf, WGDEVICE_A_LISTEN_PORT, port));

        // WGDEVICE_A_FWMARK
        config.fwMark().ifPresent(fw -> putU32Attr(buf, WGDEVICE_A_FWMARK, fw));

        // WGDEVICE_A_FLAGS
        if (replacePeers) {
            putU32Attr(buf, WGDEVICE_A_FLAGS, WGDEVICE_F_REPLACE_PEERS);
        }

        // Peers
        if (!config.peers().isEmpty()) {
            var peersStart = buf.position();
            buf.putShort((short) 0);
            buf.putShort((short) (WGDEVICE_A_PEERS | NLA_F_NESTED));

            int peerIdx = 0;
            for (var peer : config.peers()) {
                var peerStart = buf.position();
                buf.putShort((short) 0);
                buf.putShort((short) (peerIdx | NLA_F_NESTED));

                putKeyAttr(buf, WGPEER_A_PUBLIC_KEY, peer.publicKey());

                peer.presharedKey().ifPresent(k -> putKeyAttr(buf, WGPEER_A_PRESHARED_KEY, k));

                // WGPEER_A_FLAGS - replace allowed IPs for this peer
                putU32Attr(buf, WGPEER_A_FLAGS, WGPEER_F_REPLACE_ALLOWEDIPS);

                peer.endpointAddress().ifPresent(addr -> {
                    var port = peer.endpointPort().orElse(51820);
                    putEndpointAttr(buf, addr, port);
                });

                peer.persistentKeepalive().ifPresent(ka ->
                    putU16Attr(buf, WGPEER_A_PERSISTENT_KEEPALIVE_INTERVAL, ka));

                // Allowed IPs
                if (!peer.allowedIps().isEmpty()) {
                    var aipsStart = buf.position();
                    buf.putShort((short) 0);
                    buf.putShort((short) (WGPEER_A_ALLOWEDIPS | NLA_F_NESTED));

                    int aipIdx = 0;
                    for (var allowedIp : peer.allowedIps()) {
                        putAllowedIp(buf, aipIdx, allowedIp);
                        aipIdx++;
                    }

                    var aipsEnd = buf.position();
                    buf.putShort(aipsStart, (short) (aipsEnd - aipsStart));
                    alignBuffer(buf);
                }

                var peerEnd = buf.position();
                buf.putShort(peerStart, (short) (peerEnd - peerStart));
                alignBuffer(buf);

                peerIdx++;
            }

            var peersEnd = buf.position();
            buf.putShort(peersStart, (short) (peersEnd - peersStart));
            alignBuffer(buf);
        }

        fillNlmsgHeader(buf, familyId, seq, CLibrary.NLM_F_REQUEST | CLibrary.NLM_F_ACK);

        var result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }

    private void checkAckResponse(List<byte[]> response) throws IOException {
        for (var msg : response) {
            var rb = ByteBuffer.wrap(msg);
            rb.order(ByteOrder.nativeOrder());
            rb.getInt();   // nlmsg_len
            var type = rb.getShort() & 0xFFFF;
            rb.getShort(); // flags
            rb.getInt();   // seq
            rb.getInt();   // pid

            if (type == CLibrary.NLMSG_ERROR) {
                var err = rb.getInt();
                if (err != 0) {
                    throw new IOException("WireGuard Netlink set error: errno=" + (-err));
                }
                return; // errno=0 means success (ACK)
            }
        }
        // No error message = success for NLM_F_ACK
    }

    // --- Attribute helpers ---

    private static void putStringAttr(ByteBuffer buf, int type, String value) {
        var bytes = (value + "\0").getBytes(StandardCharsets.US_ASCII);
        var nlaLen = 4 + bytes.length;
        buf.putShort((short) nlaLen);
        buf.putShort((short) type);
        buf.put(bytes);
        alignBuffer(buf);
    }

    private static void putKeyAttr(ByteBuffer buf, int type, String base64Key) {
        var key = Base64.getDecoder().decode(base64Key);
        var nlaLen = 4 + key.length;
        buf.putShort((short) nlaLen);
        buf.putShort((short) type);
        buf.put(key);
        alignBuffer(buf);
    }

    private static void putU16Attr(ByteBuffer buf, int type, int value) {
        buf.putShort((short) 6); // 4 + 2
        buf.putShort((short) type);
        buf.putShort((short) value);
        alignBuffer(buf);
    }

    private static void putU32Attr(ByteBuffer buf, int type, int value) {
        buf.putShort((short) 8); // 4 + 4
        buf.putShort((short) type);
        buf.putInt(value);
        alignBuffer(buf);
    }

    private static void putEndpointAttr(ByteBuffer buf, String host, int port) {
        try {
            var addr = java.net.InetAddress.getByName(host);
            var addrBytes = addr.getAddress();

            if (addrBytes.length == 4) {
                // sockaddr_in: family(2) + port(2) + addr(4) + zero(8) = 16
                var nlaLen = 4 + 16;
                buf.putShort((short) nlaLen);
                buf.putShort((short) WGPEER_A_ENDPOINT);
                buf.putShort((short) AF_INET);
                buf.putShort((short) port);
                buf.put(addrBytes);
                buf.put(new byte[8]); // padding
                alignBuffer(buf);
            } else {
                // sockaddr_in6: family(2) + port(2) + flowinfo(4) + addr(16) + scope_id(4) = 28
                var nlaLen = 4 + 28;
                buf.putShort((short) nlaLen);
                buf.putShort((short) WGPEER_A_ENDPOINT);
                buf.putShort((short) AF_INET6);
                buf.putShort((short) port);
                buf.putInt(0); // flowinfo
                buf.put(addrBytes);
                buf.putInt(0); // scope_id
                alignBuffer(buf);
            }
        } catch (Exception e) {
            LOG.warn("Failed to encode endpoint {}:{}", host, port, e);
        }
    }

    private static void putAllowedIp(ByteBuffer buf, int index, String cidrNotation) {
        try {
            var parts = cidrNotation.split("/");
            var host = parts[0];
            var cidr = parts.length > 1 ? Integer.parseInt(parts[1]) : (host.contains(":") ? 128 : 32);
            var addr = java.net.InetAddress.getByName(host);
            var addrBytes = addr.getAddress();
            var family = addrBytes.length == 4 ? AF_INET : AF_INET6;

            var aipStart = buf.position();
            buf.putShort((short) 0);
            buf.putShort((short) (index | NLA_F_NESTED));

            // WGALLOWEDIP_A_FAMILY
            putU16Attr(buf, WGALLOWEDIP_A_FAMILY, family);

            // WGALLOWEDIP_A_IPADDR
            var addrNlaLen = 4 + addrBytes.length;
            buf.putShort((short) addrNlaLen);
            buf.putShort((short) WGALLOWEDIP_A_IPADDR);
            buf.put(addrBytes);
            alignBuffer(buf);

            // WGALLOWEDIP_A_CIDR_MASK
            buf.putShort((short) 5); // 4 + 1
            buf.putShort((short) WGALLOWEDIP_A_CIDR_MASK);
            buf.put((byte) cidr);
            alignBuffer(buf);

            var aipEnd = buf.position();
            buf.putShort(aipStart, (short) (aipEnd - aipStart));
            alignBuffer(buf);
        } catch (Exception e) {
            LOG.warn("Failed to encode allowed IP: {}", cidrNotation, e);
        }
    }

    private static void fillNlmsgHeader(ByteBuffer buf, int familyId, int seq, int flags) {
        var totalLen = buf.position();
        buf.putInt(0, totalLen);
        buf.putShort(4, (short) familyId);
        buf.putShort(6, (short) flags);
        buf.putInt(8, seq);
        buf.putInt(12, 0); // pid
    }

    private static void alignBuffer(ByteBuffer buf) {
        var pos = buf.position();
        var aligned = CLibrary.nlmsgAlign(pos);
        while (buf.position() < aligned) {
            buf.put((byte) 0);
        }
    }

    private static boolean isAllZeros(byte[] data) {
        for (var b : data) {
            if (b != 0) return false;
        }
        return true;
    }
}
