/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.vpnservice.neutronvpn.l2gw.L2GatewayProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.lockmanager.rev150819.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.neutronvpn.rev150602.NeutronvpnService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NeutronvpnProvider implements BindingAwareProvider, INeutronVpnManager, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnProvider.class);
    private NeutronvpnManager nvManager;
    private IMdsalApiManager mdsalManager;
    private LockManagerService lockManager;
    private NeutronBgpvpnChangeListener bgpvpnListener;
    private NeutronNetworkChangeListener networkListener;
    private NeutronSubnetChangeListener subnetListener;
    private NeutronRouterChangeListener routerListener;
    private NeutronPortChangeListener portListener;
    private RpcProviderRegistry rpcProviderRegistry;
    private L2GatewayProvider l2GatewayProvider;
    private NotificationPublishService notificationPublishService;
    private NotificationService notificationService;
    private EntityOwnershipService entityOwnershipService;

    public NeutronvpnProvider(RpcProviderRegistry rpcRegistry,NotificationPublishService notificationPublishService,
                              NotificationService notificationService) {
        this.rpcProviderRegistry = rpcRegistry;
        this.notificationPublishService = notificationPublishService;
        this.notificationService = notificationService;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public void setRpcProviderRegistry(RpcProviderRegistry rpcProviderRegistry) {
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setLockManager(LockManagerService lockManager) {
        this.lockManager = lockManager;
    }

    public void setEntityOwnershipService(EntityOwnershipService entityOwnershipService) {
        this.entityOwnershipService = entityOwnershipService;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        try {
            final DataBroker dbx = session.getSALService(DataBroker.class);
            nvManager = new NeutronvpnManager(dbx, mdsalManager,notificationPublishService,notificationService);
            final BindingAwareBroker.RpcRegistration<NeutronvpnService> rpcRegistration =
                    getRpcProviderRegistry().addRpcImplementation(NeutronvpnService.class, nvManager);
            bgpvpnListener = new NeutronBgpvpnChangeListener(dbx, nvManager);
            networkListener = new NeutronNetworkChangeListener(dbx, nvManager);
            subnetListener = new NeutronSubnetChangeListener(dbx, nvManager);
            routerListener = new NeutronRouterChangeListener(dbx, nvManager);
            portListener = new NeutronPortChangeListener(dbx, nvManager,notificationPublishService,notificationService);
            portListener.setLockManager(lockManager);
            nvManager.setLockManager(lockManager);
            l2GatewayProvider = new L2GatewayProvider(dbx, rpcProviderRegistry, entityOwnershipService);

            LOG.info("NeutronvpnProvider Session Initiated");
        } catch (Exception e) {
            LOG.error("Error initializing services", e);
        }
    }

    @Override
    public void close() throws Exception {
        portListener.close();
        subnetListener.close();
        routerListener.close();
        networkListener.close();
        bgpvpnListener.close();
        nvManager.close();
        l2GatewayProvider.close();
        LOG.info("NeutronvpnProvider Closed");
    }

    @Override
    public List<String> showNeutronPortsCLI() {
        return nvManager.showNeutronPortsCLI();
    }

    @Override
    public List<String> showVpnConfigCLI(Uuid vuuid) {
        return nvManager.showVpnConfigCLI(vuuid);
    }

    @Override
    public void addSubnetToVpn(Uuid vpnId, Uuid subnet) {
        nvManager.addSubnetToVpn(vpnId, subnet);
    }

    @Override
    public List<Uuid> getSubnetsforVpn(Uuid vpnid) {
        return nvManager.getSubnetsforVpn(vpnid);
    }

    @Override
    public void removeSubnetFromVpn(Uuid vpnId, Uuid subnet) {
        nvManager.removeSubnetFromVpn(vpnId, subnet);
    }

    @Override
    public Port getNeutronPort(String name) {
        return nvManager.getNeutronPort(name);
    }

    @Override
    public Port getNeutronPort(Uuid portId) {
        return nvManager.getNeutronPort(portId);
    }

    @Override
    public Subnet getNeutronSubnet(Uuid subnetId) {
        return nvManager.getNeutronSubnet(subnetId);
    }

    @Override
    public String uuidToTapPortName(Uuid id) {
        return NeutronvpnUtils.uuidToTapPortName(id);
    }

    @Override
    public IpAddress getNeutronSubnetGateway(Uuid subnetId) {
        return nvManager.getNeutronSubnetGateway(subnetId);
    }

}
