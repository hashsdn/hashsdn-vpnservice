/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.elan.l2gw.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
* Created by ekvsver on 4/15/2016.
*/
public class DisAssociateHwvtepFromElanJob implements Callable<List<ListenableFuture<Void>>> {
    DataBroker broker;
    L2GatewayDevice l2GatewayDevice;
    ElanInstance elanInstance;
    Devices l2Device;
    Integer defaultVlan;
    boolean isLastL2GwConnDeleted;

    private static final Logger LOG = LoggerFactory.getLogger(DisAssociateHwvtepFromElanJob.class);

    public DisAssociateHwvtepFromElanJob(DataBroker broker, L2GatewayDevice l2GatewayDevice, ElanInstance elanInstance,
                                         Devices l2Device, Integer defaultVlan, boolean isLastL2GwConnDeleted) {
        this.broker = broker;
        this.l2GatewayDevice = l2GatewayDevice;
        this.elanInstance = elanInstance;
        this.l2Device = l2Device;
        this.defaultVlan = defaultVlan;
        this.isLastL2GwConnDeleted = isLastL2GwConnDeleted;
        LOG.info("created disassosiate l2gw connection job for {} {}", elanInstance.getElanInstanceName(),
                l2GatewayDevice.getHwvtepNodeId());
    }

    public String getJobKey() {
        return elanInstance.getElanInstanceName();
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        String elanName = elanInstance.getElanInstanceName();
        String strHwvtepNodeId = l2GatewayDevice.getHwvtepNodeId();
        NodeId hwvtepNodeId = new NodeId(strHwvtepNodeId);
        LOG.info("running disassosiate l2gw connection job for {} {}", elanName, strHwvtepNodeId);

        List<ListenableFuture<Void>> futures = new ArrayList<>();

        // Remove remote MACs and vlan mappings from physical port
        // Once all above configurations are deleted, delete logical
        // switch
        LOG.info("delete vlan bindings for {} {}", elanName, strHwvtepNodeId);
        futures.add(ElanL2GatewayUtils.deleteVlanBindingsFromL2GatewayDevice(hwvtepNodeId, l2Device, defaultVlan));

        if (isLastL2GwConnDeleted) {
            LOG.info("delete remote ucast macs {} {}", elanName, strHwvtepNodeId);
            futures.add(ElanL2GatewayUtils.deleteElanMacsFromL2GatewayDevice(l2GatewayDevice, elanName));

            LOG.info("delete mcast mac for {} {}", elanName, strHwvtepNodeId);
            futures.addAll(ElanL2GatewayMulticastUtils.handleMcastForElanL2GwDeviceDelete(elanInstance,
                    l2GatewayDevice));

            LOG.info("delete local ucast macs {} {}", elanName, strHwvtepNodeId);
            futures.addAll(ElanL2GatewayUtils.deleteL2GwDeviceUcastLocalMacsFromElan(l2GatewayDevice, elanName));

            LOG.info("scheduled delete logical switch {} {}", elanName, strHwvtepNodeId);
            ElanL2GatewayUtils.scheduleDeleteLogicalSwitch(hwvtepNodeId,
                    ElanL2GatewayUtils.getLogicalSwitchFromElan(elanName));
        }

        return futures;
    }
}
