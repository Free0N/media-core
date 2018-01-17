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

package org.restcomm.media.control.mgcp.command.dlcx;

import org.restcomm.media.control.mgcp.connection.MgcpConnection;

import com.google.common.util.concurrent.FutureCallback;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class UnregisterConnectionCallback implements FutureCallback<MgcpConnection> {

    private final DeleteConnectionContext context;
    private final DeleteConnectionFsm fsm;

    public UnregisterConnectionCallback(DeleteConnectionContext context, DeleteConnectionFsm fsm) {
        this.context = context;
        this.fsm = fsm;
    }

    @Override
    public void onSuccess(MgcpConnection result) {
        // Update list of unregistered connections
        this.context.setUnregisteredConnections(new MgcpConnection[] { result });

        // TODO Gather statistics from connection
        final String stats = "PS=0, PR=0";
        this.context.setConnectionParams(stats);

        this.fsm.fire(DeleteConnectionEvent.UNREGISTERED_CONNECTIONS, this.context);
    }

    @Override
    public void onFailure(Throwable t) {
        this.context.setError(t);
        this.fsm.fire(DeleteConnectionEvent.FAILURE, this.context);
    }

}