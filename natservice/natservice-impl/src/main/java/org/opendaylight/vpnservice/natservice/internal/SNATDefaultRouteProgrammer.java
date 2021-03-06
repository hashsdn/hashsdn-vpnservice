/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SNATDefaultRouteProgrammer {

    private static final Logger LOG = LoggerFactory.getLogger(SNATDefaultRouteProgrammer.class);
    private IMdsalApiManager mdsalManager;

    public SNATDefaultRouteProgrammer(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long vpnId) {

        InetAddress defaultIP = null;

        try {
            defaultIP = InetAddress.getByName("0.0.0.0");

        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException in buildDefNATFlowEntity. Failed  to build FIB Table Flow for Default Route to NAT table ");
            return null;
        }

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        //add match for default route "0.0.0.0/0"
//        matches.add(new MatchInfo(MatchFieldType.ipv4_dst, new long[] {
//                NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NatConstants.PSNAT_TABLE }));

        String flowRef = getFlowRefFib(dpId, NatConstants.L3_FIB_TABLE, vpnId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NatConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long bgpVpnId, long routerId) {

        InetAddress defaultIP = null;

        try {
            defaultIP = InetAddress.getByName("0.0.0.0");

        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException in buildDefNATFlowEntity. Failed  to build FIB Table Flow for Default Route to NAT table ");
            return null;
        }

        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        //add match for default route "0.0.0.0/0"
//        matches.add(new MatchInfo(MatchFieldType.ipv4_dst, new long[] {
//                NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(bgpVpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NatConstants.PSNAT_TABLE }));

        String flowRef = getFlowRefFib(dpId, NatConstants.L3_FIB_TABLE, routerId);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NatConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NatConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    private String getFlowRefFib(BigInteger dpnId, short tableId, long routerID) {
        return new StringBuilder().append(NatConstants.NAPT_FLOWID_PREFIX).append(dpnId).append(NatConstants.FLOWID_SEPARATOR).
                append(tableId).append(NatConstants.FLOWID_SEPARATOR).append(routerID).toString();
    }

    void installDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.installFlow(flowEntity);
    }

    void installDefNATRouteInDPN(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.installFlow(flowEntity);
    }

    void removeDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.removeFlow(flowEntity);
    }

    void removeDefNATRouteInDPN(BigInteger dpnId, long bgpVpnId, long routerId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, bgpVpnId, routerId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.removeFlow(flowEntity);
    }

}
