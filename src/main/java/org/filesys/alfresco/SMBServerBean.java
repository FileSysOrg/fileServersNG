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
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.netbios.server.NetBIOSNameServer;
import org.filesys.server.NetworkServer;
import org.filesys.server.SessionListener;
import org.filesys.server.SrvSessionList;
import org.filesys.server.config.LicenceConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBServer;
import org.filesys.smb.server.SMBSrvPacket;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * SMB Server Bean Class
 * 
 * <p>Create and start the various server components required to run the SMB server.
 * 
 * @author GKSpencer
 */
public class SMBServerBean extends AbstractLifecycleBean
{
    private static final Log logger = LogFactory.getLog("org.alfresco.smb.server");

    // Server configuration and sections
    private ServerConfiguration m_filesysConfig;
    private SMBConfigSection m_smbConfig;

    // SMB server
    private SMBServer m_smbServer;

    // List of SMB server components
    private List<NetworkServer> serverList = new LinkedList<NetworkServer>();
    private List<SessionListener> sessionListeners = new LinkedList<SessionListener>();
    
    /**
     * Class constructor
     *
     * @param serverConfig ServerConfiguration
     */
    public SMBServerBean(ServerConfiguration serverConfig)
    {
        m_filesysConfig = serverConfig;
    }

    public void setSessionListeners(List<SessionListener> sessionListeners)
    {
        this.sessionListeners = sessionListeners;
    }

    /**
     * Return the server configuration
     * 
     * @return ServerConfiguration
     */
    public final ServerConfiguration getConfiguration()
    {
        return m_filesysConfig;
    }

    /**
     * @return Returns true if the server started up without any errors
     */
    public boolean isStarted()
    {
        return (!serverList.isEmpty() && m_filesysConfig.isServerRunning( "SMB"));
    }

    /**
     * Start the SMB server components
     * 
     * @exception SocketException If a network error occurs
     * @exception IOException If an I/O error occurs
     */
    public final void startServer() throws SocketException, IOException
    {
        try
        {
            // Create the SMB server and NetBIOS name server, if enabled
            
            m_smbConfig = (SMBConfigSection) m_filesysConfig.getConfigSection(SMBConfigSection.SectionName);
            
            if (m_smbConfig != null)
            {
                // Create the NetBIOS name server if NetBIOS SMB is enabled
                
                if (m_smbConfig.hasNetBIOSSMB())
                    serverList.add(new NetBIOSNameServer(m_filesysConfig));

                // Check if a licence key has been set, if so then check if the Enterprise Java File Server is available
                m_smbServer = null;
                LicenceConfigSection licenceConfig = (LicenceConfigSection) m_filesysConfig.getConfigSection( LicenceConfigSection.SectionName);

                if ( licenceConfig != null && licenceConfig.getLicenceKey() != null && licenceConfig.getLicenceKey().length() > 0) {

                    try {

                        // Check if the Enterprise SMB server is available
                        String entSMBSrvClassName = "org.filesys.smb.server.EnterpriseSMBServer";
                        Class.forName( entSMBSrvClassName);

                        // Find the constructor for the Enterprise SMB server
                        Class[] constructorParams = new Class[1];
                        constructorParams[0] = ServerConfiguration.class;

                        Constructor<?> srvConstructor = Class.forName( entSMBSrvClassName).getConstructor( constructorParams);

                        // Create an Enterprise SMB server
                        if ( srvConstructor != null) {
                            Object[] constructArgs = new Object[1];
                            constructArgs[0] = m_filesysConfig;

                            try {
                                m_smbServer = (SMBServer) srvConstructor.newInstance(constructArgs);

                                // DEBUG
                                if ( logger.isDebugEnabled())
                                    logger.debug("Using Enterprise SMB server");
                            }
                            catch ( InvocationTargetException ex) {

                                // DEBUG
                                if ( logger.isErrorEnabled())
                                    logger.error("Failed to create Enterprise SMB server, ex=" + ex.getTargetException());
                            }
                        }
                    }
                    catch ( Exception ex) {
                    }
                }
                else if ( logger.isDebugEnabled())
                    logger.debug("No licence key found");

                // Set the enabled SMB dialects for the SMB server
                DialectSelector dialects = null;

                if ( m_smbConfig.getEnabledDialects() != null) {

                    // Mask the enabled dialects with the supported dialects
                    dialects = m_smbConfig.getEnabledDialects();
                    dialects.maskWith(SMBSrvPacket.getParserFactory().getSupportedDialects());

                    // DEBUG
                    if ( logger.isDebugEnabled())
                        logger.debug("Enabled SMB dialects: " + dialects.toString() + ", supported: " + SMBSrvPacket.getParserFactory().getSupportedDialects().toString());
                }
                else {

                    // The enabled dialect list was not configured, enable all of the supported dialects depending on which
                    // server is being used
                    dialects = SMBSrvPacket.getParserFactory().getSupportedDialects();

                    // DEBUG
                    if ( logger.isDebugEnabled())
                        logger.debug("Using supported SMB dialects: " + dialects.toString());
                }

                // Check if there are any enabled dialects
                if ( dialects.isEmpty())
                    throw new AlfrescoRuntimeException("No SMB dialects enabled");

                // Enable SMB dialects
                m_smbConfig.setEnabledDialects( dialects);

                // Create the SMB server
                if ( m_smbServer == null)
                    m_smbServer = new SMBServer(m_filesysConfig);

                serverList.add(m_smbServer);

                // Install any SMB server listeners so they receive callbacks when sessions are
                // opened/closed on the SMB server (e.g. for Authenticators)

                for (SessionListener sessionListener : this.sessionListeners)
                {
                    m_smbServer.addSessionListener(sessionListener);
                }
                
                // Add the servers to the configuration
                
                for (NetworkServer server : serverList)
                {
                    m_filesysConfig.addServer(server);
                }
            }

            // Start the SMB server(s)

            for (NetworkServer server : serverList)
            {
                if (logger.isInfoEnabled())
                    logger.info("Starting server " + server.getProtocolName() + " ...");

                // Start the server
                server.startServer();
            }
        }
        catch (Throwable e)
        {
        	for (NetworkServer server : serverList)
            {
        		getConfiguration().removeServer(server.getProtocolName());
            }
        	
            serverList.clear();
            throw new AlfrescoRuntimeException("Failed to start SMB Server", e);
        }
        // success
    }

    /**
     * Stop the SMB server components
     */
    public final void stopServer()
    {
        if (m_filesysConfig == null)
        {
            // initialisation failed
            return;
        }
        
        // Shutdown the SMB server components

        for ( NetworkServer server : serverList)
        {
            if (logger.isInfoEnabled())
                logger.info("Shutting server " + server.getProtocolName() + " ...");

            // Stop the server
            
            server.shutdownServer(false);
            
            // Remove the server from the global list
            
            getConfiguration().removeServer(server.getProtocolName());
        }
        
        // Clear the server list
        
        serverList.clear();
    }

    /**
     * Runs the SMB server directly
     * 
     * @param args String[]
     */
    public static void main(String[] args)
    {
        PrintStream out = System.out;

        out.println("SMB Server Test");
        out.println("---------------");

        ClassPathXmlApplicationContext ctx = null;
        try
        {
            // Create the configuration service in the same way that Spring creates it
            
            ctx = new ClassPathXmlApplicationContext("alfresco/application-context.xml");

            // Get the SMB server bean
            
            SMBServerBean server = (SMBServerBean) ctx.getBean("smbServer");
            if (server == null)
            {
                throw new AlfrescoRuntimeException("Server bean 'smbServer' not defined");
            }

            // Stop the FTP server, if running
            
            NetworkServer srv = server.getConfiguration().findServer("FTP");
            if ( srv != null)
                srv.shutdownServer(true);
            
            // Stop the NFS server, if running
            
            srv = server.getConfiguration().findServer("NFS");
            if ( srv != null)
                srv.shutdownServer(true);
            
            // Only wait for shutdown if the SMB server is enabled
            
            if ( server.getConfiguration().hasConfigSection(SMBConfigSection.SectionName))
            {
                
                // SMB server should have automatically started
                // Wait for shutdown via the console
                
                out.println("Enter 'x' to shutdown ...");
                boolean shutdown = false;
    
                // Wait while the server runs, user may stop the server by typing a key
                
                while (shutdown == false)
                {
    
                    // Wait for the user to enter the shutdown key
    
                    int ch = System.in.read();
    
                    if (ch == 'x' || ch == 'X')
                        shutdown = true;
    
                    synchronized (server)
                    {
                        server.wait(20);
                    }
                }
    
                // Stop the server
                
                server.stopServer();
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if (ctx != null)
            {
                ctx.close();
            }
        }
        System.exit(1);
    }

    @Override
    protected void onBootstrap(ApplicationEvent event)
    {
        try
        {
            startServer();
        }
        catch (SocketException e)
        {
            throw new AlfrescoRuntimeException("Failed to start SMB server", e);
        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException("Failed to start SMB server", e);
        }
    }

    @Override
    protected void onShutdown(ApplicationEvent event)
    {
        stopServer();
        
        // Clear the configuration
        m_filesysConfig = null;

        // Clear the SMB server
        m_smbServer = null;
    }

    /**
     * Return the SMB server, or null if not active
     *
     * @return SMBServer
     */
    public SMBServer getSMBServer() {
        return m_smbServer;
    }
}
