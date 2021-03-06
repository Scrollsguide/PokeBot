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
package org.hoenn.pokebot.api.events;

import org.hoenn.pokebot.api.users.User;

/**
 * @author Lord_Ralex
 * @version 1.0
 */
public class PermissionEvent implements UserEvent {

    private final User user;
    private final boolean isForced;
    private final long timestamp = System.currentTimeMillis();

    public PermissionEvent(User u) {
        this(u, false);
    }

    public PermissionEvent(User u, boolean isF) {
        user = u;
        isForced = isF;
    }

    public PermissionEvent(String u) {
        this(User.getUser(u));
    }

    @Override
    public User getUser() {
        return user;
    }

    public boolean isForced() {
        return isForced;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
