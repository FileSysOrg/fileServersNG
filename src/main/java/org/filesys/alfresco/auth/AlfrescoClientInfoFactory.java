/*
 * Copyright (C) 2018-2019 GK Spencer
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

package org.filesys.alfresco.auth;

import org.filesys.alfresco.base.AlfrescoClientInfo;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.auth.ClientInfoFactory;

/**
 * Alfresco Client Information Factory Class
 *
 * <p>Generate Alfresco specific client information objects</p>
 *
 * @author gkspencer
 */
public class AlfrescoClientInfoFactory implements ClientInfoFactory {

    /**
     * Create an Alfresco client information object
     *
     * @param userName String
     * @param pwd byte[]
     * @return ClientInfo
     */
    @Override
    public ClientInfo createInfo(String userName, byte[] pwd) {
        return new AlfrescoClientInfo(userName, pwd);
    }
}
