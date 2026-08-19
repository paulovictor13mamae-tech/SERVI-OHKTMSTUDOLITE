package ohkt.player;

import ohkt.graphics.Renderer3D;
import ohkt.utils.ColorUtil;

/**
 * Desenha humanoides (jogador, NPCs, policiais) com animacao procedural:
 * caminhada/corrida, mira, agachamento, morte e poses de combate.
 */
public final class HumanoidRenderer {

    public static final class Look {
        public int skin = 0xffc8a080;
        public int shirt = 0xffd0d0d0;
        public int pants = 0xff3a3a44;
        public int shoes = 0xff30241c;
        public int hair = 0xff2a2018;
        public float scale = 1f;

        public Look set(int skin, int shirt, int pants, int shoes, int hair) {
            this.skin = skin;
            this.shirt = shirt;
            this.pants = pants;
            this.shoes = shoes;
            this.hair = hair;
            return this;
        }

        public Look copy() {
            Look l = new Look();
            l.skin = skin; l.shirt = shirt; l.pants = pants; l.shoes = shoes; l.hair = hair; l.scale = scale;
            return l;
        }
    }

    /**
     * @param phase   fase da passada (rad)
     * @param speed01 0 parado .. 1 correndo
     * @param aiming  segurando arma apontada
     * @param crouch  agachado
     * @param dead01  0 vivo .. 1 morto (deitado)
     * @param sitting dentro de veiculo
     * @param attack01 brusco de soco 0..1
     */
    public static void draw(Renderer3D r, float x, float y, float z, float yaw,
                            float phase, float speed01, boolean aiming, boolean crouch,
                            float dead01, boolean sitting, float attack01, Look look) {
        float s = look.scale;
        float bodyY = y; // base dos pes em y
        if (dead01 > 0) {
            drawDead(r, x, bodyY, z, yaw, look, dead01);
            return;
        }
        float legLen = 0.78f * s * (crouch ? 0.6f : 1f);
        float torsoH = 0.62f * s;
        float torsoY = bodyY + legLen;
        float headR = 0.13f * s;

        float swing = (float) Math.sin(phase) * (0.55f * speed01 + 0.06f);
        float swing2 = -swing;

        // pernas (caixas com pitch em torno do proprio centro)
        float hipY = bodyY + legLen;
        drawLimb(r, x, hipY - legLen / 2, z, yaw, swing, legLen / 2, 0.09f * s, 0.11f * s, look.pants);
        drawLimb(r, x, hipY - legLen / 2, z, yaw, swing2, legLen / 2, 0.09f * s, 0.11f * s, ColorUtil.shade(look.pants, 0.9f));
        // sapatos
        float shoeF = (float) (Math.sin(phase) * 0.25f * speed01);
        // torso
        float lean = crouch ? 0.25f : (aiming ? 0.08f : speed01 * 0.12f);
        r.drawBox(x, torsoY + torsoH / 2, z, 0.20f * s, torsoH / 2, 0.12f * s, yaw, lean, 0, look.shirt, false);
        // cabeca
        float headY = torsoY + torsoH + headR * 1.35f;
        r.drawBox(x, headY, z, headR, headR, headR * 1.05f, yaw, lean * 0.4f, 0, look.skin, false);
        // cabelo
        r.drawBox(x, headY + headR * 0.55f, z - headR * 0.12f, headR * 1.02f, headR * 0.6f, headR * 1.02f, yaw, lean * 0.4f, 0, look.hair, false);
        // bracos
        float shoulderY = torsoY + torsoH * 0.86f;
        float armLen = 0.55f * s;
        if (sitting) {
            drawLimb(r, x, shoulderY - armLen / 2 + 0.1f, z, yaw, 0.9f, armLen / 2, 0.07f * s, 0.09f * s, look.shirt);
            drawLimb(r, x, shoulderY - armLen / 2 + 0.1f, z, yaw, 0.9f, armLen / 2, 0.07f * s, 0.09f * s, look.shirt);
        } else if (aiming) {
            // bracos estendidos para frente
            float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
            float rX = (float) Math.cos(yaw), rZ = (float) Math.sin(yaw);
            float handF = 0.34f * s;
            float h1x = x + fwX * handF - rX * 0.14f * s, h1z = z + fwZ * handF - rZ * 0.14f * s;
            float h2x = x + fwX * handF * 0.8f + rX * 0.16f * s, h2z = z + fwZ * handF * 0.8f + rZ * 0.16f * s;
            r.drawBox(h1x, shoulderY - 0.02f, h1z, 0.06f * s, 0.06f * s, armLen / 2, yaw, 0, 0, look.shirt, false);
            r.drawBox(h2x, shoulderY - 0.06f, h2z, 0.06f * s, 0.06f * s, armLen / 2 * 0.8f, yaw, 0.35f, 0, look.shirt, false);
        } else {
            float armSwing = (float) Math.sin(phase) * (0.5f * speed01 + 0.05f);
            float punch = attack01 * 1.35f;
            drawLimb(r, x, shoulderY - armLen / 2, z, yaw, -armSwing, armLen / 2, 0.07f * s, 0.09f * s, look.shirt);
            drawLimb(r, x, shoulderY - armLen / 2, z, yaw, armSwing - punch, armLen / 2, 0.07f * s, 0.09f * s, ColorUtil.shade(look.shirt, 0.92f));
        }
    }

    /** Membro rotacionado: pitch em relacao ao eixo do corpo. */
    private static void drawLimb(Renderer3D r, float x, float centerY, float z, float yaw, float pitch, float halfLen, float halfW, float halfD, int color) {
        // desloca o centro na direcao do balanço
        float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
        float off = (float) Math.sin(-pitch) * halfLen * 0.8f;
        r.drawBox(x + fwX * off, centerY, z + fwZ * off, halfW, halfLen, halfD, yaw, pitch, 0, color, false);
    }

    private static void drawDead(Renderer3D r, float x, float y, float z, float yaw, Look look, float t) {
        float fall = Math.min(1, t * 2.2f);
        float s = look.scale;
        // corpo deitado com queda interpolada (afunda um pouco)
        float yBody = y + 0.22f * s * (1 - fall * 0.35f);
        r.drawBox(x, yBody, z, 0.2f * s, 0.14f * s, 0.42f * s, yaw, 0, (float) (Math.PI / 2 * fall), look.shirt, false);
        // cabeca
        float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
        r.drawBox(x + fwX * 0.52f * s * fall, yBody + 0.05f, z + fwZ * 0.52f * s * fall, 0.13f * s, 0.13f * s, 0.13f * s, yaw, 0, 0, look.skin, false);
        // pernas
        r.drawBox(x - fwX * 0.5f * s, yBody - 0.03f, z - fwZ * 0.5f * s, 0.1f * s, 0.1f * s, 0.4f * s, yaw, 0, 0, look.pants, false);
    }
}
