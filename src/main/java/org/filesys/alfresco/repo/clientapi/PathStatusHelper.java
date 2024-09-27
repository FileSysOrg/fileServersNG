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
import org.alfresco.service.cmr.repository.NodeService;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.clientapi.json.ClientAPIException;
import org.filesys.server.filesys.clientapi.json.PathStatusInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Get Path Status Helper Class
 *
 * <p>Get Path Status request helper class</p>
 *
 * @author gkspencer
 */
public class PathStatusHelper {

    /**
     * Validate the get path status check type
     *
     * @param chkType String
     * @return PathCheckType
     */
    public static PathCheckType asCheckType( String chkType) {

        // Make sure the check type is not empty
        if ( chkType.isEmpty())
            return PathCheckType.Invalid;

        // Check for a valid check type string
        for ( PathCheckType typ : PathCheckType.values()) {
            if ( typ.name().equalsIgnoreCase( chkType))
                return typ;
        }

        // Not a valid check type
        return PathCheckType.Invalid;
    }

    /**
     * Generate the path status list for a list of paths for the specified check type
     *
     * @param api AlfrescoClientAPI
     * @param chkType PathCheckType
     * @param nodes List&lt;NodeDetails&gt;
     * @return List&lt;PathStatusInfo&gt;
     * @throws ClientAPIException If a processing error occurs
     */
    public static List<PathStatusInfo> checkPathStatusForPaths( AlfrescoClientApi api, PathCheckType chkType, List<NodeDetails> nodes)
        throws ClientAPIException {

        // Make sure the list has entries
        if ( nodes.isEmpty())
            return null;

        // Create a list for the path status information
        List<PathStatusInfo> stsList = new ArrayList<>( nodes.size());

        // Process the paths and generate the requested path status information
        for ( NodeDetails node : nodes) {

            // Create path information for the current node
            PathStatusInfo pathInfo = new PathStatusInfo( node.getPath());

            // Generate the required path status information
            switch ( chkType) {

                // Check if the path is a working copy
                case WorkingCopy:
                    checkWorkingCopy( api, node, pathInfo);
                    break;

                // Check if the path is locked
                case Locked:
                    checkLocked( api, node, pathInfo);
                    break;
            }

            // Add the status information to the list
            stsList.add( pathInfo);
        }

        // Return the path status list
        return stsList;
    }

    /**
     * Check if a node is a working copy
     *
     * @param api AlfrescoClientApi
     * @param node NodeDetails
     * @param pathInfo PathStatusInfo
     */
    private static void checkWorkingCopy( AlfrescoClientApi api, NodeDetails node, PathStatusInfo pathInfo) {

        // Get the node service
        NodeService nodeService = api.getNodeService();

        // Check if the node has the working copy aspect
        if ( node.hasNodeRef()) {

            // Check that the node is not a working copy
            if ( nodeService.hasAspect( node.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                // Mark the path as being a working copy
                pathInfo.setStatus( true);
            }
        }

        // DEBUG
        if ( api.hasDebug())
            Debug.println("Check working copy, pathInfo=" + pathInfo);
    }

    /**
     * Check if a node is locked
     *
     * @param api AlfrescoClientApi
     * @param node NodeDetails
     * @param pathInfo PathStatusInfo
     */
    private static void checkLocked( AlfrescoClientApi api, NodeDetails node, PathStatusInfo pathInfo) {

        // Get the node service
        NodeService nodeService = api.getNodeService();

        // Check if the node has the lockable aspect and an active lock
        if ( nodeService.hasAspect( node.getNodeRef(), ContentModel.ASPECT_LOCKABLE)) {

            // Get the lock type
            String lockType = (String) nodeService.getProperty( node.getNodeRef(), ContentModel.PROP_LOCK_TYPE);

            if ( lockType != null) {

                // Indicate the path is locked, and return the lock type
                pathInfo.setStatus( true);
                pathInfo.setAdditionalInformation( lockType);
            }
        }

        // DEBUG
        if ( api.hasDebug())
            Debug.println("Check locked, pathInfo=" + pathInfo);
    }
}
