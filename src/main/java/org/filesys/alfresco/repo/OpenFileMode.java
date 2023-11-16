/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.filesys.alfresco.repo;

import org.filesys.server.filesys.FileOpenParams;

public enum OpenFileMode
{
    READ_ONLY,
    WRITE_ONLY,
    READ_WRITE,
    DELETE,
    ATTRIBUTES_ONLY;

    /**
     * Get the open file mode from the file open parameters
     *
     * @param params FileOpenParams
     * @return OpenFileMode
     */
    public static OpenFileMode getOpenMode( FileOpenParams params) {

        OpenFileMode openMode = READ_ONLY;

        if(params.isAttributesOnlyAccess())
        {
            openMode = OpenFileMode.ATTRIBUTES_ONLY;
        }
        else if (params.isReadWriteAccess())
        {
            openMode = OpenFileMode.READ_WRITE;
        }
        else if (params.isWriteOnlyAccess())
        {
            openMode = OpenFileMode.WRITE_ONLY;
        }
        else if (params.isReadOnlyAccess())
        {
            openMode = OpenFileMode.READ_ONLY;
        }
        else if(params.isDeleteOnClose())
        {
            openMode = OpenFileMode.DELETE;
        }

        return openMode;
    }
}
