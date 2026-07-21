package com.yulinlin.jdbc.session;


import com.yulinlin.data.core.cache.CacheKey;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.node.INode;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.SimpParamsContext;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.session.EntitySession;
import com.yulinlin.data.core.session.RequestType;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.jdbc.JdbcProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public abstract class AbstractJdbcSession extends AbstractSession implements EntitySession {


   private JdbcProperties properties;

    protected DataSource dataSource;

    public AbstractJdbcSession(DataSource dataSource) {
        this.dataSource = dataSource;

    }

    @Override
    protected ParseResult parseNode(
            RequestType requestType,
            Object root, INode node, Class clazz) {
        if(root == null){
            root = new HashMap<>();
        }
        SimpParamsContext context = new SimpParamsContext(requestType,root,getCoderManager().createEncoderBuffer(),clazz,isMapUnderscoreToCamelCase());

        if(root instanceof Map){
            context.put((Map)root);
        }else {
            for (Field field : ReflectionUtil.getAllDeclaredFields(root.getClass())) {
                Object o = ReflectionUtil.invokeGetter(root, field);
                if(o == null){
                    continue;
                }
                context.put(field.getName(),o);
            }

        }




        ParseResult result = (ParseResult) getParseManager().parse(node,context);
        return result;
    }

    @Override
    protected boolean isMapUnderscoreToCamelCase() {
        return properties.isMapUnderscoreToCamelCase();
    }


    protected List<IDataBuffer> resultSetToBuffer(ResultSet resultSet ) throws Exception {



        ArrayList<IDataBuffer> list =  new ArrayList<>();
        while (resultSet.next()) {
            IDataBuffer buffer = getCoderManager().createDecoderBuffer();

            list.add(buffer);

            ResultSetMetaData metaData = resultSet.getMetaData();

            int columnTotal = metaData.getColumnCount();
            for (int i = 1; i <= columnTotal; i++) {

                String columnName = metaData.getColumnLabel(i);
                Object value = null;

                //列类型
            //    int type =  metaData.getColumnType(i);
            //    JDBCType jdbcType = JDBCType.valueOf(type);

                value = resultSet.getString(columnName);


                if (value == null) {
                    continue;
                }
                buffer.put(columnName,value);
            }

        }

        return list;
    }


    @Override
    protected CompletableFuture<Integer> executeUpdateAsync(List<ParseResult> results, RequestType requestType) {
        ConnectionPool pool = ConnectionUtil.pool(dataSource);

        CompletableFuture<Integer> future =   CompletableFuture.supplyAsync(() -> {
            Connection connection  =  pool.getConnection();
            try {
                return executeUpdateNode(connection,results);
            }finally {
                pool.releaseConnection(connection);
            }
        },getThreadPoolExecutor());


        return future;


    }

    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        List<IDataBuffer> buffers = executeSelect(request);
        return buffers.get(0);
    }

    @Override
    protected Integer executeUpdate(List<ParseResult> list,RequestType requestType) {


            Connection connection =  ConnectionUtil.getSpringConnection(dataSource);

            try {
                Integer value =  executeUpdateNode(connection,list);
                return value;
            }finally {
                ConnectionUtil.releaseSpringConnection(dataSource,connection);
            }





    }


    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {
        return executeSelect(request);
    }

    @Override
    protected List<IDataBuffer> executeSelect(ParseResult request) {
        SqlNode sqlNode = (SqlNode)request.getRequest();


        Connection connection =  ConnectionUtil.getSpringConnection(dataSource);

        try {
            List<IDataBuffer> value =  executeSelectNode(connection,sqlNode);

            return value;
        }finally {
            ConnectionUtil.releaseSpringConnection(dataSource,connection);
        }

    }





    protected  abstract Integer executeUpdateNode(Connection connection, List<ParseResult> list);


    protected  abstract List<IDataBuffer> executeSelectNode(Connection connection,SqlNode node);






    @Override
    public void startTransaction() {
        ConnectionUtil.startTransaction();
        super.startTransaction();

    }

    @Override
    public void commitTransaction() {

        boolean ok = isOpenTransaction();
        super.commitTransaction();


        if(ok && !isOpenTransaction()){
            ConnectionUtil.commitTransaction();
        }
    }

    @Override
    public void rollbackTransaction() {
        boolean ok = isOpenTransaction();
        super.rollbackTransaction();
        if(ok && !isOpenTransaction()){
            ConnectionUtil.rollbackTransaction();
        }

    }

    public void setProperties(JdbcProperties properties) {
        this.properties = properties;
    }
}
