/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.alfresco.util;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

/**
 * WINS Utility Class
 */
public class WINS {

    /**
     * Return a comma delimeted list of WINS server TCP/IP addresses, or null if no WINS servers are
     * configured.
     *
     * @return String
     */
    public static String getWINSServerList() {

        // Open the NetBIOS over TCP/IP registry key
        WinReg.HKEYByReference pnbtKey = new WinReg.HKEYByReference();
        StringBuffer winsListStr = new StringBuffer();

        String ifaceRoot = "System\\CurrentControlSet\\Services\\NetBT\\Parameters\\Interfaces";

        if (Advapi32.INSTANCE.RegOpenKeyEx( WinReg.HKEY_LOCAL_MACHINE, ifaceRoot,
                0, WinNT.KEY_ENUMERATE_SUB_KEYS, pnbtKey) == W32Errors.ERROR_SUCCESS) {

            // Allocate a buffer for the subkey name
            char[] name = new char[256];
            IntByReference lpcchValueName = new IntByReference( 256);

            // Allocate a buffer for the WINS server list
            char[] winsBuf = new char[256];
            IntByReference lpcbData = new IntByReference();
            IntByReference lpType = new IntByReference();

            // Enumerate the interfaces
            int sts = W32Errors.ERROR_SUCCESS;
            int keyIndex = 0;

            while ( sts == W32Errors.ERROR_SUCCESS) {

                sts = Advapi32.INSTANCE.RegEnumKeyEx( pnbtKey.getValue(), keyIndex++, name, lpcchValueName, null, null, null, null);

                if ( sts != W32Errors.ERROR_SUCCESS)
                    continue;

                // Check if we found a TcpIP interface
                String ifaceName = Native.toString( name);

                if ( ifaceName.startsWith( "Tcpip_")) {

                    // Build the path to the specific interface key
                    String ifaceKey = ifaceRoot + "\\" + ifaceName;

                    // Check if there is a WINS server list for the current interface
                    try {
                        if (Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, ifaceKey, "NameServerList")) {

                            // Get the WINS name server list for the current interface
                            String[] winsList = Advapi32Util.registryGetStringArray(WinReg.HKEY_LOCAL_MACHINE, ifaceKey, "NameServerList");

                            if (winsList != null && winsList.length > 0) {
                                for (int i = 0; i < winsList.length; i++) {
                                    winsListStr.append(winsList[i]);
                                    winsListStr.append(",");
                                }
                            }
                        }
                    }
                    catch ( Exception ex) {

                    }
                }
            }

            // Close the interfaces key
            Advapi32.INSTANCE.RegCloseKey( pnbtKey.getValue());
        }

        return winsListStr.toString();
    }
}
