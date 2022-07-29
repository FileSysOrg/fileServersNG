/*
 * Copyright (C) 2021 GK Spencer
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
import org.filesys.smb.server.SMBServer;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * SMB Session List Webscript Class
 *
 * <p>Return the current session list for the SMB server</p>
 *
 * @author gkspencer
 */
public class SMBServerStatsScript extends AbstractWebScript {
    private static final Log logger = LogFactory.getLog( org.filesys.alfresco.webscripts.SMBServerStatsScript.class.getName());

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

        // Build the JSON response
        Writer out = webScriptResponse.getWriter();
        JSONObject json = new JSONObject();

        try {
            // Check if the SMB server bean has been set
            if (smbServerBean != null) {

                // Get the SMB server, if active
                SMBServer smbServer = smbServerBean.getSMBServer();

                if (smbServer != null) {

                    // Add the server details
                    json.put( "current_users", smbServer.getCurrentSessionCount());
                    json.put( "max_users", smbServer.getMaximumSessionCount());
                    json.put( "disconnected_sessions", smbServer.getDisconnectedSessionCount());

                    json.put( "server_name", smbServer.getServerName());
                } else {
                    json.put("error", "SMB server not active");
                }
            } else {
                json.put( "error", "SMB server bean not found");
            }
        }
        catch ( JSONException ex) {
            out.write("JSON Error: " + ex.toString());
        }

        // Create the JSON string and output to the response
        String jsonStr = json.toString();
        out.write( jsonStr);
    }
}
