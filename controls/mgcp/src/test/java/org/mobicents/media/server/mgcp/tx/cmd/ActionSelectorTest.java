/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mobicents.media.server.mgcp.tx.cmd;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.mgcp.MgcpEvent;
import org.mobicents.media.server.mgcp.MgcpProvider;
import org.mobicents.media.server.mgcp.message.MgcpRequest;
import org.mobicents.media.server.mgcp.tx.Action;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.scheduler.ServiceScheduler;
import org.mobicents.media.server.utils.Text;

/**
 *
 * @author kulikov
 */
public class ActionSelectorTest {
    
    private Scheduler mediaScheduler;
    private final Scheduler scheduler = new ServiceScheduler();
    
    private ActionSelector selector;
    private MgcpProvider mgcpProvider;
    
    private UdpManager udpInterface;
    private SocketAddress address;
    
    @Before
    public void setUp() throws IOException {
        mediaScheduler = new ServiceScheduler();
        mediaScheduler.start();
        
        udpInterface = new UdpManager(scheduler);
        udpInterface.setBindAddress("localhost");
        scheduler.start();
        udpInterface.start();
        
        mgcpProvider = new MgcpProvider(udpInterface, 1024);
        address = new InetSocketAddress("localhost", 2425);
        
        selector = new ActionSelector(mediaScheduler);
    }
    
    @After
    public void tearDown() {
        mgcpProvider.shutdown();
        scheduler.stop();
        mediaScheduler.stop();
    }

    /**
     * Test of getAction method, of class ActionSelector.
     */
    @Test
    public void testCrcx() {
        MgcpEvent evt = (MgcpEvent) mgcpProvider.createEvent(MgcpEvent.REQUEST, address);
        MgcpRequest msg = (MgcpRequest) evt.getMessage();
        
        msg.setCommand(new Text("CRCX"));
        msg.setEndpoint(new Text("test@localhost:2427"));
        msg.setTxID(1);
        
        Action action = selector.getAction(evt);
        assertTrue("Unexpected action", action instanceof CreateConnectionCmd);
        
    }
    
    @Test
    public void testMdxc() {
        MgcpEvent evt = (MgcpEvent) mgcpProvider.createEvent(MgcpEvent.REQUEST, address);
        MgcpRequest msg = (MgcpRequest) evt.getMessage();
        
        msg.setCommand(new Text("MDCX"));
        msg.setEndpoint(new Text("test@localhost:2427"));
        msg.setTxID(1);
        
        Action action = selector.getAction(evt);
        assertTrue("Unexpected action", action instanceof ModifyConnectionCmd);        
    }
    
    @Test
    public void testDlcx() {
        MgcpEvent evt = (MgcpEvent) mgcpProvider.createEvent(MgcpEvent.REQUEST, address);
        MgcpRequest msg = (MgcpRequest) evt.getMessage();
        
        msg.setCommand(new Text("DLCX"));
        msg.setEndpoint(new Text("test@localhost:2427"));
        msg.setTxID(1);
        
        Action action = selector.getAction(evt);
        assertTrue("Unexpected action", action instanceof DeleteConnectionCmd);        
    }
    
    @Test
    public void testRqnt() {
        MgcpEvent evt = (MgcpEvent) mgcpProvider.createEvent(MgcpEvent.REQUEST, address);
        MgcpRequest msg = (MgcpRequest) evt.getMessage();
        
        msg.setCommand(new Text("RQNT"));
        msg.setEndpoint(new Text("test@localhost:2427"));
        msg.setTxID(1);
        
        Action action = selector.getAction(evt);
        assertTrue("Unexpected action", action instanceof NotificationRequestCmd);        
    }

    @Test
    public void testNtfy() {
        MgcpEvent evt = (MgcpEvent) mgcpProvider.createEvent(MgcpEvent.REQUEST, address);
        MgcpRequest msg = (MgcpRequest) evt.getMessage();
        
        msg.setCommand(new Text("NTFY"));
        msg.setEndpoint(new Text("test@localhost:2427"));
        msg.setTxID(1);
        
        Action action = selector.getAction(evt);
        assertTrue("Unexpected action", action instanceof NotifyCmd);        
    }
    
    
}
