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

package eu.carrade.amaury.UHCReloaded.timers;

import eu.carrade.amaury.UHCReloaded.events.TimerEndsEvent;
import eu.carrade.amaury.UHCReloaded.events.TimerStartsEvent;
import fr.zcraft.zlib.components.i18n.I;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.UUID;


/**
 * Represents a timer.
 *
 * @author Amaury Carrade
 */
public class UHTimer
{
    private static final NumberFormat formatter = new DecimalFormat("00");

    private UUID id;
    private String name;
    private Boolean registered = false;
    private Boolean running = false;
    private Boolean displayed = false;

    private Long startTime = 0L;
    private Integer duration = 0; // seconds

    // Cached values
    private Integer hoursLeft = 0;
    private Integer minutesLeft = 0;
    private Integer secondsLeft = 0;

    // Old values, used by the scoreboard to reset the scores.
    private Integer oldHoursLeft = -1;
    private Integer oldMinutesLeft = -1;
    private Integer oldSecondsLeft = -1;

    // Pause
    private Boolean paused = false;
    private Long pauseTime = 0L;

    // Display this timer following the format "hh:mm:ss"?
    private Boolean displayHoursInTimer = false;


    public UHTimer(String name)
    {
        Validate.notNull(name, "The name cannot be null");

        this.id = UUID.randomUUID(); // only used as a hashCode.
        this.name = name;
    }

    /**
     * Sets the duration of the timer, in seconds.
     *
     * @param seconds The duration.
     */
    public void setDuration(int seconds)
    {
        this.duration = seconds;

        this.hoursLeft = (int) Math.floor(this.duration / 3600);
        this.minutesLeft = (int) (Math.floor(this.duration / 60) - (this.hoursLeft * 60));
        this.secondsLeft = this.duration - (this.minutesLeft * 60 + this.hoursLeft * 3600);

        this.displayHoursInTimer = (this.hoursLeft != 0);
    }

    /**
     * Starts this timer.
     *
     * If this is called while the timer is running, the timer is restarted.
     */
    public void start()
    {
        this.running = true;
        this.startTime = System.currentTimeMillis();

        Bukkit.getServer().getPluginManager().callEvent(new TimerStartsEvent(this));
    }

    /**
     * Stops this timer.
     */
    public void stop()
    {
        stop(false);
    }

    /**
     * Stops this timer.
     *
     * @param wasUp If true, the timer was stopped because the timer was up.
     */
    private void stop(boolean wasUp)
    {
        final TimerEndsEvent event = new TimerEndsEvent(this, wasUp);
        Bukkit.getServer().getPluginManager().callEvent(event);

        if (isRegistered())
        {
            if (event.getRestart())
            {
                start();
            }
            else
            {
                this.running = false;
                this.startTime = 0L;

                this.hoursLeft = 0;
                this.minutesLeft = 0;
                this.secondsLeft = 0;

                this.oldHoursLeft = 0;
                this.oldMinutesLeft = 0;
                this.oldSecondsLeft = 0;
            }
        }
    }

    /**
     * Updates the timer.
     */
    public void update()
    {
        if (running && !paused)
        {
            oldHoursLeft = hoursLeft;
            oldMinutesLeft = minutesLeft;
            oldSecondsLeft = secondsLeft;

            long timeSinceStart = System.currentTimeMillis() - this.startTime; // ms

            if (timeSinceStart >= getDuration() * 1000)
            {
                stop(true);
            }
            else
            {
                int countSecondsLeft = (int) (getDuration() - Math.floor(timeSinceStart / 1000));

                secondsLeft = countSecondsLeft % 60;
                minutesLeft = (countSecondsLeft % 3600) / 60;
                hoursLeft = (int) Math.floor(countSecondsLeft / 3600);
            }
        }
    }

    /**
     * Pauses (or restarts after a pause) the timer.
     * <p>
     * If the timer is not running, nothing is done.
     *
     * @param pause If true the timer will be paused.
     */
    public void setPaused(boolean pause)
    {
        if (this.running)
        {
            // The pause is only set once (as example if the user executes /uh freeze all twice).
            if (pause && !this.paused)
            {
                this.paused = true;
                this.pauseTime = System.currentTimeMillis();
            }

            if (!pause && this.paused)
            {
                // We have to add to the time of the start of the episode the elapsed time
                // during the pause.
                this.startTime += (System.currentTimeMillis() - this.pauseTime);
                this.pauseTime = 0l;

                this.paused = false;
            }
        }
    }

    /**
     * Checks if the timer is registered in the TimerManager.
     *
     * @return true if the timer is registered.
     */
    public Boolean isRegistered()
    {
        return registered;
    }

    /**
     * Marks a timer as registered, or not.
     *
     * @param registered true if the timer is now registered.
     */
    protected void setRegistered(Boolean registered)
    {
        this.registered = registered;
    }

    /**
     * Returns the name of the timer.
     *
     * @return The name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the display name of the timer.
     * <p>
     * The display name is the name with all &-based color codes replaced by §-based ones.
     *
     * @return The name.
     */
    public String getDisplayName()
    {
        return ChatColor.translateAlternateColorCodes('&', name);
    }


    /**
     * Checks if the timer is currently running.
     *
     * @return true if the timer is running.
     */
    public Boolean isRunning()
    {
        return running;
    }

    /**
     * Checks if the timer is currently displayed in the scoreboard.
     *
     * @return {@code true} if displayed.
     */
    public Boolean isDisplayed()
    {
        return displayed;
    }

    /**
     * Display or hide this timer in/from the scoreboard.
     *
     * @param displayed {@code true} to display, and {@code false} to hide.
     */
    public void setDisplayed(Boolean displayed)
    {
        this.displayed = displayed;
    }

    /**
     * Returns the duration of the timer, in seconds.
     *
     * @return The duration.
     */
    public Integer getDuration()
    {
        return duration;
    }

    /**
     * Returns the number of hours left until the end of this countdown.
     *
     * @return The number of hours left.
     */
    public Integer getHoursLeft()
    {
        return hoursLeft;
    }

    /**
     * Returns the number of minutes left until the end of this countdown.
     *
     * @return The number of minutes left.
     */
    public Integer getMinutesLeft()
    {
        return minutesLeft;
    }

    /**
     * Returns the number of seconds left until the end of this countdown.
     *
     * @return The number of seconds left.
     */
    public Integer getSecondsLeft()
    {
        return secondsLeft;
    }

    /**
     * Returns the number of hours left until the end of this countdown, before the last update.
     * <p>
     * Used by the scoreboard, to remove the old score.
     *
     * @return The old number of hours left, or -1 if the timer was never updated.
     */
    public Integer getOldHoursLeft()
    {
        return oldHoursLeft;
    }

    /**
     * Returns the number of minutes left until the end of this countdown, before the last update.
     * <p>
     * Used by the scoreboard, to remove the old score.
     *
     * @return The old number of minutes left, or -1 if the timer was never updated.
     */
    public Integer getOldMinutesLeft()
    {
        return oldMinutesLeft;
    }

    /**
     * Returns the number of seconds left until the end of this countdown, before the last update.
     * <p>
     * Used by the scoreboard, to remove the old score.
     *
     * @return The old number of seconds left, or -1 if the timer was never updated.
     */
    public Integer getOldSecondsLeft()
    {
        return oldSecondsLeft;
    }

    /**
     * Checks if this timer is paused.
     *
     * @return true if the timer is paused.
     */
    public Boolean isPaused()
    {
        return paused;
    }

    /**
     * Returns true if this timer is displayed as "hh:mm:ss" in the scoreboard.
     *
     * @return true if this timer is displayed as "hh:mm:ss" in the scoreboard.
     */
    public Boolean getDisplayHoursInTimer()
    {
        return displayHoursInTimer;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof UHTimer && ((UHTimer) other).getName().equals(this.getName());
    }

    @Override
    public String toString()
    {
        return toString(displayHoursInTimer);
    }

    public String toString(boolean displayHours)
    {
        if (displayHours)
        {
            /// Timer. {0} = hours; {1} = minutes; {2} = seconds.
            return I.t("{0}{gray}:{white}{1}{gray}:{white}{2}", formatter.format(hoursLeft), formatter.format(minutesLeft), formatter.format(secondsLeft));
        }
        else
        {
            /// Timer. {0} = minutes; {1} = seconds.
            return I.t("{white}{0}{gray}:{white}{1}", formatter.format(minutesLeft), formatter.format(secondsLeft));
        }
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }
}
