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

import org.filesys.debug.Debug;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import java.util.EnumSet;
import java.util.List;

/**
 * Open In Share Server Action Class
 *
 * <p>Returns a URL to open the selected file in Share</p>
 *
 * @author gkspencer
 */
public class OpenInShareServerAction extends ServerAction {

    /**
     * Class constructor
     */
    protected OpenInShareServerAction() {
        super( EnumSet.of( ServerAction.Flags.Files));

        // Set the context menu icon
        setIcon( ActionIcon.createShellIcon( ActionIcon.SHELLICON_WEBBROWSER));
    }

    @Override
    public ClientAPIResponse runAction(RunActionRequest req, ClientAPINetworkFile netFile)
            throws ClientAPIException {

        // DEBUG
        if (req.hasDebug())
            Debug.println("[OpenInShare] Open in Share request=" + req);

        // Make sure there is a path
        if (req.getRelativePaths().isEmpty())
            throw new ClientAPIException("No path selected to open in Share", false);

        // Access the client API
        if ( !req.hasClientAPI() || !(req.getClientAPI() instanceof AlfrescoClientApi))
            throw new ClientAPIException("Client API not set in request", false);
        AlfrescoClientApi alfApi = (AlfrescoClientApi) req.getClientAPI();

        // Make sure the Share base URL has been set
        if ( alfApi.getShareBaseURL().isEmpty())
            throw new ClientAPIException("Share Base URL not configured on server", false);

        // Convert the path to a NodeRef
        String path = req.getRelativePaths().get(0);
        NodeDetails nodeDetails = alfApi.getNodeDetailsForPath( path);

        if ( nodeDetails == null || !nodeDetails.hasNodeRef())
            throw new ClientAPIException("Failed to find details for path " + path, false);

        // Build the Share URL to the selected file
        String shareUrl = alfApi.buildSharePathForNode( nodeDetails);

        if ( shareUrl == null)
            throw new ClientAPIException("Failed to build share URL for path " + path, false);

        // Return the URL and open the web browser on the client
        RunActionResponse response = new RunActionResponse();

        response.setOpenURL( shareUrl);
        return response;
    }
}
