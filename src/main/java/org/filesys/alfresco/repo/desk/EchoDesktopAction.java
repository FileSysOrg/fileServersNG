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
package org.filesys.alfresco.repo.desk;

import java.util.Date;

import org.filesys.alfresco.base.DesktopAction;
import org.filesys.alfresco.base.DesktopParams;
import org.filesys.alfresco.base.DesktopResponse;

/**
 * Echo Desktop Action Class
 * 
 * <p>Simple desktop action that echoes back the received string.
 * 
 * @author gkspencer
 */
public class EchoDesktopAction extends DesktopAction {

	/**
	 * Class constructor
	 */
	public EchoDesktopAction()
	{
		super( 0, PreConfirmAction);
	}
	
	@Override
	public String getConfirmationString() {
		return "Run echo action";
	}

	@Override
	public DesktopResponse runAction(DesktopParams params) {

		// Return a text message
		
		return new DesktopResponse(StsSuccess, "Test message from echo action at " + new Date()); 
	}
}
