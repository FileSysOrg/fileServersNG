/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * Copyright (C) 2018 - 2019 GK Spencer
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
package org.filesys.alfresco;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Locale;
import java.util.StringTokenizer;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.alfresco.base.AlfrescoClientInfoFactory;
import org.filesys.alfresco.base.ExtendedDiskInterface;
import org.filesys.alfresco.repo.debug.FileServerDebugInterface;
import org.filesys.debug.Debug;
import org.filesys.debug.DebugConfigSection;
import org.filesys.ftp.FTPConfigSection;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.oncrpc.nfs.NFSConfigSection;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.config.GlobalConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.LicenceConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.util.IPAddress;
import org.filesys.util.LocalServer;
import org.filesys.util.PlatformType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.extensions.config.element.GenericConfigElement;

/**
 * Alfresco File Server Configuration Bean Class
 * 
 * @author gkspencer
 */
public abstract class AbstractServerConfigurationBean extends ServerConfiguration implements
        ExtendedServerConfigurationAccessor, ApplicationListener, ApplicationContextAware
{

  // Debug logging

  protected static final Log logger = LogFactory.getLog("org.alfresco.fileserverNG");

  // IP address representing null
  
  public static final String BIND_TO_IGNORE = "0.0.0.0";
  
  // Default FTP server port
  protected static final int DefaultFTPServerPort = 21;

  // Default FTP server session timeout
  protected static final int DefaultFTPSrvSessionTimeout = 5000;

  // Default FTP anonymous account name
  protected static final String DefaultFTPAnonymousAccount = "anonymous";
  
  // Token name to substitute current server name into the SMB server name
  public static final String TokenLocalName = "${localname}";
  
  // Default thread pool size
  protected static final int DefaultThreadPoolInit	= 25;
  protected static final int DefaultThreadPoolMax	= 50;
	
  // Default memory pool settings
  protected static final int[] DefaultMemoryPoolBufSizes  = { 256, 4096, 16384, 66000 };
  protected static final int[] DefaultMemoryPoolInitAlloc = {  20,   20,     5,     5 };
  protected static final int[] DefaultMemoryPoolMaxAlloc  = { 100,   50,    50,    50 };

  // Memory pool allocation limits
  protected static final int MemoryPoolMinimumAllocation	= 5;
  protected static final int MemoryPoolMaximumAllocation   = 500;
	
  // Maximum session timeout
  public static final int MaxSessionTimeout    = 60 * 60;  // 1 hour
    
  // Disk interface to use for shared filesystems
  private ExtendedDiskInterface m_repoDiskInterface;
  
  // Runtime platform type
  private PlatformType.Type m_platform = PlatformType.Type.Unchecked;

  // flag to indicate successful initialization
  private boolean m_initialised;

  // Main authentication service, public API
  private AuthenticationService m_authenticationService;

  // Authentication component, for internal functions
  protected AuthenticationComponent m_authenticationComponent;
  
  // Various services
  private NodeService m_nodeService;
  private PersonService m_personService;
  private TransactionService m_transactionService;
  protected TenantService m_tenantService;
  private SearchService m_searchService;
  private NamespaceService m_namespaceService;
  private AuthorityService m_authorityService;
  
  // Local server name and domain/workgroup name
  private String m_localName;
  private String m_localNameFull;
  private String m_localDomain;
  
  // Disable use of native code on Windows, do not use any JNI calls
  protected boolean m_disableNativeCode = false;

  // Debug settings
  private boolean m_dumpStackTrace;

    /**
   * Default constructor
   */
  public AbstractServerConfigurationBean()
  {
    super ( "");
  }
  
  /**
   * Class constructor
   * 
   * @param srvName String
   */
  public AbstractServerConfigurationBean( String srvName)
  {
      super( srvName);
  }
  
  /**
   * Set the authentication service
   * 
   * @param authenticationService AuthenticationService
   */
  public void setAuthenticationService(AuthenticationService authenticationService)
  {
      m_authenticationService = authenticationService;
  }

  /**
   * Set the filesystem driver for the node service based filesystem
   * 
   * @param diskInterface DiskInterface
   */
  public void setDiskInterface(ExtendedDiskInterface diskInterface)
  {
      m_repoDiskInterface = diskInterface;
  }

  /**
   * Set the authentication component
   * 
   * @param component AuthenticationComponent
   */
  public void setAuthenticationComponent(AuthenticationComponent component)
  {
      m_authenticationComponent = component;
  }

  /**
   * Set the node service
   * 
   * @param service NodeService
   */
  public void setNodeService(NodeService service)
  {
      m_nodeService = service;
  }

  /**
   * Set the person service
   * 
   * @param service PersonService
   */
  public void setPersonService(PersonService service)
  {
      m_personService = service;
  }

  /**
   * Set the transaction service
   * 
   * @param service TransactionService
   */
  public void setTransactionService(TransactionService service)
  {
      m_transactionService = service;
  }

  /**
   * Set the tenant service
   * 
   * @param tenantService TenantService
   */
  public void setTenantService(TenantService tenantService)
  {
	  m_tenantService = tenantService;
  }

  /**
   * Set the search service
   * 
   * @param searchService SearchService
   */
  public void setSearchService(SearchService searchService)
  {
	  m_searchService = searchService;
  }
  
  /**
   * Set the namespace service
   * 
   * @param namespaceService NamespaceService
   */
  public void setNamespaceService(NamespaceService namespaceService)
  {
	  m_namespaceService = namespaceService;
  }
  
  /**
   * Set the authority service
   * 
   * @param authService AuthorityService
   */
  public void setAuthorityService(AuthorityService authService)
  {
  	m_authorityService = authService;
  }

  /**
   * Enable/disable dumping of exception stack traces
   *
   * @param ena boolean
   */
  public void setDumpStackTraces(boolean ena) { m_dumpStackTrace = ena; }

    /**
   * Check if the configuration has been initialized
   * 
   * @return Returns true if the configuration was fully initialised
   */
  public boolean isInitialised()
  {
      return m_initialised;
  }

  /**
   * Check if the SMB server is enabled
   * 
   * @return boolean
   */
  public final boolean isSMBServerEnabled()
  {
      return hasConfigSection( SMBConfigSection.SectionName);
  }

  /**
   * Check if the FTP server is enabled
   * 
   * @return boolean
   */
  public final boolean isFTPServerEnabled()
  {
      return hasConfigSection( FTPConfigSection.SectionName);
  }

  /**
   * Check if the NFS server is enabled
   * 
   * @return boolean
   */
  public final boolean isNFSServerEnabled()
  {
      return hasConfigSection( NFSConfigSection.SectionName);
  }
  
  /**
   * Return the repository disk interface to be used to create shares
   * 
   * @return DiskInterface
   */
  public final ExtendedDiskInterface getRepoDiskInterface()
  {
      return m_repoDiskInterface;
  }
  
  /**
   * Initialize the configuration using the configuration service
   */
  public void init()
  {
      // Check that all required properties have been set
	  
      if (m_authenticationComponent == null)
      {
          throw new AlfrescoRuntimeException("Property 'authenticationComponent' not set");
      }
      else if (m_authenticationService == null)
      {
          throw new AlfrescoRuntimeException("Property 'authenticationService' not set");
      }
      else if (m_nodeService == null)
      {
          throw new AlfrescoRuntimeException("Property 'nodeService' not set");
      }
      else if (m_personService == null)
      {
          throw new AlfrescoRuntimeException("Property 'personService' not set");
      }
      else if (m_transactionService == null)
      {
          throw new AlfrescoRuntimeException("Property 'transactionService' not set");
      }
      else if (m_repoDiskInterface == null)
      {
          throw new AlfrescoRuntimeException("Property 'diskInterface' not set");
      }
      else if (m_authorityService == null)
      {
      	throw new AlfrescoRuntimeException("Property 'authorityService' not set");
      }
      
      // Set the platform type

      determinePlatformType();

      // Create the debug output configuration using a logger for all file server debug output
      
      DebugConfigSection debugConfig = new DebugConfigSection( this);
      try
      {
          GenericConfigElement config = new GenericConfigElement( "params");
          if ( m_dumpStackTrace)
              config.addChild( new GenericConfigElement( "dumpStackTrace"));

          debugConfig.setDebug("org.filesys.alfresco.repo.debug.FileServerDebugInterface", config);
      }
      catch ( InvalidConfigurationException ex)
      {
      }
      
      // Create the global configuration and Alfresco configuration sections
      
      new GlobalConfigSection( this);
      new AlfrescoConfigSection( this);
      
      // Install the Alfresco client information factory
      
      ClientInfo.setFactory( new AlfrescoClientInfoFactory());
      
      // We need to check for a WINS server configuration in the SMB server config section to initialize
      // the NetBIOS name lookups to use WINS rather broadcast lookups, which may be used to get the local
      // domain
      
      try {

    	  // Get the SMB server config section and extract the WINS server config, if available
    	  
          processWINSServerConfig();
      }
      catch (Exception ex) {
    	  
          // Configuration error

          logger.error("File server configuration error (WINS), " + ex.getMessage(), ex);
      }
      
      // Initialize the filesystems
      
      try
      {
    	  // Process the core server configuration
    	  processCoreServerConfig();
    	  
          // Process the security configuration
          processSecurityConfig();
          
          // Process the Cluster  configuration
          processClusterConfig();

          // Process the filesystems configuration
          processFilesystemsConfig();

          // Load the optional licence key
          processLicenceConfig();

          // Load the optional audit log configuration
          processAuditLog();
      }
      catch (Exception ex)
      {
          // Configuration error
          throw new AlfrescoRuntimeException("File server configuration error, " + ex.getMessage(), ex);
      }

      // Initialize the SMB and FTP servers, if the filesystem(s) initialized successfully
      
      // Initialize the SMB server

      try
      {

          // Process the SMB server configuration
          processSMBServerConfig();

          // Log the successful startup
          logger.info("SMB server " + (isSMBServerEnabled() ? "" : "NOT ") + "started");
      }
      catch (UnsatisfiedLinkError ex)
      {
          // Error accessing the Win32NetBIOS DLL code

          logger.error("Error accessing Win32 NetBIOS, check DLL is on the path");

          // Disable the SMB server

          removeConfigSection( SMBConfigSection.SectionName);
      }
      catch (Throwable ex)
      {
          // Configuration error

          logger.error("SMB server configuration error, " + ex.getMessage(), ex);

          // Disable the SMB server

          removeConfigSection( SMBConfigSection.SectionName);
      }

      // Initialize the FTP server

      try
      {
          // Process the FTP server configuration
          processFTPServerConfig();
          
          // Log the successful startup
          
          logger.info("FTP server " + (isFTPServerEnabled() ? "" : "NOT ") + "started");
      }
      catch (Exception ex)
      {
          // Configuration error
        
          logger.error("FTP server configuration error, " + ex.getMessage(), ex);
      }                 
  }

  protected abstract void processCoreServerConfig() throws InvalidConfigurationException;

  protected abstract void processSecurityConfig();
  
  protected abstract void processFilesystemsConfig();

  protected abstract void processSMBServerConfig();

  protected abstract void processFTPServerConfig();
  
  protected abstract void processClusterConfig() throws InvalidConfigurationException;

  protected abstract void processLicenceConfig();

  protected abstract void processAuditLog() throws IOException;

  protected void processWINSServerConfig() {}

  /**
   * Close the configuration bean
   */
  public final void closeConfiguration()
  {
      super.closeConfiguration();
  }
  
  /**
   * Determine the platform type
   */
  private final void determinePlatformType()
  {
    if ( m_platform == PlatformType.Type.Unchecked)
      m_platform = PlatformType.isPlatformType();
  }
  
    /**
     * Parse the platforms attribute returning the set of platform ids
     * 
     * @param platformStr String
     */
    protected final EnumSet<PlatformType.Type> parsePlatformString(String platformStr)
    {
        // Split the platform string and build up a set of platform types
  
        EnumSet<PlatformType.Type> platformTypes = EnumSet.noneOf(PlatformType.Type.class);
        if (platformStr == null || platformStr.length() == 0)
            return platformTypes;
  
        StringTokenizer token = new StringTokenizer(platformStr.toUpperCase(Locale.ENGLISH), ",");
        String typ = null;
  
        try
        {
            while (token.hasMoreTokens())
            {
  
                // Get the current platform type string and validate
  
                typ = token.nextToken().trim();
                PlatformType.Type platform = PlatformType.Type.valueOf(typ);
  
                if (platform != PlatformType.Type.Unknown)
                    platformTypes.add(platform);
                else
                    throw new AlfrescoRuntimeException("Invalid platform type, " + typ);
            }
        }
        catch (IllegalArgumentException ex)
        {
            throw new AlfrescoRuntimeException("Invalid platform type, " + typ);
        }
  
        // Return the platform types
  
        return platformTypes;
    }
    
    /**
     * Get the local server name and optionally trim the domain name
     * 
     * @param trimDomain boolean
     * @return String
     */
    public final String getLocalServerName(boolean trimDomain)
    {
        // Use cached untrimmed version if necessary
        if (!trimDomain)
        {
            return getLocalServerName();
        }
        
        // Check if the name has already been set
        if (m_localName != null)
            return m_localName;

        // Find the local server name
        String srvName = getLocalServerName();

        // Strip the domain name

        if (trimDomain && srvName != null)
        {
            int pos = srvName.indexOf(".");
            if (pos != -1)
                srvName = srvName.substring(0, pos);
        }

        // Save the local server name

        m_localName = srvName;

        // Return the local server name

        return srvName;
    }

    /**
     * Get the local server name (untrimmed)
     * 
     * @return String
     */
    private String getLocalServerName()
    {
        // Check if the name has already been set

        if (m_localNameFull != null)
            return m_localNameFull;

        // Find the local server name

        String srvName = LocalServer.getLocalServerName( true);

        // Save the local server name

        m_localNameFull = srvName;

        // Return the local server name

        return srvName;
    }

    /**
     * Get the local domain/workgroup name
     * 
     * @return String
     */
    public final String getLocalDomainName()
    {
        // Check if the local domain has been set

        if (m_localDomain != null)
            return m_localDomain;

        // Find the local domain name

        String domainName = LocalServer.getLocalDomainName();

        // Save the local domain name

        m_localDomain = domainName;

        // Return the local domain/workgroup name

        return domainName;
    }
    
    /**
     * Parse an adapter name string and return the matching address
     * 
     * @param adapter String
     * @return InetAddress
     * @exception InvalidConfigurationException
     */
    protected final InetAddress parseAdapterName(String adapter)
      throws InvalidConfigurationException {

      NetworkInterface ni = null;
      
      try {
        ni = NetworkInterface.getByName( adapter);
      }
      catch (SocketException ex) {
        throw new InvalidConfigurationException( "Invalid adapter name, " + adapter);
      }
      
      if ( ni == null)
        throw new InvalidConfigurationException( "Invalid network adapter name, " + adapter);
      
      // Get the IP address for the adapter

      InetAddress adapAddr = null;
      Enumeration<InetAddress> addrEnum = ni.getInetAddresses();
      
      while ( addrEnum.hasMoreElements() && adapAddr == null) {
        
        // Get the current address
        
        InetAddress addr = addrEnum.nextElement();
        if ( IPAddress.isNumericAddress( addr.getHostAddress()))
          adapAddr = addr;
      }
      
      // Check if we found the IP address to bind to
      
      if ( adapAddr == null)
        throw new InvalidConfigurationException( "Adapter " + adapter + " does not have a valid IP address");

      // Return the adapter address
      
      return adapAddr;
    }
    
    private ApplicationContext applicationContext = null;
    
    
    /* (non-Javadoc)
     * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
     */
    public void onApplicationEvent(ApplicationEvent event)
    {
        if (event instanceof ContextRefreshedEvent)
        {
            ContextRefreshedEvent refreshEvent = (ContextRefreshedEvent)event;
            ApplicationContext refreshContext = refreshEvent.getApplicationContext();
            if (refreshContext != null && refreshContext.equals(applicationContext))
            {
                // Initialize the bean
              
                init();
            }
        }
    }

    /* (non-Javadoc)
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext applicationContext)
        throws BeansException
    {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Return the authentication service
     * 
     * @return AuthenticationService
     */
    protected final AuthenticationService getAuthenticationService()
    {
        return m_authenticationService;
    }
    
    /**
     * Return the authentication component
     * 
     * @return AuthenticationComponent
     */
    protected final AuthenticationComponent getAuthenticationComponent()
    {
        return m_authenticationComponent;
    }
    
    /**
     * Return the node service
     * 
     * @return NodeService
     */
    protected final NodeService getNodeService()
    {
        return m_nodeService;
    }
    
    /**
     * Return the person service
     * 
     * @return PersonService
     */
    protected final PersonService getPersonService()
    {
        return m_personService;
    }
    
    /**
     * Return the transaction service
     * 
     * @return TransactionService
     */
    protected final TransactionService getTransactionService()
    {
        return m_transactionService;
    }
    
    /**
     * Return the tenant service
     * 
     * @return TenantService
     */
    protected final TenantService getTenantService()
    {
    	return m_tenantService;
    }
    
    /**
     * Return the search service
     * 
     * @return SearchService
     */
    protected final SearchService getSearchService()
    {
    	return m_searchService;
    }
    
    /**
     * Return the namespace service
     * 
     * @return NamespaceService
     */
    protected final NamespaceService getNamespaceService()
    {
    	return m_namespaceService;
    }
    
    /**
     * Check if native code calls are disabled
     * 
     * @return boolean
     */
    public final boolean isNativeCodeDisabled()
    {
    	return m_disableNativeCode;
    }
    
    /**
     * Return the named bean
     * 
     * @param beanName String
     * @return Object
     */
    public final Object getBean( String beanName)
    {
    	return applicationContext.getBean( beanName);
    }
    
    /**
     * Return the applicatin context
     * 
     * @return ApplicationContext
     */
    public final ApplicationContext getApplicationsContext()
    {
    	return applicationContext;
    }

    /**
     * Return the authority service
     * 
     * @return AuthorityService
     */
    public final AuthorityService getAuthorityService()
    {
    	return m_authorityService;
    }

    /**
     * Get the default number of threads to create in the thread pool
     *
     * @return int
     */
    protected int getDefaultThreads() {
        return DefaultThreadPoolInit;
    }

    /**
     * Get the maximum number of threads to create in the thread pool
     *
     * @return int
     */
    protected int getMaximumThreads() {
        return DefaultThreadPoolMax;
    }
}
