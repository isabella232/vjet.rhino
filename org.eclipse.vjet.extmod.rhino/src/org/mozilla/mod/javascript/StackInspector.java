/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * Portions created by eBay are Copyright (c) 2005, 2012 eBay Inc. All rights reserved.
 * 
 * Contributor(s):
 *   Yitao Yao
 *   Justin Early
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.mozilla.mod.javascript;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.mozilla.mod.javascript.Interpreter.CallFrame;

/**
 * This is a helper class added by eBay to retrieve javascript stack info.
 * 
 * To enable this class, one needs to modify existing Interpreter class to
 * make its CallFrame inner class has a default access. See EBAY_MODIFICATION.
 */
public class StackInspector {
	
	private static final List<StackInfo> EMPTY = new ArrayList<StackInfo>();
	
	public static List<StackInfo> getStack() {
		Context cx = Context.getCurrentContext();
        if (cx == null) {
        	return EMPTY;
        }
        List<StackInfo> stackInfos = new ArrayList<StackInfo>();
        if (cx.lastInterpreterFrame == null) {
        	//not in interprete mode, so using following hack to get info from java stacktrace
            CharArrayWriter writer = new CharArrayWriter();
            RuntimeException re = new RuntimeException();
            re.printStackTrace(new PrintWriter(writer));
            String s = writer.toString();
            int open = -1;
            int close = -1;
            int colon = -1;
            for (int i=0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ':') {
                    colon = i;
                } else if (c == '(') {
                    open = i;
                } else if (c == ')') {
                    close = i;
                } else if (c == '\n' && open != -1 && close != -1 && colon != -1 && open < colon && colon < close) {
                    String fileStr = s.substring(open + 1, colon);
                    if (!fileStr.endsWith(".java")) {
                        String lineStr = s.substring(colon + 1, close);
                        try {
                            int lineNumber = Integer.parseInt(lineStr);
                            if (lineNumber < 0) {
                            	lineNumber = 0;
                            }
                            stackInfos.add(new StackInfo(fileStr, lineNumber));
                        }
                        catch (NumberFormatException e) {
                            // fall through
                        }
                    }
                    open = close = colon = -1;
                }
            }
        }
        else {
	        // has interpreter frame on the stack
	        CallFrame[] array;
	        if (cx.previousInterpreterInvocations == null || cx.previousInterpreterInvocations.size() == 0) {
	            array = new CallFrame[1];
	        } else {
	            int previousCount = cx.previousInterpreterInvocations.size();
	            if (cx.previousInterpreterInvocations.peek() == cx.lastInterpreterFrame) {
	                // It can happen if exception was generated after
	                // frame was pushed to cx.previousInterpreterInvocations
	                // but before assignment to cx.lastInterpreterFrame.
	                // In this case frames has to be ignored.
	                --previousCount;
	            }
	            array = new CallFrame[previousCount + 1];
	            cx.previousInterpreterInvocations.toArray(array);
	        }
	        array[array.length - 1]  = (CallFrame)cx.lastInterpreterFrame;
	
	        // Get StackInfo from all interpreter frames. Start from the most nested frame
	        for (int i = array.length - 1; i >= 0; i--) {
	            CallFrame frame = array[i];
	            while (frame != null) {
	            	int lineNumber = 0;
	                if (frame.pcSourceLineStart >= 0) {
	                	lineNumber = getIndex(frame.idata.itsICode, frame.pcSourceLineStart);
	                }
	                String functionName = frame.fnOrScript.getFunctionName();
	                if (functionName.length() == 0) {
	                	Object value = frame.fnOrScript.get("_name", frame.fnOrScript);
	                	if (value instanceof String) {
	                		functionName = (String)value;							
						}
	                }
	            	stackInfos.add(new StackInfo(frame.idata.itsSourceFile, lineNumber, functionName));
	                frame = frame.parentFrame;
	            }
	        }
        }
		return stackInfos;
	}
	
    private static int getIndex(byte[] iCode, int pc) {
        return ((iCode[pc] & 0xFF) << 8) | (iCode[pc + 1] & 0xFF);
    }
	
	public static class StackInfo {
		private final String m_sourceName;
		private final int m_lineNumber;
		private final String m_functionName;
		
		StackInfo(String sourceName, int lineNumber, String functionName) {
			m_sourceName = sourceName;
			m_lineNumber = lineNumber;
			m_functionName = functionName;
		}
		
		StackInfo(String sourceName, int lineNumber) {
			this(sourceName, lineNumber, null);
		}	
		
		public String getSourceName() {
			return m_sourceName;
		}
		
		public int getLineNumber() {
			return m_lineNumber;
		}
		
		public String getFunctionName() {
			return m_functionName;
		}
		
		public String toString() {
			return "at " + (m_functionName == null ? "" : m_functionName) 
				+ " (" + m_sourceName  + ":" + m_lineNumber + ")";
		}		
	}
}
