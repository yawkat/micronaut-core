/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.mongo.reactive

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoCollection
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.reactivex.Single
import org.bson.types.ObjectId
import spock.lang.Specification

class MongoPojoSpec extends Specification {

    void 'Pojo id property is set automatically after inserting the document'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run((MongoSettings.MONGODB_URI): "mongodb://localhost:${SocketUtils.findAvailableTcpPort()}")
        MongoClient mongoClient = applicationContext.getBean(MongoClient)

        and:
        MongoCollection<User> collection = mongoClient.getDatabase('test').getCollection('user', User.class)

        when:
        User user = new User(name: 'John')
        User updatedUser = Single
            .fromPublisher(collection.insertOne(user))
            .map { success -> user }
            .blockingGet()

        then:
        updatedUser != null
        updatedUser.name == 'John'
        updatedUser.id != null

        cleanup:
        applicationContext.stop()
    }

    static class User {
        ObjectId id
        String name
    }
}


