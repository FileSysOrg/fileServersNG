/*
 * Copyright (C) 2024 GK Spencer
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

package org.filesys.alfresco.repo.clientapi;

import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.scripts.ScriptException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.transaction.TransactionService;
import org.filesys.alfresco.base.DesktopParams;
import org.filesys.alfresco.base.DesktopTarget;
import org.filesys.alfresco.repo.ContentContext;
import org.filesys.alfresco.repo.SMBHelper;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.TransactionalFilesystemInterface;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Alfresco Scripted Server Action Class
 *
 * <p>Implements running server side scripts for Alfresco</p>
 *
 * @author gkspencer
 */
public class AlfrescoScriptedServerAction extends ScriptedServerAction {

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     */
    public AlfrescoScriptedServerAction(String name, String desc, EnumSet<Flags> flags, String scriptPath) {
        super(name, desc, flags, scriptPath);

        // Set the context menu icon
        setIcon( ActionIcon.createShellIcon( ActionIcon.SHELLICON_SCRIPT));
    }

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     * @param icon ActionIcon
     */
    public AlfrescoScriptedServerAction(String name, String desc, EnumSet<Flags> flags, String scriptPath, ActionIcon icon) {
        super(name, desc, flags, scriptPath);

        // Set the context menu icon
        if ( icon != null)
            setIcon( icon);
        else
            setIcon( ActionIcon.createShellIcon( ActionIcon.SHELLICON_SCRIPT));
    }

    @Override
    public ClientAPIResponse runAction(RunActionRequest req, ClientAPINetworkFile netFile)
        throws ClientAPIException {

        // DEBUG
        if ( req.hasDebug())
            Debug.println("[Script] Run script request=" + req);

        // Check that the script file exists
        Path scriptPath = Path.of( getScriptPath());

        if ( !Files.exists( scriptPath)) {

            // DEBUG
            if ( req.hasDebug())
                Debug.println("[Script] Script " + getScriptPath() + " does not exist");

            // Return an error
            throw new ClientAPIException("Cannot find server script for '" + req.getAction() + "'");
        }

        // Get the script modification timestamp, check if the script has been updated and needs reloading
        long scriptModifyAt = 0L;

        try {
            scriptModifyAt = Files.getLastModifiedTime(scriptPath).toMillis();
        }
        catch (IOException ignored) {
        }

        // Check if the script text is loaded
        if ( !hasScriptText() || ( scriptModifyAt != 0L && scriptModifyAt != getScriptModifiedAt())) {

            try {
                // Load the script text
                String scriptText = Files.readString(scriptPath);

                // Save the script text in the scripted action, and the current script modification timestamp
                setScriptText( scriptText);
                setScriptModifiedAt( scriptModifyAt);

                // DEBUG
                if ( req.hasDebug())
                    Debug.println("[Script] Script " + getScriptPath() + " text loaded/reloaded");
            }
            catch ( IOException ex) {
                throw new ClientAPIException("Failed to load script '" + getScriptPath() + "', " + ex.getMessage());
            }
        }

        ClientAPIResponse resp = null;

        // Access the client API
        if ( !req.hasClientAPI() || !(req.getClientAPI() instanceof AlfrescoClientApi))
            throw new ClientAPIException("Client API not set in request", false);
        AlfrescoClientApi alfApi = (AlfrescoClientApi) req.getClientAPI();

        // Start a transaction
        if( this instanceof TransactionalFilesystemInterface)
        {
            TransactionalFilesystemInterface tx = (TransactionalFilesystemInterface) this;
            tx.beginReadTransaction( netFile.getSession());
        }

        // Get an authentication ticket for the client, or validate the existing ticket. The ticket can be used when
        // generating URLs for the client-side application so that the user does not have to re-authenticate
        alfApi.getTicketForClient( netFile.getSession());

        // Get the parent folder node from the request path(s)
        NodeRef parentNode = null;
        String parentPath = null;

        if ( !req.getRelativePaths().isEmpty()) {
            parentPath = FileName.getParentPart(req.getRelativePaths().get(0));
            parentNode = alfApi.getParentNodeForPath( parentPath);
        }

        // Create the DesktopParams
        DesktopParams deskParams = new DesktopParams( netFile.getSession(), parentNode, null);

        // Get various services and helpers
        SMBHelper smbHelper = alfApi.getSmbHelper();
        TransactionService transService = alfApi.getTransactionService();
        ServiceRegistry serviceRegistry = alfApi.getServiceRegistry();
        ContentContext context = alfApi.getContentContext();
        Nodes nodes = alfApi.getNodes();

        // Add desktop targets for each path
        for ( String path : req.getRelativePaths()) {

            try {
                // Get the node for the current path, all paths should be absolute paths
                NodeRef node = smbHelper.getNodeRef( context.getRootNode(), path);

                // Determine the path type
                DesktopTarget.Type targetTyp = smbHelper.isDirectory( node) ? DesktopTarget.Type.Folder : DesktopTarget.Type.File;

                // Add the desktop target
                deskParams.addTarget( new DesktopTarget( targetTyp, path, node));
            }
            catch ( FileNotFoundException ex) {

                // Return an error
                resp = RunActionResponse.createErrorResponse( "Could not find node for path '" + path + "'");
                return resp;
            }
        }

        // Run the scripted action
        final ScriptService scriptService = alfApi.getScriptService();
        final ScriptedServerAction scriptAction = this;

        if ( scriptService != null)
        {
            // Create the objects to be passed to the script
            final Map<String, Object> model = new HashMap<String, Object>();
            model.put("params", deskParams);
            model.put("result", new RunActionResponse());
            model.put("out", System.out);

            model.put("registry", serviceRegistry);
            model.put("nodes", nodes);

            // Add the Share webapp base URL, if valid
            if ( alfApi.getShareBaseURL() != null)
                model.put("shareURL", alfApi.getShareBaseURL());

            // If the parent path is valid update the parent folder timestamps
            if ( parentPath != null)
                alfApi.updateParentFolderTimestamps( parentPath, context);

            // Run the scripted action in a transaction
            RetryingTransactionHelper tx = transService.getRetryingTransactionHelper();

            RetryingTransactionHelper.RetryingTransactionCallback<RunActionResponse> runScriptCB = new RetryingTransactionHelper.RetryingTransactionCallback<RunActionResponse>() {

                @Override
                public RunActionResponse execute() throws Throwable
                {
                    RunActionResponse response = null;

                    try
                    {
                        // Run the script
                        Object result = scriptService.executeScriptString( scriptAction.getScriptText(), model);

                        // Check the result
                        if (result != null)
                        {
                            // Check for a full response object
                            if (result instanceof RunActionResponse)
                            {
                                response = (RunActionResponse) result;
                            }
                            // Encoded response in the format '<status>,<stsMessage>'
                            else if (result instanceof String)
                            {
                                String responseMsg = (String) result;

                                // Parse the status message
                                StringTokenizer token = new StringTokenizer(responseMsg, ",");
                                String stsToken = token.nextToken();
                                String msgToken = token.nextToken();

                                if ( stsToken.equalsIgnoreCase( "Ok"))
                                    response = new RunActionResponse();
                                else if ( stsToken.equalsIgnoreCase( "Error"))
                                    response = RunActionResponse.createErrorResponse( msgToken);
                            }
                            else {
                                Debug.println("[Script] Script returned result=" + result.getClass().getSimpleName());
                            }
                        }

                        // Return the response
                        return response;
                    }
                    catch (ScriptException ex)
                    {
                        return RunActionResponse.createErrorResponse( ex.getMessage());
                    }
                }
            };

            // Compute the response in a retryable write transaction
            resp = tx.doInTransaction(runScriptCB, false, false);
        }
        else
        {
            // Return an error response, script service not available
            return RunActionResponse.createErrorResponse("Script service not available");
        }

        // DEBUG
        if ( req.hasDebug())
            Debug.println("[Script] Returning " + resp);

        return resp;
    }
}
