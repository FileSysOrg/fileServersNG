/*
 * Copyright (C) 2019 GK Spencer
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
package org.filesys.alfresco.config;

import org.alfresco.error.AlfrescoRuntimeException;
import org.filesys.util.MemorySize;

/**
 * SMB 2 Configuration Bean
 *
 * @author gkspencer
 */
public class SMB2ConfigBean {

    // Minimum/maximum packet size
    public static final int MinSMBV2PacketSize     = (int) (64 * MemorySize.KILOBYTE);
    public static final int MaxSMBV2PacketSize     = (int) (8 * MemorySize.MEGABYTE);

    // Maximum packet size for SMB2
    private int maxPacketSize;

    // Packet signing required
    private boolean m_packetSigning = false;

    /**
     * Return the maximum packet size
     *
     * @return int
     */
    public final int getMaxPacketSize() { return maxPacketSize; }

    /**
     * Set the maximum packet size
     *
     * @param maxSizeStr String
     */
    public final void setMaxPacketSize(String maxSizeStr) {

        if (maxSizeStr != null && maxSizeStr.length() > 0) {
            try {

                // Convert the maximum packet size to a byte value, may be specified as a memory size with K/M/G suffix
                maxPacketSize = MemorySize.getByteValueInt(maxSizeStr);

                if (maxPacketSize < MinSMBV2PacketSize || maxPacketSize > MaxSMBV2PacketSize)
                    throw new AlfrescoRuntimeException("Maximum packet size out of range (" + MemorySize.asScaledString(MinSMBV2PacketSize) +
                            " - " + MemorySize.asScaledString(MaxSMBV2PacketSize) + ")");
            }
            catch (NumberFormatException ex) {
                throw new AlfrescoRuntimeException("Invalid maximum packet size value, " + maxSizeStr);
            }
        }
    }

    /**
     * Check if packet signing is required
     *
     * @return boolean
     */
    public final boolean getRequireSigning() { return m_packetSigning; }

    /**
     * Enable/disable packet signing
     *
     * @param enaSigning boolean
     */
    public final void setRequireSigning(boolean enaSigning) {
        m_packetSigning = enaSigning;
    }

}
