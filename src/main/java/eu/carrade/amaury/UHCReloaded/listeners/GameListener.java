/*
 * Copyright or © or Copr. Amaury Carrade (2014 - 2016)
 *
 * http://amaury.carrade.eu
 *
 * This software is governed by the CeCILL-B license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL-B
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-B license and that you accept its terms.
 */

package eu.carrade.amaury.UHCReloaded.listeners;

import eu.carrade.amaury.UHCReloaded.UHCReloaded;
import eu.carrade.amaury.UHCReloaded.UHConfig;
import eu.carrade.amaury.UHCReloaded.events.EpisodeChangedCause;
import eu.carrade.amaury.UHCReloaded.events.TimerEndsEvent;
import eu.carrade.amaury.UHCReloaded.events.TimerStartsEvent;
import eu.carrade.amaury.UHCReloaded.events.UHEpisodeChangedEvent;
import eu.carrade.amaury.UHCReloaded.events.UHGameEndsEvent;
import eu.carrade.amaury.UHCReloaded.events.UHGameStartsEvent;
import eu.carrade.amaury.UHCReloaded.events.UHPlayerDeathEvent;
import eu.carrade.amaury.UHCReloaded.events.UHPlayerResurrectedEvent;
import eu.carrade.amaury.UHCReloaded.events.UHTeamDeathEvent;
import eu.carrade.amaury.UHCReloaded.misc.RuntimeCommandsExecutor;
import eu.carrade.amaury.UHCReloaded.protips.ProTips;
import eu.carrade.amaury.UHCReloaded.teams.UHTeam;
import eu.carrade.amaury.UHCReloaded.utils.UHSound;
import fr.zcraft.zlib.components.i18n.I;
import fr.zcraft.zlib.components.rawtext.RawText;
import fr.zcraft.zlib.tools.runners.RunTask;
import fr.zcraft.zlib.tools.text.RawMessage;
import fr.zcraft.zlib.tools.text.Titles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class GameListener implements Listener
{
    private final UHCReloaded p;

    private final Set<UUID> enableSpectatorModeOnRespawn = new HashSet<>();


    public GameListener()
    {
        this.p = UHCReloaded.get();
    }


    /**
     * Used to:
     *  - call events (UHPlayerDeathEvent, UHTeamDeathEvent, UHGameEndsEvent);
     *  - play the death sound;
     *  - update the scoreboard;
     *  - kick the player (if needed);
     *  - broadcast a team-death message (if needed);
     *  - highlight the death message in the console;
     *  - increase visibility of the death message (if needed);
     *  - drop the skull of the dead player (if needed);
     *  - send a ProTip to the killer about the "golden heads" (if needed);
     *  - update the number of alive players/teams;
     *  - save the location of the death of the player, to allow a teleportation later;
     *  - show the death location on the dynmap (if needed);
     *  - give XP to the killer (if needed);
     *  - notify the player about the possibility of respawn if hardcore hearts are enabled;
     *  - update the MOTD if needed;
     *  - disable the team-chat-lock if needed.
     */
    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent ev)
    {
        // This needs to be executed only if the player die as a player, not a spectator.
        // Also, the game needs to be started.
        if (p.getGameManager().isPlayerDead(ev.getEntity()) || !p.getGameManager().isGameStarted())
        {
            return;
        }

        p.getServer().getPluginManager().callEvent(new UHPlayerDeathEvent(ev.getEntity(), ev));

        // Plays sound.
        p.getGameManager().getDeathSound().broadcast();

        // Send lightning strike if needed.
        if (UHConfig.DEATH.ANNOUNCEMENTS.LIGHTNING_STRIKE.get())
        {
            ev.getEntity().getLocation().getWorld().strikeLightningEffect(ev.getEntity().getLocation());
        }

        // Removes the player from the alive players.
        this.p.getGameManager().addDead(ev.getEntity());

        // Remember to enable spectator mode on respawn
        enableSpectatorModeOnRespawn.add(ev.getEntity().getUniqueId());

        // Kicks the player if needed.
        if (UHConfig.DEATH.KICK.DO.get())
        {
            RunTask.later(() ->
            {
                /// The kick message of a player when death.kick.do = true in config
                ev.getEntity().kickPlayer(I.t("jayjay"));
            }, 20L * UHConfig.DEATH.KICK.TIME.get());
        }

        // Drops the skull of the player.
        if (UHConfig.DEATH.HEAD.DROP.get())
        {
            if (!UHConfig.DEATH.HEAD.PVP_ONLY.get() || (UHConfig.DEATH.HEAD.PVP_ONLY.get() && ev.getEntity().getKiller() != null))
            {
                Location l = ev.getEntity().getLocation();
                ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.PLAYER.ordinal());
                SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                skullMeta.setOwner(ev.getEntity().getName());
                skullMeta.setDisplayName(ChatColor.RESET + ev.getEntity().getDisplayName());
                skull.setItemMeta(skullMeta);
                l.getWorld().dropItem(l, skull);

                // Protip
                if (ev.getEntity().getKiller() != null)
                {
                    final Player killer = ev.getEntity().getKiller();
                    RunTask.later(() -> ProTips.CRAFT_GOLDEN_HEAD.sendTo(killer), 200L);
                }
            }
        }

        // Give XP to the killer (if needed)
        if (UHConfig.DEATH.GIVE_XP_TO_KILLER.LEVELS.get() > 0)
        {
            Player killer = ev.getEntity().getKiller();
            if (killer != null)
            {
                boolean inSameTeam = p.getTeamManager().inSameTeam(ev.getEntity(), killer);
                boolean onlyOtherTeam = UHConfig.DEATH.GIVE_XP_TO_KILLER.ONLY_OTHER_TEAM.get();

                if (!onlyOtherTeam || !inSameTeam)
                {
                    killer.giveExpLevels(UHConfig.DEATH.GIVE_XP_TO_KILLER.LEVELS.get());
                }
            }
        }

        // Sends a team-death message & event if needed.
        final UHTeam team = p.getTeamManager().getTeamForPlayer(ev.getEntity());
        if (team != null)
        {
            boolean isAliveTeam = false;

            for (UUID playerID : team.getPlayersUUID())
            {
                if (!p.getGameManager().isPlayerDead(playerID))
                {
                    isAliveTeam = true;
                    break;
                }
            }

            if (!isAliveTeam)
            {
                p.getServer().getPluginManager().callEvent(new UHTeamDeathEvent(team));

                if (UHConfig.DEATH.MESSAGES.NOTIFY_IF_TEAM_HAS_FALLEN.get())
                {
                    // Used to display this message after the death message.
                    RunTask.later(() -> {
                        String format = ChatColor.translateAlternateColorCodes('&', UHConfig.DEATH.MESSAGES.TEAM_DEATH_MESSAGES_FORMAT.get());
                        p.getServer().broadcastMessage(I.t("{0}The team {1} has fallen!", format, team.getDisplayName() + format));
                    }, 1L);
                }
            }
        }

        // Highlights the death message in the console
        p.getServer().getConsoleSender().sendMessage(ChatColor.GOLD + "-- Death of " + ev.getEntity().getDisplayName() + ChatColor.GOLD + " (" + ev.getDeathMessage() + ") --");

        // Customizes the death message
        String dmFormat = ChatColor.translateAlternateColorCodes('&', UHConfig.DEATH.MESSAGES.DEATH_MESSAGES_FORMAT.get());
        String deathMessage = dmFormat + ev.getDeathMessage();
        deathMessage = deathMessage.replace(ev.getEntity().getName(), ev.getEntity().getDisplayName() + dmFormat);
        if (ev.getEntity().getKiller() != null)
        {
            deathMessage = deathMessage.replace(ev.getEntity().getKiller().getName(), ev.getEntity().getKiller().getDisplayName() + dmFormat);
        }
        ev.setDeathMessage(deathMessage);

        // Saves the location of the death
        p.getGameManager().addDeathLocation(ev.getEntity(), ev.getEntity().getLocation());

        // Shows the death location on the dynmap
        p.getDynmapIntegration().showDeathLocation(ev.getEntity());

        // Is the game ended? If so, we need to call an event.
        if (p.getGameManager().isGameRunning() && p.getGameManager().getAliveTeamsCount() == 1)
        {
            p.getGameManager().setGameFinished(true);

            // There's only one team alive, so the winner team is the first one.
            p.getServer().getPluginManager().callEvent(new UHGameEndsEvent(p.getGameManager().getAliveTeams().iterator().next()));
        }

        // Notifies the player about the possibility of respawn if hardcore hearts are enabled
        if (UHConfig.HARDCORE_HEARTS.DISPLAY.get() && p.getProtocolLibIntegrationWrapper().isProtocolLibIntegrationEnabled() && UHConfig.HARDCORE_HEARTS.RESPAWN_MESSAGE.get())
        {
            RunTask.later(() -> {
                /// A message displayed to the players under the death screen, about the respawn possibility even if the death screen says the opposite (in hardcore mode)
                ev.getEntity().sendMessage(I.t("{darkpurple}{obfuscated}----{lightpurple}{italic} YOU CAN RESPAWN{lightpurple}, just click {italic}Respawn {lightpurple}on the next screen."));
            }, 2L);
        }

        // Disables the team-chat-lock if needed
        if (UHConfig.TEAMS_OPTIONS.TEAM_CHAT.DISABLE_LOCK_ON_DEATH.get())
        {
            if (p.getTeamChatManager().isTeamChatEnabled(ev.getEntity()))
            {
                p.getTeamChatManager().toggleChatForPlayer(ev.getEntity());
            }
        }

        // Updates the list headers & footers.
        p.getPlayerListHeaderFooterManager().updateHeadersFooters();
    }


    /**
     * Used to enable the spectator mode when the player respawns.
     */
    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent ev)
    {
        if (enableSpectatorModeOnRespawn.remove(ev.getPlayer().getUniqueId()))
        {
            RunTask.nextTick(() -> p.getSpectatorsManager().setSpectating(ev.getPlayer(), true));
        }
    }


    /**
     * Used to disable all damages if the game is not started.
     *
     * @param ev
     */
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent ev)
    {
        if (ev.getEntity() instanceof Player)
        {
            if (p.getGameManager().isSlowStartInProgress() || (!p.getGameManager().isGameRunning() && !UHConfig.BEFORE_START.ENABLE_PVP.get()) || (p.getGameManager().isGameRunning() && !p.getGameManager().isTakingDamage()))
            {
                ev.setCancelled(true);
            }
        }
    }


    /**
     * Used to prevent the food level from dropping if the game has not started.
     *
     * @param ev
     */
    @EventHandler
    public void onFoodUpdate(final FoodLevelChangeEvent ev)
    {
        if (!p.getGameManager().isGameRunning())
        {
            if (ev.getEntity() instanceof Player)
            {
                ((Player) ev.getEntity()).setFoodLevel(20);
                ((Player) ev.getEntity()).setSaturation(20f);
            }

            ev.setCancelled(true);
        }
    }


    /**
     * Used to display our custom state-based MOTD (if needed).
     */
    @EventHandler
    public void onServerListPing(final ServerListPingEvent ev)
    {
        if (p.getMOTDManager().isEnabled())
        {
            ev.setMotd(p.getMOTDManager().getCurrentMOTD());
        }
    }


    /**
     * Used to prevent the player to login after his death (if needed).
     */
    @EventHandler
    public void onPlayerLogin(final PlayerLoginEvent ev)
    {
        if (p.getGameManager().isGameStarted()
                && p.getGameManager().isPlayerDead(ev.getPlayer())
                && !p.getGameManager().isDeadPlayersToBeResurrected(ev.getPlayer())
                && !p.getGameManager().getStartupSpectators().contains(ev.getPlayer().getName())
                && !UHConfig.DEATH.KICK.ALLOW_RECONNECT.get())
        {

            ev.setResult(Result.KICK_OTHER);
            /// The kick message displayed if a player tries to relog after his death and it's forbidden by the config.
            ev.setKickMessage(I.t("You are dead!"));
        }
    }


    /**
     * Used to:
     *  - change the gamemode of the player, if the game is not running;
     *  - teleport the player to the spawn, if the game is not running;
     *  - update the scoreboard;
     *  - put a new player in spectator mode if the game is started (following the config);
     *  - resurrect a player (if the player was offline).
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerJoin(final PlayerJoinEvent ev)
    {
        if (!this.p.getGameManager().isGameStarted())
        {
            if (!p.getGameManager().isSlowStartInProgress())
            {
                // Initialization of the player (teleportation, life, health objective score...).
                p.getGameManager().initPlayer(ev.getPlayer());

                // Teams selector.
                if (UHConfig.TEAMS_OPTIONS.GUI.AUTO_DISPLAY.get() && p.getTeamManager().getTeams().size() != 0)
                {
                    RunTask.later(() -> {
                        if (p.getTeamManager().getTeamForPlayer(ev.getPlayer()) == null)
                        {
                            p.getTeamManager().displayTeamChooserChatGUI(ev.getPlayer());
                        }
                    }, 20L * UHConfig.TEAMS_OPTIONS.GUI.DELAY.get());
                }

                // Rules
                if (p.getRulesManager().displayOnJoin())
                {
                    RunTask.later(() -> p.getRulesManager().displayRulesTo(ev.getPlayer()), 15 * 20L);
                }
            }
            else
            {
                // Without that the player will be kicked for flying.
                ev.getPlayer().setAllowFlight(true);
                ev.getPlayer().setFlying(true);
            }
        }

        // Mainly useful on the first join.
        p.getScoreboardManager().setScoreboardForPlayer(ev.getPlayer());

        // The headers & footers needs to be (re)sent.
        p.getPlayerListHeaderFooterManager().sendTo(ev.getPlayer());

        // The display name is reset when the player logs off.
        p.getTeamManager().colorizePlayer(ev.getPlayer());

        if (!p.getGameManager().isGameStarted() && ev.getPlayer().hasPermission("uh.*"))
        {
            // A warning to the administrators if ProtocolLib is not present.
            if (!p.getProtocolLibIntegrationWrapper().isProtocolLibIntegrationEnabled())
            {
                final List<String> enabledOptionsWithProtocolLibNeeded = p.getProtocolLibIntegrationWrapper().isProtocolLibNeeded();

                if (enabledOptionsWithProtocolLibNeeded != null)
                {
                    ev.getPlayer().sendMessage(I.t("{darkred}[UHC] {ce}ProtocolLib is needed but not installed!"));
                    ev.getPlayer().sendMessage(I.t("{gray}The following options require the presence of ProtocolLib:"));

                    for (String option : enabledOptionsWithProtocolLibNeeded)
                    {
                        /// An option requiring ProtocolLib, in the “missing PLib” message. {0} = option path.
                        ev.getPlayer().sendMessage(I.tc("protocollib_option", "{darkgray} - {gray}{0}", option));
                    }

                    String pLibDownloadURL;
                    if (p.getServer().getBukkitVersion().contains("1.7"))  // 1.7.9 or 1.7.10
                    {
                        pLibDownloadURL = "http://dev.bukkit.org/bukkit-plugins/protocollib/";
                    }
                    else  // 1.8+
                    {
                        pLibDownloadURL = "http://www.spigotmc.org/resources/protocollib.1997/";
                    }

                    try
                    {
                        RawMessage.send(ev.getPlayer(),
                            new RawText(I.t("{gray}You can download ProtocolLib by clicking here."))
                                .uri(pLibDownloadURL)
                                .hover(ChatColor.GRAY + pLibDownloadURL)
                            .build()
                        );
                    }
                    catch (URISyntaxException e)
                    {
                        e.printStackTrace();

                        /// {0} = ProtocolLib download URL for the current Minecraft version.
                        ev.getPlayer().sendMessage(I.t("{gray}ProtocolLib is available here: {0}", pLibDownloadURL));
                    }
                }
            }
        }

        // If the player needs to be resurrected...
        if (p.getGameManager().isDeadPlayersToBeResurrected(ev.getPlayer()))
        {
            p.getGameManager().resurrectPlayerOnlineTask(ev.getPlayer());
            p.getGameManager().markPlayerAsResurrected(ev.getPlayer());
        }

        // If the player is a new one, the game is started, and the option is set to true...
        if (p.getGameManager().isGameRunning() && UHConfig.SPECTATOR_MODE_WHEN_NEW_PLAYER_JOIN_AFTER_START.get()
                && !p.getGameManager().getAlivePlayers().contains(ev.getPlayer()))
        {
            p.getSpectatorsManager().setSpectating(ev.getPlayer(), true);
        }
    }

    /**
     * Used to disable the achievements before the game.
     */
    @EventHandler
    public void onPlayerAchievementAwarded(final PlayerAchievementAwardedEvent ev)
    {
        if (!p.getGameManager().isGameStarted() && UHConfig.ACHIEVEMENTS.DISABLE_ACHIEVEMENTS_BEFORE_START.get())
        {
            ev.setCancelled(true);
        }
    }

    /**
     * Used to disable the statistics before the game.
     */
    @EventHandler
    public void onPlayerStatisticIncrement(final PlayerStatisticIncrementEvent ev)
    {
        if (!p.getGameManager().isGameStarted() && UHConfig.STATISTICS.DISABLE_STATISTICS_BEFORE_START.get())
        {
            ev.setCancelled(true);
        }
    }


    /**
     * Used to prevent players from breaking blocks if the game is not currently running.
     */
    @EventHandler
    public void onBlockBreakEvent(final BlockBreakEvent ev)
    {
        if (!this.p.getGameManager().isGameStarted() && !ev.getPlayer().hasPermission("uh.build"))
        {
            ev.setCancelled(true);
        }
    }

    /**
     * Used to prevent players from placing blocks if the game is not currently running.
     */
    @EventHandler
    public void onBlockPlaceEvent(final BlockPlaceEvent ev)
    {
        if (!this.p.getGameManager().isGameStarted() && !ev.getPlayer().hasPermission("uh.build"))
        {
            ev.setCancelled(true);
        }
    }


    /**
     * Used to send the chat to the team-chat if this team-chat is enabled.
     */
    // Priority LOWEST to be able to cancel the event before all other plugins
    @EventHandler (priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(final AsyncPlayerChatEvent ev)
    {
        // If the event is asynchronous, the message was sent by a "real" player.
        // Else, the message was sent by a plugin (like our /g command, or another plugin), and
        // the event is ignored.
        if (ev.isAsynchronous())
        {
            if (p.getTeamChatManager().isTeamChatEnabled(ev.getPlayer()))
            {
                ev.setCancelled(true);
                p.getTeamChatManager().sendTeamMessage(ev.getPlayer(), ev.getMessage());
            }
            else if (p.getTeamChatManager().isOtherTeamChatEnabled(ev.getPlayer()))
            {
                ev.setCancelled(true);
                p.getTeamChatManager().sendTeamMessage(ev.getPlayer(), ev.getMessage(), p.getTeamChatManager().getOtherTeamEnabled(ev.getPlayer()));
            }
        }
    }

    /**
     * Used to:
     *  - update the internal list of running timers;
     *  - shift the episode if the main timer is up (and restart this main timer);
     *  - hide an other timer when it is up.
     */
    @EventHandler
    public void onTimerEnds(final TimerEndsEvent ev)
    {
        p.getTimerManager().updateStartedTimersList();

        if (ev.getTimer().equals(p.getTimerManager().getMainTimer()))
        {
            // If this timer is the main one, we shifts an episode.
            p.getGameManager().shiftEpisode();
            ev.setRestart(true);
        }
        else
        {
            ev.getTimer().setDisplayed(false);
        }

        if (ev.getTimer().equals(p.getBorderManager().getWarningTimer()) && ev.wasTimerUp())
        {
            p.getBorderManager().getWarningSender().sendMessage(I.t("{cs}The timer before the new border is up!"));
            p.getBorderManager().sendCheckMessage(p.getBorderManager().getWarningSender(), p.getBorderManager().getWarningSize());
        }
    }

    /**
     * Used to:
     *  - update the internal list of running timers;
     *  - display a timer when it is started.
     */
    @EventHandler
    public void onTimerStarts(final TimerStartsEvent ev)
    {
        p.getTimerManager().updateStartedTimersList();

        if (!ev.getTimer().equals(p.getTimerManager().getMainTimer()))
        {
            ev.getTimer().setDisplayed(true);
        }
    }


    /**
     * Used to broadcast the episode change.
     */
    @EventHandler
    public void onEpisodeChange(final UHEpisodeChangedEvent ev)
    {
        String message;

        if (ev.getCause() == EpisodeChangedCause.SHIFTED)
        {
            message = I.t("{aqua}-------- End of episode {0} [forced by {1}] --------", String.valueOf(ev.getNewEpisode() - 1), ev.getShifter());
        }
        else
        {
            message = I.t("{aqua}-------- End of episode {0} --------", String.valueOf(ev.getNewEpisode() - 1));
        }

        p.getServer().broadcastMessage(message);


        // Broadcasts title
        if (UHConfig.EPISODES.TITLE.get())
        {
            Titles.broadcastTitle(
                    5, 32, 8,
                    /// The title displayed when the episode change. {0} = new episode number; {1} = old.
                    I.t("{darkaqua}Episode {aqua}{0}", ev.getNewEpisode(), ev.getNewEpisode() - 1),
                    ""
            );
        }


        // Updates the list headers & footers.
        p.getPlayerListHeaderFooterManager().updateHeadersFooters();
    }


    /**
     * Used to:
     *  - broadcast the beginning of a game, with sound & message;
     *  - schedule the commands executed after the beginning of the game.
     */
    @EventHandler
    public void onGameStarts(final UHGameStartsEvent ev)
    {
        // Start sound
        new UHSound(UHConfig.START.SOUND).broadcast();

        // Broadcast
        /// Start message broadcasted in chat
        Bukkit.getServer().broadcastMessage(I.t("{green}--- GO ---"));

        // Title
        if (UHConfig.START.DISPLAY_TITLE.get())
        {
            Titles.broadcastTitle(
                    5, 40, 8,
                    /// Title of title displayed when the game starts.
                    I.t("{darkgreen}Let's go!"),
                    /// Subtitle of title displayed when the game starts.
                    I.t("{green}Good luck, and have fun")
            );
        }

        // Commands
        p.getRuntimeCommandsExecutor().registerCommandsInScheduler(RuntimeCommandsExecutor.AFTER_GAME_START);

        // Border shrinking
        p.getBorderManager().scheduleBorderReduction();

        // MOTD
        p.getMOTDManager().updateMOTDDuringGame();

        // List headers & footers.
        p.getPlayerListHeaderFooterManager().updateHeadersFooters();

        // Rules
        if (p.getRulesManager().displayOnStart())
        {
            RunTask.later(() -> p.getRulesManager().broadcastRules(), 15 * 20L);
        }

        // Banners
        if (p.getGameManager().START_GIVE_BANNER || p.getGameManager().START_PLACE_BANNER_HEAD || p.getGameManager().START_PLACE_BANNER_SPAWN)
        {
            RunTask.later(() ->
            {
                for (UHTeam team : p.getTeamManager().getTeams())
                {
                    if (!team.isEmpty())
                    {
                        final ItemStack banner = team.getBanner();
                        for (Player player : team.getOnlinePlayers())
                        {
                            if (p.getGameManager().START_GIVE_BANNER)
                                player.getInventory().setItem(8, banner);

                            if (p.getGameManager().START_PLACE_BANNER_HEAD)
                                player.getInventory().setHelmet(banner);

                            if (p.getGameManager().START_PLACE_BANNER_SPAWN)
                            {
                                final Block place = player.getWorld().getHighestBlockAt(player.getLocation());
                                final Block under = place.getRelative(BlockFace.DOWN);

                                // We don't want a stack of banners
                                if (under.getType() != Material.STANDING_BANNER)
                                {
                                    if (!under.getType().isSolid())
                                        under.setType(Material.WOOD);

                                    place.setType(Material.STANDING_BANNER);

                                    Banner bannerBlock = (Banner) place.getState();
                                    BannerMeta bannerMeta = (BannerMeta) banner.getItemMeta();

                                    bannerBlock.setBaseColor(bannerMeta.getBaseColor());
                                    bannerBlock.setPatterns(bannerMeta.getPatterns());

                                    bannerBlock.update();
                                }
                            }
                        }
                    }
                }
            }, 5L);
        }
    }

    /**
     * Used to:
     *  - broadcast the winner(s) and launch some fireworks if needed, a few seconds later;
     *  - schedule the commands executed after the end of the game.
     */
    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEnd(final UHGameEndsEvent ev)
    {
        if (UHConfig.FINISH.AUTO.DO.get())
        {
            RunTask.later(() ->
            {
                try
                {
                    p.getGameManager().finishGame();
                }
                catch (IllegalStateException e)
                {
                    // The game is not finished (...what?).
                    e.printStackTrace();
                }
            }, UHConfig.FINISH.AUTO.TIME_AFTER_LAST_DEATH.get() * 20L);
        }

        // Commands
        p.getRuntimeCommandsExecutor().registerCommandsInScheduler(RuntimeCommandsExecutor.AFTER_GAME_END);

        // Updates the MOTD.
        p.getMOTDManager().updateMOTDAfterGame(ev.getWinnerTeam());
    }


    /**
     * Used to:
     *  - disable the spectator mode;
     *  - hide the death point from the dynmap;
     *  - broadcast this resurrection to all players;
     *  - update the MOTD.
     */
    @EventHandler
    public void onPlayerResurrected(final UHPlayerResurrectedEvent ev)
    {
        // Spectator mode disabled
        p.getSpectatorsManager().setSpectating(ev.getPlayer(), false);

        // Death point removed on the dynmap
        p.getDynmapIntegration().hideDeathLocation(ev.getPlayer());

        // All players are notified
        /// Resurrection notification. {0} = raw resurrected player name.
        p.getServer().broadcastMessage(I.t("{gold}{0} returned from the dead!", ev.getPlayer().getName()));

        // Updates the MOTD.
        p.getMOTDManager().updateMOTDDuringGame();

        // Updates the list headers & footers.
        p.getPlayerListHeaderFooterManager().updateHeadersFooters();
    }
}
