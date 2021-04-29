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

package org.filesys.alfresco.auth.smb;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.management.subsystems.ActivateableBean;
import org.alfresco.repo.security.authentication.*;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.filesys.alfresco.base.AlfrescoClientInfo;
import org.filesys.alfresco.base.AlfrescoClientInfoFactory;
import org.filesys.server.auth.*;
import org.filesys.server.auth.spnego.NegTokenInit;
import org.filesys.server.auth.spnego.OID;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.smbv2.auth.V2EnterpriseSMBAuthenticator;
import org.ietf.jgss.Oid;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.Vector;

/**
 * Alfresco SMB Authenticator Class
 *
 * <p>Transactional authenticator that extends the default Enterprise SMB authenticator</p>
 *
 * @author gkspencer
 */
public class AlfrescoSMBAuthenticator extends V2EnterpriseSMBAuthenticator
    implements TransactionalSMBAuthenticator, ActivateableBean, InitializingBean, DisposableBean {

    // Logging
    protected static final Log logger = LogFactory.getLog(AlfrescoSMBAuthenticator.class);

    // MD4 hash decoder
    protected MD4PasswordEncoder m_md4Encoder = new MD4PasswordEncoderImpl();

    /** The authentication component. */
    private AuthenticationComponent authenticationComponent;

    /** The authentication service. */
    private AuthenticationService authenticationService;

    /** The node service. */
    private NodeService nodeService;

    /** The person service. */
    private PersonService personService;

    /** The transaction service. */
    private TransactionService transactionService;

    /** The authority service. */
    private AuthorityService authorityService;

    /** The disk interface. */
    private DiskInterface diskInterface;

    /** Is this component active, i.e. should it be used? */
    private boolean active = true;

    // Should we strip the @domain suffix from the Kerberos username?
    private boolean m_stripKerberosUsernameSuffix = true;

    // enable Kerberos API debug output
    private boolean m_kerberosDebug;

    // Disable use of NTLM logons
    private boolean m_disableNTLM;

    /**
     * Class constructor
     */
    public AlfrescoSMBAuthenticator()
    {
        super();
    }

    /**
     * Sets the authentication component.
     *
     * @param authenticationComponent
     *            the authenticationComponent to set
     */
    public void setAuthenticationComponent(AuthenticationComponent authenticationComponent)
    {
        this.authenticationComponent = authenticationComponent;
    }

    /**
     * Sets the authentication service.
     *
     * @param authenticationService
     *            the authenticationService to set
     */
    public void setAuthenticationService(AuthenticationService authenticationService)
    {
        this.authenticationService = authenticationService;
    }

    /**
     * Sets the node service.
     *
     * @param nodeService
     *            the nodeService to set
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * Sets the person service.
     *
     * @param personService
     *            the personService to set
     */
    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }

    /**
     * Sets the transaction service.
     *
     * @param transactionService
     *            the transactionService to set
     */
    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * Sets the authority service.
     *
     * @param authorityService
     *            the authorityService to set
     */
    public void setAuthorityService(AuthorityService authorityService)
    {
        this.authorityService = authorityService;
    }

    /**
     * Set the filesystem driver for the node service based filesystem.
     *
     * @param diskInterface
     *            DiskInterface
     */
    public void setDiskInterface(DiskInterface diskInterface)
    {
        this.diskInterface = diskInterface;
    }

    /**
     * Sets the SMB service account password. (the Principal should be configured in java.login.config)
     *
     * @param password
     *            the password to set
     */
    public void setPassword(String password)
    {
        m_password = password;
    }

    /**
     * Sets the SMB service account realm.
     *
     * @param realm
     *            the realm to set
     */
    public void setRealm(String realm)
    {
        m_krbRealm = realm;
    }

    /**
     * Sets the SMB service login configuration entry name.
     *
     * @param jaasConfigEntryName
     *            the loginEntryName to set
     */
    public void setJaasConfigEntryName(String jaasConfigEntryName)
    {
        m_loginEntryName = jaasConfigEntryName;
    }

    /**
     * Enable/disable Kerberos debug logging
     *
     * @param kerberosDebug boolean
     */
    public void setKerberosDebug(boolean kerberosDebug)
    {
        m_kerberosDebug = kerberosDebug;
    }

    /**
     * Enable/disable NTLM logons
     *
     * @param disableNTLM boolean
     */
    public void setDisableNTLM(boolean disableNTLM)
    {
        m_disableNTLM = disableNTLM;
    }

    /**
     * Enable/disable use of SPNGEO style logon
     *
     * @param useSPNEGO boolean
     */
    public void setUseSPNEGO(boolean useSPNEGO)
    {
        m_useRawNTLMSSP = !useSPNEGO;
    }

    /**
     * Enable/disable use of NTLM v1 logons
     *
     * @param disallowNTLMv1 boolean
     */
    public void setDisallowNTLMv1(boolean disallowNTLMv1)
    {
        m_acceptNTLMv1 = !disallowNTLMv1;
    }

    /**
     * Strip Kerberos username prefix
     *
     * @param stripKerberosUsernameSuffix boolean
     */
    public void setStripKerberosUsernameSuffix(boolean stripKerberosUsernameSuffix)
    {
        m_stripKerberosUsernameSuffix = stripKerberosUsernameSuffix;
    }

    /**
     * Set the Kerberos configuration path
     *
     * @param krbConfPath String
     */
    public void setKerberosConfiguration(String krbConfPath) {

        if ( krbConfPath != null && krbConfPath.length() > 0) {

            // Make sure the Kerberos configuration file exists
            if (Files.exists( Paths.get( krbConfPath), LinkOption.NOFOLLOW_LINKS)) {

                // Set the Kerberos configuration path
                System.setProperty( "java.security.krb5.conf", krbConfPath);
            }
            else {

                // Configuration file does not exist
                throw new AlfrescoRuntimeException("Kerberos configuration file does not exist - " + krbConfPath);
            }
        }
    }

    /**
     * Set the Java login configuration path
     *
     * @param loginConfPath String
     */
    public void setLoginConfiguration(String loginConfPath) {

        if ( loginConfPath != null && loginConfPath.length() > 0) {

            // Make sure the login configuration file exists
            if (Files.exists( Paths.get( loginConfPath), LinkOption.NOFOLLOW_LINKS)) {

                // Set the login configuration path
                System.setProperty( "java.security.auth.login.config", loginConfPath);
            }
            else {

                // Configuration file does not exist
                throw new AlfrescoRuntimeException("Login configuration file does not exist - " + loginConfPath);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.alfresco.repo.management.subsystems.ActivateableBean#isActive()
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Activates or deactivates the bean.
     *
     * @param active
     *            <code>true</code> if the bean is active and initialization should complete
     */
    public void setActive(boolean active)
    {
        this.active = active;
    }

    /**
     * Initialize the authenticator (after properties have been set)
     *
     * @exception InvalidConfigurationException
     */
    @Override
    public void initialize() throws InvalidConfigurationException
    {
        super.initialize();

        // Enable debugging in the main authenticator if debug logging is enabled
        if ( logger.isDebugEnabled())
            setDebug( true);

        // Make sure the correct client info factory is plugged in
        if ( ClientInfo.getFactory() == null || ClientInfo.getFactory() instanceof AlfrescoClientInfoFactory == false)
            ClientInfo.setFactory( new AlfrescoClientInfoFactory());

        // Check if Java API Kerberos debug output should be enabled
        if ( m_kerberosDebug)
        {
            // Enable Kerberos API debug output

            System.setProperty("sun.security.jgss.debug", "true");
            System.setProperty("sun.security.krb5.debug", "true");
        }

        // Check if Kerberos is enabled
        if (m_krbRealm != null && m_krbRealm.length() > 0)
        {
            // Get the login configuration entry name
            if (m_loginEntryName == null || m_loginEntryName.length() == 0)
            {
                throw new InvalidConfigurationException("Invalid login entry specified");
            }

            // Create a login context for the SMB server service
            try
            {
                // Login the SMB server service
                m_loginContext = new LoginContext(m_loginEntryName, this);
                m_loginContext.login();
            }
            catch (LoginException ex)
            {
                // Debug
                if (logger.isErrorEnabled())
                {
                    logger.error("SMB Kerberos authenticator error", ex);
                }

                throw new InvalidConfigurationException("Failed to login SMB server service");
            }

            // Get the SMB service account name from the subject
            Subject subj = m_loginContext.getSubject();
            Principal princ = subj.getPrincipals().iterator().next();

            m_accountName = princ.getName();

            if (logger.isDebugEnabled())
            {
                logger.debug("Logged on using principal " + m_accountName);
            }

            // Create the Oid list for the SPNEGO NegTokenInit, include NTLMSSP for fallback
            List<Oid> mechTypes = new Vector<Oid>();

            // DEBUG
            if (logger.isDebugEnabled())
            {
                logger.debug("Enabling mechTypes :-Kerberos5 MS-Kerberos5");
            }

            // Always enable Kerberos
            mechTypes.add(OID.KERBEROS5);
            mechTypes.add(OID.MSKERBEROS5);

            if (!m_disableNTLM)
            {
                mechTypes.add(OID.NTLMSSP);

                // DEBUG
                if (logger.isDebugEnabled())
                {
                    logger.debug(" Enabling NTLMSSP");
                }
            }

            // Build the NegTokenInit
            NegTokenInit negTokenInit = buildNegTokenInit( m_config.getServerName(), mechTypes);

            try {
                m_negTokenInit = negTokenInit.encode();
            }

            catch ( IOException ex) {

                // Debug
                if (logger.isDebugEnabled())
                    logger.debug("[SMB] Error creating SPNEGO NegTokenInit blob - " + ex.getMessage());

                throw new InvalidConfigurationException("Failed to create SPNEGO NegTokenInit blob (Kerberos)");
            }

            // Indicate that SPNEGO security blobs are being used
            m_useRawNTLMSSP = false;
        }
        // Check if raw NTLMSSP or SPNEGO/NTLMSSP should be used
        else if (!m_useRawNTLMSSP)
        {
            // SPNEGO security blobs are being used
            //
            // Create the Oid list for the SPNEGO NegTokenInit
            List<Oid> mechTypes = new Vector<Oid>();

            mechTypes.add(OID.NTLMSSP);

            // Build the NegTokenInit
            NegTokenInit negTokenInit = buildNegTokenInit( m_config.getServerName(), mechTypes);

            try {
                m_negTokenInit = negTokenInit.encode();
            }

            catch ( IOException ex) {

                // Debug
                if (logger.isDebugEnabled())
                    logger.debug("[SMB] Error creating SPNEGO NegTokenInit blob - " + ex.getMessage());

                throw new InvalidConfigurationException("Failed to create SPNEGO NegTokenInit blob (NTLMSSP)");
            }

        }
        else
        {
            // Use raw NTLMSSP security blobs
        }
    }

    /**
     * Called after a successful logon
     *
     * @param client ClientInfo
     */
    protected void onSuccessfulLogon( ClientInfo client) {

        // Make sure we got an Alfresco client information object
        if ( client instanceof AlfrescoClientInfo == false)
            throw new AlfrescoRuntimeException( "Expected AlfrescoClientInfo, got " + client.getClass().getTypeName());

        // Setup the authentication context
        AlfrescoClientInfo alfClient = (AlfrescoClientInfo) client;

        if ( alfClient.isGuest() || alfClient.isNullSession()) {

            // Setup the authentication context for a guest user, save the ticket to be used to setup the authentication
            // context for subsequent requests
            getAuthenticationService().authenticateAsGuest();
            alfClient.setAuthenticationTicket(getAuthenticationService().getCurrentTicket());

            // DEBUG
            if ( logger.isDebugEnabled())
                logger.debug("Logged on as guest");
        }
        else {

            // Check if the username suffix should be stripped
            String userName = client.getUserName();

            if ( m_stripKerberosUsernameSuffix == false && client.hasLoggedOnName()) {

                // Use the full user name that was used to logon, that includes the Kerberos realm
                userName = client.getLoggedOnName();
            }

            // Map the user name to an Alfresco person name
            String personName = mapUserNameToPerson( userName, true);

            // DEBUG
            if ( logger.isDebugEnabled())
                logger.debug("Mapped user name " + userName + " to person " + personName);

            try {

                // Set the authentication context to the current user/person
                authenticationComponent.setCurrentUser(personName); //, AuthenticationComponent.UserNameValidationMode.NONE);
            }
            catch ( AuthenticationException ex) {

                ex.printStackTrace();
            }

            // Save the current ticket to be used to setup the authentication context for subsequent requests
            // for this session/virtual circuit
            alfClient.setAuthenticationTicket(getAuthenticationService().getCurrentTicket());
        }

        // Check if the user is an administrator
        checkForAdminUserName( client);

        // Get the users home folder node, if available
        getHomeFolderForUser( client);
    }

    /**
     * Normalize a user name from a Kerberos/NTLM logon to a person name within Alfresco
     *
     * @param externalUserId String
     * @return String
     * @throws SMBSrvException
     */
    protected String normalizeUserId(String externalUserId)
        throws SMBSrvException
    {
        try
        {
            return mapUserNameToPerson(externalUserId, true);
        }
        catch (AuthenticationException e)
        {
            // Invalid user. Return a logon failure status
            logger.debug("Authentication Exception", e);
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public final void afterPropertiesSet() throws InvalidConfigurationException
    {
        // If the bean is active, call the overridable initialize method
        if (this.active)
        {
            initialize();
        }
    }

    /**
     * Handle tidy up on container shutdown.
     *
     * @throws Exception
     *             the exception
     */
    public void destroy() throws Exception
    {
        closeAuthenticator();
    }

    /**
     * Map the case insensitive logon name to the internal person object user name.
     * And optionally check whether the user is enabled.
     *
     *
     * @param userName
     *            String
     * @param checkEnabled
     * @return the user name
     */
    public final String mapUserNameToPerson(final String userName, final boolean checkEnabled)
    {
        if(logger.isDebugEnabled())
        {
            logger.debug("mapUserNameToPerson userName:" + userName + ", checkEnabled:" + checkEnabled);
        }

        // Do the lookup as the system user
        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<String>()
        {
            public String doWork() throws Exception
            {
                return doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<String>()
                {

                    public String execute() throws Throwable
                    {
                        String personName = getPersonService().getUserIdentifier(userName);

                        // Check if the person exists

                        if (personName == null)
                        {
                            // Force creation of a person if possible
                            authenticationComponent.setCurrentUser(userName);
                            personName = getPersonService().getUserIdentifier(userName);
                        }

                        if(checkEnabled && personName != null)
                        {
                            /**
                             * Check the authenticator is enabled
                             */
                            boolean isAuthenticationEnabled = getAuthenticationService().getAuthenticationEnabled(personName);
                            if(!isAuthenticationEnabled)
                            {
                                logger.debug("Authentication service says user is not enabled");
                                throw new AuthenticationException("Authentication not enabled for:" + userName);
                            }

                            /**
                             * Check the person is enabled
                             */
                            boolean isEnabled = personService.isEnabled(personName);
                            if(!isEnabled)
                            {
                                logger.debug("Person service says user is not enabled");
                                throw new AuthenticationException("Authentication not enabled for person:" + userName);
                            }
                        }

                        return personName == null ? userName : personName;
                    }
                });
            }
        }, AuthenticationUtil.getSystemUserName());
    }

    /**
     * Get the home folder for the user.
     *
     * @param client
     *            ClientInfo
     */
    protected final void getHomeFolderForUser(final ClientInfo client)
    {
        // Check if the client is an Alfresco client, and not a null logon

        if (client instanceof AlfrescoClientInfo == false || client.isNullSession() == true)
            return;

        final AlfrescoClientInfo alfClient = (AlfrescoClientInfo) client;

        // Get the home folder for the user

        doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>()
        {

            public Object execute() throws SMBSrvException
            {
                NodeRef homeSpaceRef = (NodeRef) getNodeService().getProperty(
                        getPersonService().getPerson(client.getUserName()), ContentModel.PROP_HOMEFOLDER);
                alfClient.setHomeFolder(homeSpaceRef);
                return null;
            }
        });
    }

    /**
     * Set the current authenticated user context for this thread.
     *
     * @param client ClientInfo or null to clear the context
     */
    public void setCurrentUser(final ClientInfo client)
    {
        // Check the account type and setup the authentication context

        // No need for a transaction to clear the context
        if (client == null || client.isNullSession())
        {
            // Clear the authentication, null user should not be allowed to do any service calls

            getAuthenticationComponent().clearCurrentSecurityContext();
            return;
        }
        if (client.isGuest() == false && client instanceof AlfrescoClientInfo)
        {
            // Set the authentication context for the request

            AlfrescoClientInfo alfClient = (AlfrescoClientInfo) client;
            if (alfClient.hasAuthenticationTicket())
            {
                boolean ticketFailed = false;

                try
                {
                    getAuthenticationService().validate(alfClient.getAuthenticationTicket());
                }
                catch (AuthenticationException e)
                {
                    // Indicate the existing ticket is bad

                    ticketFailed = true;

                    // DEBUG

                    if ( logger.isDebugEnabled())
                        logger.debug("Failed to validate ticket, user=" + client.getUserName() + ", ticket=" + alfClient.getAuthenticationTicket());
                }

                // If the ticket did not validate then try and get a new ticket for the user

                if ( ticketFailed == true) {

                    try {
                        String normalized = mapUserNameToPerson( client.getUserName(), false);
                        getAuthenticationComponent().setCurrentUser( normalized);
                        alfClient.setAuthenticationTicket(getAuthenticationService().getCurrentTicket());
                    }
                    catch ( AuthenticationException ex) {

                        // Cannot get a new ticket for the user

                        if ( logger.isErrorEnabled()) {
                            logger.error("Failed to get new ticket for user=" + client.getUserName());
                            logger.error( ex);
                        }

                        // Clear the ticket/security context

                        alfClient.setAuthenticationTicket(null);
                        getAuthenticationComponent().clearCurrentSecurityContext();
                    }
                }
            }
            else
            {
                getAuthenticationComponent().clearCurrentSecurityContext();
            }
        }
        else
        {
            // Enable guest access for the request

            getAuthenticationComponent().setGuestUserAsCurrentUser();
        }
    }

    /**
     * Return the authentication component.
     *
     * @return AuthenticationComponent
     */
    protected final AuthenticationComponent getAuthenticationComponent()
    {
        return this.authenticationComponent;
    }


    /**
     * Return the authentication service.
     *
     * @return AuthenticationService
     */
    protected final AuthenticationService getAuthenticationService()
    {
        return this.authenticationService;
    }

    /**
     * Return the node service.
     *
     * @return NodeService
     */
    protected final NodeService getNodeService()
    {
        return this.nodeService;
    }

    /**
     * Return the person service.
     *
     * @return PersonService
     */
    protected final PersonService getPersonService()
    {
        return this.personService;
    }

    /**
     * Return the transaction service.
     *
     * @return TransactionService
     */
    private final TransactionService getTransactionService()
    {
        return this.transactionService;
    }

    /**
     * Return the authority service.
     *
     * @return AuthorityService
     */
    protected final AuthorityService getAuthorityService() {
        return this.authorityService;
    }

    /**
     * Check if the user is an administrator user name.
     *
     * @param cInfo
     *            ClientInfo
     */
    protected final void checkForAdminUserName(final ClientInfo cInfo)
    {

        // Check if the user name is an administrator

        doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>()
        {

            public Object execute()
            {
                if (cInfo.getLogonType() == ClientInfo.LogonType.Normal
                        && getAuthorityService().isAdminAuthority(cInfo.getUserName()))
                {

                    // Indicate that this is an administrator logon

                    cInfo.setLogonType(ClientInfo.LogonType.Administrator);
                }
                return null;
            }
        });
    }

    /**
     * Does work in a transaction. This will be a writeable transaction unless the system is in read-only mode.
     *
     * @param callback
     *            a callback that does the work
     * @return the result, or <code>null</code> if not applicable
     */
    protected <T> T doInTransaction(RetryingTransactionHelper.RetryingTransactionCallback<T> callback)
    {
        // Get the transaction service

        TransactionService txService = getTransactionService();

        // the repository is read-only, we settle for a read-only transaction
        if (txService.isReadOnly() || !txService.getAllowWrite())
        {
            return txService.getRetryingTransactionHelper().doInTransaction(callback,
                    /* READ ONLY */ true,
                    /* DOES NOT REQUIRE NEW TRAN */false);
        }

        // otherwise we want force a writable transaction
        return txService.getRetryingTransactionHelper().doInTransaction(callback,
                /* READ/WRITE */ false,
                /* DOES NOT REQUIRE NEW TRAN */false);

    }

    @Override
    public ISMBAuthenticator.AuthStatus processSecurityBlobInTransaction(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
            throws SMBSrvException {

        // Try and do the logon
        AuthStatus authSts = AuthStatus.DISALLOW;

        final SMBSrvSession fSess = sess;
        final ClientInfo fClient = client;
        final SecurityBlob fSecBlob = secBlob;

        authSts = doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<AuthStatus>()
        {

            public AuthStatus execute() throws Throwable
            {
                // Process the security blob
                return processSecurityBlobInternal( fSess, fClient, fSecBlob);
            }
        });

        return authSts;
    }

    /**
     * Determine if Kerberos support is enabled
     *
     * @return boolean
     */
    private final boolean isKerberosEnabled()
    {
        return m_krbRealm != null && m_loginContext != null;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public boolean hasDebugOutput() {
        return logger.isDebugEnabled();
    }

    /**
     * Output debug logging
     *
     * @param dbg String
     */
    public void debugOutput(String dbg) {
        logger.debug( dbg);
    }

}
