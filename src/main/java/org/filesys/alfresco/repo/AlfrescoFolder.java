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

import java.io.IOException;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.NetworkFileStateInterface;

/**
 * Object returned to JFileServer if the repository object is a folder.
 */
public class AlfrescoFolder extends NetworkFile implements NetworkFileStateInterface
{
    private FileState fileState;

    public AlfrescoFolder(String path, FileInfo fileInfo, boolean readOnly, long reqId)
    {
        super(path);
        setFullName(path);
        
        // Set the file timestamps
        
        if ( fileInfo.hasCreationDateTime())
            setCreationDate( fileInfo.getCreationDateTime());
        
        if ( fileInfo.hasModifyDateTime())
            setModifyDate(fileInfo.getModifyDateTime());
        
        if ( fileInfo.hasAccessDateTime())
            setAccessDate(fileInfo.getAccessDateTime());
        
        // Set the file attributes
        setAttributes(fileInfo.getFileAttributes());

        // Set the unique identifier
        setUniqueId( reqId);

        // Mark as open
        setClosed( false);
    }

    @Override
    public void openFile(boolean createFlag) throws IOException
    {
        throw new AlfrescoRuntimeException("Unable to open channel for a directory network file: " + this);        
    }

    @Override
    public int readFile(byte[] buf, int len, int pos, long fileOff)
            throws IOException
    {
        throw new AlfrescoRuntimeException("Unable to open channel for a directory network file: " + this);
    }

    @Override
    public void writeFile(byte[] buf, int len, int pos, long fileOff)
            throws IOException
    {
        throw new AlfrescoRuntimeException("Unable to open channel for a directory network file: " + this);        
    }

    @Override
    public long seekFile(long pos, int typ) throws IOException
    {
        return 0;
    }

    @Override
    public void flushFile() throws IOException
    {
        // Do nothing.
    }

    @Override
    public void truncateFile(long siz) throws IOException
    {
        throw new AlfrescoRuntimeException("Unable to open channel for a directory network file: " + this);
    }

    @Override
    public void closeFile() throws IOException
    {
        setClosed(true);
    }
    
    // For JFileServer file state lock manager
    public void setFileState(FileState fileState)
    {
        this.fileState = fileState;
    }

    @Override
    public FileState getFileState()
    {
        return fileState;

    }
}
