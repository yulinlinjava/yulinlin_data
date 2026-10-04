package com.yulinlin.elasticsearch.util;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.coder.IDataBuffer;
import jakarta.json.JsonArray;

import java.util.*;
import java.util.stream.Collectors;

public class AggregationUtil {


    public static List<IDataBuffer> toBufferList(Map<String,Aggregate> aggregationMap,     IDataBuffer decoderBuffer  ) {
        List<IDataBuffer> buffers = new ArrayList<>();

        toBufferList(aggregationMap,decoderBuffer,buffers);
        return buffers;
    }



    private static void toBufferList(Map<String,Aggregate> aggregationMap,
                                     IDataBuffer buffer

            ,List<IDataBuffer> buffers) {



        boolean ok = true;

        for (Map.Entry<String, Aggregate> entry : aggregationMap.entrySet()) {
            String key  = entry.getKey();
            Aggregate aggregate = entry.getValue();

            Object val = null;

            if(aggregate.isSterms()){

                StringTermsAggregate termsAggregate = aggregate.sterms();
                for (StringTermsBucket bucket : termsAggregate.buckets().array()) {
                    val = bucket.key();
                    IDataBuffer copy = buffer.copy();

                    copy.put(key,val);

                    toBufferList( bucket.aggregations(),copy,buffers);
                    ok= false;
                }
            }else if(aggregate.isDateHistogram()){
                for (DateHistogramBucket bucket : aggregate.dateHistogram().buckets().array()) {
                    val = bucket.keyAsString();
                    IDataBuffer copy = buffer.copy();

                    copy.put(key,val);

                    toBufferList( bucket.aggregations(),copy,buffers);
                    ok= false;
                }
            }else {
                if(aggregate.isAvg()){
                    val= aggregate.avg().value();
                }else if(aggregate.isValueCount()){
                    val= aggregate.valueCount().value();

                }else if(aggregate.isCardinality()) {
                    val = aggregate.cardinality().value();


                }
                else if(aggregate.isSum()){
                    val= aggregate.sum().value();
                }else if(aggregate.isMin()){
                    val = aggregate.min().value();
                }else if(aggregate.isMax()){
                    val= aggregate.max().value();

                }
                buffer.put(key,val);
            }


        }

        if(ok){
            buffers.add(buffer);
        }


    }
    public static List<IDataBuffer> toBufferList(List<Hit> hits, ICoderManager coderManager ) {

        return   hits.stream().map(row -> {
            return toBuffer(row,coderManager);
        }).collect(Collectors.toList());

    }
    private static IDataBuffer toBuffer( Hit hit,ICoderManager coderManager ){
        IDataBuffer buffer =coderManager.createDecoderBuffer();
        Collection<Map.Entry<String, JsonData>> entries = hit.fields().entrySet();
        for (Map.Entry<String, JsonData>  entry : entries) {
            JsonArray jsonValues = entry.getValue().toJson().asJsonArray();
            String val =jsonValues.get(0).toString();
            String key  = entry.getKey();
            buffer.put(key,val);
        }
        Set<Map.Entry<String, List<String>>>  set = hit.highlight().entrySet();
        for (Map.Entry<String, List<String>> entry : set) {
            buffer.put( entry.getKey(),entry.getValue().get(0));
        }

        buffer.put(    "_id",hit.id());
        buffer.put(     "_index",hit.index());
        buffer.put(     "_type",hit.type());
        buffer.put(    "_score",hit.score());
        return buffer;
    }
}
