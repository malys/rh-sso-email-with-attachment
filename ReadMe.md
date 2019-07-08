# RH-SSO email with attachment providers

This RH-SSO email provider is able to attach files in email. 

## Versioning

Refer to [Semantic Versioning 2.0.0](http://semver.org/).

## Deployment on Maven repository

 ```bash
 mvn clean deploy -Pnexus
 ```
**Nexus** maven profile defines:

    <nexus.url.release>${nexus.url}/content/repositories/releases</nexus.url.release>
    <nexus.url.snapshot>${nexus.url}/content/repositories/snapshots</nexus.url.snapshot>


### Deployment

Create a JBoss module:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.1" name="com.lyra.idm.keycloak.email">
  <resources>
    <resource-root path="email-0.0.1.jar" />
  </resources>
  <dependencies>
    <module name="javax.activation.api" />
	<module name="javax.mail.api" />
    <module name="org.jboss.logging" />
    <module name="org.keycloak.keycloak-common" />
    <module name="org.keycloak.keycloak-core" />
    <module name="org.keycloak.keycloak-server-spi" />
    <module name="org.keycloak.keycloak-server-spi-private" />
    <module name="org.keycloak.keycloak-services" />
  </dependencies>
</module>
```

## Configuration

### Standalone.xml

    include: Attach <Theme path>/email/resources/xxx/<filename>.
    parent: Attach the contains of <Theme path>/email/resources/xxx/ folder.

```xml
<spi name="emailSender">
    <default-provider>emailwithattachment</default-provider>
    <provider name="emailwithattachment" enabled="true">
        <properties>
            <property name="include" value="img/logo.png"/>
            <property name="parent" value="true"/>
        </properties>
    </provider>
</spi>
```

### CLI

```
/subsystem=keycloak-server/:write-attribute(name=providers,value=["classpath:${jboss.home.dir}/providers/*","module:com.lyra.idm.keycloak.email:0.0.1"])
/subsystem=keycloak-server/spi=emailSender/:add
/subsystem=keycloak-server/spi=emailSender/:write-attribute(name=default-provider,value=emailwithattachment)
/subsystem=keycloak-server/spi=emailSender/provider=emailwithattachment/:add(enabled=true)
/subsystem=keycloak-server/spi=emailSender/provider=emailwithattachment/:write-attribute(name=properties,value={"include" => "img/logo.png","parent" => "true"})
```

## Release Notes

### 0.0.1

* file attachment
* folder contains attachment

## Author

Sylvain M. for [Lyra](https://lyra.com).

