/*
 * Copyright (C) 2018-2019 GK Spencer
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

import java.io.*;

/**
 * Licence Configuration Bean Class
 *
 * <p>Contains details of the licence required to enable Enterprise features of the file server</p>
 *
 * @author gkspencer
 */
public class LicenceConfigBean {

    // The licence key
    private String m_licenceKey;

    /**
     * Return the licence key
     *
     * @return String
     */
    public String getLicenceKey() {
        return m_licenceKey;
    }

    /**
     * Set the licence key
     *
     * @param key String
     */
    public void setLicenceKey( String key) {

        // Set the licence key
        if ( key != null) {

            // Strip <cr> and <lf> characters from the string
            m_licenceKey = key.replaceAll( "\n","").replaceAll("\r", "").replaceAll(" ", "");
        }
        else
            m_licenceKey = null;
    }

    /**
     * Set the path to the licence file
     *
     * @param licPath String
     * @exception Exception
     */
    public void setLicencePath( String licPath)
        throws Exception {

        // Make sure the path is valid
        if ( licPath == null)
            return;

        // Load the licence details
        InputStream licStream = null;

        // If the path is relative then load the licence as a resource, if the path is absolute then try to load
        // the file directly
        if ( licPath.startsWith(File.pathSeparator)) {

            // Try and load the file directly
            licStream = new FileInputStream(licPath);
        }
        else {

            // Relative path, try and load as a resource
            licStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(licPath);
        }

        if ( licStream == null) {

            // No licence found
            m_licenceKey = null;
            return;
        }
//            throw new Exception("Failed to find licence file - " + licPath);

        // Load the licence data
        StringBuilder licData = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(licStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                licData.append(line);
            }
        }

        // Set the licence key
        setLicenceKey( licData.toString());
    }
}
