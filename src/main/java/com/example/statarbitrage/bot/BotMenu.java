package com.example.statarbitrage.bot;

import lombok.Getter;

import static com.example.statarbitrage.common.constant.Constants.GET_CSV_COMMAND;
import static com.example.statarbitrage.common.constant.Constants.GET_STATISTIC_COMMAND;

@Getter
public enum BotMenu {
    GET_CSV(GET_CSV_COMMAND),
    GET_STATISTIC(GET_STATISTIC_COMMAND);

    BotMenu(String name) {
        this.name = name;
    }

    private final String name;

}
