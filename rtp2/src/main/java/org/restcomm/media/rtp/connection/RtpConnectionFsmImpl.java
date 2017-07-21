/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.media.rtp.connection;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.log4j.Logger;
import org.restcomm.media.rtp.MediaType;
import org.restcomm.media.rtp.RtpSession;
import org.restcomm.media.rtp.connection.exception.RtpConnectionException;
import org.restcomm.media.rtp.sdp.SdpBuilder;
import org.restcomm.media.sdp.SdpException;
import org.restcomm.media.sdp.SessionDescription;
import org.restcomm.media.sdp.fields.MediaDescriptionField;
import org.restcomm.media.spi.ConnectionMode;

import com.google.common.util.concurrent.FutureCallback;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class RtpConnectionFsmImpl extends AbstractRtpConnectionFsm {

    private static final Logger log = Logger.getLogger(RtpConnectionFsmImpl.class);

    private final RtpConnectionContext context;

    public RtpConnectionFsmImpl(RtpConnectionContext context) {
        super();
        this.context = context;
    }
    
    @Override
    public void enterParsingRemoteDescription(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final OpenContext openContext = (OpenContext) txContext;
        final String remoteDescription = openContext.getRemoteDescription();
        
        try {
            // Parse remote description and update global context
            SessionDescription remoteSession = openContext.getSdpParser().parse(remoteDescription);
            this.context.setRemoteDescription(remoteSession);
            
            // Move to next state
            fire(RtpConnectionEvent.PARSED_REMOTE_DESCRIPTION, txContext);
        } catch (SdpException e) {
            // Wrap exception
            String cname = this.context.getCname();
            Exception exception = new RtpConnectionException("RTP Connection " + cname + " could not parse remote session description" , e);
            openContext.setThrowable(exception);
            
            // Move to CORRUPTED state
            fire(RtpConnectionEvent.PARSE_REMOTE_DESCRIPTION_FAILURE, txContext);
        }
    }

    @Override
    public void enterAllocatingSession(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final OpenContext openContext = (OpenContext) txContext;
        final RtpSession session = openContext.getSession();
        final SocketAddress address = openContext.getAddress();

        // Open session. The callback will fire proper event to move to next state.
        AllocateSessionCallback callback = new AllocateSessionCallback(this, openContext);
        session.open(address, callback);
    }

    @Override
    public void enterSettingSessionMode(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final OpenContext openContext = (OpenContext) txContext;
        final RtpSession session = openContext.getSession();
        final ConnectionMode mode = openContext.getMode();

        // Set mode in context
        this.context.setMode(mode);

        // Update session mode. The callback will fire proper event to move to next state.
        UpdateSessionModeCallback callback = new UpdateSessionModeCallback(this, openContext);
        session.updateMode(mode, callback);
    }

    @Override
    public void enterNegotiatingSession(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final OpenContext openContext = (OpenContext) txContext;
        final RtpSession session = openContext.getSession();
        final String audioType = MediaType.AUDIO.name().toLowerCase();
        final MediaDescriptionField remoteSession = this.context.getRemoteDescription().getMediaDescription(audioType);

        if(remoteSession == null) {
            // Abort negotiation if remote peer does not declare an audio session
            RtpConnectionException exception = new RtpConnectionException("Remote peer did not declare an audio session");
            openContext.setThrowable(exception);
            fire(RtpConnectionEvent.SESSION_NEGOTIATION_FAILURE, txContext);
        } else {
            // Negotiate session. The callback will fire proper event to move to next state.
            NegotiateSessionCallback callback = new NegotiateSessionCallback(this, openContext);
            session.negotiate(remoteSession, callback);
        }
    }

    @Override
    public void enterGeneratingLocalDescription(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        final OpenContext openContext = (OpenContext) txContext;
        final String cname = this.context.getCname();
        final InetSocketAddress localAddress = (InetSocketAddress) openContext.getAddress();
        final String externalAddress = openContext.getExternalAddress();
        final RtpSession session = openContext.getSession();
        final SdpBuilder sdpBuilder = openContext.getSdpBuilder();

        try {
            // Generate local description
            SessionDescription answer = sdpBuilder.buildSessionDescription(false, cname, localAddress.getHostString(), externalAddress, session);
            // Register local description in global context
            this.context.setLocalDescription(answer);
            // Move to next OPEN state
            fire(RtpConnectionEvent.OPENED, txContext);
        } catch (Exception e) {
            // Wrap failure in proper exception type
            Exception exception = new RtpConnectionException("RTP Connection " + cname + " could not generate local description", e);
            openContext.setThrowable(exception);
            // Move to CORRUPTED state
            fire(RtpConnectionEvent.GENERATE_LOCAL_DESCRIPTION_FAILURE, txContext);
        }
    }

    @Override
    public void enterOpen(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        if (log.isDebugEnabled()) {
            if (RtpConnectionEvent.OPENED.equals(event)) {
                final OpenContext openContext = (OpenContext) txContext;
                final RtpSession session = openContext.getSession();
                final String cname = this.context.getCname();
                log.debug("RTP Connection " + cname + " opened session " + session.getSsrc());
            } else if (RtpConnectionEvent.OPENED.equals(event)) {
                final UpdateModeContext modeContext = (UpdateModeContext) txContext;
                final ConnectionMode mode = modeContext.getMode();
                final String cname = this.context.getCname();
                log.debug("RTP Connection " + cname + " updated mode to " + mode.name());
            }
        }

        // Let originator of the OPEN request know the operation completed successfully
        txContext.getOriginator().onSuccess(null);
    }

    @Override
    public void enterCorrupted(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        final RtpConnectionException exception;
        final String cname = this.context.getCname();

        switch (event) {
            case SESSION_ALLOCATION_FAILURE: {
                final OpenContext openContext = (OpenContext) txContext;
                final RtpSession session = openContext.getSession();
                final Throwable t = openContext.getThrowable();
                exception = new RtpConnectionException(
                        "RTP Connection " + cname + " could not allocate session " + session.getSsrc(), t);
                break;
            }

            case SESSION_NEGOTIATION_FAILURE: {
                final OpenContext openContext = (OpenContext) txContext;
                final RtpSession session = openContext.getSession();
                final Throwable t = openContext.getThrowable();
                exception = new RtpConnectionException(
                        "RTP Connection " + cname + " could not negotiate session " + session.getSsrc(), t);
                break;
            }

            case SESSION_MODE_UPDATE_FAILURE: {
                final RtpSession session;
                final Throwable t;

                if (txContext instanceof OpenContext) {
                    OpenContext openContext = (OpenContext) txContext;
                    session = openContext.getSession();
                    t = openContext.getThrowable();
                    exception = new RtpConnectionException(
                            "RTP Connection " + cname + " could not update mode of session " + session.getSsrc(), t);
                } else {
                    UpdateModeContext updateContext = (UpdateModeContext) txContext;
                    session = updateContext.getSession();
                    t = updateContext.getThrowable();
                    exception = new RtpConnectionException(
                            "RTP Connection " + cname + " could not update mode of session " + session.getSsrc(), t);
                }
                break;
            }

            default: {
                final OpenContext openContext = (OpenContext) txContext;
                final Throwable t = openContext.getThrowable();
                exception = new RtpConnectionException("RTP Connection " + cname + " became corrupted. Reason: " + event.name(),
                        t);
                break;
            }
        }

        // Let originator of the OPEN request know the operation failed
        txContext.getOriginator().onFailure(exception);
    }

    @Override
    public void enterUpdatingMode(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final UpdateModeContext modeContext = (UpdateModeContext) txContext;
        final ConnectionMode mode = modeContext.getMode();

        // Update connection-level mode
        // TODO Optimization: only try to update mode if currMode != newMode
        this.context.setMode(mode);
    }

    @Override
    public void enterUpdatingSessionMode(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final UpdateModeContext modeContext = (UpdateModeContext) txContext;
        final ConnectionMode mode = modeContext.getMode();
        final RtpSession session = modeContext.getSession();

        // Update session-level mode. The callback will fire proper event to move to next state.
        UpdateSessionModeCallback callback = new UpdateSessionModeCallback(this, modeContext);
        session.updateMode(mode, callback);
    }

    @Override
    public void enterSessionModeUpdated(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Move immediately to successful state
        fire(RtpConnectionEvent.MODE_UPDATED, txContext);
    }

    @Override
    public void enterClosingSession(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final CloseContext closeContext = (CloseContext) txContext;
        final RtpSession session = closeContext.getSession();

        // Close session. The callback will fire proper event to move to next state.
        SessionCloseCallback callback = new SessionCloseCallback(this, closeContext);
        session.close(callback);
    }

    @Override
    public void enterClosedSession(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        // Get relevant data from context
        final CloseContext closeContext = (CloseContext) txContext;
        final RtpSession session = closeContext.getSession();
        final String cname = this.context.getCname();

        // Log events
        if (RtpConnectionEvent.SESSION_CLOSED.equals(event)) {
            if (log.isDebugEnabled()) {
                log.debug("RTP connection " + cname + " closed session " + session.getSsrc());
            }
        } else {
            final Throwable t = closeContext.getThrowable();
            log.warn("RTP connection " + cname + " did not close session " + session.getSsrc() + " properly!", t);
        }

        // Move to final state
        fire(RtpConnectionEvent.CLOSED, txContext);
    }

    @Override
    public void enterClosed(RtpConnectionState from, RtpConnectionState to, RtpConnectionEvent event, RtpConnectionTransitionContext txContext) {
        if (log.isDebugEnabled()) {
            final String cname = this.context.getCname();
            log.debug("RTP connection " + cname + " is closed.");
        }
        
        // Clean connection context
        this.context.setLocalDescription(null);
        this.context.setRemoteDescription(null);
        this.context.setMode(ConnectionMode.INACTIVE);

        // Warn callback that CLOSE operation is complete
        final FutureCallback<Void> callback = txContext.getOriginator();
        callback.onSuccess(null);
    }

}