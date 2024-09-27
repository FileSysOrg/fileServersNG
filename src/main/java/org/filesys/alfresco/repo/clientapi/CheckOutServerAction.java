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
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.filesys.alfresco.repo.ContentContext;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.NotifyAction;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Check Out Server Action Class
 *
 * <p>Check out file(s) from Alfresco and create working copy file(s)</p>
 *
 * @author gkspencer
 */
public class CheckOutServerAction extends ServerAction {

    /**
     * Class constructor
     */
    protected CheckOutServerAction() {
        super( EnumSet.of( Flags.Files, Flags.MultiSelect));

        // Set the context menu icon
        setIcon( ActionIcon.createAppIcon( ActionIcon.APPICON_ALFRESCO_CHECKOUT));
    }

    @Override
    public ClientAPIResponse runAction(RunActionRequest req, ClientAPINetworkFile netFile)
            throws ClientAPIException {

        // DEBUG
        if ( req.hasDebug())
            Debug.println("[CheckOut] Check out files request=" + req);

        // Make sure there are paths to check out
        if (req.getRelativePaths().isEmpty())
            throw new ClientAPIException("No paths to check out", false);

        // Access the client API
        if ( !req.hasClientAPI() || !(req.getClientAPI() instanceof AlfrescoClientApi))
            throw new ClientAPIException("Client API not set in request", false);
        AlfrescoClientApi alfApi = (AlfrescoClientApi) req.getClientAPI();

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = alfApi.getNodeDetailsForPaths(req.getRelativePaths());

        // Process the list of nodes
        int chkCnt = 0;
        List<String> workPaths = new ArrayList<String>(nodes.size());
        NodeService nodeService = alfApi.getNodeService();
        CheckOutCheckInService checkOutCheckInService = alfApi.getCheckOutInService();
        ContentContext filesysContext = alfApi.getContentContext();

        for (NodeDetails curNode : nodes) {

            // Make sure the node status is not an error
            if (curNode.hasNodeRef()) {

                // Check that the node is not a working copy
                if ( nodeService.hasAspect(curNode.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                    // DEBUG
                    if ( req.hasDebug())
                        Debug.println("[CheckOut] Path is a working copy, cannot check out, path=" + curNode.getPath());

                    // Indicate the path is a working copy
                    curNode.setStatus(PathStatus.WorkingCopy);
                } else if ( nodeService.hasAspect(curNode.getNodeRef(), ContentModel.ASPECT_LOCKABLE)) {

                    // Get the lock type
                    if ( nodeService.getProperty(curNode.getNodeRef(), ContentModel.PROP_LOCK_TYPE) != null) {

                        // DEBUG
                        if ( req.hasDebug())
                            Debug.println("[CheckOut] Path is locked, cannot check out, path=" + curNode.getPath());

                        // Indicate the path is locked
                        curNode.setStatus(PathStatus.Locked);
                    }
                }

                // Check out the file
                if (curNode.hasStatus() == PathStatus.NotProcessed) {

                    // Check out the file
                    NodeRef workingCopyNode = checkOutCheckInService.checkout(curNode.getNodeRef());

                    // Update the count of checked out files
                    chkCnt++;

                    // Get the working copy file name
                    String workingCopyName = (String) nodeService.getProperty(workingCopyNode, ContentModel.PROP_NAME);

                    // DEBUG
                    if ( req.hasDebug())
                        Debug.println("[CheckOut] Checked out working copy " + workingCopyName);

                    // Build the relative path to the checked out file
                    String parentFolderPath = FileName.removeFileName( curNode.getPath());
                    String workingCopyPath = parentFolderPath + FileName.DOS_SEPERATOR_STR + workingCopyName;
                    workPaths.add(workingCopyPath);

                    // Update cached state for the working copy to indicate the file exists
                    FileStateCache stateCache = filesysContext.getStateCache();
                    if (stateCache != null) {

                        // Update any cached state for the working copy file
                        FileState fstate = stateCache.findFileState(workingCopyPath);
                        if (fstate != null)
                            fstate.setFileStatus(FileStatus.FileExists);

                        // Update the parent folder timestamps
                        alfApi.updateParentFolderTimestamps( parentFolderPath, filesysContext);
                    }

                    // Check if there are any file/directory change notify requests active
                    if ( filesysContext.hasFileServerNotifications()) {

                        // Queue a file added change notification
                        filesysContext.getChangeHandler().notifyFileChanged(NotifyAction.Added, workingCopyPath);
                    }

                    // Indicate the file check out was successful
                    curNode.setStatus(PathStatus.Success);
                }
            }
        }

        // If the working copy paths list is empty then no files were checked out
        RunActionResponse response = new RunActionResponse();

        if ( !workPaths.isEmpty()) {

            // Return a success status to the client, with a list of the new working copy paths and a desktop notification
            response.setCreatedPaths(workPaths);
            response.setRefreshOriginal(true);

            // Build a notification
            StringBuilder notifyStr = new StringBuilder();

            if ( workPaths.size() == 1) {
                notifyStr.append("Checked out 1 file:\n  ");
                notifyStr.append(workPaths.get( 0));
            }
            else {

                // Show the total number of checked out files and up to 3 paths
                notifyStr.append("Checked out ");
                notifyStr.append( workPaths.size());
                notifyStr.append( " files:\n");

                int cnt = Integer.min(workPaths.size(), 3);
                for ( int idx = 0; idx < cnt; idx++) {
                    notifyStr.append( workPaths.get( idx));
                    notifyStr.append( ", ");
                }
                if ( workPaths.size() > cnt)
                    notifyStr.append( "...\n");
            }
            response.setNotification(new NotificationAction( notifyStr.toString()));
        }
        else {

            // Return an error
            response.setError( "No files checked out");
        }

        return response;
    }
}
