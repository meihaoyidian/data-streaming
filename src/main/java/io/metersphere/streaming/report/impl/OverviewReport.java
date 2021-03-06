package io.metersphere.streaming.report.impl;

import io.metersphere.streaming.commons.constants.ReportKeys;
import io.metersphere.streaming.report.base.ChartsData;
import io.metersphere.streaming.report.base.Statistics;
import io.metersphere.streaming.report.base.TestOverview;
import io.metersphere.streaming.report.parse.ResultDataParse;
import org.apache.jmeter.report.processor.*;
import org.apache.jmeter.report.processor.graph.impl.ActiveThreadsGraphConsumer;
import org.apache.jmeter.report.processor.graph.impl.HitsPerSecondGraphConsumer;
import org.apache.jmeter.report.processor.graph.impl.ResponseTimeOverTimeGraphConsumer;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OverviewReport extends AbstractReport {

    @Override
    public String getReportKey() {
        return ReportKeys.Overview.name();
    }

    private final DecimalFormat responseTimeFormat = new DecimalFormat("0.0000");

    @Override
    public void execute() {

        Map<String, Object> activeDataMap = ResultDataParse.getGraphDataMap(content, new ActiveThreadsGraphConsumer());
        List<ChartsData> usersList = ResultDataParse.graphMapParsing(activeDataMap, "users", "yAxis");
        Map<String, List<ChartsData>> collect = usersList.stream().collect(Collectors.groupingBy(ChartsData::getGroupName));
        AtomicInteger maxUser = new AtomicInteger();
        collect.forEach((k, cs) -> {
            Optional<ChartsData> max = cs.stream().max(Comparator.comparing(ChartsData::getyAxis));
            int i = max.get().getyAxis().setScale(0, BigDecimal.ROUND_UP).intValue();
            maxUser.addAndGet(i);
        });

        Map<String, Object> hitsDataMap = ResultDataParse.getGraphDataMap(content, new HitsPerSecondGraphConsumer());
        List<ChartsData> hitsList = ResultDataParse.graphMapParsing(hitsDataMap, "hits", "yAxis2");
        double hits = hitsList.stream().map(ChartsData::getyAxis2)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0);

        Map<String, Object> errorDataMap = ResultDataParse.getSummaryDataMap(content, new StatisticsSummaryConsumer());
        List<Statistics> statisticsList = ResultDataParse.summaryMapParsing(errorDataMap, Statistics.class);
        double avgTp90 = statisticsList.stream().map(item -> Double.parseDouble(item.getTp90())).mapToDouble(Double::doubleValue).average().orElse(0);
        double avgBandwidth = statisticsList.stream().map(item -> Double.parseDouble(item.getReceived())).mapToDouble(Double::doubleValue).average().orElse(0);

        Map<String, Object> responseDataMap = ResultDataParse.getGraphDataMap(content, new ResponseTimeOverTimeGraphConsumer());
        List<ChartsData> responseDataList = ResultDataParse.graphMapParsing(responseDataMap, "response", "yAxis2");
        double responseTime = responseDataList.stream().map(ChartsData::getyAxis2)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0);

        Map<String, Object> sampleDataMap = ResultDataParse.getSampleDataMap(content, new RequestsSummaryConsumer());
        String error = "";
        for (String key : sampleDataMap.keySet()) {
            MapResultData mapResultData = (MapResultData) sampleDataMap.get(key);
            ResultData koPercent = mapResultData.getResult("KoPercent");
            error = ((ValueResultData) koPercent).getValue().toString();
        }

        TestOverview testOverview = new TestOverview();
        testOverview.setMaxUsers(String.valueOf(maxUser.get()));
        testOverview.setAvgThroughput(decimalFormat.format(hits));
        testOverview.setErrors(decimalFormat.format(Double.valueOf(error)));
        testOverview.setAvgResponseTime(responseTimeFormat.format(responseTime / 1000));
        testOverview.setResponseTime90(responseTimeFormat.format(avgTp90 / 1000));
        testOverview.setAvgBandwidth(decimalFormat.format(avgBandwidth));

        saveResult(reportId, testOverview);
    }
}
