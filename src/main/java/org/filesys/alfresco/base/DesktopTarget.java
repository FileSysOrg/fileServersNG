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

import org.alfresco.service.cmr.repository.NodeRef;
import org.filesys.server.filesys.FileName;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Desktop Target Class
 * 
 * <p>Contains the details of a target file/folder/node for a desktop action.
 * 
 * @author gkspencer
 */
public class DesktopTarget {

	// Target types
	public enum Type {
		File,
		Folder,
		CopiedFile,
		CopiedFolder,
		NodeRef,

		Invalid
	}

	// Target type
	private Type m_type;
	
	// Target path/id
	private String m_target;
	
	// Associated noderef
	private NodeRef m_noderef;
	
	/**
	 * Class constructor
	 * 
	 * @param typ int
	 * @param path String
	 */
	public DesktopTarget(int typ, String path)
	{
		m_type = typeFromInt( typ);
		m_target = path;
	}

	/**
	 * Class constructor
	 *
	 * @param typ Type
	 * @param path String
	 * @param node NodeRef
	 */
	public DesktopTarget(Type typ, String path, NodeRef node)
	{
		m_type = typ;
		m_target = path;
		m_noderef = node;
	}

	/**
	 * Return the target type
	 * 
	 * @return Type
	 */
	public final Type isType()
	{
		return m_type;
	}

	/**
	 * Check if the target is a file
	 *
	 * @return boolean
	 */
	public final boolean isFile() { return m_type == Type.File; }

	/**
	 * Check if the target is a folder
	 *
	 * @return boolean
	 */
	public final boolean isFolder() { return m_type == Type.Folder; }

	/**
	 * Return the target path
	 *
	 * @return String
	 */
	public final String getPath() { return m_target; }

	/**
	 * Return the file extension from the path
	 *
	 * @return String
	 */
	public final String getExtension() {
		if ( m_target != null && !m_target.isEmpty()) {
			int idx = m_target.lastIndexOf( '.');
			if ( idx != -1)
				return m_target.substring( idx + 1);
		}

		return null;
	}

	/**
	 * Return the parent path of the target
	 *
	 * @return String
	 */
	public final String getParentPath() {
		if ( m_target != null && !m_target.isEmpty()) {
			return FileName.getParentPart( m_target);
		}

		return null;
	}

	/**
	 * Return the target path/id
	 * 
	 * @return String
	 */
	public final String getTarget()
	{
		return m_target;
	}

	/**
	 * Check if the associated node is valid
	 * 
	 * @return boolean
	 */
	public final boolean hasNodeRef()
	{
		return m_noderef != null;
	}
	
	/**
	 * Return the associated node
	 * 
	 * @return NodeRef
	 */
	public final NodeRef getNode()
	{
		return m_noderef;
	}

	/**
	 * Get a target type from an integer value
	 *
	 * @param ival int
	 * @return Type
	 */
	private Type typeFromInt( int ival) {
		Type typ = Type.Invalid;

		switch ( ival) {
			case 0:
				typ = Type.File;
				break;
			case 1:
				typ = Type.Folder;
				break;
			case 2:
				typ = Type.CopiedFile;
				break;
			case 3:
				typ = Type.CopiedFolder;
				break;
			case 4:
				typ = Type.NodeRef;
				break;
		}

		return typ;
	}

	/**
	 * Return the target type as a string
	 * 
	 * @return String
	 */
	public final String getTypeAsString()
	{
		return isType().name();
	}
	
	/**
	 * Set the associated node
	 * 
	 * @param node NodeRef
	 */
	public final void setNode(NodeRef node)
	{
		m_noderef = node;
	}
	
	/**
	 * Return the desktop target as a string
	 * 
	 * @return String
	 */
	public String toString()
	{
		StringBuilder str = new StringBuilder();
		
		str.append("[");
		str.append(getTypeAsString());
		str.append(":");
		str.append(getTarget());
		
		if ( hasNodeRef())
		{
			str.append(":");
			str.append(getNode());
		}
		str.append("]");
		
		return str.toString();
	}
}
