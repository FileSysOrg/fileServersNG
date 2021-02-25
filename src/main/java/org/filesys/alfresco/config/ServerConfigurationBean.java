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
package org.filesys.alfresco.config;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.*;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.alfresco.AbstractServerConfigurationBean;
import org.filesys.alfresco.base.AlfrescoContext;
import org.filesys.alfresco.base.ExtendedDiskInterface;
import org.filesys.alfresco.config.acl.AccessControlListBean;
import org.filesys.alfresco.repo.*;
import org.filesys.alfresco.util.WINS;
import org.filesys.audit.Audit;
import org.filesys.audit.AuditGroup;
import org.filesys.debug.DebugInterface;
import org.filesys.debug.LogFileDebug;
import org.filesys.ftp.*;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.netbios.server.LANAMapper;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.acl.AccessControlList;
import org.filesys.server.auth.passthru.DomainMapping;
import org.filesys.server.auth.passthru.RangeDomainMapping;
import org.filesys.server.auth.passthru.SubnetDomainMapping;
import org.filesys.server.config.CoreServerConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.LicenceConfigSection;
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.core.ShareMapper;
import org.filesys.server.filesys.DiskSharedDevice;
import org.filesys.server.filesys.FilesystemsConfigSection;
import org.filesys.server.filesys.cache.FileStateLockManager;
import org.filesys.server.filesys.cache.StandaloneFileStateCache;
import org.filesys.server.filesys.cache.cluster.ClusterFileStateCache;
import org.filesys.server.filesys.cache.hazelcast.ClusterConfigSection;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.SMBV1VirtualCircuitList;
import org.filesys.smb.server.smbv2.config.SMBV2ConfigSection;
import org.filesys.smb.server.smbv2.config.SMBV3ConfigSection;
import org.filesys.util.IPAddress;
import org.filesys.util.MemorySize;
import org.filesys.util.PlatformType;
import org.filesys.util.X64;
import org.alfresco.repo.management.subsystems.ActivateableBean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.extensions.config.element.GenericConfigElement;


/**
 * Alfresco File Server Configuration Bean Class
 * <p>
 * Acts as an adaptor between the Java file server configuration requirements and the spring configuration of
 * the Alfresco filesystem subsystem.
 * <p>
 * Also contains an amount of initialisation logic. 
 * 
 * @author gkspencer
 * @author dward
 * @author mrogers
 */
public class ServerConfigurationBean extends AbstractServerConfigurationBean implements DisposableBean
{
    private SMBConfigBean smbConfigBean;
    private FTPConfigBean ftpConfigBean;
    private List<DeviceContext> filesystemContexts;
    private SecurityConfigBean securityConfigBean;
    private CoreServerConfigBean coreServerConfigBean;
    private LicenceConfigBean licenceConfigBean;
    private SMB2ConfigBean smb2ConfigBean;
    private SMB3ConfigBean smb3ConfigBean;
    private AuditConfigBean auditConfigBean;

    private ThreadRequestPool threadPool;
    protected ClusterConfigBean clusterConfigBean;

    // Memory pool settings for Enterprise file server
    private static final int[] EnterpriseMemoryPoolBufSizes  = { 256, 4096, 16384, 66000, 2048000 };
    private static final int[] EnterpriseMemoryPoolInitAlloc = {  20,   20,     5,     5,       5 };
    private static final int[] EnterpriseMemoryPoolMaxAlloc  = { 100,   50,    50,    50,      10 };

    /**
     * Default constructor
     */
    public ServerConfigurationBean()
    {
        super("");
    }

    /**
     * Class constructor
     * 
     * @param srvName String
     */
    public ServerConfigurationBean(String srvName)
    {
        super(srvName);
    }

    /**
     * Set the SMB server configuration bean
     *
     * @param smbConfigBean SMBConfigBean
     */
    public void setSmbConfigBean(SMBConfigBean smbConfigBean)
    {
        this.smbConfigBean = smbConfigBean;
    }

    /**
     * Set the FTP server configuration bean
     *
     * @param ftpConfigBean FTPConfigBean
     */
    public void setFtpConfigBean(FTPConfigBean ftpConfigBean)
    {
        this.ftpConfigBean = ftpConfigBean;
    }

    /**
     * Set the filesystem contexts
     *
     * @param filesystemContexts List<DeviceContext>
     */
    public void setFilesystemContexts(List<DeviceContext> filesystemContexts)
    {
        this.filesystemContexts = filesystemContexts;
    }

    /**
     * Set the security configuration bean
     *
     * @param securityConfigBean SecurityConfigBean
     */
    public void setSecurityConfigBean(SecurityConfigBean securityConfigBean)
    {
        this.securityConfigBean = securityConfigBean;
    }

    /**
     * Set the core server configuration bean
     *
     * @param coreServerConfigBean CoreServerConfigBean
     */
    public void setCoreServerConfigBean(CoreServerConfigBean coreServerConfigBean)
    {
        this.coreServerConfigBean = coreServerConfigBean;
    }

    /**
     * Set the cluster configuration bean
     *
     * @param clusterConfigBean ClusterConfigBean
     */
    public void setClusterConfigBean(ClusterConfigBean clusterConfigBean)
    {
        this.clusterConfigBean = clusterConfigBean;
    }

    /**
     * Set the licence configuration
     *
     * @param licenceBean LicenceConfigBean
     */
    public void setLicenceConfigBean(LicenceConfigBean licenceBean) {
        licenceConfigBean = licenceBean;
    }

    /**
     * Set the SMB2 configuration
     *
     * @param smb2Bean SMB2ConfigBean
     */
    public void setSmb2ConfigBean(SMB2ConfigBean smb2Bean) { smb2ConfigBean = smb2Bean; }

    /**
     * Set the SMB3 configuration
     *
     * @param smb3Bean SMB2ConfigBean
     */
    public void setSmb3ConfigBean(SMB3ConfigBean smb3Bean) { smb3ConfigBean = smb3Bean; }

    /**
     * Set the audit log configuration
     *
     * @param auditLogBean Audit
     */
    public void setAuditConfigBean(AuditConfigBean auditLogBean) { auditConfigBean = auditLogBean; }

    /**
     * Process the SMB server configuration
     */
    protected void processSMBServerConfig()
    {
        // If the configuration section is not valid then SMB is disabled

        if (smbConfigBean == null)
        {
            removeConfigSection(SMBConfigSection.SectionName);
            return;
        }

        // Check if the server has been disabled
        if (!smbConfigBean.getServerEnabled())
        {
            removeConfigSection(SMBConfigSection.SectionName);
            return;
        }

        // Before we go any further, let's make sure there's a compatible authenticator in the authentication chain.
        ISMBAuthenticator authenticator = smbConfigBean.getAuthenticator();
        if (authenticator == null || authenticator instanceof ActivateableBean && !((ActivateableBean)authenticator).isActive())
        {
            logger.error("No enabled SMB authenticator found in authentication chain. SMB Server disabled");
            removeConfigSection(SMBConfigSection.SectionName);
            return;
        }
            
        // Create the SMB server configuration section

        SMBConfigSection smbConfig = new SMBConfigSection(this);

        try
        {
            // Check if native code calls should be disabled on Windows
            if (smbConfigBean.getDisableNativeCode())
            {
                // Disable native code calls so that the JNI DLL is not required

                smbConfig.setNativeCodeDisabled(true);
                m_disableNativeCode = true;

                // Warning

                logger.warn("SMB server native calls disabled, JNI code will not be used");
            }

            // Get the network broadcast address
            //
            // Note: We need to set this first as the call to getLocalDomainName() may use a NetBIOS
            // name lookup, so the broadcast mask must be set before then.

            String broadcastAddess = smbConfigBean.getBroadcastAddress();
            if (broadcastAddess != null && broadcastAddess.length() > 0)
            {
                // Can be set to 'auto'
                if ( broadcastAddess.equalsIgnoreCase("auto") == false) {

                    // Check if the broadcast mask is a valid numeric IP address
                    if (IPAddress.isNumericAddress(broadcastAddess) == false) {
                        throw new AlfrescoRuntimeException("SMB Invalid broadcast mask, must be n.n.n.n format");
                    }
                }

                // Set the network broadcast mask
                smbConfig.setBroadcastMask(broadcastAddess);
            }

            // Get the terminal server address
            
            List<String> terminalServerList = smbConfigBean.getTerminalServerList();
            if (terminalServerList != null && terminalServerList.size() > 0)
            {
                // Check if the terminal server address is a valid numeric IP address
                for (String terminalServerAddress : terminalServerList)
                {
                    if (IPAddress.isNumericAddress(terminalServerAddress) == false)
                        throw new AlfrescoRuntimeException("Invalid terminal server address, must be n.n.n.n format");
                }
                // Set the terminal server address

                smbConfig.setTerminalServerList(terminalServerList);
            }

            // Get the load balancer address

            List<String> loadBalancerList = smbConfigBean.getLoadBalancerList();
            if (loadBalancerList != null && loadBalancerList.size() > 0)
            {
                // Check if the load balancer address is a valid numeric IP address
                for (String loadBalancerAddress : loadBalancerList)
                {
                    if (IPAddress.isNumericAddress(loadBalancerAddress) == false)
                        throw new AlfrescoRuntimeException("Invalid load balancer address, must be n.n.n.n format");
                }
                // Set the terminal server address

                smbConfig.setLoadBalancerList(loadBalancerList);
            }

            // Get the host configuration

            String hostName = smbConfigBean.getServerName();
            if (hostName == null || hostName.length() == 0)
            {
                throw new AlfrescoRuntimeException("SMB Host name not specified or invalid");
            }
            
            // Get the local server name

            String srvName = getLocalServerName(true);
            
            // Check if the host name contains the local name token

            int pos = hostName.indexOf(TokenLocalName);
            if (pos != -1)
            {
                // Rebuild the host name substituting the token with the local server name

                StringBuilder hostStr = new StringBuilder();

                hostStr.append(hostName.substring(0, pos));
                hostStr.append(srvName);

                pos += TokenLocalName.length();
                if (pos < hostName.length())
                {
                    hostStr.append(hostName.substring(pos));
                }
                
                hostName = hostStr.toString();
            }

            // Make sure the SMB server name does not match the local server name

            if (hostName.toUpperCase().equals(srvName.toUpperCase()) && getPlatformType() == PlatformType.Type.WINDOWS)
            {
                throw new AlfrescoRuntimeException("SMB server name must be unique");
            }

            // Check if the host name is longer than 15 characters. NetBIOS only allows a maximum of 16 characters in
            // the
            // server name with the last character reserved for the service type.

            if (hostName.length() > 15)
            {
                // Truncate the SMB server name

                hostName = hostName.substring(0, 15);

                // Output a warning

                logger.warn("SMB server name is longer than 15 characters, truncated to " + hostName);
            }

            // Set the SMB server name

            smbConfig.setServerName(hostName.toUpperCase());
            setServerName(hostName.toUpperCase());

            // Get the domain/workgroup name

            String domain = smbConfigBean.getDomainName();
            if (domain != null && domain.length() > 0)
            {
                // Set the domain/workgroup name

                smbConfig.setDomainName(domain.toUpperCase());
            }
            else
            {
                // Get the local domain/workgroup name

                String localDomain = getLocalDomainName();

                if (localDomain == null && (getPlatformType() != PlatformType.Type.WINDOWS || isNativeCodeDisabled()))
                {
                    // Use a default domain/workgroup name

                    localDomain = "WORKGROUP";

                    // Output a warning
                    logger.warn("SMB, Unable to get local domain/workgroup name, using default of " + localDomain + ". This may be due to firewall settings or incorrect <broadcast> setting)");
                }

                // Set the local domain/workgroup that the SMB server belongs to

                smbConfig.setDomainName(localDomain);
            }

            // Check for a server comment
            
            String comment = smbConfigBean.getServerComment();
            if (comment != null && comment.length() > 0)
            {
                smbConfig.setComment(comment);
            }

            // Set the maximum virtual circuits per session
            
            if ( smbConfigBean.getMaximumVirtualCircuits() < SMBV1VirtualCircuitList.MinCircuits ||
            		smbConfigBean.getMaximumVirtualCircuits() > SMBV1VirtualCircuitList.MaxCircuits)
            	throw new AlfrescoRuntimeException("Invalid virtual circuits value, valid range is " + SMBV1VirtualCircuitList.MinCircuits + " - " + SMBV1VirtualCircuitList.MaxCircuits);
            else
            	smbConfig.setMaximumVirtualCircuits( smbConfigBean.getMaximumVirtualCircuits());
            
            // Check for a bind address

            // Check if the network adapter name has been specified
            String bindToAdapter = smbConfigBean.getBindToAdapter();
            String bindTo;

            if (bindToAdapter != null && bindToAdapter.length() > 0)
            {

                // Get the IP address for the adapter

                InetAddress bindAddr = parseAdapterName(bindToAdapter);

                // Set the bind address for the server

                smbConfig.setSMBBindAddress(bindAddr);
            }
            else if ((bindTo = smbConfigBean.getBindToAddress()) != null && bindTo.length() > 0
                    && !bindTo.equals(BIND_TO_IGNORE))
            {

                // Validate the bind address
                try
                {
                    // Check the bind address

                    InetAddress bindAddr = InetAddress.getByName(bindTo);

                    // Set the bind address for the server

                    smbConfig.setSMBBindAddress(bindAddr);
                }
                catch (UnknownHostException ex)
                {
                    throw new AlfrescoRuntimeException("SMB Unable to bind to address :" + bindTo, ex);
                }
            }

            // Get the authenticator

            if (authenticator != null)
            {
                smbConfig.setAuthenticator(authenticator);
            }
            else
            {
                throw new AlfrescoRuntimeException("SMB authenticator not specified");
            }
            
            // Check if the host announcer has been disabled
            
            if (!smbConfigBean.getHostAccouncerEnabled())
            {
                // Switch off the host announcer
                
                smbConfig.setHostAnnouncer( false);
                
                // Log that host announcements are not enabled
                
                logger.info("SMB Host announcements not enabled");
            }
            else
            {
                // Check for an announcement interval
      
                Integer interval = smbConfigBean.getHostAccounceInterval();
                if (interval != null)
                {
                    smbConfig.setHostAnnounceInterval(interval);
                }
      
                // Check if the domain name has been set, this is required if the
                // host announcer is enabled
      
                if (smbConfig.getDomainName() == null)
                {
                    throw new AlfrescoRuntimeException("SMB Domain name must be specified if host announcement is enabled");
                }
                
                // Enable host announcement
      
                smbConfig.setHostAnnouncer(true);
            }

            // Check if NetBIOS SMB is enabled
            NetBIOSSMBConfigBean netBIOSSMBConfigBean = smbConfigBean.getNetBIOSSMB();
            if (netBIOSSMBConfigBean != null)
            {
                // Check if NetBIOS over TCP/IP is enabled for the current platform

                String platformsStr = netBIOSSMBConfigBean.getPlatforms();
                boolean platformOK = false;

                if (platformsStr != null && platformsStr.length() > 0)
                {
                    // Parse the list of platforms that NetBIOS over TCP/IP is to be enabled for and
                    // check if the current platform is included

                    EnumSet<PlatformType.Type> enabledPlatforms = parsePlatformString(platformsStr);
                    if (enabledPlatforms.contains(getPlatformType()))
                        platformOK = true;
                }
                else
                {
                    // No restriction on platforms

                    platformOK = true;
                }

                // Enable the NetBIOS SMB support, if enabled for this platform

                smbConfig.setNetBIOSSMB(platformOK);

                // Parse/check NetBIOS settings, if enabled

                if (smbConfig.hasNetBIOSSMB())
                {
                    // Check if the broadcast mask has been specified

                    if (smbConfig.getBroadcastMask() == null)
                    {
                        throw new AlfrescoRuntimeException("SMB Network broadcast mask not specified");
                    }
                    
                    // Check for a bind address

                    String bindto = netBIOSSMBConfigBean.getBindTo();
                    if (bindto != null && bindto.length() > 0 && !bindto.equals(BIND_TO_IGNORE))
                    {

                        // Validate the bind address

                        try
                        {

                            // Check the bind address

                            InetAddress bindAddr = InetAddress.getByName(bindto);

                            // Set the bind address for the NetBIOS name server

                            smbConfig.setNetBIOSBindAddress(bindAddr);
                        }
                        catch (UnknownHostException ex)
                        {
                            throw new AlfrescoRuntimeException("SMB Invalid NetBIOS bind address:" + bindto, ex);
                        }
                    }
                    else if (smbConfig.hasSMBBindAddress())
                    {

                        // Use the SMB bind address for the NetBIOS name server

                        smbConfig.setNetBIOSBindAddress(smbConfig.getSMBBindAddress());
                    }
                    else
                    {
                        // Get a list of all the local addresses

                        InetAddress[] addrs = null;

                        try
                        {
                            // Get the local server IP address list

                            addrs = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
                        }
                        catch (UnknownHostException ex)
                        {
                            logger.error("SMB Failed to get local address list", ex);
                        }

                        // Check the address list for one or more valid local addresses filtering out the loopback
                        // address

                        int addrCnt = 0;

                        if (addrs != null)
                        {
                            for (int i = 0; i < addrs.length; i++)
                            {

                                // Check for a valid address, filter out '127.0.0.1' and '0.0.0.0' addresses

                                if (addrs[i].getHostAddress().equals("127.0.0.1") == false
                                        && addrs[i].getHostAddress().equals("0.0.0.0") == false)
                                    addrCnt++;
                            }
                        }

                        // Check if any addresses were found

                        if (addrCnt == 0)
                        {
                            // Enumerate the network adapter list

                            Enumeration<NetworkInterface> niEnum = null;

                            try
                            {
                                niEnum = NetworkInterface.getNetworkInterfaces();
                            }
                            catch (SocketException ex)
                            {
                            }

                            if (niEnum != null)
                            {
                                while (niEnum.hasMoreElements())
                                {
                                    // Get the current network interface

                                    NetworkInterface ni = niEnum.nextElement();

                                    // Enumerate the addresses for the network adapter

                                    Enumeration<InetAddress> niAddrs = ni.getInetAddresses();
                                    if (niAddrs != null)
                                    {
                                        // Check for any valid addresses

                                        while (niAddrs.hasMoreElements())
                                        {
                                            InetAddress curAddr = niAddrs.nextElement();

                                            if (curAddr.getHostAddress().equals("127.0.0.1") == false
                                                    && curAddr.getHostAddress().equals("0.0.0.0") == false)
                                                addrCnt++;
                                        }
                                    }
                                }

                                // DEBUG

                                if (addrCnt > 0 && logger.isDebugEnabled())
                                    logger.debug("Found valid IP address from interface list");
                            }

                            // Check if we found any valid network addresses

                            if (addrCnt == 0)
                            {
                                // Log the available IP addresses

                                if (logger.isDebugEnabled())
                                {
                                    logger.debug("Local address list dump :-");
                                    if (addrs != null)
                                    {
                                        for (int i = 0; i < addrs.length; i++)
                                            logger.debug("  Address: " + addrs[i]);
                                    }
                                    else
                                    {
                                        logger.debug("  No addresses");
                                    }
                                }

                                // Throw an exception to stop the SMB/NetBIOS name server from starting

                                throw new AlfrescoRuntimeException(
                                        "Failed to get IP address(es) for the local server, check hosts file and/or DNS setup");
                            }
                        }
                    }

                    // Check if the session port has been specified

                    Integer portNum = netBIOSSMBConfigBean.getSessionPort();
                    if (portNum != null)
                    {
                        smbConfig.setSessionPort(portNum);
                        if (smbConfig.getSessionPort() <= 0 || smbConfig.getSessionPort() >= 65535)
                            throw new AlfrescoRuntimeException("NetBIOS session port out of valid range");
                    }

                    // Check if the name port has been specified

                    portNum = netBIOSSMBConfigBean.getNamePort();
                    if (portNum != null)
                    {
                        smbConfig.setNameServerPort(portNum);
                        if (smbConfig.getNameServerPort() <= 0 || smbConfig.getNameServerPort() >= 65535)
                            throw new AlfrescoRuntimeException("NetBIOS name port out of valid range");
                    }

                    // Check if the datagram port has been specified

                    portNum = netBIOSSMBConfigBean.getDatagramPort();
                    if (portNum != null)
                    {
                        smbConfig.setDatagramPort(portNum);
                        if (smbConfig.getDatagramPort() <= 0 || smbConfig.getDatagramPort() >= 65535)
                            throw new AlfrescoRuntimeException("NetBIOS datagram port out of valid range");
                    }

                    // Check for a bind address

                    String attr = netBIOSSMBConfigBean.getBindTo();
                    if (attr != null && attr.length() > 0 && !attr.equals(BIND_TO_IGNORE))
                    {

                        // Validate the bind address

                        try
                        {

                            // Check the bind address

                            InetAddress bindAddr = InetAddress.getByName(attr);

                            // Set the bind address for the NetBIOS name server

                            smbConfig.setNetBIOSBindAddress(bindAddr);
                        }
                        catch (UnknownHostException ex)
                        {
                            throw new InvalidConfigurationException(ex.toString());
                        }
                    }

                    // Check for a bind address using the adapter name

                    else if ((attr = netBIOSSMBConfigBean.getAdapter()) != null && attr.length() > 0)
                    {

                        // Get the bind address via the network adapter name

                        InetAddress bindAddr = parseAdapterName(attr);
                        smbConfig.setNetBIOSBindAddress(bindAddr);
                    }
                    else if (smbConfig.hasSMBBindAddress())
                    {

                        // Use the SMB bind address for the NetBIOS name server

                        smbConfig.setNetBIOSBindAddress(smbConfig.getSMBBindAddress());
                    }

                }
            }
            else
            {

                // Disable NetBIOS SMB support

                smbConfig.setNetBIOSSMB(false);
            }

            // Check if TCP/IP SMB is enabled

            TcpipSMBConfigBean tcpipSMBConfigBean = smbConfigBean.getTcpipSMB();
            if (tcpipSMBConfigBean != null)
            {

                // Check if native SMB is enabled for the current platform

                String platformsStr = tcpipSMBConfigBean.getPlatforms();
                boolean platformOK = false;

                if (platformsStr != null)
                {
                    // Parse the list of platforms that native SMB is to be enabled for and
                    // check if the current platform is included

                    EnumSet<PlatformType.Type> enabledPlatforms = parsePlatformString(platformsStr);
                    if (enabledPlatforms.contains(getPlatformType()))
                        platformOK = true;
                }
                else
                {
                    // No restriction on platforms

                    platformOK = true;
                }

                // Enable the TCP/IP SMB support, if enabled for this platform

                smbConfig.setTcpipSMB(platformOK);

                // Check if the port has been specified

                Integer portNum = tcpipSMBConfigBean.getPort();
                if (portNum != null)
                {
                    smbConfig.setTcpipSMBPort(portNum);
                    if (smbConfig.getTcpipSMBPort() <= 0 || smbConfig.getTcpipSMBPort() >= 65535)
                        throw new AlfrescoRuntimeException("TCP/IP SMB port out of valid range");
                }
                
                // Check if IPv6 support should be enabled
                
                if ( tcpipSMBConfigBean.getIpv6Enabled())
                {
                    try
                    {
                        // Use the IPv6 bind all address
                        
                        smbConfig.setSMBBindAddress( InetAddress.getByName( "::"));
                        
                        // DEBUG
                        
                        if ( logger.isInfoEnabled())
                        {
                            logger.info("Enabled SMB IPv6 bind address for native SMB");
                    
                        }
                    }
                    catch ( UnknownHostException ex)
                    {
                        throw new AlfrescoRuntimeException("SMB Failed to enable IPv6 bind address, " + ex.getMessage());
                    }
                }
                
            }
            else
            {

                // Disable TCP/IP SMB support

                smbConfig.setTcpipSMB(false);
            }

            // Check if Win32 NetBIOS is enabled

            Win32NetBIOSConfigBean win32NetBIOSConfigBean = smbConfigBean.getWin32NetBIOS();
            if (win32NetBIOSConfigBean != null)
            {
                // Check if the Win32 NetBIOS classes are available
                LANAMapper lanaMapper = null;
                boolean win32Available = false;

                try {
                    lanaMapper = (LANAMapper) Class.forName("org.filesys.netbios.win32.Win32NetBIOS").newInstance();
                    win32Available = true;
                }
                catch (IllegalAccessException ex) {
                }
                catch (InstantiationException ex) {
                }
                catch (ClassNotFoundException ex) {
                }

                if ( win32Available == false || lanaMapper == null) {

                    // Disable Win32 NetBIOS
                    smbConfig.setWin32NetBIOS(false);

                    logger.warn("Win32NetBIOS classes not available");
                }
                else {

                    // Check if the Win32 NetBIOS server name has been specified

                    String win32Name = win32NetBIOSConfigBean.getName();
                    if (win32Name != null && win32Name.length() > 0) {

                        // Validate the name

                        if (win32Name.length() > 16)
                            throw new AlfrescoRuntimeException("Invalid Win32 NetBIOS name, " + win32Name);

                        // Set the Win32 NetBIOS file server name

                        smbConfig.setWin32NetBIOSName(win32Name);
                    }

                    // Check if the Win32 NetBIOS LANA has been specified

                    String lanaStr = win32NetBIOSConfigBean.getLana();
                    if (lanaStr != null && lanaStr.length() > 0) {
                        // Check if the LANA has been specified as an IP address or adapter name

                        int lana = -1;

                        if (IPAddress.isNumericAddress(lanaStr)) {

                            // Convert the IP address to a LANA id

                            lana = lanaMapper.getLANAForIPAddress(lanaStr);
                            if (lana == -1)
                                throw new AlfrescoRuntimeException("Failed to convert IP address " + lanaStr + " to a LANA");
                        } else if (lanaStr.length() > 1 && Character.isLetter(lanaStr.charAt(0))) {

                            // Convert the network adapter to a LANA id

                            lana = lanaMapper.getLANAForAdapterName(lanaStr);
                            if (lana == -1)
                                throw new AlfrescoRuntimeException("Failed to convert network adapter " + lanaStr
                                        + " to a LANA");
                        } else {

                            try {
                                lana = Integer.parseInt(lanaStr);
                            } catch (NumberFormatException ex) {
                                throw new AlfrescoRuntimeException("Invalid win32 NetBIOS LANA specified");
                            }
                        }

                        // LANA should be in the range 0-255

                        if (lana < 0 || lana > 255)
                            throw new AlfrescoRuntimeException("Invalid Win32 NetBIOS LANA number, " + lana);

                        // Set the LANA number

                        smbConfig.setWin32LANA(lana);
                    }

                    // Check if the native NetBIOS interface has been specified, either 'winsock' or 'netbios'

                    String nativeAPI = win32NetBIOSConfigBean.getApi();
                    if (nativeAPI != null && nativeAPI.length() > 0) {
                        // Validate the API type

                        boolean useWinsock = true;

                        if (nativeAPI.equalsIgnoreCase("netbios"))
                            useWinsock = false;
                        else if (nativeAPI.equalsIgnoreCase("winsock") == false)
                            throw new AlfrescoRuntimeException("Invalid NetBIOS API type, spefify 'winsock' or 'netbios'");

                        // Set the NetBIOS API to use

                        smbConfig.setWin32WinsockNetBIOS(useWinsock);
                    }

                    // Force the older NetBIOS API code to be used on 64Bit Windows

                    if (smbConfig.useWinsockNetBIOS() == true && X64.isWindows64()) {
                        // Debug

                        if (logger.isDebugEnabled())
                            logger.debug("Using older Netbios() API code");

                        // Use the older NetBIOS API code

                        smbConfig.setWin32WinsockNetBIOS(false);
                    }

                    // Check if the current operating system is supported by the Win32 NetBIOS handler

                    String osName = System.getProperty("os.name");
                    if (osName.startsWith("Windows")
                            && (osName.endsWith("95") == false && osName.endsWith("98") == false && osName.endsWith("ME") == false)
                            && isNativeCodeDisabled() == false) {

                        // Enable Win32 NetBIOS

                        smbConfig.setWin32NetBIOS(true);

                    } else {

                        // Win32 NetBIOS not supported on the current operating system

                        smbConfig.setWin32NetBIOS(false);
                    }
                }
            }
            else
            {

                // Disable Win32 NetBIOS

                smbConfig.setWin32NetBIOS(false);
            }

            // Check if the Win32 host announcer has been disabled
            
            if ( !smbConfigBean.getWin32HostAnnouncerEnabled())
            {
                // Switch off the Win32 host announcer
                
                smbConfig.setWin32HostAnnouncer( false);
                
                // Log that host announcements are not enabled
                
                logger.info("Win32 host announcements not enabled");
            }
            else
            {
                // Check for an announcement interval
                Integer interval = smbConfigBean.getWin32HostAnnounceInterval();
                if (interval != null)
                {
                    smbConfig.setWin32HostAnnounceInterval(interval);
                }

                // Check if the domain name has been set, this is required if the
                // host announcer is enabled

                if (smbConfig.getDomainName() == null)
                    throw new AlfrescoRuntimeException("Domain name must be specified if host announcement is enabled");

                // Enable Win32 NetBIOS host announcement

                smbConfig.setWin32HostAnnouncer(true);
            }

            // Check if NetBIOS and/or TCP/IP SMB have been enabled

            if (smbConfig.hasNetBIOSSMB() == false && smbConfig.hasTcpipSMB() == false
                    && smbConfig.hasWin32NetBIOS() == false)
                throw new AlfrescoRuntimeException("NetBIOS SMB, TCP/IP SMB or Win32 NetBIOS must be enabled");

            // Check if WINS servers are configured

            WINSConfigBean winsConfigBean = smbConfigBean.getWINSConfig();

            if (winsConfigBean != null && !winsConfigBean.isAutoDetectEnabled())
            {

                // Get the primary WINS server

                String priWins = winsConfigBean.getPrimary();

                if (priWins == null || priWins.length() == 0)
                    throw new AlfrescoRuntimeException("No primary WINS server configured");

                // Validate the WINS server address

                InetAddress primaryWINS = null;

                try
                {
                    primaryWINS = InetAddress.getByName(priWins);
                }
                catch (UnknownHostException ex)
                {
                    throw new AlfrescoRuntimeException("Invalid primary WINS server address, " + priWins);
                }

                // Check if a secondary WINS server has been specified

                String secWins = winsConfigBean.getSecondary();
                InetAddress secondaryWINS = null;

                if (secWins != null && secWins.length() > 0)
                {

                    // Validate the secondary WINS server address

                    try
                    {
                        secondaryWINS = InetAddress.getByName(secWins);
                    }
                    catch (UnknownHostException ex)
                    {
                        throw new AlfrescoRuntimeException("Invalid secondary WINS server address, " + secWins);
                    }
                }

                // Set the WINS server address(es)

                smbConfig.setPrimaryWINSServer(primaryWINS);
                if (secondaryWINS != null)
                    smbConfig.setSecondaryWINSServer(secondaryWINS);

                // Pass the setting to the NetBIOS session class

                NetBIOSSession.setDefaultWINSServer(primaryWINS);
            }

            // Check if WINS is configured, if we are running on Windows and socket based NetBIOS is enabled

            else if (smbConfig.hasNetBIOSSMB() && getPlatformType() == PlatformType.Type.WINDOWS && !isNativeCodeDisabled())
            {
                // Get the WINS server list

                String winsServers = WINS.getWINSServerList();

                if (winsServers != null)
                {
                    // Use the first WINS server address for now

                    StringTokenizer tokens = new StringTokenizer(winsServers, ",");
                    String addr = tokens.nextToken();

                    try
                    {
                        // Convert to a network address and check if the WINS server is accessible

                        InetAddress winsAddr = InetAddress.getByName(addr);

                        Socket winsSocket = new Socket();
                        InetSocketAddress sockAddr = new InetSocketAddress(winsAddr, RFCNetBIOSProtocol.NAMING);

                        winsSocket.connect(sockAddr, 3000);
                        winsSocket.close();

                        // Set the primary WINS server address

                        smbConfig.setPrimaryWINSServer(winsAddr);

                        // Debug

                        if (logger.isDebugEnabled())
                            logger.debug("Configuring to use WINS server " + addr);
                    }
                    catch (UnknownHostException ex)
                    {
                        throw new AlfrescoRuntimeException("Invalid auto WINS server address, " + addr);
                    }
                    catch (IOException ex)
                    {
                        if (logger.isDebugEnabled())
                            logger.debug("Failed to connect to auto WINS server " + addr);
                    }
                }
            }

            // Check if the enabled dialect list has been set
            if  (smbConfigBean.getEnabledDialects() != null) {

                // Set the enabled SMB dialects
                DialectSelector enaDialects = smbConfigBean.getEnabledDialects();
                smbConfig.setEnabledDialects( enaDialects);
            }

            // Check for session debug flags
            EnumSet<SMBSrvSession.Dbg> smbDbg = EnumSet.<SMBSrvSession.Dbg>noneOf( SMBSrvSession.Dbg.class);
            String flags = smbConfigBean.getSessionDebugFlags();

            if ( flags != null) {

                // Parse the flags
                flags = flags.toUpperCase();
                StringTokenizer token = new StringTokenizer(flags, ",");

                while (token.hasMoreTokens()) {

                    // Get the current debug flag token
                    String dbg = token.nextToken().trim();

                    // Convert the debug flag name to an enum value
                    try {
                        smbDbg.add(SMBSrvSession.Dbg.valueOf(dbg));
                    }
                    catch ( IllegalArgumentException ex) {
                        throw new AlfrescoRuntimeException("Invalid session debug flag, " + dbg);
                    }
                }
            }

            // Set the session debug flags
            smbConfig.setSessionDebugFlags(smbDbg);

            // Check if NIO based socket code should be disabled

            if (smbConfigBean.getDisableNIO())
            {

                // Disable NIO based code

                smbConfig.setDisableNIOCode(true);

                // DEBUG

                if (logger.isDebugEnabled())
                    logger.debug("NIO based code disabled for SMB server");
            }
            
            
            // Check if a session timeout is configured
            
            Integer tmo = smbConfigBean.getSessionTimeout();
            if (tmo != null)
            {

                // Validate the session timeout value

                smbConfigBean.validateSessionTimeout(tmo);

                // Convert the session timeout to milliseconds

                smbConfig.setSocketTimeout(tmo * 1000);
            }

            // Check for SMB2 specific configuration settings
            if ( smb2ConfigBean != null) {

                // Create the SMB2 configuration section
                SMBV2ConfigSection smb2Config = new SMBV2ConfigSection(this);

                // Set the maximum packet size to use for SMB2 requests/responses
                if ( smb2ConfigBean.getMaxPacketSize() > 0)
                    smb2Config.setMaximumPacketSize( smb2ConfigBean.getMaxPacketSize());

                // Check if signing is required
                smb2Config.setPacketSigningRequired(smbConfigBean.getRequireSigning());
            }

            // Check for SMB3 specific configuration settings
            if ( smb3ConfigBean != null) {

                // Create the SMB3 configuration section
                SMBV3ConfigSection smb3Config = new SMBV3ConfigSection(this);

                // Set the primary and secondary encryption types/order
                smb3Config.setPrimaryEncryptionType( smb3ConfigBean.getPrimaryEncryptionType());
                smb3Config.setSecondaryEncryptionType( smb3ConfigBean.getSecondaryEncryptionType());

                // Set the SMB3 encryption disabled flag
                smb3Config.setDisableEncryption( smb3ConfigBean.getDisableEncryption());
            }
        }
        catch (InvalidConfigurationException ex)
        {
            throw new AlfrescoRuntimeException(ex.getMessage());
        }
    }

    /**
     * Process the FTP server configuration
     */
    protected void processFTPServerConfig()
    {
        // If the configuration section is not valid then FTP is disabled

        if (ftpConfigBean == null)
        {
            removeConfigSection(FTPConfigSection.SectionName);
            return;
        }

        // Check if the server has been disabled

        if (!ftpConfigBean.getServerEnabled())
        {
            removeConfigSection(FTPConfigSection.SectionName);
            return;
        }

        // Create the FTP configuration section

        FTPConfigSection ftpConfig = new FTPConfigSection(this);

        try
        {
            // Check for a bind address

            String bindText = ftpConfigBean.getBindTo();
            if (bindText != null && bindText.length() > 0 && !bindText.equals(BIND_TO_IGNORE))
            {

                // Validate the bind address

                try
                {

                    // Check the bind address

                    InetAddress bindAddr = InetAddress.getByName(bindText);

                    // Set the bind address for the FTP server

                    ftpConfig.setFTPBindAddress(bindAddr);
                }
                catch (UnknownHostException ex)
                {
                    throw new AlfrescoRuntimeException("Unable to find FTP bindto address, " + bindText, ex);
                }
            }

            // Check for an FTP server port

            Integer port = ftpConfigBean.getPort();
            if (port != null)
            {
                ftpConfig.setFTPPort(port);
                if (ftpConfig.getFTPPort() <= 0 || ftpConfig.getFTPPort() >= 65535)
                    throw new AlfrescoRuntimeException("FTP server port out of valid range");
            }
            else
            {

                // Use the default FTP port

                ftpConfig.setFTPPort(DefaultFTPServerPort);
            }

            // Check for an FTP server timeout for connection to client
            Integer sessionTimeout = ftpConfigBean.getSessionTimeout();
            if (sessionTimeout != null)
            {
                ftpConfig.setFTPSrvSessionTimeout(sessionTimeout);
                if (ftpConfig.getFTPSrvSessionTimeout() < 0)
                    throw new AlfrescoRuntimeException("FTP server session timeout must have positive value or zero");
            }
            else
            {

                // Use the default timeout

                ftpConfig.setFTPSrvSessionTimeout(DefaultFTPSrvSessionTimeout);
            }

            // Check if anonymous login is allowed

            if (ftpConfigBean.getAllowAnonymous())
            {

                // Enable anonymous login to the FTP server

                ftpConfig.setAllowAnonymousFTP(true);

                // Check if an anonymous account has been specified

                String anonAcc = ftpConfigBean.getAnonymousAccount();
                if (anonAcc != null && anonAcc.length() > 0)
                {

                    // Set the anonymous account name

                    ftpConfig.setAnonymousFTPAccount(anonAcc);

                    // Check if the anonymous account name is valid

                    if (ftpConfig.getAnonymousFTPAccount() == null || ftpConfig.getAnonymousFTPAccount().length() == 0)
                    {
                        throw new AlfrescoRuntimeException("Anonymous FTP account invalid");
                    }
                }
                else
                {

                    // Use the default anonymous account name

                    ftpConfig.setAnonymousFTPAccount(DefaultFTPAnonymousAccount);
                }
            }
            else
            {

                // Disable anonymous logins

                ftpConfig.setAllowAnonymousFTP(false);
            }

            // Check if a root path has been specified

            String rootPath = ftpConfigBean.getRootDirectory();
            if (rootPath != null && rootPath.length() > 0)
            {
                try
                {

                    // Parse the path

                    new FTPPath(rootPath);

                    // Set the root path

                    ftpConfig.setFTPRootPath(rootPath);
                }
                catch (InvalidPathException ex)
                {
                    throw new AlfrescoRuntimeException("Invalid FTP root directory, " + rootPath);
                }
            }

            // Check for FTP debug flags

            String flags = ftpConfigBean.getDebugFlags();
            EnumSet<FTPSrvSession.Dbg> ftpDbg = EnumSet.<FTPSrvSession.Dbg>noneOf( FTPSrvSession.Dbg.class);

            if ( flags != null) {

                // Parse the flags
                flags = flags.toUpperCase();
                StringTokenizer token = new StringTokenizer(flags, ",");

                while (token.hasMoreTokens()) {

                    // Get the current debug flag token
                    String dbg = token.nextToken().trim();

                    // Convert the debug flag name to an enum value
                    try {
                        ftpDbg.add(FTPSrvSession.Dbg.valueOf(dbg));
                    }
                    catch ( IllegalArgumentException ex) {
                        throw new AlfrescoRuntimeException("Invalid FTP debug flag, " + dbg);
                    }
                }
            }

            // Set the FTP debug flags
            ftpConfig.setFTPDebug(ftpDbg);

            // Check if a character set has been specified

            String charSet = ftpConfigBean.getCharSet();
            if (charSet != null && charSet.length() > 0)
            {

                try
                {

                    // Validate the character set name

                    Charset.forName(charSet);

                    // Set the FTP character set

                    ftpConfig.setFTPCharacterSet(charSet);
                }
                catch (IllegalCharsetNameException ex)
                {
                    throw new AlfrescoRuntimeException("FTP Illegal character set name, " + charSet);
                }
                catch (UnsupportedCharsetException ex)
                {
                    throw new AlfrescoRuntimeException("FTP Unsupported character set name, " + charSet);
                }
            }

            // Check if an authenticator has been specified

            FTPAuthenticator auth = ftpConfigBean.getAuthenticator();
            if (auth != null)
            {

                // Initialize and set the authenticator class

                ftpConfig.setAuthenticator(auth);
            }
            else
                throw new AlfrescoRuntimeException("FTP authenticator not specified");

            // Check if a data port range has been specified
            
            if ( ftpConfigBean.getDataPortFrom() != 0 && ftpConfigBean.getDataPortTo() != 0) {
            	
            	// Range check the data port values
            	
            	int rangeFrom = ftpConfigBean.getDataPortFrom();
            	int rangeTo   = ftpConfigBean.getDataPortTo();
            	
            	if ( rangeFrom != 0 && rangeTo != 0) {
            		
            		// Validate the FTP data port range
            	
	    			if ( rangeFrom < 1024 || rangeFrom > 65535)
	    				throw new InvalidConfigurationException("Invalid FTP data port range from value, " + rangeFrom);
	
	    			if ( rangeTo < 1024 || rangeTo > 65535)
	    				throw new InvalidConfigurationException("Invalid FTP data port range to value, " + rangeTo);
	
	    			if ( rangeFrom >= rangeTo)
	    				throw new InvalidConfigurationException("Invalid FTP data port range, " + rangeFrom + "-" + rangeTo);
	
	    			// Set the FTP data port range
	
	    			ftpConfig.setFTPDataPortLow(rangeFrom);
	    			ftpConfig.setFTPDataPortHigh(rangeTo);
	    			
	    			// Log the data port range
	    			
	    			logger.info("FTP server data ports restricted to range " + rangeFrom + ":" + rangeTo);
            	}
            }
            
    		// FTPS parameter parsing
    		//
    		// Check if a key store path has been specified
    		
    		if ( ftpConfigBean.getKeyStorePath() != null && ftpConfigBean.getKeyStorePath().length() > 0) {

    			// Get the path to the key store, check that the file exists

    			String keyStorePath = ftpConfigBean.getKeyStorePath();
    			File keyStoreFile = new File( keyStorePath);
    			
    			if ( keyStoreFile.exists() == false)
    				throw new InvalidConfigurationException("FTPS key store file does not exist, " + keyStorePath);
    			else if ( keyStoreFile.isDirectory())
    				throw new InvalidConfigurationException("FTPS key store path is a directory, " + keyStorePath);
    			
    			// Set the key store path
    			
    			ftpConfig.setKeyStorePath( keyStorePath);
    		
	    		// Check if the key store type has been specified
	    		
	    		if ( ftpConfigBean.getKeyStoreType() != null && ftpConfigBean.getKeyStoreType().length() > 0) {
	    			
	    			// Get the key store type, and validate
	    			
	    			String keyStoreType = ftpConfigBean.getKeyStoreType();
	    			
	    			if ( keyStoreType == null || keyStoreType.length() == 0)
	    				throw new InvalidConfigurationException("FTPS key store type is invalid");
	    			
	    			try {
	    				KeyStore.getInstance( keyStoreType);
	    			}
	    			catch ( KeyStoreException ex) {
	    				throw new InvalidConfigurationException("FTPS key store type is invalid, " + keyStoreType, ex);
	    			}
	    			
	    			// Set the key store type
	    			
	    			ftpConfig.setKeyStoreType( keyStoreType);
	    		}

	    		// Check if the key store passphrase has been specified
	    		
	    		if ( ftpConfigBean.getKeyStorePassphrase() != null && ftpConfigBean.getKeyStorePassphrase().length() > 0) {

	    			// Set the key store passphrase
	    			
	    			ftpConfig.setKeyStorePassphrase( ftpConfigBean.getKeyStorePassphrase());
	    		}
    		}
    		
    		// Check if the trust store path has been specified
    		
    		if ( ftpConfigBean.getTrustStorePath() != null && ftpConfigBean.getTrustStorePath().length() > 0) {

    			// Get the path to the trust store, check that the file exists
    			
    			String trustStorePath = ftpConfigBean.getTrustStorePath();
    			File trustStoreFile = new File( trustStorePath);
    			
    			if ( trustStoreFile.exists() == false)
    				throw new InvalidConfigurationException("FTPS trust store file does not exist, " + trustStorePath);
    			else if ( trustStoreFile.isDirectory())
    				throw new InvalidConfigurationException("FTPS trust store path is a directory, " + trustStorePath);
    			
    			// Set the trust store path
    			
    			ftpConfig.setTrustStorePath( trustStorePath);

	    		// Check if the trust store type has been specified
	    		
	    		if ( ftpConfigBean.getTrustStoreType() != null && ftpConfigBean.getTrustStoreType().length() > 0) {
	    			
	    			// Get the trust store type, and validate
	    			
	    			String trustStoreType = ftpConfigBean.getTrustStoreType();
	    			
	    			if ( trustStoreType == null || trustStoreType.length() == 0)
	    				throw new InvalidConfigurationException("FTPS trust store type is invalid");
	    			
	    			try {
	    				KeyStore.getInstance( trustStoreType);
	    			}
	    			catch ( KeyStoreException ex) {
	    				throw new InvalidConfigurationException("FTPS trust store type is invalid, " + trustStoreType, ex);
	    			}
	    			
	    			// Set the trust store type
	    			
	    			ftpConfig.setTrustStoreType( trustStoreType);
	    		}
    		
	    		// Check if the trust store passphrase has been specified
	    		
	    		if ( ftpConfigBean.getTrustStorePassphrase() != null && ftpConfigBean.getTrustStorePassphrase().length() > 0) {
	
	    			// Set the trust store passphrase
	    			
	    			ftpConfig.setTrustStorePassphrase( ftpConfigBean.getTrustStorePassphrase());
	    		}
    		}
    		
    		// Check if only secure sessions should be allowed to logon
    		
    		if ( ftpConfigBean.hasRequireSecureSession()) {

    			// Only allow secure sessions to logon to the FTP server

    			ftpConfig.setRequireSecureSession( true);
    		}
    		
    		// Check that all the required FTPS parameters have been set
    		// MNT-7301 FTPS server requires unnecessarly to have a trustStore while a keyStore should be sufficient
    		if ( ftpConfig.getKeyStorePath() != null) {
    			
    			// Make sure all parameters are set
    			
    			if ( ftpConfig.getKeyStorePath() == null)
    				throw new InvalidConfigurationException("FTPS configuration requires keyStore to be set");
    		}
    		
    		// Check if SSLEngine debug output should be enabled
    		
    		if ( ftpConfigBean.hasSslEngineDebug()) {

    			// Enable SSLEngine debug output

    			System.setProperty("javax.net.debug", "ssl,handshake");
    		}
        }
        catch (InvalidConfigurationException ex)
        {
            throw new AlfrescoRuntimeException(ex.getMessage());
        }
    }

    /**
     * Process the filesystems configuration
     */
    protected void processFilesystemsConfig()
    {
        // Create the filesystems configuration section

        FilesystemsConfigSection fsysConfig = new FilesystemsConfigSection(this);

        // Access the security configuration section

        SecurityConfigSection secConfig = (SecurityConfigSection) getConfigSection(SecurityConfigSection.SectionName);

        // Process the filesystems list

        if (this.filesystemContexts != null)
        {

            // Add the filesystems

            for (DeviceContext filesystem : this.filesystemContexts)
            {

                // Get the current filesystem configuration

                try
                {
                    // Check the filesystem type and use the appropriate driver

                    DiskSharedDevice filesys = null;

                    // Create a new filesystem driver instance and register a context for
                    // the new filesystem

                    ExtendedDiskInterface filesysDriver = getRepoDiskInterface();
                    ContentContext filesysContext = (ContentContext) filesystem;
                    
                    if(clusterConfigBean != null && clusterConfigBean.getClusterEnabled())
                    {
                        if(logger.isDebugEnabled())
                        {
                            logger.debug("start hazelcast cache : " + clusterConfigBean.getClusterName() + ", shareName: "+ filesysContext.getShareName());
                        }
                        GenericConfigElement hazelConfig = createClusterConfig("SMB.filesys."+filesysContext.getShareName());
                        ClusterFileStateCache hazel = null;

                        try {
                            hazel = (ClusterFileStateCache) Class.forName("org.filesys.server.filesys.cache.hazelcast.HazelCastClusterFileStateCacheV3").newInstance();
                            hazel.initializeCache(hazelConfig, this);
                            filesysContext.setStateCache(hazel);
                        }
                        catch ( Exception ex) {
                            throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                        }
                    }
                    else
                    {
                        // Create state cache here and inject
                        StandaloneFileStateCache standaloneCache = new StandaloneFileStateCache();
                        standaloneCache.initializeCache( new GenericConfigElement( ""), this);

                        filesysContext.setStateCache(standaloneCache);
                    }
                    
                    if ( filesysContext.hasStateCache()) {
                        
                        // Register the state cache with the reaper thread
                        // has many side effects including initialisation of the cache    
                        fsysConfig.addFileStateCache( filesystem.getDeviceName(), filesysContext.getStateCache());
                        
                        // Create the lock manager for the context.
                        FileStateLockManager lockMgr = new FileStateLockManager(filesysContext.getStateCache());
                        filesysContext.setLockManager(lockMgr); 
                        filesysContext.setOpLockManager(lockMgr);
                    }
                    
                    if ((!smbConfigBean.getServerEnabled() && !ftpConfigBean.getServerEnabled())
                                && isContentDiskDriver2(filesysDriver))
                    {
                        ((ContentContext) filesystem).setDisableNodeMonitor(true);
                    }
                    
                    filesysDriver.registerContext(filesystem);

                    // Check if an access control list has been specified

                    AccessControlList acls = null;
                    AccessControlListBean accessControls = filesysContext.getAccessControlList();
                    if (accessControls != null)
                    {
                        // Parse the access control list
                        acls = accessControls.toAccessControlList(secConfig);
                    }
                    else if (secConfig.hasGlobalAccessControls())
                    {

                        // Use the global access control list for this disk share
                        acls = secConfig.getGlobalAccessControls();
                    }

                    // Create the shared filesystem

                    filesys = new DiskSharedDevice(filesystem.getDeviceName(), filesysDriver, filesysContext);
                    filesys.setConfiguration( this);

                    // Add any access controls to the share

                    filesys.setAccessControlList(acls);


                    
                    // Check if change notifications should be enabled
                    
                    if ( filesysContext.getDisableChangeNotifications() == false)
                        filesysContext.enableChangeHandler( true);
                    
                    // Start the filesystem

                    filesysContext.startFilesystem(filesys);

                    // Add the new filesystem

                    fsysConfig.addShare(filesys);
                }
                catch (DeviceContextException ex)
                {
                    throw new AlfrescoRuntimeException("Error creating filesystem " + filesystem.getDeviceName(), ex);
                }
                catch (InvalidConfigurationException ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }
        }
        else
        {
            // No filesystems defined

            logger.warn("No filesystems defined");
        }

        // home folder share mapper could be declared in security config
    }

    /**
     * Returns true if either: the disk interface is a ContentDiskDriver2; or
     * the disk interface is a {@link BufferedContentDiskDriver} and its disk
     * interface is a ContentDiskDriver2 (wrapped by several other DiskInterface objects).
     * 
     * @param diskInterface ExtendedDiskInterface
     * @return boolean
     */
    private boolean isContentDiskDriver2(ExtendedDiskInterface diskInterface)
    {
        if (diskInterface instanceof ContentDiskDriver2)
        {
            return true;
        }
        if (diskInterface instanceof BufferedContentDiskDriver)
        {
            BufferedContentDiskDriver bufferedDriver = (BufferedContentDiskDriver) diskInterface;
            ExtendedDiskInterface underlyingDriver = bufferedDriver.getDiskInterface();
            
            if (underlyingDriver instanceof LegacyFileStateDriver)
            {
                LegacyFileStateDriver legacyDriver = (LegacyFileStateDriver) underlyingDriver;
                underlyingDriver = legacyDriver.getDiskInterface();
                
                if (underlyingDriver instanceof NonTransactionalRuleContentDiskDriver)
                {
                    // This is the best we can do. The underlying driver of this driver (the
                    // NonTransactionalRuleContentDiskDriver) is a dynamic proxy and we can't
                    // say for sure if it is a ContentDiskDriver2.
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Process the security configuration
     */
    protected void processSecurityConfig()
    {
        // Create the security configuration section

        SecurityConfigSection secConfig = new SecurityConfigSection(this);

        try
        {
            // Check if global access controls have been specified

            AccessControlListBean accessControls = securityConfigBean.getGlobalAccessControl();

            if (accessControls != null)
            {
                // Parse the access control list
                AccessControlList acls = accessControls.toAccessControlList(secConfig);
                if (acls != null)
                    secConfig.setGlobalAccessControls(acls);
            }
           

            // Check if a JCE provider class has been specified
            
            String jceProvider = securityConfigBean.getJCEProvider();
            if (jceProvider != null && jceProvider.length() > 0)
            {

                // Set the JCE provider

                secConfig.setJCEProvider(jceProvider);
            }
            else
            {
                // Use the default Bouncy Castle JCE provider

                secConfig.setJCEProvider("org.bouncycastle.jce.provider.BouncyCastleProvider");
            }

            // Check if a share mapper has been specified

            ShareMapper shareMapper = securityConfigBean.getShareMapper();
            if (shareMapper != null)
            {
                // Associate the share mapper
                secConfig.setShareMapper(shareMapper);
            }
     
            // Check if any domain mappings have been specified

            List<DomainMappingConfigBean> mappings = securityConfigBean.getDomainMappings();
            if (mappings != null)
            {
                DomainMapping mapping = null;

                for (DomainMappingConfigBean domainMap : mappings)
                {
                    // Get the domain name

                    String name = domainMap.getName();

                    // Check if the domain is specified by subnet or range

                    String subnetStr = domainMap.getSubnet();
                    String rangeFromStr;
                    if (subnetStr != null && subnetStr.length() > 0)
                    {
                        String maskStr = domainMap.getMask();

                        // Parse the subnet and mask, to validate and convert to int values

                        int subnet = IPAddress.parseNumericAddress(subnetStr);
                        int mask = IPAddress.parseNumericAddress(maskStr);

                        if (subnet == 0 || mask == 0)
                            throw new AlfrescoRuntimeException("Invalid subnet/mask for domain mapping " + name);

                        // Create the subnet domain mapping

                        mapping = new SubnetDomainMapping(name, subnet, mask);
                    }
                    else if ((rangeFromStr = domainMap.getRangeFrom()) != null && rangeFromStr.length() > 0)
                    {
                        String rangeToStr = domainMap.getRangeTo();

                        // Parse the range from/to values and convert to int values

                        int rangeFrom = IPAddress.parseNumericAddress(rangeFromStr);
                        int rangeTo = IPAddress.parseNumericAddress(rangeToStr);

                        if (rangeFrom == 0 || rangeTo == 0)
                            throw new AlfrescoRuntimeException("Invalid address range domain mapping " + name);

                        // Create the subnet domain mapping

                        mapping = new RangeDomainMapping(name, rangeFrom, rangeTo);
                    }
                    else
                        throw new AlfrescoRuntimeException("Invalid domain mapping specified");

                    // Add the domain mapping

                    secConfig.addDomainMapping(mapping);
                }
            }

            // Check if the users interface has been set
            if ( securityConfigBean.getUsersInterface() != null)
                secConfig.setUsersInterface( securityConfigBean.getUsersInterface());
        }
        catch (InvalidConfigurationException ex)
        {
            throw new AlfrescoRuntimeException(ex.getMessage());
        }
    }

    /**
     * Process the core server configuration
     * 
     * @exception InvalidConfigurationException
     */
    protected void processCoreServerConfig() throws InvalidConfigurationException
    {
        // Create the core server configuration section
        CoreServerConfigSection coreConfig = new CoreServerConfigSection(this);

    	// Check if the SMB server is not enabled, do not create the thread/memory pools
    	if ( smbConfigBean == null || smbConfigBean.getServerEnabled() == false)
    		return;

        // Check if the server core element has been specified
        if (coreServerConfigBean == null)
        {

            // Configure a default memory pool
            coreConfig.setMemoryPool( getMemoryBufferSizes(), getMemoryBufferAllocations(), getMemoryBufferMaximumAllocations());

            // Configure a default thread pool size
            coreConfig.setThreadPool( getDefaultThreads(), getMaximumThreads());

            threadPool = coreConfig.getThreadPool();
            return;
        }

        // Check if the thread pool size has been specified
        Integer initSize = coreServerConfigBean.getThreadPoolInit();
        if (initSize == null)
        {
            initSize = DefaultThreadPoolInit;
        }
        Integer maxSize = coreServerConfigBean.getThreadPoolMax();
        if (maxSize == null)
        {
            maxSize = DefaultThreadPoolMax;
        }

        // Range check the thread pool size
        if (initSize < ThreadRequestPool.MinimumWorkerThreads)
            throw new InvalidConfigurationException("Thread pool size below minimum allowed size");

        if (initSize > ThreadRequestPool.MaximumWorkerThreads)
            throw new InvalidConfigurationException("Thread pool size above maximum allowed size");

        // Range check the maximum thread pool size
        if (maxSize < ThreadRequestPool.MinimumWorkerThreads)
            throw new InvalidConfigurationException("Thread pool maximum size below minimum allowed size");

        if (maxSize > ThreadRequestPool.MaximumWorkerThreads)
            throw new InvalidConfigurationException("Thread pool maximum size above maximum allowed size");

        if (maxSize < initSize)
            throw new InvalidConfigurationException("Initial size is larger than maxmimum size");        

        // Configure the thread pool
        coreConfig.setThreadPool(initSize, maxSize);

        threadPool = coreConfig.getThreadPool();

        // Check if thread pool debug output is enabled
        if (coreServerConfigBean.getThreadPoolDebug())
            coreConfig.getThreadPool().setDebug(true);

        // Check if the packet sizes/allocations have been specified
        List<MemoryPacketConfigBean> packetSizes = coreServerConfigBean.getMemoryPacketSizes();
        if (packetSizes != null)
        {

            // Calculate the array size for the packet size/allocation arrays
            int elemCnt = packetSizes.size();

            // Create the packet size, initial allocation and maximum allocation arrays
            int[] pktSizes = new int[elemCnt];
            int[] initSizes = new int[elemCnt];
            int[] maxSizes = new int[elemCnt];

            int elemIdx = 0;

            // Process the packet size elements
            for (MemoryPacketConfigBean curChild : packetSizes)
            {
                // Get the packet size
                int pktSize = -1;

                Long pktSizeLong = curChild.getSize();
                if (pktSizeLong == null)
                    throw new InvalidConfigurationException("Memory pool packet size not specified");

                // Parse the packet size
                try
                {
                    pktSize = MemorySize.getByteValueInt(pktSizeLong.toString());
                }
                catch (NumberFormatException ex)
                {
                    throw new InvalidConfigurationException("Memory pool packet size, invalid size value, "
                            + pktSizeLong);
                }

                // Make sure the packet sizes have been specified in ascending order
                if (elemIdx > 0 && pktSizes[elemIdx - 1] >= pktSize)
                    throw new InvalidConfigurationException(
                            "Invalid packet size specified, less than/equal to previous packet size");

                // Get the initial allocation for the current packet size
                Integer initAlloc = curChild.getInit();
                if (initAlloc == null)
                    throw new InvalidConfigurationException("Memory pool initial allocation not specified");

                // Range check the initial allocation
                if (initAlloc < MemoryPoolMinimumAllocation)
                    throw new InvalidConfigurationException("Initial memory pool allocation below minimum of "
                            + MemoryPoolMinimumAllocation);

                if (initAlloc > MemoryPoolMaximumAllocation)
                    throw new InvalidConfigurationException("Initial memory pool allocation above maximum of "
                            + MemoryPoolMaximumAllocation);

                // Get the maximum allocation for the current packet size
                Integer maxAlloc = curChild.getMax();
                if (maxAlloc == null)
                    throw new InvalidConfigurationException("Memory pool maximum allocation not specified");

                // Range check the maximum allocation
                if (maxAlloc < MemoryPoolMinimumAllocation)
                    throw new InvalidConfigurationException("Maximum memory pool allocation below minimum of "
                            + MemoryPoolMinimumAllocation);

                if (initAlloc > MemoryPoolMaximumAllocation)
                    throw new InvalidConfigurationException("Maximum memory pool allocation above maximum of "
                            + MemoryPoolMaximumAllocation);

                // Set the current packet size elements
                pktSizes[elemIdx] = pktSize;
                initSizes[elemIdx] = initAlloc;
                maxSizes[elemIdx] = maxAlloc;

                elemIdx++;
            }

            // Check if all elements were used in the packet size/allocation arrays
            if (elemIdx < pktSizes.length)
            {

                // Re-allocate the packet size/allocation arrays
                int[] newPktSizes = new int[elemIdx];
                int[] newInitSizes = new int[elemIdx];
                int[] newMaxSizes = new int[elemIdx];

                // Copy the values to the shorter arrays
                System.arraycopy(pktSizes, 0, newPktSizes, 0, elemIdx);
                System.arraycopy(initSizes, 0, newInitSizes, 0, elemIdx);
                System.arraycopy(maxSizes, 0, newMaxSizes, 0, elemIdx);

                // Move the new arrays into place
                pktSizes = newPktSizes;
                initSizes = newInitSizes;
                maxSizes = newMaxSizes;
            }

            // Configure the memory pool
            coreConfig.setMemoryPool(pktSizes, initSizes, maxSizes);
        }
        else
        {
            // Configure a default memory pool
            coreConfig.setMemoryPool(DefaultMemoryPoolBufSizes, DefaultMemoryPoolInitAlloc, DefaultMemoryPoolMaxAlloc);
        }
    }
    
    /**
     * Initialise a runtime context - not configured through spring e.g MT.
     * 
     * TODO - what about desktop actions etc?
     * 
     * @param uniqueName String
     * @param diskCtx AlfrescoContext
     */
    public void initialiseRuntimeContext(String uniqueName, AlfrescoContext diskCtx)
    {
        logger.debug("initialiseRuntimeContext" + diskCtx);
        
        if (diskCtx.getStateCache() == null) {
          
          // Set the state cache, use a hard coded standalone cache for now
          FilesystemsConfigSection filesysConfig = (FilesystemsConfigSection) this.getConfigSection( FilesystemsConfigSection.SectionName);
  
          if ( filesysConfig != null) 
          {
              
              try 
              {
                  if(clusterConfigBean != null && clusterConfigBean.getClusterEnabled())
                  {
                      if(logger.isDebugEnabled())
                      {
                          logger.debug("start hazelcast cache : " + clusterConfigBean.getClusterName() + ", uniqueName: "+ uniqueName);
                      }

                      GenericConfigElement hazelConfig = createClusterConfig(uniqueName);
                      ClusterFileStateCache hazel = null;

                      try {
                          hazel = (ClusterFileStateCache) Class.forName("org.filesys.server.filesys.cache.hazelcast.HazelCastClusterFileStateCacheV3").newInstance();
                          hazel.initializeCache(hazelConfig, this);
                          diskCtx.setStateCache(hazel);
                      }
                      catch ( Exception ex) {
                          throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                      }
                  }
                  else
                  {          
                      // Create a standalone state cache
                      StandaloneFileStateCache standaloneCache = new StandaloneFileStateCache();
                      standaloneCache.initializeCache( new GenericConfigElement( ""), this); 
                      filesysConfig.addFileStateCache( diskCtx.getDeviceName(), standaloneCache);
                      diskCtx.setStateCache( standaloneCache);
                  }
                    
                  // Register the state cache with the reaper thread
                  // has many side effects including initialisation of the cache    
                 filesysConfig.addFileStateCache( diskCtx.getShareName(), diskCtx.getStateCache());
                       
                 FileStateLockManager lockMgr = new FileStateLockManager(diskCtx.getStateCache());
                 diskCtx.setLockManager(lockMgr); 
                 diskCtx.setOpLockManager(lockMgr); 
              }
              catch ( InvalidConfigurationException ex) 
              {
                  throw new AlfrescoRuntimeException( "Failed to initialize standalone state cache for " + diskCtx.getDeviceName());
              }
          }
      }
    }
    

    @Override
    protected void processClusterConfig() throws InvalidConfigurationException
    {
        if (clusterConfigBean  == null || !clusterConfigBean.getClusterEnabled())
        {
            removeConfigSection(ClusterConfigSection.SectionName);
            logger.info("Filesystem cluster cache not enabled");
            return;
        }
                
        // Create a ClusterConfigSection and attach it to 'this'.
        ClusterConfigSection clusterConf = new ClusterConfigSection(this);
    }
    

    @Override
    protected void processLicenceConfig() {

        // Check if the licence key has been specified
        if ( licenceConfigBean == null || licenceConfigBean.getLicenceKey() == null || licenceConfigBean.getLicenceKey().length() == 0) {

            // No valid licence key
            removeConfigSection( LicenceConfigSection.SectionName);
        }
        else {

            // Create the licence configuration
            LicenceConfigSection licenceConfig = new LicenceConfigSection( this);
            licenceConfig.setLicenceKey( licenceConfigBean.getLicenceKey());
            licenceConfig.setProductEdition( "Alfresco");
        }
    }

    @Override
    protected void processAuditLog() throws IOException {

        if ( auditConfigBean != null) {

            // Check for enabled audit groups
            String groupList = auditConfigBean.getAuditGroups();
            EnumSet<AuditGroup> auditGroups = EnumSet.<AuditGroup>noneOf( AuditGroup.class);

            if ( groupList != null) {

                if ( groupList.equals( "*")) {
                    auditGroups = EnumSet.allOf( AuditGroup.class);
                }
                else {

                    // Parse the groups list
                    groupList = groupList.toUpperCase();
                    StringTokenizer token = new StringTokenizer(groupList, ",");

                    while (token.hasMoreTokens()) {

                        // Get the current audit group name
                        String groupName = token.nextToken().trim();

                        // Convert the group name to an enum value
                        try {
                            auditGroups.add(AuditGroup.valueOf(groupName));
                        }
                        catch (IllegalArgumentException ex) {
                            throw new AlfrescoRuntimeException("Invalid audit group name, " + groupName);
                        }
                    }
                }
            }

            // Set the enabled audit groups
            Audit.setAuditGroups( auditGroups);

            // Create audit log interface
            DebugInterface auditInterface = new LogFileDebug(auditConfigBean.getAuditLogPath(), true);

            // Set the audit log output to go to a separate file
            Audit.setAuditInterface( auditInterface);
        }
    }

    private  GenericConfigElement createClusterConfig(String topicName) throws InvalidConfigurationException
    {
        GenericConfigElement config = new GenericConfigElement("hazelcastStateCache");
        GenericConfigElement clusterNameCfg = new GenericConfigElement("clusterName");
        clusterNameCfg.setValue(clusterConfigBean.getClusterName());
        config.addChild(clusterNameCfg);
    
        GenericConfigElement topicNameCfg = new GenericConfigElement("clusterTopic");
        if(topicName == null || topicName.isEmpty())
        {
            topicName="default";
        }
        topicNameCfg.setValue(topicName);
        config.addChild(topicNameCfg);
    
        if(clusterConfigBean.getDebugFlags() != null)
        {
            GenericConfigElement debugCfg = new GenericConfigElement("cacheDebug");
            debugCfg.addAttribute("flags", clusterConfigBean.getDebugFlags());
            config.addChild(debugCfg);
        }
    
        if(clusterConfigBean.getNearCacheTimeout() > 0)
        {
            GenericConfigElement nearCacheCfg = new GenericConfigElement("nearCache");
            nearCacheCfg.addAttribute("disable", Boolean.FALSE.toString());
            nearCacheCfg.addAttribute("timeout", Integer.toString(clusterConfigBean.getNearCacheTimeout()));
            config.addChild(nearCacheCfg);
        }
        return config;
    }

    @Override
    public void destroy() throws Exception
    {
        if (threadPool != null)
        {
            threadPool.shutdownThreadPool();
            threadPool = null;
        }
    }

    /**
     * Get the memory pool buffer sizes
     *
     * @return int[]
     */
    protected int[] getMemoryBufferSizes() {
        if ( licenceConfigBean != null) {

            // Check if there is a custom maximum packet size for SMBv2
            if ( smb2ConfigBean != null && smb2ConfigBean.getMaxPacketSize() > 0) {

                // Create a custom set of buffer sizes
                int[] bufSizes = Arrays.copyOf( EnterpriseMemoryPoolBufSizes, EnterpriseMemoryPoolBufSizes.length);

                // Use the custom maximum packet size value for the largest buffer size
                bufSizes[ bufSizes.length -1] = smb2ConfigBean.getMaxPacketSize();

                return bufSizes;
            }

            // Use the default buffer sizes
            return EnterpriseMemoryPoolBufSizes;
        }
        else
            return DefaultMemoryPoolBufSizes;
    }

    /**
     * Get the memory pool buffer initial allocations for each buffer size
     *
     * @return int[]
     */
    protected int[] getMemoryBufferAllocations() {
        if ( licenceConfigBean != null)
            return EnterpriseMemoryPoolInitAlloc;
        else
            return DefaultMemoryPoolInitAlloc;
    }

    /**
     * Get the memory pool buffer maximum allocations for each buffer size
     *
     * @return int[]
     */
    protected int[] getMemoryBufferMaximumAllocations() {
        if ( licenceConfigBean != null)
            return EnterpriseMemoryPoolMaxAlloc;
        else
            return DefaultMemoryPoolMaxAlloc;
    }
}
