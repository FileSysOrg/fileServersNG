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

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;

/**
 * The in flight corrector corrects search results that have not yet been committed to the
 * repository.
 * 
 * It substitutes the "in flight" valuses from the state cache in place of the values committed to 
 * the repo
 * 
 * @author mrogers
 */
public class InFlightCorrectorImpl implements InFlightCorrector
{
    TreeConnection tree;
    
    private static final Log logger = LogFactory.getLog(InFlightCorrectorImpl.class);
    
    public InFlightCorrectorImpl(TreeConnection tree)
    {
        this.tree = tree;
    }
    public void correct(FileInfo info, String folderPath)
    {
        ContentContext tctx = (ContentContext) tree.getContext();
        
        String path = folderPath + info.getFileName();
        
        if(tctx.hasStateCache())
        {
            FileStateCache cache = tctx.getStateCache();
            FileState fstate = cache.findFileState( path, true);

            // Save the original and cached file size
            long infSize = info.getSize();
            long fsSize = fstate.getFileSize();

            if(fstate != null && fstate.getFileStatus() != FileStatus.NotExist)
            {
                if ( logger.isDebugEnabled())
                    logger.debug("correct " + path);

                // Check if the file state has cached file information, copy to the info object
                FileInfo fInfo = (FileInfo) fstate.findAttribute( FileState.FileInformation);
                if ( fInfo != null) {

                    // Copy the details from the cached file information
                    info.copyFrom( fInfo);

                    // Make sure the file size is valid
                    if ( info.getSize() == -1L) {
                        if ( fsSize != -1)
                            info.setFileSize( fsSize);
                        else
                            info.setFileSize( infSize);
                    }

                    // DEBUG
                    if ( logger.isDebugEnabled())
                        logger.debug("copy from cached file information " + fInfo);

                    return;
                }

                /*
                 * What about stale file state values here?
                 */
                if(fstate.hasFileSize())
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("replace file size " + info.getSize() + " with " + fstate.getFileSize());
                    }

                    // Make sure the file size is valid
                    if ( fsSize != -1L)
                        info.setFileSize(fstate.getFileSize());
                }
                if ( fstate.hasAccessDateTime())
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("replace access date " + new Date(info.getAccessDateTime()) + " with " + new Date(fstate.getAccessDateTime()));
                    }
                    info.setAccessDateTime(fstate.getAccessDateTime());
                }
                if ( fstate.hasChangeDateTime())
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("replace change date " + new Date(info.getChangeDateTime()) + " with " + new Date(fstate.getChangeDateTime()));
                    }
                    info.setChangeDateTime(fstate.getChangeDateTime());
                }
                if ( fstate.hasModifyDateTime())
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("replace modified date " + new Date(info.getModifyDateTime()) + " with " + new Date(fstate.getModifyDateTime()));
                    }
                    info.setModifyDateTime(fstate.getModifyDateTime());
                }
                if ( fstate.hasAllocationSize())
                {
                    if(logger.isDebugEnabled())
                    {
                        logger.debug("replace allocation size" + info.getAllocationSize() + " with " + fstate.getAllocationSize());
                    }
                    info.setAllocationSize(fstate.getAllocationSize());
                }
            }           
        }
    }

}
