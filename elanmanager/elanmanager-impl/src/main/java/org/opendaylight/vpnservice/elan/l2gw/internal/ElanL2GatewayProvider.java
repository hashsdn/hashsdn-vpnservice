/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.elan.l2gw.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.elan.internal.ElanInstanceManager;
import org.opendaylight.vpnservice.elan.internal.ElanInterfaceManager;
import org.opendaylight.vpnservice.elan.internal.ElanServiceProvider;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepLocalUcastMacListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepLogicalSwitchListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepNodeListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepPhysicalLocatorListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.HwvtepRemoteMcastMacListener;
import org.opendaylight.vpnservice.elan.l2gw.listeners.L2GatewayConnectionListener;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayMulticastUtils;
import org.opendaylight.vpnservice.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.vpnservice.elan.l2gw.utils.L2GatewayConnectionUtils;
import org.opendaylight.vpnservice.utils.clustering.EntityOwnerUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elan L2 Gateway provider class.
 */
public class ElanL2GatewayProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanL2GatewayProvider.class);

    private DataBroker broker;
    private EntityOwnershipService entityOwnershipService;
    private ItmRpcService itmRpcService;
    private ElanInstanceManager elanInstanceManager;
    private ElanInterfaceManager elanInterfaceManager;

    private L2GatewayConnectionListener l2GwConnListener;
    private HwvtepNodeListener hwvtepNodeListener;
    private HwvtepLocalUcastMacListener torMacsListener;
    private HwvtepPhysicalLocatorListener physicalLocatorListener;

    static DataStoreJobCoordinator dataStoreJobCoordinator;

    /**
     * Instantiates a new elan l2 gateway provider.
     *
     * @param elanServiceProvider
     *            the elan service provider
     */
    public ElanL2GatewayProvider(ElanServiceProvider elanServiceProvider) {
        this.broker = elanServiceProvider.getBroker();
        this.entityOwnershipService = elanServiceProvider.getEntityOwnershipService();
        this.itmRpcService = elanServiceProvider.getItmRpcService();
        this.elanInstanceManager = elanServiceProvider.getElanInstanceManager();
        this.elanInterfaceManager = elanServiceProvider.getElanInterfaceManager();
        dataStoreJobCoordinator = elanServiceProvider.getDataStoreJobCoordinator();
        init();

        LOG.info("ElanL2GatewayProvider Initialized");
    }

    /**
     * Initialize Elan L2 Gateway.
     */
    private void init() {
        ElanL2GwCacheUtils.createElanL2GwDeviceCache();
        ElanL2GatewayUtils.setDataBroker(broker);
        ElanL2GatewayUtils.setItmRpcService(itmRpcService);
        ElanL2GatewayUtils.setDataStoreJobCoordinator(dataStoreJobCoordinator);

        ElanL2GatewayMulticastUtils.setBroker(broker);
        ElanL2GatewayMulticastUtils.setElanInstanceManager(elanInstanceManager);
        ElanL2GatewayMulticastUtils.setElanInterfaceManager(elanInterfaceManager);
        ElanL2GatewayMulticastUtils.setDataStoreJobCoordinator(dataStoreJobCoordinator);

        L2GatewayConnectionUtils.setElanInstanceManager(elanInstanceManager);
        L2GatewayConnectionUtils.setBroker(broker);
        L2GatewayConnectionUtils.setDataStoreJobCoordinator(dataStoreJobCoordinator);

        HwvtepRemoteMcastMacListener.setDataStoreJobCoordinator(dataStoreJobCoordinator);
        HwvtepLogicalSwitchListener.setDataStoreJobCoordinator(dataStoreJobCoordinator);

        this.torMacsListener = new HwvtepLocalUcastMacListener(broker);
        this.l2GwConnListener = new L2GatewayConnectionListener(broker, elanInstanceManager);
        this.hwvtepNodeListener = new HwvtepNodeListener(broker, elanInstanceManager, itmRpcService);
        this.hwvtepNodeListener.registerListener(LogicalDatastoreType.OPERATIONAL, broker);

        physicalLocatorListener = new HwvtepPhysicalLocatorListener(broker);
        try {
            EntityOwnerUtils.registerEntityCandidateForOwnerShip(entityOwnershipService,
                    HwvtepSouthboundConstants.ELAN_ENTITY_TYPE, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    null/*listener*/);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register the entity");
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.AutoCloseable#close()
     */
    @Override
    public void close() throws Exception {
        this.torMacsListener.close();
        this.l2GwConnListener.close();
        this.hwvtepNodeListener.close();
        LOG.info("ElanL2GatewayProvider Closed");
    }
}
