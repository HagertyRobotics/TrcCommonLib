/*
 * Copyright (c) 2019 Titan Robotics Club (http://www.titanrobotics.com)
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

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;

import java.util.Locale;
import java.util.Objects;

/**
 * This class implements a 2D pose object that represents the positional state of an object.
 */
public class TrcPose2D
{
    private static final String moduleName = "TrcPose2D";
    private static final boolean debugEnabled = false;
    private static final boolean tracingEnabled = false;
    private static final boolean useGlobalTracer = false;
    private static final TrcDbgTrace.TraceLevel traceLevel = TrcDbgTrace.TraceLevel.API;
    private static final TrcDbgTrace.MsgLevel msgLevel = TrcDbgTrace.MsgLevel.INFO;
    private TrcDbgTrace dbgTrace = null;

    public double x;
    public double y;
    public double angle;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param x     specifies the x component of the position.
     * @param y     specifies the y component of the position.
     * @param angle specifies the angle.
     */
    public TrcPose2D(double x, double y, double angle)
    {
        if (debugEnabled)
        {
            dbgTrace = useGlobalTracer ?
                TrcDbgTrace.getGlobalTracer() :
                new TrcDbgTrace(moduleName, tracingEnabled, traceLevel, msgLevel);
        }

        this.x = x;
        this.y = y;
        this.angle = angle;
    }   //TrcPose2D

    /**
     * Constructor: Create an instance of the object.
     *
     * @param x specifies the x coordinate of the position.
     * @param y specifies the y coordinate of the position.
     */
    public TrcPose2D(double x, double y)
    {
        this(x, y, 0.0);
    }   //TrcPose2D

    /**
     * Constructor: Create an instance of the object.
     */
    public TrcPose2D()
    {
        this(0.0, 0.0, 0.0);
    }   //TrcPose2D

    /**
     * This method returns the string representation of the pose.
     *
     * @return string representation of the pose.
     */
    @Override
    public String toString()
    {
        return String.format(Locale.US, "(x=%.1f,y=%.1f,angle=%.1f)", x, y, angle);
    }   //toString

    /**
     * This method compares this pose with the specified pose for equality.
     *
     * @return true if equal, false otherwise.
     */
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TrcPose2D pose2D = (TrcPose2D) o;
        return Double.compare(pose2D.x, x) == 0 && Double.compare(pose2D.y, y) == 0 &&
               Double.compare(pose2D.angle, angle) == 0;
    }   //equals

    /**
     * This method returns the hash code of the values in this pose.
     *
     * @return pose hash code.
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(x, y, angle);
    }   //hashCode

    /**
     * This method creates and returns a copy of this pose.
     *
     * @return a copy of this pose.
     */
    public TrcPose2D clone()
    {
        return new TrcPose2D(this.x, this.y, this.angle);
    }   //clone

    /**
     * This method returns the vector form of this pose.
     *
     * @return vector form of this pose.
     */
    public RealVector toPosVector()
    {
        return TrcUtil.createVector(x, y);
    }   //toPoseVector

    /**
     * This method returns the distance of the specified pose to this pose.
     *
     * @param pose specifies the pose to calculate the distance to.
     *
     * @return distance to specified pose.
     */
    public double distanceTo(TrcPose2D pose)
    {
        return toPosVector().getDistance(pose.toPosVector());
    }   //distanceTo

    /**
     * This method sets this pose to be the same as the given pose.
     *
     * @param pose specifies the pose to make this pose equal to.
     */
    public void setAs(TrcPose2D pose)
    {
        this.x = pose.x;
        this.y = pose.y;
        this.angle = pose.angle;
    }   //setAs

    /**
     * This method returns a transformed pose relative to the given pose.
     *
     * @param pose           specifies the reference pose.
     * @param transformAngle specifies true to also transform angle, false to leave it alone.
     * @return pose relative to the given pose.
     */
    public TrcPose2D relativeTo(TrcPose2D pose, boolean transformAngle)
    {
        double deltaX = x - pose.x;
        double deltaY = y - pose.y;
        RealVector newPos =
            TrcUtil.rotateCCW(MatrixUtils.createRealVector(new double[] { deltaX, deltaY }), pose.angle);

        return new TrcPose2D(newPos.getEntry(0), newPos.getEntry(1), transformAngle ? angle - pose.angle : angle);
    }   //relativeTo

    /**
     * This method returns a transformed pose relative to the given pose.
     *
     * @param pose specifies the reference pose.
     * @return pose relative to the given pose.
     */
    public TrcPose2D relativeTo(TrcPose2D pose)
    {
        return relativeTo(pose, true);
    }   //relativeTo

    /**
     * This method translates this pose with the x and y offset in reference to the angle of the pose.
     *
     * @param xOffset specifies the x offset in reference to the angle of the pose.
     * @param yOffset specifies the y offset in reference to the angle of the pose.
     * @return translated pose.
     */
    public TrcPose2D translatePose(double xOffset, double yOffset)
    {
        final String funcName = "translatePose";
        TrcPose2D newPose = clone();
        double angleRadians = Math.toRadians(newPose.angle);
        double cosAngle = Math.cos(angleRadians);
        double sinAngle = Math.sin(angleRadians);

        newPose.x += xOffset * cosAngle + yOffset * sinAngle;
        newPose.y += -xOffset * sinAngle + yOffset * cosAngle;

        if (debugEnabled)
        {
            dbgTrace.traceInfo(funcName, "xOffset=%.1f, yOffset=%.1f, Pose:%s, newPose:%s",
                xOffset, yOffset, this, newPose);
        }

        return newPose;
    }   //translatePose

    /**
     * This method adds a relative pose to the this pose and return the resulting pose. The relative pose has a
     * relative vector and relative angle from this pose.
     *
     * @param relativePose specifies the pose relative to the previous pose.
     * @return resulting pose.
     */
    public TrcPose2D addRelativePose(TrcPose2D relativePose)
    {
        RealVector vec = TrcUtil.createVector(this.x, this.y).add(
                TrcUtil.rotateCW(relativePose.toPosVector(), this.angle));
        return new TrcPose2D(vec.getEntry(0), vec.getEntry(1), this.angle + relativePose.angle);
    }   //addRelativePose

}   //class TrcPose2D
