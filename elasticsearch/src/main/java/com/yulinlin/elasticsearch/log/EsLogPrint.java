package com.yulinlin.elasticsearch.log;

import co.elastic.clients.elasticsearch._types.RequestBase;
import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.elasticsearch.ElasticSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class EsLogPrint implements LogPrint<RequestBase> {



    @Autowired
    ElasticSearchProperties properties;

    @Override
    public void success(long time, ParseResult request) {
        if(!properties.isLog()){
            return;
        }
        EntitySession session =  SessionUtil.route().session();

        log.info("[{}][{}]" +
                        "[{}]\n" +
                        "[{}]",
                session.group(),session.cluster(),
                time,request.getRequest().toString());
    }

    @Override
    public void error(Throwable e, ParseResult request) {
        EntitySession session =  SessionUtil.route().session();

        log.error("[{}][{}]" +
                        "\n" +
                        "[{}]",
                session.group(),session.cluster(),request.getRequest().toString());
    }


}
