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

import static java.lang.Thread.sleep;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ScheduledExecutorService;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

import com.jadaptive.nodal.core.lib.NativeComponents;
import com.jadaptive.nodal.core.lib.PlatformService;
import com.jadaptive.nodal.core.lib.SystemConfiguration;
import com.jadaptive.nodal.core.lib.SystemContext;
import com.jadaptive.nodal.core.lib.VpnAdapter;
import com.jadaptive.nodal.core.remote.lib.RemotePlatformService;
import com.jadaptive.nodal.core.remote.node.RemotePlatformServiceDelegate;
import com.sshtools.liftlib.commands.ElevatableSystemCommands;
import com.sshtools.liftlib.commands.SystemCommands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Command(name = "lbv-remote-node-agent", description = "Exposes the VPN driver API over D-Bus.", mixinStandardHelpOptions = true)
@Resource({ "windows-task\\.xml" })
@Bundle
public class NdlRemoteNodeAgent extends AbstractCommand implements SystemContext {

    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(NdlRemoteNodeAgent.class.getName());

    private final class LbvConfiguration implements SystemConfiguration {
        @Override
        public Optional<Duration> connectTimeout() {
            if (connectTimeout.isPresent()) {
                var to = connectTimeout.get();
                if (to == 0)
                    return Optional.empty();
                else
                    return Optional.of(Duration.ofSeconds(to));
            } else {
                return Optional.of(SystemConfiguration.CONNECT_TIMEOUT);
            }
        }

        @Override
        public Optional<Integer> defaultMTU() {
            return mtu;
        }
 
        @Override
        public Optional<String> dnsIntegrationMethod() {
            return dns;
        }

        @Override
        public Duration handshakeTimeout() {
            return handshakeTimeout.map(Duration::ofSeconds).orElse(SystemConfiguration.HANDSHAKE_TIMEOUT);
        }

        @Override
        public boolean ignoreLocalRoutes() {
            return !localRoutes;
        }

        @Override
        public Duration serviceWait() {
            return serviceWait.map(Duration::ofSeconds).orElse(SystemConfiguration.SERVICE_WAIT_TIMEOUT);
        }
    }

    final static PrintStream out = System.out;

    public static void main(String[] args) throws Exception {
        var cmd = new NdlRemoteNodeAgent();
        System.exit(new CommandLine(cmd).setExecutionExceptionHandler(new ExceptionHandler(cmd)).execute(args));
    }

    @Option(names = { "-s",
            "--configuration-search-path" }, paramLabel = "PATHS", description = "Alternative location(s) to search configuration files named <interface>.conf.")
    private Optional<String> configurationSearchPath;

    @Option(names = { "-d",
            "--dns" }, paramLabel = "METHOD", description = "The method of DNS integration to use. There should be no need to specify this option, but if you do it must be one of the methods supported by this operating system.")
    private Optional<String> dns;

    @Option(names = { "-m", "--mtu" }, paramLabel = "BYTES", description = "The default MTU. ")
    private Optional<Integer> mtu;

    @Option(names = { "-r",
            "--tunnel-local-routes" }, description = "When this option is present, any routes (allowed ips) that are local may be routed via the VPN. Ordinarily you wouln't want to do this.")
    private boolean localRoutes;

    @Option(names = { "-w",
            "--service-wait" }, paramLabel = "SECONDS", description = "Only applicable on operating systems that use a system service to manage the virtual network interface (e.g. Windows), this option defines how long (in seconds) to wait for a response after installation before the service is considered invalid and an error is thrown.")
    private Optional<Integer> serviceWait;

    @Option(names = { "-h",
            "--handshake-timeout" }, paramLabel = "SECONDS", description = "How much time must elapse before the connection is considered dead.")
    private Optional<Integer> handshakeTimeout;

    @Option(names = { "-t",
            "--timeout" }, paramLabel = "SECONDS", description = "Connection timeout. If no handshake has been received in this time then the connection is considered invalid. An error with thrown and the connection taken down. Only applies if there is a single peer with a single endpoint.")
    private Optional<Integer> connectTimeout;

	@Option(names = {
			"--dbus-address" }, description = "Address of DBus daemon. Otherwise session bus willl be used. It is not recommended the system bus be used without additional security configuration.", paramLabel = "<arg>")
	private Optional<String> dbusAddress;
    
    private SystemConfiguration configuration;
    private ScheduledExecutorService queue;
    private SystemCommands commands;
    private NativeComponents nativeComponents;

    @Override
    protected Integer onCall() throws Exception {

        var ps = PlatformService.create(this);
        alert(BUNDLE.getString("platform"), ps.getClass().getName());
        
        DBusConnection conx;
        if(dbusAddress.isPresent()) {
        	var busname = dbusAddress.get();
        	if(busname.equals("system"))
                conx = DBusConnectionBuilder.forSystemBus().build();
        	else if(busname.equals("session"))
                conx = DBusConnectionBuilder.forSessionBus().build();
        	else
        	    conx = DBusConnectionBuilder.forAddress(busname).build();
        }
        else {
        	conx = DBusConnectionBuilder.forSessionBus().build();
        }
        
        /* Request bus */
        conx.requestBusName(RemotePlatformService.BUS_NAME);
        
        /* Build and export services */
        try(var rs = new RemotePlatformServiceDelegate(ps, conx)) {
        
	        /* Lets go! */
	        alert(BUNDLE.getString("ready"), conx.getAddress());
	        
	        while(true)
	        	sleep(Integer.MAX_VALUE);
        }
        
    }

	@Override
	public void alert(String message, Object... args) {
        System.out.format("[+] %s%n", MessageFormat.format(message, args));
	}

    @Override
    public ScheduledExecutorService queue() {
        return queue;
    }

    @Override
    public SystemConfiguration configuration() {
        if (configuration == null) {
            configuration = new LbvConfiguration();
        }
        return configuration;
    }

    @Override
    public SystemCommands commands() {
        if(commands == null)
            commands = new ElevatableSystemCommands();
        return commands;
    }

    @Override
    public NativeComponents nativeComponents() {
        if(nativeComponents == null)
            nativeComponents = new NativeComponents();
        return nativeComponents;
    }

    @Override
    public void addScriptEnvironmentVariables(VpnAdapter connection, Map<String, String> env) {
    }
    
    static void logCommandLine(String... args) {
        var largs = new ArrayList<>(Arrays.asList(args));
        var path = Paths.get(largs.get(0));
        if(path.isAbsolute()) {
            largs.set(0, path.getFileName().toString());
        }
        System.out.format("[#] %s%n", String.join(" ", largs));
    }
}
