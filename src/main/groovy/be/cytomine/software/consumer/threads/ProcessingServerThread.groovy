package be.cytomine.software.consumer.threads

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

import be.cytomine.client.models.ProcessingServer
import be.cytomine.software.communication.SSH
import be.cytomine.software.consumer.Main
import be.cytomine.software.processingmethod.AbstractProcessingMethod
import com.rabbitmq.client.Channel
import groovy.util.logging.Log4j

@Log4j
class ProcessingServerThread implements Runnable {

    private Channel channel
    private AbstractProcessingMethod processingMethod
    private ProcessingServer processingServer
    private def mapMessage
    def runningJobs = [:]

    ProcessingServerThread(Channel channel, def mapMessage, ProcessingServer processingServer) {
        this.channel = channel
        this.mapMessage = mapMessage
        updateProcessingServer(processingServer)
    }

    def updateProcessingServer(def newProcessingServer) {
        this.processingServer = newProcessingServer
        try {
            log.info("${this.processingServer.getStr("processingMethodName")}")
            processingMethod = AbstractProcessingMethod.newInstance(this.processingServer.getStr("processingMethodName"))
            processingMethod.communication = new SSH(
                    host: processingServer.getStr("host"),
                    port: processingServer.getStr("port") as Integer,
                    user: processingServer.getStr("username"),
                    keyFilePath: Main.configFile.cytomine.software.sshKeysFile
            )
            log.info("Processing server : ${processingServer.getStr("name")}")
            log.info("================================================")
            log.info("host : ${processingServer.getStr("host")}")
            log.info("port : ${processingServer.getStr("port")}")
            log.info("user : ${processingServer.getStr("username")}")
            log.info("================================================")

        } catch (ClassNotFoundException ex) {
            log.info(ex.toString())
        }
    }

    @Override
    void run() {

        RabbitMQConsumerProcessingServer consumer= new RabbitMQConsumerProcessingServer(processingServer,mapMessage,processingMethod,channel, runningJobs,this)
        channel.basicConsume(mapMessage["name"] as String, true, consumer)
    }

}
