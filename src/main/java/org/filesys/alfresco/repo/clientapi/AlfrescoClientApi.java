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

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.filesys.alfresco.base.AlfrescoClientInfo;
import org.filesys.alfresco.repo.ContentContext;
import org.filesys.alfresco.repo.SMBHelper;
import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.FileType;
import org.filesys.server.filesys.NotifyAction;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.clientapi.ApiRequest;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Alfresco JSON Client API Class
 *
 * <p>Alfresco implementation of the JFileServer client API</p>
 *
 * @author gkspencer
 */
public class AlfrescoClientApi extends JSONClientAPI {

    // Client API version
    public static final String CLIENT_API_VERSION = "0.1.0";

    // Debug output prefix
    public static final String DBG = "[AlfClientAPI] ";

    // Services and helpers
    private SMBHelper m_smbHelper;
    private NodeService m_nodeService;
    private TransactionService m_transactionService;
    private AuthenticationService m_authService;
    private FileFolderService m_fileFolderService;
    private CheckOutCheckInService m_checkOutCheckInService;
    private ServiceRegistry m_serviceRegistry;

    // List of context contexts (should only be one context in the list)
    private List<ContentContext> m_contentContexts;

    // Content context for the main filesystem (should only be one defined)
    private ContentContext m_filesysContext;

    // Filesystem device name, from the filesystem.name property value
    private String m_filesystemName;

    // Base URL for Share, in the format protocol://host:port/webapp
    private String m_shareBaseURL;

    /**
     * Alfresco client API bean initialization
     */
    private void init() {
        PropertyCheck.mandatory(this, "smbHelper", m_smbHelper);
        PropertyCheck.mandatory( this, "nodeService", m_nodeService);
        PropertyCheck.mandatory( this, "transactionService", m_transactionService);
        PropertyCheck.mandatory( this, "authenticationService", m_authService);
        PropertyCheck.mandatory(this, "fileFolderService", m_fileFolderService);
        PropertyCheck.mandatory( this, "checkOutCheckInService", m_checkOutCheckInService);
        PropertyCheck.mandatory( this, "serviceRegistry", m_serviceRegistry);
        PropertyCheck.mandatory( this, "contentContexts", m_contentContexts);
        PropertyCheck.mandatory( this, "filesyststemName", m_filesystemName);

        // Find the content context for the main filesystem
        if ( !m_contentContexts.isEmpty()) {

            // Find the context for the main filesystem
            for( ContentContext ctx: m_contentContexts) {

                // Find the context for the main filesystem
                if ( ctx.getDeviceName().equalsIgnoreCase( m_filesystemName))
                    m_filesysContext = ctx;
            }
        }

        // Make sure we got the content context
        if ( m_filesysContext == null)
            throw new RuntimeException( "fileserversNG Failed to find context for filesystem '" + m_filesystemName + "'");
    }

    // Setters
    public void setSmbHelper(SMBHelper smbHelper) {
        m_smbHelper = smbHelper;
    }
    public void setNodeService( NodeService nodeService) {
        m_nodeService = nodeService;
    }
    public void setTransactionService(TransactionService transactionService) { m_transactionService = transactionService; }
    public void setAuthenticationService(AuthenticationService authenticationService) { m_authService = authenticationService; }
    public void setFileFolderService(FileFolderService fileFolderService) {
        m_fileFolderService = fileFolderService;
    }
    public void setCheckOutCheckInService(CheckOutCheckInService checkOutCheckInService) { m_checkOutCheckInService = checkOutCheckInService; }
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        m_serviceRegistry = serviceRegistry;
    }
    public void setContentContexts(ArrayList<ContentContext> contentContexts) { m_contentContexts = contentContexts; }
    public void setFilesystemName(String filesystemName) {
        m_filesystemName = filesystemName;
    }

    // Getters
    public SMBHelper getSmbHelper() { return m_smbHelper; }
    public NodeService getNodeService() { return m_nodeService; }
    public TransactionService getTransactionService() { return m_transactionService; }
    public AuthenticationService getAuthenticationService() { return m_authService; }
    public FileFolderService getFileFolderService() { return m_fileFolderService; }
    public CheckOutCheckInService getCheckOutInService() { return m_checkOutCheckInService; }

    /**
     * Set the Share base URL for creating URLs to files, should be in the format protocol://host:port/app
     * eg. http://host:8180/share
     *
     * @param shareBaseURL String
     */
    public void setShareBaseURL(String shareBaseURL) {
        m_shareBaseURL = shareBaseURL;

        // Make sure the base URL ends with a trailing '/'
        if ( !m_shareBaseURL.endsWith( "/"))
            m_shareBaseURL = m_shareBaseURL + "/";
    }

    /**
     * Get node details for a relative path
     *
     * @param path String
     * @return NodeDetails
     */
    private NodeDetails getNodeDetailsForPath( String path) {

        NodeDetails details = null;

        try {
            // Get the NodeRef for the current path
            NodeRef nodeRef = m_smbHelper.getNodeRef(m_filesysContext.getRootNode(), path);

            // Get the file/folder details
            org.alfresco.service.cmr.model.FileInfo fInfo = m_fileFolderService.getFileInfo(nodeRef);

            // Create the node details
            FileType fType = fInfo.isFolder() ? FileType.Directory : FileType.RegularFile;
            if ( fInfo.isLink())
                fType = FileType.SymbolicLink;

            details = new NodeDetails( path, fType, nodeRef);
        }
        catch ( java.io.FileNotFoundException ex) {

            // Create node details entry for the missing file/folder
            details = new NodeDetails( path, PathStatus.PathNotExist);
        }

        // Return the node details
        return details;
    }

    /**
     * Build a list of NodeRefs from a list of relative paths
     *
     * @param paths List&lt;String&gt;
     * @return List&lt;NodeDetails&gt;
     * @exception ClientAPIException If a relative path does not exist
     */
    private List<NodeDetails> getNodeDetailsForPaths( List<String> paths)
        throws ClientAPIException {

        // Create a list for the nodes
        List<NodeDetails> nodes = new ArrayList<>( paths.size());

        for ( String path : paths) {

            // Get the node details, or an entry that indicates the path does not exist
            nodes.add( getNodeDetailsForPath( path));
        }

        // Return the list of nodes
        return nodes;
    }

    /**
     * Build a Share URL for a relative path
     *
     * @param node NodeDetails
     * @return String
     */
    private String buildSharePathForNode( NodeDetails node) {

        // For now build a Share URL that directs to the Repository view
        String shareURL = m_shareBaseURL;

        if ( node.isType() == FileType.Directory) {

            // Convert the relative path to an HTTP path
            String httpPath = node.getPath().replace( '\\', '/');
            if ( httpPath.endsWith( "/"))
                httpPath = httpPath.substring( httpPath.length() - 1);

            String encodedPath = URLEncoder.encode( httpPath, StandardCharsets.UTF_8);

            // Append the repository location details for the folder
            shareURL += "page/repository#filter=path|" + encodedPath + "|&page=1";

            // DEBUG
            if ( hasDebug())
                Debug.println( DBG + "Share URL for folder=" + node.getPath() + ", URL=" + shareURL);
        }
        else if ( node.isType() == FileType.RegularFile) {

            // Append the repository location details for the file
            shareURL += "page/document-details?nodeRef=" + node.getNodeRef().toString();

            // DEBUG
            if ( hasDebug())
                Debug.println( DBG + "Share URL for file=" + node.getPath() + ", URL=" + shareURL);
        }
        else {

            // Unsupported file type

            // DEBUG
            if ( hasDebug())
                Debug.println( DBG + "Path " + node.getPath() + " has unsupported file type=" + node.isType().name());
        }

        // Return the Share URL
        return shareURL;
    }

    /**
     * Get, or validate, an authentication ticket for the client
     *
     * @param sess SrvSession
     */
    private final void getTicketForClient(SrvSession<?> sess)
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
                m_authService.validate( cInfo.getAuthenticationTicket());
                needTicket = false;
            }
            catch ( AuthenticationException ex)
            {
                // Invalidate the current ticket
                try
                {
                    m_authService.invalidateTicket( cInfo.getAuthenticationTicket());
                    cInfo.setAuthenticationTicket( null);
                }
                catch (Exception ex2)
                {
                    // DEBUG
                    if ( hasDebug())
                        Debug.println( DBG + "Error during invalidate ticket, ex=" + ex2);
                }

                // DEBUG
                if ( hasDebug())
                    Debug.println( DBG + "Auth ticket expired or invalid");
            }
        }

        // Check if a ticket needs to be allocated
        if ( needTicket)
        {
            // Allocate a new ticket and store in the client information for this session
            String ticket = m_authService.getCurrentTicket();
            cInfo.setAuthenticationTicket( ticket);
        }
    }

    /**
     * Get the list of supported requests by this client API implementation
     *
     * @return EnumSet&lt;RequestId&gt;
     */
    public EnumSet<ApiRequest> getSupportedRequests() {
        return EnumSet.of( ApiRequest.GetApiInfo, ApiRequest.CheckOutFile, ApiRequest.CheckInFile, ApiRequest.GetUrlForPath);
    }

    /**
     * Get the client API version
     *
     * @return String
     */
    public String getClientAPIVersion() {
        return CLIENT_API_VERSION;
    }

    @Override
    protected void preProcessRequest(ClientAPINetworkFile netFile, ClientAPIRequest req) {

        // Setup authentication for the request
        getTicketForClient( netFile.getSession());
    }

    @Override
    public ClientAPIResponse processCheckOutFile(CheckOutFileRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Check out files request=" + req);

        // Make sure there are paths to check out
        if ( req.getRelativePaths().isEmpty())
            throw new ClientAPIException( "No paths to check out", false);

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = getNodeDetailsForPaths( req.getRelativePaths());

        // Process the list of nodes
        int chkCnt = 0;
        List<String> workPaths = new ArrayList<String>( nodes.size());

        for ( NodeDetails curNode : nodes) {

            // Make sure the node status is not an error
            if ( curNode.hasNodeRef()) {

                // Check that the node is not a working copy
                if ( m_nodeService.hasAspect( curNode.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                    // DEBUG
                    if ( hasDebug())
                        Debug.println( DBG + "Path is a working copy, cannot check out, path=" + curNode.getPath());

                    // Indicate the path is a working copy
                    curNode.setStatus( PathStatus.WorkingCopy);
                }
                else if ( m_nodeService.hasAspect( curNode.getNodeRef(), ContentModel.ASPECT_LOCKABLE)) {

                    // Get the lock type
                    if ( m_nodeService.getProperty( curNode.getNodeRef(), ContentModel.PROP_LOCK_TYPE) != null) {

                        // DEBUG
                        if ( hasDebug())
                            Debug.println( DBG + "Path is locked, cannot check out, path=" + curNode.getPath());

                        // Indicate the path is locked
                        curNode.setStatus( PathStatus.Locked);
                    }
                }

                // Check out the file
                if ( curNode.hasStatus() == PathStatus.NotProcessed) {

                    // Check out the file
                    NodeRef workingCopyNode = m_checkOutCheckInService.checkout( curNode.getNodeRef());

                    // Update the count of checked out files
                    chkCnt++;

                    // Get the working copy file name
                    String workingCopyName = (String) m_nodeService.getProperty( workingCopyNode, ContentModel.PROP_NAME);

                    // DEBUG
                    if ( hasDebug())
                        Debug.println( DBG + "Checked out working copy " + workingCopyName);

                    // Build the relative path to the checked out file
                    String workingCopyPath = FileName.removeFileName( curNode.getPath()) + FileName.DOS_SEPERATOR_STR + workingCopyName;
                    workPaths.add( workingCopyPath);

                    // Update cached state for the working copy to indicate the file exists
                    FileStateCache stateCache = m_filesysContext.getStateCache();
                    if ( stateCache != null) {

                        // Update any cached state for the working copy file
                        FileState fstate = stateCache.findFileState( workingCopyPath);
                        if ( fstate != null)
                            fstate.setFileStatus( FileStatus.FileExists);
                    }

                    // Check if there are any file/directory change notify requests active
                    if ( m_filesysContext.hasFileServerNotifications()) {

                        // Queue a file added change notification
                        m_filesysContext.getChangeHandler().notifyFileChanged( NotifyAction.Added, workingCopyPath);
                    }

                    // Indicate the file check out was successful
                    curNode.setStatus( PathStatus.Success);
                }
            }
        }

        // Return a success status to the client
        return new PathListResponse( workPaths);
    }

    @Override
    public ClientAPIResponse processCheckInFile(CheckInFileRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Check in files request=" + req);

        // Make sure there are paths to check out
        if ( req.getRelativePaths().isEmpty())
            throw new ClientAPIException( "No paths to check out", false);

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = getNodeDetailsForPaths( req.getRelativePaths());

        // Process the list of nodes
        int chkCnt = 0;

        for ( NodeDetails curNode : nodes) {

            // Make sure the node status is not an error
            if (curNode.hasNodeRef()) {

                // Check that the node is not a working copy
                if (m_nodeService.hasAspect(curNode.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                    try {

                        // Get the original file node ref
                        List<AssociationRef> assocs = m_nodeService.getSourceAssocs( curNode.getNodeRef(), ContentModel.ASSOC_WORKING_COPY_LINK);
                        NodeRef originalRef = null;

                        if ( assocs != null && assocs.size() == 1)
                            originalRef = assocs.get( 0).getSourceRef();

                        // Check in the file, pass an empty version properties so that versionable nodes create a new version
                        Map<String, Serializable> versionProperties = new HashMap<String, Serializable>();
                        m_checkOutCheckInService.checkin(curNode.getNodeRef(), versionProperties, null, false);

                        // Update the count of checked in files
                        chkCnt++;

                        // Update the node details with the original document node ref
                        curNode.setNodeRef( originalRef);

                        // DEBUG
                        if (hasDebug())
                            Debug.println( DBG + "Checked in working copy " + curNode.getPath());

                        // Check if there are any file/directory change notify requests active
                        if (m_filesysContext.hasFileServerNotifications()) {

                            // Queue a file added change notification
                            m_filesysContext.getChangeHandler().notifyFileChanged(NotifyAction.Removed, curNode.getPath());
                        }

                        // Indicate the file check out was successful
                        curNode.setStatus(PathStatus.Success);

                        if ( curNode.hasNodeRef()) {

                            // Get the original file name
                            String origName = (String) m_nodeService.getProperty( curNode.getNodeRef(), ContentModel.PROP_NAME);

                            // Build the relative path to the original file
                            String origPath = FileName.removeFileName( curNode.getPath()) + FileName.DOS_SEPERATOR_STR + origName;

                            // Update the relative path to the original document path
                            curNode.setPath( origPath);
                        }

                    } catch (Exception ex) {
                    }
                }
            }
        }

        // Build a list of the original document paths
        List<String> origPaths = new ArrayList<String>( nodes.size());

        for ( NodeDetails curNode : nodes) {
            origPaths.add( curNode.getPath());
        }

        // Return a list of the updated original document paths
        return new PathListResponse( origPaths);
    }

    @Override
    public ClientAPIResponse processCancelCheckOut(CancelCheckOutFileRequest req) throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Cancel check out files request=" + req);

        // Make sure there are paths to check out
        if ( req.getRelativePaths().isEmpty())
            throw new ClientAPIException( "No paths to check out", false);

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = getNodeDetailsForPaths( req.getRelativePaths());

        // Process the list of nodes
        int chkCnt = 0;

        for ( NodeDetails curNode : nodes) {

            // Make sure the node status is not an error
            if (curNode.hasNodeRef()) {

                // Check that the node is not a working copy
                if (m_nodeService.hasAspect(curNode.getNodeRef(), ContentModel.ASPECT_WORKING_COPY)) {

                    try {

                        // Get the original file node ref
                        List<AssociationRef> assocs = m_nodeService.getSourceAssocs( curNode.getNodeRef(), ContentModel.ASSOC_WORKING_COPY_LINK);
                        NodeRef originalRef = null;

                        if ( assocs != null && assocs.size() == 1)
                            originalRef = assocs.get( 0).getSourceRef();

                        // Cancel the file check out
                        m_checkOutCheckInService.cancelCheckout( curNode.getNodeRef());

                        // Update the count of checked in files
                        chkCnt++;

                        // Update the node details with the original document node ref
                        curNode.setNodeRef( originalRef);

                        // DEBUG
                        if (hasDebug())
                            Debug.println( DBG + "Cancelled check out, working copy " + curNode.getPath());

                        // Check if there are any file/directory change notify requests active
                        if (m_filesysContext.hasFileServerNotifications()) {

                            // Queue a file added change notification
                            m_filesysContext.getChangeHandler().notifyFileChanged(NotifyAction.Removed, curNode.getPath());
                        }

                        // Indicate the file check out was successful
                        curNode.setStatus(PathStatus.Success);

                        if ( curNode.hasNodeRef()) {

                            // Get the original file name
                            String origName = (String) m_nodeService.getProperty( curNode.getNodeRef(), ContentModel.PROP_NAME);

                            // Build the relative path to the original file
                            String origPath = FileName.removeFileName( curNode.getPath()) + FileName.DOS_SEPERATOR_STR + origName;

                            // Update the relative path to the original document path
                            curNode.setPath( origPath);
                        }

                    } catch (Exception ex) {
                    }
                }
            }
        }

        // Build a list of the original document paths
        List<String> origPaths = new ArrayList<String>( nodes.size());

        for ( NodeDetails curNode : nodes) {
            origPaths.add( curNode.getPath());
        }

        // Return a list of the updated original document paths
        return new PathListResponse( origPaths);
    }

    @Override
    public ClientAPIResponse processGetURLForPath(GetURLForPathRequest req)
        throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Get URL for path request=" + req);

        // Check that the path is valid
        if ( req.getRelativePath().isEmpty())
            throw new ClientAPIException( "Path is empty", false);

        // Get hte node details for the path
        NodeDetails node = getNodeDetailsForPath( req.getRelativePath());

        if ( node.hasStatus() == PathStatus.PathNotExist)
            throw new ClientAPIException( "Path " + req.getRelativePath() + " does not exist", false);

        // Build the Share URL to the file/folder
        String shareURL = buildSharePathForNode( node);

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Returning Share URL=" + shareURL + " for path=" + node.getPath());

        // TODO: Optionally build an alternate URL to the Alfresco node browser

        // Return the URL(s)
        return new GetURLForPathResponse( shareURL, "");
    }

    @Override
    public ClientAPIResponse processGetPathStatus(GetPathStatusRequest req) throws ClientAPIException {

        // DEBUG
        if ( hasDebug())
            Debug.println( DBG + "Get path status request=" + req);

        // Make sure there are paths to check
        if ( req.getRelativePaths().isEmpty())
            throw new ClientAPIException( "No paths to check status", false);

        // Make sure the check type is valid
        PathCheckType checkTyp = PathStatusHelper.asCheckType( req.getCheckType());

        if ( checkTyp == PathCheckType.Invalid)
            throw new ClientAPIException( "Invalid check type - " + req.getCheckType(), false);

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = getNodeDetailsForPaths( req.getRelativePaths());

        // Run the path status check for the requested check type
        List<PathStatusInfo> stsList = PathStatusHelper.checkPathStatusForPaths( this, checkTyp, nodes);

        // Build the response
        return new GetPathStatusResponse( stsList);
    }
}
