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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;

import org.alfresco.service.cmr.repository.NodeRef;
import org.filesys.alfresco.base.NetworkFileLegacyReferenceCount;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.NetworkFileStateInterface;
import org.filesys.smb.server.disk.original.JavaNetworkFile;

/**
 * Temporary Java backed network file.
 * 
 * @author mrogers
 */
public class TempNetworkFile extends JavaNetworkFile
    implements NetworkFileStateInterface,
    NetworkFileLegacyReferenceCount
{
    private boolean changed = false;
    boolean modificationDateSetDirectly = false;
    private FileState fileState;
    private int legacyOpenCount = 0;

    /**
     * Create a new temporary file with no existing content.
     * 
     * @param file the underlying File
     * @param netPath where in the repo this file is going.
     */
    public TempNetworkFile(File file, String netPath)
    {
        super(file, netPath);
        setFullName(netPath);
        setAttributes(FileAttribute.NTNormal);
        setClosed(false);
    }
    
    /**
     * A new temporary network file with some existing content.
     * @param file File
     * @param netPath String
     * @param existingContent Reader
     */
    public TempNetworkFile(File file, String netPath, Reader existingContent)
    {
        super(file, netPath);
        setFullName(netPath);
        setAttributes(FileAttribute.NTNormal);
        setClosed(false);
    }
    
    /**
     * Access to the underlying file.
     *
     * @return the file.
     */
    public File getFile()
    {
        return m_file;
    } 

    @Override
    public void closeFile() throws IOException {

        super.closeFile();
    }

    @Override
    public int readFile(byte[] buf, int len, int pos, long fileOff)
    throws java.io.IOException
    {
        if(fileState != null)
        {
            fileState.updateAccessDateTime();
        }
        return super.readFile(buf, len, pos, fileOff);
    }
    
    @Override
    public void writeFile(byte[] buf, int len, int pos) throws IOException
    {
        setChanged(true);

        super.writeFile(buf, len, pos);
        
        long size = m_io.length();
        setFileSize(size);

        if(fileState != null)
        {
            // Update cached values
            updateTimestampsAndSize( fileState, size);

            // File is being shared so flush updates
            if ( fileState.getOpenCount() > 1)
                m_io.getFD().sync();
        }
    }
    
    @Override
    public void writeFile(byte[] buffer, int length, int position, long fileOffset)
    throws IOException
    {
        setChanged(true);
        
        super.writeFile(buffer, length, position, fileOffset);
        
        long size = m_io.length();
        setFileSize(size);

        if(fileState != null)
        {
            // Update cached values
            updateTimestampsAndSize( fileState, size);

            // File is being shared so flush updates
            if ( fileState.getOpenCount() > 1)
                m_io.getFD().sync();
        }
    }
    
    @Override
    public void truncateFile(long size) throws IOException
    {
        super.truncateFile(size);
        
        if(size == 0)
        {
            setChanged(true);
        }
        
        setFileSize(size);
        if(fileState != null)
        {
            // Update cached values
            updateTimestampsAndSize( fileState, size);

            // File is being shared so flush updates
            if ( fileState.getOpenCount() > 1)
                m_io.getFD().sync();
        }
    }

    /**
     * Set the associated file state
     *
     * @param fileState FileState
     */
    public void setFileState(FileState fileState)
    {
        this.fileState = fileState;
    }

    @Override
    public FileState getFileState()
    {
        return fileState;
    }
    
    /**
     * Tell JFileServer it needs to call disk.closeFile rather than short cutting.
     *
     * @return boolean
     */
    public boolean allowsOpenCloseViaNetworkFile() {
        return false;
    }

    /**
     * Indicate the file has been changed
     *
     * @param changed boolean
     */
    public void setChanged(boolean changed)
    {
        this.changed = changed;

        if ( changed && fileState != null)
            fileState.setDataStatus(FileState.DataStatus.Updated);
    }

    /**
     * Check if the file has been changed
     *
     * @return boolean
     */
    public boolean isChanged()
    {
        if ( fileState != null)
            return fileState.getDataStatus() == FileState.DataStatus.Updated;
        return changed;
    }

    /**
     * Check if the modification date is set directly
     *
     * @return boolean
     */
    public boolean isModificationDateSetDirectly()
    {
        return modificationDateSetDirectly;
    }

    /**
     * Set if the modification date is set directly
     *
     * @param modificationDateSetDirectly boolean
     */
    public void setModificationDateSetDirectly(boolean  modificationDateSetDirectly)
    {
        this.modificationDateSetDirectly =  modificationDateSetDirectly;
    }

    /**
     * Increment the legacy file open count
     * 
     * @return int
     */
    public synchronized final int incrementLegacyOpenCount() {
        legacyOpenCount++;
        return legacyOpenCount;
    }
    
    /**
     * Decrement the legacy file open count
     * 
     * @return int
     */
    public synchronized final int decrementLegacyOpenCount() {
        legacyOpenCount--;
        return legacyOpenCount;
    }
    
    /**
     * Return the legacy open file count
     * 
     * @return int
     */
    public final int getLegacyOpenCount() {
        return legacyOpenCount;
    }

    /**
     * Update the cached timestamps and file size
     *
     * @param fstate FileState
     * @param fsize long
     */
    protected final void updateTimestampsAndSize(FileState fstate, long fsize) {

        if(fstate != null)
        {
            fstate.updateModifyDateTime();
            fstate.updateAccessDateTime();
            fstate.setFileSize(fsize);
            fstate.setAllocationSize((fsize + 512L) & 0xFFFFFFFFFFFFFE00L);

            // Update cached file information, if available
            FileInfo fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);

            if ( fInfo != null) {
                fInfo.setFileSize( fsize);

                fInfo.setModifyDateTime( fstate.getModifyDateTime());
                fInfo.setAccessDateTime( fstate.getAccessDateTime());
            }
        }
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();

        str.append( "[");
        str.append(getFullName());
        str.append(",token=");
        str.append( getAccessToken());

        str.append(",");
        str.append( getGrantedAccess().name());

        str.append(",temp=");
        str.append( m_file.getAbsolutePath());

        if ( hasLocks()) {
            str.append(",locks=");
            str.append( numberOfLocks());
        }

        if ( hasOpLock()) {
            str.append(",oplock=");
            str.append( getOpLock());
        }

        if ( getWriteCount() > 0)
            str.append( ",Modified");
        if ( isClosed())
            str.append(",Closed");
        str.append(",fstate=");
        str.append( getFileState());

        str.append( "]");

        return str.toString();
    }
}
