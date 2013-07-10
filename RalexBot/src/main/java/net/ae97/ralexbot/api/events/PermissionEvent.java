/*
 * Copyright (C) 2013 Lord_Ralex
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ae97.ralexbot.api.events;

import net.ae97.ralexbot.api.channels.Channel;
import net.ae97.ralexbot.api.users.User;

/**
 * @author Lord_Ralex
 * @version 1.0
 */
public class PermissionEvent implements UserEvent, ChannelEvent {

    private final User user;
    private final Channel channel;

    public PermissionEvent(User u, Channel c) {
        user = u;
        channel = c;
    }

    public PermissionEvent(String u, String c) {
        this(User.getUser(u), Channel.getChannel(c));
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public User getUser() {
        return user;
    }
}
