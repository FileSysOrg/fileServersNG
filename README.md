# fileServersNG
The replacement Alfresco file servers subsystem based on the JFileServer Enterprise file server code,
with support for the latest SMB2 and SMB3 protocols, including hardware accelerated SMB3 for high performance
secure file transfers.

Builds the fileserversng AMP for Alfresco supporting Alfresco versions 6.2 to 26.1 using the Alfresco 4.1 SDK.

To build the fileServersNG AMP use `mvn clean install`, this will create the fileserversng-nn.m.amp. We now use the same
version numbering as Alfresco, where `nn` is the two digit year of the release and `m` is the release number for that
year, eg. fileserversng-26.1.amp.

## Running With Docker
There is a separate project for building Docker images for various versions of Alfresco Community Edition [here](https://github.com/FileSysOrg/fileserversNG-docker).

The Docker builds include a docker-compose.yml for running each version.

To enable the file server SMB2/SMB3 functionality you will need a fileServersNG licence key. To request a trial licence
email [info@filesys.org](mailto:info@filesys.org).

More information is available on the filesys.org Wiki pages [here](http://www.filesys.org/wiki/index.php).

## Maven Repository

The fileServersNG project is also the Maven repository for the fileServersNG AMP. To access the repository in your
pom.xml add the following sections :-

    <dependencies>
        <!-- fileServersNG AMP -->
        <dependency>
            <groupId>org.filesys</groupId>
            <artifactId>fileserversng</artifactId>
            <version>...</version>
        </dependency>
    </dependencies>

    <repositories>

        <!-- fileServersNG AMP GitHub Maven repository -->
        <repository>
            <id>fileServersNG-mvn-repo</id>
            <url>https://github.com/FileSysOrg/fileServersNG/raw/mvn-repo/</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>always</updatePolicy>
            </snapshots>
        </repository>
    </repositories>
