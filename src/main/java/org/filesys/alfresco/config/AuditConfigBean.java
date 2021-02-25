/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.alfresco.config;

import java.io.File;

/**
 * Audit Configuration Bean Class
 *
 * <p>Enable audit logging output to a seperate log file. Requires the 'Audit' debug level to be enabled</p>
 *
 * @author gkspencer
 */
public class AuditConfigBean {

    // Path to the audit log file
    private String m_auditLogPath;

    // List of enabled audit groups
    private String m_auditGroups;

    /**
     * Return the audit log path
     *
     * @return String
     */
    public String getAuditLogPath() { return m_auditLogPath; }

    /**
     * Return the audit groups list
     *
     * @return String
     */
    public String getAuditGroups() { return m_auditGroups; }

    /**
     * Set the audit log path
     *
     * @param path String
     */
    public void setAuditLogPath(String path) {

        // Check if the path is valid
        if (path == null)
            return;

        // Set the audit log path
        m_auditLogPath = path;
    }

    /**
     * Set the enabled audit groups list
     *
     * @param auditGroups String
     */
    public void setAuditGroups(String auditGroups) {
        m_auditGroups = auditGroups;
    }
}
