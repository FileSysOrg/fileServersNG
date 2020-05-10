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
package org.filesys.alfresco.base;

import org.filesys.smb.nt.NTIOCtl;

/**
 * Content Disk Driver I/O Control Codes Class
 * 
 * <p>Contains I/O control codes and status codes used by the content disk driver I/O control
 * implementation.
 * 
 * @author gkspencer
 */
public class IOControl
{
    // Custom I/O control codes
    
    public static final int CmdProbe      		= NTIOCtl.FsCtlCustom;
    public static final int CmdFileStatus 		= NTIOCtl.FsCtlCustom + 1;
    // Version 1 CmdCheckOut = NTIOCtl.FsCtlCustom + 2
    // Version 1 CmdCheckIn  = NTIOCtl.FsCtlCustom + 3
    public static final int CmdGetActionInfo	= NTIOCtl.FsCtlCustom + 4;
    public static final int CmdRunAction   		= NTIOCtl.FsCtlCustom + 5;
    public static final int CmdGetAuthTicket	= NTIOCtl.FsCtlCustom + 6;

    // I/O control request/response signature
    
    public static final String Signature   = "ALFRESCO";
    
    // I/O control interface version id
    
    public static final int Version				= 2;
    
    // Boolean field values
    
    public static final int True                = 1;
    public static final int False               = 0;
    
    // File status field values
    //
    // Node type
    
    public static final int TypeFile            = 0;
    public static final int TypeFolder          = 1;
    
    // Lock status
    
    public static final int LockNone            = 0;
    public static final int LockRead            = 1;
    public static final int LockWrite           = 2;
}
