/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.alfresco.repo;

import org.filesys.alfresco.base.AlfrescoNetworkFile;
import org.filesys.alfresco.base.NetworkFileLegacyReferenceCount;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.NetworkFileStateInterface;

import java.io.IOException;

/**
 * Re-Used Network File Class
 *
 * <p>Wraps an existing NetworkFile to re-use the underlying file but allows a separate access token and
 * oplock owner to be specified</p>
 *
 * @author gkspencer
 */
public class ReUsedNetworkFile extends AlfrescoNetworkFile implements NetworkFileLegacyReferenceCount {

    // Wrapped network file
    private NetworkFile m_origFile;

    /**
     * Class constructor
     *
     * @param netFile NetworkFile
     */
    public ReUsedNetworkFile( NetworkFile netFile) {
        super( netFile);

        m_origFile = netFile;

        // Copy the file state details, if available
        if ( netFile instanceof NetworkFileStateInterface) {
            NetworkFileStateInterface fsFile = (NetworkFileStateInterface) netFile;
            setFileState( fsFile.getFileState());
        }
    }

    /**
     * Return the original file
     *
     * @return NetworkFile
     */
    public final NetworkFile getOriginalFile() { return m_origFile; }

    @Override
    public void openFile(boolean createFlag)
            throws IOException {

        // Pass to the original file
        m_origFile.openFile( createFlag);
    }

    @Override
    public int readFile(byte[] buf, int len, int pos, long fileOff)
            throws java.io.IOException {

        // Pass to the original file
        return m_origFile.readFile( buf, len, pos, fileOff);
    }

    @Override
    public void writeFile(byte[] buf, int len, int pos, long fileOff)
            throws java.io.IOException {

        // Pass to the original file
        m_origFile.writeFile( buf, len, pos, fileOff);

        // Update the file size
        m_fileSize = m_origFile.getFileSize();
    }

    @Override
    public long seekFile(long pos, int typ)
            throws IOException {

        // Pass to the original file
        return m_origFile.seekFile( pos, typ);
    }

    @Override
    public void flushFile()
            throws IOException {

        // Pass to the original file
        m_origFile.flushFile();
    }

    @Override
    public void truncateFile(long siz)
            throws IOException {

        // Pass to the original file
        m_origFile.truncateFile( siz);

        // Update the file size
        m_fileSize = m_origFile.getFileSize();
    }

    @Override
    public void closeFile()
            throws IOException {

        // Do nothing in the re-used file, let the original file open close the underlying file
    }

    @Override
    public int incrementLegacyOpenCount() {
        if ( m_origFile instanceof NetworkFileLegacyReferenceCount) {
            NetworkFileLegacyReferenceCount refCountFile = (NetworkFileLegacyReferenceCount) m_origFile;
            refCountFile.incrementLegacyOpenCount();

            return refCountFile.getLegacyOpenCount();
        }

        return 0;
    }

    @Override
    public int decrementLegacyOpenCount() {
        if ( m_origFile instanceof NetworkFileLegacyReferenceCount) {
            NetworkFileLegacyReferenceCount refCountFile = (NetworkFileLegacyReferenceCount) m_origFile;
            refCountFile.decrementLegacyOpenCount();

            return refCountFile.getLegacyOpenCount();
        }

        return 0;
    }

    @Override
    public int getLegacyOpenCount() {
        if ( m_origFile instanceof NetworkFileLegacyReferenceCount) {
            NetworkFileLegacyReferenceCount refCountFile = (NetworkFileLegacyReferenceCount) m_origFile;

            return refCountFile.getLegacyOpenCount();
        }
        return 0;
    }
}
