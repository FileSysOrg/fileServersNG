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
package org.filesys.alfresco.repo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import org.filesys.alfresco.base.ExtendedDiskInterface;
import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.clientapi.ClientAPI;
import org.filesys.server.filesys.clientapi.ClientAPIInterface;
import org.filesys.server.filesys.postprocess.PostCloseProcessor;
import org.filesys.server.filesys.version.FileVersionInfo;
import org.filesys.server.filesys.version.VersionInterface;
import org.filesys.server.locking.FileLockingInterface;
import org.filesys.server.locking.LockManager;
import org.filesys.server.locking.OpLockInterface;
import org.filesys.server.locking.OpLockManager;
import org.filesys.smb.SMBException;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.util.DataBuffer;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.util.PropertyCheck;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.config.ConfigElement;

/**
 * Alfresco Content Disk Driver Cache
 * <p>
 * Decorates ContentDiskDriver with a performance cache of some frequently used 
 * results.   In particular for getFileInformation and fileExists
 */
public class BufferedContentDiskDriver implements ExtendedDiskInterface,
        DiskInterface,
        DiskSizeInterface,
        DiskVolumeInterface,
        IOCtlInterface,
        OpLockInterface,
        FileLockingInterface,
        VersionInterface,
        PostCloseProcessor,
        ClientAPI,
        TransactionalMarkerInterface,
        NodeServicePolicies.OnDeleteNodePolicy,
        NodeServicePolicies.OnMoveNodePolicy {

    // Logging
    private static final Log logger = LogFactory.getLog(BufferedContentDiskDriver.class);

    private ExtendedDiskInterface diskInterface;
    private DiskSizeInterface diskSizeInterface;
    private IOCtlInterface ioctlInterface;
    private OpLockInterface opLockInterface;
    private FileLockingInterface fileLockingInterface;
    private VersionInterface versionInterface;
    private PolicyComponent policyComponent;
    private ClientAPI clientAPI;

    // Enable/disable use of the post close processor
    private boolean usePostClose;

    // Volume information
    private Date volumeCreatedAt = new Date();
    private int volumeSerialNo = new java.util.Random().nextInt();

    public void init() {
        PropertyCheck.mandatory(this, "diskInterface", diskInterface);
        PropertyCheck.mandatory(this, "diskSizeInterface", diskSizeInterface);
        PropertyCheck.mandatory(this, "ioctltInterface", ioctlInterface);
        PropertyCheck.mandatory(this, "fileInfoCache", fileInfoCache);
        PropertyCheck.mandatory(this, "fileLockingInterface", getFileLockingInterface());
        PropertyCheck.mandatory(this, "opLockInterface", getOpLockInterface());
        PropertyCheck.mandatory(this, "fileLockingInterface", fileLockingInterface);
        PropertyCheck.mandatory(this, "versionInterface", versionInterface);
        PropertyCheck.mandatory(this, "policyComponent", getPolicyComponent());
        PropertyCheck.mandatory( this, "clientAPI", clientAPI);

        getPolicyComponent().bindClassBehaviour(NodeServicePolicies.OnDeleteNodePolicy.QNAME,
                this, new JavaBehaviour(this, "onDeleteNode"));
        getPolicyComponent().bindClassBehaviour(NodeServicePolicies.OnMoveNodePolicy.QNAME,
                this, new JavaBehaviour(this, "onMoveNode"));
    }

    /**
     * FileInfo Cache for path to FileInfo
     */
    private SimpleCache<Serializable, FileInfo> fileInfoCache;

    /**
     * Set the cache that maintains node ID-NodeRef cross referencing data
     *
     * @param cache the cache
     */
    public void setFileInfoCache(SimpleCache<Serializable, FileInfo> cache) {
        this.fileInfoCache = cache;
    }

    private static class FileInfoKey implements Serializable {
        /**
         *
         */
        private static final long serialVersionUID = 1L;

        String deviceName;
        String path;
        String user;
        int hashCode;

        public FileInfoKey(SrvSession sess, String path, TreeConnection tree) {
            this.path = path;
            this.user = sess.getUniqueId();
            this.deviceName = tree.getSharedDevice().getName();

//            if(deviceName == null)
//            {
//                throw new RuntimeException("device name is null");
//            }
//            if(path == null)
//            {
//                throw new RuntimeException("path is null");
//            }
//            if(user == null)
//            {
//                throw new RuntimeException("unique id is null");
//            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || !(other instanceof FileInfoKey)) {
                return false;
            }

            FileInfoKey o = (FileInfoKey) other;

            return path.equals(o.path) && user.equals(o.user) && deviceName.equals(o.deviceName);
        }

        @Override
        public int hashCode() {
            if (hashCode == 0) {
                hashCode = (user + path + deviceName).hashCode();
            }
            return hashCode;
        }
    }

    private FileInfo getFileInformationInternal(SrvSession sess, TreeConnection tree,
                                                String path) throws IOException {

        //String userName = AuthenticationUtil.getFullyAuthenticatedUser();
        SharedDevice device = tree.getSharedDevice();
        String deviceName = device.getName();

        if (logger.isDebugEnabled()) {
            logger.debug("getFileInformation session:" + sess.getUniqueId() + ", deviceName:" + deviceName + ", path:" + path);
        }

        if (path == null) {
            throw new IllegalArgumentException("Path is null");
        }

        FileInfoKey key = new FileInfoKey(sess, path, tree);

        FileInfo fromCache = fileInfoCache.get(key);

        if (fromCache != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("returning FileInfo from cache");
            }
            return fromCache;
        }

        FileInfo info = diskInterface.getFileInformation(sess, tree, path);

        if (info != null) {
            /**
             * Don't cache directories since the modification date is important.
             */
            if (!info.isDirectory()) {
                fileInfoCache.put(key, info);
            }
        }

        /*
         * Dual Key the cache so it can be looked up by NodeRef or Path
         */
        if (info instanceof ContentFileInfo) {
            ContentFileInfo cinfo = (ContentFileInfo) info;
            fileInfoCache.put(cinfo.getNodeRef(), info);
        }

        return info;
    }


    @Override
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree,
                                       String path) throws IOException {
        return diskInterface.getFileInformation( sess, tree, path);
    }

    @Override
    public FileStatus fileExists(SrvSession sess, TreeConnection tree, String path) {
        return diskInterface.fileExists( sess, tree, path);
    }

    @Override
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException {
        return diskInterface.createContext(shareName, args);
    }

    @Override
    public void treeOpened(SrvSession sess, TreeConnection tree) {
        diskInterface.treeOpened(sess, tree);
    }

    @Override
    public void treeClosed(SrvSession sess, TreeConnection tree) {
        diskInterface.treeClosed(sess, tree);
    }

    @Override
    public DataBuffer processIOControl(SrvSession sess, TreeConnection tree,
                                       int ctrlCode, int fid, DataBuffer dataBuf, boolean isFSCtrl,
                                       int filter) throws IOControlNotImplementedException, SMBException {
        return ioctlInterface.processIOControl(sess, tree, ctrlCode, fid, dataBuf, isFSCtrl, filter);
    }

    @Override
    public void getDiskInformation(DiskDeviceContext ctx, SrvDiskInfo diskDev)
            throws IOException {
        diskSizeInterface.getDiskInformation(ctx, diskDev);
    }

    @Override
    public VolumeInfo getVolumeInformation(DiskDeviceContext ctx) {

        // Return the disk volume information
        return new VolumeInfo( ctx.getDeviceName(), volumeSerialNo, volumeCreatedAt);
    }

    @Override
    public void closeFile(SrvSession sess, TreeConnection tree,
                          NetworkFile netFile) throws IOException {

        // Check if the file has been written to, and the post close processor is enabled
        //
        // Note: Only use post close for SMB sessions
        if ( getEnablePostClose() && !netFile.isReadOnly() && netFile.getWriteCount() > 0 &&
                sess instanceof SMBSrvSession) {

            // Run the file close via a post close processor after the protocol layer has sent the response to the client
            netFile.setStatusFlag(NetworkFile.Flags.POST_CLOSE_FILE, true);
            return;
        }

        // Close the file
        diskInterface.closeFile(sess, tree, netFile);

        // If the fileInfo cache may have just had some content updated.
        if ( !netFile.isDirectory() && !netFile.isReadOnly()) {
            fileInfoCache.clear();
        }
    }

    @Override
    public void createDirectory(SrvSession sess, TreeConnection tree,
                                FileOpenParams params) throws IOException {
        diskInterface.createDirectory(sess, tree, params);
    }

    @Override
    public NetworkFile createFile(SrvSession sess, TreeConnection tree,
                                  FileOpenParams params) throws IOException {
        return diskInterface.createFile(sess, tree, params);
    }

    @Override
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws IOException {
        fileInfoCache.remove(dir);

        diskInterface.deleteDirectory(sess, tree, dir);
    }

    @Override
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws IOException {
        fileInfoCache.remove(name);

        diskInterface.deleteFile(sess, tree, name);
    }

    @Override
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException {
        diskInterface.flushFile(sess, tree, file);
    }

    @Override
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws IOException {
        return diskInterface.isReadOnly(sess, ctx);
    }

    @Override
    public NetworkFile openFile(SrvSession sess, TreeConnection tree,
                                FileOpenParams params) throws IOException {
        return diskInterface.openFile(sess, tree, params);
    }

    @Override
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file,
                        byte[] buf, int bufPos, int siz, long filePos) throws IOException {
        return diskInterface.readFile(sess, tree, file, buf, bufPos, siz, filePos);
    }

    @Override
    public void renameFile(SrvSession sess, TreeConnection tree,
                           String oldName, String newName, NetworkFile netFile) throws IOException {
        diskInterface.renameFile(sess, tree, oldName, newName, netFile);
    }

    @Override
    public long seekFile(SrvSession sess, TreeConnection tree,
                         NetworkFile file, long pos, int typ) throws IOException {
        return diskInterface.seekFile(sess, tree, file, pos, typ);
    }

    @Override
    public void setFileInformation(SrvSession sess, TreeConnection tree,
                                   String name, FileInfo info) throws IOException {
        diskInterface.setFileInformation(sess, tree, name, info);
    }

    @Override
    public SearchContext startSearch(SrvSession sess, TreeConnection tree,
                                     String searchPath, int attrib, EnumSet<SearchFlags> flags) throws FileNotFoundException {
        return diskInterface.startSearch(sess, tree, searchPath, attrib, flags);
    }

    @Override
    public void truncateFile(SrvSession sess, TreeConnection tree,
                             NetworkFile file, long siz) throws IOException {
        diskInterface.truncateFile(sess, tree, file, siz);
    }

    @Override
    public int writeFile(SrvSession sess, TreeConnection tree,
                         NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws IOException {
        return diskInterface.writeFile(sess, tree, file, buf, bufoff, siz, fileoff);
    }

    @Override
    public void registerContext(DeviceContext ctx)
            throws DeviceContextException {
        diskInterface.registerContext(ctx);
    }

    public void setDiskInterface(ExtendedDiskInterface diskInterface) {
        this.diskInterface = diskInterface;
    }

    public ExtendedDiskInterface getDiskInterface() {
        return diskInterface;
    }

    public void setDiskSizeInterface(DiskSizeInterface diskSizeInterface) {
        this.diskSizeInterface = diskSizeInterface;
    }

    public DiskSizeInterface getDiskSizeInterface() {
        return diskSizeInterface;
    }

    public void setIoctlInterface(IOCtlInterface iocltlInterface) {
        this.ioctlInterface = iocltlInterface;
    }

    public IOCtlInterface getIoctlInterface() {
        return ioctlInterface;
    }

    @Override
    public void onMoveNode(ChildAssociationRef oldChildAssocRef,
                           ChildAssociationRef newChildAssocRef) {
        if (fileInfoCache.contains(oldChildAssocRef.getChildRef())) {
            logger.debug("cached node moved - clear the cache");
            fileInfoCache.clear();
        }
    }

    @Override
    public void onDeleteNode(ChildAssociationRef oldChildAssocRef, boolean isArchived) {
        if (fileInfoCache.contains(oldChildAssocRef.getChildRef())) {
            logger.debug("cached node deleted - clear the cache");
            fileInfoCache.clear();
        }
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public PolicyComponent getPolicyComponent() {
        return policyComponent;
    }

    public void setOpLockInterface(OpLockInterface opLockInterface) {
        this.opLockInterface = opLockInterface;
    }

    public OpLockInterface getOpLockInterface() {
        return opLockInterface;
    }

    @Override
    public OpLockManager getOpLockManager(SrvSession sess, TreeConnection tree) {
        return opLockInterface.getOpLockManager(sess, tree);
    }

    @Override
    public boolean isOpLocksEnabled(SrvSession sess, TreeConnection tree) {
        return opLockInterface.isOpLocksEnabled(sess, tree);
    }

    @Override
    public LockManager getLockManager(SrvSession sess, TreeConnection tree) {
        return getFileLockingInterface().getLockManager(sess, tree);
    }


    public void setFileLockingInterface(FileLockingInterface fileLockingInterface) {
        this.fileLockingInterface = fileLockingInterface;
    }


    public FileLockingInterface getFileLockingInterface() {
        return fileLockingInterface;
    }

    /**
     * Return the filesystem version interface
     *
     * @return VersionInterface
     */
    public VersionInterface getVersionInterface() {
        return versionInterface;
    }

    /**
     * Set the filesystem version interface
     *
     * @param verInterface VersionInterface
     */
    public void setVersionInterface(VersionInterface verInterface) {
        this.versionInterface = verInterface;
    }

    /**
     * Set the client API interface
     *
     * @param clientApi ClientAPI
     */
    public void setClientAPI(ContentDiskDriver2 clientApi) {
        clientAPI = clientApi;
    }

    /**
     * Check if the post close processor feature  is enabled
     *
     * @return boolean
     */
    public final boolean getEnablePostClose() { return usePostClose; }

    /**
     * Enable/disable the post close processor feature
     *
     * @param ena boolean
     */
    public final void setEnablePostClose(boolean ena) { usePostClose = ena; }

    /**
     * Get the list of available previous versions for the specified path
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file
     * @return List &lt; FileVersionInfo &gt;
     * @throws IOException If an error occurs.
     */
    public List<FileVersionInfo> getPreviousVersions(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException {
        return versionInterface.getPreviousVersions(sess, tree, file);
    }

    /**
     * Open a previous version of a file
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @throws IOException If an error occurs.
     */
    public NetworkFile openPreviousVersion(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws IOException {
        return versionInterface.openPreviousVersion(sess, tree, params);
    }

    /**
     * Return the file information for a particular version of a file
     *
     * @param sess      Server session
     * @param tree      Tree connection
     * @param path      String
     * @param timeStamp long
     * @return FileInfo
     * @throws IOException If an error occurs.
     */
    public FileInfo getPreviousVersionFileInformation(SrvSession sess, TreeConnection tree, String path, long timeStamp)
            throws IOException {
        return versionInterface.getPreviousVersionFileInformation(sess, tree, path, timeStamp);
    }

    //-------------------- ClientAPI implementation --------------------//
    @Override
    public ClientAPIInterface getClientAPI(SrvSession<?> sess, TreeConnection tree) {
        return clientAPI.getClientAPI( sess, tree);
    }

    //-------------------- PostCloseProcessor implementation --------------------//

    /**
     * Post close the file, called after the protocol layer has sent the close response to the client.
     *
     * @param sess    Server session
     * @param tree    Tree connection.
     * @param netFile Network file context.
     * @throws IOException If an error occurs.
     */
    public void postCloseFile(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws IOException
    {
        // Close the file
        diskInterface.closeFile(sess, tree, netFile);

        // If the fileInfo cache may have just had some content updated.
        if (! netFile.isDirectory() && ! netFile.isReadOnly()) {
            fileInfoCache.clear();
        }
    }
}