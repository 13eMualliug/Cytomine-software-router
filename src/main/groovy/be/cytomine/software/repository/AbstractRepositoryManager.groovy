package be.cytomine.software.repository

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

import groovy.util.logging.Log4j

@Log4j
abstract class AbstractRepositoryManager {

    String username

    static def newInstance(String provider, String username, String prefix = "") throws ClassNotFoundException {
        def providers = new ConfigSlurper()
                .parse(new File("src/main/resources/providers.groovy")
                .toURI()
                .toURL())

        String className = providers.get(provider)
        if (className == null) {
            throw new ClassNotFoundException("No class associated with the provider : ${provider}")
        }

        def instance = Class
                .forName("be.cytomine.software.repository." + className)
                .getConstructor(String.class, String.class)
                .newInstance(username, prefix)

        return instance
    }

    AbstractRepositoryManager(String username) {
        this.username = username
        /*it's totally possible to be block by this function.... Why? well, it's not a bug. I mean, the github API was designed like this:
        when it receives too much request in on hour by a certain IP, it "block" this ip... It's not definitive of course...
        Several solutions: first one is to wait ahahahah. Not a good one
        second one is to change your ip address. Vpn,...
        third one is to use a local Json to make local test*/
        connectToRepository(this.username)
    }

    def abstract connectToRepository(String username)

}
