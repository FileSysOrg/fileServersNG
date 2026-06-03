/*
 * Copyright (C) 2026 GK Spencer
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

import org.alfresco.error.AlfrescoRuntimeException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * FS Temp File Provider Class
 *
 * A version of the TempFileProvider class that uses a temp folder that is on the same volume
 * as the main Alfresco data folder. This allows much quicker moving of the data into the data store
 * as a rename/move can be done instead of a byte by byte copy.
 *
 * @author gkspencer
 */
public class FSTempFileProvider {
    private static final Log logger = LogFactory.getLog(FSTempFileProvider.class);

    // fileServersNG temporary files sub-folder
    public static final String FSNG_TEMP_FILE_DIR = "fileServersNG";

    // FileServersNG temporary files folder
    public static File FSTempRoot;

    /**
     * Static class only
     */
    private FSTempFileProvider() {
    }

    /**
     * Return the temporary directory folder path
     *
     * @return String
     */
    public static String getTempDirRoot() {
         return FSTempRoot.getAbsolutePath();
    }

    /**
     * Set the root temporary folder path
     *
     * @param tempPath String
     * @exception java.io.IOException Root path does not exist
     */
    public static void setTempDirRoot( String tempPath)
        throws IOException {

        Path tempRoot = Paths.get(tempPath);
        if ( !Files.exists(tempRoot))
            throw new IOException("Temp directory does not exist: " + tempPath);

        // Append the fileServersNG folder and make sure the path exists
        Path fsTempRoot = Paths.get( tempRoot.toString(), FSNG_TEMP_FILE_DIR);
        if ( Files.exists(fsTempRoot) && !Files.isDirectory(fsTempRoot))
            throw new IOException("Temp path exists but is not a directory: " + fsTempRoot);

        if ( Files.exists(fsTempRoot)) {
            clearTempDirectory(fsTempRoot);
        }
        else {
            Files.createDirectories(fsTempRoot);
        }

        // Save the temporary folder path
        FSTempRoot = new File( fsTempRoot.toString());
    }

    /**
     * Create a temporary file in the default temporary folder
     *
     * @param prefix String
     * @param suffix String
     * @return File
     */
    public static File createTempFile(String prefix, String suffix)
    {
        return createTempFile(prefix, suffix, FSTempRoot);
    }

    /**
     * Create a unique temporary file
     *
     * @return Returns a temp <code>File</code> that will be located in the
     *         given directory
     */
    public static File createTempFile(String prefix, String suffix, File directory)
    {
        try
        {
            File tempFile = File.createTempFile(prefix, suffix, directory);
            if (logger.isDebugEnabled())
                logger.debug("Creating tmp file: " + tempFile);

            return tempFile;
        } catch (IOException e)
        {
            throw new AlfrescoRuntimeException("Failed to created temp file: " +
                    "prefix: " + prefix + ",suffix: " + suffix + ",directory: " + directory, e);
        }
    }

    private static void clearTempDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }

                if (!dir.equals(directory)) {
                    Files.delete(dir);
                }

                return FileVisitResult.CONTINUE;
            }
        });

        if (logger.isDebugEnabled()) {
            logger.debug("Cleared temp directory: " + directory);
        }
    }
}
