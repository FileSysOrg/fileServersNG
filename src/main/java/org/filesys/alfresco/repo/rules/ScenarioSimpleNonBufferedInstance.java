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
package org.filesys.alfresco.repo.rules;

import java.util.ArrayList;

import org.filesys.alfresco.repo.TempNetworkFile;
import org.filesys.alfresco.repo.rules.commands.CloseFileCommand;
import org.filesys.alfresco.repo.rules.commands.CompoundCommand;
import org.filesys.alfresco.repo.rules.commands.CreateFileCommand;
import org.filesys.alfresco.repo.rules.commands.DoNothingCommand;
import org.filesys.alfresco.repo.rules.commands.MoveFileCommand;
import org.filesys.alfresco.repo.rules.commands.DeleteFileCommand;
import org.filesys.alfresco.repo.rules.commands.OpenFileCommand;
import org.filesys.alfresco.repo.rules.commands.ReduceQuotaCommand;
import org.filesys.alfresco.repo.rules.commands.RemoveTempFileCommand;
import org.filesys.alfresco.repo.rules.commands.RenameFileCommand;
import org.filesys.alfresco.repo.rules.operations.CloseFileOperation;
import org.filesys.alfresco.repo.rules.operations.CreateFileOperation;
import org.filesys.alfresco.repo.rules.operations.DeleteFileOperation;
import org.filesys.alfresco.repo.rules.operations.MoveFileOperation;
import org.filesys.alfresco.repo.rules.operations.OpenFileOperation;
import org.filesys.alfresco.repo.rules.operations.RenameFileOperation;
import org.filesys.server.filesys.NetworkFile;

/**
 * The Simple Standard Scenario is what will be done if no other 
 * scenario intervenes.
 */
public class ScenarioSimpleNonBufferedInstance implements ScenarioInstance
{
    private Ranking ranking = Ranking.LOW;
    
    @Override
    public Command evaluate(Operation operation)
    {
        if(operation instanceof CreateFileOperation)
        {
            CreateFileOperation c = (CreateFileOperation)operation;
            return new CreateFileCommand(c.getName(), c.getRootNodeRef(), c.getPath(), c.getAllocationSize(), c.isHidden());
        }
        else if(operation instanceof DeleteFileOperation)
        {
            DeleteFileOperation d = (DeleteFileOperation)operation;
            return new DeleteFileCommand(d.getName(), d.getRootNodeRef(), d.getPath());
        }
        else if(operation instanceof RenameFileOperation)
        {
            RenameFileOperation r = (RenameFileOperation)operation;
            return new RenameFileCommand(r.getFrom(), r.getTo(), r.getRootNodeRef(), r.getFromPath(), r.getToPath());
        }
        else if(operation instanceof MoveFileOperation)
        {
            MoveFileOperation m = (MoveFileOperation)operation;
            return new MoveFileCommand(m.getFrom(), m.getTo(), m.getRootNodeRef(), m.getFromPath(), m.getToPath());
        }
        else if(operation instanceof OpenFileOperation)
        {
            OpenFileOperation o = (OpenFileOperation)operation;
            return new OpenFileCommand(o.getName(), o.getMode(), o.isTruncate(), o.getRootNodeRef(), o.getPath());
        }
        else if(operation instanceof CloseFileOperation)
        {
            CloseFileOperation c = (CloseFileOperation)operation;
            
            NetworkFile file = c.getNetworkFile();
            
            ArrayList<Command> commands = new ArrayList<Command>();
            ArrayList<Command> postCommitCommands = new ArrayList<Command>();
            ArrayList<Command> postErrorCommands = new ArrayList<Command>();
            
            commands.add(new CloseFileCommand(c.getName(), file, c.getRootNodeRef(), c.getPath()));
            
            // postErrorCommands.add(new RemoveNoContentFileOnError(c.getName(), c.getRootNodeRef(), c.getPath()));
            
            if(c.isDeleteOnClose())
            {
                postCommitCommands.add(new ReduceQuotaCommand(c.getName(), file, c.getRootNodeRef(), c.getPath()));
            }
            
            if (file instanceof TempNetworkFile)
            { 
                postCommitCommands.add(new RemoveTempFileCommand((TempNetworkFile) file));
            }

            return new CompoundCommand(commands, postCommitCommands, postErrorCommands);  
            
        }
        else return new DoNothingCommand();
    }

    @Override
    public boolean isComplete()
    {
        /** 
         * This instance is always complete
         */
        return true;
    }

    
    @Override
    public Ranking getRanking()
    {
        return ranking;
    }
    
    public void setRanking(Ranking ranking)
    {
        this.ranking = ranking;
    }
    
    public String toString()
    {
        return "ScenarioSimpleNonBuffered default instance";
    }
}
