package org.filesys.alfresco.webscripts;

import org.alfresco.repo.management.subsystems.ChildApplicationContextFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.alfresco.SMBServerBean;
import org.filesys.server.SrvSession;
import org.filesys.server.SrvSessionList;
import org.filesys.smb.server.SMBServer;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.VirtualCircuit;
import org.filesys.smb.server.VirtualCircuitList;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * SMB Session and VC List Webscript Class
 *
 * <p>Return the current session list with virtual circuit details for the SMB server</p>
 *
 * @author gkspencer
 */
public class SMBSessionVCListScript extends AbstractWebScript {
    private static final Log logger = LogFactory.getLog( SMBSessionListScript.class.getName());

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

                if (sessList != null && sessList.numberOfSessions() > 0) {

                    Enumeration<SrvSession> enumSess = sessList.enumerateSessions();
                    int idx = 1;

                    while (enumSess.hasMoreElements()) {
                        SrvSession curSess = enumSess.nextElement();

                        if (curSess != null && curSess instanceof SMBSrvSession) {

                            // Output the session details
                            SMBSrvSession smbSess = (SMBSrvSession) curSess;
                            out.write("" + idx++ + ": " + smbSess.toString());
                            out.write("\r\n");

                            // Output the virtual circuit details
                            VirtualCircuitList vcList = smbSess.getVirtualCircuitList();
                            Iterator<VirtualCircuit> vcIter = vcList.iterator();

                            while ( vcIter.hasNext()) {
                                VirtualCircuit vc = vcIter.next();
                                out.write("  " + vc.getId() + ": " + vc.toString());
                                out.write( "\r\n");
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
