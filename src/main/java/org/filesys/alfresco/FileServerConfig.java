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
package org.filesys.alfresco;

import java.net.InetAddress;

import org.filesys.alfresco.util.SMBMounter;
import org.filesys.ftp.FTPConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.TcpipSMB;
import org.filesys.smb.server.SMBConfigSection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.util.PlatformType;

/**
 * File Server Configuration MBean Class
 * 
 * <p>Implements the file server configuration interface using the fileServerConfigurationBase bean
 * from network-protocol-context.xml.
 * 
 * @author gkspencer
 */
public class FileServerConfig implements FileServerConfigMBean {

	// Debug logging
	
    private static final Log logger = LogFactory.getLog( FileServerConfig.class);
    
	// File server configuration
	
	private ServerConfiguration m_serverConfig;
	
	private FTPServerBean  m_ftpServer;
	private SMBServerBean m_smbServer;

	/**
	 * Default constructor
	 */
	public FileServerConfig()
	{
	}

	/**
	 * Set the file server configuration
	 * 
	 * @return ServerConfiguration
	 */
	public ServerConfiguration getFileServerConfiguration()
	{
		return m_serverConfig;
	}
	
	/**
	 * Set the file server configuration
	 * 
	 * @param serverConfig ServerConfiguration
	 */
	public void setFileServerConfiguration(ServerConfiguration serverConfig)
	{
		m_serverConfig = serverConfig;
	}
	
	/**
	 * Set the SMB server
	 * 
	 * @param smbServer  SMB server
	 */
	public void setSmbServer(SMBServerBean smbServer)
	{
		m_smbServer = smbServer;
	}
	
	/**
	 * Check if the SMB server is enabled
	 * 
	 * @return boolean
	 */
	public boolean isSMBServerEnabled()
	{
		return (m_smbServer.isStarted() && m_serverConfig.hasConfigSection(SMBConfigSection.SectionName));
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.filesys.alfresco.server.config.FileServerConfigMBean#setSMBServerEnabled(boolean)
	 */
	public void setSMBServerEnabled(boolean enabled) throws Exception
	{
		if (!enabled && isSMBServerEnabled())
		{
			m_smbServer.stopServer();
		}
		
		if (enabled && !isSMBServerEnabled())
		{
			m_smbServer.startServer();
		}
	}
	
	/**
	 * Set the FTP server
	 * 
	 * @param ftpServer  FTP server
	 */
	public void setFtpServer(FTPServerBean ftpServer)
	{
		m_ftpServer = ftpServer;
	}
	
	/**
	 * Check if the FTP server is enabled
	 * 
	 * @return boolean
	 */
	public boolean isFTPServerEnabled()
	{
		return (m_ftpServer.isStarted() && m_serverConfig.hasConfigSection(FTPConfigSection.SectionName));
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.filesys.alfresco.server.config.FileServerConfigMBean#setFTPServerEnabled(boolean)
	 */
	public void setFTPServerEnabled(boolean enabled) throws Exception
	{
		if (!enabled && isFTPServerEnabled())
		{
			m_ftpServer.stopServer();
		}
		
		if (enabled && !isFTPServerEnabled())
		{
			m_ftpServer.startServer();
		}
	}
	
	/**
	 * Return the SMB server name
	 * 
	 * @return String
	 */
	public String getSMBServerName()
	{
		return m_serverConfig.getServerName();
	}
	
	/**
	 * Return the SMB server IP address
	 * 
	 * @return String
	 */
	public String getSMBServerAddress()
	{
		return null;
	}

	/**
	 * Create a mounter to mount/unmount a share on the SMB server
	 * 
	 * @return SMBMounter
	 */
	public SMBMounter createMounter() {
		
		// Check if the SMB server is enabled
		
		if ( isSMBServerEnabled() == false)
			return null;

		// Access the SMB configuration
		
		SMBConfigSection SMBConfig = (SMBConfigSection) m_serverConfig.getConfigSection(SMBConfigSection.SectionName);
		
		// Create the SMB mounter
		
		SMBMounter SMBMounter = new SMBMounter();
		SMBMounter.setServerName( getSMBServerName());
		
		// Set the server address if the global bind address has been set
		
		if ( SMBConfig.hasSMBBindAddress())
		{
			// Use the global SMB server bind address
			
			SMBMounter.setServerAddress( SMBConfig.getSMBBindAddress().getHostAddress());
		}
		
		// Get the local platform type
		
		PlatformType.Type platform = PlatformType.isPlatformType();
		
		// Figure out which SMB sub-protocol to use to connect to the server
		
		if ( platform == PlatformType.Type.LINUX && SMBConfig.hasTcpipSMB())
		{
			// Connect using native SMB, this defaults to port 445 but may be running on a different port
			
			SMBMounter.setProtocolType( SMBMounter.NativeSMB);
			
			// Check if the native SMB server is listening on a non-standard port
			
			if ( SMBConfig.getTcpipSMBPort() != TcpipSMB.PORT)
				SMBMounter.setProtocolPort( SMBConfig.getTcpipSMBPort());
		}
		else
		{
			// Check if the server is using Win32 NetBIOS
			
			if ( SMBConfig.hasWin32NetBIOS())
				SMBMounter.setProtocolType( SMBMounter.Win32NetBIOS);
			else if ( SMBConfig.hasNetBIOSSMB())
			{
				// Set the protocol type for Java socket based NetBIOS
				
				SMBMounter.setProtocolType( SMBMounter.NetBIOS);
				
				// Check if the socket NetBIOS is bound to a particular address
				
				if ( SMBConfig.hasNetBIOSBindAddress())
					SMBMounter.setServerAddress( SMBConfig.getNetBIOSBindAddress().getHostAddress());
			}
		}
		
		// Check if the SMB mounter server address has been set, if not then get the local address
		
		if ( SMBMounter.getServerAddress() == null)
		{
			// Set the SMB mounter server address
			
			try
			{
				SMBMounter.setServerAddress( InetAddress.getLocalHost().getHostAddress());
			}
			catch ( java.net.UnknownHostException ex)
			{
				logger.error( "Failed to get local IP address", ex);
			}
		}
		
		// Return the SMB mounter
		
		return SMBMounter;
	}
}
