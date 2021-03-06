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
package org.hoenn.pokebot.eventhandler;

import java.io.IOException;
import org.hoenn.pokebot.api.events.JoinEvent;
import org.hoenn.pokebot.api.events.ActionEvent;
import org.hoenn.pokebot.api.events.PermissionEvent;
import org.hoenn.pokebot.api.events.PrivateMessageEvent;
import org.hoenn.pokebot.api.events.NickChangeEvent;
import org.hoenn.pokebot.api.events.NoticeEvent;
import org.hoenn.pokebot.api.events.KickEvent;
import org.hoenn.pokebot.api.events.PartEvent;
import org.hoenn.pokebot.api.events.QuitEvent;
import org.hoenn.pokebot.api.events.Event;
import org.hoenn.pokebot.api.events.CommandEvent;
import org.hoenn.pokebot.api.events.MessageEvent;
import org.hoenn.pokebot.api.Listener;
import org.hoenn.pokebot.api.Priority;
import org.hoenn.pokebot.api.users.User;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.hoenn.pokebot.PokeBot;
import org.hoenn.pokebot.api.CommandExecutor;
import org.hoenn.pokebot.api.EventExecutor;
import org.hoenn.pokebot.api.events.CancellableEvent;
import org.hoenn.pokebot.api.events.ConnectionEvent;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.UserSnapshot;
import org.pircbotx.hooks.ListenerAdapter;

public final class EventHandler extends ListenerAdapter {

    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
    private final EventRunner runner;
    private static final List<CommandPrefix> commandChars = new ArrayList<>();
    private final PircBotX masterBot;
    private final ExecutorService execServ;
    private final Set<Class<? extends Event>> eventClasses = new HashSet<>();
    private final Map<Class<? extends Event>, Set<EventHandler.EventExecutorService>> eventExecutors = new ConcurrentHashMap<>();
    private final Set<CommandExecutor> commandExecutors = new HashSet<>();

    public EventHandler(PircBotX bot) {
        super();
        masterBot = bot;
        runner = new EventRunner();
        runner.setName("Event_Runner_Thread");
        execServ = Executors.newFixedThreadPool(5);
    }

    public void load() {
        List<String> settings = PokeBot.getInstance().getSettings().getStringList("command-prefix");
        commandChars.clear();
        if (settings.isEmpty()) {
            settings.add("**");
        }
        for (String commandChar : settings) {
            String[] args = commandChar.split("\\|");
            String prefix = args[0];
            String owner;
            if (args.length == 1) {
                owner = null;
            } else {
                owner = args[1];
            }
            PokeBot.log(Level.INFO, "Adding command prefix: " + prefix + (owner == null ? "" : " ( " + owner + ")"));
            commandChars.add(new CommandPrefix(prefix, owner));
        }
        eventExecutors.clear();
        commandExecutors.clear();
        eventClasses.clear();
        registerEvent(ActionEvent.class);
        registerEvent(ConnectionEvent.class);
        registerEvent(JoinEvent.class);
        registerEvent(KickEvent.class);
        registerEvent(MessageEvent.class);
        registerEvent(NickChangeEvent.class);
        registerEvent(NoticeEvent.class);
        registerEvent(PartEvent.class);
        registerEvent(PermissionEvent.class);
        registerEvent(PrivateMessageEvent.class);
        registerEvent(QuitEvent.class);
    }

    public void unload() {
        eventExecutors.clear();
        commandExecutors.clear();
        eventClasses.clear();
    }

    public void registerEvent(Class<? extends Event> cl) {
        eventClasses.add(cl);
        eventExecutors.put(cl, new HashSet<EventExecutorService>());
    }

    public void registerListener(Listener list) {
        PokeBot.log(Level.INFO, "  Added listener: " + list.getClass().getName());
        Method[] methods = list.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(EventExecutor.class)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1) {
                    continue;
                }
                for (Class<? extends Event> clz : eventClasses) {
                    if (clz.equals(params[0])) {
                        eventExecutors.get(clz).add(new EventHandler.EventExecutorService(list, method, method.getAnnotation(EventExecutor.class).priority()));
                        PokeBot.log(Level.INFO, "    Registered event: " + clz.getName() + "(" + method.getAnnotation(EventExecutor.class).priority().toString() + ")");
                    }
                }
            }
        }
    }

    public void registerCommandExecutor(CommandExecutor executor) {
        PokeBot.log(Level.INFO, "  Added command executor: " + executor.getClass().getName());
        commandExecutors.add(executor);
    }

    public Set<Class<? extends Event>> getEventClasses() {
        return eventClasses;
    }

    public void startQueue() {
        if (!runner.isAlive()) {
            runner.start();
        }
    }

    @Override
    public void onMessage(org.pircbotx.hooks.events.MessageEvent event) {
        Event nextEvt;
        if (isCommand(event.getMessage())) {
            for (CommandPrefix commandchar : commandChars) {
                if (event.getMessage().startsWith(commandchar.getPrefix())) {
                    if (commandchar.getOwner() != null && event.getChannel().getUsers().contains(masterBot.getUser(commandchar.getOwner()))) {
                        return;
                    }
                }
            }
            nextEvt = new CommandEvent(event);
        } else {
            nextEvt = new MessageEvent(event);
        }
        fireEvent(nextEvt);
    }

    @Override
    public void onPrivateMessage(org.pircbotx.hooks.events.PrivateMessageEvent event) throws Exception {
        Event nextEvt;
        if (isCommand(event.getMessage())) {
            nextEvt = new CommandEvent(event);
        } else {
            nextEvt = new PrivateMessageEvent(event);
        }
        fireEvent(nextEvt);
    }

    @Override
    public void onNotice(org.pircbotx.hooks.events.NoticeEvent event) throws Exception {
        Event nextEvt;
        if (isCommand(event.getMessage())) {
            nextEvt = new CommandEvent(event);
        } else {
            nextEvt = new NoticeEvent(event);
        }
        fireEvent(nextEvt);
    }

    @Override
    public void onJoin(org.pircbotx.hooks.events.JoinEvent event) throws Exception {
        JoinEvent nextEvt = new JoinEvent(event);
        fireEvent(nextEvt);
    }

    @Override
    public void onNickChange(org.pircbotx.hooks.events.NickChangeEvent event) throws Exception {
        NickChangeEvent nextEvt = new NickChangeEvent(event);
        fireEvent(nextEvt);
    }

    @Override
    public void onQuit(org.pircbotx.hooks.events.QuitEvent event) throws Exception {
        UserSnapshot user = event.getUser();
        Set<Channel> channels = user.getChannels();
        for (Channel chan : channels) {
            if (chan.getUsers().contains(masterBot.getUserBot())) {
                PartEvent partEvent = new PartEvent(user, chan);
                fireEvent(partEvent);
            }
        }
        QuitEvent nextEvt = new QuitEvent(event);
        fireEvent(nextEvt);
    }

    @Override
    public void onPart(org.pircbotx.hooks.events.PartEvent event) throws Exception {
        PartEvent nextEvt = new PartEvent(event);
        fireEvent(nextEvt);
    }

    @Override
    public void onAction(org.pircbotx.hooks.events.ActionEvent event) throws Exception {
        ActionEvent nextEvt = new ActionEvent(event);
        fireEvent(nextEvt);
    }

    @Override
    public void onKick(org.pircbotx.hooks.events.KickEvent event) throws Exception {
        KickEvent nextEvt = new KickEvent(event);
        fireEvent(nextEvt);
    }

    private boolean isCommand(String message) {
        for (CommandPrefix code : commandChars) {
            if (message.startsWith(code.getPrefix())) {
                return true;
            }
        }
        return false;
    }

    public void fireEvent(final Event event) {
        queue.add(event);
        runner.ping();
    }

    public void stopRunner() {
        synchronized (runner) {
            runner.interrupt();
        }
        synchronized (eventExecutors) {
            eventExecutors.clear();
        }
    }

    public static List<String> getCommandPrefixes() {
        List<String> clone = new ArrayList<>();
        for (CommandPrefix prefix : commandChars) {
            clone.add(prefix.getPrefix());
        }
        return clone;
    }

    private class EventRunner extends Thread {

        @Override
        public void run() {
            boolean run = true;
            while (run) {
                Event next = queue.poll();
                if (next == null) {
                    synchronized (this) {
                        try {
                            this.wait();
                        } catch (InterruptedException ex) {
                            run = false;
                        }
                    }
                    continue;
                }

                if (next instanceof PermissionEvent) {
                    PokeBot.getInstance().getPermManager().runPermissionEvent((PermissionEvent) next);
                } else if (next instanceof CommandEvent) {
                    CommandEvent evt = (CommandEvent) next;
                    org.hoenn.pokebot.api.users.User user = evt.getUser();
                    org.hoenn.pokebot.api.channels.Channel chan = evt.getChannel();
                    if (user.getNick().toLowerCase().endsWith("esper.net")) {
                        continue;
                    }
                    PermissionEvent permEvent = new PermissionEvent(user);
                    try {
                        PokeBot.getInstance().getPermManager().runPermissionEvent(permEvent);
                    } catch (Exception e) {
                        PokeBot.log(Level.SEVERE, "Error on permission event", e);
                        continue;
                    }
                    if (evt.getCommand().equalsIgnoreCase("reload")) {
                        User sender = evt.getUser();
                        if (sender != null) {
                            if (!sender.hasPermission((String) null, "bot.reload")) {
                                continue;
                            }
                        }
                        PokeBot.log(Level.INFO, "Performing a reload, please hold");
                        if (sender != null) {
                            sender.sendNotice("Reloading");
                        }
                        PokeBot.getInstance().getExtensionManager().unload();
                        PokeBot.getInstance().getEventHandler().unload();
                        PokeBot.getInstance().getEventHandler().load();
                        PokeBot.getInstance().getExtensionManager().load();
                        try {
                            PokeBot.getInstance().getPermManager().reload();
                            PokeBot.log(Level.INFO, "Reloaded permissions");
                            if (sender != null) {
                                sender.sendNotice("Reloaded permissions");
                            }
                        } catch (IOException e) {
                            PokeBot.log(Level.SEVERE, "Error on reloading permissions", e);
                            if (sender != null) {
                                sender.sendNotice("Reloading permissions encountered an error: " + e.getMessage());
                            }
                        }
                        PokeBot.log(Level.INFO, "Reloaded");
                        if (sender != null) {
                            sender.sendNotice("Reloaded");
                        }
                    } else if (evt.getCommand().equalsIgnoreCase("permreload")) {
                        User sender = evt.getUser();
                        if (sender != null) {
                            if (!sender.hasPermission((String) null, "bot.permreload")) {
                                continue;
                            }
                        }
                        PokeBot.log(Level.INFO, "Performing a permission reload, please hold");
                        if (sender != null) {
                            sender.sendNotice("Reloading permissions");
                        }
                        try {
                            PokeBot.getInstance().getPermManager().reload();
                            PokeBot.log(Level.INFO, "Reloaded permissions");
                            if (sender != null) {
                                sender.sendNotice("Reloaded permissions");
                            }
                        } catch (IOException e) {
                            PokeBot.log(Level.SEVERE, "Error on reloading permissions", e);
                            if (sender != null) {
                                sender.sendNotice("Reloading permissions encountered an error: " + e.getMessage());
                            }
                        }

                    } else if (evt.getCommand().equalsIgnoreCase("permcache")) {
                        User sender = evt.getUser();
                        if (sender != null) {
                            if (!sender.hasPermission((String) null, "bot.permcache")) {
                                continue;
                            }
                        }
                        if (evt.getArgs().length == 0) {
                            continue;
                        }
                        for (String arg : evt.getArgs()) {
                            PokeBot.log("Forcing cache update on " + arg);
                            PermissionEvent p = new PermissionEvent(arg);
                            PokeBot.getInstance().getPermManager().runPermissionEvent(p);
                        }
                    } else {
                        for (CommandExecutor exec : commandExecutors) {
                            if (Arrays.asList(exec.getAliases()).contains(evt.getCommand())) {
                                execServ.submit(new CommandCallable(exec, evt));
                                break;
                            }
                        }
                    }
                } else {
                    Set<EventExecutorService> executors = eventExecutors.get(next.getClass());
                    if (executors == null) {
                        continue;
                    }
                    for (Priority prio : Priority.values()) {
                        for (EventExecutorService exec : executors) {
                            if (exec.getPriority() == prio) {
                                try {
                                    if (next instanceof CancellableEvent) {
                                        CancellableEvent evt = (CancellableEvent) next;
                                        if (evt.isCancelled() && !exec.ignoreCancelled()) {
                                            continue;
                                        }
                                    }
                                    exec.getMethod().invoke(exec.getListener(), next);
                                } catch (Exception e) {
                                    PokeBot.log(Level.SEVERE, "Error on handling " + next.getClass().getName() + " in " + exec.getListener().getClass().getName(), e);
                                }
                            }
                        }
                    }
                }
            }
            PokeBot.log(Level.INFO, "Ending event listener");
        }

        public void ping() {
            try {
                synchronized (this) {
                    if (this.isAlive()) {
                        this.notifyAll();
                    } else {
                    }
                }
            } catch (IllegalMonitorStateException e) {
                PokeBot.log(Level.SEVERE, "Major issue on pinging event system", e);
            }
        }
    }

    private class CommandCallable implements Callable {

        private final CommandExecutor listener;
        private final CommandEvent event;

        public CommandCallable(CommandExecutor list, CommandEvent evt) {
            listener = list;
            event = evt;
        }

        @Override
        public Object call() throws Exception {
            listener.runEvent(event);
            return event;
        }
    }

    private class CommandPrefix {

        private final String prefix;
        private final String owner;

        public CommandPrefix(String p, String o) {
            prefix = p;
            owner = o;
        }

        public CommandPrefix(String p) {
            this(p, null);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getOwner() {
            return owner;
        }
    }

    private class EventExecutorService {

        private final Method method;
        private final Listener listener;
        private final Priority priority;
        private final boolean ignoreCancelled;

        public EventExecutorService(Listener l, Method m, Priority p) {
            this(l, m, p, false);
        }

        public EventExecutorService(Listener l, Method m, Priority p, boolean ignore) {
            method = m;
            listener = l;
            priority = p;
            ignoreCancelled = ignore;
        }

        public Listener getListener() {
            return listener;
        }

        public Priority getPriority() {
            return priority;
        }

        public Method getMethod() {
            return method;
        }

        public boolean ignoreCancelled() {
            return ignoreCancelled;
        }
    }
}
