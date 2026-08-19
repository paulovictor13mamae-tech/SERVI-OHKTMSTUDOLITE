package ohkt.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Barramento de eventos desacoplado entre sistemas
 * (combate -> policia, missoes -> HUD, veiculos -> audio, etc).
 */
public final class EventBus {

    public enum Type {
        GUNSHOT, EXPLOSION, PED_KILLED, COP_KILLED, CRIME,
        WANTED_CHANGED, PLAYER_DIED, PLAYER_BUSTED, PLAYER_RESPAWN,
        VEHICLE_ENTERED, VEHICLE_EXITED, VEHICLE_DESTROYED, VEHICLE_DAMAGE,
        MISSION_STARTED, MISSION_COMPLETED, MISSION_FAILED, OBJECTIVE_CHANGED,
        MONEY_CHANGED, NOTIFICATION, DAY_TICK, NPC_CALLS_POLICE
    }

    public interface Listener {
        void onEvent(Event event);
    }

    public static final class Event {
        public final Type type;
        public final Object[] data;

        Event(Type type, Object[] data) {
            this.type = type;
            this.data = data;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(int i) {
            return (T) data[i];
        }
    }

    private final Map<Type, List<Listener>> listeners = new EnumMap<>(Type.class);

    public void subscribe(Type type, Listener l) {
        listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(l);
    }

    public void post(Type type, Object... data) {
        List<Listener> ls = listeners.get(type);
        if (ls == null) return;
        Event e = new Event(type, data);
        for (int i = 0; i < ls.size(); i++) {
            ls.get(i).onEvent(e);
        }
    }
}
