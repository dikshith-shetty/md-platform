package com.rc.md.client.analytics;

import com.rc.md.common.api.HistoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "md-analytics",
        url = "${md.analytics.base-url}"
)
public interface AnalyticsClient {

    @GetMapping("api/v1/history")
    HistoryResponse getHistory(
            @RequestParam("symbol") String symbol,
            @RequestParam("interval") String interval,
            @RequestParam("from") long from,
            @RequestParam("to") long to
    );

//    @GetMapping("api/v2/history")
//    HistoryResponse getHistoryV2(String symbol, String interval, long from, long to);
}
