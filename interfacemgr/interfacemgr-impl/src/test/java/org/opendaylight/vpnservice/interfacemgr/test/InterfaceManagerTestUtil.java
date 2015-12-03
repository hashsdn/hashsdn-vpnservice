/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.test;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadata;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.Options;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.OptionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.OptionsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev130712.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InterfaceManagerTestUtil {
    public static final String interfaceName = "s1-eth1";
    public static final String tunnelInterfaceName = "s2-gre1";
    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface buildStateInterface(
            String ifName, NodeConnectorId ncId) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder =
                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifName));
        ifaceBuilder.setOperStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up);
        ifaceBuilder.setLowerLayerIf(Arrays.asList(ncId.getValue()));
        return ifaceBuilder.build();
    }

    public static InstanceIdentifier<NodeConnector> getNcIdent(String nodeKey, NodeConnectorId ncId) {
        return InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(new NodeId(nodeKey)))
                .child(NodeConnector.class, new NodeConnectorKey(ncId))
                .build();
    }

    public static Interface buildInterface(String ifName, String desc, boolean enabled, Object ifType,
                                           BigInteger dpn) {
        InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
                .setDescription(desc).setEnabled(enabled).setType((Class<? extends InterfaceType>) ifType);
        ParentRefs parentRefs = new ParentRefsBuilder().setDatapathNodeIdentifier(dpn).build();
        builder.addAugmentation(ParentRefs.class, parentRefs);
        if(ifType.equals(L2vlan.class)){
            IfL2vlan l2vlan = new IfL2vlanBuilder().setVlanId(VlanId.getDefaultInstance("0"))
                    .setL2vlanMode(IfL2vlan.L2vlanMode.Access).build();
            builder.addAugmentation(IfL2vlan.class, l2vlan);
        }else if(ifType.equals(IfTunnel.class)){
            IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(null).setTunnelGateway(null).setTunnelSource(null)
                    .setTunnelInterfaceType(null).build();
            builder.addAugmentation(IfTunnel.class, tunnel);
        }
        return builder.build();
    }

    public static Interface buildTunnelInterface(BigInteger dpn, String ifName, String desc, boolean enabled, Class<? extends TunnelTypeBase> tunType,
                                           String remoteIpStr, String localIpStr) {
        InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
                .setDescription(desc).setEnabled(enabled).setType(Tunnel.class);
        ParentRefs parentRefs = new ParentRefsBuilder().setDatapathNodeIdentifier(dpn).build();
        builder.addAugmentation(ParentRefs.class, parentRefs);
        IpAddress remoteIp = new IpAddress(Ipv4Address.getDefaultInstance(remoteIpStr));
        IpAddress localIp =  new IpAddress(Ipv4Address.getDefaultInstance(localIpStr));
        IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(remoteIp).setTunnelGateway(localIp).setTunnelSource(localIp)
                    .setTunnelInterfaceType( tunType).build();
        builder.addAugmentation(IfTunnel.class, tunnel);
        return builder.build();
    }

    public static NodeConnector buildNodeConnector(NodeConnectorId ncId) {
        NodeConnectorBuilder ncBuilder = new NodeConnectorBuilder()
                .setId(ncId)
                .setKey(new NodeConnectorKey(ncId));
        return ncBuilder.build();
    }

    public static NodeConnectorId buildNodeConnectorId(BigInteger dpn, long portNo) {
        return new NodeConnectorId(buildNodeConnectorString(dpn, portNo));
    }

    public static String buildNodeConnectorString(BigInteger dpn, long portNo){
       return new StringBuffer().append(IfmConstants.OF_URI_PREFIX).
               append(dpn).append(IfmConstants.OF_URI_SEPARATOR).
               append(portNo).toString();
    }

    public static InstanceIdentifier<BridgeInterfaceEntry> buildBridgeEntryId(BigInteger dpn, String interfaceName){
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpn);
        BridgeInterfaceEntryKey bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(interfaceName);
        InstanceIdentifier<BridgeInterfaceEntry> bridgeInterfaceEntryIid =
                InterfaceMetaUtils.getBridgeInterfaceEntryIdentifier(bridgeEntryKey, bridgeInterfaceEntryKey);
        return bridgeInterfaceEntryIid;
    }

    public static BridgeEntry buildBridgeEntry(BigInteger dpn, BridgeInterfaceEntry bridgeInterfaceEntry){
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpn);
        BridgeEntry bridgeEntry = new BridgeEntryBuilder().setKey(bridgeEntryKey).setDpid(dpn).
                setBridgeInterfaceEntry(Arrays.asList(bridgeInterfaceEntry)).build();
        return bridgeEntry;
    }

    public static BridgeInterfaceEntry buildBridgeInterfaceEntry(BigInteger dpn, String interfaceName){
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpn);
        BridgeInterfaceEntryKey bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(interfaceName);
        BridgeInterfaceEntryBuilder entryBuilder = new BridgeInterfaceEntryBuilder().setKey(bridgeInterfaceEntryKey)
                .setInterfaceName(interfaceName);
        return entryBuilder.build();
    }

    public static InstanceIdentifier<OvsdbBridgeAugmentation> getOvsdbAugmentationInstanceIdentifier(String portName,
                                                                                                     org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node) {
        InstanceIdentifier<OvsdbBridgeAugmentation> ovsdbBridgeAugmentationInstanceIdentifier = InstanceIdentifier
                .create(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node.class,node.getKey())
                .augmentation(OvsdbBridgeAugmentation.class);
        return ovsdbBridgeAugmentationInstanceIdentifier;
    }

    public static OvsdbBridgeAugmentation getOvsdbBridgeRef(String bridgeName){
        OvsdbBridgeAugmentationBuilder builder = new OvsdbBridgeAugmentationBuilder().setBridgeName(new OvsdbBridgeName(bridgeName));
        return builder.build();
    }

    public static InstanceIdentifier<TerminationPoint> getTerminationPointId(InstanceIdentifier<?> bridgeIid, String portName){
        InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node.class)), portName);
        return tpIid;
    }

    public static TerminationPoint getTerminationPoint(InstanceIdentifier<?> bridgeIid, OvsdbBridgeAugmentation bridgeNode,
                                            String portName, int vlanId, Class type,
                                            Interface iface) {
        InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node.class)), portName);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();

        tpAugmentationBuilder.setName(portName);

        if (type != null) {
            tpAugmentationBuilder.setInterfaceType(type);
        }
        IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
        Map<String, String> options = Maps.newHashMap();
        options.put("key", "flow");

        IpAddress localIp = ifTunnel.getTunnelSource();
        options.put("local_ip", localIp.getIpv4Address().getValue());

        IpAddress remoteIp = ifTunnel.getTunnelDestination();
        options.put("remote_ip", remoteIp.getIpv4Address().getValue());

        if (options != null) {
            List<Options> optionsList = new ArrayList<Options>();
            for (Map.Entry<String, String> entry : options.entrySet()) {
                OptionsBuilder optionsBuilder = new OptionsBuilder();
                optionsBuilder.setKey(new OptionsKey(entry.getKey()));
                optionsBuilder.setOption(entry.getKey());
                optionsBuilder.setValue(entry.getValue());
                optionsList.add(optionsBuilder.build());
            }
            tpAugmentationBuilder.setOptions(optionsList);
        }

        if (vlanId != 0) {
            tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Access);
            tpAugmentationBuilder.setVlanTag(new VlanId(vlanId));
        }

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());
        return tpBuilder.build();
    }
}