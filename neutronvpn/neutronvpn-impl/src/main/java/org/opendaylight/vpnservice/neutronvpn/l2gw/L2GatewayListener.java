/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.neutronvpn.l2gw;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.vpnservice.utils.clustering.ClusteringUtils;
import org.opendaylight.vpnservice.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.vpnservice.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.vpnservice.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class L2GatewayListener extends AsyncClusteredDataChangeListenerBase<L2gateway, L2GatewayListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(L2GatewayListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private ItmRpcService itmRpcService;
    private EntityOwnershipService entityOwnershipService;

    public L2GatewayListener(final DataBroker db, RpcProviderRegistry rpcRegistry,
            EntityOwnershipService entityOwnershipService) {
        super(L2gateway.class, L2GatewayListener.class);
        broker = db;
        this.entityOwnershipService = entityOwnershipService;
        itmRpcService = rpcRegistry.getRpcService(ItmRpcService.class);
        registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("L2 Gateway listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class),
                    L2GatewayListener.this, DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("Neutron Manager L2 Gateway DataChange listener registration fail!", e);
            throw new IllegalStateException("Neutron Manager L2 Gateway DataChange listener registration failed.", e);
        }
    }

    @Override
    protected void add(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Adding L2gateway with ID: {}", input.getUuid());

        List<Devices> l2Devices = input.getDevices();
        for (Devices l2Device : l2Devices) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Adding L2gateway device: {}", l2Device);
            }
            addL2Device(l2Device, input);
        }
    }

    @Override
    protected void remove(final InstanceIdentifier<L2gateway> identifier, final L2gateway input) {
        LOG.info("Removing L2gateway with ID: {}", input.getUuid());

        List<Devices> l2Devices = input.getDevices();
        for (Devices l2Device : l2Devices) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Removing L2gateway device: {}", l2Device);
            }
            removeL2Device(l2Device, input);
        }
    }

    @Override
    protected void update(InstanceIdentifier<L2gateway> identifier, L2gateway original, L2gateway update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Updating L2gateway : key: " + identifier + ", original value=" + original + ", update value="
                    + update);
        }
    }

    private void addL2Device(Devices l2Device, L2gateway input) {
        final String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
        if (l2GwDevice != null) {
            if (!L2GatewayUtils.isGatewayAssociatedToL2Device(l2GwDevice)
                    && L2GatewayUtils.isL2GwDeviceConnected(l2GwDevice)) {
                // VTEP already discovered; create ITM tunnels
                final String hwvtepId = l2GwDevice.getHwvtepNodeId();
                InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                        entityOwnershipService, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                        HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
                final List<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
                Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isOwner) {
                        if (isOwner) {
                            LOG.info("Creating ITM Tunnels for {} connected to cluster node owner", l2DeviceName);
                            for (IpAddress tunnelIp : tunnelIps) {
                                L2GatewayUtils.createItmTunnels(itmRpcService, hwvtepId, l2DeviceName, tunnelIp);
                            }
                        } else {
                            LOG.info("ITM Tunnels are not created on the cluster node as this is not owner for {}",
                                    l2DeviceName);
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        LOG.error("Failed to create ITM tunnels", error);
                    }
                });
            } else {
                LOG.trace("ITM tunnels are already created for device {}", l2DeviceName);
            }
        } else {
            LOG.trace("{} is not connected; ITM tunnels will be created when device comes up", l2DeviceName);
            // Pre-provision scenario. Create L2GatewayDevice without VTEP
            // details for pushing configurations as soon as device discovered
            l2GwDevice = new L2GatewayDevice();
            l2GwDevice.setDeviceName(l2DeviceName);
            L2GatewayCacheUtils.addL2DeviceToCache(l2DeviceName, l2GwDevice);
        }
        l2GwDevice.addL2GatewayId(input.getUuid());
    }

    private void removeL2Device(Devices l2Device, L2gateway input) {
        final String l2DeviceName = l2Device.getDeviceName();
        L2GatewayDevice l2GwDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
        if (l2GwDevice != null) {
            // Delete ITM tunnels if it's last Gateway deleted and device is connected
            // Also, do not delete device from cache if it's connected
            if (L2GatewayUtils.isLastL2GatewayBeingDeleted(l2GwDevice)) {
                if (L2GatewayUtils.isL2GwDeviceConnected(l2GwDevice)) {
                    l2GwDevice.removeL2GatewayId(input.getUuid());
                    // Delete ITM tunnels
                    final String hwvtepId = l2GwDevice.getHwvtepNodeId();
                    InstanceIdentifier<Node> iid = HwvtepSouthboundUtils.createInstanceIdentifier(new NodeId(hwvtepId));
                    ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                            entityOwnershipService, HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                            HwvtepSouthboundConstants.ELAN_ENTITY_NAME);
                    final List<IpAddress> tunnelIps = l2GwDevice.getTunnelIps();
                    Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean isOwner) {
                            if (isOwner) {
                                LOG.info("Deleting ITM Tunnels for {} connected to cluster node owner", l2DeviceName);
                                for (IpAddress tunnelIp : tunnelIps) {
                                    L2GatewayUtils.deleteItmTunnels(itmRpcService, hwvtepId, l2DeviceName, tunnelIp);
                                }
                            } else {
                                LOG.info("ITM Tunnels are not deleted on the cluster node as this is not owner for {}",
                                        l2DeviceName);
                            }
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            LOG.error("Failed to delete ITM tunnels", error);
                        }
                    });
                } else {
                    L2GatewayCacheUtils.removeL2DeviceFromCache(l2DeviceName);
                }
            } else {
                l2GwDevice.removeL2GatewayId(input.getUuid());
                LOG.trace("ITM tunnels are not deleted for {} as this device has other L2gateway associations",
                        l2DeviceName);
            }
        } else {
            LOG.error("Unable to find L2 Gateway details for {}", l2DeviceName);
        }
    }

    @Override
    protected InstanceIdentifier<L2gateway> getWildCardPath() {
        return InstanceIdentifier.create(L2gateway.class);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return L2GatewayListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.BASE;
    }
}
