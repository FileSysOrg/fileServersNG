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
 * SMB 3 Configuration Bean
 *
 * @author gkspencer
 */
public class SMB3ConfigBean {

    // Minimum/maximum packet size
    public static final int MinSMBV2PacketSize     = (int) (64 * MemorySize.KILOBYTE);
    public static final int MaxSMBV2PacketSize     = (int) (8 * MemorySize.MEGABYTE);

    // Encryption capability cipher types
    public static final int SMBV3_ENCRYPT_CIPHER_NONE         = 0x0000;
    public static final int SMBV3_ENCRYPT_CIPHER_AES128CCM    = 0x0001;
    public static final int SMBV3_ENCRYPT_CIPHER_AES128GCM    = 0x0002;

    // Enabled SMB v3 encryption types
    private int m_primaryEncryptionType   = SMBV3_ENCRYPT_CIPHER_AES128CCM;
    private int m_secondaryEncryptionType = SMBV3_ENCRYPT_CIPHER_AES128GCM;

    // Encryption disable
    private boolean m_encryptionDisabled = false;

    /**
     * Get the primary encryption type to use
     *
     * @return int
     */
    public final int getPrimaryEncryptionType() { return m_primaryEncryptionType; }

    /**
     * Get the secondary encryption type to use
     *
     * @return int
     */
    public final int getSecondaryEncryptionType() { return m_secondaryEncryptionType; }

    /**
     * Get the encryption disabled status
     *
     * @return boolean
     */
    public final boolean getDisableEncryption() { return m_encryptionDisabled; }

    /**
     * Set the primary encryption type to use
     *
     * @param primaryEncryption String
     */
    public final void setPrimaryEncryptionType(String primaryEncryption) {

        if (primaryEncryption != null && primaryEncryption.length() > 0) {

            // Validate the encryption type string
            if ( primaryEncryption.equalsIgnoreCase( "CCM"))
                m_primaryEncryptionType = SMBV3_ENCRYPT_CIPHER_AES128CCM;
            else if ( primaryEncryption.equalsIgnoreCase( "GCM"))
                m_primaryEncryptionType = SMBV3_ENCRYPT_CIPHER_AES128GCM;
            else if ( primaryEncryption.equalsIgnoreCase( "NONE"))
                throw new AlfrescoRuntimeException( "SMB3 primary encryption type cannot be 'None'");
            else
                throw new AlfrescoRuntimeException( "SMB3 primary encryption type not valid");
        }
        else
            throw new AlfrescoRuntimeException( "SMB3 primary encryption type cannot be null");
    }

    /**
     * Set the secondary encryption type to use
     *
     * @param secondaryEncryption String
     */
    public final void setSecondaryEncryptionType(String secondaryEncryption) {

        if (secondaryEncryption != null && secondaryEncryption.length() > 0) {

            // Validate the encryption type string
            if ( secondaryEncryption.equalsIgnoreCase( "CCM"))
                m_secondaryEncryptionType = SMBV3_ENCRYPT_CIPHER_AES128CCM;
            else if ( secondaryEncryption.equalsIgnoreCase( "GCM"))
                m_secondaryEncryptionType = SMBV3_ENCRYPT_CIPHER_AES128GCM;
            else if ( secondaryEncryption.equalsIgnoreCase( "NONE"))
                m_secondaryEncryptionType = SMBV3_ENCRYPT_CIPHER_NONE;
            else
                throw new AlfrescoRuntimeException( "SMB3 secondary encryption type not valid");
        }
        else
            throw new AlfrescoRuntimeException( "SMB3 secondary encryption type cannot be null");
    }

    /**
     * Enable/disable SMB3 encryption
     *
     * @param disEnc boolean
     */
    public final void setDisableEncryption(boolean disEnc) {
        m_encryptionDisabled = disEnc;
    }
}
