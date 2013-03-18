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

import com.lordralex.ralexbot.api.EventField;
import com.lordralex.ralexbot.api.EventType;
import com.lordralex.ralexbot.api.Listener;
import com.lordralex.ralexbot.api.events.CommandEvent;

/**
 * @author Lord_Ralex
 * @version 1.0
 */
public class VoxelHeadCommands extends Listener {

    @Override
    public void setup() {
    }

    @Override
    @EventType(event = EventField.Command)
    public void runEvent(CommandEvent event) {
    }

    @Override
    public String[] getAliases() {
        return new String[]{
            "vh",
            "voxelhead"
        };
    }
}