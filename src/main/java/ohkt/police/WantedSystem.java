package ohkt.police;

import ohkt.engine.EventBus;

/**
 * Nível de procurado (estrelas 0-5) com calor acumulado, testemunhas e decaimento.
 */
public final class WantedSystem {

    public int stars;
    public float heat;
    public float lastCrimeT = -99;
    public float noLosTimer;
    public boolean suppressed; // desativado por missão/cutscene

    public void crime(float worldTime, String type, boolean witnessed) {
        if (suppressed) return;
        float amount;
        switch (type) {
            case "TIRO": amount = 7; break;
            case "PED_KILLED": amount = 26; break;
            case "COP_KILLED": amount = 55; break;
            case "ROUBO_VEICULO": amount = 13; break;
            case "ATROPELO": amount = 11; break;
            case "EXPLOSAO": amount = 30; break;
            case "BRIGA": amount = 5; break;
            default: amount = 8; break;
        }
        if (!witnessed) amount *= 0.45f; // sem testemunhas pesa menos
        heat += amount;
        lastCrimeT = worldTime;
        updateStars();
    }

    private void updateStars() {
        int s;
        if (heat >= 210) s = 5;
        else if (heat >= 135) s = 4;
        else if (heat >= 78) s = 3;
        else if (heat >= 34) s = 2;
        else if (heat >= 9) s = 1;
        else s = 0;
        if (s != stars) {
            stars = s;
        }
    }

    /** Decai quando o jogador fica fora da vista da polícia. */
    public void update(float dt, float worldTime, boolean seenByPolice) {
        if (suppressed) return;
        if (seenByPolice) {
            noLosTimer = 0;
        } else {
            noLosTimer += dt;
        }
        if (stars > 0 && worldTime - lastCrimeT > 8f && noLosTimer > 5f) {
            heat -= dt * (stars >= 4 ? 1.6f : 3.2f);
            if (heat < 0) heat = 0;
            updateStars();
        }
    }

    public void clear() {
        stars = 0;
        heat = 0;
        noLosTimer = 0;
    }

    public void post(EventBus bus) {
        bus.post(EventBus.Type.WANTED_CHANGED, stars);
    }
}
