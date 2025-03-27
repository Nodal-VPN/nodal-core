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
package com.jadaptive.nodal.core.macos;

import static java.lang.String.format;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.jadaptive.nodal.core.lib.PlatformService;

/**
 * A Java interface to Mac OS `scutil` command, or at least enough of it to be used
 * by our DNS providers. 
 */
public class SCUtil {
    
    enum ShowState {
        OPEN_DICT,
        DATA_OR_CLOSE_DICT,
        ARRAY_ROW_OR_CLOSE_ARRAY,
        EOF
    }

    @SuppressWarnings("serial")
    public final static class Dictionary extends LinkedHashMap<String, Object> {
        
        private final String key;
        private final PlatformService<?> platform;

        Dictionary(String key, PlatformService<?> platform) {
            this.key = key;
            this.platform = platform;
        }

        public void set() throws IOException {
            try(var str = new StringWriter()) {
                str.append(String.format("d.init%n"));
                forEach((k, v) -> {
                    append(str, k, v); 
                });
                str.append(format("set %s%nquit%n", key));
                platform.context().commands().privileged().pipeTo(str.toString(), "scutil");
            }
        }
        
        public void add() throws IOException {
            try(var str = new StringWriter()) {
                str.append(String.format("d.init%n"));
                forEach((k, v) -> {
                    append(str, k, v); 
                });
                str.append(format("add %s%nquit%n", key));
                platform.context().commands().privileged().pipeTo(str.toString(), "scutil");
            }
        }

        @SuppressWarnings("unchecked")
        private void append(StringWriter str, String k, Object v) {
            if(v instanceof Collection)
                str.append(format("d.add %s %s%n", k, String.join(" ", (Collection<String>)v)));
            else
                str.append(format("d.add %s %s%n", k, v));
        }

        public String key() {
            return key;
        }
    }

    private final PlatformService<?> platform;

    public SCUtil(PlatformService<?> platform) {
        this.platform = platform;
    }

    public List<String> list() throws IOException {
        return platform.context().commands().pipeTo(String.format("list%nquit%n"), "scutil").stream()
                .map(s -> s.substring(s.indexOf('=') + 1)).collect(Collectors.toList());
    }

    public List<String> list(String pattern) throws IOException {
        return platform.context().commands().pipeTo("list " + pattern + System.lineSeparator(), "scutil")
                .stream().map(s -> s.substring(s.indexOf('=') + 1)).collect(Collectors.toList());
    }

    public Dictionary dictionary(String key) {
        return new Dictionary(key, platform);
    }

    public Dictionary get(String key) throws IOException {
        var dict = new Dictionary(key, platform);
        var state = ShowState.OPEN_DICT;
        List<String> arr = null;
        String arrKey = null;
        for(var line : platform.context().commands().pipeTo(String.format("show %s%nquit%n", key), "scutil")) {
            line = line.trim();
            switch(state) {
            case OPEN_DICT:
            	if(line.startsWith("No such key"))
            		throw new IllegalArgumentException("No such key " + key);
                if(!line.equals("<dictionary> {")) {
                    throw new IOException("Unexpected response.");
                }
                state = ShowState.DATA_OR_CLOSE_DICT;
                break;
            case DATA_OR_CLOSE_DICT:
                if(line.equals("}")) {
                    state = ShowState.EOF;
                }
                else {
                    var idx = line.indexOf(':');                    
                    var k = line.substring(0, idx - 1).trim();
                    var v = line.substring(idx + 1).trim();
                    if(v.equals("<array> {")) {
                        arr = new ArrayList<>();
                        arrKey = k;
                        state = ShowState.ARRAY_ROW_OR_CLOSE_ARRAY;
                    }
                    else {
                        dict.put(k, v);
                    }
                }
                break;
            case ARRAY_ROW_OR_CLOSE_ARRAY:
                if(line.equals("}")) {
                    dict.put(arrKey, arr);
                    state = ShowState.DATA_OR_CLOSE_DICT;
                    arrKey = null;
                    arr = null;
                }
                else {
                    var idx = line.indexOf(':');                   
                    var v = line.substring(idx + 1).trim();
                    arr.add(v);
                }
                break;
            case EOF:
                throw new IOException("Unexpected trailing response.");
             }
        }
        return dict;
    }
    
    public void remove(String key) throws IOException {
        platform.context().commands().privileged().pipeTo(String.format("remove %s%nquit%n", key), "scutil");
    }
}
