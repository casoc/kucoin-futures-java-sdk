/*
 * Copyright 2019 Mek Global Limited
 */

package com.kucoin.futures.core;

import com.kucoin.futures.core.model.enums.PrivateChannelEnum;
import com.kucoin.futures.core.model.enums.PublicChannelEnum;
import com.kucoin.futures.core.rest.request.OrderCreateApiRequest;
import com.kucoin.futures.core.rest.response.MarkPriceResponse;
import com.kucoin.futures.core.rest.response.OrderCreateResponse;
import com.kucoin.futures.core.rest.response.TickerResponse;
import com.kucoin.futures.core.websocket.event.AccountChangeEvent;
import com.kucoin.futures.core.websocket.event.ContractMarketEvent;
import com.kucoin.futures.core.websocket.event.ExecutionChangeEvent;
import com.kucoin.futures.core.websocket.event.Level2ChangeEvent;
import com.kucoin.futures.core.websocket.event.Level2OrderBookEvent;
import com.kucoin.futures.core.websocket.event.Level3ChangeEvent;
import com.kucoin.futures.core.websocket.event.PositionChangeEvent;
import com.kucoin.futures.core.websocket.event.StopOrderLifecycleEvent;
import com.kucoin.futures.core.websocket.event.TransactionStatisticEvent;
import com.kucoin.futures.core.websocket.event.Level3ChangeEventV2;
import org.hamcrest.core.Is;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

/**
 * Run with -Dorg.slf4j.simpleLogger.defaultLogLevel=debug for debug logging
 *
 * @author chenshiwei
 * @email casocroz@gmail.com
 * @date 2019/10/21
 */
public class KucoinFuturesWSClientTest {

    private static KucoinFuturesRestClient kucoinFuturesRestClient;
    private static KucoinFuturesPrivateWSClient kucoinFuturesPrivateWSClient;

    private static final String SYMBOL = "XBTUSDM";

    @BeforeClass
    public static void setupClass() throws Exception {
        KucoinFuturesClientBuilder builder = new KucoinFuturesClientBuilder().withBaseUrl("https://api-sandbox-futures.kucoin.com")
                .withApiKey("5da5345c5a78b400087f0779", "5aaa3efb-3b9a-4849-9a35-040712d7d108", "Abc123456");
        kucoinFuturesRestClient = builder.buildRestClient();
        kucoinFuturesPrivateWSClient = builder.buildPrivateWSClient();
        kucoinFuturesPrivateWSClient.connect();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        kucoinFuturesPrivateWSClient.close();
    }

    @Test
    public void onStopOrderLifecycle() throws Exception {
        AtomicReference<StopOrderLifecycleEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onStopOrderLifecycle(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PrivateChannelEnum.STOP_ORDER, SYMBOL);
            gotEvent.countDown();
        });

        MarkPriceResponse currentMarkPrice = kucoinFuturesRestClient.indexAPI().getCurrentMarkPrice(SYMBOL);
        BigDecimal marketPrice = currentMarkPrice.getValue();

        placeStopOrder(marketPrice.add(new BigDecimal("0.05")), "up");
        placeStopOrder(marketPrice.add(new BigDecimal("0.5")), "up");
        placeStopOrder(marketPrice.add(BigDecimal.ONE), "up");
        placeStopOrder(marketPrice.subtract(BigDecimal.ONE), "down");
        placeStopOrder(marketPrice.subtract(new BigDecimal("0.05")), "down");
        placeStopOrder(marketPrice.subtract(new BigDecimal("0.5")), "down");

        boolean await = gotEvent.await(20, TimeUnit.SECONDS);
        kucoinFuturesRestClient.orderAPI().cancelAllLimitOrders(SYMBOL);
        kucoinFuturesRestClient.orderAPI().cancelAllStopOrders(SYMBOL);

        assertTrue(await);
        assertThat(event.get(), notNullValue());
    }

    @Test
    public void onAccountBalance() throws Exception {
        AtomicReference<AccountChangeEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onAccountBalance(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PrivateChannelEnum.ACCOUNT);
            gotEvent.countDown();
        });

        buyAndSell();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    @Test
    public void onPositionChange() throws Exception {
        AtomicReference<PositionChangeEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onPositionChange(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PrivateChannelEnum.POSITION, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        buyAndSell();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    @Test
    public void ping() throws Exception {
        String requestId = "1234567890";
        String ping = kucoinFuturesPrivateWSClient.ping(requestId);
        assertThat(ping, Is.is(requestId));
    }

    @Test
    public void onTicker() throws Exception {
        AtomicReference<TickerResponse> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onTicker(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.TICKER, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        // Make some actual executions
        buyAndSell();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    @Test
    public void onLevel2Data() throws Exception {
        AtomicReference<Level2ChangeEvent> event = new AtomicReference<>();
        AtomicReference<Level2OrderBookEvent> depth5Event = new AtomicReference<>();
        AtomicReference<Level2OrderBookEvent> depth50Event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(3);

        kucoinFuturesPrivateWSClient.onLevel2Data(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.LEVEL2, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        kucoinFuturesPrivateWSClient.onLevel2Depth5Data(response -> {
            depth5Event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.LEVEL2_DEPTH_5, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        kucoinFuturesPrivateWSClient.onLevel2Depth50Data(response -> {
            depth50Event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.LEVEL2_DEPTH_50, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        // Trigger a market change
        placeOrderAndCancelOrder();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
        assertThat(depth5Event.get(), notNullValue());
        assertThat(depth50Event.get(), notNullValue());
    }

    @Test
    public void onMatchExecutionData() throws Exception {
        AtomicReference<ExecutionChangeEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onExecutionData(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.MATCH, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        // Make some actual executions
        buyAndSell();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    @Test
    public void onLevel3Data() throws Exception {
        AtomicReference<Level3ChangeEvent> event = new AtomicReference<>();
        AtomicReference<Level3ChangeEventV2> v2Event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onLevel3Data(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.LEVEL3, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        kucoinFuturesPrivateWSClient.onLevel3DataV2(response -> {
            v2Event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.LEVEL3_V2, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        // Trigger a market change
        placeOrderAndCancelOrder();

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
        assertThat(v2Event.get(), notNullValue());
    }

    @Test
    public void onContractMarketData() throws Exception {
        AtomicReference<ContractMarketEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onContractMarketData(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.CONTRACT_MARKET, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    @Test
    @Ignore
    public void onTransactionStatistic() throws Exception {
        AtomicReference<TransactionStatisticEvent> event = new AtomicReference<>();
        CountDownLatch gotEvent = new CountDownLatch(1);

        kucoinFuturesPrivateWSClient.onTransactionStatistic(response -> {
            event.set(response.getData());
            kucoinFuturesPrivateWSClient.unsubscribe(PublicChannelEnum.TRANSACTION_STATISTIC, SYMBOL);
            gotEvent.countDown();
        }, SYMBOL);

        assertTrue(gotEvent.await(20, TimeUnit.SECONDS));
        assertThat(event.get(), notNullValue());
    }

    private void placeOrderAndCancelOrder() throws InterruptedException, IOException {
        Thread.sleep(1000);
        OrderCreateApiRequest request = OrderCreateApiRequest.builder()
                .price(BigDecimal.valueOf(1000)).size(BigDecimal.ONE).side("buy").leverage("5")
                .symbol(SYMBOL).type("limit").clientOid(UUID.randomUUID().toString()).build();
        OrderCreateResponse order = kucoinFuturesRestClient.orderAPI().createOrder(request);
        kucoinFuturesRestClient.orderAPI().cancelOrder(order.getOrderId());
    }

    private void buyAndSell() throws InterruptedException, IOException {
        Thread.sleep(1000);
        OrderCreateApiRequest request1 = OrderCreateApiRequest.builder()
                .size(BigDecimal.ONE)
                .side("buy")
                .symbol(SYMBOL)
                .type("market")
                .leverage("5")
                .clientOid(UUID.randomUUID().toString())
                .build();
        kucoinFuturesRestClient.orderAPI().createOrder(request1);
        OrderCreateApiRequest request2 = OrderCreateApiRequest.builder()
                .size(BigDecimal.ONE)
                .side("sell")
                .symbol(SYMBOL)
                .type("market")
                .leverage("5")
                .clientOid(UUID.randomUUID().toString())
                .build();
        kucoinFuturesRestClient.orderAPI().createOrder(request2);
    }

    private OrderCreateResponse placeStopOrder(BigDecimal stopPrice, String stop) throws IOException {
        OrderCreateApiRequest pageRequest = OrderCreateApiRequest.builder()
                .stop(stop).stopPriceType("MP").stopPrice(stopPrice)
                .price(BigDecimal.valueOf(1000)).size(BigDecimal.ONE).side("buy").leverage("5")
                .symbol(SYMBOL).type("limit").clientOid(UUID.randomUUID().toString()).build();
        return kucoinFuturesRestClient.orderAPI().createOrder(pageRequest);
    }
}
