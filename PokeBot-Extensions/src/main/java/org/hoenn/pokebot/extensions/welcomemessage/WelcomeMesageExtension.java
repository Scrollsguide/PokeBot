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
package org.hoenn.pokebot.extensions.welcomemessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.hoenn.pokebot.PokeBot;
import org.hoenn.pokebot.api.CommandExecutor;
import org.hoenn.pokebot.api.EventExecutor;
import org.hoenn.pokebot.api.Listener;
import org.hoenn.pokebot.api.Utilities;
import org.hoenn.pokebot.api.events.CommandEvent;
import org.hoenn.pokebot.api.events.JoinEvent;
import org.hoenn.pokebot.extension.Extension;

/**
 * @author Lord_Ralex
 */
public class WelcomeMesageExtension extends Extension implements Listener, CommandExecutor {

    private final Map<String, String> mappings = new HashMap<>();

    @Override
    public void load() {
        PokeBot.getInstance().getExtensionManager().addCommandExecutor(this);
        PokeBot.getInstance().getExtensionManager().addListener(this);
    }

    @EventExecutor
    public void runEvent(final JoinEvent event) {
        String message = mappings.get(event.getChannel().getName().toLowerCase());
        if (message == null || message.isEmpty()) {
            return;
        }
        final String[] parts = message.split(";;");
        PokeBot.getInstance().getScheduler().scheduleTask(new Runnable() {
            @Override
            public void run() {
                for (String part : parts) {
                    event.getUser().sendNotice(part.replace("{user}", event.getUser().getNick()).replace("{channel}", event.getChannel().getName()));
                }
            }
        }, 2, TimeUnit.SECONDS);

    }

    @Override
    public void runEvent(CommandEvent event) {
        if (event.getUser().hasOP(event.getChannel().getName()) || event.getUser().hasPermission(event.getChannel().getName(), "saymessage.set")) {
            if (event.getArgs().length == 0) {
                mappings.remove(event.getChannel().getName().toLowerCase());
                event.getChannel().sendMessage("I will stop messaging people when they join");
            } else {
                mappings.put(event.getChannel().getName().toLowerCase(), Utilities.toString(event.getArgs()));
                event.getChannel().sendMessage("I will start messaging people this when they join: " + Utilities.toString(event.getArgs()));
            }
        }
    }

    @Override
    public String[] getAliases() {
        return new String[]{
            "message"
        };
    }
}
