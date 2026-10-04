package com.yulinlin.jdbc.log;

import com.yulinlin.data.core.log.LogPrint;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.SessionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.JdbcProperties;
import com.yulinlin.jdbc.session.SqlNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SqlNodeLog implements LogPrint<SqlNode> {





    @Autowired
    JdbcProperties jdbcProperties;


    public SqlNodeLog() {

    }




    private List format(SqlNode node){

            List list =  node.getList().stream().map(row -> {
                if(row instanceof Date){
                    return  DateTime.date((Date)row);
                }
                return row;
            }).collect(Collectors.toList());
        return list;
    }

    @Override
    public void success(long time, ParseResult result) {

        if(!jdbcProperties.isLog()){
            return;
        }
        SqlNode request = (SqlNode)result.getRequest();


        EntitySession session =  SessionUtil.route().session();


        log.info("[{}][{}]" +
                        "[{}]\n" +
                        "[{}]\n" +
                        "{}",
                session.group(),session.cluster(),
                time,request.getSql(),format(request));

    }

    @Override
    public void error(Throwable e, ParseResult result) {


        EntitySession session =  SessionUtil.route().session();
        SqlNode request =(SqlNode) result.getRequest();

        log.error("[{}][{}]" +
                        "[{}]\n" +
                        "\n{}",
                session.group(),session.cluster(),
                request.getSql(),format(request));
        log.error("sql异常:"+e.getMessage(),e);

    }


}
