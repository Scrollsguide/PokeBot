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
import com.lordralex.ralexbot.api.Priority;
import com.lordralex.ralexbot.api.channels.Channel;
import com.lordralex.ralexbot.api.events.MessageEvent;
import com.lordralex.ralexbot.api.sender.Sender;
import com.lordralex.ralexbot.api.users.BotUser;
import com.lordralex.ralexbot.settings.Settings;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @version 1.0
 * @author Lord_Ralex
 */
public class RecordedMessage extends Listener {

    final Map<String, Thread> threads = new ConcurrentHashMap<>();
    int counter = 0;

    @Override
    @EventType(event = EventField.Message, priority = Priority.LOW)
    public void runEvent(MessageEvent event) {
        if (!event.getSender().hasOP(event.getChannel().getName())) {
            return;
        }
        Sender sender = event.getChannel();
        if (sender == null) {
            sender = event.getSender();
        }
        if (event.getMessage().startsWith(BotUser.getBotUser().getNick() + ", please teach")) {
            String load = event.getMessage().replace(BotUser.getBotUser().getNick() + ", please teach", "").trim();
            load = load.split(" ")[0];
            AutomatedMessageThread thread = new AutomatedMessageThread(load, event.getChannel(), counter);
            counter++;
            synchronized (threads) {
                threads.put(thread.getName(), thread);
            }
            thread.start();
        } else if (event.getMessage().startsWith(BotUser.getBotUser().getNick() + ", please stop")) {
            String load = event.getMessage().replace(BotUser.getBotUser().getNick() + ", please stop", "").trim();
            load = load.split(" ")[0];
            if (load.equalsIgnoreCase("all")) {
                sender.sendMessage("Stopping all then");
                Set<String> ids = threads.keySet();
                for (String id : ids) {
                    threads.remove(id).interrupt();
                }
                sender.sendMessage("All stopped");
            } else {
                Thread thread = threads.remove(load);
                if (thread == null) {
                    sender.sendMessage("No thread with that name");
                } else {
                    thread.interrupt();
                }
            }
        }
    }

    private class AutomatedMessageThread extends Thread {

        final List<String> messages;
        final Channel chan;
        final int message_delay;
        boolean stop = false;

        public AutomatedMessageThread(String section, Channel channel, int ID) {
            super("message_" + ID);
            chan = channel;
            chan.sendMessage("Loading " + section);
            messages = Settings.getGlobalSettings().getStringList(section);
            message_delay = Settings.getGlobalSettings().getInt(section + "_timing");
            chan.sendMessage("Loaded with name " + this.getName());
        }

        @Override
        public void run() {
            while (!isInterrupted() && !messages.isEmpty()) {
                synchronized (this) {
                    try {
                        wait(message_delay * 1000);
                    } catch (InterruptedException ex) {
                    }
                }
                if (!stop && !isInterrupted() && !messages.isEmpty()) {
                    synchronized (messages) {
                        String nextLine = messages.remove(0);
                        chan.sendMessage(nextLine);
                    }
                }
            }
            synchronized (threads) {
                threads.remove(this.getName());
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
            stop = true;
        }

        @Override
        public boolean isInterrupted() {
            if (super.isInterrupted() || stop) {
                return true;
            } else {
                return false;
            }
        }
    }
}
