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
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;

import java.util.List;

/**
 * Nodes Class
 *
 * <p>Helper class with methods for dealing with NodeRefs from server-side scripts</p>
 *
 * @author gkspencer
 */
public class Nodes {

    // Node service
    private NodeService m_nodeService;

    /**
     * Class constructor
     *
     * @param nodeService NodeService
     */
    public Nodes( NodeService nodeService) {
        m_nodeService = nodeService;
    }

    /**
     * Check if a node is versioned
     *
     * @param nodeRef NodeRef
     * @return boolean
     */
    public final boolean isVersionable( NodeRef nodeRef) {
        return m_nodeService.hasAspect( nodeRef, ContentModel.ASPECT_VERSIONABLE);
    }

    /**
     * Check if a node is a working copy
     *
     * @param nodeRef NodeRef
     * @return boolean
     */
    public final boolean isWorkingCopy( NodeRef nodeRef) {
        return m_nodeService.hasAspect( nodeRef, ContentModel.ASPECT_WORKING_COPY);
    }

    /**
     * Check if a node is lockable
     *
     * @param nodeRef NodeRef
     * @return boolean
     */
    public final boolean isLockable( NodeRef nodeRef) {
        return m_nodeService.hasAspect( nodeRef, ContentModel.ASPECT_LOCKABLE);
    }

    /**
     * Get the working copy original node
     *
     * @param nodeRef NodeRef
     * @return NodeRef
     */
    public final NodeRef getWorkingCopyOriginal( NodeRef nodeRef) {

        // Make sure the node is a working copy
        NodeRef originalNode = null;

        if ( m_nodeService.hasAspect( nodeRef, ContentModel.ASPECT_WORKING_COPY)) {

            // Get the original file node ref
            List<AssociationRef> assocs = m_nodeService.getSourceAssocs( nodeRef, ContentModel.ASSOC_WORKING_COPY_LINK);

            if (assocs != null && assocs.size() == 1)
                originalNode = assocs.get(0).getSourceRef();
        }

        // Return the original node for the working copy, or null
        return originalNode;
    }

    /**
     * Check if a node has a lock
     *
     * @param nodeRef NodeRef
     * @return boolean
     */
    public final boolean isLocked( NodeRef nodeRef) {

        // Check if the node is lockable, and has a lock
        boolean locked = false;

        if (m_nodeService.hasAspect(nodeRef, ContentModel.ASPECT_LOCKABLE)) {

            // Get the lock type
            if (m_nodeService.getProperty(nodeRef, ContentModel.PROP_LOCK_TYPE) != null)
                locked = true;
        }

        return locked;
    }
}
