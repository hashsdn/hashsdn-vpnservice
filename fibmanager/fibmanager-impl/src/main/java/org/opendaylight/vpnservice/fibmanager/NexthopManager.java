/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.fibmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.BucketInfo;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetInternalOrExternalInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetInternalOrExternalInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rpcs.rev151217.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthopKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NexthopManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NexthopManager.class);
    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;
    private OdlInterfaceRpcService interfaceManager;
    private ItmRpcService itmManager;
    private IdManagerService idManager;
    private static final short LPORT_INGRESS_TABLE = 0;
    private static final short LFIB_TABLE = 20;
    private static final short FIB_TABLE = 21;
    private static final short DEFAULT_FLOW_PRIORITY = 10;
    private static final String NEXTHOP_ID_POOL_NAME = "nextHopPointerPool";
    private static final long FIXED_DELAY_IN_MILLISECONDS = 4000;

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
        new FutureCallback<Void>() {
            public void onSuccess(Void result) {
                LOG.debug("Success in Datastore write operation");
            }
            public void onFailure(Throwable error) {
                LOG.error("Error in Datastore write operation", error);
            };
        };

    /**
    * Provides nexthop functions
    * Creates group ID pool
    *
    * @param db - dataBroker reference
    */
    public NexthopManager(final DataBroker db) {
        broker = db;
    }

    @Override
    public void close() throws Exception {
        LOG.info("NextHop Manager Closed");
    }

    public void setInterfaceManager(OdlInterfaceRpcService ifManager) {
        this.interfaceManager = ifManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
        createNexthopPointerPool();
    }

    public void setITMRpcService(ItmRpcService itmManager) {
        this.itmManager = itmManager;
    }

    protected void createNexthopPointerPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME)
            .setLow(150000L)
            .setHigh(175000L)
            .build();
        //TODO: Error handling
        Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
        LOG.trace("NextHopPointerPool result : {}", result);
    }

    private BigInteger getDpnId(String ofPortId) {
        String[] fields = ofPortId.split(":");
        BigInteger dpn = new BigInteger(fields[1]);
        LOG.debug("DpnId: {}", dpn);
        return dpn;
    }

    private String getNextHopKey(long vpnId, String ipAddress){
        String nhKey = new String("nexthop." + vpnId + ipAddress);
        return nhKey;
    }

    private String getNextHopKey(String ifName, String ipAddress){
        String nhKey = new String("nexthop." + ifName + ipAddress);
        return nhKey;
    }

    protected long createNextHopPointer(String nexthopKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NEXTHOP_ID_POOL_NAME).setIdKey(nexthopKey)
            .build();
        //TODO: Proper error handling once IdManager code is complete
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.trace("",e);
        }
        return 0;
    }

    protected void removeNextHopPointer(String nexthopKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().
                                       setPoolName(NEXTHOP_ID_POOL_NAME)
                                       .setIdKey(nexthopKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", nexthopKey, e);
        }
    }

    protected List<ActionInfo> getEgressActionsForInterface(String ifName) {
        List<ActionInfo> listActionInfo = new ArrayList<ActionInfo>();
        try {
            Future<RpcResult<GetEgressActionsForInterfaceOutput>> result =
                interfaceManager.getEgressActionsForInterface(
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).build());
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get egress actions for interface {} returned with Errors {}", ifName, rpcResult.getErrors());
            } else {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actions =
                    rpcResult.getResult().getAction();
                for (Action action : actions) {
                    org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action actionClass = action.getAction();
                    if (actionClass instanceof OutputActionCase) {
                        listActionInfo.add(new ActionInfo(ActionType.output,
                                                          new String[] {((OutputActionCase)actionClass).getOutputAction()
                                                                            .getOutputNodeConnector().getValue()}));
                    } else if (actionClass instanceof PushVlanActionCase) {
                        listActionInfo.add(new ActionInfo(ActionType.push_vlan, new String[] {}));
                    } else if (actionClass instanceof SetFieldCase) {
                        if (((SetFieldCase)actionClass).getSetField().getVlanMatch() != null) {
                            int vlanVid = ((SetFieldCase)actionClass).getSetField().getVlanMatch().getVlanId().getVlanId().getValue();
                            listActionInfo.add(new ActionInfo(ActionType.set_field_vlan_vid,
                                                              new String[] { Long.toString(vlanVid) }));
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        return listActionInfo;
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, BigInteger dstDpId) {
        try {
            Future<RpcResult<GetTunnelInterfaceNameOutput>> result = itmManager.getTunnelInterfaceName(new GetTunnelInterfaceNameInputBuilder()
                                                                                 .setSourceDpid(srcDpId)
                                                                                 .setDestinationDpid(dstDpId).build());
            RpcResult<GetTunnelInterfaceNameOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceId returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstDpId, e);
        }
        
        return null;
    }

    protected String getTunnelInterfaceName(BigInteger srcDpId, IpAddress dstIp) {
        try {
            Future<RpcResult<GetInternalOrExternalInterfaceNameOutput>> result = itmManager.getInternalOrExternalInterfaceName(new GetInternalOrExternalInterfaceNameInputBuilder()
                                                                                 .setSourceDpid(srcDpId)
                                                                                 .setDestinationIp(dstIp).build());
            RpcResult<GetInternalOrExternalInterfaceNameOutput> rpcResult = result.get();
            if(!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to getTunnelInterfaceName returned with Errors {}", rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getInterfaceName();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting tunnel interface Id for tunnel between {} and  {}", srcDpId, dstIp, e);
        }
        
        return null;
    }

    public long createLocalNextHop(long vpnId, BigInteger dpnId,
                                   String ifName, String ipAddress) {
        long groupId = createNextHopPointer(getNextHopKey(vpnId, ipAddress));
        String nextHopLockStr = new String(vpnId + ipAddress);
        synchronized (nextHopLockStr.intern()) {
            VpnNexthop nexthop = getVpnNexthop(vpnId, ipAddress);
            LOG.trace("nexthop: {}", nexthop);
            if (nexthop == null) {
                Optional<Adjacency> adjacencyData =
                        read(LogicalDatastoreType.OPERATIONAL, getAdjacencyIdentifier(ifName, ipAddress));
                String macAddress = adjacencyData.isPresent() ? adjacencyData.get().getMacAddress() : null;
                List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
                List<ActionInfo> listActionInfo = getEgressActionsForInterface(ifName);
                BucketInfo bucket = new BucketInfo(listActionInfo);
                // MAC re-write
                if (macAddress != null) {
                    listActionInfo.add(0, new ActionInfo(ActionType.set_field_eth_dest, new String[]{macAddress}));
                    //listActionInfo.add(0, new ActionInfo(ActionType.pop_mpls, new String[]{}));
                } else {
                    //FIXME: Log message here.
                    LOG.debug("mac address for new local nexthop is null");
                }
                listBucketInfo.add(bucket);
                GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                        dpnId, groupId, ipAddress, GroupTypes.GroupIndirect, listBucketInfo);

                //update MD-SAL DS
                addVpnNexthopToDS(dpnId, vpnId, ipAddress, groupId);

                // install Group
                mdsalManager.syncInstallGroup(groupEntity, FIXED_DELAY_IN_MILLISECONDS);

            } else {
                //nexthop exists already; a new flow is going to point to it, increment the flowrefCount by 1
                int flowrefCnt = nexthop.getFlowrefCount() + 1;
                VpnNexthop nh = new VpnNexthopBuilder().setKey(new VpnNexthopKey(ipAddress)).setFlowrefCount(flowrefCnt).build();
                LOG.trace("Updating vpnnextHop {} for refCount {} to Operational DS", nh, flowrefCnt);
                syncWrite(LogicalDatastoreType.OPERATIONAL, getVpnNextHopIdentifier(vpnId, ipAddress), nh, DEFAULT_CALLBACK);

            }
        }
        return groupId;
    }


    protected void addVpnNexthopToDS(BigInteger dpnId, long vpnId, String ipPrefix, long egressPointer) {

        InstanceIdentifierBuilder<VpnNexthops> idBuilder = InstanceIdentifier.builder(
            L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId));

        // Add nexthop to vpn node
        VpnNexthop nh = new VpnNexthopBuilder().
                setKey(new VpnNexthopKey(ipPrefix)).
                setDpnId(dpnId).
                setIpAddress(ipPrefix).
                setFlowrefCount(1).
                setEgressPointer(egressPointer).build();

        InstanceIdentifier<VpnNexthop> id1 = idBuilder
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix)).build();
        LOG.trace("Adding vpnnextHop {} to Operational DS", nh);
        syncWrite(LogicalDatastoreType.OPERATIONAL, id1, nh, DEFAULT_CALLBACK);

    }



    protected InstanceIdentifier<VpnNexthop> getVpnNextHopIdentifier(long vpnId, String ipAddress) {
        InstanceIdentifier<VpnNexthop> id = InstanceIdentifier.builder(
                L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId)).child(VpnNexthop.class, new VpnNexthopKey(ipAddress)).build();
        return id;
    }

    protected VpnNexthop getVpnNexthop(long vpnId, String ipAddress) {

        // check if vpn node is there
        InstanceIdentifierBuilder<VpnNexthops> idBuilder =
            InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class,
                                                              new VpnNexthopsKey(vpnId));
        InstanceIdentifier<VpnNexthops> id = idBuilder.build();
        Optional<VpnNexthops> vpnNexthops = read(LogicalDatastoreType.OPERATIONAL, id);
        if (vpnNexthops.isPresent()) {
            // get nexthops list for vpn
            List<VpnNexthop> nexthops = vpnNexthops.get().getVpnNexthop();
            for (VpnNexthop nexthop : nexthops) {
                if (nexthop.getIpAddress().equals(ipAddress)) {
                    // return nexthop
                    LOG.trace("VpnNextHop : {}", nexthop);
                    return nexthop;
                }
            }
            // return null if not found
        }
        return null;
    }


    public String getRemoteNextHopPointer(BigInteger localDpnId, BigInteger remoteDpnId,
                                                    long vpnId, String prefixIp, String nextHopIp) {
        String tunnelIfName = null;
        LOG.trace("getRemoteNextHopPointer: input [localDpnId {} remoteDpnId {}, vpnId {}, prefixIp {}, nextHopIp {} ]",
                  localDpnId, remoteDpnId, vpnId, prefixIp, nextHopIp);

        LOG.trace("getRemoteNextHopPointer: Calling ITM with localDpnId {} ", localDpnId);
        if (nextHopIp != null && !nextHopIp.isEmpty()) {
            try{
                // here use the config for tunnel type param
                tunnelIfName = getTunnelInterfaceName(remoteDpnId, IpAddressBuilder.getDefaultInstance(nextHopIp));
            }catch(Exception ex){
            LOG.error("Error while retrieving nexthop pointer for nexthop {} : ", nextHopIp, ex.getMessage());
            }
        }
        return tunnelIfName;
    }

    public BigInteger getDpnForPrefix(long vpnId, String prefixIp) {
        VpnNexthop vpnNexthop = getVpnNexthop(vpnId, prefixIp);
        BigInteger localDpnId = (vpnNexthop == null) ? null : vpnNexthop.getDpnId();
        return localDpnId;
    }


    private void removeVpnNexthopFromDS(long vpnId, String ipPrefix) {

        InstanceIdentifierBuilder<VpnNexthop> idBuilder = InstanceIdentifier.builder(L3nexthop.class)
                .child(VpnNexthops.class, new VpnNexthopsKey(vpnId))
                .child(VpnNexthop.class, new VpnNexthopKey(ipPrefix));
        InstanceIdentifier<VpnNexthop> id = idBuilder.build();
        // remove from DS
        LOG.trace("Removing vpn next hop from datastore : {}", id);
        delete(LogicalDatastoreType.OPERATIONAL, id);
    }

 
    public void removeLocalNextHop(BigInteger dpnId, Long vpnId, String ipAddress) {

        String nextHopLockStr = new String(vpnId + ipAddress);
        synchronized (nextHopLockStr.intern()) {
            VpnNexthop nh = getVpnNexthop(vpnId, ipAddress);
            if (nh != null) {
                int newFlowrefCnt = nh.getFlowrefCount() - 1;
                if (newFlowrefCnt == 0) { //remove the group only if there are no more flows using this group
                    GroupEntity groupEntity = MDSALUtil.buildGroupEntity(
                            dpnId, nh.getEgressPointer(), ipAddress, GroupTypes.GroupIndirect, null);
                    // remove Group ...
                    mdsalManager.removeGroup(groupEntity);
                    //update MD-SAL DS
                    removeVpnNexthopFromDS(vpnId, ipAddress);
                    //release groupId
                    removeNextHopPointer(getNextHopKey(vpnId, ipAddress));
                    LOG.debug("Local Next hop for {} on dpn {} successfully deleted", ipAddress, dpnId);
                } else {
                    //just update the flowrefCount of the vpnNexthop
                    VpnNexthop currNh = new VpnNexthopBuilder().setKey(new VpnNexthopKey(ipAddress)).setFlowrefCount(newFlowrefCnt).build();
                    LOG.trace("Updating vpnnextHop {} for refCount {} to Operational DS", currNh, newFlowrefCnt);
                    syncWrite(LogicalDatastoreType.OPERATIONAL, getVpnNextHopIdentifier(vpnId, ipAddress), currNh, DEFAULT_CALLBACK);
                }
            } else {
                //throw error
                LOG.error("Local Next hop for {} on dpn {} not deleted", ipAddress, dpnId);
            }
        }

    }


    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    private <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        tx.submit();
    }

    private <T extends DataObject> void delete(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    private InstanceIdentifier<Adjacency> getAdjacencyIdentifier(String vpnInterfaceName, String ipAddress) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
            .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).augmentation(
                Adjacencies.class).child(Adjacency.class, new AdjacencyKey(ipAddress)).build();
    }

    InstanceIdentifier<Adjacencies> getAdjListPath(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).augmentation(
                        Adjacencies.class).build();
    }
}
