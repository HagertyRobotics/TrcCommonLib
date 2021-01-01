/*
 * Copyright (c) 2015 Titan Robotics Club (http://www.titanrobotics.com)
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

/**
 * This class implements a timer that will signal an event or make a notification callback when the time has expired.
 * This is useful for doing delays in autonomous.
 */
public class TrcTimer
{
    private static final String moduleName = "TrcTimer";
    private static final boolean debugEnabled = false;
    private static final boolean tracingEnabled = false;
    private static final boolean useGlobalTracer = false;
    private static final TrcDbgTrace.TraceLevel traceLevel = TrcDbgTrace.TraceLevel.API;
    private static final TrcDbgTrace.MsgLevel msgLevel = TrcDbgTrace.MsgLevel.INFO;
    private TrcDbgTrace dbgTrace = null;

    private final String instanceName;
    private final TrcTimerMgr timerMgr;
    private long expiredTimeInMsec = 0;
    private boolean expired = false;
    private boolean canceled = false;
    private TrcEvent notifyEvent = null;
    private TrcNotifier.Receiver notifyReceiver = null;
    private double securityKey = -1.0;

    /**
     * Constructor: Creates an instance of the timer with the given name.
     *
     * @param instanceName specifies the name to identify this instance of the timer.
     */
    public TrcTimer(final String instanceName)
    {
        if (debugEnabled)
        {
            dbgTrace = useGlobalTracer?
                TrcDbgTrace.getGlobalTracer():
                new TrcDbgTrace(moduleName + "." + instanceName, tracingEnabled, traceLevel, msgLevel);
        }

        this.instanceName = instanceName;
        timerMgr = TrcTimerMgr.getInstance();
    }   //TrcTimer

    /**
     * This method returns the instance name.
     *
     * @return instance name.
     */
    @Override
    public String toString()
    {
        return instanceName;
    }   //toString

    /**
     * This methods sets the expire time relative to the current time. When the time expires, it will signal the
     * given event if any and call back the notification receiver if any.
     *
     * @param time specifies the expire time in seconds relative to the current time.
     * @param event specifies the event to signal when time has expired.
     * @param receiver specifies the notification receiver to call when time has expired.
     * @throws IllegalStateException when set is called more than once without a call to {@link #cancel()} between them.
     */
    public synchronized void set(double time, TrcEvent event, TrcNotifier.Receiver receiver)
            throws IllegalStateException
    {
        final String funcName = "set";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API, "time=%f,event=%s,receiver=%s",
                    time, event != null? event: "null", receiver != null? receiver: "null");
        }

        if (expiredTimeInMsec > 0)
        {
            throw new IllegalStateException("Set called on a timer that is currently running. " +
                    "Wait for the timer to expire or call cancel first.");
        }

        expiredTimeInMsec = TrcUtil.getCurrentTimeMillis() + (long)(time*1000);
        expired = false;
        canceled = false;
        notifyEvent = event;
        notifyReceiver = receiver;
        if (event != null)
        {
            event.clear();
        }
        //
        // Security key implements a simple mutual authentication mechanism. It is a double value where the decimal
        // part is a random ID generated by the client of TrcTimerMgr (i.e. you, an instance of TrcTimer) and the
        // integer part is a unique ID returned by TrcTimerMgr.add(). Together they formed a mutual authentication
        // ID only known between you (an instance of TrcTimer) and the TrcTimerMgr. If TrcTimerMgr is calling you to
        // alter the state of your timer (e.g. setExpired), you can check the securityKey parameter to make sure the
        // caller is indeed TrcTimerMgr.
        //
        securityKey = timerMgr.add(this, Math.random());

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //set

    /**
     * This methods sets the expire time relative to the current time. When the time expires, it will signal the
     * given event.
     *
     * @param time specifies the expire time in seconds relative to the current time.
     * @param event specifies the event to signal when time has expired.
     */
    public void set(double time, TrcEvent event)
    {
        set(time, event, null);
    }   //set

    /**
     * This methods sets the expire time relative to the current time. When the time expires, it will call back the
     * notification receiver.
     *
     * @param time specifies the expire time in seconds relative to the current time.
     * @param receiver specifies the notification receiver to call when time has expired.
     */
    public void set(double time, TrcNotifier.Receiver receiver)
    {
        set(time, null, receiver);
    }   //set

    /**
     * This method returns the expired timestamp in milliseconds. If the timer is not set, it returns 0.
     *
     * @return expired timestamp in msec.
     */
    public synchronized long getExpiredTimeInMsec()
    {
        return expiredTimeInMsec;
    }   //getExpiredTimeInMsec

    /**
     * This method checks if the timer has expired.
     *
     * @return true if the timer has expired, false otherwise.
     */
    public synchronized boolean isExpired()
    {
        final String funcName = "isExpired";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%s", Boolean.toString(expired));
        }

        return expired;
    }   //isExpired

    /**
     * This method checks if the timer is active (i.e. running and not expired)
     *
     * @return true if the timer is active, false otherwise.
     */
    public synchronized boolean isActive()
    {
        final String funcName = "isActive";
        boolean active = expiredTimeInMsec != 0.0 && !expired;

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%s", active);
        }

        return active;
    }   //isActive

    /**
     * This method is called by TrcTimerMgr when the timer has expired. DO NOT call this if you are not TrcTimerMgr.
     *
     * @param securityKey specifies the security key returned to you by TrcTimerMgr when you added your timer. This is
     *                    a simple authentication measure to make sure the caller is indeed TrcTimeMgr.
     */
    public synchronized void setExpired(double securityKey)
    {
        final String funcName = "setExpired";

        if (securityKey == this.securityKey)
        {
            if (debugEnabled)
            {
                dbgTrace.traceInfo(funcName, "Time expired, notifying %s/%s.",
                        notifyEvent != null? notifyEvent: "null", notifyReceiver != null? notifyReceiver: "null");
            }

            this.expiredTimeInMsec = 0;
            this.expired = true;
            this.securityKey = -1.0;

            if (notifyEvent != null)
            {
                notifyEvent.signal();
                notifyEvent = null;
            }

            if (notifyReceiver != null)
            {
                notifyReceiver.notify(this);
                notifyReceiver = null;
            }
        }
        else
        {
            throw new SecurityException("Only TrcTimerMgr is allowed to call this method.");
        }
    }   //setExpired

    /**
     * This method checks if the timer was canceled.
     *
     * @return true if the timer was canceled, false otherwise.
     */
    public synchronized boolean isCanceled()
    {
        final String funcName = "isCanceled";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%s", Boolean.toString(canceled));
        }

        return canceled;
    }   //isCanceled

    /**
     * This method cancels the timer if it's set but has not expired. If the timer is canceled, the event is signaled.
     */
    public synchronized void cancel()
    {
        final String funcName = "cancel";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
        }

        // Only do this if the timer is actually armed and not expired.
        if (securityKey != -1.0)
        {
            timerMgr.remove(this, securityKey);
            expiredTimeInMsec = 0;
            expired = false;
            canceled = true;
            securityKey = -1.0;

            if (notifyEvent != null)
            {
                notifyEvent.cancel();
                notifyEvent = null;
            }

            if (notifyReceiver != null)
            {
                notifyReceiver.notify(this);
                notifyReceiver = null;
            }
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //cancel

}   //class TrcTimer
