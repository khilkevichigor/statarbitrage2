package com.example.statarbitrage.experemental.chatgpt;

import java.util.Random;

public class DynamicRRSimulation {

    public static void main(String[] args) {
        simulate(1_000_000, 10); // 1 млн сделок, тейк = 10
    }

    public static void simulate(int trades, int reward) {
        double risk = 1.0;

        // Пример: вероятность успеха обратно пропорциональна reward
        double successProbability = Math.min(1.0, 0.5 * risk / reward * 3); // немного подкручено для реализма

        Random random = new Random();
        int wins = 0;
        int losses = 0;
        double balance = 0.0;

        for (int i = 0; i < trades; i++) {
            if (random.nextDouble() < successProbability) {
                wins++;
                balance += reward;
            } else {
                losses++;
                balance -= risk;
            }
        }

        System.out.println("Reward: " + reward);
        System.out.println("Success probability: " + String.format("%.2f", successProbability * 100) + "%");
        System.out.println("Сделок: " + trades);
        System.out.println("Профитных: " + wins);
        System.out.println("Убыточных: " + losses);
        System.out.printf("Итог: $%.2f\n", balance);
        System.out.printf("Win Rate: %.2f%%\n", 100.0 * wins / trades);
    }
}
