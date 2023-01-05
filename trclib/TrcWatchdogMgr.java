/*
 * Copyright (c) 2022 Titan Robotics Club (http://www.titanrobotics.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package TrcCommonLib.trclib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import TrcCommonLib.trclib.TrcTaskMgr.TaskType;

/**
 * This class is a singleton. It manages a list of watchdogs. Watchdogs are typically used for detecting thread
 * deadlocks. A thread can register a watchdog with the Watchdog manager and periodically sends a heartbeat to it.
 * If a heart beat is not sent within the heart beat threshold time, a stack trace for the thread will be dump to the
 * trace log. This allows us to catch thread deadlocks quite easily. Since watchdog is designed per thread, only one
 * watchdog needs to be registered for each thread.
 */
public class TrcWatchdogMgr
{
    public static final String moduleName = "TrcWatchdogMgr";
    private static final TrcDbgTrace globalTracer = TrcDbgTrace.getGlobalTracer();
    private static final boolean debugEnabled = false;

    private static final double DEF_TASK_INTERVAL = 1.0;        // in seconds.
    private static final double DEF_HEARTBEAT_THRESHOLD = 1.0;  // in seconds.

    private static TrcWatchdogMgr instance;
    private static final ArrayList<Watchdog> watchdogList = new ArrayList<>();
    private static final HashMap<String, Watchdog> watchdogMap = new HashMap<>();

    /**
     * This class encapsulates the state of the watchdog. A watchdog has an identifiable name, an associated thread,
     * a maximum time interval between which the heart beat must be received and the next heart beat expiraton time.
     */
    public static class Watchdog
    {
        private final String name;
        private final double heartBeatThreshold;
        private final Thread thread;
        private volatile double heartBeatExpiredTime;
        private volatile boolean expired;
        private volatile boolean paused;

        /**
         * Constructor: Creates an instance of the object.
         *
         * @param name specifies the name of the watchdog.
         * @param heartBeatThreshold specifies the maximum heart beat interval in seconds.
         */
        private Watchdog(String name, double heartBeatThreshold)
        {
            this.name = name;
            this.heartBeatThreshold = heartBeatThreshold;
            this.thread = Thread.currentThread();
            this.heartBeatExpiredTime = TrcTimer.getCurrentTime() + heartBeatThreshold;
            this.expired = false;
            this.paused = false;
        }   //Watchdog

        /**
         * This method is called to pause watchdog monitoring. It is useful for a thread to call this before going
         * into sleep or a wait knowing it won't be able to send periodic heartbeat to prevent a watchdog timeout.
         */
        public synchronized void pauseWatch()
        {
            paused = true;
        }   //pauseWatch

        /**
         * This method is called to resume watchdog monitoring. It is useful for a thread to call this right after
         * waking up from a sleep or a wait so watchdog monitoring will be resumed.
         */
        public synchronized void resumeWatch()
        {
            paused = false;
        }   //resumeWatch

        /**
         * This method is called by the thread that registered the watchdog to send a heart beat. This will update
         * the next heart beat expiration time.
         */
        public void sendHeartBeat()
        {
            final String funcName = "sendHeartBeat";

            if (this.thread == Thread.currentThread())
            {
                double currTime = TrcTimer.getCurrentTime();

                synchronized (this)
                {
                    // Sending a heartbeat will also unpause a paused watchdog.
                    heartBeatExpiredTime = currTime + heartBeatThreshold;
                    expired = false;
                    paused = false;
                }
            }
            else
            {
                globalTracer.traceWarn(
                    funcName, "Only the thread created this watchdog %s is allowed to send heart beat (thread=%s).",
                    name, thread.getName());
                TrcDbgTrace.printThreadStack();
            }
        }   //sendHeartBeat

        /**
         * This method checks if the watchdog has expired.
         *
         * @return true if no heartbeat has been received for the specified threshold period, false otherwise.
         */
        private synchronized boolean checkForExpiration()
        {
            final String funcName = "checkForExpiration";
            double currTime = TrcTimer.getCurrentTime();

            if (!paused && !expired && currTime > heartBeatExpiredTime)
            {
                expired = true;
                globalTracer.traceWarn(funcName, "[%.3f] watchdog %s expired.", currTime, this);
                TrcDbgTrace.printThreadStack(thread);
            }

            return expired;
        }   //checkForExpiration

        /**
         * This method unregisters this watchdog from Watchdog Manager.
         * Important: this method must be called in the thread the watchdog is monitoring. In other words, the
         * caller's thread must be the owner of the watchdog.
         *
         * @return true if watchdog is unregistered successfully, false if watchdog does not exist.
         */
        public boolean unregister()
        {
            final String funcName = "unregister";
            boolean success = false;

            if (this.thread == Thread.currentThread())
            {
                success = unregisterWatchdog(this);
            }
            else
            {
                globalTracer.traceWarn(
                    funcName, "Only the thread created this watchdog %s is allowed to unregister (thread=%s).",
                    name, thread.getName());
                TrcDbgTrace.printThreadStack();
            }

            return success;
        }   //unregsiter

        /**
         * This method returns the string containing the info of the watchdog.
         *
         * @return string form of the watchdog info.
         */
        @Override
        public String toString()
        {
            return String.format(
                Locale.US, "[%.3f] %s: threshold=%.3f, expiredTime=%.3f, expired=%s, paused=%s",
                TrcTimer.getCurrentTime(), name, heartBeatThreshold, heartBeatExpiredTime, expired, paused);
        }   //toString

        /**
         * This method returns the heart beat threshold time for the watchdog.
         *
         * @return watchdog heart beat threshold time in seconds.
         */
        public double getHeartBeatThreshold()
        {
            return heartBeatThreshold;
        }   //getHeartBeatThreshold

    }   //class Watchdog

    /**
     * This method should only be called once. It creates a singleton of the Watchdog Manager. If it is called again,
     * it will just return the Watchdog Manager instance created previously.
     *
     * @param taskInterval specifies the watchdog task interval.
     * @return Watchdog Manager instance.
     */
    public static TrcWatchdogMgr getInstance(double taskInterval)
    {
        if (instance == null)
        {
            instance = new TrcWatchdogMgr(taskInterval);
        }

        return instance;
    }   //getInstance

    /**
     * This method should only be called once. It creates a singleton of the Watchdog Manager. If it is called again,
     * it will just return the Watchdog Manager instance created previously.
     *
     * @return Watchdog Manager instance.
     */
    public static TrcWatchdogMgr getInstance()
    {
        return getInstance(DEF_TASK_INTERVAL);
    }   //getInstance

    /**
     * Constructor: Creates an instance of the object.
     *
     * @param taskInterval specifies the watchdog task interval.
     */
    private TrcWatchdogMgr(double taskInterval)
    {
        TrcTaskMgr.TaskObject watchdogTaskObj = TrcTaskMgr.createTask(moduleName, this::watchdogTask);
        watchdogTaskObj.registerTask(TaskType.STANDALONE_TASK, (long) (taskInterval*1000));
        if (debugEnabled)
        {
            globalTracer.traceInfo(moduleName, "Starting Watchdog Manager.");
        }
    }   //TrcWatchdogMgr

    /**
     * This method registers a new watchdog for the current thread if one is not already registered.
     * Important: this method must be called in the thread the watchdog is monitoring.
     *
     * @param name specifies the name of the watchdog.
     * @param heartBeatThreshold specifies the maximum heart beat interval in seconds.
     * @return newly created watchdog.
     */
    public static Watchdog registerWatchdog(String name, double heartBeatThreshold)
    {
        final String funcName = "registerWatchdog";
        Watchdog watchdog = null;

        instance = getInstance();
        if (debugEnabled)
        {
            globalTracer.traceInfo(
                funcName, "Registering watchdog %s (heartBeatThreshold=%.3f).", name, heartBeatThreshold);
            TrcDbgTrace.printThreadStack();
        }

        synchronized (watchdogList)
        {
            if (!watchdogMap.containsKey(name))
            {
                watchdog = new Watchdog(name, heartBeatThreshold);
                watchdogList.add(watchdog);
                watchdogMap.put(name, watchdog);
            }
            else
            {
                globalTracer.traceWarn(funcName, "Watchdog %s was already registered.", name);
                TrcDbgTrace.printThreadStack();
            }
        }

        return watchdog;
    }   //registerWatchdog

    /**
     * This method registers a new watchdog for the current thread if one is not already registered.
     * Important: this method must be called in the thread the watchdog is monitoring.
     *
     * @param name specifies the name of the watchdog.
     * @return newly created watchdog.
     */
    public static Watchdog registerWatchdog(String name)
    {
        return registerWatchdog(name, DEF_HEARTBEAT_THRESHOLD);
    }   //registerWatchdog

    /**
     * This method removes the watchdog from the watchdog list and map. It can be called from any thread as long as
     * it provides the watchdog to be unregistered.
     *
     * @param watchdog specifies the watchdog to be removed.
     * @return true if watchdog is removed successfully, false if watchdog does not exist.
     */
    public static boolean unregisterWatchdog(Watchdog watchdog)
    {
        final String funcName = "unregisterWatchdog";
        boolean success;

        if (debugEnabled)
        {
            globalTracer.traceInfo(funcName, "Unregistering watchdog %s.", watchdog);
            TrcDbgTrace.printThreadStack();
        }

        synchronized (watchdogList)
        {
            watchdogMap.remove(watchdog.name);
            success = watchdogList.remove(watchdog);
        }

        if (!success)
        {
            globalTracer.traceWarn(funcName, "Watchdog %s was never registered.", watchdog.name);
            TrcDbgTrace.printThreadStack();
        }

        return success;
    }   //unregisterWatchdog

    /**
     * This method runs periodically to check for watchdog timeouts.
     *
     * @param taskType specifies the type of task being run.
     * @param runMode specifies the current robot run mode.
     * @param slowPeriodicLoop specifies true if it is running the slow periodic loop on the main robot thread,
     *        false otherwise.
     */
    private void watchdogTask(TrcTaskMgr.TaskType taskType, TrcRobot.RunMode runMode, boolean slowPeriodicLoop)
    {
        synchronized (watchdogList)
        {
            for (Watchdog watchdog: watchdogList)
            {
                watchdog.checkForExpiration();
            }
        }
    }   //watchdogTask

}   //class TrcWatchdogMgr
