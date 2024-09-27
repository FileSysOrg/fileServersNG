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

import org.alfresco.jlan.util.StringList;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.PropertyCheck;
import org.filesys.alfresco.base.AlfrescoClientInfo;
import org.filesys.alfresco.repo.ContentContext;
import org.filesys.alfresco.repo.SMBHelper;
import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.clientapi.ApiRequest;
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;
import org.filesys.server.filesys.clientapi.json.*;

import com.moandjiezana.toml.Toml;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public static final String CLIENT_API_VERSION = "1.0.0";

    // Script actions configuration file name
    public static final String CLIENT_API_SCRIPTS_CONFIGURATION = "scripts.toml";

    // Built in action Toml keys
    public static final String TOML_BUILTIN_ACTIONS = "BuiltInActions";
    public static final String TOML_CHECKINOUT      = "CheckInOut";
    public static final String TOML_OPENINBROWSER   = "OpenInBrowser";

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
    private ScriptService m_scriptService;

    private Nodes m_nodes;

    // List of context contexts (should only be one context in the list)
    private List<ContentContext> m_contentContexts;

    // Content context for the main filesystem (should only be one defined)
    private ContentContext m_filesysContext;

    // Filesystem device name, from the filesystem.name property value
    private String m_filesystemName;

    // Base URL for Share, in the format protocol://host:port/webapp
    private String m_shareBaseURL;

    // Server side scripts folder, where the actions configuration and server-side scripts are located
    private String m_scriptsDir;

    // Context menu title, description and icon details
    private String m_menuTitle;
    private String m_menuDescription;

    private ActionIcon m_menuIcon;

    // List of server side actions and scripted actions
    private List<ServerAction> m_actions;
    private HashMap<String, ServerAction> m_actionsMap;

    private List<ScriptedServerAction> m_scriptedActions;

    // Scripted actions definitions file path and last modified date/time
    private File m_scriptsConfigFile;
    private long m_scriptsConfigModifiedAt;
    private long m_configNextCheckAt;
    private long m_configCheckInterval = 60000L;    // milliseconds

    // Built-in action configuration
    private boolean m_builtInCheckInOut;
    private boolean m_builtInOpenInBrowser;

    /**
     * Alfresco client API bean initialization
     */
    private void init() {
        PropertyCheck.mandatory(this, "smbHelper", m_smbHelper);
        PropertyCheck.mandatory(this, "nodeService", m_nodeService);
        PropertyCheck.mandatory(this, "transactionService", m_transactionService);
        PropertyCheck.mandatory(this, "authenticationService", m_authService);
        PropertyCheck.mandatory(this, "fileFolderService", m_fileFolderService);
        PropertyCheck.mandatory(this, "checkOutCheckInService", m_checkOutCheckInService);
        PropertyCheck.mandatory(this, "serviceRegistry", m_serviceRegistry);
        PropertyCheck.mandatory(this, "contentContexts", m_contentContexts);
        PropertyCheck.mandatory(this, "filesystemName", m_filesystemName);
        PropertyCheck.mandatory(this, "scriptService", m_scriptService);
//        PropertyCheck.mandatory( this, "scriptsDir", m_scriptsDir);

        // Find the content context for the main filesystem
        if (!m_contentContexts.isEmpty()) {

            // Find the context for the main filesystem
            for (ContentContext ctx : m_contentContexts) {

                // Find the context for the main filesystem
                if (ctx.getDeviceName().equalsIgnoreCase(m_filesystemName))
                    m_filesysContext = ctx;
            }
        }

        // Make sure we got the content context
        if (m_filesysContext == null)
            throw new RuntimeException("fileserversNG Failed to find context for filesystem '" + m_filesystemName + "'");

        // Check the scripts folder
        if (m_scriptsDir != null) {

            Path scriptsPath = Path.of(m_scriptsDir);

            if (Files.exists(scriptsPath) && Files.isDirectory(scriptsPath)) {

                // Check for a script actions configuration TOML file
                File scriptsConfigFile = Paths.get(m_scriptsDir, CLIENT_API_SCRIPTS_CONFIGURATION).toFile();

                try {
                    m_scriptedActions = parseScriptConfiguration(scriptsConfigFile);

                    // DEBUG
                    if (hasDebug())
                        Debug.println(DBG + "Loaded " + m_scriptedActions.size() + " scripted actions from " + scriptsConfigFile.getAbsolutePath());

                    // Save the scripts configuration file path and last modified date/time
                    m_scriptsConfigFile = scriptsConfigFile;
                    m_scriptsConfigModifiedAt = scriptsConfigFile.lastModified();
                    m_configNextCheckAt = System.currentTimeMillis() + m_configCheckInterval;

                    // Update the action map with the scripted actions
                    updateActionsMap( m_scriptedActions);

                } catch (FileNotFoundException ex) {
                    throw new RuntimeException("fileserversNG Script configuration file " + CLIENT_API_SCRIPTS_CONFIGURATION + " not found", ex);
                } catch (Exception ex) {
                    throw new RuntimeException("fileserversNG Script configuration file " + CLIENT_API_SCRIPTS_CONFIGURATION + " error", ex);
                }
            } else
                throw new RuntimeException("fileserversNG Client API scripts path is not valid - " + m_scriptsDir);
        } else {

            // DEBUG
            if (hasDebug())
                Debug.println(DBG + "No script actions specified");
        }

        // Create the node script helper object
        m_nodes = new Nodes(m_nodeService);
    }

    /**
     * Parse the scripts configuration file and return a list of scripted actions
     *
     * @param scriptConfig File
     * @return List&lt;ScriptedAction&gt;
     * @throws FileNotFoundException Scripts configuration file not found
     */
    private List<ScriptedServerAction> parseScriptConfiguration(File scriptConfig) throws FileNotFoundException {

        // Check if the scripts configuration exists, and it is a file
        List<ScriptedServerAction> actionList = null;

        if (scriptConfig.exists() && scriptConfig.isFile()) {

            // Parse the configuration TOML file
            FileReader configReader = new FileReader(scriptConfig);
            Toml toml = new Toml().read(configReader);

            // Check if any built-in actions have been disabled
            if ( toml.contains( TOML_BUILTIN_ACTIONS)) {
                Toml builtIn = toml.getTable(TOML_BUILTIN_ACTIONS);

                // Check if the check in/out actions should be enabled
                if (builtIn.contains(TOML_CHECKINOUT)) {
                    m_builtInCheckInOut = builtIn.getBoolean(TOML_CHECKINOUT);

                    // DEBUG
                    if (hasDebug())
                        Debug.println(DBG + "Built-in action CheckInOut enabled: " + m_builtInCheckInOut);
                }

                // Check if the open in browser actions should be enabled
                if (builtIn.contains(TOML_OPENINBROWSER)) {
                    m_builtInOpenInBrowser = builtIn.getBoolean(TOML_OPENINBROWSER);

                    // DEBUG
                    if (hasDebug())
                        Debug.println(DBG + "Built-in action OpenInBrowser enabled: " + m_builtInOpenInBrowser);
                }
            }
            else {

                // Enable the built-in actions
                m_builtInCheckInOut     = true;
                m_builtInOpenInBrowser  = true;

                // DEBUG
                if (hasDebug())
                    Debug.println(DBG + "Enabled all built-in actions");
            }

            // Enable/disable Java server actions
            for ( ServerAction action : m_actions) {

                // Enable/disable the check in/out actions
                if ( action instanceof CheckInServerAction || action instanceof CheckOutServerAction)
                    action.setEnabled( m_builtInCheckInOut);

                // Enable/disable the open in browser action
                else if ( action instanceof OpenInShareServerAction)
                    action.setEnabled( m_builtInOpenInBrowser);
            }

            // Check if there are any scripted actions defined
            List<Toml> tomlActions = toml.getTables("Action");
            if (!tomlActions.isEmpty()) {

                // Allocate the scripted actions list
                actionList = new ArrayList<ScriptedServerAction>(tomlActions.size());

                // Create scripted actions
                for (Toml tomlAction : tomlActions) {

                    // Get the action details, and validate
                    String name = tomlAction.getString("name");
                    String desc = tomlAction.getString("description");
                    String script = tomlAction.getString("script");
                    List<String> attrList = tomlAction.getList("attributes");
                    String icon = tomlAction.getString("icon");

                    if (name == null || name.isEmpty())
                        throw new RuntimeException("fileserversNG Scripted action invalid, or empty, name");
                    if (script == null || script.isEmpty())
                        throw new RuntimeException("fileserversNG Scripted action " + name + " has invalid, or empty, script name");
                    if (attrList == null || attrList.isEmpty())
                        throw new RuntimeException("fileserversNG Scripted action " + name + " has invalid, or empty, attributes");

                    // Check that the script file exists
                    File scriptFile = Path.of(m_scriptsDir, script).toFile();

                    if (!scriptFile.isFile())
                        throw new RuntimeException("fileserversNG Script file " + script + " is not a file");

                    // Check that the script file exists, add to the list of scripted actions
                    if (scriptFile.exists()) {

                        // Parse the attributes
                        EnumSet<ServerAction.Flags> flags = EnumSet.noneOf(ServerAction.Flags.class);

                        // Validate the action attributes
                        boolean attrValid = true;

                        for (String attr : attrList) {

                            try {
                                // Validate the attribute
                                ServerAction.Flags attrFlag = ServerAction.Flags.valueOf(attr);
                                flags.add(attrFlag);
                            } catch (Exception ex) {

                                // Mark the attributes as invalid
                                attrValid = false;

                                // DEBUG
                                if (hasDebug())
                                    Debug.println(DBG + "Invalid attribute '" + attr + "' for action " + name + ", ignoring action");
                            }
                        }

                        // Create an action icon for the menu, if specified
                        ActionIcon menuIcon = parseIconConfiguration( icon);

                        // Create the scripted action and add to the list
                        if (attrValid) {

                            ScriptedServerAction action = new AlfrescoScriptedServerAction(name, desc, flags, scriptFile.getAbsolutePath(), menuIcon);
                            actionList.add(action);

                            // DEBUG
                            if (hasDebug())
                                Debug.println(DBG + "Loaded scripted action=" + action);
                        }
                    } else {

                        // DEBUG
                        if (hasDebug())
                            Debug.println(DBG + "Failed to load script=" + scriptFile + ", ignoring");
                    }
                }
            }
        } else
            throw new RuntimeException("fileserversNG Client API scripts configuration does not exist, or is not a file (" +
                    CLIENT_API_SCRIPTS_CONFIGURATION + ")");

        // Return the list of scripted actions
        return actionList;
    }

    /**
     * Parse an action icon configuration
     *
     * @param iconCfg String
     * @return ActionIcon
     */
    private ActionIcon parseIconConfiguration( String iconCfg) {

        ActionIcon menuIcon = null;

        if (iconCfg != null && !iconCfg.isEmpty()) {

            // Check if the icon matches a built in icon name
            menuIcon = ActionIcon.createByName( iconCfg);

            if ( menuIcon == null) {

                // Check for a custom icon configuration
                StringTokenizer tokens = new StringTokenizer(iconCfg, ":");

                if (tokens.countTokens() == 3) {

                    if (tokens.nextToken().equalsIgnoreCase("CUSTOM")) {

                        // Get the icon library and icon index values
                        String iconLib = tokens.nextToken();
                        String iconIdxStr = tokens.nextToken();

                        // Convert the token index to negative integer value
                        int iconIdx = 0;

                        try {
                            iconIdx = Integer.parseInt(iconIdxStr);
                            if (iconIdx > 0)
                                iconIdx = -iconIdx;

                            // Create the custom action icon
                            menuIcon = ActionIcon.createCustomIcon(iconIdx, iconLib);
                        } catch (NumberFormatException ex) {

                            // DEBUG
                            if (hasDebug())
                                Debug.println(DBG + "Invalid custom icon idx = " + iconIdxStr + ", ex=" + ex);
                        }
                    }
                }
            }

            // DEBUG
            if ( hasDebug() && menuIcon != null)
                Debug.println(DBG + "Loaded icon configuration=" + menuIcon);
        }

        // Return the menu icon, or null
        return menuIcon;
    }

    /**
     * Update the actions map from the list of scripted actions
     *
     * @param scriptedActions List&lt;ScriptedServerAction&gt;
     */
    private void updateActionsMap( List<ScriptedServerAction> scriptedActions) {

        // Make sure the actions map is valid
        if ( m_actionsMap == null)
            return;

        // Remove existing scripted actions from the map
        List<String> scriptActions = new ArrayList<String>();

        for ( String name : m_actionsMap.keySet()) {
            ServerAction curAction = m_actionsMap.get( name);
            if ( curAction instanceof ScriptedServerAction)
                scriptActions.add( name);
        }
        scriptActions.forEach( name -> m_actionsMap.remove( name));

        // Add the scripted actions
        for ( ScriptedServerAction action : scriptedActions) {
            m_actionsMap.put( action.getName(), action);
        }
    }

    // Setters
    public void setSmbHelper(SMBHelper smbHelper) {
        m_smbHelper = smbHelper;
    }

    public void setNodeService(NodeService nodeService) {
        m_nodeService = nodeService;
    }

    public void setTransactionService(TransactionService transactionService) {
        m_transactionService = transactionService;
    }

    public void setAuthenticationService(AuthenticationService authenticationService) {
        m_authService = authenticationService;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        m_fileFolderService = fileFolderService;
    }

    public void setCheckOutCheckInService(CheckOutCheckInService checkOutCheckInService) {
        m_checkOutCheckInService = checkOutCheckInService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        m_serviceRegistry = serviceRegistry;
    }

    public void setContentContexts(ArrayList<ContentContext> contentContexts) {
        m_contentContexts = contentContexts;
    }

    public void setFilesystemName(String filesystemName) {
        m_filesystemName = filesystemName;
    }

    public void setScriptService(ScriptService scriptService) {
        m_scriptService = scriptService;
    }

    public void setScriptsDir(String scriptsDir) {
        m_scriptsDir = scriptsDir;
    }

    public void setMenuTitle( String title) {
        m_menuTitle = title;

        if ( m_menuTitle == null || m_menuTitle.isEmpty())
            throw new RuntimeException("Client API menu title cannot be empty");
    }

    public void setMenuDescription( String description) { m_menuDescription = description; }

    public void setMenuIcon ( String iconName) {
        m_menuIcon = ActionIcon.createByName( iconName);

        if ( m_menuIcon == null)
            throw new RuntimeException("Invalid menu icon '" + iconName + "'");
    }

    public void setActions( List<ServerAction> actions) {
        m_actions = actions;

        // Clear the current actions map, and load with the new actions
        m_actionsMap = null;
        if ( actions != null) {
            m_actionsMap = new HashMap<String, ServerAction>();

            for ( ServerAction action : actions ) {
                m_actionsMap.put(action.getName(), action);
            }
        }
    }

    // Getters
    public SMBHelper getSmbHelper() {
        return m_smbHelper;
    }

    public NodeService getNodeService() {
        return m_nodeService;
    }

    public TransactionService getTransactionService() {
        return m_transactionService;
    }

    public AuthenticationService getAuthenticationService() {
        return m_authService;
    }

    public FileFolderService getFileFolderService() {
        return m_fileFolderService;
    }

    public CheckOutCheckInService getCheckOutInService() {
        return m_checkOutCheckInService;
    }

    public ScriptService getScriptService() {
        return m_scriptService;
    }

    public String getScriptsDir() {
        return m_scriptsDir;
    }

    public ContentContext getContentContext() {
        return m_filesysContext;
    }

    public ServiceRegistry getServiceRegistry() {
        return m_serviceRegistry;
    }

    public Nodes getNodes() {
        return m_nodes;
    }

    public String getShareBaseURL() {
        return m_shareBaseURL;
    }

    /**
     * Set the Share base URL for creating URLs to files, should be in the format protocol://host:port/app
     * eg. http://host:8180/share
     *
     * @param shareBaseURL String
     */
    public void setShareBaseURL(String shareBaseURL) {
        m_shareBaseURL = shareBaseURL;

        // Make sure the base URL ends with a trailing '/'
        if (!m_shareBaseURL.endsWith("/"))
            m_shareBaseURL = m_shareBaseURL + "/";
    }

    /**
     * Get node details for a relative path
     *
     * @param path String
     * @return NodeDetails
     */
    protected NodeDetails getNodeDetailsForPath(String path) {

        NodeDetails details = null;

        try {
            // Get the NodeRef for the current path
            NodeRef nodeRef = m_smbHelper.getNodeRef(m_filesysContext.getRootNode(), path);

            // Get the file/folder details
            org.alfresco.service.cmr.model.FileInfo fInfo = m_fileFolderService.getFileInfo(nodeRef);

            // Create the node details
            FileType fType = fInfo.isFolder() ? FileType.Directory : FileType.RegularFile;
            if (fInfo.isLink())
                fType = FileType.SymbolicLink;

            details = new NodeDetails(path, fType, nodeRef);
        } catch (java.io.FileNotFoundException ex) {

            // Create node details entry for the missing file/folder
            details = new NodeDetails(path, PathStatus.PathNotExist);
        }

        // Return the node details
        return details;
    }

    /**
     * Build a list of NodeRefs from a list of relative paths
     *
     * @param paths List&lt;String&gt;
     * @return List&lt;NodeDetails&gt;
     * @throws ClientAPIException If a relative path does not exist
     */
    protected List<NodeDetails> getNodeDetailsForPaths(List<String> paths)
            throws ClientAPIException {

        // Create a list for the nodes
        List<NodeDetails> nodes = new ArrayList<>(paths.size());

        for (String path : paths) {

            // Get the node details, or an entry that indicates the path does not exist
            nodes.add(getNodeDetailsForPath(path));
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
    protected String buildSharePathForNode(NodeDetails node) {

        // For now build a Share URL that directs to the Repository view
        String shareURL = m_shareBaseURL;

        if (node.isType() == FileType.Directory) {

            // Convert the relative path to an HTTP path
            String httpPath = node.getPath().replace('\\', '/');
            if (httpPath.endsWith("/"))
                httpPath = httpPath.substring(httpPath.length() - 1);

            String encodedPath = URLEncoder.encode(httpPath, StandardCharsets.UTF_8);

            // Append the repository location details for the folder
            shareURL += "page/repository#filter=path|" + encodedPath + "|&page=1";

            // DEBUG
            if (hasDebug())
                Debug.println(DBG + "Share URL for folder=" + node.getPath() + ", URL=" + shareURL);
        } else if (node.isType() == FileType.RegularFile) {

            // Append the repository location details for the file
            shareURL += "page/document-details?nodeRef=" + node.getNodeRef().toString();

            // DEBUG
            if (hasDebug())
                Debug.println(DBG + "Share URL for file=" + node.getPath() + ", URL=" + shareURL);
        } else {

            // Unsupported file type
            shareURL = null;

            // DEBUG
            if (hasDebug())
                Debug.println(DBG + "Path " + node.getPath() + " has unsupported file type=" + node.isType().name());
        }

        // Return the Share URL
        return shareURL;
    }

    /**
     * Get, or validate, an authentication ticket for the client
     *
     * @param sess SrvSession
     */
    protected final void getTicketForClient(SrvSession<?> sess) {

        // Get the client information and check if there is a ticket allocated
        AlfrescoClientInfo cInfo = (AlfrescoClientInfo) sess.getClientInformation();
        if (cInfo == null)
            return;

        boolean needTicket = true;

        if (cInfo.hasAuthenticationTicket()) {
            // Validate the existing ticket, it may have expired
            try {
                // Validate the existing ticket
                m_authService.validate(cInfo.getAuthenticationTicket());
                needTicket = false;
            } catch (AuthenticationException ex) {
                // Invalidate the current ticket
                try {
                    m_authService.invalidateTicket(cInfo.getAuthenticationTicket());
                    cInfo.setAuthenticationTicket(null);
                } catch (Exception ex2) {
                    // DEBUG
                    if (hasDebug())
                        Debug.println(DBG + "Error during invalidate ticket, ex=" + ex2);
                }

                // DEBUG
                if (hasDebug())
                    Debug.println(DBG + "Auth ticket expired or invalid");
            }
        }

        // Check if a ticket needs to be allocated
        if (needTicket) {
            // Allocate a new ticket and store in the client information for this session
            String ticket = m_authService.getCurrentTicket();
            cInfo.setAuthenticationTicket(ticket);
        }
    }

    /**
     * Get the list of supported requests by this client API implementation
     *
     * @return EnumSet&lt;RequestId&gt;
     */
    public EnumSet<ApiRequest> getSupportedRequests() {
        return EnumSet.of ( ApiRequest.GetApiInfo, ApiRequest.GetPathStatus, ApiRequest.GetUrlForPath, ApiRequest.RunAction);
    }

    @Override
    public ContextMenu getContextMenu() {

        // Check if the scripted actions configuration has been updated
        if ( m_configNextCheckAt < System.currentTimeMillis()) {

            // Check if the scripts configuration has been updated
            if ( m_scriptsConfigFile.lastModified() != m_scriptsConfigModifiedAt) {

                long nextCheckAt = m_configNextCheckAt;

                synchronized ( m_scriptedActions) {

                    // Check if the scripts configuration has been reloaded by another thread
                    if ( m_configNextCheckAt == nextCheckAt) {

                        // Reload the scripts configuration
                        try {

                            m_scriptedActions = parseScriptConfiguration(m_scriptsConfigFile);

                            // DEBUG
                            if (hasDebug())
                                Debug.println(DBG + "Reloaded " + m_scriptedActions.size() + " scripted actions from " + m_scriptsConfigFile.getAbsolutePath());

                            // Save the last modified date/time and update the next configuration check time
                            m_scriptsConfigModifiedAt = m_scriptsConfigFile.lastModified();
                            m_configNextCheckAt = System.currentTimeMillis() + m_configCheckInterval;

                            // Update the action map with the scripted actions
                            updateActionsMap( m_scriptedActions);

                        } catch (FileNotFoundException ex) {
                            throw new RuntimeException("fileserversNG Reload script configuration file " + CLIENT_API_SCRIPTS_CONFIGURATION + " not found", ex);
                        } catch (Exception ex) {
                            throw new RuntimeException("fileserversNG Reload script configuration file " + CLIENT_API_SCRIPTS_CONFIGURATION + " error", ex);
                        }
                    }
                }
            }
        }

        // Build the full list of actions from the Java actions and scripted actions
        List<ServerAction> actions = new ArrayList<>( m_actions.size() + m_scriptedActions.size());

        // Add the enabled Java actions
        for ( ServerAction action : m_actions ) {
            if ( action.isEnabled())
                actions.add( action);
        }

        // Add the scripted actions
        actions.addAll(m_scriptedActions);

        // Create the context menu details
        ContextMenu ctxMenu = new ContextMenu( m_menuTitle, m_menuDescription, m_menuIcon);
        ctxMenu.setActions( actions);

        return ctxMenu;
    }

    /**
     * Find the specified scripted action
     *
     * @param name String
     * @return ScriptedServerAction
     */
    private final ScriptedServerAction findScriptedAction( String name) {

        // Check that there are scripted actions
        if ( m_scriptedActions == null)
            return null;

        // Search for the required scripted action
        for ( ScriptedServerAction action : m_scriptedActions ) {
            if ( action.getName().equalsIgnoreCase( name))
                return action;
        }

        return null;
    }

    /**
     * Get the parent folder node for the specified path
     *
     * @param path String
     * @return NodeRef
     */
    protected NodeRef getParentNodeForPath(String path) {

        NodeRef parent = null;

        try {

            // Get the parent folder path
            String parentPath = FileName.getParentPart( path);

            // Get the parent folder node
            parent = m_smbHelper.getNodeRef(m_filesysContext.getRootNode(), parentPath);
        }
        catch ( FileNotFoundException ignored) {
        }

        return parent;
    }

    /**
     * Get the client API version
     *
     * @return String
     */
    public String getClientAPIVersion() {
        return CLIENT_API_VERSION;
    }

    /**
     * Update the parent folder path file information last write and change timestamps
     *
     * @param path String
     * @param ctx ContentContext
     */
    protected final void updateParentFolderTimestamps(String path, ContentContext ctx) {

        // Get the file state for the parent folder, or create it
        if(ctx.hasStateCache()) {
            FileStateCache cache = ctx.getStateCache();
            FileState fstate = cache.findFileState( path, true);

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
                if (hasDebug())
                    Debug.println(DBG + "Update timestamps for folder=" + path);
                //            if ( hasDebug()) {
                //                Debug.println("$$$ Update parent folder path=" + parentPath + ", fstate=" + fstate);
                //                ctx.getStateCache().dumpCache( true);
                //            }
            }
        }
    }

    @Override
    protected void preProcessRequest(ClientAPINetworkFile netFile, ClientAPIRequest req) {

        // Setup authentication for the request
        getTicketForClient(netFile.getSession());
    }

    @Override
    public ClientAPIResponse processGetURLForPath(GetURLForPathRequest req)
            throws ClientAPIException {

        // DEBUG
        if (hasDebug())
            Debug.println(DBG + "Get URL for path request=" + req);

        // Check that the path is valid
        if (req.getRelativePath().isEmpty())
            throw new ClientAPIException("Path is empty", false);

        // Get hte node details for the path
        NodeDetails node = getNodeDetailsForPath(req.getRelativePath());

        if (node.hasStatus() == PathStatus.PathNotExist)
            throw new ClientAPIException("Path " + req.getRelativePath() + " does not exist", false);

        // Build the Share URL to the file/folder
        String shareURL = buildSharePathForNode(node);

        // DEBUG
        if (hasDebug())
            Debug.println(DBG + "Returning Share URL=" + shareURL + " for path=" + node.getPath());

        // TODO: Optionally build an alternate URL to the Alfresco node browser

        // Return the URL(s)
        return new GetURLForPathResponse(shareURL, "");
    }

    @Override
    public ClientAPIResponse processGetPathStatus(GetPathStatusRequest req) throws ClientAPIException {

        // DEBUG
        if (hasDebug())
            Debug.println(DBG + "Get path status request=" + req);

        // Make sure there are paths to check
        if (req.getRelativePaths().isEmpty())
            throw new ClientAPIException("No paths to check status", false);

        // Make sure the check type is valid
        PathCheckType checkTyp = PathStatusHelper.asCheckType(req.getCheckType());

        if (checkTyp == PathCheckType.Invalid)
            throw new ClientAPIException("Invalid check type - " + req.getCheckType(), false);

        // Convert the path list to a list of NodeRefs
        List<NodeDetails> nodes = getNodeDetailsForPaths(req.getRelativePaths());

        // Run the path status check for the requested check type
        List<PathStatusInfo> stsList = PathStatusHelper.checkPathStatusForPaths(this, checkTyp, nodes);

        // Build the response
        return new GetPathStatusResponse(stsList);
    }

    @Override
    public ClientAPIResponse processRunAction( RunActionRequest req, ClientAPINetworkFile netFile)
            throws ClientAPIException {

        // DEBUG
        if (hasDebug())
            Debug.println(DBG + "Process RunAction request, action=" + req.getAction());

        // Find the associated action to handle the request
        ServerAction action = m_actionsMap.get( req.getAction());

        if ( action != null) {

            // Pass the client API implementation to the action
            req.setClientAPI( this);
            req.setDebug( hasDebug());

            // Run the Java action
            return action.runAction( req, netFile);
        }

        // Return an unsupported error
        return new ErrorResponse("Unknown action - '" + req.getAction() + "'", true);
    }

}
