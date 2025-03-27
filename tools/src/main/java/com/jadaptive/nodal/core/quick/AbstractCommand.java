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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.slf4j.bridge.SLF4JBridgeHandler;

import com.sshtools.liftlib.Helper;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class AbstractCommand implements Callable<Integer> {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR,
    }

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<Level> level;

    @Option(names = { "--elevate" }, hidden = true, paramLabel = "SOCKET_PATH", description = "Run this as an elevated helper")
    private Optional<String> socketPath;

    @Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;

    @Option(names = { "-D", "--sysprop" }, description = "Set a system property.")
    private List<String> systemProperties;
    
    @Spec
    CommandSpec spec;

    @Override
    public final Integer call() throws Exception {
        initCommand();
        return onCall();
    }
    
    public final void initCommand() throws Exception {
        var defaultLevel = level.orElse(Level.WARN);
        
        if(systemProperties != null) {
	        for(var str: systemProperties) {
	        	var idx = str.indexOf('=');
	        	if(idx == -1) {
	        		System.setProperty(str, "true");
	        	}
	        	else {
	        		System.setProperty(str.substring(0, idx), str.substring(idx + 1));
	        	}
	        }
        }
        
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", defaultLevel.name());
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(toJulLevel(defaultLevel));
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(toJulLevel(defaultLevel));
        }
        if(socketPath.isPresent()) {
            Helper.main(new String[] { socketPath.get() });
            System.exit(0);
        }
    }
    
    private static java.util.logging.Level toJulLevel(Level defaultLevel) {
    	switch(defaultLevel) {
    	case TRACE:
    		return java.util.logging.Level.FINEST;
    	case DEBUG:
    		return java.util.logging.Level.FINE;
    	case INFO:
    		return java.util.logging.Level.INFO;
    	case WARN:
    		return java.util.logging.Level.WARNING;
    	case ERROR:
    		return java.util.logging.Level.SEVERE;
    	default:
    		return java.util.logging.Level.OFF;
    	}
	}

	boolean verboseExceptions() {
        return verboseExceptions;
    }
    
    protected abstract Integer onCall() throws Exception;
}
