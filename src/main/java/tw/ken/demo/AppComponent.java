/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tw.ken.demo;

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/* My Imports */

import java.util.HashMap;
import java.util.Map;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketPriority;

import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRuleService;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    private ApplicationId appId;
    private Map<DeviceId, HashMap> deviceTable = new HashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        appId = coreService.registerApplication("tw.ken.demo");
        log.info("Started, {}", appId.id());

        packetService.addProcessor(processor, PacketProcessor.director(2));

        TrafficSelector.Builder selector1 = DefaultTrafficSelector.builder();
        selector1.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector1.build(), PacketPriority.HIGH1, appId);

        TrafficSelector.Builder selector2 = DefaultTrafficSelector.builder();
        selector2.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector2.build(), PacketPriority.HIGH1, appId);
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);

        TrafficSelector.Builder selector1 = DefaultTrafficSelector.builder();
        selector1.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector1.build(), PacketPriority.HIGH1, appId);
        TrafficSelector.Builder selector2 = DefaultTrafficSelector.builder();
        selector2.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector2.build(), PacketPriority.HIGH1, appId);

        flowRuleService.removeFlowRulesById(appId);

        packetService.removeProcessor(processor);
        processor = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private class MyPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            // Packet
            InboundPacket pkt = context.inPacket();
            log.info("\n!!: \n{}", pkt);
            ConnectPoint connectPoint = pkt.receivedFrom();
            log.info("ConnectPoint: {}", connectPoint);
            DeviceId deviceId = connectPoint.deviceId();
            log.info("DeviceId: {}", deviceId);
            PortNumber portNumber = connectPoint.port();
            log.info("PortNumber: {}", portNumber);

            // Ethernet Packet
            Ethernet ethPkt = pkt.parsed();
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            log.info("SrcMac->DstMac: {}->{}", srcMac, dstMac);
            if (ethPkt == null) {
                log.info("Eth (null): {}", ethPkt); // never reach
                return;
            }

            // Init the Mac Table HashMap: Get Mac Table of current device
            HashMap<MacAddress, PortNumber> macTable;
            if (deviceTable.get(deviceId) == null) {
                macTable = new HashMap<>();
                deviceTable.put(deviceId, macTable);
            } else {
                macTable = deviceTable.get(deviceId);
            }

            // If Mac Table don't recognize this source mac, store it.
            if (macTable.get(srcMac) == null) {
                macTable.put(srcMac, portNumber);
            }

            log.info("deviceTable: {}", deviceTable);
            log.info("macTable of {}: {}", deviceId, macTable);

            // Check whether there is a record in Mac Table
            PortNumber outputPortNumber = macTable.get(dstMac);
            if (outputPortNumber != null) { // exists, install flow rule
                TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
                selectorBuilder.matchEthDst(dstMac);

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outputPortNumber)
                    .build();

                int flowPriority = PacketPriority.HIGH2.priorityValue();
                FlowRule flowRule = DefaultFlowRule.builder()
                        .forDevice(deviceId)
                        .withSelector(selectorBuilder.build())
                        .withTreatment(treatment)
                        .withPriority(flowPriority)
                        .fromApp(appId)
                        .makeTemporary(60)
                        .build();

                flowRuleService.applyFlowRules(flowRule);

                context.treatmentBuilder().setOutput(outputPortNumber);
                context.send();
            } else { // not exist in Mac Table, Do Flooding
                // Default Action: flooding
                context.treatmentBuilder().setOutput(PortNumber.FLOOD);
                context.send();
            }
        }
    }
}