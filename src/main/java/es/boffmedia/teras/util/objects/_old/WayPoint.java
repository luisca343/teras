package es.boffmedia.teras.util.objects._old;

import journeymap.client.waypoint.Waypoint;
import net.minecraft.util.math.BlockPos;

import java.awt.*;

public class WayPoint {
    int x;
    int y;
    int z;
    String color;
    String name;

    public WayPoint(int x, int y, int z, String world, String color, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.name = name;
    }

    public WayPoint(double x, double y, double z, String world, String color, String name) {
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
        this.color = color;
        this.name = name;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BlockPos getBlockPos() {
        return new BlockPos(x, y, z);
    }

    public Waypoint getWaypoint(String world) {
        return new Waypoint(name, getBlockPos(), Color.decode(color), Waypoint.Type.Normal, world, false);
    }

    public void setX(double x) {
        this.x = (int) x;
    }

    public void setY(double y) {
        this.y = (int) y;
    }

    public void setZ(double z) {
        this.z = (int) z;
    }
}
