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
package com.jadaptive.nodal.core.quick;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import com.jadaptive.nodal.core.lib.NativeComponents;
import com.jadaptive.nodal.core.lib.PlatformService;
import com.jadaptive.nodal.core.lib.SystemConfiguration;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.Vpn;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.jadaptive.nodal.core.lib.VpnAdapterConfiguration;
import com.jadaptive.nodal.core.lib.VpnInterfaceInformation;
import com.jadaptive.nodal.core.lib.util.Keys;
import com.jadaptive.nodal.core.lib.util.Util;
import com.sshtools.liftlib.commands.ElevatableSystemCommands;
import com.sshtools.liftlib.commands.SystemCommands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "ndl", description = "Set and retrieve configuration of wireguard interfaces, a Java clone of the 'wg' command.", mixinStandardHelpOptions = true, subcommands = {
        Ndl.Show.class, Ndl.ShowConf.class, Ndl.SetConf.class, Ndl.SyncConf.class, Ndl.AddConf.class, Ndl.GenKey.class, Ndl.GenPsk.class, Ndl.PubKey.class })
public class Ndl extends AbstractCommand implements SystemContext {

    final static PrintStream out = System.out;

    public static void main(String[] args) throws Exception {
        Ndl cmd = new Ndl();
        System.exit(new CommandLine(cmd).setExecutionExceptionHandler(new ExceptionHandler(cmd)).execute(args));
    }

    private PlatformService<?> platformService;
    private SystemConfiguration configuration;
    private ScheduledExecutorService queue;
    private SystemCommands commands;
    private NativeComponents nativeComponents;

    @Override
    protected Integer onCall() throws Exception {
        platform();
        return 0;
    }

    @Override
    public SystemCommands commands() {
        if (commands == null)
            commands = new ElevatableSystemCommands();
        return commands;
    }

	@Override
	public void alert(String message, Object... args) {
        System.out.format("[+] %s%n", MessageFormat.format(message, args));
	}

    @Override
    public NativeComponents nativeComponents() {
        if (nativeComponents == null)
            nativeComponents = new NativeComponents();
        return nativeComponents;
    }

    PlatformService<?> platform() {
        if (platformService == null) {
            queue = Executors.newSingleThreadScheduledExecutor();
            configuration = SystemConfiguration.defaultConfiguration();
            platformService = PlatformService.create(this);
        }
        return platformService;
    }

    @Override
    public ScheduledExecutorService queue() {
        return queue;
    }

    @Override
    public SystemConfiguration configuration() {
        return configuration;
    }

    @Override
    public void addScriptEnvironmentVariables(VpnAdapter connection, Map<String, String> env) {
    }

    @Command(name = "show", description = "Shows the current configuration and device information", subcommands = {
            Show.Interfaces.class, Show.All.class, Show.PublicKey.class, Show.LatestHandshake.class, Show.ListenPort.class, Show.FwMark.class,
            Show.Peers.class, Show.PresharedKeys.class, Show.Endpoints.class })
    public final static class Show implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Parameters(arity = "0..1")
        private Optional<String> iface;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            if (iface.isPresent())
                show(parent.platform().adapter(iface.get()));
            else {
                var idx = 0;
                for (var ip : parent.platform().adapters()) {
                    if (idx++ > 0)
                        out.println();
                    show(ip);
                }
            }
            return 0;
        }

        void printDetail(StringBuilder bldr, int indent, String name, String fmt, Object... args) {
            printDetail(bldr, indent, name, Optional.empty(), fmt, args);
        }
        
        void printDetail(StringBuilder bldr, int indent, String name, Optional<String> color, String fmt, Object... args) {
            if(color.isPresent()) {
                if(indent == 0) {
                    bldr.append(Ansi.AUTO.string(String.format("@|bold," + color.get() + " %s: |@", name)));
                }
                else {
                    bldr.append(Ansi.AUTO.string(String.format("%" + indent + "s@|bold," + color.get() + " %s: |@", "", name)));
                }
                bldr.append(Ansi.AUTO.string("@|" + color.get() + " " + String.format(fmt, args) + "|@"));
            }
            else {
                if(indent == 0) {
                    bldr.append(Ansi.AUTO.string(String.format("@|bold %s: |@", name)));
                }
                else {
                    bldr.append(Ansi.AUTO.string(String.format("%" + indent + "s@|bold %s: |@", "", name)));
                }
                bldr.append(String.format(fmt, args));
            }
            bldr.append(System.lineSeparator());
        }

        void show(VpnAdapter vpn) throws IOException {
            var info = vpn.information();
            var config = vpn.configuration();
            
            var report = new StringBuilder();
            
            if(vpn.address().hasVirtualName())
                printDetail(report, 0, "interface", "%s (%s)", info.interfaceName(), vpn.address().nativeName());
            else
            	printDetail(report, 0, "interface", "%s", info.interfaceName());            
            printDetail(report, 2, "public key", "%s", config.publicKey());            
            printDetail(report, 2, "private key", "%s", "(hidden)");

            info.listenPort().ifPresent(p -> printDetail(report, 2, "listening port", "%s", p));
            info.fwmark().ifPresent(p -> printDetail(report, 2, "fwmark", "0x%4x", p));

            var peers = config.peers();
            if (peers.size() > 0) {
                report.append(System.lineSeparator());
                for (var peer : peers) {
                    
                    printDetail(report, 0, "peer", Optional.of("yellow"), "%s", peer.publicKey());
                    peer.endpointAddress().ifPresent(ep -> printDetail(report, 2, "endpoint", "%s", peer.endpointPort().orElse(Vpn.DEFAULT_PORT)));
                    printDetail(report, 2, "allowed ips", "%s", String.join(", ", peer.allowedIps()));

                    var peerInfo = info.peer(peer.publicKey());
                    peerInfo.ifPresent(i -> {
                        var seconds = Duration.between(i.lastHandshake(), Instant.now()).toSeconds();
                        printDetail(report, 2, "latest handshake", "%d %s ago", seconds, seconds < 2 ? "second" : "seconds");
                        printDetail(report, 2, "transfer", "%s received, %s sent", Util.toHumanSize(i.rx()), Util.toHumanSize(i.tx()));
                    });

                    peer.persistentKeepalive().ifPresent(
                            p -> printDetail(report, 2, "persistent keepalive", "every %d %s", p, p < 2 ? "second" : "seconds"));
                }
            }
            
            out.print(report.toString());
        }

        @Command(name = "public-key", description = "Shows the public key")
        public final static class PublicKey implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                out.format("%s%n", ip.information().publicKey());
                return 0;
            }

        }

        @Command(name = "latest-handshake", description = "Shows the latest handshake")
        public final static class LatestHandshake implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                VpnInterfaceInformation info = ip.information();
                out.format("%s\t%d%n", info.publicKey(), info.lastHandshake().getEpochSecond());
                return 0;
            }

        }

        @Command(name = "private-key", description = "Shows the private key")
        public final static class PrivateKey implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                out.format("%s%n", ip.information().privateKey());
                return 0;
            }

        }

        @Command(name = "listen-port", description = "Shows the listening port")
        public final static class ListenPort implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                out.format("%s%n", ip.information().listenPort().orElse(0));
                return 0;
            }

        }

        @Command(name = "fwmark", description = "Shows the fwmark")
        public final static class FwMark implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                out.format("%s%n", ip.information().fwmark().map(i -> i.toString()).orElse("off"));
                return 0;
            }

        }

        @Command(name = "peers", description = "Shows the peers")
        public final static class Peers implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                for (var peer : ip.information().peers()) {
                    out.println(peer.publicKey());
                }
                return 0;
            }

        }

        @Command(name = "preshared-keys", description = "Shows the preshared keys")
        public final static class PresharedKeys implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                for (var peer : ip.information().peers()) {
                    out.format("%s\t%s%n", peer.publicKey(), peer.presharedKey().orElse("(none)"));
                }
                return 0;
            }

        }

        @Command(name = "interfaces", description = "Shows the current configuration and device information")
        public final static class Interfaces implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ips = parent.parent.platform().adapters();
                if (!ips.isEmpty()) {
                    out.println(String.join(" ", ips.stream().map(a -> a.address().name()).collect(Collectors.toList())));
                }
                return 0;
            }

        }

        @Command(name = "endpoints", description = "Shows the endpoints")
        public final static class Endpoints implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var ip = parent.parent.platform().adapter(parent.iface.get());
                for (var peer : ip.information().peers()) {
                    out.format("%s\t%s%n", peer.publicKey(),
                            peer.remoteAddress().map(s -> s.toString()).orElse("(none)"));
                }
                return 0;
            }
        }

        @Command(name = "all", description = "Shows all wireguard interfaces", subcommands = { All.PublicKey.class,
                All.PrivateKey.class, All.ListenPort.class, All.FwMark.class, All.Peers.class, All.LatestHandshakes.class, All.PresharedKeys.class,
                All.Endpoints.class })
        public final static class All implements Callable<Integer> {

            @ParentCommand
            private Show parent;

            @Override
            public Integer call() throws Exception {
                parent.parent.initCommand();
                var idx = 0;
                for (var ip : parent.parent.platform().adapters()) {
                    if (idx++ > 0)
                        out.println();
                    parent.show(ip);
                }
                return 0;
            }

            @Command(name = "public-key", description = "Shows the public key")
            public final static class PublicKey implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        out.format("%s\t%s%n", ip.address().name(), ip.information().publicKey());
                    }
                    return 0;
                }

            }

            @Command(name = "private-key", description = "Shows the private key")
            public final static class PrivateKey implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        out.format("%s\t%s%n", ip.address().name(), ip.information().privateKey());
                    }
                    return 0;
                }

            }

            @Command(name = "listen-port", description = "Shows the listening ports")
            public final static class ListenPort implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        out.format("%s\t%s%n", ip.address().name(), ip.information().listenPort().orElse(0));
                    }
                    return 0;
                }

            }

            @Command(name = "fwmark", description = "Shows the fwmark")
            public final static class FwMark implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        out.format("%s\t%s%n", ip.address().name(),
                                ip.information().fwmark().map(i -> i.toString()).orElse("off"));
                    }
                    return 0;
                }

            }

            @Command(name = "peers", description = "Shows the peers")
            public final static class Peers implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        for (var peer : ip.information().peers()) {
                            out.format("%s\t%s%n", ip.address().name(), peer.publicKey());
                        }
                    }
                    return 0;
                }

            }

            @Command(name = "latest-handshakes", description = "Shows the latest handshakes")
            public final static class LatestHandshakes implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        var info = ip.information();
                        for (var peer : info.peers()) {
                            out.format("%s\t%s\t%d%n", ip.address().name(), peer.publicKey(), peer.lastHandshake().getEpochSecond());
                        }
                    }
                    return 0;
                }

            }

            @Command(name = "preshared-keys", description = "Shows the preshared keys")
            public final static class PresharedKeys implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        for (var peer : ip.information().peers()) {
                            out.format("%s\t%s\t%s%n", ip.address().name(), peer.publicKey(),
                                    peer.presharedKey().orElse("(none)"));
                        }
                    }
                    return 0;
                }

            }

            @Command(name = "endpoints", description = "Shows the endpoints")
            public final static class Endpoints implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        out.format("%s\t",
                                ip.address().name()); /*
                                                       * TODO: really? this is how "wg show all endpoints" does it, but
                                                       * it looks like a bug. Others always start with interface name
                                                       */
                        for (var peer : ip.information().peers()) {
                            out.format("%s\t%s%n", peer.publicKey(),
                                    peer.remoteAddress().map(s -> s.toString()).orElse("(none)"));
                        }
                    }
                    return 0;
                }

            }

            @Command(name = "allowed-ips", description = "Shows the allowed IPs")
            public final static class AllowedIps implements Callable<Integer> {

                @ParentCommand
                private All parent;

                @Override
                public Integer call() throws Exception {
                    parent.parent.parent.initCommand();
                    for (var ip : parent.parent.parent.platform().adapters()) {
                        for (var peer : ip.information().peers()) {
                            out.format("%s\t%s%n", peer.publicKey(), String.join(", ", peer.allowedIps()));
                        }
                    }
                    return 0;
                }

            }

        }
    }

    @Command(name = "showconf", description = "Shows the current configuration of a given WireGuard interface, for use with `setconf'")
    public final static class ShowConf implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Parameters(arity = "1")
        private String iface;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            parent.platform().adapter(iface).configuration().write(out);
            return 0;
        }
    }

    @Command(name = "setconf", description = "Set the current configuration of a given WireGuard interface to the contents of the given configuration file.")
    public final static class SetConf implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Parameters(index = "0", arity = "1")
        private String iface;

        @Parameters(index = "1", arity = "1")
        private Path configuration;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            parent.platform().adapter(iface).reconfigure(new VpnAdapterConfiguration.Builder().fromFile(configuration).build());
            return 0;
        }
    }

    @Command(name = "syncconf", description = "Like setconf, but reads back the existing configuration first and only makes changes that are explicitly different between the configuration file and the interface")
    public final static class SyncConf implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Parameters(index = "0", arity = "1")
        private String iface;

        @Parameters(index = "1", arity = "1")
        private Path configuration;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            parent.platform().adapter(iface).sync(new VpnAdapterConfiguration.Builder().fromFile(configuration).build());
            return 0;
        }
    }

    @Command(name = "addconf", description = "Like setconf, but reads back the existing configuration first and only makes changes that are explicitly different between the configuration file and the interface.")
    public final static class AddConf implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Parameters(index = "0", arity = "1")
        private String iface;

        @Parameters(index = "1", arity = "1")
        private Path configuration;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            parent.platform().adapter(iface).append(new VpnAdapterConfiguration.Builder().fromFile(configuration).build());
            return 0;
        }
    }

    @Command(name = "genkey", description = "Generates a new private key and writes it to stdout")
    public static class GenKey implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            out.println(Keys.genkey().getBase64PrivateKey());
            return 0;
        }
    }

    @Command(name = "genpsk", description = "Generates a new preshared key and writes it to stdout")
    public final static class GenPsk extends GenKey {
    }

    @Command(name = "pubkey", description = "Reads a private key from stdin and writes a public key to stdout")
    public final static class PubKey implements Callable<Integer> {

        @ParentCommand
        private Ndl parent;

        @Override
        public Integer call() throws Exception {
            parent.initCommand();
            try (var in = new BufferedReader(new InputStreamReader(System.in))) {
                out.println(Keys.pubkeyBase64(in.readLine().trim()).getBase64PublicKey());
            }
            return 0;
        }
    }

}
