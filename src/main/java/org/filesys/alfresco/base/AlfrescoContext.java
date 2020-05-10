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
package org.filesys.alfresco.base;

import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.alfresco.config.GlobalDesktopActionConfigBean;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.DiskSharedDevice;
import org.filesys.server.filesys.FileSystem;
import org.filesys.server.filesys.SrvDiskInfo;
import org.filesys.server.filesys.pseudo.PseudoFileInterface;
import org.filesys.server.locking.LockManager;
import org.filesys.server.locking.OpLockManager;
import org.alfresco.repo.admin.SysAdminParams;

/**
 * Alfresco Filesystem Context Class
 * 
 * <p>Contains per filesystem context.
 * 
 * @author GKSpencer
 */
public abstract class AlfrescoContext extends DiskDeviceContext
{
    private SysAdminParams sysAdminParams;
    
    private boolean pseudoFilesEnabled = false;
    private boolean isAlfrescoURLEnabled = false;
    private boolean isShareURLEnabled = false;

	// Debug levels
	
	public final static int DBG_FILE		= 0x00000001;	// file/folder create/delete
	public final static int DBG_FILEIO		= 0x00000002;	// file read/write/truncate
	public final static int DBG_SEARCH  	= 0x00000004;	// folder search
	public final static int DBG_INFO        = 0x00000008;	// file/folder information
	public final static int DBG_LOCK        = 0x00000010;	// file byte range locking
	public final static int DBG_PSEUDO      = 0x00000020;	// pseudo files/folders
	public final static int DBG_RENAME      = 0x00000040;	// rename file/folder
	
	// Filesystem debug flag strings
	  
	private static final String m_filesysDebugStr[] = { "FILE", "FILEIO", "SEARCH", "INFO", "LOCK", "PSEUDO", "RENAME" };

    // URL pseudo file web path prefix (server/port/webapp) and link file name
    
    private String m_urlFileName;
    private String m_shareUrlFileName;
    
    // Pseudo file interface
    
    private PseudoFileInterface m_pseudoFileInterface;

    // Desktop actions
    
    private GlobalDesktopActionConfigBean m_globalDesktopActionConfig = new GlobalDesktopActionConfigBean();
    private DesktopActionTable m_desktopActions;
    private List<DesktopAction> m_desktopActionsToInitialize;
    
    // Debug flags
    //
    // Requires the logger to be enabled for debug output
    
    public int m_debug;
    
    public AlfrescoContext()
    {
        // Default the filesystem to look like an 80Gb sized disk with 90% free space
        
        setDiskInformation(new SrvDiskInfo(2560000, 64, 512, 2304000));
        
        // Set parameters
        
        setFilesystemAttributes(FileSystem.CasePreservedNames + FileSystem.UnicodeOnDisk +
                FileSystem.CaseSensitiveSearch);        
    }
    
    public void setSysAdminParams(SysAdminParams sysAdminParams)
    {
        this.sysAdminParams = sysAdminParams;
    }
    
    public SysAdminParams getSysAdminParams()
    {
        return sysAdminParams;
    }

    public void setDisableChangeNotification(boolean disableChangeNotification)
    {
        enableChangeHandler(!disableChangeNotification);
    }
    
    /**
     * Complete initialization by registering with a disk driver
     */
    public void initialize(AlfrescoDiskDriver filesysDriver)
    {
        if (m_desktopActionsToInitialize != null)
        {
            for (DesktopAction desktopAction : m_desktopActionsToInitialize)
            {
                // Initialize the desktop action
                try
                {
                    desktopAction.initializeAction(filesysDriver.getServiceRegistry(), this);
                }
                catch (DesktopActionException ex)
                {
                    throw new AlfrescoRuntimeException("Failed to initialize desktop action", ex);
                }
                addDesktopAction(desktopAction);
            }
        }
    }

    /**
     * Return the filesystem type, either FileSystem.TypeFAT or FileSystem.TypeNTFS.
     * 
     * @return String
     */
    public String getFilesystemType()
    {
        return FileSystem.TypeNTFS;
    }

    /**
     * Determine if the pseudo file interface is enabled
     * 
     * @return boolean
     */
    public final boolean hasPseudoFileInterface()
    {
    	return m_pseudoFileInterface != null ? true : false;
    }
    
    /**
     * Return the pseudo file interface
     * 
     * @return PseudoFileInterface
     */
    public final PseudoFileInterface getPseudoFileInterface()
    {
    	return m_pseudoFileInterface;
    }

    /**
     * Enable the pseudo file interface for this filesystem
     */
    private final void enabledPseudoFileInterface()
    {
    	if ( m_pseudoFileInterface == null)
    		m_pseudoFileInterface = new PseudoFileImpl();
    }
    
    /**
     * Determine if there are desktop actins configured
     * 
     * @return boolean
     */
    public final boolean hasDesktopActions()
    {
    	return m_desktopActions != null ? true : false;
    }
    
    /**
     * Return the desktop actions table
     * 
     * @return DesktopActionTable
     */
    public final DesktopActionTable getDesktopActions()
    {
    	return m_desktopActions;
    }
    
    /**
     * Return the count of desktop actions
     * 
     * @return int
     */
    public final int numberOfDesktopActions()
    {
    	return m_desktopActions != null ? m_desktopActions.numberOfActions() : 0;
    }

    /**
     * Add a desktop action
     * 
     * @param action DesktopAction
     * @return boolean
     */
    public final boolean addDesktopAction(DesktopAction action)
    {
    	// Check if the desktop actions table has been created
    	
    	if ( m_desktopActions == null)
    	{
    		m_desktopActions = new DesktopActionTable();
    		
    		// Enable pseudo files
    		
    		enabledPseudoFileInterface();
    	}
    	
    	// Add the action
    	
    	return m_desktopActions.addAction(action);
    }

    
//    /**
//     * Determine if the URL pseudo file is enabled
//     * 
//     * @return boolean
//     */
//    public final boolean hasURLFile()
//    {
//        if (m_urlFileName != null)
//            return true;
//        return false;
//    }
//     
    /**
     * Return the URL pseudo file path prefix
     * @deprecated - does not know about share
     * 
     * @return String
     */
    public final String getURLPrefix()
    {
        return sysAdminParams.getAlfrescoProtocol() + "://" + sysAdminParams.getAlfrescoHost() + ":" + sysAdminParams.getAlfrescoPort() + "/" + sysAdminParams.getAlfrescoContext() + "/";
    }
    
    public final String getShareUrlPrefix()
    {
    	return sysAdminParams.getShareProtocol() + "://" + sysAdminParams.getShareHost() + ":" + sysAdminParams.getSharePort() + "/" + sysAdminParams.getShareContext() + "/";
    }

    /**
     * Return the URL pseudo file name
     * 
     * @return String
     */
    public final String getURLFileName()
    {
        return m_urlFileName;
    }
    
    /**
     * Return the Share URL pseudo file name
     * 
     * @return String
     */
    public final String getShareURLFileName()
    {
        return m_shareUrlFileName;
    }

    
    /**
     * Set the URL pseudo file name
     * 
     * @param urlFileName String
     */
    public final void setURLFileName(String urlFileName)
    {
        m_urlFileName = urlFileName;

        // URL file name must end with .url
        if (urlFileName != null)
        {
            if (!urlFileName.endsWith(".url"))
                throw new AlfrescoRuntimeException("URL link file must end with .url, " + urlFileName);

            enabledPseudoFileInterface();
        }
    }
    
    /**
     * Set the Share URL pseudo file name
     * 
     * @param urlFileName String
     */
    public final void setShareURLFileName(String urlFileName)
    {
        m_shareUrlFileName = urlFileName;

        // URL file name must end with .url
        if (urlFileName != null)
        {
            if (!urlFileName.endsWith(".url"))
                throw new AlfrescoRuntimeException("URL Share link file must end with .url, " + urlFileName);

            enabledPseudoFileInterface();
        }
    }

    /**
     * Set the desktop actions
     * 
     * @param desktopActions DesktopActionTable
     * @param filesysDriver DiskInterface
     */
    public final void setDesktopActions(DesktopActionTable desktopActions, DiskInterface filesysDriver)
    {
    	// Enumerate the desktop actions and add to this filesystem
    	
    	Enumeration<String> names = desktopActions.enumerateActionNames();
    	
    	while ( names.hasMoreElements())
    	{
    		addDesktopAction( desktopActions.getAction(names.nextElement()));
    	}
    	
//    	// If there are desktop actions then create the custom I/O control handler
//    	
//    	if ( numberOfDesktopActions() > 0)
//    	{
//    		// Create the custom I/O control handler
//    	
//    		m_ioHandler = createIOHandler( filesysDriver);
//    		if ( m_ioHandler != null)
//    			m_ioHandler.initialize(( AlfrescoDiskDriver) filesysDriver, this);
//    	}
    }
    

    /**
     * Set the desktop actions
     * 
     * @param desktopActions DesktopAction List
     */
    public final void setDesktopActionList(List<DesktopAction> desktopActions)
    {
        m_desktopActionsToInitialize = desktopActions;
    }

    public void setGlobalDesktopActionConfig(GlobalDesktopActionConfigBean desktopActionConfig)
    {
        m_globalDesktopActionConfig = desktopActionConfig;
    }


    protected GlobalDesktopActionConfigBean getGlobalDesktopActionConfig()
    {
        return m_globalDesktopActionConfig;
    }
    
    
    /**
     * Set the debug flags, also requires the logger to be enabled for debug output
     * 
     * @param flagsStr String
     */
    public final void setDebug(String flagsStr)
    {
    	int filesysDbg = 0;
    	
    	if (flagsStr != null)
        {
	        // Parse the flags
	  
	        StringTokenizer token = new StringTokenizer(flagsStr.toUpperCase(), ",");
	  
	        while (token.hasMoreTokens())
	        {
	        	// Get the current debug flag token
  
                String dbg = token.nextToken().trim();
  
                // Find the debug flag name
  
                int idx = 0;
                boolean match = false;
  
                while (idx < m_filesysDebugStr.length && match == false)
                {
                	if ( m_filesysDebugStr[idx].equalsIgnoreCase(dbg) == true)
                		match = true;
                	else
                		idx++;
                }
  
                if (match == false)
                    throw new AlfrescoRuntimeException("Invalid filesystem debug flag, " + dbg);
  
                // Set the debug flag
  
                filesysDbg += 1 << idx;
            }
	        
	        // Set the debug flags
	        
	        m_debug = filesysDbg;
        }
    }
    
    /**
     * Check if a debug flag is enabled
     * 
     * @param flg int
     * @return boolean
     */
    public final boolean hasDebug(int flg)
    {
    	return (m_debug & flg) != 0 ? true : false;
    }
    
    /**
     * Start the filesystem
     * 
     * @param share DiskSharedDevice
     * @exception DeviceContextException
     */
    public void startFilesystem(DiskSharedDevice share)
        throws DeviceContextException {
        
      

        // Call the base class
        
        super.startFilesystem(share);
    }

    LockManager lockManager;

    public void setLockManager(LockManager lockManager)
    {
        this.lockManager = lockManager;
    }

    /**
     * Return the lock manager, if enabled
     *
     * @return LockManager
     */
    public LockManager getLockManager() {
        return lockManager;
    }

    OpLockManager opLockManager;
    
    /**
     * Return the oplock manager, if enabled
     * 
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager() 
    {
        return opLockManager;
    }
    
    public void setOpLockManager(OpLockManager opLockManager) 
    {
        this.opLockManager = opLockManager;
    }

    public void setPseudoFilesEnabled(boolean enablePseudoFiles)
    {
        this.pseudoFilesEnabled = enablePseudoFiles;
    }

    public boolean isPseudoFilesEnabled()
    {
        return pseudoFilesEnabled;
    }

    public void setAlfrescoURLEnabled(boolean isAlfrescoURLEnabled)
    {
        this.isAlfrescoURLEnabled = isAlfrescoURLEnabled;
    }

    public boolean isAlfrescoURLEnabled()
    {
        return isAlfrescoURLEnabled;
    }

    public void setShareURLEnabled(boolean isShareURLEnabled)
    {
        this.isShareURLEnabled = isShareURLEnabled;
    }

    public boolean isShareURLEnabled()
    {
        return isShareURLEnabled;
    }

}
