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
package com.jadaptive.nodal.core.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jadaptive.nodal.core.lib.util.Keys;

/**
 * Provides direct access to the WireGuard userspace API (UAPI) via Unix domain
 * sockets, eliminating the need to fork the {@code wg} command-line tool.
 * <p>
 * This works with both the kernel WireGuard module (when the {@code wg} tool has
 * created a UAPI socket) and wireguard-go (which always creates one). The socket
 * is typically located at {@code /var/run/wireguard/<interface>.sock}.
 * <p>
 * The UAPI protocol is a simple text-based key=value protocol:
 * <ul>
 *   <li><b>Get</b>: Send {@code get=1\n\n}, read back key=value lines, terminated by {@code \n\n}</li>
 *   <li><b>Set</b>: Send {@code set=1\nkey=value\n...\n\n}, read back {@code errno=0\n\n} on success</li>
 * </ul>
 * 
 * @see <a href="https://www.wireguard.com/xplatform/">WireGuard Cross-Platform Documentation</a>
 */
public final class WireGuardUAPI {

    private static final Logger LOG = LoggerFactory.getLogger(WireGuardUAPI.class);
    
    /**
     * Default directory where wireguard UAPI sockets are located.
     */
    public static final Path DEFAULT_SOCKET_DIR = Paths.get("/var/run/wireguard");

    private final Path socketDir;

    /**
     * Create a UAPI client using the default socket directory.
     */
    public WireGuardUAPI() {
        this(DEFAULT_SOCKET_DIR);
    }

    /**
     * Create a UAPI client using a custom socket directory.
     * 
     * @param socketDir the directory containing UAPI sockets
     */
    public WireGuardUAPI(Path socketDir) {
        this.socketDir = socketDir;
    }

    /**
     * Check if a UAPI socket exists for the given interface.
     * 
     * @param interfaceName the native interface name
     * @return true if a socket file exists
     */
    public boolean hasSocket(String interfaceName) {
        return Files.exists(socketPath(interfaceName));
    }

    /**
     * Get the socket path for an interface.
     * 
     * @param interfaceName the native interface name
     * @return the path to the UAPI socket
     */
    public Path socketPath(String interfaceName) {
        return socketDir.resolve(interfaceName + ".sock");
    }

    /**
     * List all interface names that have UAPI sockets.
     * 
     * @return list of interface names
     * @throws IOException if the directory cannot be listed
     */
    public List<String> listInterfaces() throws IOException {
        var result = new ArrayList<String>();
        if (Files.isDirectory(socketDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(socketDir, "*.sock")) {
                for (var path : stream) {
                    var name = path.getFileName().toString();
                    result.add(name.substring(0, name.length() - 5)); // strip .sock
                }
            }
        }
        return result;
    }

    /**
     * Get the full device state from the UAPI socket, including interface config
     * and all peer information (stats, allowed IPs, etc.).
     * 
     * @param interfaceName the native interface name
     * @return parsed device state
     * @throws IOException if the socket cannot be connected to or read from
     */
    public DeviceState getDevice(String interfaceName) throws IOException {
        var lines = sendGet(interfaceName);
        return DeviceState.parse(interfaceName, lines);
    }

    /**
     * Set configuration on a device. This is equivalent to {@code wg set}.
     * 
     * @param interfaceName the native interface name
     * @param commands the set commands as key=value pairs
     * @throws IOException if the socket cannot be connected to or if the operation fails
     */
    public void setDevice(String interfaceName, Map<String, String> commands) throws IOException {
        var sb = new StringBuilder("set=1\n");
        for (var entry : commands.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        sb.append('\n');
        sendAndCheckError(interfaceName, sb.toString());
    }

    /**
     * Set full configuration from an {@link VpnAdapterConfiguration}. This replaces
     * the entire configuration (equivalent to {@code wg setconf}).
     * 
     * @param interfaceName the native interface name
     * @param config the adapter configuration
     * @throws IOException if the operation fails
     */
    public void setConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        var sb = new StringBuilder("set=1\n");
        sb.append("private_key=").append(keyToHex(config.privateKey())).append('\n');
        config.listenPort().ifPresent(p -> sb.append("listen_port=").append(p).append('\n'));
        config.fwMark().ifPresent(f -> sb.append("fwmark=").append(f).append('\n'));
        sb.append("replace_peers=true\n");
        for (var peer : config.peers()) {
            appendPeerSet(sb, peer, false);
        }
        sb.append('\n');
        sendAndCheckError(interfaceName, sb.toString());
    }

    /**
     * Synchronize configuration. Like {@code wg syncconf}, this only updates
     * peers that have changed and does not disrupt existing connections.
     * 
     * @param interfaceName the native interface name
     * @param config the adapter configuration
     * @throws IOException if the operation fails
     */
    public void syncConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        // Get current state to compare
        var current = getDevice(interfaceName);
        var currentPeerKeys = new java.util.HashSet<String>();
        for (var peer : current.peers()) {
            currentPeerKeys.add(peer.publicKey());
        }

        var sb = new StringBuilder("set=1\n");
        // Set interface-level config
        sb.append("private_key=").append(keyToHex(config.privateKey())).append('\n');
        config.listenPort().ifPresent(p -> sb.append("listen_port=").append(p).append('\n'));
        config.fwMark().ifPresent(f -> sb.append("fwmark=").append(f).append('\n'));

        // Remove peers not in new config
        var newPeerKeys = new java.util.HashSet<String>();
        for (var peer : config.peers()) {
            newPeerKeys.add(peer.publicKey());
        }
        for (var existingKey : currentPeerKeys) {
            if (!newPeerKeys.contains(existingKey)) {
                sb.append("public_key=").append(keyToHex(existingKey)).append('\n');
                sb.append("remove=true\n");
            }
        }

        // Add/update peers
        for (var peer : config.peers()) {
            appendPeerSet(sb, peer, true);
        }
        sb.append('\n');
        sendAndCheckError(interfaceName, sb.toString());
    }

    /**
     * Append configuration (add peers). Like {@code wg addconf}.
     * 
     * @param interfaceName the native interface name
     * @param config the adapter configuration with peers to add
     * @throws IOException if the operation fails
     */
    public void appendConfiguration(String interfaceName, VpnAdapterConfiguration config) throws IOException {
        var sb = new StringBuilder("set=1\n");
        for (var peer : config.peers()) {
            appendPeerSet(sb, peer, false);
        }
        sb.append('\n');
        sendAndCheckError(interfaceName, sb.toString());
    }

    /**
     * Remove a peer by public key.
     * 
     * @param interfaceName the native interface name
     * @param publicKey the base64-encoded public key
     * @throws IOException if the operation fails
     */
    public void removePeer(String interfaceName, String publicKey) throws IOException {
        var sb = new StringBuilder("set=1\n");
        sb.append("public_key=").append(keyToHex(publicKey)).append('\n');
        sb.append("remove=true\n");
        sb.append('\n');
        sendAndCheckError(interfaceName, sb.toString());
    }

    /**
     * Set the fwmark on an interface.
     * 
     * @param interfaceName the native interface name
     * @param fwmark the firewall mark value
     * @throws IOException if the operation fails
     */
    public void setFwMark(String interfaceName, int fwmark) throws IOException {
        var commands = new LinkedHashMap<String, String>();
        commands.put("fwmark", String.valueOf(fwmark));
        setDevice(interfaceName, commands);
    }

    /**
     * Get the fwmark for an interface.
     * 
     * @param interfaceName the native interface name
     * @return the fwmark value, or 0 if not set
     * @throws IOException if the operation fails
     */
    public int getFwMark(String interfaceName) throws IOException {
        return getDevice(interfaceName).fwmark();
    }

    // --- Private helpers ---

    private void appendPeerSet(StringBuilder sb, VpnPeer peer, boolean replaceAllowedIps) {
        sb.append("public_key=").append(keyToHex(peer.publicKey())).append('\n');
        peer.presharedKey().ifPresent(k -> sb.append("preshared_key=").append(keyToHex(k)).append('\n'));
        peer.endpointAddress().ifPresent(addr -> {
            var port = peer.endpointPort().orElse(Vpn.DEFAULT_PORT);
            var endpoint = addr.contains(":") ? 
                String.format("[%s]:%d", addr, port) : 
                String.format("%s:%d", addr, port);
            sb.append("endpoint=").append(endpoint).append('\n');
        });
        peer.persistentKeepalive().ifPresent(k -> sb.append("persistent_keepalive_interval=").append(k).append('\n'));
        if (replaceAllowedIps) {
            sb.append("replace_allowed_ips=true\n");
        }
        for (var ip : peer.allowedIps()) {
            sb.append("allowed_ip=").append(ip).append('\n');
        }
    }

    private List<String> sendGet(String interfaceName) throws IOException {
        return sendCommand(interfaceName, "get=1\n\n");
    }

    private void sendAndCheckError(String interfaceName, String command) throws IOException {
        var lines = sendCommand(interfaceName, command);
        for (var line : lines) {
            if (line.startsWith("errno=")) {
                var errno = Integer.parseInt(line.substring(6));
                if (errno != 0) {
                    throw new IOException("WireGuard UAPI error: errno=" + errno);
                }
                return;
            }
        }
        throw new IOException("No errno response from WireGuard UAPI");
    }

    private List<String> sendCommand(String interfaceName, String command) throws IOException {
        var sockPath = socketPath(interfaceName);
        LOG.debug("Connecting to UAPI socket: {}", sockPath);
        
        var addr = UnixDomainSocketAddress.of(sockPath);
        try (var channel = SocketChannel.open(addr)) {
            // Send
            var writer = new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8);
            writer.write(command);
            writer.flush();
            channel.shutdownOutput();
            
            // Read response
            var reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    /**
     * Convert a base64-encoded WireGuard key to its hex representation
     * (as required by the UAPI protocol).
     */
    static String keyToHex(String base64Key) {
        var bytes = Base64.getDecoder().decode(base64Key);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Convert a hex-encoded WireGuard key to its base64 representation.
     */
    static String hexToKey(String hex) {
        var bytes = HexFormat.of().parseHex(hex);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Parsed device state from a UAPI {@code get} response.
     */
    public static final class DeviceState {
        private final String interfaceName;
        private final String privateKey;  // base64
        private final String publicKey;   // base64
        private final int listenPort;
        private final int fwmark;
        private final List<PeerState> peers;

        public DeviceState(String interfaceName, String privateKey, String publicKey,
                           int listenPort, int fwmark, List<PeerState> peers) {
            this.interfaceName = interfaceName;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.listenPort = listenPort;
            this.fwmark = fwmark;
            this.peers = Collections.unmodifiableList(peers);
        }

        public String interfaceName() { return interfaceName; }
        public String privateKey() { return privateKey; }
        public String publicKey() { return publicKey; }
        public int listenPort() { return listenPort; }
        public int fwmark() { return fwmark; }
        public List<PeerState> peers() { return peers; }

        static DeviceState parse(String interfaceName, List<String> lines) {
            String privateKey = "";
            String publicKey = "";
            int listenPort = 0;
            int fwmark = 0;
            var peers = new ArrayList<PeerState>();

            // Current peer being built
            String peerPublicKey = null;
            String peerPresharedKey = null;
            var peerAllowedIps = new ArrayList<String>();
            String peerEndpoint = null;
            long peerLastHandshake = 0;
            long peerRx = 0;
            long peerTx = 0;
            int peerKeepalive = 0;

            for (var line : lines) {
                var eqIdx = line.indexOf('=');
                if (eqIdx < 0) continue;
                var key = line.substring(0, eqIdx);
                var value = line.substring(eqIdx + 1);

                if (key.equals("public_key")) {
                    // Flush previous peer if any
                    if (peerPublicKey != null) {
                        peers.add(new PeerState(hexToKey(peerPublicKey),
                                peerPresharedKey == null ? null : hexToKey(peerPresharedKey),
                                peerEndpoint, peerAllowedIps, peerLastHandshake, peerRx, peerTx, peerKeepalive));
                    }
                    peerPublicKey = value;
                    peerPresharedKey = null;
                    peerAllowedIps = new ArrayList<>();
                    peerEndpoint = null;
                    peerLastHandshake = 0;
                    peerRx = 0;
                    peerTx = 0;
                    peerKeepalive = 0;
                } else if (peerPublicKey != null) {
                    // Peer-level keys
                    switch (key) {
                        case "preshared_key" -> {
                            // All zeros means no preshared key
                            if (!value.matches("^0+$")) {
                                peerPresharedKey = value;
                            }
                        }
                        case "endpoint" -> peerEndpoint = value;
                        case "allowed_ip" -> peerAllowedIps.add(value);
                        case "last_handshake_time_sec" -> peerLastHandshake = Long.parseLong(value);
                        case "rx_bytes" -> peerRx = Long.parseLong(value);
                        case "tx_bytes" -> peerTx = Long.parseLong(value);
                        case "persistent_keepalive_interval" -> peerKeepalive = Integer.parseInt(value);
                    }
                } else {
                    // Interface-level keys
                    switch (key) {
                        case "private_key" -> {
                            if (!value.matches("^0+$")) {
                                privateKey = hexToKey(value);
                            }
                        }
                        case "listen_port" -> listenPort = Integer.parseInt(value);
                        case "fwmark" -> fwmark = Integer.parseInt(value);
                    }
                }
            }

            // Flush last peer
            if (peerPublicKey != null) {
                peers.add(new PeerState(hexToKey(peerPublicKey),
                        peerPresharedKey == null ? null : hexToKey(peerPresharedKey),
                        peerEndpoint, peerAllowedIps, peerLastHandshake, peerRx, peerTx, peerKeepalive));
            }

            // Derive public key from private key if available
            if (!privateKey.isEmpty() && publicKey.isEmpty()) {
                try {
                    publicKey = Keys.pubkeyBase64(privateKey).getBase64PublicKey();
                } catch (Exception e) {
                    LOG.warn("Could not derive public key from private key", e);
                }
            }

            return new DeviceState(interfaceName, privateKey, publicKey, listenPort, fwmark, peers);
        }
    }

    /**
     * Parsed peer state from a UAPI get response.
     */
    public static final class PeerState {
        private final String publicKey;       // base64
        private final String presharedKey;    // base64, or null
        private final String endpoint;        // "host:port" or null
        private final List<String> allowedIps;
        private final long lastHandshakeTimeSec;
        private final long rxBytes;
        private final long txBytes;
        private final int persistentKeepalive;

        public PeerState(String publicKey, String presharedKey, String endpoint,
                  List<String> allowedIps, long lastHandshakeTimeSec,
                  long rxBytes, long txBytes, int persistentKeepalive) {
            this.publicKey = publicKey;
            this.presharedKey = presharedKey;
            this.endpoint = endpoint;
            this.allowedIps = Collections.unmodifiableList(new ArrayList<>(allowedIps));
            this.lastHandshakeTimeSec = lastHandshakeTimeSec;
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
            this.persistentKeepalive = persistentKeepalive;
        }

        public String publicKey() { return publicKey; }
        public Optional<String> presharedKey() { return Optional.ofNullable(presharedKey); }
        public List<String> allowedIps() { return allowedIps; }
        public long lastHandshakeTimeSec() { return lastHandshakeTimeSec; }
        public Instant lastHandshake() { return Instant.ofEpochSecond(lastHandshakeTimeSec); }
        public long rxBytes() { return rxBytes; }
        public long txBytes() { return txBytes; }
        public int persistentKeepalive() { return persistentKeepalive; }

        /**
         * Parse the endpoint string into an {@link InetSocketAddress}.
         * 
         * @return the parsed endpoint, or empty if no endpoint is set
         */
        public Optional<InetSocketAddress> remoteAddress() {
            if (endpoint == null || endpoint.isEmpty() || endpoint.equals("(none)")) {
                return Optional.empty();
            }
            return Optional.of(parseEndpoint(endpoint));
        }

        private static InetSocketAddress parseEndpoint(String endpoint) {
            // Handle IPv6: [::1]:51820
            if (endpoint.startsWith("[")) {
                var closeBracket = endpoint.indexOf(']');
                var host = endpoint.substring(1, closeBracket);
                var port = Integer.parseInt(endpoint.substring(closeBracket + 2));
                return new InetSocketAddress(host, port);
            }
            // IPv4: 1.2.3.4:51820
            var lastColon = endpoint.lastIndexOf(':');
            var host = endpoint.substring(0, lastColon);
            var port = Integer.parseInt(endpoint.substring(lastColon + 1));
            return new InetSocketAddress(host, port);
        }
    }
}
