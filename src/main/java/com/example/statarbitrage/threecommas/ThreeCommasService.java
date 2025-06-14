package com.example.statarbitrage.threecommas;

import com.example.statarbitrage.api.ThreeCommasClient;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBot;
import com.example.statarbitrage.model.threecommas.response.trade.TradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeCommasService {
    private final ThreeCommasClient threeCommasClient;
    private static final int LONG_DCA_BOT_ID = 15911089;
    private static final int SHORT_DCA_BOT_ID = 123;
    private static final long SPOT_ACCOUNT_ID = 32991372;
    private static final long FUTURES_ACCOUNT_ID = 32991373;

    private final OkHttpClient client = new OkHttpClient();

    public void test() {
        try {
//            threeCommasClient.validateCredentials();
//            threeCommasClient.getBotsList();
//            threeCommasClient.getTradesHistory();
//            threeCommasClient.getAccounts();

            TradeResponse trade = threeCommasClient.createTrade(
                    FUTURES_ACCOUNT_ID,
                    "USDT_XRP-USDT-SWAP",
                    OrderType.MARKET.getName(),
                    TradeSide.SELL.getName(),
                    0.01,
                    true,
                    LeverageType.CROSS.getName(),
                    false,
                    false,
                    false);
            System.out.println(trade.getTrade().getUuid());

//            TradeResponse tradeResponse = threeCommasClient.getTradeByUuid("0bb0e9d6-a8d6-4ea3-a143-e6488e0747dc");
//            Trade trade = tradeResponse.getTrade();
//            TradeData data = trade.getData();
//            if (data.isCancelable()) {
//                threeCommasClient.cancelTrade(trade.getUuid());
//            }

//            ActiveTradesResponse activeTrades = threeCommasClient.getActiveTrades();


//            threeCommasClient.getDcaBots();

//            DcaBot dcaBot = threeCommasClient.getDcaBot(15911089);
//            DcaBot editedDcaBot = threeCommasClient.editDcaBot(dcaBot);

//            threeCommasClient.disableDcaBot(15911089);
//            threeCommasClient.enableDcaBot(15911089);
//            threeCommasClient.getDcaBotProfitData(15911089);

//            threeCommasClient.getDcaBotStats(15911089); //todo не работает
//            threeCommasClient.getDcaBotDealsStats(15911089);
//            threeCommasClient.getAvailableStrategies();
//            threeCommasClient.closeDcaBotAtMarketPrice(15911089);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public DcaBot getLongDcaBot() throws Exception {
        return threeCommasClient.getDcaBot(LONG_DCA_BOT_ID);
    }

    public DcaBot getShortDcaBot() throws Exception {
        return threeCommasClient.getDcaBot(SHORT_DCA_BOT_ID);
    }


    public DcaBot editDcaBot(DcaBot dcaBot) throws Exception {
        return threeCommasClient.editDcaBot(dcaBot);
    }

    public DcaBot enableDcaBot(long botId) throws Exception {
        return threeCommasClient.enableDcaBot(botId);
    }


}
