package ohkt.mission;

import java.util.ArrayList;
import java.util.List;

/** Objetivo de missão com dados por tipo. */
public final class Objective {

    public enum Type {
        GOTO, DRIVE_TO, ENTER_VEHICLE, KILL_TAG, DESTROY_VEHICLE_TAG, ESCAPE_POLICE,
        SURVIVE_TIME, PROTECT_ALLY, RACE, DELIVER, CUTSCENE, LOSE_HEALTH_CHECK
    }

    public final Type type;
    public String text = "";
    public float x, z, r = 4f;
    public String tag = "";
    public int count;
    public float time;
    public float timer; // uso interno (ondas etc)
    public boolean failIfBusted = true;
    public List<float[]> checkpoints; // corridas
    public float checkpointRadius = 7f;

    private Objective(Type type) { this.type = type; }

    public static Objective gotoObjective(String text, float x, float z) {
        Objective o = new Objective(Type.GOTO);
        o.text = text;
        o.x = x;
        o.z = z;
        return o;
    }

    public static Objective driveTo(String text, float x, float z) {
        Objective o = new Objective(Type.DRIVE_TO);
        o.text = text;
        o.x = x;
        o.z = z;
        o.r = 6f;
        return o;
    }

    public static Objective enterVehicle(String text, String vehicleTag) {
        Objective o = new Objective(Type.ENTER_VEHICLE);
        o.text = text;
        o.tag = vehicleTag;
        return o;
    }

    public static Objective killTag(String text, String tag, int count) {
        Objective o = new Objective(Type.KILL_TAG);
        o.text = text;
        o.tag = tag;
        o.count = count;
        return o;
    }

    public static Objective destroyVehicle(String text, String tag) {
        Objective o = new Objective(Type.DESTROY_VEHICLE_TAG);
        o.text = text;
        o.tag = tag;
        return o;
    }

    public static Objective escapePolice(String text) {
        Objective o = new Objective(Type.ESCAPE_POLICE);
        o.text = text;
        return o;
    }

    public static Objective survive(String text, float seconds) {
        Objective o = new Objective(Type.SURVIVE_TIME);
        o.text = text;
        o.time = seconds;
        return o;
    }

    public static Objective protectAlly(String text, float seconds, String tag) {
        Objective o = new Objective(Type.PROTECT_ALLY);
        o.text = text;
        o.time = seconds;
        o.tag = tag;
        return o;
    }

    public static Objective race(String text, List<float[]> checkpoints) {
        Objective o = new Objective(Type.RACE);
        o.text = text;
        o.checkpoints = checkpoints;
        o.x = checkpoints.get(0)[0];
        o.z = checkpoints.get(0)[1];
        return o;
    }

    public static Objective deliver(String text, float x, float z) {
        Objective o = new Objective(Type.DELIVER);
        o.text = text;
        o.x = x;
        o.z = z;
        return o;
    }

    public static Objective cutscene(String text) {
        Objective o = new Objective(Type.CUTSCENE);
        o.text = text;
        return o;
    }
}
