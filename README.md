# fileServersNG
The replacement Alfresco file servers subsystem based on the JFileServer file server code. 

Builds the fileserversng-v62 AMP for Alfresco 6.2 using the Alfresco 4.1 SDK.

To build the fileServersNG AMP use `mvn clean package`, this will create the fileserversng-v62-1.0.0.amp
file in the target/ folder.

For more complete instructions on how to build and deploy the fileServersNG subsystem see
[here](http://www.filesys.org/wiki/index.php/How_to_build_and_deploy_the_fileServersNG_subsystem).

A Docker image is available containing an Alfresco 6.2 installation with the fileServersNG file server
replacement subsystem deployed, see [here](http://www.filesys.org/wiki/index.php/Using_the_fileServersNG_Docker_Images)
for details of how to download and configure the Docker image.
