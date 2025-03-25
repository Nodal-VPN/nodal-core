/**
 * Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
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

import java.net.UnknownHostException;
import java.text.MessageFormat;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public class ExceptionHandler implements IExecutionExceptionHandler {
	
	private final AbstractCommand cmd;

	public ExceptionHandler(AbstractCommand cmd) {
		this.cmd = cmd;
	}

	@Override
	public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult)
			throws Exception {
		var report = new StringBuilder();
		var msg = ex.getMessage() == null ? "An unknown error occured." : ex.getMessage();
		if(ex instanceof UnknownHostException) {
			msg = MessageFormat.format("Could not resolve hostname {0}: Name or service not known.", ex.getMessage());
		}
		report.append(Ansi.AUTO.string("@|red " + cmd.spec.commandLine().getCommandName() + ": " + msg + "|@"));
		report.append(System.lineSeparator());
		if(cmd.verboseExceptions()) {
			Throwable nex = ex;
			int indent = 0;
			while(nex != null) {
				if(indent > 0) {
					report.append(String.format("%" + ( 8 + ((indent - 1 )* 2) ) + "s", ""));
			        report.append(Ansi.AUTO.string("@|red " + (nex.getMessage() == null ? "No message." : nex.getMessage())+ "|@"));
					report.append(System.lineSeparator());
				}
				
				for(var el : nex.getStackTrace()) {
					report.append(String.format("%" + ( 8 + (indent * 2) ) + "s", ""));
					report.append("at ");
					if(el.getModuleName() != null) {
						report.append(el.getModuleName());
						report.append('/');
					}
                    report.append(Ansi.AUTO.string("@|yellow " + el.getClassName() + "." + el.getMethodName() + "|@"));
					if(el.getFileName() != null) {
						report.append('(');
						report.append(el.getFileName());
						if(el.getLineNumber() > -1) {
							report.append(':');
		                    report.append(Ansi.AUTO.string("@|yellow " + String.valueOf(el.getLineNumber()) + "|@"));
							report.append(')');
						}
					}
					report.append(System.lineSeparator());
				}
				indent++;
				nex = nex.getCause();
			}
		}
		System.err.print(report.toString());
		return 0;
	}

}
