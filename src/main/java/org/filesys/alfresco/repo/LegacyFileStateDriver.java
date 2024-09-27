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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;

import org.filesys.alfresco.base.AlfrescoNetworkFile;
import org.filesys.alfresco.base.ExtendedDiskInterface;
import org.filesys.alfresco.base.NetworkFileLegacyReferenceCount;
import org.filesys.alfresco.config.ServerConfigurationBean;
import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.db.DBDeviceContext;
import org.filesys.server.filesys.db.DBFileInfo;
import org.filesys.server.locking.FileLockingInterface;
import org.filesys.server.locking.LockManager;
import org.filesys.server.locking.OpLockInterface;
import org.filesys.server.locking.OpLockManager;
import org.filesys.smb.SharingMode;
import org.alfresco.model.ContentModel;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.smb.WinNT;
import org.filesys.util.FileDateTime;
import org.springframework.extensions.config.ConfigElement;

/**
 * The Legacy file state driver is used to update JFileServer's file state cache.
 * <p>
 * This class decorates an ExtendedDiskInterface with odds and ends to keep JFileServer happy.
 * <p>
 * In particular this implementation cannot contain any code that requires access to the 
 * alfresco repository.
 * 
 */
public class LegacyFileStateDriver implements ExtendedDiskInterface
{
    // File state attribute names
    private static final String AlfrescoAttrTempPath    = "AlfTempPath";

    private ExtendedDiskInterface diskInterface;
    
    private OpLockInterface opLockInterface;
    
    private FileLockingInterface fileLockingInterface; 
          
    public void init()
    {
        PropertyCheck.mandatory(this, "diskInterface", diskInterface);
        PropertyCheck.mandatory(this, "fileLockingInterface", fileLockingInterface);
        PropertyCheck.mandatory(this, "opLockInterface", getOpLockInterface());
    }
    
    private static final Log logger = LogFactory.getLog(LegacyFileStateDriver.class);

    @Override
    public void treeOpened(SrvSession sess, TreeConnection tree)
    {
        diskInterface.treeOpened(sess, tree);
        
    }

    @Override
    public void treeClosed(SrvSession sess, TreeConnection tree)
    {
        diskInterface.treeClosed(sess, tree);
    }

    @Override
    public NetworkFile createFile(SrvSession sess, TreeConnection tree,
                                  FileOpenParams params) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        
        FileStateCache cache = null;
        FileState fstate = null;
  
        FileAccessToken token = null;
        
        if(tctx.hasStateCache())
        {
            cache = tctx.getStateCache();
            fstate = tctx.getStateCache().findFileState( params.getPath(), true);
            token = cache.grantFileAccess(params, fstate, FileStatus.NotExist);
            if(logger.isDebugEnabled())
            {
                logger.debug("create file created lock token:" + token);
            }
        }
        
        try
        {
            NetworkFile newFile = diskInterface.createFile(sess, tree, params);
            
            int openCount = 1;
            
            if(newFile instanceof NetworkFileLegacyReferenceCount)
            {
                NetworkFileLegacyReferenceCount counter = (NetworkFileLegacyReferenceCount)newFile;
                openCount = counter.incrementLegacyOpenCount();
            }
            // This is the create so we store the first access token always
            newFile.setAccessToken(token);   
          
            if(tctx.hasStateCache())
            {
                // Indicate that the file is open
                fstate.setFileStatus(newFile.isDirectory()? FileStatus.DirectoryExists : FileStatus.FileExists);
         
                long allocationSize = params.getAllocationSize();
                if(allocationSize > 0)
                {
                    fstate.setAllocationSize(allocationSize);
                    fstate.setFileSize(allocationSize);
                }
                
                if (newFile instanceof NodeRefNetworkFile)
                {
                    NodeRefNetworkFile x = (NodeRefNetworkFile)newFile;
                    x.setFileState(fstate);
                }
                
                if (newFile instanceof TempNetworkFile)
                {
                    TempNetworkFile x = (TempNetworkFile)newFile;
                    x.setFileState(fstate);
                }

                // Update parent folder timestamps
                updateParentFolderTimestamps( params.getPath(), tctx);
            }
            
            if (newFile instanceof NodeRefNetworkFile)
            {
                NodeRefNetworkFile x = (NodeRefNetworkFile)newFile;
                x.setProcessId( params.getProcessId());
                x.setAccessToken(token);
            }
            
            if (newFile instanceof TempNetworkFile)
            {
                TempNetworkFile x = (TempNetworkFile)newFile;
                x.setAccessToken(token);
            }

            // Check if the delete-on-close create option is set
            if ( (params.getCreateOptions() & WinNT.CreateDeleteOnClose) != 0)
                newFile.setDeleteOnClose( true);

            return newFile;
            
        }
        catch(IOException ie)
        {
            if(logger.isDebugEnabled())
            {
                logger.debug("create file exception caught", ie);   
            }    
            if(tctx.hasStateCache() && token != null)
            {
                if(cache != null && fstate != null && token != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("create file release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            throw ie;
        }
        catch (RuntimeException re)
        {
        	// we could be out of memory or a NPE or some other unforseen situation.  JFileServer will complain loudly ... as it should.
            if(logger.isDebugEnabled())
            {
                logger.debug("create file exception caught", re);   
            }    
            if(tctx.hasStateCache() && token != null)
            {
                if(cache != null && fstate != null && token != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("create file release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            throw re;
        }
    }

    @Override
    public NetworkFile openFile(SrvSession sess, TreeConnection tree,
            FileOpenParams params) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        String path = params.getPath();
        
        boolean rollbackOpen = false;
        boolean rollbackToken = false;
        boolean rollbackCount = false;
        boolean rollbackSetToken = false;

        FileAccessToken token = null;
        
        FileStateCache cache = null;
        FileState fstate = null;
        NetworkFile openFile = null;

        if(tctx.hasStateCache())
        {
            cache = tctx.getStateCache();
            fstate = tctx.getStateCache().findFileState( params.getPath(), true);

            if(!params.isDirectory())
            {
                try
                {
                    token = cache.grantFileAccess(params, fstate, FileStatus.Unknown);
                }
                catch (IOException e)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("UNABLE to grant file access for path:" + path + ", params" + params, e);
                    }
                    throw e;
                }
                
                rollbackToken = true;
                if(logger.isDebugEnabled())
                {
                    logger.debug("open file created lock token:" + token + ", for path:" + path);
                }
            }
        }

        try
        {
            // Check if the path has a file state, and there is an associated temporary file path
            if ( fstate != null && !params.isAttributesOnlyAccess()) {
                String tempPath = (String) fstate.findAttribute( AlfrescoAttrTempPath);

                if ( tempPath != null) {

                    // Check if the temporary file still exists
                    File tFile = new File( tempPath);

                    if ( tFile.exists()) {

                        // File is already open and using a temporary path file
                        TempNetworkFile tempFile = new TempNetworkFile(new File(tempPath), path);
                        tempFile.setFileState(fstate);
                        tempFile.setAccessToken(token);

                        if (params.isReadOnlyAccess())
                            tempFile.setGrantedAccess(NetworkFile.Access.READ_ONLY);
                        else
                            tempFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

                        // Save the original access mask from the request
                        tempFile.setAccessMask(params.getAccessMode());

                        // Copy current file details
                        //
                        // Check if the cached file size is valid, if not then get the file details from the repository
                        if ( fstate.getFileSize() != -1L) {

                            // Use the cached file size
                            tempFile.setFileSize(fstate.getFileSize());
                        }
                        else {

                            // Get the file size from the repository
                            FileInfo fInfo = diskInterface.getFileInformation( sess, tree, params.getPath());
                            if ( fInfo != null)
                                tempFile.setFileSize(fInfo.getSize());
                        }

                        // Reset file timestamps if there is cached information
                        FileInfo fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);
                        if ( fInfo != null) {
                            tempFile.setCreationDate( fInfo.getCreationDateTime());
                            tempFile.setModifyDate( fInfo.getModifyDateTime());
                        }

                        // DEBUG
                        if (logger.isDebugEnabled())
                            logger.debug("Open file, re-use existing tempPath=" + tempPath + " for path " + path);

                        return tempFile;
                    }
                    else {

                        // Remove the file state attribute
                        fstate.removeAttribute( AlfrescoAttrTempPath);
                    }
                }
            }

            // Open the file
            openFile = diskInterface.openFile(sess, tree, params);
            rollbackOpen = true;

            if(openFile instanceof NetworkFileLegacyReferenceCount)
            {
                NetworkFileLegacyReferenceCount counter = (NetworkFileLegacyReferenceCount)openFile;
                int legacyOpenCount = counter.incrementLegacyOpenCount();
                if(logger.isDebugEnabled())
                {
                    logger.debug("openFile: legacyOpenCount: " + legacyOpenCount);
                }
                
                rollbackCount = true;
            }
            else
            {
                logger.debug("openFile does not implement NetworkFileLegacyReferenceCount");
            }
            
            if( openFile.hasAccessToken())
            {
                // already has an access token, release the second token
                if(cache != null && fstate != null && token != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("already has access token, release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            else
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("store access token on open network file object token:" + token);
                }
  
                // first access token
                openFile.setAccessToken(token);
                rollbackSetToken = true;
            }
            
            if(tctx.hasStateCache())
            {
                fstate = tctx.getStateCache().findFileState( path, true);

                // Check if the file state has a file size
                long fsSize = 0;
                if( fstate.getFileStatus() == FileStatus.FileExists)
                    fsSize = fstate.getFileSize();

                fstate.setFileSize( fsSize != 0 ? fsSize : openFile.getFileSize());

                if ( fstate.hasChangeDateTime() && fstate.getChangeDateTime() > openFile.getModifyDate()) {

                    // Use the cached date/time
                    openFile.setModifyDate( fstate.getChangeDateTime());

                    // DEBUG
                    if ( logger.isDebugEnabled())
                        logger.debug( "update open file change date/time from file state - " + FileDateTime.longToString( fstate.getChangeDateTime()));
                }
                else {

                    // Use the file date/time
                    fstate.updateChangeDateTime(openFile.getModifyDate());
                }

                if ( fstate.hasModifyDateTime() && fstate.getModifyDateTime() > openFile.getModifyDate()) {

                    // Use the cached date/time
                    openFile.setModifyDate( fstate.getModifyDateTime());

                    // DEBUG
                    if ( logger.isDebugEnabled())
                        logger.debug( "update open file modify date/time from file state - " + FileDateTime.longToString( fstate.getModifyDateTime()));
                }
                else {

                    // Use the file date/time
                    fstate.updateModifyDateTime(openFile.getModifyDate());
                }

                fstate.updateAccessDateTime();

                // Update cached file information
                FileInfo fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);

                if ( fInfo != null) {
                    fInfo.setFileSize( fsSize);

                    fInfo.setChangeDateTime( fstate.getChangeDateTime());
                    fInfo.setModifyDateTime( fstate.getModifyDateTime());
                    fInfo.setChangeDateTime( fstate.getChangeDateTime());
                    fInfo.setModifyDateTime( fstate.getModifyDateTime());
                    fInfo.setAccessDateTime( fstate.getAccessDateTime());
                }
            }
            
            if (openFile instanceof ContentNetworkFile)
            {
                ContentNetworkFile x = (ContentNetworkFile)openFile;
                x.setProcessId( params.getProcessId());

                if(fstate != null)
                {
                    x.setFileState(fstate);
                }
            } 
            else if (openFile instanceof TempNetworkFile)
            {
                TempNetworkFile x = (TempNetworkFile)openFile;
                if(fstate != null)
                {
                    x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.FileExists);

                    // Save the temporary file path
                    fstate.addAttribute( AlfrescoAttrTempPath, x.getFile().getPath());
                }
            }
            else if (openFile instanceof AlfrescoFolder)
            {
                AlfrescoFolder x = (AlfrescoFolder)openFile;
                if(fstate != null)
                {
                    x.setFileState(fstate);
                    fstate.setFileStatus(FileStatus.DirectoryExists);
                }
            }

            // Update the file status
            if(fstate != null)
            {
                fstate.setFileStatus( openFile.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
            }

            // Check if the delete-on-close create option is set
            if ( (params.getCreateOptions() & WinNT.CreateDeleteOnClose) != 0)
                openFile.setDeleteOnClose( true);

            rollbackToken = false;
            rollbackCount = false;
            rollbackSetToken = false;
            rollbackOpen = false;
            
            if(logger.isDebugEnabled())
            {
                logger.debug("successfully opened file:" + openFile);
            }

            // Mark the file/folder as open
            openFile.setClosed( false);

            return openFile;
        }
        finally
        {
            if(rollbackToken)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("rollback token:" + token);
                }
                if(cache != null && fstate != null && token != null)
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("open file release lock token:" + token);
                    }
                    cache.releaseFileAccess(fstate, token);
                }
            }
            if(rollbackCount)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("rollback legacy open count:" + token);
                }
                if(openFile instanceof NetworkFileLegacyReferenceCount)
                {
                    NetworkFileLegacyReferenceCount counter = (NetworkFileLegacyReferenceCount)openFile;
                    counter.decrementLegacyOpenCount();
                }
            }
            if(rollbackSetToken)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("rollback set access token:" + token);
                }
                openFile.setAccessToken(null);
            }
            if(rollbackOpen)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("rollback open:" + token);
                }
                diskInterface.closeFile(sess, tree, openFile);
            }
        }
    }
    
    @Override
    public void closeFile(SrvSession sess, TreeConnection tree,
            NetworkFile file) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        FileStateCache cache = null;
        FileState fstate = null;
   
        if(logger.isDebugEnabled())
        {
            logger.debug("closeFile:" + file.getFullName() + ", accessToken:" + file.getAccessToken());
        }

        // Check for a special client API file
        if ( file.isClientAPIFile()) {

            // Call the client API file close
            file.closeFile();

            if (logger.isDebugEnabled())
                logger.debug("  closed client API file");
            return;
        }

        int legacyOpenCount = 0;
        
        if(file instanceof NetworkFileLegacyReferenceCount)
        {
            NetworkFileLegacyReferenceCount counter = (NetworkFileLegacyReferenceCount)file;
            legacyOpenCount = counter.decrementLegacyOpenCount();
            if(logger.isDebugEnabled())
            {
                logger.debug("closeFile: legacyOpenCount=" + legacyOpenCount);
            }
        }
        else
        {
            logger.debug("file to close does not implement NetworkFileLegacyReferenceCount");
        }

        // Release the oplock
        if ( file.hasOpLock())
        {
            if ( logger.isDebugEnabled())
            {
               logger.debug("File Has OpLock - release oplock for closed file, file=" + file.getFullName());
            }

            // Release the oplock
            OpLockManager oplockMgr = opLockInterface.getOpLockManager(sess, tree);
            oplockMgr.releaseOpLock( file.getOpLock().getPath(), file.getOplockOwner());

            //  DEBUG

            if ( logger.isDebugEnabled())
            {
               logger.debug("Released oplock for closed file, file=" + file.getFullName());
            }
        }


        //  Release any locks on the file owned by this session

        if ( file.hasLocks())
        {
            if ( logger.isDebugEnabled())
            {
               logger.debug("Release all locks, file=" + file.getFullName());
            }

            LockManager lockMgr = fileLockingInterface.getLockManager(sess, tree);

            if(lockMgr != null)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("Releasing locks for closed file, file=" + file.getFullName() + ", locks=" + file.numberOfLocks());
                }

                //  Release all locks on the file owned by this session
                lockMgr.releaseLocksForFile(sess, tree, file);
            }
        }

        // Release any access token, will update the file state open count
        if(tctx.hasStateCache())
        {
            cache = tctx.getStateCache();
            fstate = cache.findFileState( file.getFullName(), true);

            if(cache != null && fstate != null && file.getAccessToken() != null)
            {
                FileAccessToken token = file.getAccessToken();
                if(logger.isDebugEnabled())
                {
                    logger.debug("close file, release access token:" + token);
                }
                cache.releaseFileAccess(fstate, token);
                file.setAccessToken( null);
            }

            if( fstate.getOpenCount() == 0)
            {
                if (logger.isDebugEnabled())
                    logger.debug("fstate OpenCount == 0, reset in-flight state");

                // Remove Alfresco specific attributes
                fstate.removeAttribute( AlfrescoAttrTempPath);

                // Make sure the oplock has been released
                if ( fstate.hasOpLock()) {

                    // Release the oplock
                    OpLockManager oplockMgr = opLockInterface.getOpLockManager(sess, tree);
                    oplockMgr.releaseOpLock( fstate.getOpLock().getPath(), file.getOplockOwner());
                }

                // Update the cached file size, if written to
                if ( file.getWriteCount() > 0) {

                    // Update the cached file size
                    fstate.setFileSize( file.getFileSize());
                    FileInfo fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);
                    if ( fInfo != null)
                        fInfo.setFileSize( file.getFileSize());
                }
            }

            // If the file has the delete on close flag set then mark the file as no longer existing
            if ( file.hasDeleteOnClose()) {

                // Mark the file as no longer existing, remove any cached attributes
                fstate.setFileStatus( FileStatus.NotExist);
                fstate.removeAllAttributes();

                if ( logger.isDebugEnabled())
                    logger.debug("Close on delete, mark file state as NonExist");
            }
        }

        // Close the file, via ContentDiskDriver2
        diskInterface.closeFile(sess, tree, file);
    }

    @Override
    public void registerContext(DeviceContext ctx) throws DeviceContextException
    {
        diskInterface.registerContext(ctx);
    }
    
    public void setDiskInterface(ExtendedDiskInterface diskInterface)
    {
        this.diskInterface = diskInterface;
    }

    public ExtendedDiskInterface getDiskInterface()
    {
        return diskInterface;
    }

    @Override
    public void createDirectory(SrvSession sess, TreeConnection tree,
            FileOpenParams params) throws IOException
    {
        diskInterface.createDirectory(sess, tree, params);

        // Update parent folder timestamps
        ContentContext tctx = (ContentContext) tree.getContext();

        if ( tctx.hasStateCache())
            updateParentFolderTimestamps(params.getPath(), tctx);
    }

    @Override
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws IOException
    {
        diskInterface.deleteDirectory(sess, tree, dir);

        // Update the cache details
        ContentContext tctx = (ContentContext) tree.getContext();
        if(tctx.hasStateCache())
        {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState( dir, false);

            if(fstate != null)
                fstate.setFileStatus(FileStatus.NotExist);

            // Update parent folder timestamps
            updateParentFolderTimestamps( dir, tctx);
        }
    }

    @Override
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();

        diskInterface.deleteFile(sess, tree, name);
        
        if(tctx.hasStateCache())
        {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState( name, false);
            
            if(fstate != null)
                fstate.setFileStatus(FileStatus.NotExist);

            // Update parent folder timestamps
            updateParentFolderTimestamps( name, tctx);
        }
    }

    @Override
    public FileStatus fileExists(SrvSession sess, TreeConnection tree, String name)
    {
        ContentContext tctx = (ContentContext) tree.getContext();

        if(tctx.hasStateCache()) {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState(name, false);

            if (fstate != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("file exists from file state for:" + name);
                }

                // Return the file status from the cached state
                if ( fstate.getFileStatus() != FileStatus.Unknown)
                    return fstate.getFileStatus();
            }
        }

        return diskInterface.fileExists(sess, tree, name);
    }

    @Override
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException
    {
        diskInterface.flushFile(sess, tree, file);        
    }

    @Override
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree,
            String name) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        FileStateCache cache = null;
        FileState fstate = null;

        if( tctx.hasStateCache()) {
            cache = tctx.getStateCache();
            fstate = cache.findFileState(name, false);
        }

        if( fstate != null) {

            if(logger.isDebugEnabled())
            {
                logger.debug("get file information from file state for:" + name);
            }

            // Check if the file/folder exists
            if ( fstate.getFileStatus() == FileStatus.NotExist) {
                return null;
            }
            else if ( fstate.getFileStatus() != FileStatus.Unknown) {

                // Check if the file state has cached file information
                FileInfo finfo = (FileInfo) fstate.findAttribute(FileState.FileInformation);

                if (finfo != null) {

                    // Update the file information with the latest file details
                    if ( fstate.getFileSize() != -1L)
                        finfo.setFileSize(fstate.getFileSize());
                    if ( fstate.getAllocationSize() != -1L)
                        finfo.setAllocationSize(fstate.getAllocationSize());

                    finfo.setAccessDateTime(fstate.getAccessDateTime());
                    finfo.setChangeDateTime(fstate.getChangeDateTime());
                    finfo.setModifyDateTime(fstate.getModifyDateTime());

                    // Make sure we do not return invalid size/allocation values
                    if ( finfo.getSize() == -1L)
                        finfo.setFileSize( 0);
                    if ( finfo.getAllocationSize() == -1L)
                        finfo.setAllocationSize( 0);

                    return finfo;
                }
            }
        }

        // Get the file information
        FileInfo finfo = diskInterface.getFileInformation(sess, tree, name);

        if ( finfo != null) {

            // Add to the existing file state if available
            if ( fstate != null) {
                fstate.addAttribute( FileState.FileInformation, finfo);
            }
            else {

                // Create a new file state
                fstate = cache.findFileState( name, true);

                // Set the file status, cache the file information
                if ( fstate != null) {
                    fstate.setFileStatus(finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
                    fstate.addAttribute(FileState.FileInformation, finfo);
                }
            }
        }

        // Return the file information, or null if the path does not exist
        return finfo;
    }

    @Override
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws IOException
    {
        return diskInterface.isReadOnly(sess, ctx);
    }

    @Override
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file,
            byte[] buf, int bufPos, int siz, long filePos) throws IOException
    {
        return diskInterface.readFile(sess, tree, file, buf, bufPos, siz, filePos);
    }

    @Override
    public void renameFile(SrvSession sess, TreeConnection tree,
            String oldName, String newName, NetworkFile netFile) throws IOException
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        
        diskInterface.renameFile(sess, tree, oldName, newName, netFile);
        
        if(tctx.hasStateCache())
        {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState( oldName, false);
            
            if(fstate != null)
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("rename file state from:" + oldName + ", to:" + newName);
                }
                cache.renameFileState(newName, fstate, fstate.isDirectory());
                fstate.removeAllAttributes();

                // Add a 'NotExists' file state for the original path
                cache.findFileState( oldName, true, FileStatus.NotExist);
            }
        }
        
    }

    @Override
    public long seekFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, long pos, int typ) throws IOException
    {
        return diskInterface.seekFile(sess, tree, file, pos, typ);
    }

    @Override
    public void setFileInformation(SrvSession sess, TreeConnection tree,
            String name, FileInfo info) throws IOException
    {

       diskInterface.setFileInformation(sess, tree, name, info);
        
       ContentContext tctx = (ContentContext) tree.getContext();
        
       if(tctx.hasStateCache())
       {
           FileStateCache cache = tctx.getStateCache();
           FileState fstate = cache.findFileState( name, true);
           FileInfo fInfo = null;

           if ( fstate != null)
               fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);
 
           if ( info.hasSetFlag(FileInfo.SetCreationDate))
           {
               if ( logger.isDebugEnabled())
               {
                   logger.debug("Set creation date in file state cache" + name + ", " + info.getCreationDateTime());
               }

               if ( fInfo != null)
                   fInfo.setCreationDateTime( info.getCreationDateTime());
           }

           // Update the modification timestamp
           if ( info.hasSetFlag(FileInfo.SetModifyDate)) 
           {   
               if ( logger.isDebugEnabled())
               {
                   logger.debug("Set modification date in file state cache" + name + ", " + info.getModifyDateTime());
               }

               fstate.updateModifyDateTime( info.getModifyDateTime());
               if ( fInfo != null)
                   fInfo.setModifyDateTime( info.getModifyDateTime());
           }

           // Update the change timestamp
           if ( info.hasSetFlag(FileInfo.SetChangeDate)) {
               if ( logger.isDebugEnabled())
               {
                   logger.debug("Set change date in file state cache" + name + ", " + info.getChangeDateTime());
               }

               fstate.updateChangeDateTime( info.getChangeDateTime());
               if ( fInfo != null)
                   fInfo.setChangeDateTime( info.getChangeDateTime());
           }
       }        
    }

    @Override
    public SearchContext startSearch(SrvSession sess, TreeConnection tree,
            String searchPath, int attrib, EnumSet<SearchFlags> flags) throws FileNotFoundException
    {
        InFlightCorrector t = new InFlightCorrectorImpl(tree);  
        
        SearchContext ctx = diskInterface.startSearch(sess, tree, searchPath, attrib, flags);
        
        if(ctx instanceof InFlightCorrectable)
        {
            InFlightCorrectable thingable = (InFlightCorrectable)ctx;
            thingable.setInFlightCorrector(t);
        }
             
        return ctx;

    }

    @Override
    public void truncateFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, long siz) throws IOException {
        diskInterface.truncateFile(sess, tree, file, siz);

        // Update the cached file size
        ContentContext tctx = (ContentContext) tree.getContext();

        if (tctx.hasStateCache()) {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState(file.getFullName(), false);

            if (fstate != null)
                fstate.setFileSize(siz);
        }
    }

    @Override
    public int writeFile(SrvSession sess, TreeConnection tree,
            NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws IOException
    {
        int wrLen = diskInterface.writeFile(sess, tree, file, buf, bufoff, siz, fileoff);

        // If data was written to the file then update the cached values
        if ( wrLen > 0) {
            ContentContext tctx = (ContentContext) tree.getContext();

            if ( tctx.hasStateCache()) {
                FileStateCache cache = tctx.getStateCache();
                FileState fstate = cache.findFileState(file.getFullName(), false);

                if (fstate != null) {

                    // Update the cached file size
                    long fsiz = file.getFileSize();
                    fstate.setFileSize(fsiz);

                    // Update cached file information
                    FileInfo fInfo = (FileInfo) fstate.findAttribute(FileState.FileInformation);

                    if (fInfo != null) {
                        fInfo.setFileSize(fsiz);
                        if (!fInfo.isArchived())
                            fInfo.setFileAttributes(fInfo.getFileAttributes() + FileAttribute.Archive);
                        fInfo.setAccessDateTime(System.currentTimeMillis());
                    }
                }
            }
        }

        // Return the length of data written
        return wrLen;
    }

    @Override
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException
    {
        
        return diskInterface.createContext(shareName, args);
    }

     
    public void setFileLockingInterface(FileLockingInterface fileLockingInterface)
    {
        this.fileLockingInterface = fileLockingInterface;
    }

    public FileLockingInterface getFileLockingInterface()
    {
        return fileLockingInterface;
    }
    
    public void setOpLockInterface(OpLockInterface opLockInterface)
    {
        this.opLockInterface = opLockInterface;
    }

    public OpLockInterface getOpLockInterface()
    {
        return opLockInterface;
    }

    /**
     * Update the parent folder path file information last write and change timestamps
     *
     * @param path String
     * @param ctx ContentContext
     */
    protected final void updateParentFolderTimestamps(String path, ContentContext ctx) {

        // Get the parent folder path
        String parentPath = FileName.removeFileName( path);

        // Get the file state for the parent folder, or create it
        if(ctx.hasStateCache()) {
            FileStateCache cache = ctx.getStateCache();
            FileState fstate = cache.findFileState(parentPath, true);

            if (fstate != null) {

                // Update the cached last write, access and change timestamps for the folder
                long updTime = System.currentTimeMillis();

                fstate.updateModifyDateTime(updTime);
                fstate.updateChangeDateTime(updTime);
                fstate.updateAccessDateTime();

                // Update the cached file information, if available
                FileInfo finfo = (FileInfo) fstate.findAttribute(FileState.FileInformation);
                if (finfo != null) {

                    // Update the file information timestamps
                    finfo.setModifyDateTime(updTime);
                    finfo.setChangeDateTime(updTime);
                    finfo.setAccessDateTime(updTime);
                }

                // DEBUG
                //            if ( hasDebug()) {
                //                Debug.println("$$$ Update parent folder path=" + parentPath + ", fstate=" + fstate);
                //                ctx.getStateCache().dumpCache( true);
                //            }
            }
        }
    }
}
  