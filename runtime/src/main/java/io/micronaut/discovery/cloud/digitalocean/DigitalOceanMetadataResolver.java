/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.cloud.digitalocean;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.jr.stree.JacksonJrsTreeCodec;
import com.fasterxml.jackson.jr.stree.JrsArray;
import com.fasterxml.jackson.jr.stree.JrsNumber;
import com.fasterxml.jackson.jr.stree.JrsValue;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.cloud.NetworkInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.micronaut.discovery.cloud.ComputeInstanceMetadataResolverUtils.*;
import static io.micronaut.discovery.cloud.digitalocean.DigitalOceanMetadataKeys.*;

/**
 * Resolves {@link ComputeInstanceMetadata} for Digital Ocean.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 1.1
 */
@Singleton
@Requires(env = Environment.DIGITAL_OCEAN)
public class DigitalOceanMetadataResolver implements ComputeInstanceMetadataResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DigitalOceanMetadataResolver.class);
    private static final int READ_TIMEOUT_IN_MILLS = 5000;
    private static final int CONNECTION_TIMEOUT_IN_MILLS = 5000;

    private final DigitalOceanMetadataConfiguration configuration;
    private DigitalOceanInstanceMetadata cachedMetadata;

    /**
     *
     * @param configuration Digital Ocean Metadata configuration
     */
    @Inject
    public DigitalOceanMetadataResolver(DigitalOceanMetadataConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Construct with default settings.
     */
    public DigitalOceanMetadataResolver() {
        configuration = new DigitalOceanMetadataConfiguration();
    }

    @Override
    public Optional<ComputeInstanceMetadata> resolve(Environment environment) {
        if (!configuration.isEnabled()) {
            return Optional.empty();
        }
        if (cachedMetadata != null) {
            cachedMetadata.setCached(true);
            return Optional.of(cachedMetadata);
        }

        DigitalOceanInstanceMetadata instanceMetadata = new DigitalOceanInstanceMetadata();

        try {
            String metadataUrl = configuration.getUrl();
            JrsValue metadataJson = (JrsValue) readMetadataUrl(new URL(metadataUrl), CONNECTION_TIMEOUT_IN_MILLS, READ_TIMEOUT_IN_MILLS, JacksonJrsTreeCodec.SINGLETON, new JsonFactory(), new HashMap<>());
            if (metadataJson != null) {
                instanceMetadata.setInstanceId(textValue(metadataJson, DROPLET_ID));
                instanceMetadata.setName(textValue(metadataJson, HOSTNAME));
                instanceMetadata.setVendorData(textValue(metadataJson, VENDOR_DATA));
                instanceMetadata.setUserData(textValue(metadataJson, USER_DATA));
                instanceMetadata.setRegion(textValue(metadataJson, REGION));

                TreeNode networkInterfaces = metadataJson.get(INTERFACES.getName());
                List<NetworkInterface> privateInterfaces = processJsonInterfaces((JrsArray) networkInterfaces.get(PRIVATE_INTERFACES.getName()), instanceMetadata::setPrivateIpV4, instanceMetadata::setPrivateIpV6);
                List<NetworkInterface> publicInterfaces = processJsonInterfaces((JrsArray) networkInterfaces.get(PUBLIC_INTERFACES.getName()), instanceMetadata::setPublicIpV4, instanceMetadata::setPublicIpV6);
                List<NetworkInterface> allInterfaces = new ArrayList<>();
                allInterfaces.addAll(publicInterfaces);
                allInterfaces.addAll(privateInterfaces);
                instanceMetadata.setInterfaces(allInterfaces);

                populateMetadata(instanceMetadata, metadataJson);
                cachedMetadata = instanceMetadata;

                return Optional.of(instanceMetadata);
            }
        } catch (MalformedURLException mue) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Digital Ocean metadataUrl value is invalid!: " + configuration.getUrl(), mue);
            }
        } catch (IOException ioe) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error connecting to" + configuration.getUrl() + "reading instance metadata", ioe);
            }
        }

        return Optional.empty();
    }

    private List<NetworkInterface> processJsonInterfaces(JrsArray interfaces, Consumer<String> ipv4Setter, Consumer<String> ipv6Setter) {
        List<NetworkInterface> networkInterfaces = new ArrayList<>();

        if (interfaces != null) {
            AtomicReference<Integer> networkCounter = new AtomicReference<>(0);
            interfaces.elements().forEachRemaining(
                    jsonNode -> {
                        DigitalOceanNetworkInterface networkInterface = new DigitalOceanNetworkInterface();
                        networkInterface.setId(networkCounter.toString());
                        JrsValue ipv4 = jsonNode.get(IPV4.getName());
                        if (ipv4 != null) {
                            networkInterface.setIpv4(textValue(ipv4, IP_ADDRESS));
                            networkInterface.setNetmask(textValue(ipv4, NETMASK));
                            networkInterface.setGateway(textValue(ipv4, GATEWAY));
                        }
                        JrsValue ipv6 = jsonNode.get(IPV6.getName());
                        if (ipv6 != null) {
                            networkInterface.setIpv6(textValue(ipv6, IP_ADDRESS));
                            networkInterface.setIpv6Gateway(textValue(ipv6, GATEWAY));
                            networkInterface.setCidr(((JrsNumber) ipv6.get(CIDR.getName())).getValue().intValue());
                        }
                        networkInterface.setMac(textValue(jsonNode, MAC));

                        networkCounter.getAndSet(networkCounter.get() + 1);
                        networkInterfaces.add(networkInterface);
                    }
            );

            JrsValue firstIpv4 = interfaces.get(0).get(IPV4.getName());
            ipv4Setter.accept(textValue(firstIpv4, IP_ADDRESS));

            JrsValue firstIpv6 = interfaces.get(0).get(IPV6.getName());
            if (firstIpv6 != null) {
                ipv6Setter.accept(textValue(firstIpv6, IP_ADDRESS));
            }
        }


        return networkInterfaces;
    }

    private String textValue(JrsValue node, DigitalOceanMetadataKeys key) {
        JrsValue value = node.get(key.getName());
        if (value != null) {
            return value.asText();
        } else {
            return null;
        }
    }

}
