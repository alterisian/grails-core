/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.datasource

import grails.util.Environment
import grails.util.GrailsUtil
import grails.util.Metadata

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.SQLException
import javax.sql.DataSource

import org.apache.commons.dbcp.BasicDataSource
import org.codehaus.groovy.grails.exceptions.GrailsConfigurationException
import org.codehaus.groovy.grails.orm.support.TransactionManagerPostProcessor
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jndi.JndiObjectFactoryBean

/**
 * A plugin that handles the configuration of a DataSource within Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class DataSourceGrailsPlugin {

    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [core: GrailsUtil.getGrailsVersion()]

    def watchedResources = "file:./grails-app/conf/DataSource.groovy"

    def doWithSpring = {
        transactionManagerPostProcessor(TransactionManagerPostProcessor)

        if (parentCtx?.containsBean("dataSource")) {
            return
        }

        def ds = application.config.dataSource
        if (!ds && application.domainClasses.size() == 0) {
            log.info "No data source or domain classes found. Data source configuration skipped"
            return
        }

        if (ds.jndiName) {
            dataSourceUnproxied(JndiObjectFactoryBean) {
                jndiName = ds.jndiName
                expectedType = DataSource
            }
            dataSource(TransactionAwareDataSourceProxy, dataSourceUnproxied)
            return
        }

        def properties = {
            def driver = ds?.driverClassName ? ds.driverClassName : "org.h2.Driver"
            driverClassName = driver
            url = ds?.url ? ds.url : "jdbc:h2:mem:grailsDB"
            boolean defaultDriver = (driver == "org.h2.Driver")
            String theUsername = ds?.username ?: (defaultDriver ? "sa" : null)
            if (theUsername != null) {
                username = theUsername
            }
            if (ds?.password)  {
                def thePassword = ds.password
                if (ds?.passwordEncryptionCodec) {
                    def encryptionCodec = ds?.passwordEncryptionCodec
                    if (encryptionCodec instanceof Class) {
                        try {
                            password = encryptionCodec.decode(thePassword)
                        }
                        catch (Exception e) {
                            throw new GrailsConfigurationException(
                                "Error decoding dataSource password with codec [$encryptionCodec]: ${e.message}", e)
                        }
                    }
                    else {
                        encryptionCodec = encryptionCodec.toString()
                        def codecClass = application.codecClasses.find { it.name?.equalsIgnoreCase(encryptionCodec) || it.fullName == encryptionCodec}?.clazz
                        try {
                            if (!codecClass) {
                                codecClass = Class.forName(encryptionCodec, true, application.classLoader)
                            }
                            if (codecClass) {
                               password = codecClass.decode(thePassword)
                            }
                            else {
                                throw new GrailsConfigurationException(
                                      "Error decoding dataSource password. Codec class not found for name [$encryptionCodec]")
                            }
                        }
                        catch (ClassNotFoundException e) {
                            throw new GrailsConfigurationException(
                                  "Error decoding dataSource password. Codec class not found for name [$encryptionCodec]: ${e.message}", e)
                        }
                        catch(Exception e) {
                            throw new GrailsConfigurationException(
                                  "Error decoding dataSource password with codec [$encryptionCodec]: ${e.message}", e)
                        }
                    }
                }
                else {
                    password = ds.password
                }
            }
            else {
                String thePassword = defaultDriver ? "" : null
                if (thePassword != null) {
                    password = thePassword
                }
            }
        }

        if (ds) {
            log.info("[RuntimeConfiguration] Configuring data source for environment: ${Environment.current}")
            def bean
            if (ds.pooled) {
                bean = dataSourceUnproxied(BasicDataSource, properties)
                bean.destroyMethod = "close"
            }
            else {
                bean = dataSourceUnproxied(DriverManagerDataSource, properties)
            }
            // support for setting custom properties (for example maxActive) on the dataSource bean
            def dataSourceProperties = ds.properties
            if (dataSourceProperties != null) {
                if (dataSourceProperties instanceof Map) {
                    dataSourceProperties.each { entry ->
                        log.debug("Setting property on dataSource bean ${entry.key} -> ${entry.value}")
                        bean.setPropertyValue(entry.key.toString(), entry.value)
                    }
                }
                else {
                    log.warn("dataSource.properties is not an instanceof java.util.Map, ignoring")
                }
            }
        }
        else {
            def bean = dataSourceUnproxied(BasicDataSource, properties)
            bean.destroyMethod = "close"
        }

        dataSource(TransactionAwareDataSourceProxy, dataSourceUnproxied)
    }

    def onChange = {
        restartContainer()
    }

    def onShutdown = { event ->

        ApplicationContext appCtx = event.ctx

        if (appCtx?.containsBean("dataSource")) {
            DataSource dataSource = appCtx.dataSource
            Connection connection
            try {
                connection = dataSource.getConnection()
                def dbName =connection.metaData.databaseProductName
                if (dbName == 'HSQL Database Engine') {
                    connection.createStatement().executeUpdate('SHUTDOWN')
                }
            } finally {
                connection?.close()
            }
        }

        if (Metadata.current.warDeployed) {
            deregisterJDBCDrivers()
        }
    }

    private void deregisterJDBCDrivers() {
        Enumeration<Driver> drivers = DriverManager.getDrivers()
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement()
            try {
                DriverManager.deregisterDriver(driver)
            } catch (SQLException e) {
                log.error("Error deregistering JDBC driver ["+driver+"]: " + e.getMessage(), e)
            }
        }
    }
}
