/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.bgpmanager;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import javax.management.*;
import org.apache.thrift.TException;
import org.opendaylight.bgpmanager.api.IBgpManager;
import org.opendaylight.bgpmanager.commands.Commands;
import org.opendaylight.bgpmanager.oam.*;
import org.opendaylight.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
//import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpManager implements BindingAwareProvider, AutoCloseable, IBgpManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BgpManager.class);
    private BgpConfigurationManager bcm;
    private FibDSWriter fibDSWriter;
    //private IITMProvider        itmProvider;
    private DataBroker dataBroker;
    private BgpAlarmBroadcaster     qbgpAlarmProducer = null;
    private MBeanServer qbgpAlarmServer = null;
    private NotificationFilter  qbgpAlarmFilter = null;
    final static int DEFAULT_STALEPATH_TIME = 210;
    final static boolean DEFAULT_FBIT = true;

    public BgpCounters bgpCounters;
    public Timer bgpCountersTimer;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        try {
            dataBroker = session.getSALService(DataBroker.class);
            fibDSWriter = new FibDSWriter(dataBroker);
            BgpUtil.setBroker(dataBroker);
            bcm = new BgpConfigurationManager(this);
            Commands commands = new Commands(this);
            ConfigureBgpCli.setBgpManager(this);
            LOGGER.info("BgpManager started");
        } catch (Exception e) {
            LOGGER.error("Failed to start BgpManager: "+e);
        }

        // Set up the Infra for Posting BGP Alarms as JMX notifications.
        try {
            qbgpAlarmProducer = new BgpAlarmBroadcaster();
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName alarmObj = new ObjectName("SDNC.FM:name=BgpAlarmObj");
            mbs.registerMBean(qbgpAlarmProducer, alarmObj);
        } catch (JMException e) {
            LOGGER.error("Adding a NotificationBroadcaster failed." + e.toString());
            e.printStackTrace();
        }
    }

   @Override
    public void close() throws Exception {
        bcm.close(); 
        LOGGER.info("BgpManager Closed");
   }

    /*public void setITMProvider(IITMProvider itmProvider) {
        this.itmProvider = itmProvider;
    }

    public IITMProvider getItmProvider() { return this.itmProvider; } */

    public Bgp getConfig() {
      return bcm.get();
    }

    public void configureGR(int stalepathTime) throws TException {
      bcm.addGracefulRestart(stalepathTime);
    }

    public void delGracefulRestart() throws Exception {
      bcm.delGracefulRestart();
    }

    public void addNeighbor(String ipAddress, long asNum) throws TException {
      bcm.addNeighbor(ipAddress, (int) asNum);
    }

    public void addEbgpMultihop(String ipAddress, int nhops) throws TException {
      bcm.addEbgpMultihop(ipAddress, nhops);
    }
    
    public void addUpdateSource(String ipAddress, String srcIp) throws TException {
      bcm.addUpdateSource(ipAddress, srcIp);
    }

    public void addAddressFamily(String ipAddress, af_afi afi, af_safi safi) throws TException {
      bcm.addAddressFamily(ipAddress, afi.getValue(), safi.getValue());
    }

    public void deleteNeighbor(String ipAddress) throws TException {
      bcm.delNeighbor(ipAddress);
    }

    @Override
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts) throws Exception {
        bcm.addVrf(rd, new ArrayList<String>(importRts), 
                       new ArrayList<String>(exportRts)); 
    }

    @Override
    public void deleteVrf(String rd) throws Exception {
      bcm.delVrf(rd);
    }

    @Override
    public void addPrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception {
      fibDSWriter.addFibEntryToDS(rd, prefix, nextHop, vpnLabel);
      bcm.addPrefix(rd, prefix, nextHop, vpnLabel);
    }

    @Override
    public void deletePrefix(String rd, String prefix) throws Exception {
      fibDSWriter.removeFibEntryFromDS(rd, prefix);
      bcm.delPrefix(rd, prefix);
    }

    @Override
    public void advertisePrefix(String rd, String prefix, String nextHop, int vpnLabel) throws Exception {
        bcm.addPrefix(rd, prefix, nextHop, vpnLabel);
    }

    @Override
    public void withdrawPrefix(String rd, String prefix) throws Exception {
        bcm.delPrefix(rd, prefix);
    }

    public void setQbgpLog(String fileName, String debugLevel) throws Exception {
      bcm.addLogging(fileName, debugLevel);
    }

    public void delLogging() throws Exception {
      bcm.delLogging();
    }

    public void startBgp(int asn, String routerId, int spt, boolean fbit) {
      bcm.startBgp(asn, routerId, spt, fbit);
    }

    public void stopBgp() {
      bcm.stopBgp();
    }

    public void startConfig(String host, int port) {
      bcm.startConfig(host, port);
    }

    public void stopConfig() {
      bcm.stopConfig();
    }

    @Override
    public String getDCGwIP() {
        Bgp conf = getConfig();
        if (conf == null) {
          return null;
        }
        List<Neighbors> nbrs = conf.getNeighbors();
        if (nbrs == null) {
          return null;
        }
        return nbrs.get(0).getAddress().getValue();
    }

    public MBeanServer getBgpAlarmServer() {
        return qbgpAlarmServer;
    }

    public synchronized void sendNotificationEvent(String pfx, int code, int subcode) {
        BgpAlarmErrorCodes errorSubCode;
        if (code != BgpConstants.BGP_NOTIFY_CEASE_CODE) {
            // CEASE Notifications alone have to be reported to the CBA.
            // Silently return here. No need to log because tons
            // of non-alarm notifications will be sent to the SDNc.
            return;
        }
        errorSubCode = BgpAlarmErrorCodes.checkErrorSubcode(subcode);
        if (errorSubCode == BgpAlarmErrorCodes.ERROR_IGNORE) {
            // Need to report only those subcodes, defined in
            // BgpAlarmErrorCodes enum class.
            return;
        }
        String alarmString = "";
        alarmString = "Alarm (" + code + "," + subcode + ") from neighbor " + pfx;
        qbgpAlarmProducer.sendBgpAlarmInfo(pfx, code, subcode);
    }

    public Timer getBgpCountersTimer() {
        return bgpCountersTimer;
    }

    public BgpCounters getBgpCounters() {
        return bgpCounters;
    }

    public  void setBgpCountersTimer (Timer t) {
        bgpCountersTimer = t;
    }

    public void startBgpCountersTask() {
        if (getBgpCounters() == null) {

            try {
                bgpCounters = new BgpCounters();
                setBgpCountersTimer(new Timer(true));
                getBgpCountersTimer().scheduleAtFixedRate(bgpCounters, 0, 120 * 1000);


                LOGGER.info("Bgp Counters task scheduled for every two minutes.");
            } catch (Exception e) {
                System.out.println("Could not start the timertask for Bgp Counters.");
                e.printStackTrace();
            }

            try {
                setQbgpLog(BgpConstants.BGP_DEF_LOG_FILE, BgpConstants.BGP_DEF_LOG_LEVEL);
            } catch (Exception e) {
                System.out.println("Could not set the default options for logging");
            }
        }
    }

    public void stopBgpCountersTask() {
        Timer t = getBgpCountersTimer();
        if (getBgpCounters() != null) {
            t.cancel();
            setBgpCountersTimer(null);
            bgpCounters = null;
        }
    }

    public FibDSWriter getFibWriter() {
        return fibDSWriter;
    } 

    public DataBroker getBroker() {
        return dataBroker;
    } 

    public String getConfigHost() {
        return bcm.getConfigHost();
    }

    public int getConfigPort() {
        return bcm.getConfigPort();
    }

    public void bgpRestarted() {
        bcm.bgpRestarted();
    }
}
