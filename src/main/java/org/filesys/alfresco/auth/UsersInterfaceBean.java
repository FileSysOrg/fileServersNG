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

package org.filesys.alfresco.auth;

import org.alfresco.repo.management.subsystems.ActivateableBean;
import org.alfresco.repo.security.authentication.*;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.transaction.TransactionService;
import org.filesys.server.auth.UserAccount;
import org.filesys.server.auth.UsersInterface;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.extensions.config.ConfigElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Users Interface Bean Class
 *
 * <p>Provides the interface to Alfresco users, normalizing user names, returning the user account details including
 * the MD4 hashed password</p>
 *
 * @author gkspencer
 */
public class UsersInterfaceBean implements UsersInterface, ActivateableBean, InitializingBean, DisposableBean {

    // Logging
    protected static final Log logger = LogFactory.getLog(UsersInterfaceBean.class);

    // Authentication component
    private AuthenticationComponent m_authComponent;

    // Authentication service
    private AuthenticationService m_authService;

    // Person service
    private PersonService m_personService;

    // Transaction service
    private TransactionService m_transService;

    // Bean active, or not
    private boolean m_active = true;

    // Authentication component implementation
    private AuthenticationComponentImpl m_authComponentImpl;

    // MD4 hash decoder
    private MD4PasswordEncoder m_md4Encoder = new MD4PasswordEncoderImpl();

    /**
     * Sets the authentication component.
     *
     * @param authenticationComponent the authenticationComponent to set
     */
    public void setAuthenticationComponent(AuthenticationComponent authenticationComponent)
    {
        m_authComponent = authenticationComponent;
    }

    /**
     * Set the authentication service
     *
     * @param authService AuthenticationService
     */
    public void setAuthenticationService( AuthenticationService authService) {
        m_authService = authService;
    }

    /**
     * Sets the person service.
     *
     * @param personService the personService to set
     */
    public void setPersonService(PersonService personService)
    {
        m_personService = personService;
    }

    /**
     * Set the transaction service
     *
     * @param transService TransactionService
     */
    public void setTransactionService(TransactionService transService) { m_transService = transService; }

    /**
     * Return the authentication component.
     *
     * @return AuthenticationComponent
     */
    protected final AuthenticationComponent getAuthenticationComponent()
    {
        return m_authComponent;
    }

    /**
     * Return the authentication service
     *
     * @return AuthenticationService
     */
    protected final AuthenticationService getAuthenticationService() { return m_authService; }

    /**
     * Return the person service.
     *
     * @return PersonService
     */
    protected final PersonService getPersonService()
    {
        return m_personService;
    }

    /**
     * Return the transaction service
     *
     * @return TransactionService
     */
    protected final TransactionService getTransactionService() { return m_transService; }

    /*
     * (non-Javadoc)
     * @see org.alfresco.repo.management.subsystems.ActivateableBean#isActive()
     */
    public boolean isActive()
    {
        return m_active;
    }

    /**
     * Activates or deactivates the bean.
     *
     * @param active <code>true</code> if the bean is active and initialization should complete
     */
    public void setActive(boolean active)
    {
        m_active = active;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public final void afterPropertiesSet() throws InvalidConfigurationException
    {
        // Initialize the bean, properties have been set
        if ( isActive())
            initialize();
    }

    /**
     * Initialize the users interface
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception org.filesys.server.config.InvalidConfigurationException
     */
    @Override
    public void initializeUsers(ServerConfiguration config, ConfigElement params) {

    }

    /**
     * Handle tidy up on container shutdown.
     *
     * @throws Exception the exception
     */
    public void destroy()
        throws Exception
    {
    }

    /**
     * Initialize the users interface bean
     */
    public void initialize()
        throws InvalidConfigurationException {

        // Make sure all properties have been set
        if ( m_authComponent == null)
            throw new InvalidConfigurationException( "Missing authentication component property");

        if ( m_personService == null)
            throw new InvalidConfigurationException( "Missing person service property");

        // Check if the default authentiction component implementation is being used
        if ( m_authComponent instanceof AuthenticationComponentImpl)
            m_authComponentImpl = (AuthenticationComponentImpl) m_authComponent;
    }

    /**
     * Get user account details for the specified user
     *
     * @param userName String
     * @return UserAccount
     */
    @Override
    public UserAccount getUserAccount(String userName) {

        UserAccount userAccount = null;

        try {

            // Make sure we have an authentication component that supports MD4 hashed passwords
            if ( m_authComponentImpl != null){

                // Map the user name to a person, and check if the account is enabled
                String personName = mapUserNameToPerson(userName, true);

                // Create the user account details
                userAccount = new UserAccount(userName, null);
                userAccount.setMappedName(personName);

                // Get the MD4 password for the user
                String md4hash = m_authComponentImpl.getMD4HashedPassword(userName);

                userAccount.setMD4Password(m_md4Encoder.decodeHash(md4hash));
            }
            else if (logger.isDebugEnabled())
                logger.debug("No authentication component for MD4 passwords, user: " + userName);
        }
        catch ( AuthenticationException ex) {

        }

        // Return the user account details, or null
        return userAccount;
    }

    /**
     * Map the case insensitive logon name to the internal person object user name.
     * And optionally check whether the user is enabled.
     *
     * @param userName String
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
                            m_authComponent.setCurrentUser(userName);
                            personName = getPersonService().getUserIdentifier(userName);
                        }

                        if(checkEnabled && personName != null)
                        {
                             // Check the authenticator is enabled
                            boolean isAuthenticationEnabled = getAuthenticationService().getAuthenticationEnabled(personName);
                            if(!isAuthenticationEnabled)
                            {
                                logger.debug("Authentication service says user is not enabled");
                                throw new AuthenticationException("Authentication not enabled for:" + userName);
                            }

                            // Check the person is enabled
                            boolean isEnabled = m_personService.isEnabled(personName);
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
     * Does work in a transaction. This will be a writeable transaction unless the system is in read-only mode.
     *
     * @param callback a callback that does the work
     * @return the result, or <code>null</code> if not applicable
     */
    protected <T> T doInTransaction(RetryingTransactionHelper.RetryingTransactionCallback<T> callback)
    {
        // Get the transaction service
        TransactionService txService = getTransactionService();

        // The repository is read-only, we settle for a read-only transaction
        if (txService.isReadOnly() || !txService.getAllowWrite())
        {
            return txService.getRetryingTransactionHelper().doInTransaction(callback, true, false);
        }

        // Otherwise we want force a writable transaction
        return txService.getRetryingTransactionHelper().doInTransaction(callback,false,false);
    }
}
