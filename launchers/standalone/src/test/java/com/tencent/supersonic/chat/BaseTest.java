package com.tencent.supersonic.chat;

import com.google.common.collect.Lists;
import com.tencent.supersonic.BaseApplication;
import com.tencent.supersonic.chat.api.pojo.request.ChatExecuteReq;
import com.tencent.supersonic.chat.api.pojo.request.ChatParseReq;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.service.AgentService;
import com.tencent.supersonic.chat.server.service.ChatQueryService;
import com.tencent.supersonic.common.pojo.enums.DatePeriodEnum;
import com.tencent.supersonic.common.service.ChatModelService;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.response.ParseResp;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import com.tencent.supersonic.util.DataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class BaseTest extends BaseApplication {

    protected final int unit = 7;
    protected final String startDay = LocalDate.now().minusDays(unit).toString();
    protected final String endDay = LocalDate.now().toString();
    protected final DatePeriodEnum period = DatePeriodEnum.DAY;

    @Autowired
    protected ChatQueryService chatQueryService;
    @Autowired
    protected AgentService agentService;
    @Autowired
    protected ChatModelService chatModelService;

    @Value("${s2.demo.enableLLM:false}")
    protected boolean enableLLM;
    protected int agentId;


    protected List<Long> durations = Lists.newArrayList();

    protected QueryResult submitMultiTurnChat(String queryText, Integer agentId, Integer chatId)
            throws Exception {
        ParseResp parseResp = submitParse(queryText, agentId, chatId);

        SemanticParseInfo semanticParseInfo = parseResp.getSelectedParses().get(0);
        ChatExecuteReq request = ChatExecuteReq.builder().queryText(parseResp.getQueryText())
                .user(DataUtils.getUser()).parseId(semanticParseInfo.getId())
                .queryId(parseResp.getQueryId()).chatId(chatId).agentId(agentId).saveAnswer(true)
                .build();
        QueryResult queryResult = chatQueryService.execute(request);
        queryResult.setChatContext(semanticParseInfo);
        return queryResult;
    }

    protected QueryResult submitNewChat(String queryText, Integer agentId) throws Exception {
        int chatId = DataUtils.ONE_TURNS_CHAT_ID;
        ParseResp parseResp = submitParse(queryText, agentId, chatId);

        SemanticParseInfo parseInfo = parseResp.getSelectedParses().get(0);
        ChatExecuteReq request = ChatExecuteReq.builder().queryText(parseResp.getQueryText())
                .user(DataUtils.getUser()).parseId(parseInfo.getId()).agentId(agentId)
                .chatId(chatId).queryId(parseResp.getQueryId()).saveAnswer(false).build();

        QueryResult result = chatQueryService.execute(request);
        result.setChatContext(parseInfo);
        return result;
    }

    protected ParseResp submitParse(String queryText, Integer agentId, Integer chatId) {

        ChatParseReq chatParseReq = DataUtils.getChatParseReq(chatId, queryText, enableLLM);
        chatParseReq.setAgentId(agentId);
        return chatQueryService.parse(chatParseReq);
    }

    protected void assertSchemaElements(Set<SchemaElement> expected, Set<SchemaElement> actual) {
        Set<String> expectedNames = expected.stream().map(s -> s.getName()).filter(s -> s != null)
                .collect(Collectors.toSet());
        Set<String> actualNames = actual.stream().map(s -> s.getName()).filter(s -> s != null)
                .collect(Collectors.toSet());

        assertEquals(expectedNames, actualNames);
    }

    protected void assertQueryResult(QueryResult expected, QueryResult actual) {
        SemanticParseInfo expectedParseInfo = expected.getChatContext();
        SemanticParseInfo actualParseInfo = actual.getChatContext();

        assertEquals(QueryState.SUCCESS, actual.getQueryState());
        assertEquals(expected.getQueryMode(), actual.getQueryMode());
        assertEquals(expectedParseInfo.getAggType(), actualParseInfo.getAggType());

        assertSchemaElements(expectedParseInfo.getMetrics(), actualParseInfo.getMetrics());
        assertSchemaElements(expectedParseInfo.getDimensions(), actualParseInfo.getDimensions());

        assertEquals(expectedParseInfo.getDimensionFilters(),
                actualParseInfo.getDimensionFilters());
        assertEquals(expectedParseInfo.getMetricFilters(), actualParseInfo.getMetricFilters());

        assertEquals(expectedParseInfo.getDateInfo(), actualParseInfo.getDateInfo());
    }
}
