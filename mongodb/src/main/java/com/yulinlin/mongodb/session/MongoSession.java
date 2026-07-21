
package com.yulinlin.mongodb.session;


import com.mongodb.client.*;
import com.yulinlin.data.core.coder.IDataBuffer;
import com.yulinlin.data.core.parse.ParseResult;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.session.AbstractSession;
import com.yulinlin.data.core.session.RequestType;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MongoSession extends AbstractSession  {

    private MongoDatabase client;

    public MongoSession(MongoDatabase client) {
        this.client = client;
    }

    @Override
    protected IDataBuffer executeCount(ParseResult request) {
        BsonDocument command =(BsonDocument) request.getRequest();

        Document document = client.runCommand(command);

        int l = document.getInteger("n");

        IDataBuffer buffer = createDecoderBuffer();
        buffer.put("total",l);
        return buffer;
    }

    @Override
    protected boolean isMapUnderscoreToCamelCase() {
        return true;
    }



    @Override
    protected Integer executeUpdate(List<ParseResult> list, RequestType requestType) {

        List<ParseResult> results = (List)list;

        ParseResult request = results.get(0);


        String key=null ;

        switch (requestType){
            case insert:{
                key="documents";
                break;
            }case update:{
                key="updates";
                break;
            }case delete:{
                key="deletes";
                break;
            }
        }
        BsonDocument command =(BsonDocument) request.getRequest();
        int total = 0;
        for(int i = 1;i<results.size();i++){
            BsonArray documents = command.getArray(key);
            command.getArray(key).addAll(documents);
        }


        Document result = client.runCommand(command);


        if(request.getType() == ParseType.update){
            total += result.getInteger("nModified");
        }else {
            total += result.getInteger("n");
        }



        return total;
    }


    private IDataBuffer toBuffer(ParseResult request, Document document){
        IDataBuffer buffer = createDecoderBuffer();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            String s = request.getContext().toKey(entry.getKey());
            Object value = entry.getValue();

            if(value instanceof List){
                List<Document> list =(List)value;
                List<IDataBuffer> collect = list.stream().map(row -> toBuffer(request,row)).collect(Collectors.toList());
                buffer.put(s,collect);
            }else {

                Object val = entry.getValue();
                 if(val instanceof Document){
                    Document bf = (Document)val;
                    //日期处理
                    if(bf.containsKey("format")&&bf.containsKey("date")){
                        String date = bf.getString("date");
                        buffer.put(entry.getKey(),date);

                    }else {
                        IDataBuffer b = toBuffer(request,bf);
                        buffer.putAll(b.toMap());

                    }


                }else {
                    buffer.put(s,entry.getValue());
                }

            }

        }

      if(request.getType() == ParseType.group){

                Object data = document.get("_id");
                if(data instanceof Document){

                    Document _id = (Document)data;
                    IDataBuffer bf = toBuffer(request, _id);
                    buffer.putAll(bf.toMap());

                }

        }


        return buffer;
    }

    private    List<IDataBuffer> toBufferList (   ParseResult request,     MongoIterable<Document> documents){
        ArrayList<IDataBuffer> buffers = new ArrayList<>();
        MongoCursor<Document> iterator = documents.iterator();

        while (iterator.hasNext()){
            Document document = iterator.next();
            IDataBuffer buffer1 = toBuffer(request, document);
            buffers.add(buffer1);
        }

        return buffers;

    }

    @Override
    protected List<IDataBuffer> executeGroup(ParseResult request) {


        BsonDocument command = (BsonDocument)request.getRequest();


        List pipeline = command.getArray("pipeline");





        MongoCollection<Document> collection = client.getCollection(command.getString("aggregate").getValue());

       // Document aggregate1 = client.runCommand(command);

        AggregateIterable<Document> aggregate = collection.aggregate(pipeline);



        return toBufferList(request,aggregate);
    }

    @Override
    protected List<IDataBuffer> executeSelect(ParseResult parseResult) {


        BsonDocument command =(BsonDocument) parseResult.getRequest();

        BsonDocument filter = command.getDocument("filter");



        MongoCollection<Document> collection = client.getCollection(command.getString("find").getValue());


        FindIterable<Document> documents = collection.find(filter);

        if(command.containsKey("skip")){
            documents.skip(command.getInt32("skip").getValue())
                    .limit(command.getInt32("limit").getValue());
        }
        if(command.containsKey("sort")){
            documents.sort(command.getDocument("sort"));
        }

        return toBufferList(parseResult,documents);



    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void startTransaction() {

    }

    @Override
    public void commitTransaction() {

    }

    @Override
    public void rollbackTransaction() {

    }

}
