package ohkt.economy;

import ohkt.engine.EventBus;
import ohkt.engine.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Economia do jogador: dinheiro, transações, renda de propriedades.
 */
public final class Economy {

    private int money;
    private int totalEarned, totalSpent;
    private final List<String> transactions = new ArrayList<>();

    public int money() { return money; }

    public int totalEarned() { return totalEarned; }

    public int totalSpent() { return totalSpent; }

    public void earn(int amount, String label) {
        if (amount <= 0) return;
        money += amount;
        totalEarned += amount;
        transactions.add("+" + amount + " " + label);
        if (transactions.size() > 30) transactions.remove(0);
    }

    /** Tenta gastar; retorna false se não houver dinheiro. */
    public boolean spend(Game g, int amount, String label) {
        if (money < amount) {
            if (g != null) g.hud.notify("Dinheiro insuficiente (R$" + amount + ")");
            return false;
        }
        money -= amount;
        totalSpent += amount;
        transactions.add("-" + amount + " " + label);
        if (transactions.size() > 30) transactions.remove(0);
        if (g != null) {
            g.audio.play("CASH", g.player.pos.x, g.player.pos.y, g.player.pos.z, 0.4f, 0.8f);
            g.bus.post(EventBus.Type.MONEY_CHANGED, money);
        }
        return true;
    }

    public List<String> transactions() { return transactions; }

    public void setMoney(int v) { this.money = v; }

    public void setTotals(int earned, int spent) {
        this.totalEarned = earned;
        this.totalSpent = spent;
    }

    /** Renda diária de propriedades. */
    public int dailyIncome(Properties props) {
        int total = 0;
        for (String id : props.owned()) {
            total += Properties.incomeOf(id);
        }
        return total;
    }
}
