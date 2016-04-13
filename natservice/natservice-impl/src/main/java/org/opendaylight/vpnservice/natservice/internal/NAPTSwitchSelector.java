/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.NaptSwitchesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class NAPTSwitchSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NAPTSwitchSelector.class);

    private DataBroker dataBroker;
    public NAPTSwitchSelector(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    BigInteger selectNewNAPTSwitch(String routerName) {
        LOG.info("NAT Service : Select a new NAPT switch for router {}", routerName);
        Map<BigInteger, Integer> naptSwitchWeights = constructNAPTSwitches();
        List<BigInteger> routerSwitches = getDpnsForVpn(routerName);
        if(routerSwitches.isEmpty()) {
            LOG.debug("NAT Service : No dpns that are part of router {}", routerName);
            LOG.warn("NAT Service : NAPT switch selection stopped due to no dpns scenario for router {}", routerName);
            return BigInteger.ZERO;
        }

        Set<SwitchWeight> switchWeights = new TreeSet<>();
        for(BigInteger dpn : routerSwitches) {
            if(naptSwitchWeights.get(dpn) != null) {
                switchWeights.add(new SwitchWeight(dpn, naptSwitchWeights.get(dpn)));
            } else {
                switchWeights.add(new SwitchWeight(dpn, 0));
            }
        }

        BigInteger primarySwitch;

        if(!switchWeights.isEmpty()) {

            LOG.debug("NAT Service : Current switch weights for router {} - {}", routerName, switchWeights);

            Iterator<SwitchWeight> it = switchWeights.iterator();
            List<RouterToNaptSwitch> routerToNaptSwitchList = new ArrayList<>();
            RouterToNaptSwitchBuilder routerToNaptSwitchBuilder = new RouterToNaptSwitchBuilder().setRouterName(routerName);
            if ( switchWeights.size() == 1 )
            {
                SwitchWeight singleSwitchWeight = null;
                while(it.hasNext() ) {
                    singleSwitchWeight = it.next();
                }
                primarySwitch = singleSwitchWeight.getSwitch();
                routerToNaptSwitchBuilder.setPrimarySwitchId(primarySwitch);
                routerToNaptSwitchList.add(routerToNaptSwitchBuilder.build());
                NaptSwitches naptSwitches = new NaptSwitchesBuilder().setRouterToNaptSwitch(routerToNaptSwitchList).build();
                MDSALUtil.syncWrite( dataBroker, LogicalDatastoreType.OPERATIONAL, getNaptSwitchesIdentifier(), naptSwitches);

                LOG.debug( "NAT Service : successful addition of RouterToNaptSwitch to napt-switches container for single switch" );
                return primarySwitch;
            }
            else
            {
                SwitchWeight firstSwitchWeight = null;
                while(it.hasNext() ) {
                    firstSwitchWeight = it.next();
                }
                primarySwitch = firstSwitchWeight.getSwitch();
                routerToNaptSwitchBuilder.setPrimarySwitchId(primarySwitch);
                routerToNaptSwitchList.add(routerToNaptSwitchBuilder.build());
                NaptSwitches naptSwitches = new NaptSwitchesBuilder().setRouterToNaptSwitch(routerToNaptSwitchList).build();
                MDSALUtil.syncWrite( dataBroker, LogicalDatastoreType.OPERATIONAL, getNaptSwitchesIdentifier(), naptSwitches);

                LOG.debug( "NAT Service : successful addition of RouterToNaptSwitch to napt-switches container");
                return primarySwitch;
            }
        } else {

                primarySwitch = BigInteger.ZERO;

                LOG.debug("NAT Service : switchWeights empty, primarySwitch: {} ", primarySwitch);
                return primarySwitch;
        }


    }

    private Map<BigInteger, Integer> constructNAPTSwitches() {
        Optional<NaptSwitches> optNaptSwitches = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, getNaptSwitchesIdentifier());
        Map<BigInteger, Integer> switchWeights = new HashMap<>();

        if(optNaptSwitches.isPresent()) {
            NaptSwitches naptSwitches = optNaptSwitches.get();
            List<RouterToNaptSwitch> routerToNaptSwitches = naptSwitches.getRouterToNaptSwitch();

            for(RouterToNaptSwitch naptSwitch : routerToNaptSwitches) {
                BigInteger primarySwitch = naptSwitch.getPrimarySwitchId();
                //update weight
                Integer weight = switchWeights.get(primarySwitch);
                if(weight == null) {
                    switchWeights.put(primarySwitch, 1);
                } else {
                    switchWeights.put(primarySwitch, ++weight);
                }
            }
        }
        return switchWeights;
    }

    private InstanceIdentifier<NaptSwitches> getNaptSwitchesIdentifier() {
        return InstanceIdentifier.create(NaptSwitches.class);
    }

    public List<BigInteger> getDpnsForVpn(String routerName ) {
        LOG.debug( "getVpnToDpnList called for RouterName {}", routerName );

        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(routerName))
                .build();

        List<BigInteger> dpnsInVpn = new ArrayList<>();
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = NatUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);

        if(vpnInstanceOpData.isPresent()) {
            LOG.debug( "NATService : getVpnToDpnList able to fetch vpnInstanceOpData" );
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = vpnInstanceOpData.get();
            List<VpnToDpnList> vpnDpnList = vpnInstanceOpDataEntry.getVpnToDpnList();
            if(vpnDpnList != null) {
                for(VpnToDpnList vpnToDpn: vpnDpnList) {
                    dpnsInVpn.add(vpnToDpn.getDpnId());
                }
            }
        }

        LOG.debug( "getVpnToDpnList returning vpnDpnList {}", dpnsInVpn);
        return dpnsInVpn;
    }

    private static class SwitchWeight implements Comparable<SwitchWeight>
    {
        private BigInteger swich;
        private int weight;

        public SwitchWeight( BigInteger swich, int weight )
        {
            this.swich = swich;
            this.weight = weight;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((swich == null) ? 0 : swich.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SwitchWeight other = (SwitchWeight) obj;
            if (swich == null) {
                if (other.swich != null)
                    return false;
            } else if (!swich.equals(other.swich))
                return false;
            return true;
        }

        public BigInteger getSwitch() {
            return swich;
        }

        public int getWeight() { 
            return weight;
        }

        public void incrementWeight() {
            ++ weight;
        }

        @Override
        public int compareTo(SwitchWeight o) {
            return o.getWeight() - weight;
        }
    }
}
