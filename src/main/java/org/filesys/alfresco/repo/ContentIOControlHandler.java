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

import java.io.FileNotFoundException;

import org.filesys.alfresco.base.AlfrescoClientInfo;
import org.filesys.alfresco.base.AlfrescoContext;
import org.filesys.alfresco.base.DesktopAction;
import org.filesys.alfresco.base.DesktopActionTable;
import org.filesys.alfresco.base.DesktopParams;
import org.filesys.alfresco.base.DesktopResponse;
import org.filesys.alfresco.base.DesktopTarget;
import org.filesys.alfresco.base.IOControl;
import org.filesys.alfresco.base.IOControlHandler;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.IOControlNotImplementedException;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TransactionalFilesystemInterface;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.nt.NTIOCtl;
import org.filesys.util.DataBuffer;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Content Disk Driver I/O Control Handler Class
 *
 * <p>Provides the custom I/O control code handling used by the SMB client interface application.
 * 
 * @author gkspencer
 */
public class ContentIOControlHandler implements IOControlHandler
{
    // Logging
    private static final Log logger = LogFactory.getLog(ContentIOControlHandler.class);
    
    /**
     * Default constructor
     */
    public ContentIOControlHandler()
    {
    }
    
    private SMBHelper smbHelper;
    private NodeService nodeService;
    private AuthenticationService authService;
    private CheckOutCheckInService checkOutCheckInService;
    private TransactionService transactionService;
    
    
    public void init()
    {
        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "smbHelper", smbHelper);
        PropertyCheck.mandatory(this, "authService", authService);
        PropertyCheck.mandatory(this, "checkOutCheckInService", authService);
        PropertyCheck.mandatory(this, "transactionService", getTransactionService());
    }
        
    public final void setNodeService( NodeService nodeService)
    {
        this.nodeService = nodeService;
    }
    
    /**
     * Return the node service
     * 
     * @return NodeService
     */
    public final NodeService getNodeService()
    {
    	return nodeService;
    }
    
    
    /**
     * Process a filesystem I/O control request
     * 
     * @param sess Server session
     * @param tree Tree connection.
     * @param ctrlCode I/O control code
     * @param fid File id
     * @param dataBuf I/O control specific input data
     * @param isFSCtrl true if this is a filesystem control, or false for a device control
     * @param filter if bit0 is set indicates that the control applies to the share root handle
     * @return DataBuffer or null if there is no response buffer.
     * 
     * @exception IOControlNotImplementedException
     * @exception SMBException
     */
    public org.filesys.util.DataBuffer processIOControl(SrvSession sess, TreeConnection tree, int ctrlCode, int fid, DataBuffer dataBuf,
                                                        boolean isFSCtrl, int filter, Object contentDriver, ContentContext contentContext)
        throws IOControlNotImplementedException, SMBException
    {
        // Validate the file id
        NetworkFile netFile = tree.findFile(fid);
        if ( netFile == null )
        {
            logger.debug("IO Control Handler called with missing file");
            throw new SMBException(SMBStatus.NTErr, SMBStatus.NTInvalidParameter);
        }
        
        // Split the control code
        
        int devType = NTIOCtl.getDeviceType(ctrlCode);
        int ioFunc  = NTIOCtl.getFunctionCode(ctrlCode);
        
        // Check for I/O controls that require a success status
        
        if ( devType == NTIOCtl.DeviceFileSystem)
        {
	        // I/O control requests that require a success status
	        //
	        // Create or get object id
	        	
	        if ( ioFunc == NTIOCtl.FsCtlCreateOrGetObjectId)
	        {
	            logger.debug("Create or Get Object Id - return null");
	            return null;
//	            
//	            logger.debug("Create or Get Object Id - throw not implemented exception");
//	            throw new IOControlNotImplementedException("Create or Get Object Id not implemented");
//	        	//return null;
	        }
        }
        
        // Check if the I/O control looks like a custom I/O control request
        
        if ( devType != NTIOCtl.DeviceFileSystem || dataBuf == null)
        {
            throw new IOControlNotImplementedException("Custom IO control request not implemented");
        }
        
        // Check if the request has a valid signature for an Alfresco SMB server I/O control
        
        if ( dataBuf.getLength() < IOControl.Signature.length())
        {
            throw new IOControlNotImplementedException("Bad request length");
        }
        
        String sig = dataBuf.getFixedString(IOControl.Signature.length(), false);
        
        if ( sig == null || sig.compareTo(IOControl.Signature) != 0)
        {
            throw new IOControlNotImplementedException("Bad request signature");
        }
        
        // Get the node for the parent folder, make sure it is a folder
        
        NodeRef folderNode = null;
        
        try
        {
            folderNode = getNodeForPath(tree, netFile.getFullName());
            
            if ( getSMBHelper().isDirectory( folderNode) == false)
            {
                folderNode = null;
            }
        }
        catch ( FileNotFoundException ex)
        {
            folderNode = null;
        }

        // If the folder node is not valid return an error
        
        if ( folderNode == null)
        {
            logger.debug("unable to get parent folder - return access denied");
            throw new SMBException(SMBStatus.NTErr, SMBStatus.NTAccessDenied);
        }
        
        // Debug
        
        if ( logger.isDebugEnabled()) 
        {
            logger.debug("IO control func=0x" + Integer.toHexString(ioFunc) + ", fid=" + fid + ", buffer=" + dataBuf + 
               "  Folder nodeRef=" + folderNode);
        }

        // Check if the I/O control code is one of our custom codes

        DataBuffer retBuffer = null;
        
        switch ( ioFunc)
        {
	        // Probe to check if this is an Alfresco SMB server
	        
	        case IOControl.CmdProbe:
	            
	            if(logger.isDebugEnabled())
	            {
	                logger.debug("CmdProbe");
	            }
	            
	            // Return a buffer with the signature and protocol version
	            
	            retBuffer = new DataBuffer(IOControl.Signature.length());
	            retBuffer.putFixedString(IOControl.Signature, IOControl.Signature.length());
	            retBuffer.putInt(DesktopAction.StsSuccess);
	            retBuffer.putInt(IOControl.Version);
	            break;
	            
	        // Get file information for a file within the current folder
	            
	        case IOControl.CmdFileStatus:
	            
	            if(logger.isDebugEnabled())
	            {
	               logger.debug("CmdFileStatus");
	            }
	            // Process the file status request
	            
	            retBuffer = procIOFileStatus( sess, tree, dataBuf, folderNode, contentDriver, contentContext);
	            break;
	
	        // Get action information for the specified executable path
	            
	        case IOControl.CmdGetActionInfo:
	        	
	        	// Process the get action information request
	            if(logger.isDebugEnabled())
	            {
	                logger.debug("GetActionInfo");
	            }
	        	
	        	retBuffer = procGetActionInfo(sess, tree, dataBuf, folderNode, netFile, contentDriver, contentContext);
	        	break;
	        	
	        // Run the named action
	        	
	        case IOControl.CmdRunAction:
	
	        	// Process the run action request
	            if(logger.isDebugEnabled())
	            {
	                logger.debug("RunAction");
	            }
	        	
	        	retBuffer = procRunActionInTransaction(sess, tree, dataBuf, folderNode, netFile, contentDriver, contentContext);
	        	break;

	        // Return the authentication ticket
	        	
	        case IOControl.CmdGetAuthTicket:
	        	
	        	// Process the get auth ticket request
	            if(logger.isDebugEnabled())
	            {
	                logger.debug("GetAuthTicket");
	            }
	        	
	        	retBuffer = procGetAuthTicket(sess, tree, dataBuf, folderNode, netFile, contentDriver, contentContext);
	        	break;
	        	
	        // Unknown I/O control code
	            
	        default:
	            if(logger.isDebugEnabled())
	            {
	                logger.debug("throwing IOControl not implemented exception :iofunc" + Integer.toHexString(ioFunc));
	            }
	            throw new IOControlNotImplementedException("IO func not implemented" + Integer.toHexString(ioFunc));
        }
        
        // Return the reply buffer, may be null
        
        return retBuffer;
    }
    
    /**
     * Process the file status I/O request
     * 
     * @param sess Server session
     * @param tree Tree connection
     * @param reqBuf Request buffer
     * @param folderNode NodeRef of parent folder
     * @return DataBuffer
     */
    private final DataBuffer procIOFileStatus( SrvSession sess, TreeConnection tree, DataBuffer reqBuf, NodeRef folderNode, Object contentDriver, AlfrescoContext contentContext)
    {
        // Start a transaction
        if(contentDriver instanceof TransactionalFilesystemInterface)
        {
            TransactionalFilesystemInterface tx = (TransactionalFilesystemInterface)contentDriver;
            tx.beginReadTransaction( sess);
        }
        
        // Get the file name from the request
        
        String fName = reqBuf.getString( true);

        if ( logger.isDebugEnabled())
        	logger.debug("  File status, fname=" + fName);

        // Create a response buffer
        
        DataBuffer respBuf = new DataBuffer(256);
        respBuf.putFixedString(IOControl.Signature, IOControl.Signature.length());
        
        // Get the node for the file/folder
        
        NodeRef childNode = null;
        
        try
        {
            childNode = getSMBHelper().getNodeRef( folderNode, fName);
        }
        catch (FileNotFoundException ex)
        {
        }

        // Check if the file/folder was found
        
        if ( childNode == null)
        {
            // Return an error response
            if(logger.isDebugEnabled())
            {
                logger.debug("FileStatusProbe StsFileNotFound");
            }
            
            respBuf.putInt(DesktopAction.StsFileNotFound);
            return respBuf;
        }

        // Check if this is a file or folder node
        
        if ( getSMBHelper().isDirectory( childNode))
        {
            // Only return the status and node type for folders
            if(logger.isDebugEnabled())
            {
                logger.debug("FileStatusProbe StsSuccess type folder");
            }

            
            respBuf.putInt(DesktopAction.StsSuccess);
            respBuf.putInt(IOControl.TypeFolder);
        }
        else
        {
            // Indicate that this is a file node
            
            if(logger.isDebugEnabled())
            {
                logger.debug("FileStatusProbe StsSuccess type file");
            }
            
            respBuf.putInt(DesktopAction.StsSuccess);
            respBuf.putInt(IOControl.TypeFile);

            // Check if this file is a working copy
            
            if ( getNodeService().hasAspect( childNode, ContentModel.ASPECT_WORKING_COPY))
            {
                // Indicate that this is a working copy
                
                respBuf.putInt(IOControl.True);
                
                // Get the owner username and file it was copied from
                
                String owner = (String) getNodeService().getProperty( childNode, ContentModel.PROP_WORKING_COPY_OWNER);
                String copiedFrom = null;
                
                // Get the path of the file the working copy was generated from
                NodeRef fromNode = getCheckOutCheckInService().getCheckedOut(childNode);
                if ( fromNode != null)
                    copiedFrom = (String) getNodeService().getProperty( fromNode, ContentModel.PROP_NAME);
                
                // Pack the owner and copied from values
                
                respBuf.putString(owner != null ? owner : "", true, true);
                respBuf.putString(copiedFrom != null ? copiedFrom : "", true, true);
            }
            else
            {
                // Not a working copy
                
                respBuf.putInt(IOControl.False);
            }
            
            // Check the lock status of the file
            
            if ( getNodeService().hasAspect( childNode, ContentModel.ASPECT_LOCKABLE))
            {
                // Get the lock type and owner
                
                String lockTypeStr = (String) getNodeService().getProperty( childNode, ContentModel.PROP_LOCK_TYPE);
                String lockOwner = null;
                
                if ( lockTypeStr != null)
                    lockOwner = (String) getNodeService().getProperty( childNode, ContentModel.PROP_LOCK_OWNER);
                
                // Pack the lock type, and owner if there is a lock on the file
                
                if ( lockTypeStr == null)
                    respBuf.putInt(IOControl.LockNone);
                else
                {
                    LockType lockType = LockType.valueOf( lockTypeStr);
                    
                    respBuf.putInt(lockType == LockType.READ_ONLY_LOCK ? IOControl.LockRead : IOControl.LockWrite);
                    respBuf.putString(lockOwner != null ? lockOwner : "", true, true);
                }
            }
            else
            {
                // File is not lockable
                
                respBuf.putInt(IOControl.LockNone);
            }
            
            // Get the content data details for the file
            
            ContentData contentData = (ContentData) getNodeService().getProperty( childNode, ContentModel.PROP_CONTENT);
            
            if ( contentData != null)
            {
                // Get the content mime-type
                
                String mimeType = contentData.getMimetype();
                
                // Pack the content length and mime-type

                respBuf.putInt( IOControl.True);
                respBuf.putLong( contentData.getSize());
                respBuf.putString( mimeType != null ? mimeType : "", true, true);
            }
            else
            {
                // File does not have any content
                
                respBuf.putInt( IOControl.False);
            }
        }
        
        // Return the response
        
        return respBuf;
    }

    /**
     * Process the get action information request
     * 
     * @param sess Server session
     * @param tree Tree connection
     * @param reqBuf Request buffer
     * @param folderNode NodeRef of parent folder
     * @param netFile NetworkFile for the folder
     * @return DataBuffer
     */
    private final DataBuffer procGetActionInfo( SrvSession sess, TreeConnection tree, DataBuffer reqBuf, NodeRef folderNode,
            NetworkFile netFile, Object contentDriver, AlfrescoContext contentContext)
    {
        // Get the executable file name from the request
        
        String exeName = reqBuf.getString( true);

        if ( logger.isDebugEnabled())
        	logger.debug("  Get action info, exe=" + exeName);

        // Create a response buffer
        
        DataBuffer respBuf = new DataBuffer(256);
        respBuf.putFixedString(IOControl.Signature, IOControl.Signature.length());
        
        // Get the desktop actions list
        
        DesktopActionTable deskActions = contentContext.getDesktopActions();
        if ( deskActions == null)
        {
        	respBuf.putInt(DesktopAction.StsNoSuchAction);
        	return respBuf;
        }
        
        // Convert the executable name to an action name
        
        DesktopAction deskAction = deskActions.getActionViaPseudoName(exeName);
        if ( deskAction == null)
        {
        	respBuf.putInt(DesktopAction.StsNoSuchAction);
        	return respBuf;
        }
        
        // Return the desktop action details
        
        respBuf.putInt(DesktopAction.StsSuccess);
        respBuf.putString(deskAction.getName(), true);
        respBuf.putInt(deskAction.getAttributes());
        respBuf.putInt(deskAction.getPreProcessActions());
        
        String confirmStr = deskAction.getConfirmationString();
        respBuf.putString(confirmStr != null ? confirmStr : "", true);
        
        // Return the response
        
        return respBuf;
    }
    
    private final DataBuffer procRunActionInTransaction( final SrvSession sess, final TreeConnection tree, final DataBuffer reqBuf, final NodeRef folderNode,
            final NetworkFile netFile, final Object contentDriver, final ContentContext contentContext)
    {
    
        RetryingTransactionHelper helper = transactionService.getRetryingTransactionHelper();
        
        RetryingTransactionCallback<DataBuffer> notifyCB = new RetryingTransactionCallback<DataBuffer>() {

	        @Override
	        public DataBuffer execute() throws Throwable
	        {
	        	return procRunAction(sess, tree, reqBuf, folderNode,
	                    netFile, contentDriver, contentContext);
	        }
	    };
        
	    // Require a new read/write transaction
        return helper.doInTransaction(notifyCB, false, true);
    	
    }
    
    
    
    /**
     * Process the run action request
     * 
     * @param sess Server session
     * @param tree Tree connection
     * @param reqBuf Request buffer
     * @param folderNode NodeRef of parent folder
     * @param netFile NetworkFile for the folder
     * @return DataBuffer
     */
    private final DataBuffer procRunAction( SrvSession sess, TreeConnection tree, DataBuffer reqBuf, NodeRef folderNode,
            NetworkFile netFile, Object contentDriver, ContentContext contentContext)
    {
    	// Get the name of the action to run
    	
    	String actionName = reqBuf.getString(true);
    	
    	if ( logger.isDebugEnabled())
    	{
    		logger.debug("  Run action, name=" + actionName);
    	}

        // Create a response buffer
        
        DataBuffer respBuf = new DataBuffer(256);
        respBuf.putFixedString(IOControl.Signature, IOControl.Signature.length());
        
    	// Find the action handler
    	
        DesktopActionTable deskActions = contentContext.getDesktopActions();
        DesktopAction action = null;
        
        if ( deskActions != null)
        {
        	action = deskActions.getAction(actionName);
        }

        if ( action == null)
        {
        	logger.debug("no such action");
        	respBuf.putInt(DesktopAction.StsNoSuchAction);
        	respBuf.putString("", true);
        	return respBuf;
        }

        // Start a transaction
        if(contentDriver instanceof TransactionalFilesystemInterface)
        {
            TransactionalFilesystemInterface tx = (TransactionalFilesystemInterface)contentDriver;
            tx.beginReadTransaction( sess);
        }

        // Get an authentication ticket for the client, or validate the existing ticket. The ticket can be used when
        // generating URLs for the client-side application so that the user does not have to re-authenticate
        
        getTicketForClient( sess);
        
        // Get the list of targets for the action
        
        int targetCnt = reqBuf.getInt();
        DesktopParams deskParams = new DesktopParams(sess, folderNode, netFile);
        
        while ( reqBuf.getAvailableLength() > 4 && targetCnt > 0)
        {
        	// Get the desktop target details
        	
        	int typ = reqBuf.getInt();
        	String path = reqBuf.getString(true);
        	
        	DesktopTarget target = new DesktopTarget(typ, path);
        	deskParams.addTarget(target);
        	
        	// Find the node for the target path
        	
            NodeRef childNode = null;
            
            try
            {
            	// Check if the target path is relative to the folder we are working in or the root of the filesystem
            	
            	if ( path.startsWith("\\"))
            	{
            		// Path is relative to the root of the filesystem
            		
            		childNode = getSMBHelper().getNodeRef(contentContext.getRootNode(), path);
            	}
            	else
            	{
            		// Path is relative to the folder we are working in
            	
            		childNode = getSMBHelper().getNodeRef( folderNode, path);
            	}
            }
            catch (FileNotFoundException ex)
            {
            }

            // If the node is not valid then return an error status
            
            if (childNode != null)
            {
            	// Set the node ref for the target
            	
            	target.setNode(childNode);
            }
            else
            {
            	// Build an error response
            	
            	respBuf.putInt(DesktopAction.StsFileNotFound);
            	respBuf.putString("Cannot find noderef for path " + path, true);
            	
            	return respBuf;
            }
            
        	// Update the target count
        	
        	targetCnt--;
        }
        
        // DEBUG
        
        if (logger.isDebugEnabled())
        {
        	logger.debug("    Desktop params: " + deskParams.numberOfTargetNodes());
        	for ( int i = 0; i < deskParams.numberOfTargetNodes(); i++) {
        		DesktopTarget target = deskParams.getTarget(i);
        		logger.debug("      " + target);
        	}
        }
        
        // Run the desktop action
        
        DesktopResponse deskResponse = null;
        
        try
        {
        	// Run the desktop action
        	
        	deskResponse = action.runAction(deskParams);
        }
        catch (Exception ex)
        {
        	// Create an error response
        	
        	deskResponse = new DesktopResponse(DesktopAction.StsError, ex.getMessage());
        }
        
        // Pack the action response
        
        if ( deskResponse != null)
        {
        	// Pack the status
        	
        	respBuf.putInt(deskResponse.getStatus());
        	respBuf.putString(deskResponse.hasStatusMessage() ? deskResponse.getStatusMessage() : "", true);
        }
        else
        {
        	// Pack an error response
        	
        	respBuf.putInt(DesktopAction.StsError);
        	respBuf.putString("Action did not return response", true);
        }
        
        // Return the response
        
    	return respBuf;
    }
    
    /**
     * Process the get authentication ticket request
     * 
     * @param sess Server session
     * @param tree Tree connection
     * @param reqBuf Request buffer
     * @param folderNode NodeRef of parent folder
     * @param netFile NetworkFile for the folder
     * @return DataBuffer
     */
    private final DataBuffer procGetAuthTicket( SrvSession sess, TreeConnection tree, DataBuffer reqBuf, NodeRef folderNode,
            NetworkFile netFile, Object contentDriver, AlfrescoContext contentContext)
    {
    	// DEBUG
    	
    	if ( logger.isDebugEnabled())
    	{
    		logger.debug("  Get Auth Ticket");
    	}
    	
        // Create a response buffer
        
        DataBuffer respBuf = new DataBuffer(256);
        respBuf.putFixedString(IOControl.Signature, IOControl.Signature.length());
        
        if(contentDriver instanceof TransactionalFilesystemInterface)
        {
            TransactionalFilesystemInterface tx = (TransactionalFilesystemInterface)contentDriver;
            tx.beginReadTransaction( sess);
        }
     

        // Get an authentication ticket for the client, or validate the existing ticket. The ticket can be used when
        // generating URLs for the client-side application so that the user does not have to re-authenticate
        
        getTicketForClient( sess);

        // Pack the response
        
        AlfrescoClientInfo cInfo = (AlfrescoClientInfo) sess.getClientInformation();
        
        if ( cInfo != null && cInfo.getAuthenticationTicket() != null) {
            respBuf.putInt(DesktopAction.StsAuthTicket);
        	respBuf.putString( cInfo.getAuthenticationTicket(), true);
        }
        else {
            respBuf.putInt(DesktopAction.StsError);
        	respBuf.putString( "Client information invalid", true);
        }

        // Return the response
        
        return respBuf;
    }
    
    /**
     * Get, or validate, an authentication ticket for the client
     * 
     * @param sess SrvSession
     */
    private final void getTicketForClient(SrvSession sess)
    {
    	// Get the client information and check if there is a ticket allocated
    	
    	AlfrescoClientInfo cInfo = (AlfrescoClientInfo) sess.getClientInformation();
    	if ( cInfo == null)
    		return;
    	
    	boolean needTicket = true;
    	
    	if ( cInfo.hasAuthenticationTicket())
    	{
    		// Validate the existing ticket, it may have expired
    		
    		try
    		{
    			// Validate the existing ticket
    			
    			getAuthenticationService().validate( cInfo.getAuthenticationTicket());
    			needTicket = false;
    		}
    		catch ( AuthenticationException ex)
    		{
    			// Invalidate the current ticket
    			
    			try
    			{
    				getAuthenticationService().invalidateTicket( cInfo.getAuthenticationTicket());
    				cInfo.setAuthenticationTicket( null);
    			}
    			catch (Exception ex2)
    			{
    				// DEBUG
    				
    				if ( logger.isDebugEnabled())
    				{
    					logger.debug("Error during invalidate ticket", ex2);
    			
    				}
    			}
    			
    			// DEBUG
    			
    			if ( logger.isDebugEnabled())
    			{
    				logger.debug("Auth ticket expired or invalid");
    			}
    		}
    	}
    	
    	// Check if a ticket needs to be allocated
    	
    	if ( needTicket == true)
    	{
    		// Allocate a new ticket and store in the client information for this session
    		
   			String ticket = getAuthenticationService().getCurrentTicket();
   			cInfo.setAuthenticationTicket( ticket);
    	}
    }

    public void setSmbHelper(SMBHelper smbHelper)
    {
        this.smbHelper = smbHelper;
    }

    public SMBHelper getSMBHelper()
    {
        return smbHelper;
    }

    public void setAuthenticationService(AuthenticationService authService)
    {
        this.authService = authService;
    }

    public AuthenticationService getAuthenticationService()
    {
        return authService;
    }

    public void setCheckOutCheckInService(CheckOutCheckInService checkOutCheckInService)
    {
        this.checkOutCheckInService = checkOutCheckInService;
    }

    public CheckOutCheckInService getCheckOutCheckInService()
    {
        return checkOutCheckInService;
    }
    
    private NodeRef getNodeForPath(TreeConnection tree, String path)
    throws FileNotFoundException
    {
        if(logger.isDebugEnabled())
        {
            logger.debug("getNodeRefForPath:" + path);
        }
   
        ContentContext ctx = (ContentContext) tree.getContext();
    
        return smbHelper.getNodeRef(ctx.getRootNode(), path);
    }

	public void setTransactionService(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	public TransactionService getTransactionService() {
		return transactionService;
	}
}
