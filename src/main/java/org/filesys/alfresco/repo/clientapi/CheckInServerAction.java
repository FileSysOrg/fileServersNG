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

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.filesys.alfresco.repo.ContentContext;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.NotifyAction;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import java.io.Serializable;
import java.util.*;

/**
 * Check In Server Action Class
 *
 * <p>Check in file(s), updating the original file(s), and removing the working copy file(s)
 * and lock(s)</p>
 *
 * @author gkspencer
 */
public class CheckInServerAction extends ServerAction {

    // Check in sub-actions, either check in the files or cancel the check out
    public final static String SUBACTION_CHECKIN    = "checkin";
    public final static String SUBACTION_CANCEL     = "cancel";

    /**
     * Class constructor
     */
    protected CheckInServerAction() {
        super( EnumSet.of( Flags.Files, Flags.MultiSelect));

        // Show the check in dialog on the client before running the server action
        setUIAction( new ClientUIAction(ClientUIAction.UIAction.CheckInDialog,
                            "", ""));

        // Set the context menu icon
        setIcon( ActionIcon.createAppIcon( ActionIcon.APPICON_ALFRESCO_CHECKIN));
    }

    @Override
    public ClientAPIResponse runAction(RunActionRequest req, ClientAPINetworkFile netFile)
            throws ClientAPIException {

        // DEBUG
        if ( req.hasDebug())
            Debug.println("[CheckIn] Check in files request=" + req);

        // Make sure there are paths to check out
        if (req.getRelativePaths().isEmpty())
            throw new ClientAPIException("No paths to check out", false);

        // Make sure there is at least one parameter with the sub-action
        if ( !req.hasParameters())
            throw new ClientAPIException("Check in sub-action parameter not specified", false);

        // Validate the sub-action parameter
        String subAction = req.getParameters().get( 0);
        boolean doCheckIn = true;

        if ( subAction.equalsIgnoreCase( SUBACTION_CHECKIN))
            doCheckIn = true;
        else if ( subAction.equalsIgnoreCase( SUBACTION_CANCEL))
            doCheckIn = false;
        else
            throw new ClientAPIException("Invalid check in sub-action - '" + subAction + "'", false);

        // Access the client API
        if ( !req.hasClientAPI() || !(req.getClientAPI() instanceof AlfrescoClientApi))
            throw new ClientAPIException("Client API not set in request", false);
        AlfrescoClientApi alfApi = (AlfrescoClientApi) req.getClientAPI();

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = alfApi.getNodeDetailsForPaths(req.getRelativePaths());

        // Process the list of nodes
        int chkCnt = 0;
        List<String> origPaths = new ArrayList<String>(nodes.size());
        NodeService nodeService = alfApi.getNodeService();
        CheckOutCheckInService checkOutCheckInService = alfApi.getCheckOutInService();
        ContentContext filesysContext = alfApi.getContentContext();

        for (NodeDetails curNode : nodes) {

            // Make sure the node status is not an error
            if (curNode.hasNodeRef()) {

                // Check that the node is a working copy
                if ( nodeService.hasAspect(curNode.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                    try {

                        // Get the original file node ref
                        List<AssociationRef> assocs = nodeService.getSourceAssocs(curNode.getNodeRef(), ContentModel.ASSOC_WORKING_COPY_LINK);
                        NodeRef originalRef = null;

                        if (assocs != null && assocs.size() == 1)
                            originalRef = assocs.get(0).getSourceRef();

                        // Check in the file or cancel the check out
                        if ( doCheckIn) {

                            // Check in the file, pass an empty version properties so that versionable nodes create a new version
                            Map<String, Serializable> versionProperties = new HashMap<String, Serializable>();
                            checkOutCheckInService.checkin(curNode.getNodeRef(), versionProperties, null, false);

                            // DEBUG
                            if ( req.hasDebug())
                                Debug.println("[CheckIn] Checked in working copy " + curNode.getPath());
                        }
                        else {

                            // Cancel the file check out
                            checkOutCheckInService.cancelCheckout(curNode.getNodeRef());

                            // DEBUG
                            if ( req.hasDebug())
                                Debug.println("[CheckIn] Cancelled check out, working copy " + curNode.getPath());
                        }

                        // Update the count of checked in files
                        chkCnt++;

                        // Update the node details with the original document node ref
                        curNode.setNodeRef(originalRef);

                        // Check if there are any file/directory change notify requests active
                        if ( filesysContext.hasFileServerNotifications()) {

                            // Queue a file added change notification
                            filesysContext.getChangeHandler().notifyFileChanged(NotifyAction.Removed, curNode.getPath());
                        }

                        // Indicate the file check in, or cancel check out, was successful
                        curNode.setStatus(PathStatus.Success);

                        if (curNode.hasNodeRef()) {

                            // Get the original file name
                            String origName = (String) nodeService.getProperty(curNode.getNodeRef(), ContentModel.PROP_NAME);

                            // Build the relative path to the original file
                            String parentFolderPath = FileName.removeFileName( curNode.getPath());
                            String origPath = parentFolderPath + FileName.DOS_SEPERATOR_STR + origName;

                            // Update the relative path to the original document path
                            curNode.setPath(origPath);

                            // Add to the list of checked in/cancelled check out paths
                            origPaths.add(origPath);

                            // Update the parent folder timestamps
                            alfApi.updateParentFolderTimestamps( parentFolderPath, filesysContext);
                        }

                    } catch (Exception ex) {
                    }
                }
            }
            else if ( req.hasDebug())
                Debug.println("[CheckIn] Node details for " + curNode.getPath() + " does not have NodeRef, details=" + curNode);
        }

        // Return a success status to the client, with a list of the new working copy paths and a desktop notification
        RunActionResponse response = new RunActionResponse();
        response.setRemovedPaths( req.getRelativePaths());
        response.setRefreshOriginal(true);

        // Build a notification
        StringBuilder notifyStr = new StringBuilder();

        if ( origPaths.size() == 1) {
            notifyStr.append( doCheckIn ? "Checked in 1 file:\n  " : "Cancelled check out of 1 file:\n  ");
            notifyStr.append(origPaths.get( 0));
        }
        else if ( origPaths.size() > 1) {

            // Show the total number of checked in files and up to 3 paths
            notifyStr.append( doCheckIn ? "Checked in " : "Cancelled check out of ");
            notifyStr.append( origPaths.size());
            notifyStr.append( " files:\n");

            int cnt = Integer.min(origPaths.size(), 3);
            for ( int idx = 0; idx < cnt; idx++) {
                notifyStr.append( origPaths.get( idx));
                notifyStr.append( ", ");
            }
            if ( origPaths.size() > cnt)
                notifyStr.append( "...\n");
        }
        else {

            // Failed to check in/cancel check out of the file(s)
            notifyStr.append( doCheckIn ? "Failed to check in " : "Failed to cancel check out of ");
            notifyStr.append( req.getRelativePaths());
            notifyStr.append( " files");

            // DEBUG
            // Dump the node details list
            if ( req.hasDebug()) {
                Debug.println("[CheckIn] Failed to check in/cancel check out, node details:");
                for ( NodeDetails curNode : nodes) {
                    Debug.println("[CheckIn]  " + curNode);
                }
            }
        }

        // Set the client notification
        response.setNotification( new NotificationAction( notifyStr.toString()));

        return response;
    }
}
