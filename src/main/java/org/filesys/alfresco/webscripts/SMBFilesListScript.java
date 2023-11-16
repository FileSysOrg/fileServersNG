/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.alfresco.webscripts;

import org.alfresco.repo.management.subsystems.ChildApplicationContextFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.alfresco.SMBServerBean;
import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.SrvSessionList;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.smb.server.SMBServer;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.VirtualCircuit;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * SMB Files List Webscript Class
 *
 * <p>Return the current open files list for the SMB server</p>
 *
 * @author gkspencer
 */
public class SMBFilesListScript extends AbstractWebScript {
    private static final Log logger = LogFactory.getLog( SMBFilesListScript.class.getName());

    // FileServersNG subsystem context factory
    private ChildApplicationContextFactory contextFactory;

    /**
     * Set the subsystem context factory
     *
     * @param ctxFactory ContextFactory
     */
    public void setContextFactory(ChildApplicationContextFactory ctxFactory)
    {
        this.contextFactory = ctxFactory;
    }

    @Override
    public void execute(WebScriptRequest webScriptRequest, WebScriptResponse webScriptResponse) throws IOException {

        // Find the SMB server bean
        SMBServerBean smbServerBean = (SMBServerBean) contextFactory.getApplicationContext().getBean( "smbServer");

        // Create a writer for the output
        Writer out = webScriptResponse.getWriter();

        // Check if the SMB server bean has been set
        if ( smbServerBean != null) {

            // Get the session list from the SMB server
            SMBServer smbServer = smbServerBean.getSMBServer();

            if (smbServer != null) {

                // Output the current and maximum user counts
                out.write("Users: " + smbServer.getCurrentSessionCount() + "/" + smbServer.getMaximumSessionCount());
                out.write("\r\n");

                // Get the active session list
                SrvSessionList sessList = smbServer.getSessions();

                if ( sessList != null && sessList.numberOfSessions() > 0) {
                    Enumeration<SrvSession> sessEnum = sessList.enumerateSessions();

                    while ( sessEnum.hasMoreElements()) {
                        SrvSession curSess = sessEnum.nextElement();

                        if ( curSess != null && curSess instanceof SMBSrvSession) {
                            SMBSrvSession smbSess = (SMBSrvSession) curSess;

                            // Iterate the virtual circuit list for open files
                            if ( smbSess.getVirtualCircuitList() != null) {
                                Iterator<VirtualCircuit> vcIter = smbSess.getVirtualCircuitList().iterator();

                                while (vcIter.hasNext()) {
                                    VirtualCircuit vc = vcIter.next();

                                    if ( vc.getConnectionCount() > 0) {

                                        // Iterate the tree connections on the current virtual circuit
                                        Iterator<Integer> treeIdIter = vc.iterateConnections();

                                        while ( treeIdIter != null && treeIdIter.hasNext()) {
                                            TreeConnection tree = vc.findConnection( treeIdIter.next());
                                            if ( tree != null && tree.openFileCount() > 0) {
                                                out.write("  Session id=" + curSess.getUniqueId() + ", vc=" + vc.getId() + ":\r\n");

                                                Iterator<Integer> fileIdIter = tree.iterateOpenFileHandles();

                                                while( fileIdIter != null && fileIdIter.hasNext()) {
                                                    Integer fileId = fileIdIter.next();
                                                    NetworkFile curFile = tree.findFile( fileId);
                                                    out.write("    " + fileId + "/0x" + Integer.toHexString( fileId) + ": " + curFile.getClass().getName() + "=" + curFile + "\r\n");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                out.write("%% SMB server not active");
            }
        }
        else {
            out.write("%% SMBServerBean not found");
        }
    }
}
