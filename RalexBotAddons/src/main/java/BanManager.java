
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
import com.lordralex.ralexbot.RalexBot;
import com.lordralex.ralexbot.api.EventField;
import com.lordralex.ralexbot.api.EventType;
import com.lordralex.ralexbot.api.Listener;
import com.lordralex.ralexbot.api.channels.Channel;
import com.lordralex.ralexbot.api.events.CommandEvent;
import com.lordralex.ralexbot.api.events.ConnectionEvent;
import com.lordralex.ralexbot.api.users.BotUser;
import com.lordralex.ralexbot.api.users.User;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @author Lord_Ralex
 */
public class BanManager extends Listener {

    private final Set<Ban> banList = new TreeSet<>(new BanComparator());
    private final ScheduledExecutorService srv = Executors.newSingleThreadScheduledExecutor();
    private final UnbanRunnable unbanRunnable = new UnbanRunnable();

    @Override
    public void onLoad() {
        if (new File("bans.dat").exists()) {
            try {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File("bans.dat")))) {
                    boolean reading = true;
                    while (reading) {
                        try {
                            Object obj = in.readObject();
                            if (obj instanceof Ban) {
                                banList.add((Ban) obj);
                                RalexBot.log("    Loading ban: " + obj.toString());
                            }
                        } catch (ClassNotFoundException ex) {
                            RalexBot.logSevere("An error occured on loading bans", ex);
                        } catch (EOFException e) {
                            reading = false;
                        }
                    }
                }
            } catch (IOException ex) {
                RalexBot.logSevere("An error occured on loading bans", ex);
            }
        }
    }

    @Override
    public void onUnload() {
        saveBans();
        srv.shutdownNow();
    }

    public void saveBans() {
        new File("bans.dat").delete();
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(new File("bans.dat")))) {
                for (Ban ban : banList) {
                    out.writeObject(ban);
                }
            }
        } catch (IOException ex) {
            RalexBot.logSevere("An error occured on saving bans", ex);
        }
    }

    @Override
    @EventType(event = EventField.Connection)
    public void runEvent(ConnectionEvent event) {
        unbanRunnable.scheduleLater();
    }

    @Override
    @EventType(event = EventField.Command)
    public void runEvent(CommandEvent event) {
        if (event.getChannel() == null && event.getSender() != null) {
            return;
        }
        if (event.getSender() != null) {
            if (!event.getSender().hasOP(event.getChannel().getName())) {
                return;
            }
        }
        if (event.getArgs().length == 0) {
            return;
        }
        String chan = event.getChannel().getName();
        String banLine = event.getArgs()[0];
        List<String> list = event.getChannel().getUsers();
        if (list.contains(banLine)) {
            User user = User.getUser(banLine);
            banLine = "*!*@" + user.getIP();
        }
        String timeLine;
        if (event.getArgs().length == 1) {
            timeLine = "12 months";
        } else {
            timeLine = event.getArgs()[1];
            for (int i = 2; i < event.getArgs().length; i++) {
                timeLine += " " + event.getArgs()[i];
            }
        }
        long time = parseTime(timeLine);
        BotUser.getBotUser().ban(chan, banLine);
        Ban newBan = new Ban(time + System.currentTimeMillis(), banLine, chan);
        banList.add(newBan);
        synchronized (unbanRunnable) {
            unbanRunnable.scheduleLater();
        }
        event.getChannel().sendMessage("I have banned " + banLine + " from " + chan + " for " + time);
        saveBans();
    }

    @Override
    public String[] getAliases() {
        return new String[]{
            "tempban",
            "tb"
        };
    }

    private long parseTime(String time) {
        int banTime = 0;
        RalexBot.log("Input: " + time);
        String[] args = time.split(",");
        for (String arg : args) {
            String arg0 = arg.split(" ")[1].trim().toLowerCase();
            int count = Integer.parseInt(arg.split(" ")[0].trim());
            if (arg0.startsWith("month")) {
                banTime += (60 * 60 * 24 * 30) * count;
            } else if (arg0.startsWith("week")) {
                banTime += (60 * 60 * 24 * 7) * count;
            } else if (arg0.startsWith("day")) {
                banTime += (60 * 60 * 24) * count;
            } else if (arg0.startsWith("hour")) {
                banTime += (60 * 60) * count;
            } else if (arg0.startsWith("minute")) {
                banTime += (60) * count;
            }
        }
        banTime *= 1000;
        RalexBot.log("Output: " + banTime);
        return banTime;
    }

    private class BanComparator implements Comparator<Ban> {

        @Override
        public int compare(Ban o1, Ban o2) {
            int result;
            if (o1.getUnbanTime() > o2.getUnbanTime()) {
                result = 1;
            } else if (o1.getUnbanTime() < o2.getUnbanTime()) {
                result = -1;
            } else {
                int i = o1.getChannel().compareTo(o2.getChannel());
                if (i == 0) {
                    result = o1.getUnbanString().compareTo(o2.getUnbanString());
                } else {
                    result = i;
                }
            }
            return result;
        }
    }

    private class UnbanRunnable implements Runnable {

        @Override
        public void run() {
            synchronized (banList) {
                if (banList.isEmpty()) {
                    return;
                }
            }
            Ban ban;
            synchronized (banList) {
                ban = banList.iterator().next();
            }
            long currTime = System.currentTimeMillis();
            if (ban.getUnbanTime() > currTime) {
                scheduleLater();
                return;
            } else {

                BotUser bot = BotUser.getBotUser();
                bot.unban(ban.getChannel(), ban.getUnbanString());
                synchronized (banList) {
                    banList.remove(ban);
                }
                Channel.getChannel(ban.getChannel()).sendMessage("I have unbanned " + ban.getUnbanString() + " from " + ban.getChannel());
                scheduleLater();
                saveBans();
            }
        }

        public void scheduleLater() {
            if (!banList.isEmpty()) {
                Ban ban;
                synchronized (banList) {
                    ban = banList.iterator().next();
                }
                long initDelay = ban.getUnbanTime() - System.currentTimeMillis();
                if (initDelay < 0) {
                    initDelay = 0;
                }
                RalexBot.log("Unbanning a user in " + initDelay);
                srv.schedule(this, initDelay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
