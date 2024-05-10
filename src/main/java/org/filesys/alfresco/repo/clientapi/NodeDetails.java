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

import org.alfresco.service.cmr.repository.NodeRef;
import org.filesys.server.filesys.FileType;
import org.filesys.server.filesys.clientapi.json.PathStatus;

/**
 * Node Details Class
 *
 * <p>Contains details of a relative path for a client API request, with the NodeRef,</p>
 */
public class NodeDetails {

    // Node details
    private String m_relPath;
    private FileType m_fileType;
    private NodeRef m_nodeRef;
    private PathStatus m_pathStatus;

    /**
     * Class constructor
     *
     * @param path String
     * @param nodeRef NodeRef
     */
    public NodeDetails( String path, FileType fType, NodeRef nodeRef) {
        m_relPath  = path;
        m_fileType = fType;
        m_nodeRef  = nodeRef;

        m_pathStatus = PathStatus.NotProcessed;
    }

    /**
     * Class constructor
     *
     * @param path String
     * @param sts PathStatus
     */
    public NodeDetails( String path, PathStatus sts) {
        m_relPath    = path;
        m_pathStatus = sts;
    }

    /**
     * Return the relative path
     *
     * @return String
     */
    public final String getPath() { return m_relPath; }

    /**
     * Return the file type
     *
     * @return FileType
     */
    public final FileType isType() { return m_fileType; }

    /**
     * Check if the NodeRef is valid
     *
     * @return boolean
     */
    public final boolean hasNodeRef() { return m_nodeRef != null; }

    /**
     * Return the NodeRef for the path
     *
     * @return NodeRef
     */
    public final NodeRef getNodeRef() { return m_nodeRef; }

    /**
     * Return status for this path
     *
     * @return PathStatus
     */
    public final PathStatus hasStatus() { return m_pathStatus; }

    /**
     * Set the relative path
     *
     * @param path String
     */
    public final void setPath( String path) { m_relPath = path; }

    /**
     * Set the path status
     *
     * @param sts PathStatus
     */
    public final void setStatus( PathStatus sts) { m_pathStatus = sts; }

    /**
     * Set the node ref for the path
     *
     * @param ref NodeRef
     */
    public final void setNodeRef( NodeRef ref) { m_nodeRef = ref; }

    /**
     * Return the node details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Path=");
        str.append( getPath());
        str.append(",type=");
        str.append( isType().name());
        str.append(",nodeRef=");
        str.append( getNodeRef());
        str.append(",sts=");
        str.append( hasStatus().name());

        return str.toString();
    }
}
