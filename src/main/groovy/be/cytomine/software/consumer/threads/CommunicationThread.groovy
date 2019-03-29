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

import be.cytomine.software.repository.threads.RepositoryManagerThread
import com.rabbitmq.client.Channel
import groovy.util.logging.Log4j

@Log4j
class CommunicationThread implements Runnable {

    RepositoryManagerThread repositoryManagerThread

    Channel channel
    String queueName
    String exchangeName

    @Override
    void run() {
        channel.exchangeDeclare(exchangeName, "direct", true)
        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchangeName, "")
        RabbitMQConsumerComThread consumer= new RabbitMQConsumerComThread(channel,repositoryManagerThread)
        channel.basicConsume(queueName, true, consumer)
    }

}
