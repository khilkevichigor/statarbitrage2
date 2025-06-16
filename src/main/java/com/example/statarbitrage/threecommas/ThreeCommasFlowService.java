package com.example.statarbitrage.threecommas;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBot;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.ValidateService;
import com.example.statarbitrage.utils.ThreeCommasUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasFlowService {
    private final PairDataService pairDataService;
    private final ValidateService validateService;
    private final ThreeCommasService threeCommasService;

    public void startRealTradeViaDcaBots(String chatIdStr) {
        DcaBot longDcaBot = null;
        DcaBot shortDcaBot = null;
        try {
            //получить PairData
            PairData pairData = pairDataService.getPairData();

            //валидация PairData
            validateService.validatePairDataAndThrow(pairData);

            //long
            longDcaBot = threeCommasService.getDcaBot(true);
            validateService.validateLongBotBeforeNewTradeAndThrow(longDcaBot);
            String okxLongTicker = pairData.getLongTicker();
            String threeCommasLongTicker = ThreeCommasUtil.get3CommasTicker(okxLongTicker);
            longDcaBot.setPairs(Collections.singletonList(threeCommasLongTicker));
            DcaBot editedLongDcaBot = threeCommasService.editDcaBot(longDcaBot);
            validateService.validatePairsAndThrow(longDcaBot, threeCommasLongTicker);
            DcaBot enabledLongDcaBot = threeCommasService.enableDcaBot(editedLongDcaBot.getId());//todo падаю тут - {"error_attributes":"Необходимо выбрать тарифный план","error":"free_plan_limit_reached"}

            //short
            shortDcaBot = threeCommasService.getDcaBot(false);
            String okxShortTicker = pairData.getShortTicker();
            String threeCommasShortTicker = ThreeCommasUtil.get3CommasTicker(okxShortTicker);
            shortDcaBot.setPairs(Collections.singletonList(threeCommasShortTicker));
            DcaBot editedShortDcaBot = threeCommasService.editDcaBot(shortDcaBot);
            validateService.validatePairsAndThrow(shortDcaBot, threeCommasShortTicker);
            DcaBot enabledShortDcaBot = threeCommasService.enableDcaBot(editedShortDcaBot.getId());

        } catch (Exception e) {
            log.error("❌ Failed startRealTrade()", e);

            if (longDcaBot != null) {
                try {
                    threeCommasService.closeDcaBotAtMarketPrice(longDcaBot.getId());
                } catch (Exception ex) {
                    log.warn("⚠️ Failed to close long bot: " + ex.getMessage());
                }
            }

            if (shortDcaBot != null) {
                try {
                    threeCommasService.closeDcaBotAtMarketPrice(shortDcaBot.getId());
                } catch (Exception ex) {
                    log.warn("⚠️ Failed to close short bot: " + ex.getMessage());
                }
            }

            throw new RuntimeException(e);
        }
    }

    public void stopRealTradeViaDcaBots(String chatIdStr) {
        DcaBot longDcaBot = null;
        DcaBot shortDcaBot = null;
        try {
            //получить PairData
            PairData pairData = pairDataService.getPairData();

            //получить ботов
            longDcaBot = threeCommasService.getDcaBot(true);
            shortDcaBot = threeCommasService.getDcaBot(false);

            threeCommasService.closeDcaBotAtMarketPrice(longDcaBot.getId());
            threeCommasService.closeDcaBotAtMarketPrice(shortDcaBot.getId());

        } catch (Exception e) {
            log.error("❌ Failed stopRealTrade()", e);

            if (longDcaBot != null) {
                try {
                    threeCommasService.closeDcaBotAtMarketPrice(longDcaBot.getId());
                } catch (Exception ex) {
                    log.warn("⚠️ Failed to close long bot: " + ex.getMessage());
                }
            }

            if (shortDcaBot != null) {
                try {
                    threeCommasService.closeDcaBotAtMarketPrice(shortDcaBot.getId());
                } catch (Exception ex) {
                    log.warn("⚠️ Failed to close short bot: " + ex.getMessage());
                }
            }

            throw new RuntimeException(e);
        }

    }
}
