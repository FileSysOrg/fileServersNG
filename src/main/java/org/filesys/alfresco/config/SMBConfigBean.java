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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.server.SMBV1VirtualCircuitList;
import org.filesys.smb.server.VirtualCircuitList;

import static org.filesys.alfresco.AbstractServerConfigurationBean.MaxSessionTimeout;

/**
 * SMB Configuration Bean Class
 * 
 * @author dward
 * @author gkspencer
 */
public class SMBConfigBean
{
    // SMB server enabled
    private boolean serverEnabled;

    // Disable native code
    private boolean disableNativeCode;

    // Broadcast address
    private String broadcastAddress;

    // Server name
    private String serverName;

    // Domain name
    private String domainName;

    // Server comment
    private String serverComment;

    // Network adapter to bind to
    private String bindToAdapter;

    // Network address to bind to
    private String bindToAddress;

    // SMB authenticator
    private ISMBAuthenticator authenticator;

    // Host announcer enable
    private boolean hostAccouncerEnabled;

    // Host announcement interval
    private Integer hostAccounceInterval;

    // NetBIOS configuration
    private NetBIOSSMBConfigBean netBIOSSMB;

    // TCPIP/native SMB configuration
    private TcpipSMBConfigBean tcpipSMB;

    // Win32 NetBIOS native configuration
    private Win32NetBIOSConfigBean win32NetBIOS;

    // Win32 host announcer enable
    private boolean win32HostAnnouncerEnabled;

    // Win32 host announcement interval
    private Integer win32HostAnnounceInterval;

    // WINS configuration
    private WINSConfigBean winsConfig;

    // SMB session debug flags
    private String sessionDebugFlags;

    // Disable NIO, use thread per socket
    private boolean disableNIO;

    // Session timeout
    private Integer sessionTimeout;

    // Maximum virtual circuits per session
    private int m_maxVC = SMBV1VirtualCircuitList.DefMaxCircuits;
    
    // Terminal server address list
    private List<String> terminalServerList;

    // Load balancer address list
    private List<String> loadBalancerList;

    // Enabled dialect set
    private DialectSelector enabledDialects;

    // Require signing, client must support and enable signing of requests
    private boolean requireSigning;

    // Maximum packets per thread run
    private int maxPacketsPerRun = 4;

    /**
     * Class constructor
     *
     * @param terminalServerList String
     * @param loadBalancerList String
     */
    public SMBConfigBean(String terminalServerList, String loadBalancerList)
    {
        this.terminalServerList = convertToIpAddressList(terminalServerList);
        this.loadBalancerList = convertToIpAddressList(loadBalancerList);
    }

    /**
     * Convert string of addresses to the address list.
     * 
     * @param addressist
     */
    public List<String> convertToIpAddressList(String addressist)
    {
        List<String> listIpAddress = null;
        if (addressist != null && !addressist.isEmpty())
        {
            StringTokenizer tkn = new StringTokenizer(addressist, ", \t\n\r\f");
            listIpAddress = new ArrayList<String>(tkn.countTokens());
            while (tkn.hasMoreTokens())
            {
                String instance = tkn.nextToken();
                listIpAddress.add(instance);
            }
        }
        return listIpAddress;
    }
    
    /**
     * Gets the terminal server list address.
     * 
     * @return the terminal server list address
     */
    public List<String> getTerminalServerList()
    {
        return this.terminalServerList;
    }
    
    /**
     * Gets the load balancer list address.
     * 
     * @return the load balancer list address
     */
    public List<String> getLoadBalancerList()
    {
        return this.loadBalancerList;
    }

    /**
     * Checks if the SMB server is enabled.
     * 
     * @return true, if is server enabled
     */
    public boolean getServerEnabled()
    {
        return serverEnabled;
    }

    /**
     * Sets the server enabled.
     * 
     * @param serverEnabled
     *            the new server enabled
     */
    public void setServerEnabled(boolean serverEnabled)
    {
        this.serverEnabled = serverEnabled;
    }

    /**
     * Checks if native code should be disabled.
     * 
     * @return true, if is disable native code
     */
    public boolean getDisableNativeCode()
    {
        return disableNativeCode;
    }

    /**
     * Sets the disable native code.
     * 
     * @param disableNativeCode
     *            the new disable native code
     */
    public void setDisableNativeCode(boolean disableNativeCode)
    {
        this.disableNativeCode = disableNativeCode;
    }

    /**
     * Gets the broadcast address.
     * 
     * @return the broadcast address
     */
    public String getBroadcastAddress()
    {
        return broadcastAddress;
    }

    /**
     * Sets the broadcast address.
     * 
     * @param broadcastAddress
     *            the new broadcast address
     */
    public void setBroadcastAddress(String broadcastAddress)
    {
        this.broadcastAddress = broadcastAddress;
    }

    /**
     * Gets the server name.
     * 
     * @return the server name
     */
    public String getServerName()
    {
        return serverName;
    }

    /**
     * Sets the server name.
     * 
     * @param serverName
     *            the new server name
     */
    public void setServerName(String serverName)
    {
        this.serverName = serverName;
    }

    /**
     * Gets the domain name.
     * 
     * @return the domain name
     */
    public String getDomainName()
    {
        return domainName;
    }

    /**
     * Sets the domain name.
     * 
     * @param domainName
     *            the new domain name
     */
    public void setDomainName(String domainName)
    {
        this.domainName = domainName;
    }

    /**
     * Gets the server comment.
     * 
     * @return the server comment
     */
    public String getServerComment()
    {
        return serverComment;
    }

    /**
     * Sets the server comment.
     * 
     * @param serverComment
     *            the new server comment
     */
    public void setServerComment(String serverComment)
    {
        this.serverComment = serverComment;
    }

    /**
     * Gets the bind to adapter.
     * 
     * @return the bind to adapter
     */
    public String getBindToAdapter()
    {
        return bindToAdapter;
    }

    /**
     * Sets the bind to adapter.
     * 
     * @param bindToAdapter
     *            the new bind to adapter
     */
    public void setBindToAdapter(String bindToAdapter)
    {
        this.bindToAdapter = bindToAdapter;
    }

    /**
     * Gets the bind to address.
     * 
     * @return the bind to address
     */
    public String getBindToAddress()
    {
        return bindToAddress;
    }

    /**
     * Sets the bind to address.
     * 
     * @param bindToAddress
     *            the new bind to address
     */
    public void setBindToAddress(String bindToAddress)
    {
        this.bindToAddress = bindToAddress;
    }

    /**
     * Gets the authenticator.
     * 
     * @return the authenticator
     */
    public ISMBAuthenticator getAuthenticator()
    {
        return authenticator;
    }

    /**
     * Sets the authenticator.
     * 
     * @param authenticator
     *            the new authenticator
     */
    public void setAuthenticator(ISMBAuthenticator authenticator)
    {
        this.authenticator = authenticator;
    }

    /**
     * Checks if is host accouncer enabled.
     * 
     * @return true, if is host accouncer enabled
     */
    public boolean getHostAccouncerEnabled()
    {
        return hostAccouncerEnabled;
    }

    /**
     * Sets the host accouncer enabled.
     * 
     * @param hostAccouncerEnabled
     *            the new host accouncer enabled
     */
    public void setHostAccouncerEnabled(boolean hostAccouncerEnabled)
    {
        this.hostAccouncerEnabled = hostAccouncerEnabled;
    }

    /**
     * Gets the host accounce interval.
     * 
     * @return the host accounce interval
     */
    public Integer getHostAccounceInterval()
    {
        return hostAccounceInterval;
    }

    /**
     * Sets the host accounce interval.
     * 
     * @param hostAccounceInterval
     *            the new host accounce interval
     */
    public void setHostAccounceInterval(Integer hostAccounceInterval)
    {
        this.hostAccounceInterval = hostAccounceInterval;
    }

    /**
     * Gets the net biossmb.
     * 
     * @return the net biossmb
     */
    public NetBIOSSMBConfigBean getNetBIOSSMB()
    {
        return netBIOSSMB;
    }

    /**
     * Sets the net biossmb.
     * 
     * @param netBIOSSMB
     *            the new net biossmb
     */
    public void setNetBIOSSMB(NetBIOSSMBConfigBean netBIOSSMB)
    {
        this.netBIOSSMB = netBIOSSMB;
    }

    /**
     * Gets the tcpip smb.
     * 
     * @return the tcpip smb
     */
    public TcpipSMBConfigBean getTcpipSMB()
    {
        return tcpipSMB;
    }

    /**
     * Return the maxmimum virtual circuits per session
     * 
     * @return int
     */
    public int getMaximumVirtualCircuits() {
    	return m_maxVC;
    }
    
    /**
     * Sets the tcpip smb.
     * 
     * @param tcpipSMB
     *            the new tcpip smb
     */
    public void setTcpipSMB(TcpipSMBConfigBean tcpipSMB)
    {
        this.tcpipSMB = tcpipSMB;
    }

    /**
     * Gets the win32 net bios.
     * 
     * @return the win32 net bios
     */
    public Win32NetBIOSConfigBean getWin32NetBIOS()
    {
        return win32NetBIOS;
    }

    /**
     * Sets the win32 net bios.
     * 
     * @param win32NetBIOS
     *            the new win32 net bios
     */
    public void setWin32NetBIOS(Win32NetBIOSConfigBean win32NetBIOS)
    {
        this.win32NetBIOS = win32NetBIOS;
    }

    /**
     * Checks if is win32 host announcer enabled.
     * 
     * @return true, if is win32 host announcer enabled
     */
    public boolean getWin32HostAnnouncerEnabled()
    {
        return win32HostAnnouncerEnabled;
    }

    /**
     * Sets the win32 host announcer enabled.
     * 
     * @param win32HostAnnouncerEnabled
     *            the new win32 host announcer enabled
     */
    public void setWin32HostAnnouncerEnabled(boolean win32HostAnnouncerEnabled)
    {
        this.win32HostAnnouncerEnabled = win32HostAnnouncerEnabled;
    }

    /**
     * Gets the win32 host announce interval.
     * 
     * @return the win32 host announce interval
     */
    public Integer getWin32HostAnnounceInterval()
    {
        return win32HostAnnounceInterval;
    }

    /**
     * Sets the win32 host announce interval.
     * 
     * @param win32HostAnnounceInterval
     *            the new win32 host announce interval
     */
    public void setWin32HostAnnounceInterval(Integer win32HostAnnounceInterval)
    {
        this.win32HostAnnounceInterval = win32HostAnnounceInterval;
    }

    /**
     * Gets the wINS config.
     * 
     * @return the wINS config
     */
    public WINSConfigBean getWINSConfig()
    {
        return winsConfig;
    }

    /**
     * Sets the wINS config.
     * 
     * @param config
     *            the new wINS config
     */
    public void setWINSConfig(WINSConfigBean config)
    {
        winsConfig = config;
    }

    /**
     * Gets the session debug flags.
     * 
     * @return the session debug flags
     */
    public String getSessionDebugFlags()
    {
        return sessionDebugFlags;
    }

    /**
     * Sets the session debug flags.
     * 
     * @param sessionDebugFlags
     *            the new session debug flags
     */
    public void setSessionDebugFlags(String sessionDebugFlags)
    {
        this.sessionDebugFlags = sessionDebugFlags;
    }

    /**
     * Checks if is disable nio.
     * 
     * @return true, if is disable nio
     */
    public boolean getDisableNIO()
    {
        return disableNIO;
    }

    /**
     * Sets the disable nio.
     * 
     * @param disableNIO
     *            the new disable nio
     */
    public void setDisableNIO(boolean disableNIO)
    {
        this.disableNIO = disableNIO;
    }

    /**
     * Gets the session timeout.
     * 
     * @return the session timeout
     */
    public Integer getSessionTimeout()
    {
        return sessionTimeout;
    }

    /**
     * Sets the session timeout.
     * 
     * @param sessionTimeout
     *            the new session timeout
     */
    public void setSessionTimeout(Integer sessionTimeout)
    {
        validateSessionTimeout(sessionTimeout);
        this.sessionTimeout = sessionTimeout;
    }

    /**
     * Validates the session timeout.
     * 
     * @param sessionTimeout
     *            the session timeout to validate
     */
    public void validateSessionTimeout(Integer sessionTimeout)
    {
        if (sessionTimeout < 0 || sessionTimeout > MaxSessionTimeout)
            throw new AlfrescoRuntimeException("Session timeout out of range (0 - " + MaxSessionTimeout + ")");
    }

    /**
     * Set the maximum virtual circuits per session
     * 
     * @param maxVC int
     */
    public void setMaximumVirtualCircuits( int maxVC) {
    	m_maxVC = maxVC;
    }

    /**
     * Get the list of enabled SMB dialects
     *
     * @return DialectSelector
     */
    public DialectSelector getEnabledDialects() {
        return enabledDialects;
    }

    /**
     * Set the list of enabled SMB dialects
     *
     * @param diaList String
     */
    public void setEnabledDialects(String diaList) {

        // Check for a null/empty dialect list
        if ( diaList == null || diaList.length() == 0) {
            enabledDialects = null;
            return;
        }

        // Validate the dialect list string
        StringTokenizer tokens = new StringTokenizer( diaList.toUpperCase().replaceAll(" ", ""), ",");
        DialectSelector enaDialects = new DialectSelector();

        while ( tokens.hasMoreTokens()) {

            // Get the current dialect token, and validate
            String diaStr = tokens.nextToken();

            if ( diaStr.equals( "SMB1") || diaStr.equals( "CIFS"))
                enaDialects.enableGroup(DialectSelector.DialectGroup.SMBv1);
            else if ( diaStr.equals( "SMB2"))
                enaDialects.enableGroup(DialectSelector.DialectGroup.SMBv2);
            else if ( diaStr.equals( "SMB3"))
                enaDialects.enableGroup(DialectSelector.DialectGroup.SMBv3);
            else
                throw new AlfrescoRuntimeException( "Invalid SMB dialect specified - " + diaStr);
        }

        // Check if there are any dialects enabled
        if ( enaDialects.isEmpty())
            throw new AlfrescoRuntimeException( "No SMB dialects enabled");

        // Set the enabled dialects list
        enabledDialects = enaDialects;

        // If SMB2 and/or SMB3 are enabled then increase the maximum packets per thread run value
        if ( enaDialects.hasSMB2() || enabledDialects.hasSMB3())
            maxPacketsPerRun = 8;
    }

    /**
     * Check if signing is required for all sessions
     *
     * @return boolean
     */
    public boolean getRequireSigning() { return requireSigning; }

    /**
     * Set the require signing flag
     *
     * @param reqSigning boolean
     */
    public void setRequireSigning(boolean reqSigning) {
        requireSigning = reqSigning;
    }

    /**
     * Get the maximum packets per thread run
     *
     * @return int
     */
    public int getMaximumPacketsPerRun() { return maxPacketsPerRun; }

    /**
     * Set the maximum packets per thread run
     *
     * @param maxPkts int
     */
    public void setMaximumPacketsPerRun(int maxPkts) { maxPacketsPerRun = maxPkts; }
}
