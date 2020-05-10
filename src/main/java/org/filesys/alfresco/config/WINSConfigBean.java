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

// TODO: Auto-generated Javadoc
/**
 * The Class WINSConfigBean.
 * 
 * @author dward
 */
public class WINSConfigBean
{

    /** The primary. */
    private String primary;

    /** The secondary. */
    private String secondary;

    /** The auto detect enabled. */
    private boolean autoDetectEnabled = true;

    /**
     * Checks if is auto detect enabled.
     * 
     * @return true, if is auto detect enabled
     */
    public boolean isAutoDetectEnabled()
    {
        return autoDetectEnabled;
    }

    /**
     * Sets the auto detect enabled.
     * 
     * @param autoDetectEnabled
     *            the new auto detect enabled
     */
    public void setAutoDetectEnabled(boolean autoDetectEnabled)
    {
        this.autoDetectEnabled = autoDetectEnabled;
    }

    /**
     * Gets the primary.
     * 
     * @return the primary
     */
    public String getPrimary()
    {
        return primary;
    }

    /**
     * Sets the primary.
     * 
     * @param primary
     *            the new primary
     */
    public void setPrimary(String primary)
    {
        this.primary = primary;
    }

    /**
     * Gets the secondary.
     * 
     * @return the secondary
     */
    public String getSecondary()
    {
        return secondary;
    }

    /**
     * Sets the secondary.
     * 
     * @param secondary
     *            the new secondary
     */
    public void setSecondary(String secondary)
    {
        this.secondary = secondary;
    }

}
