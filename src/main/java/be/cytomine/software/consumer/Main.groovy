package be.cytomine.software.consumer

/*
 * Copyright (c) 2009-2018. Authors: see NOTICE file.
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

import be.cytomine.client.Cytomine
import be.cytomine.client.collections.ProcessingServerCollection
import be.cytomine.client.collections.SoftwareCollection
import be.cytomine.client.collections.SoftwareUserRepositoryCollection
import be.cytomine.client.models.Software
import be.cytomine.client.models.SoftwareUserRepository
import be.cytomine.software.consumer.threads.CommunicationThread
import be.cytomine.software.consumer.threads.ProcessingServerThread
import be.cytomine.software.repository.AbstractRepositoryManager
import be.cytomine.software.repository.SoftwareManager
import be.cytomine.software.repository.threads.RepositoryManagerThread
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import org.apache.log4j.PropertyConfigurator

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Log4j
class Main {

    static def configFile = new ConfigSlurper().parse(new File("config.groovy").toURI().toURL())

    static Cytomine cytomine
    static Connection connection
    static Channel channel

    static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");

        log.info("GROOVY_HOME : ${System.getenv("GROOVY_HOME")}")
        log.info("PATH : ${System.getenv("PATH")}")

        // Create the directory for logs
        def logsDirectory = new File((String) configFile.logsDirectory)
        if (!logsDirectory.exists()) logsDirectory.mkdirs()

        // Create the directory for software data
        def dataDirectory = new File((String) configFile.dataDirectory)
        if (!dataDirectory.exists()) dataDirectory.mkdirs()

        cytomine = new Cytomine(
                configFile.cytomineCoreURL as String,
                configFile.publicKey as String,
                configFile.privateKey as String
        )

        log.info("Launch repository thread")
        def repositoryManagementThread = launchRepositoryManagerThread()

        log.info("Create rabbitMQ connection")
        createRabbitMQConnection()

        log.info("Launch communication thread")
        launchCommunicationThread(repositoryManagementThread)

        log.info("Launch processing server threads")
        launchProcessingServerQueues()
    }

    static void createRabbitMQConnection() {
        ConnectionFactory connectionFactory = new ConnectionFactory()
        connectionFactory.setHost(configFile.rabbitAddress as String)
        connectionFactory.setUsername(configFile.rabbitUsername as String)
        connectionFactory.setPassword(configFile.rabbitPassword as String)
        connection = connectionFactory.newConnection()
        channel = connection.createChannel()
    }

    static RepositoryManagerThread launchRepositoryManagerThread() {
        def repositoryManagers = []

        SoftwareUserRepositoryCollection softwareUserRepositories = cytomine.getSoftwareUserRepositories()
        for (int i = 0; i < softwareUserRepositories.size(); i++) {
            SoftwareUserRepository currentSoftwareUserRepository = softwareUserRepositories.get(i)

            try {
                SoftwareManager softwareManager = new SoftwareManager(
                        currentSoftwareUserRepository.getStr("username"),
                        currentSoftwareUserRepository.getStr("dockerUsername"),
                        currentSoftwareUserRepository.getStr("prefix"),
                        currentSoftwareUserRepository.getId()
                )

                def repositoryManagerExist = false
                for (SoftwareManager elem : repositoryManagers) {
                    if (softwareManager.getClass() == elem.getClass() &&
                            softwareManager.gitHubManager.username == elem.gitHubManager.username &&
                            softwareManager.dockerHubManager.username == softwareManager.dockerHubManager.username &&
                            !softwareManager.prefixes.contains(currentSoftwareUserRepository.getStr("prefix"))) {

                        SoftwareCollection softwareCollection = cytomine.getSoftwaresBySoftwareUserRepository(currentSoftwareUserRepository.getId())
                        for (int j = 0; j < softwareCollection.size(); j++) {
                            Software currentSoftware = softwareCollection.get(j)
                            def key = currentSoftwareUserRepository.getStr("prefix").trim().toLowerCase() + currentSoftwareUserRepository.getStr("name").trim().toLowerCase()
                            elem.softwareTable.put(key, currentSoftware)
                            elem.prefixes.add(currentSoftwareUserRepository.getStr("prefix"))
                        }

                        repositoryManagerExist = true
                        break
                    }
                }

                if (!repositoryManagerExist) {
                    SoftwareCollection softwareCollection = cytomine.getSoftwaresBySoftwareUserRepository(currentSoftwareUserRepository.getId())
                    for (int j = 0; j < softwareCollection.size(); j++) {
                        Software currentSoftware = softwareCollection.get(j)
                        def key = currentSoftwareUserRepository.getStr("prefix").trim().toLowerCase() + currentSoftware.getStr("name").trim().toLowerCase()
                        softwareManager.softwareTable.put(key, currentSoftware)
                    }

                    repositoryManagers.add(softwareManager)
                }

            } catch (Exception e) {
                log.info(e.getMessage())
            }
        }

        def repositoryManagerThread = new RepositoryManagerThread(repositoryManagers: repositoryManagers as ArrayList)
        def executorService = Executors.newSingleThreadExecutor()
        executorService.execute(repositoryManagerThread)

        return repositoryManagerThread
    }

    static void launchCommunicationThread(RepositoryManagerThread repositoryManagerThread) {
        Runnable communicationThread = new CommunicationThread(
                repositoryManagerThread: repositoryManagerThread,
                channel: channel,
                queueName: configFile.queueCommunication as String,
                exchangeName: configFile.exchangeCommunication as String
        )
        ExecutorService executorService = Executors.newSingleThreadExecutor()
        executorService.execute(communicationThread)
    }

    static void launchProcessingServerQueues() {
        JsonSlurper jsonSlurper = new JsonSlurper()

        ProcessingServerCollection processingServerCollection = cytomine.getProcessingServerCollection()
        for (int i = 0; i < processingServerCollection.size(); i++) {
            def queue = jsonSlurper.parseText(processingServerCollection.get(i).getStr("amqpQueue"))

            Runnable processingServerThread = new ProcessingServerThread(
                    channel,
                    queue,
                    processingServerCollection.get(i)
            )
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            executorService.execute(processingServerThread)
        }
    }

}
