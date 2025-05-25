package com.example.statarbitrage.experemental.chatgpt;

import java.util.Random;

public class RandomTradeSimulator {

    public static void main(String[] args) {
        simulateTrades(1_000_000); // можно указать любое большое число
    }

    public static void simulateTrades(int totalTrades) {
        Random random = new Random();
        int heads = 0;
        int tails = 0;
        int balance = 0;

        for (int i = 0; i < totalTrades; i++) {
            boolean isWin = random.nextBoolean(); // true = орёл, false = решка

            if (isWin) {
                heads++;
                balance += 3; // выиграл 3$
            } else {
                tails++;
                balance -= 1; // проиграл 1$
            }
        }

        System.out.println("Всего сделок: " + totalTrades);
        System.out.println("Профитных (орёл): " + heads);
        System.out.println("Убыточных (решка): " + tails);
        System.out.println("Итоговый баланс: $" + balance);
        System.out.printf("Процент прибыльных: %.2f%%\n", 100.0 * heads / totalTrades);
    }
}
