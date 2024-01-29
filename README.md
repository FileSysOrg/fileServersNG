# fileServersNG
The replacement Alfresco file servers subsystem based on the JFileServer Enterprise file server code,
with support for the latest SMB2 and SMB3 protocols, including hardware accelerated SMB3 for high performance
secure file transfers.

Builds the fileserversng AMP for Alfresco supporting Alfresco versions 6.2 to 23.1 using the Alfresco 4.1 SDK.

To build the fileServersNG AMP use `mvn clean install`, this will create the fileserversng-nn.m.amp. We now use the same
version numbering as Alfresco, where `nn` is the two digit year of the release and `m` is the release number for that
year, eg. fileserversng-24.1.amp.

## Running With Docker
There are pre-built Docker images for fileServersNG using Alfresco 6.2, 7.4 and 23.1. To use the pre-built
images there are sample docker-compose.yml files in the Docker/ folder.

The supplied docker-compose.yml files need to be edited before they can be used. Look for the '<MAP-TO-A-LOCAL-FOLDER>'
placeholders.

To enable the file server SMB2/SMB3 functionality you will need a fileServersNG licence key. To request a trial licence
email info@filesys.org.

### Alfresco 6.2 docker-compose.yml
The fileServersNG Alfresco 6.2 setup uses Docker volumes to store the Alfresco content service, search service and 
database data. You will need to create these volumes using the following commands :-

    docker volume create fileserversng-v62-acs-volume
    docker volume create fileserversng-v62-db-volume
    docker volume create fileserversng-v62-ass-volume

More information is available on the filesys.org Wiki pages [here](http://www.filesys.org/wiki/index.php).
