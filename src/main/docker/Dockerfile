FROM ${docker.acs.image}:${alfresco.platform.version}

ARG TOMCAT_DIR=/usr/local/tomcat

USER root

# Copy Dockerfile to avoid an error if no JARs exist
COPY Dockerfile extensions/*.jar $TOMCAT_DIR/webapps/alfresco/WEB-INF/lib/

# Copy Dockerfile to avoid an error if no AMPs exist
COPY Dockerfile extensions/*.amp $TOMCAT_DIR/amps/
RUN java -jar $TOMCAT_DIR/alfresco-mmt/alfresco-mmt*.jar install \
              $TOMCAT_DIR/amps $TOMCAT_DIR/webapps/alfresco -directory -nobackup -force

COPY alfresco-global.properties $TOMCAT_DIR/shared/classes/alfresco-global.properties
COPY dev-log4j.properties $TOMCAT_DIR/shared/classes/alfresco/extension
COPY disable-webscript-caching-context.xml $TOMCAT_DIR/shared/classes/alfresco/extension

# Copy Dockerfile to avoid an error if no license file exists
COPY Dockerfile license/*.* $TOMCAT_DIR/webapps/alfresco/WEB-INF/classes/alfresco/extension/license/

USER ${USERNAME}

# Remove the older BouncyCastle library Jar
#RUN rm $TOMCAT_DIR/webapps/alfresco/WEB-INF/lib/bcprov-jdk15on-1.60.jar

# Folder for Kerberos authentication files
RUN mkdir /kerberos

# Install the Kerberos client apps
RUN yum -y install krb5-workstation

# Make sure the correct timezone is set for the Docker VM, to avoid Kerberos clock skew errors
RUN rm /etc/localtime
RUN ln -s /usr/share/zoneinfo/Europe/London /etc/localtime

# Volume mapping for the optional Kerberos configuration folder
VOLUME /kerberos
