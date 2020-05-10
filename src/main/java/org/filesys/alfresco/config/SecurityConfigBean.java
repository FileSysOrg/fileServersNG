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

import java.util.List;

import org.filesys.alfresco.config.acl.AccessControlListBean;
import org.filesys.server.auth.UsersInterface;
import org.filesys.server.core.ShareMapper;

/**
 * The Class SecurityConfigBean.
 * 
 * @author dward
 */
public class SecurityConfigBean
{
    // Global access controls
    private AccessControlListBean m_globalAccessControl;

    // JCE provider class name
    private String m_jceProvider;

    // Share mapper
    private ShareMapper m_shareMapper;

    // Domain mappings
    private List<DomainMappingConfigBean> m_domainMappings;

    // Users interface
    private UsersInterface m_userInterface;

    /**
     * Gets the global access control.
     * 
     * @return the global access control
     */
    public AccessControlListBean getGlobalAccessControl()
    {
        return m_globalAccessControl;
    }

    /**
     * Sets the global access control.
     * 
     * @param globalAccessControl
     *            the new global access control
     */
    public void setGlobalAccessControl(AccessControlListBean globalAccessControl)
    {
        m_globalAccessControl = globalAccessControl;
    }

    /**
     * Gets the jCE provider.
     * 
     * @return the jCE provider
     */
    public String getJCEProvider()
    {
        return m_jceProvider;
    }

    /**
     * Sets the jCE provider.
     * 
     * @param provider
     *            the new jCE provider
     */
    public void setJCEProvider(String provider)
    {
        m_jceProvider = provider;
    }

    /**
     * Gets the share mapper.
     * 
     * @return the share mapper
     */
    public ShareMapper getShareMapper()
    {
        return m_shareMapper;
    }

    /**
     * Sets the share mapper.
     * 
     * @param shareMapper
     *            the new share mapper
     */
    public void setShareMapper(ShareMapper shareMapper)
    {
        m_shareMapper = shareMapper;
    }

    /**
     * Gets the domain mappings.
     * 
     * @return the domain mappings
     */
    public List<DomainMappingConfigBean> getDomainMappings()
    {
        return m_domainMappings;
    }

    /**
     * Sets the domain mappings.
     * 
     * @param domainMappings
     *            the new domain mappings
     */
    public void setDomainMappings(List<DomainMappingConfigBean> domainMappings)
    {
        m_domainMappings = domainMappings;
    }

    /**
     * Get the users interface
     *
     * @return UsersInterface
     */
    public UsersInterface getUsersInterface() {
        return m_userInterface;
    }

    /**
     * Set the users interface
     *
     * @param usersIface UsersInterface
     */
    public void setUsersInterface( UsersInterface usersIface) {
        m_userInterface = usersIface;
    }
}
