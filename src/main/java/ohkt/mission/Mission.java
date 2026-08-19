package ohkt.mission;

import ohkt.engine.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Missão: sequência de objetivos com diálogos, recompensa, falha e cutscenes.
 * Hooks onEnter/onExit conectam spawn de entidades da campanha.
 */
public final class Mission {

    public final String id;
    public final String name;
    public final String giver;
    public final int reward;
    public final List<Objective> objectives = new ArrayList<>();
    public final CutscenePlayer.Line[] introDialog;
    public String introText = "";

    /** Spawn/setup ao iniciar. */
    public GameConsumer onEnter;
    /** Limpeza ao sair (falha/conclusão). */
    public GameConsumer onExit;
    /** Ao concluir com sucesso. */
    public GameConsumer onComplete;

    // runtime
    public int idx;
    public boolean active, done, failed;
    public String failReason;
    public float raceTimer;
    public int raceCheckpoint;

    @FunctionalInterface
    public interface GameConsumer {
        void accept(Game g);
    }

    public Mission(String id, String name, String giver, int reward, CutscenePlayer.Line[] introDialog) {
        this.id = id;
        this.name = name;
        this.giver = giver;
        this.reward = reward;
        this.introDialog = introDialog;
    }

    public Mission objective(Objective o) {
        objectives.add(o);
        return this;
    }

    public Objective currentObjective() {
        if (idx < objectives.size()) return objectives.get(idx);
        return null;
    }

    public boolean allComplete() { return idx >= objectives.size(); }
}
