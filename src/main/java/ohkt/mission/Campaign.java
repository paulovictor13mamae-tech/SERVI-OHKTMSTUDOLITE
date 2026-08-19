package ohkt.mission;

import ohkt.engine.Game;
import ohkt.mission.CutscenePlayer.Line;
import ohkt.world.CityLayout;

import java.util.ArrayList;
import java.util.List;

/** Campanha principal "Porto Aurora" — 10 missões conectadas. */
public final class Campaign {

    public static List<Mission> build(Game g) {
        List<Mission> list = new ArrayList<>();
        float[] casa = CityLayout.specialPos(CityLayout.Special.CASA_MAE);
        float[] lanche = CityLayout.specialPos(CityLayout.Special.LANCHONETE);
        float[] oficina = CityLayout.specialPos(CityLayout.Special.OFICINA);
        float[] galpao = CityLayout.specialPos(CityLayout.Special.GALPAO_PORTO);
        float[] delegacia = CityLayout.specialPos(CityLayout.Special.DELEGACIA);
        float[] praca = CityLayout.specialPos(CityLayout.Special.PRACA);
        float[] industrial = CityLayout.specialPos(CityLayout.Special.POSTO_B);
        float[] porto = CityLayout.specialPos(CityLayout.Special.POSTO_D);
        float farolX = CityLayout.ISLAND_X, farolZ = CityLayout.ISLAND_Z;

        // ---------- 1 ----------
        list.add(new Mission("m1", "Terra de Ninguém", "Dona Lurdes", 200, new Line[]{
                new Line("Rádio do ônibus", "Bem-vindo a Porto Aurora. Seis anos e a cidade continua a mesma...", 4),
                new Line("Dante", "Cheguei. Agora só preciso achar a minha mãe... e um emprego.", 4),
                new Line("Carta", "\"Filho, a casa é sua. Cuidado com a dívida com a Dona Lurdes. — Mãe\"", 5),
        })
                .objective(Objective.gotoObjective("Vá até a casa da sua mãe (marcador amarelo)", casa[0], casa[1]))
                .objective(Objective.gotoObjective("Fale com a Dona Lurdes no Forno de Ouro", lanche[0], lanche[1]))
                .objective(Objective.cutscene("Dona Lurdes te dá uma chance.")));
        list.get(0).onEnter = game -> game.hud.notify("Missão: Terra de Ninguém");
        list.get(0).onComplete = game -> {
            game.hud.dialogue("Dona Lurdes", "Seu pai me devodia, moleque. Agora a dívida é sua. Comece arrumando rodas.", 5);
        };

        // ---------- 2 ----------
        list.add(new Mission("m2", "Rodas Novas", "Dona Lurdes", 250, new Line[]{
                new Line("Dona Lurdes", "Preciso de alguém que dirija. Arrume um carro — não me interessa de quem.", 4.5f),
                new Line("Dante", "Nesse cidade, isso é fácil demais.", 3.5f),
        })
                .objective(Objective.enterVehicle("Roube qualquer carro na rua (F para entrar)", "*"))
                .objective(Objective.driveTo("Leve o carro à Oficina do Nino", oficina[0], oficina[1] + 6)));
        list.get(1).onComplete = game -> {
            if (game.player.vehicle != null) {
                game.player.vehicle.paint = 0xffd8b028;
                game.player.vehicle.health = 100;
                game.player.vehicle.persist = true;
            }
            game.hud.dialogue("Nino", "Pintura nova, motor revisado. O carro é seu — presente da Lurdes.", 5);
        };

        // ---------- 3 ----------
        list.add(new Mission("m3", "Entrega da Meia-Noite", "Dona Lurdes", 400, new Line[]{
                new Line("Dona Lurdes", "Este pacote vai pro galpão do cais antes da uma da manhã. Sem perguntas.", 4.5f),
                new Line("Dante", "Sem perguntas, sem respostas. Combinado.", 3.5f),
        })
                .objective(Objective.gotoObjective("Pegue o pacote no Forno de Ouro", lanche[0], lanche[1]))
                .objective(Objective.deliver("Entregue no Galpão do Porto antes de 01:00", galpao[0], galpao[1] + 6))
                .objective(Objective.killTag("Os Corvos cercaram você! Elimine-os", "corvo_m3", 3))
                .objective(Objective.escapePolice("Fuja da polícia")));
        list.get(2).onEnter = game -> {
            game.world.time.hour = 23.2f;
            game.missions.spawnGang(game, "corvo_m3", galpao[0], galpao[1] + 10, 3, 60, true);
        };
        list.get(2).onExit = game -> game.missions.clearTag(game, "corvo_m3");

        // ---------- 4 ----------
        list.add(new Mission("m4", "Fugas e Fumaça", "Dona Lurdes", 350, new Line[]{
                new Line("Dante", "A policia me armou! Alguém dedurou a entrega.", 4),
                new Line("Dona Lurdes", "O Inspetor Braga vende qualquer um. Suma daí e volta pra casa.", 4.5f),
        })
                .objective(Objective.escapePolice("Perca a polícia (2 estrelas)"))
                .objective(Objective.gotoObjective("Volte para casa e descanse", casa[0], casa[1])));
        list.get(3).onEnter = game -> {
            game.police.wantedSystem.crime(game.world.time.worldTime, "PED_KILLED", true);
            game.police.wantedSystem.heat = 60f;
            game.hud.notify("O Inspetor Braga te incriminou!");
        };

        // ---------- 5 ----------
        list.add(new Mission("m5", "Patos no Cais", "Dona Lurdes", 600, new Line[]{
                new Line("Dona Lurdes", "Os Corvos do Cais cobram proteção em tudo que é meu. Quebre eles.", 4.5f),
                new Line("Dante", "Vou precisar de ferro.", 3),
                new Line("Dona Lurdes", "Então pegue ferro na Casa do Ferreiro. Ela abre às 10.", 4),
        })
                .objective(Objective.gotoObjective("Compre a GP-9 na Casa do Ferreiro", CityLayout.specialPos(CityLayout.Special.ARMERIA)[0], CityLayout.specialPos(CityLayout.Special.ARMERIA)[1]))
                .objective(Objective.killTag("Elimine 6 Corvos no cais", "corvo_m5", 6)));
        list.get(4).onEnter = game -> {
            game.missions.spawnGang(game, "corvo_m5", porto[0], porto[1], 6, 70, false);
        };
        list.get(4).onExit = game -> game.missions.clearTag(game, "corvo_m5");
        list.get(4).onComplete = game -> {
            game.player.giveWeapon(2, 60);
            game.hud.dialogue("Dona Lurdes", "Boa. Essa GP-9 é sua, com munição. Os Corvos vão lembrar de você.", 5);
        };

        // ---------- 6 ----------
        list.add(new Mission("m6", "Corrida do Píer", "Nino", 800, new Line[]{
                new Line("Nino", "Os Corvos corrido no píer todo domingo. Ganha deles e a cidade fala seu nome.", 4.5f),
        })
                .objective(Objective.race("Vença a corrida (passe nos checkpoints)", raceTrack())));
        list.get(5).onEnter = game -> game.missions.spawnRaceOpponents(game, raceTrack());
        list.get(5).onExit = game -> game.missions.clearRace(game);

        // ---------- 7 ----------
        list.add(new Mission("m7", "O Informante", "Dona Lurdes", 700, new Line[]{
                new Line("Dona Lurdes", "Tem um cara que quer deixar os Corvos. Busque ele antes que o Braga encontre.", 5),
        })
                .objective(Objective.gotoObjective("Busque o informante na Zona Comercial", CityLayout.specialPos(CityLayout.Special.BRECHO)[0], CityLayout.specialPos(CityLayout.Special.BRECHO)[1]))
                .objective(Objective.protectAlly("Proteja o informante dos Corvos", 55, "informante"))
                .objective(Objective.driveTo("Leve ele à 12ª Delegacia", delegacia[0], delegacia[1])));
        list.get(6).onEnter = game -> {
            float[] p = CityLayout.specialPos(CityLayout.Special.BRECHO);
            game.missions.spawnAlly(game, "informante", p[0], p[1]);
        };
        list.get(6).onExit = game -> {
            game.missions.clearTag(game, "informante");
            game.missions.clearTag(game, "corvo_m7");
        };

        // ---------- 8 ----------
        list.add(new Mission("m8", "Carga Sensível", "Dona Lurdes", 1200, new Line[]{
                new Line("Dona Lurdes", "Tem um caminhão carregado na Vila do Metal. Traga ele pro galpão. Intacto.", 5),
        })
                .objective(Objective.enterVehicle("Roube o caminhão Touro", "caminhao_m8"))
                .objective(Objective.deliver("Leve ao Galpão do Porto", galpao[0], galpao[1] + 10)));
        list.get(7).onEnter = game -> {
            float[] p = CityLayout.specialPos(CityLayout.Special.POSTO_B);
            game.missions.spawnMissionVehicle(game, "caminhao_m8", "TOURO", p[0], p[1] + 10, 0);
        };
        list.get(7).onExit = game -> game.missions.clearVehicleTag(game, "caminhao_m8");

        // ---------- 9 ----------
        list.add(new Mission("m9", "Traição no Alto", "Inspetor Braga", 2000, new Line[]{
                new Line("Inspetor Braga", "Dante Moraes. Achei sua carteira no cais. Que descuido, hein?", 4.5f),
                new Line("Dante", "Você plantou. Eu só tô pagando o que você deixou pra trás.", 4),
                new Line("Inspetor Braga", "A cidade é minha. Os Corvos são meus. E você... vai ser notícia.", 5),
        })
                .objective(Objective.killTag("Sobreviva ao embate com os homens do Braga", "braga_m9", 8))
                .objective(Objective.escapePolice("Fuja da operação do Braga")));
        list.get(8).onEnter = game -> {
            game.missions.spawnGang(game, "braga_m9", praca[0], praca[1], 8, 90, false);
            game.police.wantedSystem.heat = 90f;
        };
        list.get(8).onExit = game -> game.missions.clearTag(game, "braga_m9");

        // ---------- 10 ----------
        list.add(new Mission("m10", "Rei do Porto", "Dona Lurdes", 5000, new Line[]{
                new Line("Dona Lurdes", "Sete-Dedos tá no galpão. Termina isso hoje, e Porto Aurora respira.", 5),
                new Line("Dante", "Termina hoje.", 2.5f),
        })
                .objective(Objective.killTag("Invada o galpão e elimine os Corvos", "corvo_m10", 8))
                .objective(Objective.killTag("Derrote Sete-Dedos", "setededos", 1))
                .objective(Objective.escapePolice("Escape da cidade (perca 5 estrelas)"))
                .objective(Objective.driveTo("Cruze o calçadão até a Ilha do Farol", farolX, farolZ)));
        list.get(9).onEnter = game -> {
            game.missions.spawnGang(game, "corvo_m10", galpao[0], galpao[1], 8, 110, false);
            game.missions.spawnBoss(game, "setededos", galpao[0] + 6, galpao[1] + 6);
            game.police.wantedSystem.heat = 200f;
        };
        list.get(9).onExit = game -> {
            game.missions.clearTag(game, "corvo_m10");
            game.missions.clearTag(game, "setededos");
        };
        list.get(9).onComplete = game -> {
            game.hud.dialogue("Dante", "Porto Aurora é nossa agora, Dona Lurdes. Dívida paga.", 6);
            game.stats.set("finalCompletado", 1);
        };

        return list;
    }

    private static List<float[]> raceTrack() {
        List<float[]> cps = new ArrayList<>();
        int[] nodesX = {14, 14, 10, 6, 6, 10, 14, 18, 18, 14};
        int[] nodesZ = {22, 18, 18, 20, 24, 25, 25, 24, 20, 22};
        for (int i = 0; i < nodesX.length; i++) {
            cps.add(new float[]{CityLayout.roadCoord(nodesX[i]), CityLayout.roadCoord(nodesZ[i])});
        }
        return cps;
    }
}
